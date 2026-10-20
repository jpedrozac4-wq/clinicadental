package com.empresa.calzado.erp.soporte;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface TicketEstadoRepository extends JpaRepository<TicketEstado, Integer> {
    Optional<TicketEstado> findByCode(String code);
    List<TicketEstado> findByActiveTrue();
}
