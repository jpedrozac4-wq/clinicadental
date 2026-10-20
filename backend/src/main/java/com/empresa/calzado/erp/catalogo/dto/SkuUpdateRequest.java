package com.empresa.calzado.erp.catalogo.dto;

/**
 * Request body to update the editable attributes of an SKU.
 *
 * <p>A {@code null} field means "keep the current value"; an empty {@code codigoBarrasEan} clears
 * it to null.
 *
 * <p>The size is part of the SKU identity because the code embeds it, so it is not editable here.
 * Changing it would leave the printed or scanned code inconsistent with the row (the same
 * reasoning used by {@code verificarIdentidadEditable} for the model). Create the missing SKU and
 * logically deactivate the wrong one instead.
 */
public record SkuUpdateRequest(
    String codigoBarrasEan,
    Integer pesoGramos
) {}
