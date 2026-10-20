package com.empresa.calzado.erp.facturacion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface PagoMetodoRepository extends JpaRepository<PagoMetodo, Integer> {
    Optional<PagoMetodo> findByCode(String code);
}
