package com.clinica.dental.pago.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Registro de un pago sobre una factura: monto, forma de pago y referencia opcional (S10).
 */
public record PagoRequest(
    @NotNull(message = "el monto es obligatorio")
    @DecimalMin(value = "0.01", message = "el monto debe ser mayor que cero")
    BigDecimal monto,

    @NotNull(message = "el metodo de pago es obligatorio")
    Integer idMetodoPago,

    String referencia
) {
}
