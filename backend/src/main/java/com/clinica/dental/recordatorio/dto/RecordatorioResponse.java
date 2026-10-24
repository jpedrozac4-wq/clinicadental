package com.clinica.dental.recordatorio.dto;

import com.clinica.dental.recordatorio.NotificacionCita;

import java.time.OffsetDateTime;

/** Fila de la bitacora de recordatorios expuesta a la interfaz (S5, S15). */
public record RecordatorioResponse(
    Integer id,
    Integer idCita,
    OffsetDateTime fechaHoraCita,
    String canal,
    String estado,
    String destino,
    OffsetDateTime fechaObjetivo,
    String mensaje,
    OffsetDateTime enviadoEn,
    String error
) {

    public static RecordatorioResponse desde(NotificacionCita n) {
        return new RecordatorioResponse(
            n.getId(),
            n.getCita().getId(),
            n.getCita().getFechaHoraInicio(),
            n.getCanal().getCode(),
            n.getEstado().getCode(),
            n.getDestino(),
            n.getFechaObjetivo(),
            n.getMensaje(),
            n.getEnviadoEn(),
            n.getError());
    }
}
