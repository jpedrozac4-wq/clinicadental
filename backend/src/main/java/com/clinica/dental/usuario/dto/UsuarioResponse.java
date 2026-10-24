package com.clinica.dental.usuario.dto;

import com.clinica.dental.seguridad.Role;
import com.clinica.dental.seguridad.SystemUser;

/**
 * Usuario expuesto por la API (S18).
 *
 * <p>Nunca incluye el hash de la contrasenia ni la sal: el padron solo publica identidad, estado y
 * rol. {@code nombre} repite {@code fullName} porque el selector de odontologos de la agenda (S20)
 * consume {@code {id, nombre}} desde este mismo listado.
 */
public record UsuarioResponse(
    Integer id,
    String username,
    String fullName,
    String nombre,
    String email,
    String rol,
    Boolean activo
) {

    public static UsuarioResponse desde(SystemUser usuario) {
        String nombreCompleto = usuario.getFullName();
        return new UsuarioResponse(
            usuario.getId(),
            usuario.getUsername(),
            nombreCompleto,
            nombreCompleto,
            usuario.getEmail(),
            rolPrincipal(usuario),
            usuario.getActive());
    }

    /**
     * El modelo admite varios roles por usuario, pero la interfaz administra uno solo: se publica el
     * primero en orden alfabetico para que la respuesta sea determinista.
     */
    private static String rolPrincipal(SystemUser usuario) {
        return usuario.getRoles().stream()
            .map(Role::getRoleName)
            .sorted()
            .findFirst()
            .orElse(null);
    }
}
