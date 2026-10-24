package com.clinica.dental.pago.dto;

import com.clinica.dental.pago.Pago;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Pago registrado sobre un comprobante (S10). */
public record PagoResponse(
    Integer id,
    Integer idFactura,
    OffsetDateTime fechaPago,
    BigDecimal monto,
    Integer idMetodoPago,
    String metodoPago,
    String referencia
) {

    public static PagoResponse desde(Pago pago) {
        return new PagoResponse(
            pago.getId(),
            pago.getFactura().getId(),
            pago.getFechaPago(),
            pago.getMonto(),
            pago.getMetodoPago().getId(),
            pago.getMetodoPago().getCode(),
            pago.getReferencia());
    }
}
