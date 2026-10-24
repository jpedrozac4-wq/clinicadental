package com.clinica.dental.recordatorio;

import com.clinica.dental.cita.Cita;
import com.clinica.dental.cita.CitaRepository;
import com.clinica.dental.cita.EstadoCita;
import com.clinica.dental.paciente.Paciente;
import com.clinica.dental.recordatorio.dto.RecordatorioResponse;
import com.clinica.dental.seguridad.SystemUser;
import com.clinica.dental.shared.ClinicaBusinessException;
import com.clinica.dental.tratamiento.Tratamiento;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Reglas de recordatorios de citas: programacion, despacho y bitacora (S5, S15). */
@ExtendWith(MockitoExtension.class)
class RecordatorioServiceTest {

    /** Reloj fijo: el despacho debe usar la hora inyectada, nunca la del sistema. */
    private static final OffsetDateTime AHORA = OffsetDateTime.parse("2026-10-20T08:00:00-04:00");
    private static final Clock RELOJ = Clock.fixed(AHORA.toInstant(), AHORA.getOffset());

    @Mock private NotificacionCitaRepository notificaciones;
    @Mock private EstadoNotificacionRepository estados;
    @Mock private CanalNotificacionRepository canales;
    @Mock private CitaRepository citas;
    @Mock private EnvioCorreo correo;

    private RecordatorioService service;

    @BeforeEach
    void setUp() {
        service = new RecordatorioService(notificaciones, estados, canales, citas, correo, RELOJ);
        lenient().when(estados.findByCode(anyString()))
            .thenAnswer(inv -> Optional.of(estadoNotificacion(inv.getArgument(0))));
        lenient().when(canales.findByCode(anyString()))
            .thenAnswer(inv -> Optional.of(canal(inv.getArgument(0))));
        lenient().when(notificaciones.save(any(NotificacionCita.class)))
            .thenAnswer(inv -> inv.getArgument(0));
    }

    private static EstadoNotificacion estadoNotificacion(String code) {
        EstadoNotificacion estado = new EstadoNotificacion();
        estado.setCode(code);
        return estado;
    }

    private static CanalNotificacion canal(String code) {
        CanalNotificacion canal = new CanalNotificacion();
        canal.setCode(code);
        return canal;
    }

    private static Paciente paciente() {
        Paciente p = new Paciente();
        p.setId(1);
        p.setNombres("Ana Lucía");
        p.setApellidos("Mendoza Rojas");
        p.setEmail("ana.mendoza@example.com");
        p.setTelefono("70012345");
        p.setActive(true);
        return p;
    }

    private static Tratamiento tratamiento() {
        Tratamiento t = new Tratamiento();
        t.setId(2);
        t.setNombre("Limpieza dental");
        t.setPrecio(new BigDecimal("350.00"));
        t.setDuracionMinutos(45);
        t.setActive(true);
        return t;
    }

    private static SystemUser dentista() {
        SystemUser u = new SystemUser();
        u.setId(3);
        u.setFullName("Dra. Valeria Ríos");
        u.setActive(true);
        return u;
    }

    private static Cita cita(Integer id, OffsetDateTime inicio) {
        Cita c = new Cita();
        c.setId(id);
        c.setPaciente(paciente());
        c.setTratamiento(tratamiento());
        c.setDentista(dentista());
        EstadoCita estado = new EstadoCita();
        estado.setCode(EstadoCita.PROGRAMADA);
        c.setEstado(estado);
        c.setFechaHoraInicio(inicio);
        c.setDuracionMinutos(45);
        return c;
    }

    private static NotificacionCita recordatorio(int id, Cita cita) {
        NotificacionCita n = new NotificacionCita();
        n.setId(id);
        n.setCita(cita);
        n.setCanal(canal(CanalNotificacion.EMAIL));
        n.setEstado(estadoNotificacion(EstadoNotificacion.PENDIENTE));
        n.setDestino(cita.getPaciente().getEmail());
        n.setFechaObjetivo(cita.getFechaHoraInicio().minusHours(24));
        n.setMensaje("Recordatorio: su cita en Clínica Dental es el "
            + cita.getFechaHoraInicio() + ".");
        return n;
    }

