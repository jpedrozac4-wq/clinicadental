package com.empresa.calzado.erp.facturacion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface FacturaRepository extends JpaRepository<Factura, Integer> {

    /**
     * The document types that represent a SALE. A NOTA_CREDITO is never one of them: it is a refund
     * document that credits a sale, and it shares {@code id_orden} with the sale it credits.
     *
     * <p>Every query that resolves the document a return belongs to must use this list. A return
     * resolved against a credit note does two harms at once: it refunds the customer again past what
     * was sold, and it defeats the accumulated over-return guard, which is keyed on the resolved
     * origin document and therefore ends up counting returns per credit note instead of per sale.
     */
    String TIPOS_VENTA_JPQL = "'FACTURA', 'BOLETA'";

    /**
     * The SALE document with that correlative number, never a credit note.
     */
    @Query("SELECT f FROM Factura f WHERE f.numeroCorrelativo = :numeroCorrelativo "
        + "AND f.facturaTipo.code IN (" + TIPOS_VENTA_JPQL + ")")
    Optional<Factura> findVentaByNumeroCorrelativo(@Param("numeroCorrelativo") String numeroCorrelativo);

    /**
     * Every SALE document of an order (FACTURA or BOLETA), oldest first.
     *
     * <p>An order can own more than one {@code factura} row: a credit note is written with the same
     * {@code id_orden} as the sale it credits, so a returned order has its sale document plus one
     * NOTA_CREDITO per return. A single-result lookup by order therefore stopped being unique and
     * the second return by order number failed with {@code NonUniqueResultException} (HTTP 500),
     * even though RN-POS-05 accepts the order number as an identifier.
     */
    @Query("SELECT f FROM Factura f WHERE f.orden.id = :ordenId "
        + "AND f.facturaTipo.code IN (" + TIPOS_VENTA_JPQL + ") ORDER BY f.id ASC")
    List<Factura> findVentasByOrdenId(@Param("ordenId") Integer ordenId);

    /**
     * Every correlative that starts with {@code prefijo} (for example {@code FAC-}).
     *
     * <p>The caller parses only all-digit suffixes and ignores the rest, so the legacy hexadecimal
     * correlativos written by the defect do not disturb the sequence. Reading the candidates and
     * reducing them in Java keeps the rule testable without a database; the declared trade-off of
     * Decision 34 (the type-row lock serializes same-type invoices) already bounds the load this
     * runs under.
     */
    @Query("SELECT f.numeroCorrelativo FROM Factura f WHERE f.numeroCorrelativo LIKE CONCAT(:prefijo, '%')")
    List<String> findCorrelativosPorPrefijo(@Param("prefijo") String prefijo);
}
