package com.empresa.calzado.erp.pos;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DevolucionRepository extends JpaRepository<Devolucion, Integer> {
    /**
     * Previous returns of the same invoice, used both to number the new document and to accumulate
     * how much of each SKU was already returned (the no-over-return guard).
     */
    List<Devolucion> findByFacturaOrigenId(Integer facturaId);
}
