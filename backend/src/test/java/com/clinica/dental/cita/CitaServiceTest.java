package com.clinica.dental.cita;

import com.clinica.dental.auditoria.AccionAuditoria;
import com.clinica.dental.auditoria.AuditoriaService;
import com.clinica.dental.auditoria.DetalleAuditoria;
import com.clinica.dental.cita.dto.CitaRequest;
import com.clinica.dental.cita.dto.CitaResponse;
import com.clinica.dental.cita.dto.ReprogramarRequest;
import com.clinica.dental.paciente.Paciente;
import com.clinica.dental.paciente.PacienteRepository;
import com.clinica.dental.recordatorio.RecordatorioService;
import com.clinica.dental.seguridad.SystemUser;
import com.clinica.dental.seguridad.SystemUserRepository;
import com.clinica.dental.shared.ClinicaBusinessException;
import com.clinica.dental.tratamiento.Tratamiento;
import com.clinica.dental.tratamiento.TratamientoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Reglas de agenda: programar, reprogramar y cancelar (S4, S6). */
@ExtendWith(MockitoExtension.class)
class CitaServiceTest {

    @Mock private CitaRepository citas;
    @Mock private EstadoCitaRepository estados;
    @Mock private PacienteRepository pacientes;
    @Mock private TratamientoRepository tratamientos;
    @Mock private SystemUserRepository usuarios;
    @Mock private RecordatorioService recordatorios;
    @Mock private AuditoriaService auditoria;

    @InjectMocks private CitaService service;

    @BeforeEach
    void setUp() {
        // El catalogo de estados existe en la base; para la prueba se resuelve por codigo.
        lenient().when(estados.findByCode(anyString()))
            .thenAnswer(inv -> Optional.of(estado(inv.getArgument(0))));
    }

    private static EstadoCita estado(String code) {
        EstadoCita e = new EstadoCita();
        e.setCode(code);
        return e;
    }

    private static Paciente paciente(int id) {
        Paciente p = new Paciente();
        p.setId(id);
        p.setNombres("Ana Lucía");
        p.setApellidos("Mendoza Rojas");
        p.setActive(true);
        return p;
    }

    private static Tratamiento tratamiento(int id, int duracion) {
        Tratamiento t = new Tratamiento();
        t.setId(id);
        t.setNombre("Limpieza dental");
        t.setPrecio(new BigDecimal("350.00"));
        t.setDuracionMinutos(duracion);
        t.setActive(true);
        return t;
    }

    private static SystemUser dentista(int id) {
        SystemUser u = new SystemUser();
        u.setId(id);
        u.setFullName("Dra. Valeria Ríos");
        u.setActive(true);
        return u;
    }

    private static Cita cita(int id, String code, OffsetDateTime inicio) {
        Cita c = new Cita();
        c.setId(id);
        c.setPaciente(paciente(1));
        c.setTratamiento(tratamiento(2, 45));
        c.setDentista(dentista(3));
        c.setEstado(estado(code));
        c.setFechaHoraInicio(inicio);
        c.setDuracionMinutos(45);
        c.setMotivo("Control");
        return c;
    }

    private static OffsetDateTime enDosDias() {
        return OffsetDateTime.now().plusDays(2).truncatedTo(ChronoUnit.MINUTES);
    }

    @Test
    void crearProgramaLaCitaConLaDuracionDelCatalogo() {
        when(pacientes.findByIdAndActiveTrue(1)).thenReturn(Optional.of(paciente(1)));
        when(tratamientos.findByIdAndActiveTrue(2)).thenReturn(Optional.of(tratamiento(2, 45)));
        when(usuarios.findById(3)).thenReturn(Optional.of(dentista(3)));
        when(citas.contarSolapadas(eq(3), any(), any())).thenReturn(0L);
        when(citas.save(any(Cita.class))).thenAnswer(inv -> {
            Cita c = inv.getArgument(0);
            c.setId(50);
            return c;
        });

        CitaResponse creada = service.crear(new CitaRequest(1, 2, 3, enDosDias(), "Dolor molar"));

        assertThat(creada.id()).isEqualTo(50);
        assertThat(creada.estado()).isEqualTo(EstadoCita.PROGRAMADA);
        assertThat(creada.duracionMinutos()).isEqualTo(45);
        assertThat(creada.motivo()).isEqualTo("Dolor molar");
    }

