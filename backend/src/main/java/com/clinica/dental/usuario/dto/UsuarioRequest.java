package com.clinica.dental.usuario.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Datos de alta y edicion de un usuario del sistema (S18).
 *
 * <p>El mismo cuerpo sirve para {@code POST} y {@code PUT}. La contrasenia es obligatoria en el
 * alta y opcional en la edicion: si no llega, se conserva el hash vigente.
 */
public record UsuarioRequest(
    @NotBlank(message = "el username es obligatorio")
    @Size(max = 80, message = "el username no puede superar 80 caracteres")
    String username,

    @NotBlank(message = "el nombre completo es obligatorio")
    @Size(max = 120, message = "el nombre completo no puede superar 120 caracteres")
    String fullName,

    @NotBlank(message = "el email es obligatorio")
    @Email(message = "el email no tiene un formato valido")
    @Size(max = 100, message = "el email no puede superar 100 caracteres")
    String email,

    // BCrypt trabaja sobre 72 bytes: un secreto mas largo se truncaria en silencio.
    @Size(max = 72, message = "la contrasenia no puede superar 72 caracteres")
    String password,

    @NotBlank(message = "el rol es obligatorio")
    String rol
) {
}
