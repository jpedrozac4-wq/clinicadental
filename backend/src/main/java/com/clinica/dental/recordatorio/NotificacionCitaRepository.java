package com.clinica.dental.recordatorio;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;

public interface NotificacionCitaRepository extends JpaRepository<NotificacionCita, Integer> {

    /** Cola de despacho: pendientes cuya fecha objetivo ya vencio (indice {@code idx_notificacion_cola}). */
    List<NotificacionCita> findByEstadoCodeAndFechaObjetivoLessThanEqualOrderByFechaObjetivoAsc(
        String code, OffsetDateTime momento);

    /** Historial de recordatorios de una cita, del mas reciente al mas antiguo. */
    List<NotificacionCita> findByCitaIdOrderByFechaObjetivoDesc(Integer idCita);

    List<NotificacionCita> findByCitaIdAndEstadoCode(Integer idCita, String code);

    /** Evita duplicar el pendiente de una misma cita al reprogramar. */
    boolean existsByCitaIdAndEstadoCode(Integer idCita, String code);
}
