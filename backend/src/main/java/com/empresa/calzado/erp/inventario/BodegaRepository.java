package com.empresa.calzado.erp.inventario;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface BodegaRepository extends JpaRepository<Bodega, Integer> {
    Optional<Bodega> findFirstByEsDespachableWebTrueAndActiveTrue();
    List<Bodega> findByBodegaTipoCode(String code);
}
