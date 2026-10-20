package com.clinica.dental.pago;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MetodoPagoRepository extends JpaRepository<MetodoPago, Integer> {

    Optional<MetodoPago> findByIdAndActiveTrue(Integer id);
}
