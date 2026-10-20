package com.empresa.calzado.erp.catalogo.dto;

import java.math.BigDecimal;

/**
 * Admin-facing projection of a {@code talla_equivalencia} size row.
 *
 * <p>{@code active} is exposed so the back office can list both active and logically removed sizes;
 * the row is never physically deleted.
 */
public record TallaAdmin(
    Integer id,
    BigDecimal tallaEur,
    BigDecimal tallaUsHombre,
    BigDecimal tallaUsMujer,
    BigDecimal tallaUk,
    BigDecimal tallaCmMx,
    Boolean active
) {}
