package com.empresa.calzado.erp.catalogo.dto;

/**
 * Request body for the logical removal / restore of an SKU. Only the active flag changes; the row
 * is never deleted.
 */
public record SkuActiveUpdateRequest(Boolean active) {}
