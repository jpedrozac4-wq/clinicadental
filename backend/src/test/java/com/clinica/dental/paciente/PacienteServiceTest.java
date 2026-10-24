package com.clinica.dental.paciente;

import com.clinica.dental.auditoria.AccionAuditoria;
import com.clinica.dental.auditoria.AuditoriaService;
import com.clinica.dental.auditoria.DetalleAuditoria;
import com.clinica.dental.paciente.dto.PacienteRequest;
import com.clinica.dental.paciente.dto.PacienteResponse;
import com.clinica.dental.shared.ClinicaBusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Reglas de negocio de la ficha del paciente (S1, S2).
 *
 * <p>El repositorio esta mockeado: estas pruebas fijan la logica del servicio y el contrato del DTO,
 * no la ejecucion de SQL.
 */
@ExtendWith(MockitoExtension.class)
class PacienteServiceTest {

    @Mock
    private PacienteRepository repository;

    @Mock
    private AuditoriaService auditoria;

    @InjectMocks
    private PacienteService service;

    private static PacienteRequest fichaValida() {
        return new PacienteRequest("Ana Lucía", "Mendoza Rojas", LocalDate.of(1990, 3, 14),
            "777-12345", "Av. Busch 1450", "ana.mendoza@example.com");
    }

    private static Paciente persistido(int id) {
        Paciente p = new Paciente();
        p.setId(id);
        p.setNombres("Ana Lucía");
        p.setApellidos("Mendoza Rojas");
        p.setFechaNacimiento(LocalDate.of(1990, 3, 14));
        p.setTelefono("777-12345");
        p.setDireccion("Av. Busch 1450");
        p.setEmail("ana.mendoza@example.com");
        p.setActive(true);
        return p;
    }

    @Test
    void crearGuardaElPacienteVigenteConLosDatosDeLaFicha() {
        when(repository.save(any(Paciente.class))).thenAnswer(inv -> {
            Paciente p = inv.getArgument(0);
            p.setId(7);
            return p;
        });

        PacienteResponse creado = service.crear(fichaValida());

        assertThat(creado.id()).isEqualTo(7);
        assertThat(creado.nombres()).isEqualTo("Ana Lucía");
        assertThat(creado.apellidos()).isEqualTo("Mendoza Rojas");
        assertThat(creado.fechaNacimiento()).isEqualTo(LocalDate.of(1990, 3, 14));
        assertThat(creado.telefono()).isEqualTo("777-12345");
        assertThat(creado.direccion()).isEqualTo("Av. Busch 1450");
        assertThat(creado.email()).isEqualTo("ana.mendoza@example.com");

        ArgumentCaptor<Paciente> guardado = ArgumentCaptor.forClass(Paciente.class);
        verify(repository).save(guardado.capture());
        assertThat(guardado.getValue().getActive()).isTrue();
    }

