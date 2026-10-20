package com.clinica.dental.pago;

import com.clinica.dental.factura.EstadoFactura;
import com.clinica.dental.factura.EstadoFacturaRepository;
import com.clinica.dental.factura.Factura;
import com.clinica.dental.factura.FacturaRepository;
import com.clinica.dental.pago.dto.PagoRequest;
import com.clinica.dental.pago.dto.PagoResponse;
import com.clinica.dental.shared.ClinicaBusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Registro de pagos totales y parciales sobre un comprobante (S10).
 *
 * <p>El saldo se recalcula en cada pago como {@code total - suma de pagos} y arrastra el estado de la
 * factura: {@code PAGO_PARCIAL} mientras quede saldo y {@code PAGADA} cuando llega a cero. La suma de
 * pagos nunca puede superar el total del comprobante, y una factura anulada no admite cobros.
 */
@Service
public class PagoService {

    private final PagoRepository pagos;
    private final MetodoPagoRepository metodos;
    private final FacturaRepository facturas;
    private final EstadoFacturaRepository estados;

    public PagoService(PagoRepository pagos, MetodoPagoRepository metodos,
                       FacturaRepository facturas, EstadoFacturaRepository estados) {
        this.pagos = pagos;
        this.metodos = metodos;
        this.facturas = facturas;
        this.estados = estados;
    }

    @Transactional
    public PagoResponse registrar(Integer idFactura, PagoRequest request) {
        Factura factura = buscar(idFactura);
        if (EstadoFactura.ANULADA.equals(factura.getEstado().getCode())) {
            throw new ClinicaBusinessException(HttpStatus.CONFLICT,
                "Una factura anulada no admite pagos");
        }
        if (request == null || request.monto() == null
            || request.monto().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ClinicaBusinessException("El monto del pago debe ser mayor que cero");
        }

        MetodoPago metodo = metodos.findByIdAndActiveTrue(request.idMetodoPago())
            .orElseThrow(() -> new ClinicaBusinessException(HttpStatus.NOT_FOUND,
                "Método de pago no encontrado o inactivo"));

        BigDecimal pagado = totalPagado(idFactura);
        BigDecimal acumulado = pagado.add(request.monto());
        if (acumulado.compareTo(factura.getTotal()) > 0) {
            throw new ClinicaBusinessException(HttpStatus.CONFLICT,
                "El total de pagos no puede superar el total de la factura");
        }

        Pago pago = new Pago();
        pago.setFactura(factura);
        pago.setFechaPago(OffsetDateTime.now());
        pago.setMonto(request.monto());
        pago.setMetodoPago(metodo);
        pago.setReferencia(recortar(request.referencia()));
        Pago guardado = pagos.save(pago);

        actualizarSaldoYEstado(factura, acumulado);
        return PagoResponse.desde(guardado);
    }

    @Transactional(readOnly = true)
    public List<PagoResponse> listar(Integer idFactura) {
        buscar(idFactura);
        return pagos.findByFacturaIdOrderByFechaPagoAsc(idFactura).stream()
            .map(PagoResponse::desde)
            .toList();
    }

    /** Recalcula el saldo del comprobante y mueve su estado segun lo ya cobrado (S10). */
    private void actualizarSaldoYEstado(Factura factura, BigDecimal pagado) {
        BigDecimal saldo = factura.getTotal().subtract(pagado);
        factura.setSaldo(saldo);
        factura.setEstado(estado(saldo.compareTo(BigDecimal.ZERO) <= 0
            ? EstadoFactura.PAGADA
            : EstadoFactura.PAGO_PARCIAL));
        factura.setUpdatedAt(OffsetDateTime.now());
        facturas.save(factura);
    }

    private BigDecimal totalPagado(Integer idFactura) {
        BigDecimal pagado = pagos.sumarPagos(idFactura);
        return pagado == null ? BigDecimal.ZERO : pagado;
    }

    private Factura buscar(Integer idFactura) {
        return facturas.findById(idFactura)
            .orElseThrow(() -> new ClinicaBusinessException(HttpStatus.NOT_FOUND,
                "Factura no encontrada"));
    }

    private EstadoFactura estado(String code) {
        return estados.findByCode(code)
            .orElseThrow(() -> new IllegalStateException("Catalogo de estados de factura incompleto: " + code));
    }

    private static String recortar(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
