package com.empresa.calzado.erp.reportes;

import com.empresa.calzado.erp.facturacion.Factura;
import org.springframework.stereotype.Service;

/**
 * Emits the web sale invoice: renders it from the persisted document and writes it under
 * {@code storage/facturas}. It returns the url the storage writer produced and writes it back to the
 * invoice, so the document is durable and downloadable, exactly like the POS ticket and the credit
 * note. The url is never rebuilt by hand and never lowercased.
 */
@Service
public class FacturaVentaPdfService {

    private final FacturaVentaPdf generador;
    private final FacturaPdfStorage almacen;

    public FacturaVentaPdfService(FacturaVentaPdf generador, FacturaPdfStorage almacen) {
        this.generador = generador;
        this.almacen = almacen;
    }

    public String emitir(Factura factura) {
        byte[] pdf = generador.generar(factura);
        String url = almacen.guardar(factura.getNumeroCorrelativo(), pdf);
        factura.setPdfUrl(url);
        return url;
    }
}
