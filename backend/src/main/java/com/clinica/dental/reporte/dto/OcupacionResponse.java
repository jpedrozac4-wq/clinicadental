package com.clinica.dental.reporte.dto;

import java.util.List;

/**
 * Reporte de ocupacion de la agenda (S21).
 *
 * <p>Sin citas en el rango devuelve las dos listas vacias y {@code totalCitas} en cero.
 */
public record OcupacionResponse(
    List<OcupacionPorEstado> porEstado,
    List<OcupacionPorOdontologo> porOdontologo,
    Long totalCitas
) {
}
