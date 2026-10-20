package com.empresa.calzado.erp.pos;

import com.empresa.calzado.erp.facturacion.Factura;
import com.empresa.calzado.erp.facturacion.PagoTransaccion;
import com.empresa.calzado.erp.reportes.FacturaPdfStorage;
import org.springframework.stereotype.Service;

/**
 * Emits the POS ticket: renders it from the persisted sale and writes it under
 * {@code storage/facturas}. The invoice correlativo names the file and the returned url is the
 * real, downloadable one served by the existing {@code /storage/**} handler.
 */
@Service
public class TicketPosService {

    private final TicketPosPdf generador;
    private final FacturaPdfStorage almacen;

    public TicketPosService(TicketPosPdf generador, FacturaPdfStorage almacen) {
        this.generador = generador;
        this.almacen = almacen;
    }

    /**
     * Renders, stores and links the ticket. The url is also written back to the invoice so the
     * document is durable, not only present in the HTTP response.
     */
    public String emitir(Factura factura, PagoTransaccion pago, SesionCaja sesion) {
        byte[] pdf = generador.generar(factura, pago, sesion);
        String url = almacen.guardar(factura.getNumeroCorrelativo(), pdf);
        factura.setPdfUrl(url);
        return url;
    }
}
