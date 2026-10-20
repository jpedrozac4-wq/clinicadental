package com.empresa.calzado.erp.pos;

import com.empresa.calzado.erp.catalogo.CalzadoSku;
import com.empresa.calzado.erp.catalogo.ModeloColor;
import com.empresa.calzado.erp.facturacion.Factura;
import com.empresa.calzado.erp.facturacion.PagoTransaccion;
import com.empresa.calzado.erp.reportes.pdf.DocumentoPdf;
import com.empresa.calzado.erp.seguridad.SystemUser;
import com.empresa.calzado.erp.ventas.Customer;
import com.empresa.calzado.erp.ventas.Orden;
import com.empresa.calzado.erp.ventas.OrdenLinea;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Renders the POS ticket from the real persisted sale: the order, its line, the invoice, the
 * payment transaction and the session that produced them. It never invents amounts.
 *
 * <p>Printed labels are user-facing Spanish copy and therefore carry accents, which is exactly
 * what exercises the Windows-1252 encoding of the writer. Money and dates are formatted with
 * {@link Locale#ROOT} so a locale with a decimal comma can never corrupt the document.
 */
@Component
public class TicketPosPdf {

    private static final String EMPRESA = "CALZADO EMPRESA S.A.";
    private static final String TITULO = "TICKET DE VENTA";
    private static final String CIERRE = "¡Gracias por su compra!";

    private static final float ANCHO = DocumentoPdf.ANCHO_TICKET;
    private static final float ALTO = DocumentoPdf.ALTO_TICKET;
    private static final float MARGEN = 10f;
    private static final float TAMANO = 7.5f;
    private static final float TAMANO_TITULO = 9f;
    private static final float ANCHO_UTIL = ANCHO - 2 * MARGEN;

    private static final DateTimeFormatter FORMATO_FECHA =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.ROOT);

    public byte[] generar(Factura factura, PagoTransaccion pago, SesionCaja sesion) {
        Orden orden = factura.getOrden();
        CajaPos caja = sesion.getCajaPos();
        SystemUser cajero = sesion.getCajero();
        Customer customer = factura.getCustomer();

        DocumentoPdf doc = new DocumentoPdf(ANCHO, ALTO).margenes(MARGEN, MARGEN, MARGEN, MARGEN);

        doc.textoCentrado(EMPRESA, TAMANO_TITULO, true);
        doc.textoCentrado(TITULO, TAMANO_TITULO, false);
        doc.lineaHorizontal();

        doc.filaTabla("Sucursal:", caja.getSucursal().getNombre(), TAMANO, false);
        doc.filaTabla("Terminal:", caja.getNombreTerminal(), TAMANO, false);
        doc.filaTabla("Caja:", caja.getNumeroCaja(), TAMANO, false);
        doc.filaTabla("Cajero:", cajero != null ? cajero.getUsername() : "", TAMANO, false);
        doc.filaTabla("Fecha:", fecha(factura.getCreatedAt()), TAMANO, false);
        doc.filaTabla("Ticket:", factura.getNumeroCorrelativo(), TAMANO, false);
        doc.filaTabla("Orden:", orden != null ? orden.getNumeroOrden() : "", TAMANO, false);
        doc.lineaHorizontal();

        doc.textoLinea("Artículos", TAMANO, true);
        if (orden != null) {
            for (OrdenLinea linea : orden.getLineas()) {
                doc.filaTabla(recortar(descripcion(linea), TAMANO, ANCHO_UTIL - 40f), money(linea.getLineTotal()), TAMANO, false);
                doc.filaTabla("Código " + codigo(linea) + " x" + linea.getCantidad(),
                    money(linea.getUnitPrice()), TAMANO, false);
            }
        }
        doc.lineaHorizontal();

        doc.filaTabla("Subtotal:", money(factura.getMontoSubtotal()), TAMANO, false);
        doc.filaTabla("IVA:", money(factura.getMontoIva()), TAMANO, false);
        doc.filaTabla("Total:", money(factura.getInvoiceTotal()), TAMANO, true, true);

        doc.filaTabla("Método de pago:", codigoMetodo(pago), TAMANO, false);
        doc.filaTabla("Documento:", factura.getNitCliente(), TAMANO, false);
        doc.filaTabla("Cliente:", factura.getRazonSocial(), TAMANO, false);
        if (customer != null && customer.getEmail() != null) {
            doc.filaTabla("Correo:", recortar(customer.getEmail(), TAMANO, ANCHO_UTIL - 50f), TAMANO, false);
        }
        doc.lineaHorizontal();

        doc.textoCentrado(CIERRE, TAMANO, false);
        return doc.generar();
    }

    private static String descripcion(OrdenLinea linea) {
        CalzadoSku sku = linea.getCalzadoSku();
        if (sku == null) {
            return "";
        }
        List<String> partes = new ArrayList<>();
        ModeloColor modeloColor = sku.getModeloColor();
        if (modeloColor != null) {
            if (modeloColor.getModelo() != null && modeloColor.getModelo().getNombre() != null) {
                partes.add(modeloColor.getModelo().getNombre());
            }
            if (modeloColor.getColor() != null && modeloColor.getColor().getNombre() != null) {
                partes.add(modeloColor.getColor().getNombre());
            }
        }
        if (sku.getTalla() != null && sku.getTalla().getTallaEur() != null) {
            partes.add(sku.getTalla().getTallaEur().toPlainString());
        }
        return String.join(" ", partes);
    }

    private static String codigo(OrdenLinea linea) {
        return linea.getCalzadoSku() != null ? linea.getCalzadoSku().getSku() : "";
    }

    /** The short catalogue code is what fits an 80 mm receipt; the description belongs on screen. */
    private static String codigoMetodo(PagoTransaccion pago) {
        if (pago == null || pago.getPagoMetodo() == null) {
            return "";
        }
        String codigo = pago.getPagoMetodo().getCode();
        return codigo != null ? codigo : pago.getPagoMetodo().getDescription();
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

    /** Real-width truncation so a long description cannot run past the roll width. */
    private static String recortar(String texto, float tamano, float anchoMax) {
        return DocumentoPdf.recortar(texto, tamano, false, anchoMax);
    }
}
