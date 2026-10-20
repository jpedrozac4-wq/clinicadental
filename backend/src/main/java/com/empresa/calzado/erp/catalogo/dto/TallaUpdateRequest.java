package com.empresa.calzado.erp.catalogo.dto;

import java.math.BigDecimal;

/**
 * Request body to update the editable attributes of a size.
 *
 * <p>A {@code null} field means "keep the current value". {@code tallaUsHombre},
 * {@code tallaUsMujer} and {@code tallaUk} are optional; {@code tallaCmMx} is required and must be
 * greater than 0.
 *
 * <p>{@code tallaEur} is deliberately absent: the size part of an SKU code already printed or
 * scanned was derived from it, so it must never change. Create a new size and logically deactivate
 * the wrong one instead.
 */
public record TallaUpdateRequest(
    BigDecimal tallaUsHombre,
    BigDecimal tallaUsMujer,
    BigDecimal tallaUk,
    BigDecimal tallaCmMx
) {}
