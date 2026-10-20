package com.empresa.calzado.erp.pos;

import com.empresa.calzado.erp.catalogo.CalzadoSku;
import com.empresa.calzado.erp.facturacion.Factura;
import com.empresa.calzado.erp.reportes.pdf.DocumentoPdf;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Renders the credit note of a physical return from the real persisted data: the return, its lines
 * and the credit note's own totals. It never invents amounts.
 *
 * <p>It follows the POS ticket's conventions: the layout uses the writer's measured width table
 * ({@link DocumentoPdf#anchoTexto}) and its containment primitives, so no caller-side truncation or
 * overlap guard is needed. Printed labels are user-facing Spanish copy and therefore carry accents,
 * which exercises the Windows-1252 encoding of the writer. Money and dates use {@link Locale#ROOT}.
 */
@Component
public class NotaCreditoPdf {

    private static final String EMPRESA = "CALZADO EMPRESA S.A.";
    private static final String TITULO = "NOTA DE CRÉDITO";
    private static final String LEYENDA_1 = "Este documento no tiene validez tributaria";
    private static final String LEYENDA_2 = "/ Comprobante de prueba interna";

    private static final float ANCHO = DocumentoPdf.ANCHO_TICKET;
    private static final float ALTO = DocumentoPdf.ALTO_TICKET;
    private static final float MARGEN = 10f;
    private static final float TAMANO = 7.5f;
    private static final float TAMANO_TITULO = 9f;

    private static final DateTimeFormatter FORMATO_FECHA =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.ROOT);

    public byte[] generar(Devolucion devolucion, Factura notaCredito) {
        DocumentoPdf doc = new DocumentoPdf(ANCHO, ALTO).margenes(MARGEN, MARGEN, MARGEN, MARGEN);

        doc.textoCentrado(EMPRESA, TAMANO_TITULO, true);
        doc.textoCentrado(TITULO, TAMANO_TITULO, false);
        doc.lineaHorizontal();

        doc.filaTabla("Devolución:", texto(devolucion.getNumeroDevolucion()), TAMANO, false);
        doc.filaTabla("Fecha:", fecha(notaCredito.getCreatedAt()), TAMANO, false);
        doc.filaTabla("Factura origen:", correlativoOrigen(devolucion), TAMANO, false);
        doc.filaTabla("Cliente:", texto(notaCredito.getRazonSocial()), TAMANO, false);
        doc.lineaHorizontal();

        doc.textoLinea("Artículos devueltos", TAMANO, true);
        if (devolucion.getLineas() != null) {
            for (DevolucionLinea linea : devolucion.getLineas()) {
                doc.filaTabla(descripcionLinea(linea), totalLinea(linea), TAMANO, false);
            }
        }
        doc.lineaHorizontal();

        doc.filaTabla("Subtotal:", money(notaCredito.getMontoSubtotal()), TAMANO, false);
        doc.filaTabla("IVA:", money(notaCredito.getMontoIva()), TAMANO, false);
        doc.filaTabla("Total:", money(notaCredito.getInvoiceTotal()), TAMANO, true, true);

        doc.textoCentrado(LEYENDA_1, TAMANO, false);
        doc.textoCentrado(LEYENDA_2, TAMANO, false);
        return doc.generar();
    }

    /** One row per returned line: sku, quantity, unit price and the line total. */
    private static String descripcionLinea(DevolucionLinea linea) {
        CalzadoSku sku = linea.getCalzadoSku();
        String codigo = sku != null && sku.getSku() != null ? sku.getSku() : "";
        BigDecimal unitario = linea.getUnitPrice() != null ? linea.getUnitPrice() : BigDecimal.ZERO;
        return codigo + " x" + linea.getCantidad() + " @ " + money(unitario);
    }

    private static String totalLinea(DevolucionLinea linea) {
        BigDecimal unitario = linea.getUnitPrice() != null ? linea.getUnitPrice() : BigDecimal.ZERO;
        return money(unitario.multiply(BigDecimal.valueOf(linea.getCantidad())));
    }

    private static String correlativoOrigen(Devolucion devolucion) {
        Factura origen = devolucion.getFacturaOrigen();
        return origen == null ? "" : texto(origen.getNumeroCorrelativo());
    }

    private static String texto(String valor) {
        return valor == null ? "" : valor;
    }

    /** Locale-independent money: HALF_UP to 2 decimals, plain string, never a grouping comma. */
    private static String money(BigDecimal monto) {
        if (monto == null) {
            return "0.00";
        }
        return monto.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** Locale-independent date: the pattern is built with Locale.ROOT. */
    private static String fecha(OffsetDateTime fecha) {
        return fecha == null ? "" : fecha.format(FORMATO_FECHA);
    }
}
