package com.clinica.dental.reporte.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Reporte de produccion por odontologo (S21).
 *
 * <p>Sin procedimientos en el rango devuelve la lista vacia y los dos totales en cero.
 */
public record ProduccionResponse(
    List<ProduccionPorOdontologo> porOdontologo,
    Long totalProcedimientos,
    BigDecimal totalImporte
) {
}
