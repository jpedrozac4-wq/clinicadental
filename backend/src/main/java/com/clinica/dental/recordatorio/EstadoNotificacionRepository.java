package com.clinica.dental.recordatorio;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EstadoNotificacionRepository extends JpaRepository<EstadoNotificacion, Integer> {

    Optional<EstadoNotificacion> findByCode(String code);
}
