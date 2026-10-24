package com.clinica.dental.usuario.dto;

import com.clinica.dental.seguridad.Permission;
import com.clinica.dental.seguridad.Role;

import java.util.List;

/** Rol con su matriz de permisos vigente (S18). */
public record RolResponse(
    Integer id,
    String roleName,
    String description,
    List<String> permissions
) {

    public static RolResponse desde(Role rol) {
        List<String> nombres = rol.getPermissions().stream()
            .map(Permission::getPermissionName)
            .sorted()
            .toList();
        return new RolResponse(rol.getId(), rol.getRoleName(), rol.getDescription(), nombres);
    }
}
