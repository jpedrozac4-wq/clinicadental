package com.clinica.dental.usuario;

import com.clinica.dental.seguridad.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/** Acceso al catalogo de permisos granulares (S18). */
public interface PermisoRepository extends JpaRepository<Permission, Integer> {

    /** Catalogo completo agrupado por modulo y, dentro del modulo, por nombre. */
    List<Permission> findAllByOrderByModuloAscPermissionNameAsc();

    /** Resuelve la matriz que llega desde la pantalla de roles. */
    List<Permission> findByPermissionNameIn(Collection<String> permissionNames);
}
