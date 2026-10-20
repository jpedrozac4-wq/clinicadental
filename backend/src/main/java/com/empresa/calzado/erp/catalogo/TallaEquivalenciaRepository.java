package com.empresa.calzado.erp.catalogo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.List;

@Repository
public interface TallaEquivalenciaRepository extends JpaRepository<TallaEquivalencia, Integer> {
    List<TallaEquivalencia> findByActiveTrueOrderByTallaEurAsc();

    List<TallaEquivalencia> findAllByOrderByTallaEurAsc();

    boolean existsByTallaEur(BigDecimal tallaEur);
}
