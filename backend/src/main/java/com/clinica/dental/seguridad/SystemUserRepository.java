package com.clinica.dental.seguridad;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface SystemUserRepository extends JpaRepository<SystemUser, Integer> {
    Optional<SystemUser> findByUsername(String username);
    Optional<SystemUser> findByEmail(String email);

    /**
     * Padron filtrable del modulo de usuarios (S18, S20).
     *
     * <p>Todos los filtros son opcionales: {@code rol} llega ya en su forma canonica
     * ({@code ROLE_*}), {@code activo} traduce el estado pedido y {@code q} llega como patron
     * {@code %texto%} en minusculas para comparar username, nombre completo y email sin distinguir
     * mayusculas.
     */
    @Query("""
        SELECT DISTINCT u FROM SystemUser u
        WHERE (:rol IS NULL
               OR EXISTS (SELECT r FROM u.roles r WHERE r.roleName = :rol))
          AND (:activo IS NULL OR u.active = :activo)
          AND (:q IS NULL
               OR LOWER(u.username) LIKE :q
               OR LOWER(u.fullName) LIKE :q
               OR LOWER(u.email) LIKE :q)
        ORDER BY u.fullName ASC, u.username ASC
        """)
    List<SystemUser> buscar(@Param("rol") String rol,
                            @Param("activo") Boolean activo,
                            @Param("q") String q);

    /** Usuarios activos que tienen el rol indicado: sostiene la invariante del ultimo administrador. */
    @Query("""
        SELECT COUNT(DISTINCT u) FROM SystemUser u JOIN u.roles r
        WHERE r.roleName = :rol AND u.active = true
        """)
    long contarActivosPorRol(@Param("rol") String rol);

    /** Usuarios asignados a un rol, activos o no: un rol con historial no se elimina (S18). */
    @Query("""
        SELECT COUNT(DISTINCT u) FROM SystemUser u JOIN u.roles r
        WHERE r.roleName = :rol
        """)
    long contarPorRol(@Param("rol") String rol);
}
