package com.empresa.calzado.erp.reportes;

import com.empresa.calzado.erp.catalogo.CalzadoSku;
import com.empresa.calzado.erp.facturacion.ConvencionIva;
import com.empresa.calzado.erp.facturacion.Factura;
import com.empresa.calzado.erp.facturacion.FacturaLinea;
import com.empresa.calzado.erp.facturacion.FacturaTipo;
import com.empresa.calzado.erp.ventas.Customer;
import com.empresa.calzado.erp.ventas.Orden;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Content tests for the web invoice PDF. It must itemise the stored lines, show the shared tax
 * breakdown and carry the no-tax-validity legend of RN-FIN-01, exactly like the credit note and the
 * POS ticket.
 */
class FacturaVentaPdfTest {

    private static final Charset LATIN1 = StandardCharsets.ISO_8859_1;

    @Test
    void laFacturaReflejaLosItemsElDesgloseYLaLeyenda() {
        Factura factura = facturaDeEjemplo();

        byte[] pdf = new FacturaVentaPdf().generar(factura);
        String texto = new String(pdf, LATIN1);

        assertThat(texto).startsWith("%PDF-1.4");
        assertThat(texto.trim()).endsWith("%%EOF");
        assertThat(texto).contains("FACTURA");
        assertThat(texto).contains("FAC-000102");
        assertThat(texto).contains("ORD-WEB-1");
        assertThat(texto).contains("Cliente Web");
        assertThat(texto).contains("1712345678");
        assertThat(texto).contains("TV001-40");
        assertThat(texto).contains("TV002-41");
        assertThat(texto).contains("89.29");
        assertThat(texto).contains("10.71");
        assertThat(texto).contains("100.00");
        assertThat(texto).contains("Este documento no tiene validez tributaria");
        assertThat(texto).contains("/ Comprobante de prueba interna");
        // The accent in "Artículos" proves the Windows-1252 encoding path.
        assertThat(pdf).contains((byte) 0xED);
    }

    @Test
    void laBoletaUsaSuPropioTituloYCorrelativo() {
        Factura factura = facturaDeEjemplo();
        factura.getFacturaTipo().setCode("BOLETA");
        factura.setNumeroCorrelativo("TKT-000042");

        String texto = new String(new FacturaVentaPdf().generar(factura), LATIN1);

        assertThat(texto).contains("BOLETA");
        assertThat(texto).contains("TKT-000042");
        assertThat(texto).doesNotContain("FACTURA");
    }

    @Test
    void elRecargoDePasarelaSeImprimeCuandoExiste() {
        Factura factura = facturaDeEjemplo();
        factura.setMontoRecargoPasarela(new BigDecimal("3.80"));

        String texto = new String(new FacturaVentaPdf().generar(factura), LATIN1);

        assertThat(texto).contains("Recargo pasarela");
        assertThat(texto).contains("3.80");
    }

    @Test
    void laFacturaEsUnPdfEstructuralmenteValido() {
        String texto = new String(new FacturaVentaPdf().generar(facturaDeEjemplo()), LATIN1);

        assertThat(texto).contains("/Count 1");
        assertThat(texto).contains("/WinAnsiEncoding");
        assertThat(texto).contains("/MediaBox [0 0 226.00 800.00]");
    }

    @Test
    void elFormatoDeDineroYFechaNoDependeDelLocale() {
        Locale anterior = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            String texto = new String(new FacturaVentaPdf().generar(facturaDeEjemplo()), LATIN1);

            assertThat(texto).contains("100.00");
            assertThat(texto).doesNotContain("100,00");
            assertThat(texto).contains("15/03/2025 14:30");
        } finally {
            Locale.setDefault(anterior);
        }
    }

    private static Factura facturaDeEjemplo() {
        Orden orden = new Orden();
        orden.setNumeroOrden("ORD-WEB-1");
        orden.setCreatedAt(OffsetDateTime.of(2025, 3, 15, 14, 30, 0, 0, ZoneOffset.ofHours(-5)));

        Customer customer = new Customer();
        customer.setNombreCompleto("Cliente Web");
        customer.setNumeroDocumento("1712345678");

        FacturaTipo tipo = new FacturaTipo();
        tipo.setCode("FACTURA");

        Factura factura = new Factura();
        factura.setOrden(orden);
        factura.setCustomer(customer);
        factura.setFacturaTipo(tipo);
        factura.setNumeroCorrelativo("FAC-000102");
        factura.setNitCliente("1712345678");
        factura.setRazonSocial("Cliente Web");
        factura.setMontoSubtotal(new BigDecimal("89.29"));
        factura.setMontoIva(new BigDecimal("10.71"));
        factura.setInvoiceTotal(new BigDecimal("100.00"));
        factura.setCreatedAt(OffsetDateTime.of(2025, 3, 15, 14, 30, 0, 0, ZoneOffset.ofHours(-5)));

        factura.getLineas().add(linea(factura, "TV001-40", 1, "60.00"));
        factura.getLineas().add(linea(factura, "TV002-41", 1, "40.00"));
        return factura;
    }

    private static FacturaLinea linea(Factura factura, String sku, int cantidad, String unitario) {
        CalzadoSku calzadoSku = new CalzadoSku();
        calzadoSku.setSku(sku);
        FacturaLinea linea = new FacturaLinea();
        linea.setFactura(factura);
        linea.setCalzadoSku(calzadoSku);
        linea.setCantidad(cantidad);
        BigDecimal unit = new BigDecimal(unitario);
        linea.setUnitPrice(unit);
        linea.setLineTotal(unit.multiply(BigDecimal.valueOf(cantidad)));
        return linea;
    }

    @Test
    void laConvencionCompartidaMantieneLaIdentidadEnLaFactura() {
        Factura factura = facturaDeEjemplo();
        ConvencionIva.Desglose desglose = ConvencionIva.desglosar(factura.getInvoiceTotal());

        assertThat(desglose.subtotal().add(desglose.iva()))
            .isEqualByComparingTo(factura.getInvoiceTotal());
    }
}
