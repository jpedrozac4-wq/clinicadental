package com.clinica.dental.auditoria;

import com.clinica.dental.cita.CitaService;
import com.clinica.dental.factura.EstadoFactura;
import com.clinica.dental.factura.EstadoFacturaRepository;
import com.clinica.dental.factura.Factura;
import com.clinica.dental.factura.FacturaDetalleRepository;
import com.clinica.dental.factura.FacturaPdfService;
import com.clinica.dental.factura.FacturaRepository;
import com.clinica.dental.factura.FacturaService;
import com.clinica.dental.paciente.Paciente;
import com.clinica.dental.paciente.PacienteRepository;
import com.clinica.dental.paciente.PacienteService;
import com.clinica.dental.paciente.dto.PacienteRequest;
import com.clinica.dental.pago.MetodoPago;
import com.clinica.dental.pago.MetodoPagoRepository;
import com.clinica.dental.pago.Pago;
import com.clinica.dental.pago.PagoRepository;
import com.clinica.dental.pago.PagoService;
import com.clinica.dental.pago.dto.PagoRequest;
import com.clinica.dental.realizado.TratamientoRealizadoRepository;
import com.clinica.dental.realizado.TratamientoRealizadoService;
import com.clinica.dental.tratamiento.TratamientoRepository;
import com.clinica.dental.tratamiento.TratamientoService;
import com.clinica.dental.usuario.RolRepository;
import com.clinica.dental.usuario.RolService;
import com.clinica.dental.usuario.UsuarioService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * La fila de auditoria se escribe dentro de la transaccion de la operacion de negocio (S19).
 *
 * <p>El requisito es que si el insert de auditoria falla, la operacion se revierta. La reversion
 * efectiva necesita un gestor de transacciones real sobre PostgreSQL, y este repositorio no tiene
 * suite de integracion (sin H2 ni Testcontainers, con el esquema real en {@code schema.sql}). Lo que
 * si se puede fijar sin base de datos son las tres condiciones de las que depende la reversion:
 *
 * <ol>
 *   <li>cada operacion auditada es un metodo {@code @Transactional} de propagacion
 *       {@link Propagation#REQUIRED}, de modo que no existe una segunda transaccion independiente
 *       que pudiera confirmarse sola;</li>
 *   <li>la escritura de auditoria tambien es {@code REQUIRED} y de lectura/escritura, es decir, se
 *       une a esa transaccion;</li>
 *   <li>el servicio de negocio no atrapa la excepcion: el fallo de auditoria sale del metodo, que es
 *       lo que hace que Spring marque la transaccion como {@code rollback-only} y revierta tambien
 *       el trabajo de negocio.</li>
 * </ol>
 *
 * <p>La tercera condicion ademas se comprueba en orden: la auditoria se escribe despues de la
 * escritura de negocio y antes de que el metodo retorne.
 */
class AuditoriaTransaccionTest {

    /** Operaciones de escritura que S19 obliga a auditar, por servicio. */
    private static final Map<Class<?>, List<String>> OPERACIONES_AUDITADAS = Map.of(
        PacienteService.class, List.of("crear", "actualizar", "darDeBaja"),
        CitaService.class, List.of("crear", "reprogramar", "cancelar"),
        TratamientoService.class, List.of("crear", "actualizar"),
        TratamientoRealizadoService.class, List.of("registrar"),
        FacturaService.class, List.of("emitir"),
        PagoService.class, List.of("registrar"),
        UsuarioService.class, List.of("crear", "actualizar", "darDeBaja"),
        RolService.class, List.of("crear", "actualizar", "eliminar"));

    private static Method metodosDeEscritura(Class<?> servicio, String nombre) {
        return Arrays.stream(servicio.getDeclaredMethods())
            .filter(metodo -> metodo.getName().equals(nombre))
            .filter(metodo -> Modifier.isPublic(metodo.getModifiers()))
            .findFirst()
            .orElseThrow(() -> new AssertionError(
                "No existe " + servicio.getSimpleName() + "." + nombre));
    }

    private static PacienteRequest fichaDePaciente() {
        return new PacienteRequest("Ana Lucía", "Mendoza Rojas", LocalDate.of(1990, 3, 14),
            "777-12345", "Av. Busch 1450", "ana.mendoza@example.com");
    }

    // ------------------------------------------- la operacion no abre otra transaccion

    @Test
    void cadaOperacionAuditadaEsTransaccionalYDePropagacionRequerida() {
        for (Map.Entry<Class<?>, List<String>> entrada : OPERACIONES_AUDITADAS.entrySet()) {
            Class<?> servicio = entrada.getKey();
            for (String nombre : entrada.getValue()) {
                Transactional anotacion =
                    metodosDeEscritura(servicio, nombre).getAnnotation(Transactional.class);

                assertThat(anotacion)
                    .as("%s.%s debe ser @Transactional para compartir la transaccion con la auditoria",
                        servicio.getSimpleName(), nombre)
                    .isNotNull();
                assertThat(anotacion.propagation())
                    .as("%s.%s no puede abrir una segunda transaccion", servicio.getSimpleName(), nombre)
                    .isEqualTo(Propagation.REQUIRED);
                assertThat(anotacion.readOnly())
                    .as("%s.%s escribe, no puede ser readOnly", servicio.getSimpleName(), nombre)
                    .isFalse();
            }
        }
    }

    @Test
    void laEscrituraDeAuditoriaSeUneALaTransaccionEnCurso() {
        Transactional anotacion =
            metodosDeEscritura(AuditoriaService.class, "registrar").getAnnotation(Transactional.class);

        assertThat(anotacion).isNotNull();
        assertThat(anotacion.propagation()).isEqualTo(Propagation.REQUIRED);
        assertThat(anotacion.readOnly()).isFalse();
    }

    // ------------------------------------------- el fallo de auditoria no se traga

    @Test
    void elFalloDelInsertDeAuditoriaSaleDelServicioDePacientes() {
        PacienteRepository repository = mock(PacienteRepository.class);
        AuditoriaService auditoria = mock(AuditoriaService.class);
        when(repository.save(any(Paciente.class))).thenAnswer(inv -> {
            Paciente paciente = inv.getArgument(0);
            paciente.setId(7);
            return paciente;
        });
        doThrow(new IllegalStateException("audit_log rechazo la fila"))
            .when(auditoria).registrar(any(AccionAuditoria.class), any(), any(DetalleAuditoria.class));

        PacienteService service = new PacienteService(repository, auditoria);

        assertThatThrownBy(() -> service.crear(fichaDePaciente()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("audit_log rechazo la fila");
    }

    @Test
    void elFalloDelInsertDeAuditoriaSaleDelServicioDePagos() {
        PagoRepository pagos = mock(PagoRepository.class);
        MetodoPagoRepository metodos = mock(MetodoPagoRepository.class);
        FacturaRepository facturas = mock(FacturaRepository.class);
        EstadoFacturaRepository estados = mock(EstadoFacturaRepository.class);
        AuditoriaService auditoria = mock(AuditoriaService.class);

        Factura factura = new Factura();
        factura.setId(50);
        factura.setNumero("F-000001");
        factura.setTotal(new BigDecimal("550.00"));
        factura.setSaldo(new BigDecimal("550.00"));
        EstadoFactura emitida = new EstadoFactura();
        emitida.setCode(EstadoFactura.EMITIDA);
        factura.setEstado(emitida);

        MetodoPago efectivo = new MetodoPago();
        efectivo.setId(1);
        efectivo.setCode(MetodoPago.EFECTIVO);

        when(facturas.findById(50)).thenReturn(Optional.of(factura));
        when(metodos.findByIdAndActiveTrue(1)).thenReturn(Optional.of(efectivo));
        when(pagos.sumarPagos(50)).thenReturn(new BigDecimal("0.00"));
        when(pagos.save(any(Pago.class))).thenAnswer(inv -> inv.getArgument(0));
        EstadoFactura parcial = new EstadoFactura();
        parcial.setCode(EstadoFactura.PAGO_PARCIAL);
        when(estados.findByCode(EstadoFactura.PAGO_PARCIAL)).thenReturn(Optional.of(parcial));
        doThrow(new IllegalStateException("audit_log rechazo la fila"))
            .when(auditoria).registrar(any(AccionAuditoria.class), any(), any(DetalleAuditoria.class));

        PagoService service = new PagoService(pagos, metodos, facturas, estados, auditoria);

        assertThatThrownBy(() -> service.registrar(50,
            new PagoRequest(new BigDecimal("200.00"), 1, null)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("audit_log rechazo la fila");
    }

    @Test
    void elFalloDelInsertDeAuditoriaSaleDelServicioDeFacturas() {
        FacturaRepository facturas = mock(FacturaRepository.class);
        FacturaDetalleRepository detalles = mock(FacturaDetalleRepository.class);
        EstadoFacturaRepository estados = mock(EstadoFacturaRepository.class);
        PacienteRepository pacientes = mock(PacienteRepository.class);
        TratamientoRealizadoRepository realizados = mock(TratamientoRealizadoRepository.class);
        FacturaPdfService pdf = mock(FacturaPdfService.class);
        AuditoriaService auditoria = mock(AuditoriaService.class);

        Paciente paciente = new Paciente();
        paciente.setId(1);
        paciente.setActive(true);
        when(pacientes.findByIdAndActiveTrue(1)).thenReturn(Optional.of(paciente));

        com.clinica.dental.tratamiento.Tratamiento tratamiento =
            new com.clinica.dental.tratamiento.Tratamiento();
        tratamiento.setId(2);
        tratamiento.setNombre("Obturacion de resina");

        com.clinica.dental.realizado.TratamientoRealizado realizado =
            new com.clinica.dental.realizado.TratamientoRealizado();
        realizado.setId(7);
        realizado.setPaciente(paciente);
        realizado.setTratamiento(tratamiento);
        realizado.setCosto(new BigDecimal("400.00"));
        when(realizados.findById(7)).thenReturn(Optional.of(realizado));

        when(facturas.ultimoNumero()).thenReturn(Optional.empty());
        when(facturas.save(any(Factura.class))).thenAnswer(inv -> {
            Factura factura = inv.getArgument(0);
            factura.setId(50);
            return factura;
        });
        when(detalles.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        EstadoFactura emitida = new EstadoFactura();
        emitida.setCode(EstadoFactura.EMITIDA);
        when(estados.findByCode(EstadoFactura.EMITIDA)).thenReturn(Optional.of(emitida));
        doThrow(new IllegalStateException("audit_log rechazo la fila"))
            .when(auditoria).registrar(any(AccionAuditoria.class), any(), any(DetalleAuditoria.class));

        FacturaService service =
            new FacturaService(facturas, detalles, estados, pacientes, realizados, pdf, auditoria);

        assertThatThrownBy(() -> service.emitir(
            new com.clinica.dental.factura.dto.FacturaRequest(1, List.of(7), null)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("audit_log rechazo la fila");
    }

    // ------------------------------------------- la auditoria se escribe al final

    @Test
    void laAuditoriaSeEscribeDespuesDeLaOperacionDeNegocioYAntesDeRetornar() {
        PacienteRepository repository = mock(PacienteRepository.class);
        AuditoriaService auditoria = mock(AuditoriaService.class);
        when(repository.save(any(Paciente.class))).thenAnswer(inv -> {
            Paciente paciente = inv.getArgument(0);
            paciente.setId(7);
            return paciente;
        });

        PacienteService service = new PacienteService(repository, auditoria);
        service.crear(fichaDePaciente());

        var orden = inOrder(repository, auditoria);
        orden.verify(repository).save(any(Paciente.class));
        orden.verify(auditoria).registrar(eq(AccionAuditoria.PACIENTE_CREAR), eq(7),
            any(DetalleAuditoria.class));
    }

    @Test
    void unaOperacionRechazadaNoLlegaAEscribirAuditoria() {
        PacienteRepository repository = mock(PacienteRepository.class);
        AuditoriaService auditoria = mock(AuditoriaService.class);
        PacienteService service = new PacienteService(repository, auditoria);

        assertThatThrownBy(() -> service.crear(new PacienteRequest("Ana", "Mendoza",
            LocalDate.of(1990, 3, 14), "777-12345", "Av. Busch 1450", "sin-arroba")))
            .isInstanceOf(RuntimeException.class);

        var orden = inOrder(repository, auditoria);
        orden.verifyNoMoreInteractions();
    }

    /** El catalogo real de {@code cat_log_accion} cubre los 17 codigos que S19 exige auditar. */
    @Test
    void elEnumCubreExactamenteLasDiecisieteOperacionesDeS19() {
        assertThat(AccionAuditoria.values())
            .extracting(AccionAuditoria::getCodigo)
            .containsExactlyInAnyOrder(
                "PACIENTE_CREAR", "PACIENTE_ACTUALIZAR", "PACIENTE_BAJA",
                "CITA_CREAR", "CITA_REPROGRAMAR", "CITA_CANCELAR",
                "TRATAMIENTO_CREAR", "TRATAMIENTO_ACTUALIZAR",
                "REALIZADO_REGISTRAR",
                "FACTURA_EMITIR",
                "PAGO_REGISTRAR",
                "USUARIO_CREAR", "USUARIO_ACTUALIZAR", "USUARIO_BAJA",
                "ROL_CREAR", "ROL_ACTUALIZAR", "ROL_ELIMINAR");
    }

    /** Cada codigo apunta a la tabla real del esquema; un cambio de tabla es un cambio de contrato. */
    @Test
    void cadaCodigoDeclaraLaTablaRealQueModifica() {
        assertThat(Arrays.stream(AccionAuditoria.values())
            .collect(java.util.stream.Collectors.toMap(AccionAuditoria::getCodigo, AccionAuditoria::getTabla)))
            .containsEntry("PACIENTE_CREAR", "paciente")
            .containsEntry("CITA_CANCELAR", "cita")
            .containsEntry("TRATAMIENTO_ACTUALIZAR", "tratamiento")
            .containsEntry("REALIZADO_REGISTRAR", "tratamiento_realizado")
            .containsEntry("FACTURA_EMITIR", "factura")
            .containsEntry("PAGO_REGISTRAR", "pago")
            .containsEntry("USUARIO_BAJA", "app_user")
            .containsEntry("ROL_ELIMINAR", "role");
    }
}
