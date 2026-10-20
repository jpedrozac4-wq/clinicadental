package com.clinica.dental.cita.dto;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

/** Nueva fecha y hora de una cita; el dentista es opcional (S6). */
public record ReprogramarRequest(
    @NotNull(message = "la nueva fecha y hora son obligatorias")
    OffsetDateTime fechaHoraInicio,

    Integer idDentista
) {
}
