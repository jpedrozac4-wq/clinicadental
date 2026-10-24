package com.clinica.dental.reporte.dto;

import java.math.BigDecimal;

/**
 * Cobrado de un mes calendario ({@code mes} en formato {@code YYYY-MM}), dentro del rango consultado.
 */
public record IngresoMes(String mes, BigDecimal total) {
}
