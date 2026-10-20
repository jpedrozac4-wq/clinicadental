package com.empresa.calzado.erp.pos;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SucursalRepository extends JpaRepository<Sucursal, Integer> {
    List<Sucursal> findByActiveTrue();

    /** Active branches in a stable order, so the pickup picker never reorders between calls. */
    List<Sucursal> findByActiveTrueOrderByIdAsc();
}