    @Test
    void crearConFechaEnElPasadoDevuelve422YNoGuarda() {
        when(pacientes.findByIdAndActiveTrue(1)).thenReturn(Optional.of(paciente(1)));
        when(tratamientos.findByIdAndActiveTrue(2)).thenReturn(Optional.of(tratamiento(2, 45)));
        when(usuarios.findById(3)).thenReturn(Optional.of(dentista(3)));

        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.crear(new CitaRequest(1, 2, 3, OffsetDateTime.now().minusDays(1), null)),
            ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        verify(citas, never()).save(any(Cita.class));
    }

    @Test
    void crearConSolapamientoDeAgendaDevuelve409YNoGuarda() {
        when(pacientes.findByIdAndActiveTrue(1)).thenReturn(Optional.of(paciente(1)));
        when(tratamientos.findByIdAndActiveTrue(2)).thenReturn(Optional.of(tratamiento(2, 45)));
        when(usuarios.findById(3)).thenReturn(Optional.of(dentista(3)));
        when(citas.contarSolapadas(eq(3), any(), any())).thenReturn(1L);

        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.crear(new CitaRequest(1, 2, 3, enDosDias(), null)),
            ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verify(citas, never()).save(any(Cita.class));
    }

    @Test
    void crearParaUnPacienteDadoDeBajaDevuelve404() {
        when(pacientes.findByIdAndActiveTrue(1)).thenReturn(Optional.empty());

        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.crear(new CitaRequest(1, 2, 3, enDosDias(), null)),
            ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(citas, never()).save(any(Cita.class));
    }

    @Test
    void reprogramarMarcaLaOriginalYVinculaLaNuevaCita() {
        Cita original = cita(20, EstadoCita.PROGRAMADA, enDosDias());
        when(citas.findById(20)).thenReturn(Optional.of(original));
        when(citas.contarSolapadas(eq(3), any(), any())).thenReturn(0L);
        when(citas.save(any(Cita.class))).thenAnswer(inv -> inv.getArgument(0));

        OffsetDateTime nuevaFecha = enDosDias().plusDays(3);
        CitaResponse nueva = service.reprogramar(20, new ReprogramarRequest(nuevaFecha, null));

        assertThat(original.getEstado().getCode()).isEqualTo(EstadoCita.REPROGRAMADA);
        assertThat(nueva.estado()).isEqualTo(EstadoCita.PROGRAMADA);
        assertThat(nueva.fechaHoraInicio()).isEqualTo(nuevaFecha);
        assertThat(nueva.idCitaOrigen()).isEqualTo(20);

        ArgumentCaptor<Cita> guardadas = ArgumentCaptor.forClass(Cita.class);
        verify(citas, times(2)).save(guardadas.capture());
        assertThat(guardadas.getAllValues().get(1).getCitaOrigen()).isSameAs(original);
    }

    @Test
    void reprogramarUnaCitaAtendidaDevuelve409() {
        when(citas.findById(21)).thenReturn(Optional.of(
            cita(21, EstadoCita.ATENDIDA, OffsetDateTime.now().minusDays(3))));

        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.reprogramar(21, new ReprogramarRequest(enDosDias(), null)),
            ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verify(citas, never()).save(any(Cita.class));
    }

    @Test
    void reprogramarAUnaFechaPasadaDevuelve422YNoTocaLaOriginal() {
        Cita original = cita(22, EstadoCita.PROGRAMADA, enDosDias());
        when(citas.findById(22)).thenReturn(Optional.of(original));

        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.reprogramar(22, new ReprogramarRequest(OffsetDateTime.now().minusHours(5), null)),
            ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(original.getEstado().getCode()).isEqualTo(EstadoCita.PROGRAMADA);
        verify(citas, never()).save(any(Cita.class));
    }

    @Test
    void cancelarDejaLaCitaCancelada() {
        Cita c = cita(30, EstadoCita.CONFIRMADA, enDosDias());
        when(citas.findById(30)).thenReturn(Optional.of(c));
        when(citas.save(any(Cita.class))).thenAnswer(inv -> inv.getArgument(0));

        service.cancelar(30);

        assertThat(c.getEstado().getCode()).isEqualTo(EstadoCita.CANCELADA);
        verify(citas).save(c);
    }

    @Test
    void cancelarDosVecesEsIdempotente() {
        Cita yaCancelada = cita(31, EstadoCita.CANCELADA, enDosDias());
        when(citas.findById(31)).thenReturn(Optional.of(yaCancelada));

        service.cancelar(31);

        verify(citas, never()).save(any(Cita.class));
    }

    @Test
    void cancelarUnaCitaAtendidaDevuelve409() {
        when(citas.findById(32)).thenReturn(Optional.of(
            cita(32, EstadoCita.ATENDIDA, OffsetDateTime.now().minusDays(2))));

        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.cancelar(32), ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verify(citas, never()).save(any(Cita.class));
    }

