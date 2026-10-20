package com.clinica.dental.paciente.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Datos personales que el requerimiento pide capturar del paciente (S1).
 * Se usa tanto para el alta como para la edicion (S2), porque la ficha es la misma.
 */
public record PacienteRequest(
    @NotBlank(message = "los nombres son obligatorios")
    @Size(max = 80, message = "los nombres no pueden superar 80 caracteres")
    String nombres,

    @NotBlank(message = "los apellidos son obligatorios")
    @Size(max = 80, message = "los apellidos no pueden superar 80 caracteres")
    String apellidos,

    @NotNull(message = "la fecha de nacimiento es obligatoria")
    @Past(message = "la fecha de nacimiento debe ser anterior a hoy")
    LocalDate fechaNacimiento,

    @NotBlank(message = "el telefono es obligatorio")
    @Size(max = 25, message = "el telefono no puede superar 25 caracteres")
    String telefono,

    @NotBlank(message = "la direccion es obligatoria")
    @Size(max = 200, message = "la direccion no puede superar 200 caracteres")
    String direccion,

    @NotBlank(message = "el email es obligatorio")
    @Email(message = "el email no tiene un formato valido")
    @Size(max = 120, message = "el email no puede superar 120 caracteres")
    String email
) {
}
