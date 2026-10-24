package com.clinica.dental.factura;

import com.clinica.dental.auditoria.AccionAuditoria;
import com.clinica.dental.auditoria.AuditoriaService;
import com.clinica.dental.auditoria.DetalleAuditoria;
import com.clinica.dental.paciente.Paciente;
import com.clinica.dental.paciente.PacienteRepository;
import com.clinica.dental.realizado.TratamientoRealizado;
import com.clinica.dental.realizado.TratamientoRealizadoRepository;
import com.clinica.dental.factura.dto.ComprobantePdf;
import com.clinica.dental.factura.dto.FacturaRequest;
import com.clinica.dental.factura.dto.FacturaResponse;
import com.clinica.dental.shared.ClinicaBusinessException;
import com.clinica.dental.tratamiento.Tratamiento;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Emision de facturas con detalle, total y numeracion correlativa (S9). */
@ExtendWith(MockitoExtension.class)
class FacturaServiceTest {

    @Mock private FacturaRepository facturas;
    @Mock private FacturaDetalleRepository detalles;
    @Mock private EstadoFacturaRepository estados;
    @Mock private PacienteRepository pacientes;
    @Mock private TratamientoRealizadoRepository realizados;
    @Mock private FacturaPdfService pdf;
    @Mock private AuditoriaService auditoria;

    @InjectMocks private FacturaService service;

    private static Paciente paciente(int id) {
        Paciente p = new Paciente();
        p.setId(id);
        p.setNombres("Ana Maria");
        p.setApellidos("Mendoza Rojas");
        p.setActive(true);
        return p;
    }

    private static Tratamiento tratamiento(String nombre) {
        Tratamiento t = new Tratamiento();
        t.setId(2);
        t.setNombre(nombre);
        t.setPrecio(new BigDecimal("400.00"));
        t.setDuracionMinutos(60);
        t.setActive(true);
        return t;
    }

    private static TratamientoRealizado realizado(int id, int idPaciente, String costo, String nombre) {
        TratamientoRealizado r = new TratamientoRealizado();
        r.setId(id);
        r.setPaciente(paciente(idPaciente));
        r.setTratamiento(tratamiento(nombre));
        r.setFechaRealizacion(OffsetDateTime.now().minusDays(1));
        r.setDetalleProcedimiento("Procedimiento " + id);
        r.setCosto(new BigDecimal(costo));
        return r;
    }

    private static EstadoFactura estado(String code) {
        EstadoFactura e = new EstadoFactura();
        e.setId(1);
        e.setCode(code);
        e.setDescription(code);
        e.setActive(true);
        return e;
    }

    private static FacturaRequest pedido(Integer... ids) {
        return new FacturaRequest(1, java.util.Arrays.asList(ids), null);
    }

    private void catalogoDeEstados() {
        when(estados.findByCode(EstadoFactura.EMITIDA))
            .thenReturn(Optional.of(estado(EstadoFactura.EMITIDA)));
    }

