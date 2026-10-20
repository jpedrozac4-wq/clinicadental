package com.empresa.calzado.erp.ventas;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TarifarioEnvioRepository extends JpaRepository<TarifarioEnvio, Integer> {
    List<TarifarioEnvio> findByActiveTrue();
}
