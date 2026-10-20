package com.empresa.calzado.erp.ventas;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface OrdenEstadoRepository extends JpaRepository<OrdenEstado, Integer> {
    Optional<OrdenEstado> findByCode(String code);
}
