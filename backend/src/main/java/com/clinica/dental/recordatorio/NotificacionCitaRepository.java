package com.clinica.dental.recordatorio;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface NotificacionCitaRepository extends JpaRepository<NotificacionCita, Integer> {

    /** Cola de despacho: pendientes cuya fecha objetivo ya vencio (indice {@code idx_notificacion_cola}). */
    List<NotificacionCita> findByEstadoCodeAndFechaObjetivoLessThanEqualOrderByFechaObjetivoAsc(
        String code, OffsetDateTime momento);

    /** Historial de recordatorios de una cita, del mas reciente al mas antiguo. */
    List<NotificacionCita> findByCitaIdOrderByFechaObjetivoDesc(Integer idCita);

    /**
     * Bitacora completa de recordatorios, del mas reciente al mas antiguo segun la fecha objetivo.
     *
     * <p>Ambos criterios son opcionales: {@code null} desactiva el filtro correspondiente, de modo
     * que la interfaz puede pedir la bitacora entera, solo un estado, solo una cita o la combinacion.
     */
    @Query("""
        SELECT n FROM NotificacionCita n
        WHERE (:estado IS NULL OR n.estado.code = :estado)
          AND (:idCita IS NULL OR n.cita.id = :idCita)
        ORDER BY n.fechaObjetivo DESC, n.id DESC
        """)
    List<NotificacionCita> bitacora(@Param("estado") String estado, @Param("idCita") Integer idCita);

    List<NotificacionCita> findByCitaIdAndEstadoCode(Integer idCita, String code);

    /** Evita duplicar el pendiente de una misma cita al reprogramar. */
    boolean existsByCitaIdAndEstadoCode(Integer idCita, String code);
}
