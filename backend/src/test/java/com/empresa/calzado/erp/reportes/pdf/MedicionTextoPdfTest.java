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
 * Layout-measurement tests for the shared PDF writer.
 *
 * <p>They pin the defect a rendered ticket showed: text measured with a fixed {@code 0.5 em} per
 * character is laid out as if it were narrower than the Helvetica glyphs the PDF actually
 * declares, so wide values cross the right margin and a truncated single token is still placed as
 * if it fitted. The expected widths use a small AFM oracle local to this test (advance widths from
 * the Adobe Core 14 Helvetica AFMs, in 1/1000 em) so the assertions fail while the writer still
 * uses the {@code 0.5} estimate.
 */
class MedicionTextoPdfTest {

    private static final Charset LATIN1 = StandardCharsets.ISO_8859_1;
    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");
    private static final float MARGEN = 10f;
    private static final float DERECHA = DocumentoPdf.ANCHO_TICKET - MARGEN;
    private static final Pattern TEXTO = Pattern.compile("([-0-9.]+) ([-0-9.]+) Td\\n\\((.*?)\\) Tj");

    @Test
    void unAnchoConocidoSeMideConLasMetricasAfm() {
        float medido = anchoMedido("MMMM", 10f, false);

        // 'M' = 833/1000 em in Helvetica: 4 * 833 * 10 / 1000 = 33.32 puntos, no 20.00.
        assertThat(medido).isCloseTo(4 * 833 * 10f / 1000f, within(0.001f));
        assertThat(medido).isNotCloseTo(4 * 10f * 0.5f, within(0.001f));
    }

    @Test
    void losGlifosAnchosSeMidenMasQueLaEstimacionDeMedioEm() {
        float medido = anchoMedido("WWWWWWWWWW", 10f, false);
        float estimacion = 10 * 10f * 0.5f;

        // 'W' = 944/1000 em: 10 * 944 * 10 / 1000 = 94.40 puntos frente a los 50.00 estimados.
        assertThat(medido).isCloseTo(10 * 944 * 10f / 1000f, within(0.001f));
        assertThat(medido).isGreaterThan(estimacion);
    }

    @Test
    void unTokenLargoDeSetentaYDosCaracteresQuedaContenido() {
        String token = "EFECTIVO".repeat(9);
        assertThat(token).hasSize(72);

        DocumentoPdf doc = ticket();
        doc.filaTabla("R:", token, 7.5f, false);

        List<Texto> textos = extraerTextos(doc.generar());
        for (Texto texto : textos) {
            float ancho = anchoAfm(texto.texto(), 7.5f);
            assertThat(texto.x()).isGreaterThanOrEqualTo(MARGEN - 0.01f);
            assertThat(texto.x() + ancho)
                .as("el fragmento '%s' no puede cruzar el margen derecho", texto.texto())
                .isLessThanOrEqualTo(DERECHA + 0.01f);
        }
    }

    @Test
    void unValorRealistaAlineadoALaDerechaNoCruzaElMargen() {
        for (String valor : List.of("EFECTIVO", "cliente.correo@empresa.com")) {
            DocumentoPdf doc = ticket();
            doc.filaTabla("Correo:", valor, 7.5f, false);

            List<Texto> textos = extraerTextos(doc.generar());
            Texto ultimo = textos.get(textos.size() - 1);
            float ancho = anchoAfm(ultimo.texto(), 7.5f);

            assertThat(ultimo.x() + ancho)
                .as("el valor '%s' debe terminar dentro del margen derecho", valor)
                .isLessThanOrEqualTo(DERECHA + 0.01f);
        }
    }

    @Test
    void losCaracteresAcentuadosSeMidenConSuAnchoReal() {
        // 'Ó' = 778/1000 em y 'ñ' = 556/1000 em: no son un comodín optimista.
        assertThat(anchoMedido("Ó", 10f, false)).isCloseTo(778 * 10f / 1000f, within(0.001f));
        assertThat(anchoMedido("ñ", 10f, false)).isCloseTo(556 * 10f / 1000f, within(0.001f));
    }

    @Test
    void elArrobaElPorcentajeLaIYElEspacioUsanSuAnchoExacto() {
        assertThat(anchoMedido("@", 10f, false)).isCloseTo(1015 * 10f / 1000f, within(0.001f));
        assertThat(anchoMedido("%", 10f, false)).isCloseTo(889 * 10f / 1000f, within(0.001f));
        assertThat(anchoMedido("i", 10f, false)).isCloseTo(222 * 10f / 1000f, within(0.001f));
        assertThat(anchoMedido(" " , 10f, false)).isCloseTo(278 * 10f / 1000f, within(0.001f));
    }

    private static float anchoMedido(String valor, float tamano, boolean negrita) {
        DocumentoPdf doc = ticket();
        doc.filaTabla("R:", valor, tamano, negrita);
        List<Texto> textos = extraerTextos(doc.generar());
        // El valor va alineado a la derecha: su borde izquierdo es el margen menos su ancho.
        return DERECHA - textos.get(textos.size() - 1).x();
    }

    private static DocumentoPdf ticket() {
        return new DocumentoPdf(DocumentoPdf.ANCHO_TICKET, DocumentoPdf.ALTO_TICKET)
            .margenes(MARGEN, MARGEN, MARGEN, MARGEN);
    }

    // ---------------------------------------------------------------------------------------------
    // Oráculo AFM independiente. Solo cubre los glifos que usan estos casos, para que el test
    // falle contra la estimación de 0.5 em sin depender del código de producción.
    // ---------------------------------------------------------------------------------------------

    private static float anchoAfm(String texto, float tamano) {
        int suma = 0;
        for (int i = 0; i < texto.length(); i++) {
            suma += unidades(texto.charAt(i));
        }
        return suma * tamano / 1000f;
    }

    private static int unidades(char c) {
        return switch (c) {
            case ' ' -> 278;
            case '%' -> 889;
            case '.', ':' -> 278;
            case '@' -> 1015;
            case 'C' -> 722;
            case 'E' -> 667;
            case 'F' -> 611;
            case 'I' -> 278;
            case 'M' -> 833;
            case 'O' -> 778;
            case 'R' -> 722;
            case 'T' -> 611;
            case 'V' -> 667;
            case 'W' -> 944;
            case 'a' -> 556;
            case 'c' -> 500;
            case 'e' -> 556;
            case 'i' -> 222;
            case 'l' -> 222;
            case 'm' -> 833;
            case 'n' -> 556;
            case 'o' -> 556;
            case 'p' -> 556;
            case 'r' -> 333;
            case 's' -> 500;
            case 't' -> 278;
            case 'Ó' -> 778;
            case 'ñ' -> 556;
            default -> throw new IllegalArgumentException("sin oráculo AFM para: " + c);
        };
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
    }
}
