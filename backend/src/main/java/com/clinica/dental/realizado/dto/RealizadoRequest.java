package com.clinica.dental.realizado.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

/**
 * Registro de un procedimiento: paciente, tratamiento, fecha y detalle del procedimiento (S8).
 * El costo no se envia: se toma del catalogo y queda congelado.
 */
public record RealizadoRequest(
    @NotNull(message = "el paciente es obligatorio")
    Integer idPaciente,

    @NotNull(message = "el tratamiento es obligatorio")
    Integer idTratamiento,

    @NotNull(message = "el dentista es obligatorio")
    Integer idDentista,

    @NotNull(message = "la fecha de realizacion es obligatoria")
    OffsetDateTime fechaRealizacion,

    @NotBlank(message = "el detalle del procedimiento es obligatorio")
    String detalleProcedimiento,

    String observaciones,

    Integer idCita
) {
}