    private void hayUnPendienteVencido(NotificacionCita pendiente) {
        when(notificaciones.findByEstadoCodeAndFechaObjetivoLessThanEqualOrderByFechaObjetivoAsc(
            EstadoNotificacion.PENDIENTE, AHORA)).thenReturn(List.of(pendiente));
    }

    @Test
    void programarParaUnaCitaCreaUnPendienteVeinticuatroHorasAntesConDestinoEmail() {
        Cita cita = cita(50, AHORA.plusDays(3));
        when(notificaciones.existsByCitaIdAndEstadoCode(50, EstadoNotificacion.PENDIENTE))
            .thenReturn(false);

        service.programarPara(cita);

        ArgumentCaptor<NotificacionCita> captor = ArgumentCaptor.forClass(NotificacionCita.class);
        verify(notificaciones).save(captor.capture());
        NotificacionCita programado = captor.getValue();
        assertThat(programado.getCita()).isSameAs(cita);
        assertThat(programado.getDestino()).isEqualTo("ana.mendoza@example.com");
        assertThat(programado.getCanal().getCode()).isEqualTo(CanalNotificacion.EMAIL);
        assertThat(programado.getEstado().getCode()).isEqualTo(EstadoNotificacion.PENDIENTE);
        assertThat(programado.getFechaObjetivo()).isEqualTo(cita.getFechaHoraInicio().minusHours(24));
        assertThat(programado.getMensaje()).contains("Clínica Dental").contains("Limpieza dental");
        assertThat(programado.getEnviadoEn()).isNull();
        assertThat(programado.getError()).isNull();
    }

    @Test
    void programarParaUnaCitaQueYaTienePendienteNoDuplicaFilas() {
        Cita cita = cita(50, AHORA.plusDays(3));
        when(notificaciones.existsByCitaIdAndEstadoCode(50, EstadoNotificacion.PENDIENTE))
            .thenReturn(true);

        service.programarPara(cita);

        verify(notificaciones, never()).save(any(NotificacionCita.class));
    }

    @Test
    void programarSinCitaPersistidaNoHaceNada() {
        service.programarPara(cita(null, AHORA.plusDays(3)));

        verifyNoInteractions(notificaciones);
    }

    @Test
    void procesarSinSmtpRegistraElRecordatorioComoSimuladoConLaHoraDeEnvio() {
        NotificacionCita pendiente = recordatorio(7, cita(50, AHORA.plusDays(1)));
        hayUnPendienteVencido(pendiente);
        when(correo.estaConfigurado()).thenReturn(false);

        List<RecordatorioResponse> procesados = service.procesar();

        assertThat(procesados).hasSize(1);
        assertThat(procesados.get(0).id()).isEqualTo(7);
        assertThat(procesados.get(0).estado()).isEqualTo(EstadoNotificacion.SIMULADA);
        assertThat(procesados.get(0).enviadoEn()).isEqualTo(AHORA);
        assertThat(procesados.get(0).error()).contains("SMTP no configurado");
        verify(correo, never()).enviar(anyString(), anyString(), anyString());
    }

