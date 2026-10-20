package com.empresa.calzado.erp.ventas.dto;

import java.math.BigDecimal;

/**
 * Coupon validation result returned by {@code POST /api/checkout/cupon/validar}.
 *
 * <p>Business rejections travel as HTTP 200 with {@code valido=false} and a Spanish
 * (accent-free) {@code mensaje}, because the storefront shows that text to the shopper. Only a
 * blank coupon code is an HTTP 400.
 */
public record ValidacionCupon(
    boolean valido,
    String codigoCupon,
    String tipoDescuento,
    BigDecimal descuento,
    String mensaje
) {}
