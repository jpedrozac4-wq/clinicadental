package com.clinica.dental.reporte.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Reporte de ingresos de S21.
 *
 * <p>Sin pagos en el rango devuelve {@code totalCobrado} en cero y las dos listas vacias, nunca
 * {@code null}: el frontend lo renderiza directamente.
 */
public record IngresosResponse(
    BigDecimal totalCobrado,
    List<IngresoPorMetodoPago> porMetodoPago,
    List<IngresoMes> porMes
) {
}
