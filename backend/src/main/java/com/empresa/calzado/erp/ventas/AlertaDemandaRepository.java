package com.empresa.calzado.erp.ventas;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AlertaDemandaRepository extends JpaRepository<AlertaDemanda, Integer> {

    /**
     * Dedup guard for {@code POST /api/checkout/alerta-demanda}: the same shopper asking to be
     * notified about the same SKU twice must not create a second row.
     *
     * <p>The table has no unique constraint on the pair, so this finder is the idempotency check.
     */
    boolean existsByCalzadoSkuIdAndEmailCliente(Integer idCalzadoSku, String emailCliente);
}
