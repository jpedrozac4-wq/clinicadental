package com.empresa.calzado.erp.logistica;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface DespachoEstadoRepository extends JpaRepository<DespachoEstado, Integer> {
    Optional<DespachoEstado> findByCode(String code);
}
