package com.empresa.calzado.erp.inventario;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface MovimientoStockTipoRepository extends JpaRepository<MovimientoStockTipo, Integer> {
    Optional<MovimientoStockTipo> findByCode(String code);
}
