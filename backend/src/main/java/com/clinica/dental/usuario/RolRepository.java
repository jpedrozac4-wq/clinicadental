package com.clinica.dental.usuario;

import com.clinica.dental.seguridad.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Acceso a los roles del sistema (S18). */
public interface RolRepository extends JpaRepository<Role, Integer> {

    Optional<Role> findByRoleName(String roleName);

    /** Todos los roles ordenados por nombre: es el orden en que la pantalla los muestra. */
    List<Role> findAllByOrderByRoleNameAsc();
}
