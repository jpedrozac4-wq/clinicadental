package com.clinica.dental.tratamiento.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Ficha del catalogo de tratamientos: descripcion y precio (S7). */
public record TratamientoRequest(
    @NotBlank(message = "el codigo es obligatorio")
    @Size(max = 20, message = "el codigo no puede superar 20 caracteres")
    String codigo,

    @NotBlank(message = "el nombre es obligatorio")
    @Size(max = 120, message = "el nombre no puede superar 120 caracteres")
    String nombre,

    @Size(max = 2000, message = "la descripcion no puede superar 2000 caracteres")
    String descripcion,

    @NotNull(message = "el precio es obligatorio")
    @DecimalMin(value = "0.01", message = "el precio debe ser mayor que cero")
    BigDecimal precio,

    @NotNull(message = "la duracion es obligatoria")
    @Min(value = 1, message = "la duracion debe ser al menos un minuto")
    Integer duracionMinutos
) {
}
