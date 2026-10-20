package com.empresa.calzado.erp.reportes.pdf;

import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Layout tests for the shared row primitive. They pin down the defect a printed ticket showed: a
 * right-aligned value wider than the gap the label leaves was drawn on top of the label.
 *
 * <p>The tests read the real emitted content stream and re-derive the width with the same AFM
 * metrics the layout uses, so the geometry they assert is the geometry on the page and not the old
 * {@code longitud * tamano * 0.5} estimate.
 */
class DocumentoPdfFilaTablaTest {

    private static final Charset LATIN1 = StandardCharsets.ISO_8859_1;
    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");
    private static final float TAMANO = 7.5f;
    private static final float MARGEN = 10f;
    private static final String VALOR_LARGO =
        "Cobro en efectivo en caja del punto de venta (POS)";

    private static final Pattern TEXTO = Pattern.compile("([-0-9.]+) ([-0-9.]+) Td\\n\\((.*?)\\) Tj");

    @Test
    void unValorLargoNoSeSuperponeConLaEtiqueta() {
        DocumentoPdf doc = ticket();
        doc.filaTabla("Método de pago:", VALOR_LARGO, TAMANO, false);

        List<Texto> textos = extraerTextos(doc.generar());

        assertThat(choques(textos))
            .as("la etiqueta y el valor no pueden compartir baseline solapándose")
            .isEmpty();
    }

    @Test
    void unValorCortoSeAlineaALaDerechaEnLaMismaLinea() {
        DocumentoPdf doc = ticket();
        doc.filaTabla("Subtotal:", "123.45", TAMANO, false);

        List<Texto> textos = extraerTextos(doc.generar());

        assertThat(textos).hasSize(2);
        Texto etiqueta = textos.get(0);
        Texto valor = textos.get(1);
        assertThat(valor.y()).isEqualTo(etiqueta.y());
        assertThat(valor.x()).isCloseTo(DocumentoPdf.ANCHO_TICKET - MARGEN - valor.ancho(), within(0.01f));
    }

    @Test
    void unaPalabraMasAnchaQueElAnchoUtilQuedaContenida() {
        DocumentoPdf doc = ticket();
        doc.filaTabla("Referencia:", "X".repeat(120), TAMANO, false);

        List<Texto> textos = extraerTextos(doc.generar());

        for (Texto texto : textos) {
            assertThat(texto.x()).isGreaterThanOrEqualTo(MARGEN - 0.01f);
            assertThat(texto.x() + texto.ancho()).isLessThanOrEqualTo(DocumentoPdf.ANCHO_TICKET - MARGEN + 0.01f);
        }
        assertThat(choques(textos)).isEmpty();
    }

    private static DocumentoPdf ticket() {
        return new DocumentoPdf(DocumentoPdf.ANCHO_TICKET, DocumentoPdf.ALTO_TICKET)
            .margenes(MARGEN, MARGEN, MARGEN, MARGEN);
    }

    private static List<String> choques(List<Texto> textos) {
        List<String> encontrados = new ArrayList<>();
        for (int i = 0; i < textos.size(); i++) {
            for (int j = i + 1; j < textos.size(); j++) {
                Texto a = textos.get(i);
                Texto b = textos.get(j);
                if (Math.abs(a.y() - b.y()) > 0.01f) {
                    continue;
                }
                float finA = a.x() + a.ancho();
                float finB = b.x() + b.ancho();
                if (a.x() < finB && b.x() < finA) {
                    encontrados.add(a.texto() + " [" + a.x() + ".." + finA + "] choca con "
                        + b.texto() + " [" + b.x() + ".." + finB + "]");
                }
            }
        }
        return encontrados;
    }

    private static List<Texto> extraerTextos(byte[] pdf) {
        String contenido = contenidoDeFlujos(pdf);
        List<Texto> textos = new ArrayList<>();
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
            sb.append(new String(pdf, datos, fin - datos - 1, WINDOWS_1252));
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
            return AnchosHelvetica.ancho(texto, TAMANO, false);
        }
    }
}
