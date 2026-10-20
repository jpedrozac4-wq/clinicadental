package com.empresa.calzado.erp.ventas.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Public view of one order returned by {@code GET /api/checkout/ordenes}.
 *
 * <p>Deliberately not the {@code Orden} entity: the endpoint is public, so it must not leak
 * customer records, warehouse ids, coupon ids or any other internal relation. Only the fields the
 * shopper needs to recognise the order travel.
 *
 * <p>{@code fechaCreacion} is an ISO-8601 string, not a typed date: this matches the
 * {@code expiraEn} field already returned by {@code POST /api/checkout/iniciar} and keeps the
 * storefront contract independent of the Jackson date-serialization mode.
 */
public record OrdenConsulta(
    String numeroOrden,
    String estado,
    String estadoDescripcion,
    String tipoEntrega,
    BigDecimal subtotal,
    BigDecimal costoEnvio,
    BigDecimal descuentoAplicado,
    BigDecimal totalAPagar,
    String fechaCreacion,
    List<OrdenConsultaLinea> lineas
) {}
