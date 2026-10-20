package com.clinica.dental.recordatorio;

import com.clinica.dental.cita.Cita;
import com.clinica.dental.cita.CitaRepository;
import com.clinica.dental.recordatorio.dto.RecordatorioResponse;
import com.clinica.dental.shared.ClinicaBusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Recordatorios de citas proximas (S5, S15).
 *
 * <p>Cada cita programada deja una fila {@code PENDIENTE} en {@code notificacion_cita} con fecha
 * objetivo 24 horas antes del inicio y el correo del paciente como destino. El proceso programado
 * despacha las filas vencidas y escribe el resultado real sobre la misma fila: {@code ENVIADA} con
 * su hora, {@code SIMULADA} cuando no hay SMTP configurado o {@code FALLIDA} con el texto del error.
 * La fila nunca se borra: es la bitacora que S5 exige consultable desde la interfaz.
 */
@Service
public class RecordatorioService {

    /** Antelacion con la que se avisa al paciente (S5). */
    static final Duration ANTELACION = Duration.ofHours(24);

    /** Canal por el que se entrega el recordatorio cuando la cita se programa. */
    static final String CANAL_DE_ENVIO = CanalNotificacion.EMAIL;

    static final String ASUNTO = "Recordatorio de su cita en Clínica Dental";

    /** Texto con el que se explica por que el envio no salio (S15). */
    static final String MENSAJE_SIMULADO =
        "SMTP no configurado: el envío quedó registrado como simulado.";

    /** Longitud de la columna {@code error} en {@code notificacion_cita}. */
    private static final int LONGITUD_MAXIMA_ERROR = 300;

    private static final DateTimeFormatter FORMATO_FECHA =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final Logger log = LoggerFactory.getLogger(RecordatorioService.class);

    private final NotificacionCitaRepository notificaciones;
    private final EstadoNotificacionRepository estados;
    private final CanalNotificacionRepository canales;
    private final CitaRepository citas;
    private final EnvioCorreo correo;
    private final Clock reloj;

    public RecordatorioService(NotificacionCitaRepository notificaciones,
                               EstadoNotificacionRepository estados,
                               CanalNotificacionRepository canales,
                               CitaRepository citas,
                               EnvioCorreo correo,
                               Clock reloj) {
        this.notificaciones = notificaciones;
        this.estados = estados;
        this.canales = canales;
        this.citas = citas;
        this.correo = correo;
        this.reloj = reloj;
    }

    /**
     * Deja programado el recordatorio de una cita recien agendada o reprogramada.
     *
     * <p>Si la cita ya tiene un pendiente no se crea otro: reprogramar regenera la fila de la cita
     * nueva sin duplicar pendientes.
     */
    @Transactional
    public void programarPara(Cita cita) {
        if (cita == null || cita.getId() == null) {
            return;
        }
        if (notificaciones.existsByCitaIdAndEstadoCode(cita.getId(), EstadoNotificacion.PENDIENTE)) {
            log.debug("La cita {} ya tiene un recordatorio pendiente: no se duplica", cita.getId());
            return;
        }

        NotificacionCita recordatorio = new NotificacionCita();
        recordatorio.setCita(cita);
        recordatorio.setCanal(canal(CANAL_DE_ENVIO));
        recordatorio.setEstado(estado(EstadoNotificacion.PENDIENTE));
        recordatorio.setDestino(cita.getPaciente().getEmail());
        recordatorio.setFechaObjetivo(cita.getFechaHoraInicio().minus(ANTELACION));
        recordatorio.setMensaje(mensajeDe(cita));
        notificaciones.save(recordatorio);

        log.info("Recordatorio programado para la cita {} con fecha objetivo {} y destino {}",
            cita.getId(), recordatorio.getFechaObjetivo(), recordatorio.getDestino());
    }

    /** Anula los pendientes de una cita cancelada: ya no aplica avisar al paciente (S6). */
    @Transactional
    public void cancelarPendientes(Integer idCita) {
        if (idCita == null) {
            return;
        }
        List<NotificacionCita> pendientes =
            notificaciones.findByCitaIdAndEstadoCode(idCita, EstadoNotificacion.PENDIENTE);
        if (pendientes.isEmpty()) {
            return;
        }
        EstadoNotificacion cancelada = estado(EstadoNotificacion.CANCELADA);
        pendientes.forEach(pendiente -> {
            pendiente.setEstado(cancelada);
            notificaciones.save(pendiente);
        });
        log.info("Se cancelaron {} recordatorio(s) pendientes de la cita {}", pendientes.size(), idCita);
    }

