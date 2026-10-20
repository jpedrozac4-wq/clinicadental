package com.empresa.calzado.erp.catalogo.dto;

import java.math.BigDecimal;

/**
 * Request body to create a size.
 *
 * <p>{@code tallaEur} is the European size and the identity of the row: it is required, must be
 * greater than 0, must fit {@code precision = 3, scale = 1} (0.1..99.9 with at most one decimal)
 * and must be unique. It is the value the size part of an SKU code is derived from, so it can never
 * change after creation.
 *
 * <p>{@code tallaCmMx} is required and must be greater than 0. {@code tallaUsHombre},
 * {@code tallaUsMujer} and {@code tallaUk} are optional and may be null.
 */
public record TallaCreateRequest(
    BigDecimal tallaEur,
    BigDecimal tallaUsHombre,
    BigDecimal tallaUsMujer,
    BigDecimal tallaUk,
    BigDecimal tallaCmMx
) {}
