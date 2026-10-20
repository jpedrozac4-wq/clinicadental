package com.empresa.calzado.erp.pos;

import com.empresa.calzado.erp.facturacion.Factura;
import com.empresa.calzado.erp.reportes.FacturaPdfStorage;
import org.springframework.stereotype.Service;

/**
 * Emits the credit note of a return: renders it from the persisted return and writes it under
 * {@code storage/facturas}. The returned url is the real one produced by the storage writer, so it
 * is never rebuilt by hand (the web invoice's lowercased url is the defect this avoids).
 */
@Service
public class NotaCreditoService {

    private final NotaCreditoPdf generador;
    private final FacturaPdfStorage almacen;

    public NotaCreditoService(NotaCreditoPdf generador, FacturaPdfStorage almacen) {
        this.generador = generador;
        this.almacen = almacen;
    }

    public String emitir(Devolucion devolucion, Factura notaCredito) {
        byte[] pdf = generador.generar(devolucion, notaCredito);
        String url = almacen.guardar(notaCredito.getNumeroCorrelativo(), pdf);
        notaCredito.setPdfUrl(url);
        return url;
    }
}
