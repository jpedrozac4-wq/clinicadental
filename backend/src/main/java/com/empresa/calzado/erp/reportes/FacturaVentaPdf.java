package com.empresa.calzado.erp.reportes;

import com.empresa.calzado.erp.catalogo.CalzadoSku;
import com.empresa.calzado.erp.facturacion.Factura;
import com.empresa.calzado.erp.facturacion.FacturaLinea;
import com.empresa.calzado.erp.reportes.pdf.DocumentoPdf;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Renders the web sale invoice (RN-FIN-01) from the real persisted document: header, correlative,
 * date, the stored {@code factura_linea} items, subtotal, tax and total, and the legend that it has
 * no tax validity.
 *
 * <p>It reuses the hand-written {@link DocumentoPdf} primitives and the same conventions as the POS
 * ticket and the credit note: Spanish labels with accents exercise the Windows-1252 encoding, and
 * money and dates use {@link Locale#ROOT}. It never invents amounts: every value comes from the
 * invoice row.
 */
@Component
public class FacturaVentaPdf {

    private static final String EMPRESA = "CALZADO EMPRESA S.A.";
    private static final String LEYENDA_1 = "Este documento no tiene validez tributaria";
    private static final String LEYENDA_2 = "/ Comprobante de prueba interna";

    private static final float ANCHO = DocumentoPdf.ANCHO_TICKET;
    private static final float ALTO = DocumentoPdf.ALTO_TICKET;
    private static final float MARGEN = 10f;
    private static final float TAMANO = 7.5f;
    private static final float TAMANO_TITULO = 9f;

    private static final DateTimeFormatter FORMATO_FECHA =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.ROOT);

    public byte[] generar(Factura factura) {
        DocumentoPdf doc = new DocumentoPdf(ANCHO, ALTO).margenes(MARGEN, MARGEN, MARGEN, MARGEN);

        doc.textoCentrado(EMPRESA, TAMANO_TITULO, true);
        doc.textoCentrado(titulo(factura), TAMANO_TITULO, false);
        doc.lineaHorizontal();

        doc.filaTabla("Comprobante:", texto(factura.getNumeroCorrelativo()), TAMANO, false);
        doc.filaTabla("Fecha:", fecha(factura.getCreatedAt()), TAMANO, false);
        doc.filaTabla("Orden:", factura.getOrden() != null ? texto(factura.getOrden().getNumeroOrden()) : "", TAMANO, false);
        doc.filaTabla("Cliente:", texto(factura.getRazonSocial()), TAMANO, false);
        doc.filaTabla("Documento:", texto(factura.getNitCliente()), TAMANO, false);
        doc.lineaHorizontal();

        doc.textoLinea("Artículos", TAMANO, true);
        if (factura.getLineas() != null) {
            for (FacturaLinea linea : factura.getLineas()) {
                doc.filaTabla(descripcionLinea(linea), totalLinea(linea), TAMANO, false);
            }
        }
        doc.lineaHorizontal();

        doc.filaTabla("Subtotal:", money(factura.getMontoSubtotal()), TAMANO, false);
        doc.filaTabla("IVA:", money(factura.getMontoIva()), TAMANO, false);
        if (factura.getMontoRecargoPasarela() != null
            && factura.getMontoRecargoPasarela().compareTo(BigDecimal.ZERO) > 0) {
            doc.filaTabla("Recargo pasarela:", money(factura.getMontoRecargoPasarela()), TAMANO, false);
        }
        doc.filaTabla("Total:", money(factura.getInvoiceTotal()), TAMANO, true, true);

        doc.textoCentrado(LEYENDA_1, TAMANO, false);
        doc.textoCentrado(LEYENDA_2, TAMANO, false);
        return doc.generar();
    }

    /** User-facing title derived from the document type; a formal invoice or a simplified receipt. */
    private static String titulo(Factura factura) {
        String code = factura.getFacturaTipo() != null ? factura.getFacturaTipo().getCode() : null;
        if ("BOLETA".equals(code)) {
            return "BOLETA DE VENTA";
        }
        return "FACTURA";
    }

    /** One row per stored line: sku, quantity, unit price and the line total. */
    private static String descripcionLinea(FacturaLinea linea) {
        CalzadoSku sku = linea.getCalzadoSku();
        String codigo = sku != null && sku.getSku() != null ? sku.getSku() : "";
        BigDecimal unitario = linea.getUnitPrice() != null ? linea.getUnitPrice() : BigDecimal.ZERO;
        return codigo + " x" + linea.getCantidad() + " @ " + money(unitario);
    }

    private static String totalLinea(FacturaLinea linea) {
        if (linea.getLineTotal() != null) {
            return money(linea.getLineTotal());
        }
        BigDecimal unitario = linea.getUnitPrice() != null ? linea.getUnitPrice() : BigDecimal.ZERO;
        return money(unitario.multiply(BigDecimal.valueOf(linea.getCantidad())));
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
