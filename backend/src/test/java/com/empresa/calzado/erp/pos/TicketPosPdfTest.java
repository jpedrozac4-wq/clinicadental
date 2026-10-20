package com.empresa.calzado.erp.pos;

import com.empresa.calzado.erp.catalogo.CalzadoSku;
import com.empresa.calzado.erp.catalogo.Color;
import com.empresa.calzado.erp.catalogo.Modelo;
import com.empresa.calzado.erp.catalogo.ModeloColor;
import com.empresa.calzado.erp.catalogo.TallaEquivalencia;
import com.empresa.calzado.erp.facturacion.Factura;
import com.empresa.calzado.erp.facturacion.PagoMetodo;
import com.empresa.calzado.erp.facturacion.PagoTransaccion;
import com.empresa.calzado.erp.inventario.Bodega;
import com.empresa.calzado.erp.reportes.pdf.DocumentoPdf;
import com.empresa.calzado.erp.seguridad.SystemUser;
import com.empresa.calzado.erp.ventas.Customer;
import com.empresa.calzado.erp.ventas.Orden;
import com.empresa.calzado.erp.ventas.OrdenLinea;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Content tests for the POS ticket. The ticket must reflect the real persisted sale, use Spanish
 * labels with accents and format money and dates deterministically.
 */
class TicketPosPdfTest {

    private static final Charset LATIN1 = StandardCharsets.ISO_8859_1;

    @Test
    void elTicketReflejaLosDatosRealesDeLaVenta() {
        Factura factura = facturaDeEjemplo();
        PagoTransaccion pago = pagoDeEjemplo(factura);

        byte[] pdf = new TicketPosPdf().generar(factura, pago, sesionDeEjemplo());
        String texto = new String(pdf, LATIN1);

        assertThat(texto).startsWith("%PDF-1.4");
        assertThat(texto.trim()).endsWith("%%EOF");
        assertThat(texto).contains("TKT-ABC123");
        assertThat(texto).contains("POS-ORD-01");
        assertThat(texto).contains("SKU-POS");
        assertThat(texto).contains("Deportivo Negro 42.0");
        assertThat(texto).contains("Tienda Centro");
        assertThat(texto).contains("CAJA-01");
        assertThat(texto).contains("Terminal Principal");
        assertThat(texto).contains("cajero1");
        assertThat(texto).contains("EFECTIVO");
        assertThat(texto).contains("123.45");
        assertThat(texto).contains("1234567890101");
        assertThat(texto).contains("Consumidor Final");
        assertThat(texto).contains("Código");
        assertThat(texto).contains("Artículos");
        assertThat(texto).contains("Método de pago");
        assertThat(pdf).contains((byte) 0xF3);
    }

