package com.clinica.dental.usuario;

import com.clinica.dental.usuario.dto.PermisoResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Catalogo de permisos granulares que la pantalla de roles ofrece como casillas (S18).
 *
 * <p>Es de solo lectura: los permisos los define el esquema y ningun endpoint los crea ni los edita.
 */
@Service
public class PermisoService {

    private final PermisoRepository permisos;

    public PermisoService(PermisoRepository permisos) {
        this.permisos = permisos;
    }

    @Transactional(readOnly = true)
    public List<PermisoResponse> catalogo() {
        return permisos.findAllByOrderByModuloAscPermissionNameAsc().stream()
            .map(PermisoResponse::desde)
            .toList();
    }
}
