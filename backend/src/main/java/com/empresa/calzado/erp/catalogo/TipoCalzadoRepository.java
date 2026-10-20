package com.empresa.calzado.erp.catalogo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TipoCalzadoRepository extends JpaRepository<TipoCalzado, Integer> {
    List<TipoCalzado> findByActiveTrue();
}
