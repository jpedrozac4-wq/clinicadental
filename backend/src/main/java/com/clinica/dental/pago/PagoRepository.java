package com.clinica.dental.pago;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface PagoRepository extends JpaRepository<Pago, Integer> {

    /** Pagos de un comprobante en orden cronologico. */
    List<Pago> findByFacturaIdOrderByFechaPagoAsc(Integer idFactura);

    /**
     * Suma de los pagos ya registrados de un comprobante.
     *
     * <p>El tope de S10 se controla sobre la suma real de pagos, no sobre un saldo recordado: asi
     * dos pagos consecutivos no pueden superar el total de la factura.
     */
    @Query("SELECT SUM(p.monto) FROM Pago p WHERE p.factura.id = :idFactura")
    BigDecimal sumarPagos(@Param("idFactura") Integer idFactura);
}
