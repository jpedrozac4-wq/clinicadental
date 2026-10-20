package com.empresa.calzado.erp.pos;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Cash registers. Only active registers are exposed to the POS terminal picker, in a stable id
 * order so the list never reorders between calls.
 */
@Repository
public interface CajaPosRepository extends JpaRepository<CajaPos, Integer> {
    List<CajaPos> findByActiveTrueOrderByIdAsc();
}
