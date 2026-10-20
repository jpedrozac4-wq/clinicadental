package com.empresa.calzado.erp.ventas;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface ReservaCheckoutRepository extends JpaRepository<ReservaCheckout, Integer> {

    /**
     * Reservations still ACTIVA, past their expiry, whose order is still PENDIENTE_PAGO.
     *
     * <p>The order-state predicate lives in the query on purpose. The sweep must never release the
     * stock of an order that was already paid, and pushing the condition into the query also avoids
     * loading each reservation's order in a second SELECT (N+1). A paid order is excluded here.
     */
    @Query("""
        SELECT r FROM ReservaCheckout r
        WHERE r.estado = 'ACTIVA'
          AND r.fechaExpiracion < :ahora
          AND r.orden.ordenEstado.code = 'PENDIENTE_PAGO'
        """)
    List<ReservaCheckout> findReservasActivasExpiradasDeOrdenPendiente(@Param("ahora") OffsetDateTime ahora);

    List<ReservaCheckout> findByOrdenId(Integer ordenId);
}