    private void guardadoConId(int id) {
        when(facturas.save(any(Factura.class))).thenAnswer(inv -> {
            Factura f = inv.getArgument(0);
            f.setId(id);
            return f;
        });
        when(detalles.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void emitirAgrupaElDetalleConElCostoCongeladoYCalculaElTotal() {
        when(pacientes.findByIdAndActiveTrue(1)).thenReturn(Optional.of(paciente(1)));
        when(realizados.findById(7)).thenReturn(Optional.of(realizado(7, 1, "400.00", "Obturacion de resina")));
        when(realizados.findById(8)).thenReturn(Optional.of(realizado(8, 1, "150.00", "Consulta y diagnostico")));
        when(facturas.ultimoNumero()).thenReturn(Optional.of("F-000004"));
        catalogoDeEstados();
        guardadoConId(50);

        FacturaResponse factura = service.emitir(pedido(7, 8));

        // L5: total = suma del costo congelado del detalle, sin impuestos ni recargos.
        assertThat(factura.total()).isEqualByComparingTo("550.00");
        assertThat(factura.saldo()).isEqualByComparingTo("550.00");
        assertThat(factura.estado()).isEqualTo(EstadoFactura.EMITIDA);
        assertThat(factura.detalle()).hasSize(2);
        assertThat(factura.detalle()).extracting(FacturaResponse.DetalleResponse::monto)
            .containsExactly(new BigDecimal("400.00"), new BigDecimal("150.00"));
        assertThat(factura.detalle()).extracting(FacturaResponse.DetalleResponse::idRealizado)
            .containsExactly(7, 8);
    }

    @Test
    void emitirUsaNumeracionCorrelativaSinHuecos() {
        when(pacientes.findByIdAndActiveTrue(1)).thenReturn(Optional.of(paciente(1)));
        when(realizados.findById(7)).thenReturn(Optional.of(realizado(7, 1, "400.00", "Obturacion de resina")));
        when(facturas.ultimoNumero()).thenReturn(Optional.of("F-000004"), Optional.of("F-000005"));
        catalogoDeEstados();
        guardadoConId(50);

        String primera = service.emitir(pedido(7)).numero();
        String segunda = service.emitir(pedido(7)).numero();

        assertThat(primera).isEqualTo("F-000005");
        assertThat(segunda).isEqualTo("F-000006");
    }

    @Test
    void laPrimeraFacturaDeUnaBaseVaciaArrancaEnF000001() {
        when(pacientes.findByIdAndActiveTrue(1)).thenReturn(Optional.of(paciente(1)));
        when(realizados.findById(7)).thenReturn(Optional.of(realizado(7, 1, "400.00", "Obturacion de resina")));
        when(facturas.ultimoNumero()).thenReturn(Optional.empty());
        catalogoDeEstados();
        guardadoConId(50);

        assertThat(service.emitir(pedido(7)).numero()).isEqualTo("F-000001");
    }

    @Test
    void unRealizadoDeOtroPacienteDevuelve409YNoConsumeNumeracion() {
        when(pacientes.findByIdAndActiveTrue(1)).thenReturn(Optional.of(paciente(1)));
        when(realizados.findById(7)).thenReturn(Optional.of(realizado(7, 99, "400.00", "Obturacion de resina")));

        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.emitir(pedido(7)), ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getMessage()).contains("no pertenece al paciente");
        verify(facturas, never()).save(any(Factura.class));
        // El numero se calcula despues de validar: un rechazo no reserva correlativo.
        verify(facturas, never()).ultimoNumero();
        verifyNoInteractions(detalles);
    }

    @Test
    void unRealizadoRepetidoEnLaMismaSolicitudDevuelve409YNoConsumeNumeracion() {
        when(pacientes.findByIdAndActiveTrue(1)).thenReturn(Optional.of(paciente(1)));
        when(realizados.findById(7)).thenReturn(Optional.of(realizado(7, 1, "400.00", "Obturacion de resina")));

        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.emitir(pedido(7, 7)), ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getMessage()).contains("repetido");
        verify(facturas, never()).save(any(Factura.class));
        verify(facturas, never()).ultimoNumero();
    }

