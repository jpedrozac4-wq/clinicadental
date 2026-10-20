package com.empresa.calzado.erp.ventas;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.List;

@Repository
public interface OrdenRepository extends JpaRepository<Orden, Integer> {
    Optional<Orden> findByNumeroOrden(String numeroOrden);
    List<Orden> findByOrdenEstadoCode(String code);

    @Query("SELECT COALESCE(SUM(o.totalAPagar), 0) FROM Orden o WHERE o.ordenEstado.code = 'PAGADA' AND o.createdAt >= :desde")
    BigDecimal calcularVentasDelDia(OffsetDateTime desde);

    long countByOrdenEstadoCode(String code);

    /**
     * Session sales attribution: the POS channel plus the cashier plus the turn window.
     *
     * <p>{@code orden} has no column linking it to a cash session, and DDL is frozen, so a turn's
     * sales are recovered from these three facts. {@code PosSesionService} guarantees one open
     * session per cashier, which is exactly what keeps this attribution exact instead of
     * approximate. A POS order outside the window, from another cashier, or from the web channel is
     * excluded.
     */
    @Query("SELECT COALESCE(SUM(o.totalAPagar), 0) FROM Orden o "
        + "WHERE o.canalVenta = :canalVenta AND o.userCajero.id = :cajeroId "
        + "AND o.createdAt >= :desde AND o.createdAt <= :hasta")
    BigDecimal sumTotalVentasAtribuidas(
        @Param("canalVenta") String canalVenta,
        @Param("cajeroId") Integer cajeroId,
        @Param("desde") OffsetDateTime desde,
        @Param("hasta") OffsetDateTime hasta);

    /**
     * Cash portion of the same attribution: the gross amount of the payment transactions whose
     * method code is one of the cash codes. Non-cash sales are excluded here even though they still
     * count in {@link #sumTotalVentasAtribuidas}.
     */
    @Query("SELECT COALESCE(SUM(p.montoBruto), 0) FROM Orden o, Factura f, PagoTransaccion p "
        + "WHERE f.orden = o AND p.factura = f "
        + "AND o.canalVenta = :canalVenta AND o.userCajero.id = :cajeroId "
        + "AND o.createdAt >= :desde AND o.createdAt <= :hasta "
        + "AND p.pagoMetodo.code IN :codigosEfectivo")
    BigDecimal sumVentasEfectivoAtribuidas(
        @Param("canalVenta") String canalVenta,
        @Param("cajeroId") Integer cajeroId,
        @Param("desde") OffsetDateTime desde,
        @Param("hasta") OffsetDateTime hasta,
        @Param("codigosEfectivo") Collection<String> codigosEfectivo);
}