    @Test
    void crearConEmailInvalidoSeRechazaYNoPersisteNada() {
        PacienteRequest invalida = new PacienteRequest("Ana", "Mendoza", LocalDate.of(1990, 3, 14),
            "777-12345", "Av. Busch 1450", "ana.mendoza.example.com");

        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.crear(invalida), ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getMessage()).contains("email");
        verify(repository, never()).save(any(Paciente.class));
    }

    @Test
    void crearConUnCampoObligatorioEnBlancoSeRechazaYNoPersisteNada() {
        PacienteRequest sinTelefono = new PacienteRequest("Ana", "Mendoza", LocalDate.of(1990, 3, 14),
            "   ", "Av. Busch 1450", "ana.mendoza@example.com");

        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.crear(sinTelefono), ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(repository, never()).save(any(Paciente.class));
    }

    @Test
    void crearConFechaDeNacimientoFuturaSeRechazaYNoPersisteNada() {
        PacienteRequest futuro = new PacienteRequest("Ana", "Mendoza", LocalDate.now().plusDays(1),
            "777-12345", "Av. Busch 1450", "ana.mendoza@example.com");

        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.crear(futuro), ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(repository, never()).save(any(Paciente.class));
    }

    @Test
    void obtenerUnPacienteInexistenteDevuelve404() {
        when(repository.findByIdAndActiveTrue(99)).thenReturn(Optional.empty());

        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.obtener(99), ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void actualizarUnPacienteInexistenteDevuelve404YNoPersiste() {
        when(repository.findByIdAndActiveTrue(99)).thenReturn(Optional.empty());

        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.actualizar(99, fichaValida()), ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(repository, never()).save(any(Paciente.class));
    }

    @Test
    void actualizarModificaLosDatosDeLaFicha() {
        Paciente existente = persistido(5);
        when(repository.findByIdAndActiveTrue(5)).thenReturn(Optional.of(existente));
        when(repository.save(any(Paciente.class))).thenAnswer(inv -> inv.getArgument(0));

        PacienteResponse actualizado = service.actualizar(5, new PacienteRequest(
            "Ana", "Mendoza Rojas", LocalDate.of(1990, 3, 14),
            "700-00000", "Calle Nueva 100", "ana.nueva@example.com"));

        assertThat(actualizado.telefono()).isEqualTo("700-00000");
        assertThat(actualizado.direccion()).isEqualTo("Calle Nueva 100");
        assertThat(actualizado.email()).isEqualTo("ana.nueva@example.com");
    }

    @Test
    void darDeBajaDesactivaSinBorrarElRegistro() {
        Paciente existente = persistido(5);
        when(repository.findById(5)).thenReturn(Optional.of(existente));
        when(repository.save(any(Paciente.class))).thenAnswer(inv -> inv.getArgument(0));

        service.darDeBaja(5);

        ArgumentCaptor<Paciente> guardado = ArgumentCaptor.forClass(Paciente.class);
        verify(repository).save(guardado.capture());
        assertThat(guardado.getValue().getActive()).isFalse();
        verify(repository, never()).delete(any(Paciente.class));
        verify(repository, never()).deleteById(any(Integer.class));
    }

    @Test
    void darDeBajaDosVecesEsIdempotente() {
        Paciente yaInactivo = persistido(5);
        yaInactivo.setActive(false);
        when(repository.findById(5)).thenReturn(Optional.of(yaInactivo));

        service.darDeBaja(5);

        verify(repository, never()).save(any(Paciente.class));
    }

    @Test
    void darDeBajaAUnPacienteInexistenteDevuelve404() {
        when(repository.findById(99)).thenReturn(Optional.empty());

        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.darDeBaja(99), ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void listarSinTextoConsultaSinFiltroYDevuelveSoloVigentes() {
        when(repository.buscar(null)).thenReturn(List.of(persistido(1), persistido(2)));

        List<PacienteResponse> lista = service.listar("  ");

        assertThat(lista).hasSize(2);
        verify(repository).buscar(null);
    }

    @Test
    void listarConTextoNormalizaElFiltroParaLaBusquedaParcial() {
        when(repository.buscar("%mendoza%")).thenReturn(List.of(persistido(1)));

        assertThat(service.listar("  Mendoza ")).hasSize(1);

        verify(repository).buscar("%mendoza%");
    }

    // -------------------------------------------------- auditoria de operaciones (S19)

    @Test
    void auditaElAltaConSuCodigoYLaFichaCreada() {
        when(repository.save(any(Paciente.class))).thenAnswer(inv -> {
            Paciente p = inv.getArgument(0);
            p.setId(7);
            return p;
        });

        service.crear(fichaValida());

        ArgumentCaptor<DetalleAuditoria> detalle = ArgumentCaptor.forClass(DetalleAuditoria.class);
        verify(auditoria).registrar(eq(AccionAuditoria.PACIENTE_CREAR), eq(7), detalle.capture());
        assertThat(detalle.getValue().valores())
            .containsEntry("idPaciente", 7)
            .containsEntry("email", "ana.mendoza@example.com");
    }

    @Test
    void auditaLaEdicionConElCodigoDeActualizacion() {
        when(repository.findByIdAndActiveTrue(5)).thenReturn(Optional.of(persistido(5)));
        when(repository.save(any(Paciente.class))).thenAnswer(inv -> inv.getArgument(0));

        service.actualizar(5, fichaValida());

        verify(auditoria).registrar(eq(AccionAuditoria.PACIENTE_ACTUALIZAR), eq(5),
            any(DetalleAuditoria.class));
    }

    @Test
    void auditaLaBajaConElEstadoResultante() {
        when(repository.findById(5)).thenReturn(Optional.of(persistido(5)));
        when(repository.save(any(Paciente.class))).thenAnswer(inv -> inv.getArgument(0));

        service.darDeBaja(5);

        ArgumentCaptor<DetalleAuditoria> detalle = ArgumentCaptor.forClass(DetalleAuditoria.class);
        verify(auditoria).registrar(eq(AccionAuditoria.PACIENTE_BAJA), eq(5), detalle.capture());
        assertThat(detalle.getValue().valores()).containsEntry("activo", Boolean.FALSE);
    }

    @Test
    void unaBajaRepetidaNoEscribeUnaSegundaFilaDeAuditoria() {
        Paciente yaInactivo = persistido(5);
        yaInactivo.setActive(false);
        when(repository.findById(5)).thenReturn(Optional.of(yaInactivo));

        service.darDeBaja(5);

        verifyNoInteractions(auditoria);
    }

    @Test
    void unAltaRechazadaNoDejaFilaDeAuditoria() {
        PacienteRequest invalida = new PacienteRequest("Ana", "Mendoza", LocalDate.of(1990, 3, 14),
            "777-12345", "Av. Busch 1450", "sin-arroba");

        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.crear(invalida), ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(auditoria);
    }

    @Test
    void unaEdicionInexistenteNoDejaFilaDeAuditoria() {
        when(repository.findByIdAndActiveTrue(99)).thenReturn(Optional.empty());

        catchThrowableOfType(() -> service.actualizar(99, fichaValida()), ClinicaBusinessException.class);

        verifyNoInteractions(auditoria);
    }

    @Test
    void unaBajaInexistenteNoDejaFilaDeAuditoria() {
        when(repository.findById(99)).thenReturn(Optional.empty());

        catchThrowableOfType(() -> service.darDeBaja(99), ClinicaBusinessException.class);

        verifyNoInteractions(auditoria);
    }
}
