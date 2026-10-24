package com.clinica.dental.factura;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EstadoFacturaRepository extends JpaRepository<EstadoFactura, Integer> {

    Optional<EstadoFactura> findByCode(String code);
}