    @Test
    void elFormatoDeDineroYFechaNoDependeDelLocale() {
        Locale anterior = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            Factura factura = facturaDeEjemplo();
            byte[] pdf = new TicketPosPdf().generar(factura, pagoDeEjemplo(factura), sesionDeEjemplo());
            String texto = new String(pdf, LATIN1);

            assertThat(texto).contains("123.45");
            assertThat(texto).doesNotContain("123,45");
            assertThat(texto).contains("15/03/2025 14:30");
        } finally {
            Locale.setDefault(anterior);
        }
    }

    @Test
    void elTicketEsUnPdfEstructuralmenteValido() {
        Factura factura = facturaDeEjemplo();
        byte[] pdf = new TicketPosPdf().generar(factura, pagoDeEjemplo(factura), sesionDeEjemplo());

        assertThat(new String(pdf, LATIN1)).contains("/Count 1");
        assertThat(new String(pdf, LATIN1)).contains("/MediaBox [0 0 226.00 800.00]");
        assertThat(new String(pdf, LATIN1)).contains("/WinAnsiEncoding");
    }

    @Test
    void elMetodoDePagoUsaElCodigoCortoYNoSeSuperpone() {
        Factura factura = facturaDeEjemplo();
        PagoTransaccion pago = pagoDeEjemplo(factura);
        pago.getPagoMetodo().setDescription("Cobro en efectivo en caja del punto de venta (POS)");

        byte[] pdf = new TicketPosPdf().generar(factura, pago, sesionDeEjemplo());
        String contenido = contenidoDeFlujos(pdf);

        assertThat(contenido).contains("(EFECTIVO) Tj");
        assertThat(contenido).doesNotContain("Cobro en efectivo");

        List<Texto> textos = extraerTextos(pdf);
        Texto etiqueta = textos.stream()
            .filter(t -> t.texto().equals("Método de pago:")).findFirst().orElseThrow();
        Texto valor = textos.stream()
            .filter(t -> t.texto().equals("EFECTIVO")).findFirst().orElseThrow();
        assertThat(valor.y()).isEqualTo(etiqueta.y());
        assertThat(valor.x()).isGreaterThanOrEqualTo(etiqueta.x() + etiqueta.ancho());
    }

    private static final Pattern TEXTO = Pattern.compile("([-0-9.]+) ([-0-9.]+) Td\\n\\((.*?)\\) Tj");

    private static List<Texto> extraerTextos(byte[] pdf) {
        String contenido = contenidoDeFlujos(pdf);
        List<Texto> textos = new java.util.ArrayList<>();
        Matcher matcher = TEXTO.matcher(contenido);
        while (matcher.find()) {
            textos.add(new Texto(
                Float.parseFloat(matcher.group(1)),
                Float.parseFloat(matcher.group(2)),
                desescapar(matcher.group(3))));
        }
        return textos;
    }

    private static String contenidoDeFlujos(byte[] pdf) {
        StringBuilder sb = new StringBuilder();
        int desde = 0;
        while (true) {
            int idx = indiceDe(pdf, "stream", desde);
            if (idx < 0) {
                break;
            }
            desde = idx + "stream".length();
            if (idx >= 3 && new String(pdf, idx - 3, 3, LATIN1).equals("end")) {
                continue;
            }
            int datos = idx + "stream".length() + 1;
            int fin = indiceDe(pdf, "endstream", datos);
            sb.append(new String(pdf, datos, fin - datos - 1, Charset.forName("windows-1252")));
        }
        return sb.toString();
    }

    private static String desescapar(String texto) {
        StringBuilder sb = new StringBuilder(texto.length());
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (c == '\\' && i + 1 < texto.length()) {
                char siguiente = texto.charAt(++i);
                if (siguiente == '(' || siguiente == ')' || siguiente == '\\') {
                    sb.append(siguiente);
                } else {
                    sb.append('\\').append(siguiente);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static int indiceDe(byte[] pdf, String aguja, int desde) {
        byte[] bytes = aguja.getBytes(LATIN1);
        for (int i = Math.max(desde, 0); i <= pdf.length - bytes.length; i++) {
            boolean igual = true;
            for (int j = 0; j < bytes.length; j++) {
                if (pdf[i + j] != bytes[j]) {
                    igual = false;
                    break;
                }
            }
            if (igual) {
                return i;
            }
        }
        return -1;
    }

    private record Texto(float x, float y, String texto) {
        float ancho() {
            return DocumentoPdf.anchoTexto(texto, 7.5f, false);
        }
    }

    private static Factura facturaDeEjemplo() {
        Orden orden = new Orden();
        orden.setNumeroOrden("POS-ORD-01");
        orden.setCreatedAt(OffsetDateTime.of(2025, 3, 15, 14, 30, 0, 0, ZoneOffset.ofHours(-5)));

        OrdenLinea linea = new OrdenLinea();
        linea.setOrden(orden);
        linea.setCantidad(2);
        linea.setUnitPrice(new BigDecimal("61.725"));
        linea.setLineTotal(new BigDecimal("123.45"));
        linea.setCalzadoSku(skuDeEjemplo());
        orden.getLineas().add(linea);

        Customer customer = new Customer();
        customer.setNombreCompleto("Consumidor Final");
        customer.setTipoDocumento("CF");

        Factura factura = new Factura();
        factura.setOrden(orden);
        factura.setCustomer(customer);
        factura.setNumeroCorrelativo("TKT-ABC123");
        factura.setNitCliente("1234567890101");
        factura.setRazonSocial("Consumidor Final");
        factura.setMontoSubtotal(new BigDecimal("123.45"));
        factura.setMontoIva(BigDecimal.ZERO);
        factura.setInvoiceTotal(new BigDecimal("123.45"));
        factura.setCreatedAt(OffsetDateTime.of(2025, 3, 15, 14, 30, 0, 0, ZoneOffset.ofHours(-5)));
        return factura;
    }

    private static CalzadoSku skuDeEjemplo() {
        Modelo modelo = new Modelo();
        modelo.setNombre("Deportivo");
        Color color = new Color();
        color.setNombre("Negro");
        ModeloColor modeloColor = new ModeloColor();
        modeloColor.setModelo(modelo);
        modeloColor.setColor(color);
        TallaEquivalencia talla = new TallaEquivalencia();
        talla.setTallaEur(new BigDecimal("42.0"));

        CalzadoSku sku = new CalzadoSku();
        sku.setId(10);
        sku.setSku("SKU-POS");
        sku.setPrecioVentaVigente(new BigDecimal("61.725"));
        sku.setModeloColor(modeloColor);
        sku.setTalla(talla);
        return sku;
    }

    private static PagoTransaccion pagoDeEjemplo(Factura factura) {
        PagoMetodo metodo = new PagoMetodo();
        metodo.setCode("EFECTIVO");
        metodo.setDescription("Efectivo");
        PagoTransaccion pago = new PagoTransaccion();
        pago.setFactura(factura);
        pago.setPagoMetodo(metodo);
        pago.setEstadoPago("APROBADO");
        pago.setMontoBruto(new BigDecimal("123.45"));
        pago.setMontoNetoBancario(new BigDecimal("123.45"));
        pago.setReferenciaTransaccion("SIM-TX-ABC");
        return pago;
    }

    private static SesionCaja sesionDeEjemplo() {
        Bodega bodega = new Bodega();
        bodega.setId(1);
        bodega.setNombre("Bodega 1");

        Sucursal sucursal = new Sucursal();
        sucursal.setId(11);
        sucursal.setNombre("Tienda Centro");
        sucursal.setBodega(bodega);

        CajaPos caja = new CajaPos();
        caja.setId(3);
        caja.setNumeroCaja("CAJA-01");
        caja.setNombreTerminal("Terminal Principal");
        caja.setSucursal(sucursal);

        SystemUser cajero = new SystemUser();
        cajero.setId(1);
        cajero.setUsername("cajero1");

        SesionCaja sesion = new SesionCaja();
        sesion.setId(50);
        sesion.setCajaPos(caja);
        sesion.setCajero(cajero);
        return sesion;
    }
}