    /**
     * Despacha los recordatorios vencidos y devuelve la bitacora de este paso.
     *
     * @return las filas procesadas, ya con su estado final
     */
    @Transactional
    public List<RecordatorioResponse> procesar() {
        OffsetDateTime ahora = OffsetDateTime.now(reloj);
        List<NotificacionCita> vencidos =
            notificaciones.findByEstadoCodeAndFechaObjetivoLessThanEqualOrderByFechaObjetivoAsc(
                EstadoNotificacion.PENDIENTE, ahora);

        List<RecordatorioResponse> procesados = new ArrayList<>(vencidos.size());
        for (NotificacionCita recordatorio : vencidos) {
            despachar(recordatorio, ahora);
            procesados.add(RecordatorioResponse.desde(notificaciones.save(recordatorio)));
        }
        if (!procesados.isEmpty()) {
            log.info("Se despacharon {} recordatorio(s) de citas", procesados.size());
        }
        return procesados;
    }

    /** Historial de recordatorios de una cita, del mas reciente al mas antiguo (S5). */
    @Transactional(readOnly = true)
    public List<RecordatorioResponse> historial(Integer idCita) {
        if (!citas.existsById(idCita)) {
            throw new ClinicaBusinessException(HttpStatus.NOT_FOUND, "Cita no encontrada");
        }
        return notificaciones.findByCitaIdOrderByFechaObjetivoDesc(idCita).stream()
            .map(RecordatorioResponse::desde)
            .toList();
    }

    private void despachar(NotificacionCita recordatorio, OffsetDateTime ahora) {
        Integer idCita = recordatorio.getCita().getId();
        if (!correo.estaConfigurado()) {
            recordatorio.setEstado(estado(EstadoNotificacion.SIMULADA));
            recordatorio.setEnviadoEn(ahora);
            recordatorio.setError(MENSAJE_SIMULADO);
            log.warn("Recordatorio {} de la cita {} SIMULADO: no hay SMTP configurado "
                    + "(CLINICA_SMTP_HOST / spring.mail.host). Destino: {}",
                recordatorio.getId(), idCita, recordatorio.getDestino());
            return;
        }
        try {
            correo.enviar(recordatorio.getDestino(), ASUNTO, recordatorio.getMensaje());
            recordatorio.setEstado(estado(EstadoNotificacion.ENVIADA));
            recordatorio.setEnviadoEn(ahora);
            recordatorio.setError(null);
            log.info("Recordatorio {} de la cita {} enviado a {}",
                recordatorio.getId(), idCita, recordatorio.getDestino());
        } catch (RuntimeException ex) {
            recordatorio.setEstado(estado(EstadoNotificacion.FALLIDA));
            recordatorio.setEnviadoEn(null);
            recordatorio.setError(recortarError(ex.getMessage()));
            log.warn("Recordatorio {} de la cita {} FALLIDO: {}",
                recordatorio.getId(), idCita, ex.getMessage());
        }
    }

    /** Mensaje en espanol que recibe el paciente, con la fecha local de la cita. */
    private static String mensajeDe(Cita cita) {
        return "Recordatorio: su cita de " + cita.getTratamiento().getNombre()
            + " en Clínica Dental es el " + cita.getFechaHoraInicio().format(FORMATO_FECHA)
            + ". Si no puede asistir, comuníquese con recepción.";
    }

    private static String recortarError(String mensaje) {
        String texto = mensaje == null || mensaje.isBlank()
            ? "El envío del recordatorio falló sin detalle del proveedor"
            : mensaje.trim();
        return texto.length() <= LONGITUD_MAXIMA_ERROR
            ? texto
            : texto.substring(0, LONGITUD_MAXIMA_ERROR);
    }

    private EstadoNotificacion estado(String code) {
        return estados.findByCode(code)
            .orElseThrow(() -> new IllegalStateException(
                "Catalogo de estados de notificacion incompleto: " + code));
    }

    private CanalNotificacion canal(String code) {
        return canales.findByCode(code)
            .orElseThrow(() -> new IllegalStateException(
                "Catalogo de canales de notificacion incompleto: " + code));
    }
}
