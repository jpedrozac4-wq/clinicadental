package com.empresa.calzado.erp.pos;

import com.empresa.calzado.erp.catalogo.CalzadoSku;
import com.empresa.calzado.erp.facturacion.Factura;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Content tests for the credit note. The document must reflect the real return, use Spanish labels
 * with accents and format money and dates deterministically, like the POS ticket.
 */
class NotaCreditoPdfTest {

    private static final Charset LATIN1 = StandardCharsets.ISO_8859_1;

    @Test
    void laNotaCreditoReflejaLaDevolucionSuDesgloseYLaLeyenda() {
        byte[] pdf = new NotaCreditoPdf().generar(devolucionDeEjemplo(), notaCreditoDeEjemplo());
        String texto = new String(pdf, LATIN1);

        assertThat(texto).startsWith("%PDF-1.4");
        assertThat(texto.trim()).endsWith("%%EOF");
        assertThat(texto).contains("DEV-TKT-1234-1");
        assertThat(texto).contains("TKT-1234");
        assertThat(texto).contains("Cliente Identificado");
        assertThat(texto).contains("TV001-40 x2 @ 50.00");
        assertThat(texto).contains("100.00");
        assertThat(texto).contains("89.29");
        assertThat(texto).contains("10.71");
        assertThat(texto).contains("Este documento no tiene validez tributaria");
        assertThat(texto).contains("/ Comprobante de prueba interna");
        assertThat(texto).contains("NOTA DE CRÉDITO");
        assertThat(pdf).contains((byte) 0xC9);
    }

    @Test
    void laNotaCreditoEsUnPdfEstructuralmenteValido() {
        byte[] pdf = new NotaCreditoPdf().generar(devolucionDeEjemplo(), notaCreditoDeEjemplo());
        String texto = new String(pdf, LATIN1);

        assertThat(texto).contains("/Count 1");
        assertThat(texto).contains("/WinAnsiEncoding");
        assertThat(texto).contains("/MediaBox [0 0 226.00 800.00]");
    }

    @Test
    void elFormatoDeDineroYFechaNoDependeDelLocale() {
        Locale anterior = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            byte[] pdf = new NotaCreditoPdf().generar(devolucionDeEjemplo(), notaCreditoDeEjemplo());
            String texto = new String(pdf, LATIN1);

            assertThat(texto).contains("100.00");
            assertThat(texto).doesNotContain("100,00");
            assertThat(texto).contains("15/03/2025 14:35");
        } finally {
            Locale.setDefault(anterior);
        }
    }

    private static Devolucion devolucionDeEjemplo() {
        Factura facturaOrigen = new Factura();
        facturaOrigen.setNumeroCorrelativo("TKT-1234");

        Devolucion devolucion = new Devolucion();
        devolucion.setNumeroDevolucion("DEV-TKT-1234-1");
        devolucion.setFacturaOrigen(facturaOrigen);
        devolucion.setCreatedAt(OffsetDateTime.of(2025, 3, 15, 14, 30, 0, 0, ZoneOffset.ofHours(-5)));

        DevolucionLinea linea = new DevolucionLinea();
        CalzadoSku sku = new CalzadoSku();
        sku.setSku("TV001-40");
        linea.setCalzadoSku(sku);
        linea.setCantidad(2);
        linea.setUnitPrice(new BigDecimal("50.00"));
        linea.setDevolucion(devolucion);
        devolucion.getLineas().add(linea);
        return devolucion;
    }

    private static Factura notaCreditoDeEjemplo() {
        Factura notaCredito = new Factura();
        notaCredito.setNumeroCorrelativo("NC-TKT-1234-1");
        notaCredito.setRazonSocial("Cliente Identificado");
        notaCredito.setMontoSubtotal(new BigDecimal("89.29"));
        notaCredito.setMontoIva(new BigDecimal("10.71"));
        notaCredito.setInvoiceTotal(new BigDecimal("100.00"));
        notaCredito.setCreatedAt(OffsetDateTime.of(2025, 3, 15, 14, 35, 0, 0, ZoneOffset.ofHours(-5)));
        return notaCredito;
    }
}
