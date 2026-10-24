package com.clinica.dental.auditoria;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Acceso al catalogo de acciones auditables (S19). */
public interface LogAccionRepository extends JpaRepository<LogAccion, Integer> {

    Optional<LogAccion> findByCode(String code);
}
