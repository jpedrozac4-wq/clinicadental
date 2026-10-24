package com.clinica.dental.usuario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Cuerpo de alta y edicion de un rol (S18).
 *
 * <p>{@code permissions} es la matriz completa: el servicio reemplaza la asignacion anterior en
 * lugar de acumularla, que es lo que la pantalla de permisos espera al guardar.
 */
public record RolRequest(
    @NotBlank(message = "el nombre del rol es obligatorio")
    @Size(max = 60, message = "el nombre del rol no puede superar 60 caracteres")
    String roleName,

    @Size(max = 200, message = "la descripcion no puede superar 200 caracteres")
    String description,

    List<String> permissions
) {
}
