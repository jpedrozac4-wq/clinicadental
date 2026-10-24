package com.clinica.dental.usuario.dto;

import com.clinica.dental.seguridad.Permission;

/**
 * Entrada del catalogo de permisos (S18).
 *
 * <p>La pantalla de roles agrupa visualmente por {@code modulo}; el catalogo se publica como lista
 * plana ordenada por modulo y accion para que el cliente pueda renderizarla sin transformarla.
 */
public record PermisoResponse(
    String permissionName,
    String modulo,
    String action,
    String description
) {

    public static PermisoResponse desde(Permission permiso) {
        return new PermisoResponse(
            permiso.getPermissionName(),
            permiso.getModulo(),
            permiso.getAction(),
            permiso.getDescription());
    }
}
