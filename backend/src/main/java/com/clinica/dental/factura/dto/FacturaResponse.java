package com.clinica.dental.factura.dto;

import com.clinica.dental.factura.Factura;
import com.clinica.dental.factura.FacturaDetalle;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Comprobante con su detalle, total y saldo (S9, S10).
 *
 * <p>{@code totalPagado} se deriva del saldo, de modo que cliente y servidor nunca muestran dos
 * cuentas distintas del mismo comprobante.
 */
public record FacturaResponse(
    Integer id,
    String numero,
    Integer idPaciente,
    String paciente,
    OffsetDateTime fechaEmision,
    String estado,
    BigDecimal total,
    BigDecimal saldo,
    BigDecimal totalPagado,
    String observaciones,
    List<DetalleResponse> detalle
) {

    /** Linea del comprobante con el costo congelado del tratamiento realizado. */
    public record DetalleResponse(
        Integer id,
        Integer idRealizado,
        String descripcion,
        BigDecimal monto
    ) {

        public static DetalleResponse desde(FacturaDetalle detalle) {
            return new DetalleResponse(
                detalle.getId(),
                detalle.getRealizado() == null ? null : detalle.getRealizado().getId(),
                detalle.getDescripcion(),
                detalle.getMonto());
        }
    }

    public static FacturaResponse desde(Factura factura, List<FacturaDetalle> lineas) {
        return new FacturaResponse(
            factura.getId(),
            factura.getNumero(),
            factura.getPaciente().getId(),
            factura.getPaciente().getNombres() + " " + factura.getPaciente().getApellidos(),
            factura.getFechaEmision(),
            factura.getEstado().getCode(),
            factura.getTotal(),
            factura.getSaldo(),
            factura.getTotalPagado(),
            factura.getObservaciones(),
            lineas == null ? List.of() : lineas.stream().map(DetalleResponse::desde).toList());
    }
}
