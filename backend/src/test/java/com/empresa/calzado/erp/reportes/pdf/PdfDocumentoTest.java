package com.empresa.calzado.erp.reportes.pdf;

import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Structural tests for the hand-written PDF writer. The cross-reference table is the primary
 * correctness property: every offset is the byte offset of the {@code N 0 obj} header and every
 * entry is exactly 20 bytes.
 */
class PdfDocumentoTest {

    private static final Charset LATIN1 = StandardCharsets.ISO_8859_1;

    @Test
    void elDocumentoEmpiezaConLaCabeceraPdfYTerminaConEof() {
        byte[] pdf = documentoVacio();

        assertThat(new String(pdf, LATIN1)).startsWith("%PDF-1.4");
        assertThat(new String(pdf, LATIN1).trim()).endsWith("%%EOF");
    }

    @Test
    void laTablaDeReferenciasCruzadasEsEstructuralmenteCorrecta() {
        DocumentoPdf doc = new DocumentoPdf(DocumentoPdf.ANCHO_TICKET, DocumentoPdf.ALTO_TICKET);
        doc.textoLinea("Código", 8f, true);
        doc.filaTabla("Subtotal:", "10.00", 8f, false);
        doc.saltoPagina();
        doc.textoLinea("Segunda página", 8f, false);

        validarXref(doc.generar());
    }

    @Test
    void elValidadorDeXrefDetectaUnOffsetManipulado() {
        byte[] pdf = documentoVacio();
        validarXref(pdf);

        byte[] corrupto = pdf.clone();
        int inicioXref = (int) inicioXref(pdf);
        int cursor = saltarEol(corrupto, inicioXref + "xref".length());
        int finCabecera = indiceDe(corrupto, '\n', cursor);
        int entradas = finCabecera + 1;
        int entradaObjeto1 = entradas + 20;
        // Flip the last digit of object 1's offset: the entry now points one byte off.
        corrupto[entradaObjeto1 + 9] = (byte) (corrupto[entradaObjeto1 + 9] == '0' ? '1' : '0');

        assertThatThrownBy(() -> validarXref(corrupto)).isInstanceOf(AssertionError.class);
    }

    @Test
    void laLongitudDeCadaFlujoCoincideConSusBytesReales() {
        DocumentoPdf doc = new DocumentoPdf(DocumentoPdf.ANCHO_A4, DocumentoPdf.ALTO_A4);
        doc.textoLinea("Factura de prueba", 10f, true);
        doc.filaTabla("Total:", "123.45", 10f, false);

        validarLongitudesDeFlujo(doc.generar());
    }

    @Test
    void elDocumentoSigueSiendoValidoCuandoElValorSeEnvuelve() {
        DocumentoPdf doc = new DocumentoPdf(DocumentoPdf.ANCHO_TICKET, DocumentoPdf.ALTO_TICKET)
            .margenes(10f, 10f, 10f, 10f);
        // Two continuation lines: the value does not fit beside the label, so the stream grows and
        // the byte offsets must be recomputed for the longer object.
        doc.filaTabla("Método de pago:",
            "Cobro en efectivo en caja del punto de venta (POS) con detalle adicional", 7.5f, false);

        byte[] pdf = doc.generar();

        validarXref(pdf);
        validarLongitudesDeFlujo(pdf);
    }

    @Test
    void laEstructuraSobreviveAUnTokenAnchoQueSeRecorta() {
        DocumentoPdf doc = new DocumentoPdf(DocumentoPdf.ANCHO_TICKET, DocumentoPdf.ALTO_TICKET)
            .margenes(10f, 10f, 10f, 10f);
        // The 72-character single token is reduced with the real AFM widths: the emitted stream is
        // shorter than the input, so the xref and /Length must be recomputed for the real bytes.
        doc.filaTabla("R:", "EFECTIVO".repeat(9), 7.5f, false);

        byte[] pdf = doc.generar();

        validarXref(pdf);
        validarLongitudesDeFlujo(pdf);
    }

    @Test
    void losCaracteresEspecialesSeEscapanYNoRompenLaEstructura() {
        DocumentoPdf doc = new DocumentoPdf(DocumentoPdf.ANCHO_TICKET, 400f);
        doc.textoLinea("Etiqueta (par) \\ barra", 8f, false);

        byte[] pdf = doc.generar();

        assertThat(contenidoDeFlujos(pdf)).contains("(Etiqueta \\(par\\) \\\\ barra) Tj");
        validarXref(pdf);
        validarLongitudesDeFlujo(pdf);
    }

