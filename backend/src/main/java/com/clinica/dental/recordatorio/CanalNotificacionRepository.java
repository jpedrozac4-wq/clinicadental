package com.clinica.dental.recordatorio;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CanalNotificacionRepository extends JpaRepository<CanalNotificacion, Integer> {

    Optional<CanalNotificacion> findByCode(String code);
}
