package com.empresa.calzado.erp.soporte;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface TicketSoporteRepository extends JpaRepository<TicketSoporte, Integer> {
    long countByTicketEstadoCodeNotIn(List<String> codes);
    List<TicketSoporte> findByTicketEstadoCodeInAndFechaUltimaInteraccionBefore(List<String> codes, OffsetDateTime limite);
}