    @Test
    void cancelarUnaCitaInexistenteDevuelve404() {
        when(citas.findById(99)).thenReturn(Optional.empty());

        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.cancelar(99), ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void agendaConsultaElRangoPedidoPorElCliente() {
        OffsetDateTime desde = OffsetDateTime.now().truncatedTo(ChronoUnit.DAYS);
        OffsetDateTime hasta = desde.plusDays(7);
        when(citas.agenda(desde, hasta)).thenReturn(List.of(cita(40, EstadoCita.PROGRAMADA, desde.plusHours(9))));

        List<CitaResponse> agenda = service.agenda(desde, hasta);

        assertThat(agenda).hasSize(1);
        assertThat(agenda.get(0).id()).isEqualTo(40);
    }

    @Test
    void crearProgramaElRecordatorioDeLaCitaAgendada() {
        when(pacientes.findByIdAndActiveTrue(1)).thenReturn(Optional.of(paciente(1)));
        when(tratamientos.findByIdAndActiveTrue(2)).thenReturn(Optional.of(tratamiento(2, 45)));
        when(usuarios.findById(3)).thenReturn(Optional.of(dentista(3)));
        when(citas.contarSolapadas(eq(3), any(), any())).thenReturn(0L);
        when(citas.save(any(Cita.class))).thenAnswer(inv -> {
            Cita c = inv.getArgument(0);
            c.setId(50);
            return c;
        });

        service.crear(new CitaRequest(1, 2, 3, enDosDias(), null));

        ArgumentCaptor<Cita> agendadas = ArgumentCaptor.forClass(Cita.class);
        verify(recordatorios).programarPara(agendadas.capture());
        assertThat(agendadas.getValue().getId()).isEqualTo(50);
        assertThat(agendadas.getValue().getEstado().getCode()).isEqualTo(EstadoCita.PROGRAMADA);
    }

    @Test
    void crearConSolapamientoNoProgramaRecordatorios() {
        when(pacientes.findByIdAndActiveTrue(1)).thenReturn(Optional.of(paciente(1)));
        when(tratamientos.findByIdAndActiveTrue(2)).thenReturn(Optional.of(tratamiento(2, 45)));
        when(usuarios.findById(3)).thenReturn(Optional.of(dentista(3)));
        when(citas.contarSolapadas(eq(3), any(), any())).thenReturn(1L);

        catchThrowableOfType(() -> service.crear(new CitaRequest(1, 2, 3, enDosDias(), null)),
            ClinicaBusinessException.class);

        verify(recordatorios, never()).programarPara(any(Cita.class));
    }

    @Test
    void cancelarCancelaLosRecordatoriosPendientesDeLaCita() {
        Cita c = cita(30, EstadoCita.CONFIRMADA, enDosDias());
        when(citas.findById(30)).thenReturn(Optional.of(c));
        when(citas.save(any(Cita.class))).thenAnswer(inv -> inv.getArgument(0));

        service.cancelar(30);

        verify(recordatorios).cancelarPendientes(30);
    }

    @Test
    void cancelarUnaCitaYaCanceladaNoVuelveATocarLosRecordatorios() {
        when(citas.findById(31)).thenReturn(Optional.of(cita(31, EstadoCita.CANCELADA, enDosDias())));

        service.cancelar(31);

        verify(recordatorios, never()).cancelarPendientes(any());
    }

    @Test
    void reprogramarCancelaElRecordatorioDeLaOriginalYProgramaElDeLaNueva() {
        Cita original = cita(20, EstadoCita.PROGRAMADA, enDosDias());
        when(citas.findById(20)).thenReturn(Optional.of(original));
        when(citas.contarSolapadas(eq(3), any(), any())).thenReturn(0L);
        when(citas.save(any(Cita.class))).thenAnswer(inv -> inv.getArgument(0));

        OffsetDateTime nuevaFecha = enDosDias().plusDays(3);
        service.reprogramar(20, new ReprogramarRequest(nuevaFecha, null));

        verify(recordatorios).cancelarPendientes(20);

        ArgumentCaptor<Cita> agendadas = ArgumentCaptor.forClass(Cita.class);
        verify(recordatorios).programarPara(agendadas.capture());
        assertThat(agendadas.getValue().getCitaOrigen()).isSameAs(original);
        assertThat(agendadas.getValue().getFechaHoraInicio()).isEqualTo(nuevaFecha);
    }

    // -------------------------------------------------- auditoria de operaciones (S19)

    @Test
    void auditaLaProgramacionConLaCitaAgendada() {
        when(pacientes.findByIdAndActiveTrue(1)).thenReturn(Optional.of(paciente(1)));
        when(tratamientos.findByIdAndActiveTrue(2)).thenReturn(Optional.of(tratamiento(2, 45)));
        when(usuarios.findById(3)).thenReturn(Optional.of(dentista(3)));
        when(citas.contarSolapadas(eq(3), any(), any())).thenReturn(0L);
        when(citas.save(any(Cita.class))).thenAnswer(inv -> {
            Cita c = inv.getArgument(0);
            c.setId(50);
            return c;
        });

        service.crear(new CitaRequest(1, 2, 3, enDosDias(), "Dolor molar"));

        ArgumentCaptor<DetalleAuditoria> detalle = ArgumentCaptor.forClass(DetalleAuditoria.class);
        verify(auditoria).registrar(eq(AccionAuditoria.CITA_CREAR), eq(50), detalle.capture());
        assertThat(detalle.getValue().valores())
            .containsEntry("idPaciente", 1)
            .containsEntry("idDentista", 3)
            .containsEntry("estado", "PROGRAMADA");
    }

    @Test
    void auditaLaReprogramacionConLaCitaOriginalYLaNueva() {
        Cita original = cita(20, EstadoCita.PROGRAMADA, enDosDias());
        when(citas.findById(20)).thenReturn(Optional.of(original));
        when(citas.contarSolapadas(eq(3), any(), any())).thenReturn(0L);
        when(citas.save(any(Cita.class))).thenAnswer(inv -> {
            Cita c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(21);
            }
            return c;
        });

        service.reprogramar(20, new ReprogramarRequest(enDosDias().plusDays(3), null));

        ArgumentCaptor<DetalleAuditoria> detalle = ArgumentCaptor.forClass(DetalleAuditoria.class);
        verify(auditoria).registrar(eq(AccionAuditoria.CITA_REPROGRAMAR), eq(21), detalle.capture());
        assertThat(detalle.getValue().valores())
            .containsEntry("idCitaOrigen", 20)
            .containsEntry("estado", "REPROGRAMADA");
    }