    @Test
    void elTextoAcentuadoSeCodificaEnWindows1252YLaFuenteDeclaraWinAnsi() {
        DocumentoPdf doc = new DocumentoPdf(DocumentoPdf.ANCHO_TICKET, 400f);
        doc.textoLinea("Código", 8f, true);

        byte[] pdf = doc.generar();
        String estructura = new String(pdf, LATIN1);

        // 'ó' is 0xF3 in both Latin-1 and Windows-1252.
        assertThat(pdf).contains((byte) 0xF3);
        assertThat(contenidoDeFlujos(pdf)).contains("(Código) Tj");
        assertThat(estructura).contains("/Encoding /WinAnsiEncoding");
        assertThat(estructura).contains("/BaseFont /Helvetica");
        assertThat(estructura).contains("/BaseFont /Helvetica-Bold");
    }

    @Test
    void elContenidoQueDesbordaProduceVariasPaginasYElArbolEsCoherente() {
        DocumentoPdf doc = new DocumentoPdf(DocumentoPdf.ANCHO_TICKET, 200f).margenes(10f, 10f, 10f, 20f);
        for (int i = 0; i < 60; i++) {
            doc.textoLinea("Linea " + i, 8f, false);
        }

        byte[] pdf = doc.generar();
        String texto = new String(pdf, LATIN1);
        int paginas = contar(texto, "/Type /Page ");

        assertThat(paginas).isGreaterThan(1);
        assertThat(texto).contains("/Count " + paginas);
        for (int i = 0; i < paginas; i++) {
            assertThat(texto).contains((7 + 2 * i) + " 0 R");
        }
        validarXref(pdf);
    }

    @Test
    void unDocumentoSinContenidoSigueSiendoEstructuralmenteValido() {
        byte[] pdf = documentoVacio();
        String texto = new String(pdf, LATIN1);

        assertThat(texto).contains("/Count 1");
        assertThat(texto).contains("/MediaBox");
        assertThat(texto).contains("/Producer");
        assertThat(texto).contains("/CreationDate");
        validarXref(pdf);
        validarLongitudesDeFlujo(pdf);
    }

    @Test
    void elTamanoDePaginaSeConfiguraConMediaBox() {
        byte[] ticket = new DocumentoPdf(DocumentoPdf.ANCHO_TICKET, DocumentoPdf.ALTO_TICKET).generar();
        byte[] a4 = new DocumentoPdf(DocumentoPdf.ANCHO_A4, DocumentoPdf.ALTO_A4).generar();

        assertThat(new String(ticket, LATIN1)).contains("/MediaBox [0 0 226.00 800.00]");
        assertThat(new String(a4, LATIN1)).contains("/MediaBox [0 0 595.00 842.00]");
    }

    @Test
    void elTextoNormalYLaNegritaUsanFuentesDistintas() {
        DocumentoPdf doc = new DocumentoPdf(200f, 200f);
        doc.textoLinea("normal", 8f, false);
        doc.textoLinea("fuerte", 8f, true);

        String contenido = contenidoDeFlujos(doc.generar());

        assertThat(contenido).contains("/F1 8.00 Tf");
        assertThat(contenido).contains("/F2 8.00 Tf");
    }

    @Test
    void elTextoSePuedeColocarEnUnaPosicionExplicita() {
        DocumentoPdf doc = new DocumentoPdf(200f, 200f);
        doc.texto(33f, 44f, "Hola", 9f, false);

        assertThat(contenidoDeFlujos(doc.generar())).contains("33.00 44.00 Td");
    }

    private static byte[] documentoVacio() {
        return new DocumentoPdf(DocumentoPdf.ANCHO_TICKET, DocumentoPdf.ALTO_TICKET).generar();
    }

    // ---------------------------------------------------------------------------------------------
    // Minimal PDF parser used by the tests. It reads the real bytes; a wrong offset fails here.
    // ---------------------------------------------------------------------------------------------

