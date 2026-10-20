package com.clinica.dental.realizado.dto;

import com.clinica.dental.realizado.TratamientoRealizado;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record RealizadoResponse(
    Integer id,
    Integer idPaciente,
    String paciente,
    Integer idTratamiento,
    String tratamiento,
    Integer idCita,
    Integer idDentista,
    String dentista,
    OffsetDateTime fechaRealizacion,
    String detalleProcedimiento,
    String observaciones,
    BigDecimal costo
) {

    public static RealizadoResponse desde(TratamientoRealizado r) {
        return new RealizadoResponse(
            r.getId(),
            r.getPaciente().getId(),
            r.getPaciente().getNombres() + " " + r.getPaciente().getApellidos(),
            r.getTratamiento().getId(),
            r.getTratamiento().getNombre(),
            r.getCita() == null ? null : r.getCita().getId(),
            r.getDentista().getId(),
            r.getDentista().getFullName(),
            r.getFechaRealizacion(),
            r.getDetalleProcedimiento(),
            r.getObservaciones(),
            r.getCosto());
    }
}
