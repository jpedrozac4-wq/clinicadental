package com.clinica.dental.usuario;

import com.clinica.dental.seguridad.Permission;
import com.clinica.dental.usuario.dto.PermisoResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** Catalogo de permisos que alimenta la matriz de roles (S18). */
@ExtendWith(MockitoExtension.class)
class PermisoServiceTest {

    @Mock private PermisoRepository permisos;

    @InjectMocks private PermisoService service;

    private static Permission permiso(String nombre, String modulo, String action, String descripcion) {
        Permission p = new Permission();
        p.setPermissionName(nombre);
        p.setModulo(modulo);
        p.setAction(action);
        p.setDescription(descripcion);
        return p;
    }

    @Test
    void catalogoPublicaNombreModuloAccionYDescripcion() {
        when(permisos.findAllByOrderByModuloAscPermissionNameAsc()).thenReturn(List.of(
            permiso("PACIENTE_VER", "PACIENTE", "READ", "Consultar pacientes"),
            permiso("USUARIOS_ADMIN", "SEGURIDAD", "UPDATE", "Administrar usuarios")));

        List<PermisoResponse> catalogo = service.catalogo();

        assertThat(catalogo).hasSize(2);
        assertThat(catalogo.get(0).permissionName()).isEqualTo("PACIENTE_VER");
        assertThat(catalogo.get(0).modulo()).isEqualTo("PACIENTE");
        assertThat(catalogo.get(0).action()).isEqualTo("READ");
        assertThat(catalogo.get(0).description()).isEqualTo("Consultar pacientes");
        assertThat(catalogo.get(1).permissionName()).isEqualTo("USUARIOS_ADMIN");
    }

    @Test
    void catalogoVacioDevuelveListaVaciaYNuncaNull() {
        when(permisos.findAllByOrderByModuloAscPermissionNameAsc()).thenReturn(List.of());

        assertThat(service.catalogo()).isNotNull().isEmpty();
    }
}
