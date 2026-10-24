package com.clinica.dental.reporte.dto;

import java.math.BigDecimal;

/**
 * Cobrado de un metodo de pago dentro del rango consultado (S21).
 *
 * <p>{@code total} sale de {@code SUM(pago.monto)} en la base: no se recalcula ni se redondea al
 * exponerlo, de modo que el reporte cuadra con los pagos registrados.
 */
public record IngresoPorMetodoPago(String metodo, BigDecimal total, Long cantidad) {
}
