package com.clinica.dental.cita;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EstadoCitaRepository extends JpaRepository<EstadoCita, Integer> {

    Optional<EstadoCita> findByCode(String code);
}