    @Test
    void procesarSinSmtpDejaUnLogExplicitoDeLaSimulacion() {
        Logger logger = (Logger) LoggerFactory.getLogger(RecordatorioService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            hayUnPendienteVencido(recordatorio(7, cita(50, AHORA.plusDays(1))));
            when(correo.estaConfigurado()).thenReturn(false);

            service.procesar();

            assertThat(appender.list)
                .anyMatch(evento -> evento.getFormattedMessage().toLowerCase().contains("simulad"));
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void procesarConSmtpConfiguradoMarcaElRecordatorioComoEnviado() {
        NotificacionCita pendiente = recordatorio(7, cita(50, AHORA.plusDays(1)));
        hayUnPendienteVencido(pendiente);
        when(correo.estaConfigurado()).thenReturn(true);

        List<RecordatorioResponse> procesados = service.procesar();

        assertThat(procesados).hasSize(1);
        assertThat(procesados.get(0).estado()).isEqualTo(EstadoNotificacion.ENVIADA);
        assertThat(procesados.get(0).enviadoEn()).isEqualTo(AHORA);
        assertThat(procesados.get(0).error()).isNull();
        verify(correo).enviar(anyString(), anyString(), anyString());
    }

    @Test
    void procesarCuandoElEnvioFallaRegistraElErrorEnLaMismaFila() {
        NotificacionCita pendiente = recordatorio(7, cita(50, AHORA.plusDays(1)));
        hayUnPendienteVencido(pendiente);
        when(correo.estaConfigurado()).thenReturn(true);
        doThrow(new IllegalStateException("Connection refused"))
            .when(correo).enviar(anyString(), anyString(), anyString());

        List<RecordatorioResponse> procesados = service.procesar();

        assertThat(procesados).hasSize(1);
        assertThat(procesados.get(0).estado()).isEqualTo(EstadoNotificacion.FALLIDA);
        assertThat(procesados.get(0).error()).contains("Connection refused");
        assertThat(procesados.get(0).enviadoEn()).isNull();
    }

    @Test
    void procesarRecortaElErrorAlLimiteDeLaColumna() {
        NotificacionCita pendiente = recordatorio(7, cita(50, AHORA.plusDays(1)));
        hayUnPendienteVencido(pendiente);
        when(correo.estaConfigurado()).thenReturn(true);
        doThrow(new IllegalStateException("x".repeat(400)))
            .when(correo).enviar(anyString(), anyString(), anyString());

        List<RecordatorioResponse> procesados = service.procesar();

        assertThat(procesados.get(0).error()).hasSize(300);
    }

    @Test
    void procesarSoloTomaLosPendientesVencidosSegunElRelojInyectado() {
        hayUnPendienteVencido(recordatorio(7, cita(50, AHORA.plusDays(1))));
        when(correo.estaConfigurado()).thenReturn(false);

        service.procesar();

        verify(notificaciones).findByEstadoCodeAndFechaObjetivoLessThanEqualOrderByFechaObjetivoAsc(
            EstadoNotificacion.PENDIENTE, AHORA);
        verify(notificaciones).save(any(NotificacionCita.class));
    }

    @Test
    void procesarSinPendientesVencidosNoEscribeNiEnvia() {
        when(notificaciones.findByEstadoCodeAndFechaObjetivoLessThanEqualOrderByFechaObjetivoAsc(
            EstadoNotificacion.PENDIENTE, AHORA)).thenReturn(List.of());

        assertThat(service.procesar()).isEmpty();

        verify(notificaciones, never()).save(any(NotificacionCita.class));
        verifyNoInteractions(correo);
    }

    @Test
    void procesarEscribeElResultadoEnLaMismaFilaSinCrearOtra() {
        NotificacionCita pendiente = recordatorio(7, cita(50, AHORA.plusDays(1)));
        hayUnPendienteVencido(pendiente);
        when(correo.estaConfigurado()).thenReturn(false);

        service.procesar();

        ArgumentCaptor<NotificacionCita> guardadas = ArgumentCaptor.forClass(NotificacionCita.class);
        verify(notificaciones).save(guardadas.capture());
        assertThat(guardadas.getValue()).isSameAs(pendiente);
        assertThat(guardadas.getValue().getId()).isEqualTo(7);
    }

    @Test
    void cancelarPendientesMarcaComoCanceladosLosRecordatoriosDeLaCita() {
        NotificacionCita pendiente = recordatorio(7, cita(50, AHORA.plusDays(1)));
        when(notificaciones.findByCitaIdAndEstadoCode(50, EstadoNotificacion.PENDIENTE))
            .thenReturn(List.of(pendiente));

        service.cancelarPendientes(50);

        assertThat(pendiente.getEstado().getCode()).isEqualTo(EstadoNotificacion.CANCELADA);
        verify(notificaciones).save(pendiente);
    }

    @Test
    void cancelarPendientesSinPendientesNoEscribe() {
        when(notificaciones.findByCitaIdAndEstadoCode(50, EstadoNotificacion.PENDIENTE))
            .thenReturn(List.of());

        service.cancelarPendientes(50);

        verify(notificaciones, never()).save(any(NotificacionCita.class));
    }

    @Test
    void elHistorialDevuelveLosRecordatoriosDeLaCita() {
        when(citas.existsById(50)).thenReturn(true);
        when(notificaciones.findByCitaIdOrderByFechaObjetivoDesc(50))
            .thenReturn(List.of(recordatorio(7, cita(50, AHORA.plusDays(1)))));

        List<RecordatorioResponse> historial = service.historial(50);

        assertThat(historial).hasSize(1);
        assertThat(historial.get(0).idCita()).isEqualTo(50);
        assertThat(historial.get(0).canal()).isEqualTo(CanalNotificacion.EMAIL);
    }

    @Test
    void laBitacoraSinFiltrosDevuelveTodasLasFilas() {
        when(notificaciones.bitacora(null, null)).thenReturn(List.of(
            recordatorio(9, cita(51, AHORA.plusDays(2))),
            recordatorio(7, cita(50, AHORA.plusDays(1)))));

        List<RecordatorioResponse> bitacora = service.bitacora(null, null);

        assertThat(bitacora).hasSize(2);
        assertThat(bitacora.get(0).id()).isEqualTo(9);
        assertThat(bitacora.get(1).idCita()).isEqualTo(50);
    }

    @Test
    void laBitacoraFiltraPorEstadoNormalizadoContraElCatalogo() {
        when(notificaciones.bitacora(EstadoNotificacion.ENVIADA, null)).thenReturn(List.of(
            recordatorio(7, cita(50, AHORA.plusDays(1)))));

        List<RecordatorioResponse> bitacora = service.bitacora(" enviada ", null);

        assertThat(bitacora).hasSize(1);
        verify(notificaciones).bitacora(EstadoNotificacion.ENVIADA, null);
    }

    @Test
    void laBitacoraFiltraPorCita() {
        when(notificaciones.bitacora(null, 50)).thenReturn(List.of(
            recordatorio(7, cita(50, AHORA.plusDays(1)))));

        List<RecordatorioResponse> bitacora = service.bitacora(null, 50);

        assertThat(bitacora).hasSize(1);
        assertThat(bitacora.get(0).idCita()).isEqualTo(50);
        verify(notificaciones).bitacora(null, 50);
    }

    @Test
    void unEstadoEnBlancoNoFiltraLaBitacora() {
        when(notificaciones.bitacora(null, null)).thenReturn(List.of(
            recordatorio(7, cita(50, AHORA.plusDays(1)))));

        assertThat(service.bitacora("   ", null)).hasSize(1);

        verify(notificaciones).bitacora(null, null);
    }

    @Test
    void unEstadoDesconocidoEnLaBitacoraDevuelve400SinConsultarLaBitacora() {
        when(estados.findByCode("NO_EXISTE")).thenReturn(Optional.empty());

        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.bitacora("NO_EXISTE", null), ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Estado de recordatorio desconocido: NO_EXISTE");
        verifyNoInteractions(notificaciones);
    }

    @Test
    void elHistorialDeUnaCitaInexistenteDevuelve404() {
        when(citas.existsById(99)).thenReturn(false);

        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.historial(99), ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        verifyNoInteractions(notificaciones);
    }
}