    private static void validarXref(byte[] pdf) {
        long inicio = inicioXref(pdf);
        assertThat(inicio).isLessThan(pdf.length);
        assertThat(new String(pdf, (int) inicio, 4, LATIN1)).isEqualTo("xref");

        int cursor = saltarEol(pdf, (int) inicio + "xref".length());
        int finCabecera = indiceDe(pdf, '\n', cursor);
        String[] cabecera = new String(pdf, cursor, finCabecera - cursor, LATIN1).trim().split("\\s+");
        assertThat(cabecera[0]).isEqualTo("0");
        int total = Integer.parseInt(cabecera[1]);
        assertThat(total).isGreaterThanOrEqualTo(2);

        int entradas = finCabecera + 1;
        for (int i = 0; i < total; i++) {
            int pos = entradas + i * 20;
            assertThat(pos + 20).isLessThanOrEqualTo(pdf.length);
            byte[] entrada = Arrays.copyOfRange(pdf, pos, pos + 20);
            assertThat(entrada).hasSize(20);
            assertThat(entrada[10]).isEqualTo((byte) ' ');
            assertThat(entrada[16]).isEqualTo((byte) ' ');
            assertThat(entrada[19]).isEqualTo((byte) '\n');
            String offset = new String(entrada, 0, 10, LATIN1);
            String generacion = new String(entrada, 11, 5, LATIN1);
            char tipo = (char) entrada[17];
            assertThat(offset).matches("\\d{10}");
            assertThat(generacion).matches("\\d{5}");
            if (i == 0) {
                assertThat(offset).isEqualTo("0000000000");
                assertThat(generacion).isEqualTo("65535");
                assertThat(tipo).isEqualTo('f');
            } else {
                assertThat(tipo).isEqualTo('n');
                long off = Long.parseLong(offset);
                assertThat(off).isLessThan(pdf.length);
                String esperado = i + " 0 obj";
                assertThat(new String(pdf, (int) off, esperado.length(), LATIN1)).isEqualTo(esperado);
            }
        }
    }

    private static void validarLongitudesDeFlujo(byte[] pdf) {
        int encontrados = 0;
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
            int dictInicio = ultimoIndiceDe(pdf, " obj", idx);
            String dict = new String(pdf, dictInicio, idx - dictInicio, LATIN1);
            int l = dict.indexOf("/Length ") + "/Length ".length();
            int fin = dict.indexOf(' ', l);
            int declarado = Integer.parseInt(dict.substring(l, fin));

            int datos = idx + "stream".length();
            assertThat(pdf[datos]).isEqualTo((byte) '\n');
            datos++;
            assertThat(datos + declarado).isLessThanOrEqualTo(pdf.length);
            // The EOL before endstream is not part of the stream data per ISO 32000-1.
            assertThat(pdf[datos + declarado]).isEqualTo((byte) '\n');
            assertThat(new String(pdf, datos + declarado + 1, 9, LATIN1)).isEqualTo("endstream");
            encontrados++;
        }
        assertThat(encontrados).isGreaterThan(0);
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

    private static long inicioXref(byte[] pdf) {
        int idx = indiceDe(pdf, "startxref", 0);
        int p = idx + "startxref".length();
        while (p < pdf.length && (pdf[p] == '\n' || pdf[p] == '\r' || pdf[p] == ' ')) {
            p++;
        }
        int inicio = p;
        while (p < pdf.length && pdf[p] >= '0' && pdf[p] <= '9') {
            p++;
        }
        return Long.parseLong(new String(pdf, inicio, p - inicio, LATIN1));
    }

    private static int saltarEol(byte[] pdf, int p) {
        if (pdf[p] == '\r') {
            p++;
        }
        if (pdf[p] == '\n') {
            p++;
        }
        return p;
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

    private static int indiceDe(byte[] pdf, char c, int desde) {
        for (int i = Math.max(desde, 0); i < pdf.length; i++) {
            if (pdf[i] == (byte) c) {
                return i;
            }
        }
        return -1;
    }

    private static int ultimoIndiceDe(byte[] pdf, String aguja, int hasta) {
        byte[] bytes = aguja.getBytes(LATIN1);
        for (int i = hasta - bytes.length; i >= 0; i--) {
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

    private static int contar(String texto, String aguja) {
        int total = 0;
        int desde = 0;
        while (true) {
            int idx = texto.indexOf(aguja, desde);
            if (idx < 0) {
                return total;
            }
            total++;
            desde = idx + aguja.length();
        }
    }
}