    @Test
    void unRealizadoYaFacturadoDevuelve409YNoConsumeNumeracion() {
        when(pacientes.findByIdAndActiveTrue(1)).thenReturn(Optional.of(paciente(1)));
        when(realizados.findById(7)).thenReturn(Optional.of(realizado(7, 1, "400.00", "Obturacion de resina")));
        when(detalles.existsByRealizadoId(7)).thenReturn(true);

        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.emitir(pedido(7)), ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getMessage()).contains("ya fue facturado");
        verify(facturas, never()).save(any(Factura.class));
        verify(facturas, never()).ultimoNumero();
    }

    @Test
    void unRealizadoInexistenteDevuelve404YNoConsumeNumeracion() {
        when(pacientes.findByIdAndActiveTrue(1)).thenReturn(Optional.of(paciente(1)));
        when(realizados.findById(7)).thenReturn(Optional.empty());

        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.emitir(pedido(7)), ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(facturas, never()).save(any(Factura.class));
        verify(facturas, never()).ultimoNumero();
    }

    @Test
    void emitirSinTratamientosRealizadosDevuelve400() {
        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.emitir(pedido()), ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getMessage()).contains("al menos un tratamiento realizado");
        verifyNoInteractions(pacientes, realizados, facturas);
    }

    @Test
    void emitirParaUnPacienteDadoDeBajaDevuelve404() {
        when(pacientes.findByIdAndActiveTrue(1)).thenReturn(Optional.empty());

        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.emitir(pedido(7)), ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(facturas, never()).save(any(Factura.class));
    }

    @Test
    void listarDevuelveLasFacturasConSuDetalleYSaldo() {
        Factura guardada = new Factura();
        guardada.setId(50);
        guardada.setNumero("F-000002");
        guardada.setPaciente(paciente(1));
        guardada.setEstado(estado(EstadoFactura.PAGO_PARCIAL));
        guardada.setFechaEmision(OffsetDateTime.now().minusDays(2));
        guardada.setTotal(new BigDecimal("550.00"));
        guardada.setSaldo(new BigDecimal("150.00"));

        FacturaDetalle linea = new FacturaDetalle();
        linea.setId(1);
        linea.setFactura(guardada);
        linea.setRealizado(realizado(7, 1, "400.00", "Obturacion de resina"));
        linea.setDescripcion("Obturacion de resina");
        linea.setMonto(new BigDecimal("400.00"));

        when(facturas.findAllByOrderByNumeroDesc()).thenReturn(List.of(guardada));
        when(detalles.findByFacturaIdOrderByIdAsc(50)).thenReturn(List.of(linea));

        List<FacturaResponse> listado = service.listar();

        assertThat(listado).hasSize(1);
        assertThat(listado.get(0).saldo()).isEqualByComparingTo("150.00");
        assertThat(listado.get(0).totalPagado()).isEqualByComparingTo("400.00");
        assertThat(listado.get(0).detalle()).hasSize(1);
    }

    @Test
    void obtenerUnaFacturaInexistenteDevuelve404() {
        when(facturas.findById(50)).thenReturn(Optional.empty());

        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.obtener(50), ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ex.getMessage()).contains("Factura no encontrada");
    }

    @Test
    void elComprobantePdfSeGeneraConLaFacturaYSuDetalle() {
        Factura guardada = new Factura();
        guardada.setId(50);
        guardada.setNumero("F-000001");
        guardada.setPaciente(paciente(1));
        guardada.setEstado(estado(EstadoFactura.EMITIDA));
        guardada.setFechaEmision(OffsetDateTime.now());
        guardada.setTotal(new BigDecimal("550.00"));
        guardada.setSaldo(new BigDecimal("550.00"));

        when(facturas.findById(50)).thenReturn(Optional.of(guardada));
        when(detalles.findByFacturaIdOrderByIdAsc(50)).thenReturn(List.of());
        when(pdf.generar(any(Factura.class), anyList())).thenReturn(new byte[] {1, 2, 3});

        ComprobantePdf comprobante = service.comprobante(50);

        assertThat(comprobante.numero()).isEqualTo("F-000001");
        assertThat(comprobante.nombreArchivo()).isEqualTo("factura-F-000001.pdf");
        assertThat(comprobante.contenido()).containsExactly(1, 2, 3);
        verify(pdf).generar(guardada, List.of());
    }

    // -------------------------------------------------- auditoria de operaciones (S19)

    @Test
    void auditaLaEmisionConNumeroTotalYSaldo() {
        when(pacientes.findByIdAndActiveTrue(1)).thenReturn(Optional.of(paciente(1)));
        when(realizados.findById(7)).thenReturn(Optional.of(realizado(7, 1, "400.00", "Obturacion de resina")));
        when(facturas.ultimoNumero()).thenReturn(Optional.of("F-000004"));
        catalogoDeEstados();
        guardadoConId(50);

        service.emitir(pedido(7));

        ArgumentCaptor<DetalleAuditoria> detalle = ArgumentCaptor.forClass(DetalleAuditoria.class);
        verify(auditoria).registrar(eq(AccionAuditoria.FACTURA_EMITIR), eq(50), detalle.capture());
        assertThat(detalle.getValue().valores())
            .containsEntry("numero", "F-000005")
            .containsEntry("idPaciente", 1)
            .containsEntry("lineas", 1);
        assertThat((BigDecimal) detalle.getValue().valores().get("total"))
            .isEqualByComparingTo("400.00");
    }

    @Test
    void unRealizadoYaFacturadoNoDejaFilaDeAuditoria() {
        when(pacientes.findByIdAndActiveTrue(1)).thenReturn(Optional.of(paciente(1)));
        when(realizados.findById(7)).thenReturn(Optional.of(realizado(7, 1, "400.00", "Obturacion de resina")));
        when(detalles.existsByRealizadoId(7)).thenReturn(true);

        catchThrowableOfType(() -> service.emitir(pedido(7)), ClinicaBusinessException.class);

        verifyNoInteractions(auditoria);
    }

    @Test
    void unRealizadoDeOtroPacienteNoDejaFilaDeAuditoria() {
        when(pacientes.findByIdAndActiveTrue(1)).thenReturn(Optional.of(paciente(1)));
        when(realizados.findById(7)).thenReturn(Optional.of(realizado(7, 99, "400.00", "Obturacion de resina")));

        catchThrowableOfType(() -> service.emitir(pedido(7)), ClinicaBusinessException.class);

        verifyNoInteractions(auditoria);
    }
}
