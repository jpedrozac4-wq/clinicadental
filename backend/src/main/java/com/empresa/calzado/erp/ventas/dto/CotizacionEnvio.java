package com.empresa.calzado.erp.ventas.dto;

import java.math.BigDecimal;

/**
 * Shipping quote returned by {@code POST /api/checkout/cotizar}.
 *
 * <p>Field names are Spanish because they are the JSON contract consumed verbatim by the
 * storefront ({@code frontend/js/config.js} -> {@code CHECKOUT_COTIZAR}).
 */
public record CotizacionEnvio(
    String zonaNombre,
    BigDecimal costoEnvio,
    boolean envioGratis,
    BigDecimal umbralCompraEnvioGratis
) {}
