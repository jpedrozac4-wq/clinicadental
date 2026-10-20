package com.empresa.calzado.erp.catalogo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ModeloColorRepository extends JpaRepository<ModeloColor, Integer> {
    List<ModeloColor> findByModeloIdOrderByIdAsc(Integer modeloId);

    Optional<ModeloColor> findByModeloIdAndColorId(Integer modeloId, Integer colorId);

    boolean existsByModeloIdAndColorId(Integer modeloId, Integer colorId);
}
