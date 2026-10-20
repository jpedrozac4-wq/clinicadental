package com.empresa.calzado.erp.catalogo.dto;

import java.math.BigDecimal;

/**
 * Request body for {@code PATCH /modelos/{id}/outlet}.
 *
 * <p>A discount greater than 0 implies {@code esOutlet = true}; the service enforces that rule.
 */
public record OutletUpdateRequest(
    Boolean esOutlet,
    BigDecimal porcentajeDescuento
) {}
