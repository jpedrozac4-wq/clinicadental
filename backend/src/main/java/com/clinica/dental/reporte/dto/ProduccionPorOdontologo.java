package com.clinica.dental.reporte.dto;

import java.math.BigDecimal;

/**
 * Procedimientos realizados por un odontologo en el rango consultado (S21).
 *
 * <p>{@code importe} es la suma del costo congelado de cada tratamiento realizado, no el precio
 * vigente del catalogo.
 */
public record ProduccionPorOdontologo(
    Integer idDentista,
    String odontologo,
    Long cantidad,
    BigDecimal importe
) {
}