    @Test
    void auditaLaCancelacionConElEstadoFinal() {
        Cita c = cita(30, EstadoCita.CONFIRMADA, enDosDias());
        when(citas.findById(30)).thenReturn(Optional.of(c));
        when(citas.save(any(Cita.class))).thenAnswer(inv -> inv.getArgument(0));

        service.cancelar(30);

        ArgumentCaptor<DetalleAuditoria> detalle = ArgumentCaptor.forClass(DetalleAuditoria.class);
        verify(auditoria).registrar(eq(AccionAuditoria.CITA_CANCELAR), eq(30), detalle.capture());
        assertThat(detalle.getValue().valores())
            .containsEntry("estado", "CANCELADA")
            .containsEntry("idPaciente", 1);
    }

    @Test
    void unaCancelacionRepetidaNoDejaFilaDeAuditoria() {
        when(citas.findById(31)).thenReturn(Optional.of(cita(31, EstadoCita.CANCELADA, enDosDias())));

        service.cancelar(31);

        verifyNoInteractions(auditoria);
    }

    @Test
    void unaCitaAtendidaNoDejaFilaDeAuditoriaAlReprogramarNiAlCancelar() {
        when(citas.findById(32)).thenReturn(Optional.of(
            cita(32, EstadoCita.ATENDIDA, OffsetDateTime.now().minusDays(2))));

        catchThrowableOfType(() -> service.reprogramar(32, new ReprogramarRequest(enDosDias(), null)),
            ClinicaBusinessException.class);
        catchThrowableOfType(() -> service.cancelar(32), ClinicaBusinessException.class);

        verifyNoInteractions(auditoria);
    }

    @Test
    void unSolapamientoDeAgendaNoDejaFilaDeAuditoria() {
        when(pacientes.findByIdAndActiveTrue(1)).thenReturn(Optional.of(paciente(1)));
        when(tratamientos.findByIdAndActiveTrue(2)).thenReturn(Optional.of(tratamiento(2, 45)));
        when(usuarios.findById(3)).thenReturn(Optional.of(dentista(3)));
        when(citas.contarSolapadas(eq(3), any(), any())).thenReturn(1L);

        catchThrowableOfType(() -> service.crear(new CitaRequest(1, 2, 3, enDosDias(), null)),
            ClinicaBusinessException.class);

        verifyNoInteractions(auditoria);
    }
}
