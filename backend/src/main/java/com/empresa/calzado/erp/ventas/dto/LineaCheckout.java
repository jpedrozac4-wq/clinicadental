package com.empresa.calzado.erp.ventas.dto;

/**
 * One requested cart line for order creation.
 *
 * <p>{@code cantidad} is boxed so a JSON line with a missing quantity is distinguishable from a
 * zero quantity and can be rejected with a 400 instead of silently becoming an empty line.
 */
public record LineaCheckout(String sku, Integer cantidad) {}
