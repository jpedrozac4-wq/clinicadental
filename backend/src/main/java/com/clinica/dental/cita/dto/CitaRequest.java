package com.clinica.dental.cita.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/** Datos para programar una cita: paciente, fecha, hora y tipo de tratamiento (S4). */
public record CitaRequest(
    @NotNull(message = "el paciente es obligatorio")
    Integer idPaciente,

    @NotNull(message = "el tratamiento es obligatorio")
    Integer idTratamiento,

    @NotNull(message = "el dentista es obligatorio")
    Integer idDentista,

    @NotNull(message = "la fecha y hora de inicio son obligatorias")
    OffsetDateTime fechaHoraInicio,

    @Size(max = 200, message = "el motivo no puede superar 200 caracteres")
    String motivo
) {
}
