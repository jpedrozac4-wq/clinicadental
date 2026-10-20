package com.empresa.calzado.erp.catalogo.dto;

/**
 * Request body to create an SKU for an existing colour variant and size.
 *
 * <p>The SKU code and the current sale price are derived by the service from the model; they are
 * deliberately not part of this request.
 *
 * <p>{@code codigoBarrasEan} is optional (8..14 digits when present) and {@code pesoGramos} is
 * optional and defaults to 800.
 */
public record SkuCreateRequest(
    Integer idModeloColor,
    Integer idTalla,
    String codigoBarrasEan,
    Integer pesoGramos
) {}
