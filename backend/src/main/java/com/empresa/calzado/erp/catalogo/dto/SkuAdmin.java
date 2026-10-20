package com.empresa.calzado.erp.catalogo.dto;

import java.math.BigDecimal;

/**
 * Admin-facing projection of a {@code CalzadoSku}, including the logical active flag and the
 * current sale price.
 *
 * <p>{@code idModeloColor} and {@code pesoGramos} are exposed because the back-office needs to
 * know which colour variant each SKU belongs to and to show the weight of the SKU it is about to
 * edit through {@code PUT /admin/skus/{id}}; in that request a {@code null} weight means "keep the
 * stored value", so the admin screen must read the current one first.
 */
public record SkuAdmin(
    Integer id,
    String sku,
    BigDecimal tallaEur,
    BigDecimal tallaCmMx,
    BigDecimal precioVentaVigente,
    Boolean active,
    Integer idModeloColor,
    Integer pesoGramos,
    String codigoBarrasEan
) {}
