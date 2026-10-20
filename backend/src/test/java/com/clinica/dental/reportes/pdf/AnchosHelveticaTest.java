package com.clinica.dental.reportes.pdf;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Complete-table checks on the AFM advance-width tables.
 *
 * <p>These tests are exhaustive on purpose. The defect they pin was invisible to the previous spot
 * checks: {@code W}, {@code M}, {@code space}, {@code i}, {@code @} and {@code %} all happen to
 * live in rows that were correct, while a shifted ASCII row sat precisely in the entries nobody
 * checked ({@code 0x72} r .. {@code 0x7F}), and the same shift was waiting in the bold face that
 * had never been compared against any reference at all. A spot check therefore proves nothing
 * about a table; only asserting every one of the 256 positions of both faces does.
 *
 * <p>The expected values are the advance widths of the glyph that {@code /WinAnsiEncoding} selects
 * for each Windows-1252 byte, taken from the Adobe Core 14 {@code Helvetica.afm} and
 * {@code Helvetica-Bold.afm} metrics (Adobe's {@code Core14_AFMs.zip}); they were cross-checked
 * against pypdf 6.13.1 {@code _codecs/core_font_metrics.py}, which is generated from those same
 * files. Bytes that {@code /WinAnsiEncoding} leaves unassigned (0x00-0x1F, 0x7F and the five
 * unassigned Windows-1252 positions 0x81, 0x8D, 0x8F, 0x90, 0x9D) have no glyph and are recorded
 * as 0, the value that makes the writer fall through to its conservative unknown-width fallback.
 */
class AnchosHelveticaTest {

    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");
    private static final Charset LATIN1 = StandardCharsets.ISO_8859_1;

    /** Real POS ticket geometry: 226-point roll, 10-point margins, 7.5-point body text. */
    private static final float TAMANO_TICKET = 7.5f;
    private static final float MARGEN = 10f;
    private static final float ANCHO_UTIL = DocumentoPdf.ANCHO_TICKET - 2 * MARGEN;
    private static final float BORDE_IMPRIMIBLE = DocumentoPdf.ANCHO_TICKET - MARGEN;

    private static final Pattern TEXTO = Pattern.compile("([-0-9.]+) ([-0-9.]+) Td\\n\\((.*?)\\) Tj");

    // ---------------------------------------------------------------------------------------------
    // The exhaustive table test.
    // ---------------------------------------------------------------------------------------------

    @Test
    void lasDosTablasCompletasCoincidenConLosAfmDeAdobe() {
        List<String> discrepancias = new ArrayList<>();
        comparar("Helvetica", tabla("NORMAL"), AFM_NORMAL, discrepancias);
        comparar("Helvetica-Bold", tabla("NEGRITA"), AFM_NEGRITA, discrepancias);

        assertThat(discrepancias)
            .as("cada una de las 256 posiciones de ambas fuentes debe coincidir con el AFM de Adobe; "
                + "una sola entrada desplazada o equivocada aparece aqui con su byte")
            .isEmpty();
    }

    private static void comparar(String fuente, int[] tabla, int[] afm, List<String> discrepancias) {
        for (int posicion = 0; posicion < 256; posicion++) {
            if (tabla[posicion] != afm[posicion]) {
                discrepancias.add(String.format(
                    "%s[0x%02X] %-18s actual=%4d esperado=%4d",
                    fuente, posicion, nombreDelByte(posicion), tabla[posicion], afm[posicion]));
            }
        }
    }

    /** Human-readable name for a Windows-1252 byte, so a failure names the glyph and not a count. */
    private static String nombreDelByte(int posicion) {
        try {
            return "'" + new String(new byte[] {(byte) posicion}, WINDOWS_1252) + "'";
        } catch (RuntimeException ex) {
            return "(sin glifo)";
        }
    }

    // ---------------------------------------------------------------------------------------------
    // The previously shifted ASCII row, pinned so the shift cannot return.
    // ---------------------------------------------------------------------------------------------

    @Test
    void elTramoAsciiDesplazadoQuedaFijadoConSuValorAfm() {
        // The row 0x72 r .. 0x7F was shifted one slot: the value of 'q' was duplicated at 'r' and
        // everything after it slid one position, because 's' and 'u' were under-measured while
        // 'r', 't', 'v', 'x', '{', '|' were over-measured. The expected value of every entry is
        // the Adobe Helvetica.afm advance width, named in each assertion.
        assertThat(unidades("r", false)).as("Helvetica 0x72 'r' = 333 en Helvetica.afm").isEqualTo(333f);
        assertThat(unidades("s", false)).as("Helvetica 0x73 's' = 500 en Helvetica.afm").isEqualTo(500f);
        assertThat(unidades("t", false)).as("Helvetica 0x74 't' = 278 en Helvetica.afm").isEqualTo(278f);
        assertThat(unidades("u", false)).as("Helvetica 0x75 'u' = 556 en Helvetica.afm").isEqualTo(556f);
        assertThat(unidades("v", false)).as("Helvetica 0x76 'v' = 500 en Helvetica.afm").isEqualTo(500f);
        assertThat(unidades("w", false)).as("Helvetica 0x77 'w' = 722 en Helvetica.afm").isEqualTo(722f);
        assertThat(unidades("x", false)).as("Helvetica 0x78 'x' = 500 en Helvetica.afm").isEqualTo(500f);
        assertThat(unidades("{", false)).as("Helvetica 0x7B '{' = 334 en Helvetica.afm").isEqualTo(334f);
        assertThat(unidades("|", false)).as("Helvetica 0x7C '|' = 260 en Helvetica.afm").isEqualTo(260f);
        assertThat(unidades("}", false)).as("Helvetica 0x7D '}' = 334 en Helvetica.afm").isEqualTo(334f);
        assertThat(unidades("~", false)).as("Helvetica 0x7E '~' = 584 en Helvetica.afm").isEqualTo(584f);
        assertThat(tabla("NORMAL")[0x7F])
            .as("Helvetica 0x7F no esta asignado en /WinAnsiEncoding: 0, no 584")
            .isEqualTo(0);
    }

    @Test
    void laMismaFilaDesplazadaDeLaNegritaQuedaFijada() {
        // The bold face repeated the identical shift and had never been checked against a
        // reference; Helvetica-Bold.afm supplies the expected value of every entry.
        assertThat(unidades("r", true)).as("Helvetica-Bold 0x72 'r' = 389 en Helvetica-Bold.afm").isEqualTo(389f);
        assertThat(unidades("s", true)).as("Helvetica-Bold 0x73 's' = 556 en Helvetica-Bold.afm").isEqualTo(556f);
        assertThat(unidades("t", true)).as("Helvetica-Bold 0x74 't' = 333 en Helvetica-Bold.afm").isEqualTo(333f);
        assertThat(unidades("u", true)).as("Helvetica-Bold 0x75 'u' = 611 en Helvetica-Bold.afm").isEqualTo(611f);
        assertThat(unidades("v", true)).as("Helvetica-Bold 0x76 'v' = 556 en Helvetica-Bold.afm").isEqualTo(556f);
        assertThat(unidades("w", true)).as("Helvetica-Bold 0x77 'w' = 778 en Helvetica-Bold.afm").isEqualTo(778f);
        assertThat(unidades("x", true)).as("Helvetica-Bold 0x78 'x' = 556 en Helvetica-Bold.afm").isEqualTo(556f);
        assertThat(unidades("z", true)).as("Helvetica-Bold 0x7A 'z' = 500 en Helvetica-Bold.afm").isEqualTo(500f);
        assertThat(unidades("{", true)).as("Helvetica-Bold 0x7B '{' = 389 en Helvetica-Bold.afm").isEqualTo(389f);
        assertThat(unidades("|", true)).as("Helvetica-Bold 0x7C '|' = 280 en Helvetica-Bold.afm").isEqualTo(280f);
        assertThat(unidades("}", true)).as("Helvetica-Bold 0x7D '}' = 389 en Helvetica-Bold.afm").isEqualTo(389f);
        assertThat(unidades("~", true)).as("Helvetica-Bold 0x7E '~' = 584 en Helvetica-Bold.afm").isEqualTo(584f);
        assertThat(tabla("NEGRITA")[0x7F])
            .as("Helvetica-Bold 0x7F no esta asignado en /WinAnsiEncoding: 0, no 584")
            .isEqualTo(0);
    }

    @Test
    void elApostrofoYElAcentoGraveUsanElGlifoQueDeclaraWinAnsi() {
        // /WinAnsiEncoding selects 'quotesingle' at 0x27 and 'grave' at 0x60, not the curly
        // 'quoteright'/'quoteleft' of 0x91/0x92. The old table carried the curly widths (222/278).
        assertThat(unidades("'", false)).as("Helvetica 0x27 'quotesingle' = 191 en Helvetica.afm").isEqualTo(191f);
        assertThat(unidades("'", true)).as("Helvetica-Bold 0x27 'quotesingle' = 238 en Helvetica-Bold.afm").isEqualTo(238f);
        assertThat(unidades("`", false)).as("Helvetica 0x60 'grave' = 333 en Helvetica.afm").isEqualTo(333f);
        assertThat(unidades("`", true)).as("Helvetica-Bold 0x60 'grave' = 333 en Helvetica-Bold.afm").isEqualTo(333f);
    }

    // ---------------------------------------------------------------------------------------------
    // Containment on the real POS repro strings.
    // ---------------------------------------------------------------------------------------------

    @Test
    void elClienteRealQuedaDentroDelBordeImprimible() {
        for (String cliente : List.of("Consumidor Final", "José Ángel Muñoz")) {
            float anchoReal = anchoAfm(cliente, TAMANO_TICKET, false);
            float anchoTabla = AnchosHelvetica.ancho(cliente, TAMANO_TICKET, false);
            // filaTabla places a single-line value right-aligned at BORDE_IMPRIMIBLE, so its left
            // edge is BORDE_IMPRIMIBLE - anchoTabla and the real ink reaches
            // BORDE_IMPRIMIBLE - anchoTabla + anchoReal. With the shifted table anchoTabla was
            // smaller than anchoReal and the ink crossed the edge; with the AFM table the two
            // cancel and the edge is exactly BORDE_IMPRIMIBLE.
            float borde = BORDE_IMPRIMIBLE - anchoTabla + anchoReal;

            DocumentoPdf doc = ticket();
            doc.filaTabla("Cliente:", cliente, TAMANO_TICKET, false);
            Texto colocado = textoDelValor(doc.generar(), cliente);

            assertThat(colocado.x() + anchoReal)
                .as("%s: borde = %.4f - %.4f (tabla) + %.4f (AFM real) = %.4f; debe ser <= %.4f",
                    cliente, BORDE_IMPRIMIBLE, anchoTabla, anchoReal, borde, BORDE_IMPRIMIBLE)
                .isLessThanOrEqualTo(BORDE_IMPRIMIBLE + 0.01f);
        }
    }

    @Test
    void laGarantiaSigueRecortandoElTextoQueNoCabe() {
        // The guarantee must stay a real reduction: text that genuinely cannot fit is cut, not
        // trusted. 206 points of printable width cannot hold this description at 7.5 points.
        String descripcion = "Cuero sintetico premium reforzado con suela antivibracion antideslizante";
        String visible = DocumentoPdf.recortar(descripcion, TAMANO_TICKET, false, ANCHO_UTIL);

        assertThat(visible).as("un texto mas ancho que el area util debe reducirse").isNotEqualTo(descripcion);
        assertThat(anchoAfm(visible, TAMANO_TICKET, false))
            .as("lo que sobrevive al recorte debe medir menos que el ancho util con el AFM real")
            .isLessThanOrEqualTo(ANCHO_UTIL + 0.01f);
    }

    @Test
    void noSeRecortaElTextoQueSiCabeConSuAnchoReal() {
        // Over-truncation repro: 80 'r' measure 80 * 333 * 7.5 / 1000 = 199.80 points, which fits
        // in the 206-point printable width. The shifted table measured them as 333.6 points and cut
        // the token to 50 characters.
        String texto = "r".repeat(80);
        assertThat(anchoAfm(texto, TAMANO_TICKET, false)).isCloseTo(199.8f,
            org.assertj.core.data.Offset.offset(0.01f));
        assertThat(DocumentoPdf.recortar(texto, TAMANO_TICKET, false, ANCHO_UTIL))
            .as("un texto que cabe con su ancho AFM real no puede recortarse")
            .isEqualTo(texto);
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers.
    // ---------------------------------------------------------------------------------------------

    /** A 1000-point size turns the 1/1000-em table value into the width in points. */
    private static float unidades(String texto, boolean negrita) {
        return AnchosHelvetica.ancho(texto, 1000f, negrita);
    }

    /** Reads a private table directly, so an unassigned byte can be asserted as 0 and not 1000. */
    private static int[] tabla(String campo) {
        try {
            Field campo_tabla = AnchosHelvetica.class.getDeclaredField(campo);
            campo_tabla.setAccessible(true);
            return (int[]) campo_tabla.get(null);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("no se pudo leer la tabla " + campo, ex);
        }
    }

    private static DocumentoPdf ticket() {
        return new DocumentoPdf(DocumentoPdf.ANCHO_TICKET, DocumentoPdf.ALTO_TICKET)
            .margenes(MARGEN, MARGEN, MARGEN, MARGEN);
    }

    private static Texto textoDelValor(byte[] pdf, String esperado) {
        for (Texto texto : extraerTextos(pdf)) {
            if (texto.texto().equals(esperado)) {
                return texto;
            }
        }
        throw new AssertionError("no se emitio el texto esperado: " + esperado);
    }

    // ---------------------------------------------------------------------------------------------
    // Independent AFM oracle: the reference widths, kept out of the production classes.
    // ---------------------------------------------------------------------------------------------

    private static float anchoAfm(String texto, float tamano, boolean negrita) {
        int[] afm = negrita ? AFM_NEGRITA : AFM_NORMAL;
        byte[] bytes = texto.getBytes(WINDOWS_1252);
        int suma = 0;
        for (byte b : bytes) {
            int ancho = afm[b & 0xFF];
            suma += ancho == 0 ? 1000 : ancho;
        }
        return suma * tamano / 1000f;
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

    // ---------------------------------------------------------------------------------------------
    // Reference data: the advance widths of the glyph /WinAnsiEncoding selects for each byte.
    // Source: Adobe Core 14 Helvetica.afm / Helvetica-Bold.afm (Core14_AFMs.zip), cross-checked
    // against pypdf 6.13.1 _codecs/core_font_metrics.py. 0 marks a byte with no assigned glyph.
    // ---------------------------------------------------------------------------------------------

    private static final int[] AFM_NORMAL = {
           0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,   // 0x00-0x0F
           0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,   // 0x10-0x1F
         278,  278,  355,  556,  556,  889,  667,  191,  333,  333,  389,  584,  278,  333,  278,  278,   // 0x20-0x2F
         556,  556,  556,  556,  556,  556,  556,  556,  556,  556,  278,  278,  584,  584,  584,  556,   // 0x30-0x3F
        1015,  667,  667,  722,  722,  667,  611,  778,  722,  278,  500,  667,  556,  833,  722,  778,   // 0x40-0x4F
         667,  778,  722,  667,  611,  722,  667,  944,  667,  667,  611,  278,  278,  278,  469,  556,   // 0x50-0x5F
         333,  556,  556,  500,  556,  556,  278,  556,  556,  222,  222,  500,  222,  833,  556,  556,   // 0x60-0x6F
         556,  556,  333,  500,  278,  556,  500,  722,  500,  500,  500,  334,  260,  334,  584,    0,   // 0x70-0x7F
         556,    0,  222,  556,  333, 1000,  556,  556,  333, 1000,  667,  333, 1000,    0,  611,    0,   // 0x80-0x8F
           0,  222,  222,  333,  333,  350,  556, 1000,  333, 1000,  500,  333,  944,    0,  500,  667,   // 0x90-0x9F
         278,  333,  556,  556,  556,  556,  260,  556,  333,  737,  370,  556,  584,  333,  737,  333,   // 0xA0-0xAF
         400,  584,  333,  333,  333,  556,  537,  278,  333,  333,  365,  556,  834,  834,  834,  611,   // 0xB0-0xBF
         667,  667,  667,  667,  667,  667, 1000,  722,  667,  667,  667,  667,  278,  278,  278,  278,   // 0xC0-0xCF
         722,  722,  778,  778,  778,  778,  778,  584,  778,  722,  722,  722,  722,  667,  667,  611,   // 0xD0-0xDF
         556,  556,  556,  556,  556,  556,  889,  500,  556,  556,  556,  556,  278,  278,  278,  278,   // 0xE0-0xEF
         556,  556,  556,  556,  556,  556,  556,  584,  611,  556,  556,  556,  556,  500,  556,  500,   // 0xF0-0xFF
    };

    private static final int[] AFM_NEGRITA = {
           0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,   // 0x00-0x0F
           0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,   // 0x10-0x1F
         278,  333,  474,  556,  556,  889,  722,  238,  333,  333,  389,  584,  278,  333,  278,  278,   // 0x20-0x2F
         556,  556,  556,  556,  556,  556,  556,  556,  556,  556,  333,  333,  584,  584,  584,  611,   // 0x30-0x3F
         975,  722,  722,  722,  722,  667,  611,  778,  722,  278,  556,  722,  611,  833,  722,  778,   // 0x40-0x4F
         667,  778,  722,  667,  611,  722,  667,  944,  667,  667,  611,  333,  278,  333,  584,  556,   // 0x50-0x5F
         333,  556,  611,  556,  611,  556,  333,  611,  611,  278,  278,  556,  278,  889,  611,  611,   // 0x60-0x6F
         611,  611,  389,  556,  333,  611,  556,  778,  556,  556,  500,  389,  280,  389,  584,    0,   // 0x70-0x7F
         556,    0,  278,  556,  500, 1000,  556,  556,  333, 1000,  667,  333, 1000,    0,  611,    0,   // 0x80-0x8F
           0,  278,  278,  500,  500,  350,  556, 1000,  333, 1000,  556,  333,  944,    0,  500,  667,   // 0x90-0x9F
         278,  333,  556,  556,  556,  556,  280,  556,  333,  737,  370,  556,  584,  333,  737,  333,   // 0xA0-0xAF
         400,  584,  333,  333,  333,  611,  556,  278,  333,  333,  365,  556,  834,  834,  834,  611,   // 0xB0-0xBF
         722,  722,  722,  722,  722,  722, 1000,  722,  667,  667,  667,  667,  278,  278,  278,  278,   // 0xC0-0xCF
         722,  722,  778,  778,  778,  778,  778,  584,  778,  722,  722,  722,  722,  667,  667,  611,   // 0xD0-0xDF
         556,  556,  556,  556,  556,  556,  889,  556,  556,  556,  556,  556,  278,  278,  278,  278,   // 0xE0-0xEF
         611,  611,  611,  611,  611,  611,  611,  584,  611,  611,  611,  611,  611,  556,  611,  556,   // 0xF0-0xFF
    };
}
