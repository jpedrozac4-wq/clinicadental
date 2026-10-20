package com.empresa.calzado.erp.ventas.dto;

import java.math.BigDecimal;

/**
 * One order line in the public order-consultation view.
 *
 * <p>Carries the storefront-facing SKU code instead of the internal {@code id_calzado_sku}, so the
 * public endpoint never exposes internal identifiers. Field names are Spanish camelCase, matching
 * the rest of the {@code ventas} JSON contract.
 */
public record OrdenConsultaLinea(
    String sku,
    Integer cantidad,
    BigDecimal precioUnitario,
    BigDecimal totalLinea
) {}
