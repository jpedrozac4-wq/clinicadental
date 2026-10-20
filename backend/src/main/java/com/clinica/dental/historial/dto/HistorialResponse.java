package com.clinica.dental.historial.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** Historial clinico de un paciente: sus citas y sus tratamientos realizados (S3). */
public record HistorialResponse(
    Integer idPaciente,
    String paciente,
    List<CitaHistorial> citas,
    List<RealizadoHistorial> tratamientos
) {

    public record CitaHistorial(
        Integer id,
        OffsetDateTime fechaHoraInicio,
        String tratamiento,
        String estado,
        String motivo
    ) {
    }

    public record RealizadoHistorial(
        Integer id,
        OffsetDateTime fechaRealizacion,
        String tratamiento,
        String detalleProcedimiento,
        BigDecimal costo
    ) {
    }
}
