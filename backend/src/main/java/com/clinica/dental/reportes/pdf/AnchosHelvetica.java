package com.clinica.dental.reportes.pdf;

import java.nio.charset.Charset;

/**
 * Advance widths of the two fonts {@link DocumentoPdf} declares, {@code Helvetica} and
 * {@code Helvetica-Bold}, both with {@code /WinAnsiEncoding}.
 *
 * <p>The numbers are the Adobe Core 14 AFM advance widths (units of 1/1000 em) taken from the
 * {@code Helvetica.afm} and {@code Helvetica-Bold.afm} metrics, and they are embedded in the
 * source on purpose. The writer runs in a headless container where the installed fonts are not
 * deterministic, so measuring through {@code java.awt.Font}/{@code FontMetrics} could return the
 * widths of a different face than the PDF declares; the PDF declares exactly these two fonts, so
 * the only correct width data is the one that ships with the declared fonts.
 *
 * <p>Each table is indexed by the Windows-1252 byte the writer emits for a character, because that
 * is the encoding the font selector uses. A byte the table does not define (the five unassigned
 * Windows-1252 positions) measures as {@link #ANCHO_DESCONOCIDO} units, a deliberately generous
 * fallback: over-estimating keeps the ink inside the margin, under-estimating would not.
 */
public final class AnchosHelvetica {

    /** AFM widths are expressed in units of 1/1000 em. */
    public static final float UNIDADES_POR_EM = 1000f;

    /**
     * Width used for any byte the tables do not define. 1000/1000 em is wider than every glyph in
     * both faces except Helvetica's {@code @} (1015), so an unknown byte can never be laid out as
     * if it were narrower than it is; it is the conservative side of the guarantee.
     */
    private static final int ANCHO_DESCONOCIDO = 1000;

    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    private static final int[] NORMAL = new int[256];
    private static final int[] NEGRITA = new int[256];

    static {
        // ASCII 0x20..0x7F, audited entry by entry against Helvetica.afm / Helvetica-Bold.afm.
        // 0x27 selects /quotesingle (191/238) and 0x60 selects /grave (333/333); neither is the
        // curly quoteright/quoteleft glyph that /WinAnsiEncoding selects for 0x91/0x92.
        int[] asciiNormal = {
            278, 278, 355, 556, 556, 889, 667, 191, 333, 333, 389, 584, 278, 333, 278, 278,
            556, 556, 556, 556, 556, 556, 556, 556, 556, 556,
            278, 278, 584, 584, 584, 556, 1015,
            667, 667, 722, 722, 667, 611, 778, 722, 278, 500, 667, 556, 833, 722, 778, 667,
            778, 722, 667, 611, 722, 667, 944, 667, 667, 611,
            278, 278, 278, 469, 556, 333,
            556, 556, 500, 556, 556, 278, 556, 556, 222, 222, 500, 222, 833, 556, 556, 556,
            556, 333, 500, 278, 556, 500, 722, 500, 500, 500, 334,
            260, 334, 584, 0
        };
        int[] asciiNegrita = {
            278, 333, 474, 556, 556, 889, 722, 238, 333, 333, 389, 584, 278, 333, 278, 278,
            556, 556, 556, 556, 556, 556, 556, 556, 556, 556,
            333, 333, 584, 584, 584, 611, 975,
            722, 722, 722, 722, 667, 611, 778, 722, 278, 556, 722, 611, 833, 722, 778, 667,
            778, 722, 667, 611, 722, 667, 944, 667, 667, 611,
            333, 278, 333, 584, 556, 333,
            556, 611, 556, 611, 556, 333, 611, 611, 278, 278, 556, 278, 889, 611, 611, 611,
            611, 389, 556, 333, 611, 556, 778, 556, 556, 500, 389,
            280, 389, 584, 0
        };
        System.arraycopy(asciiNormal, 0, NORMAL, 0x20, asciiNormal.length);
        System.arraycopy(asciiNegrita, 0, NEGRITA, 0x20, asciiNegrita.length);

        // Windows-1252 0x80..0x9F. The unassigned bytes 0x81, 0x8D, 0x8F, 0x90 and 0x9D keep 0
        // and fall through to ANCHO_DESCONOCIDO.
        NORMAL[0x80] = 556;
        NEGRITA[0x80] = 556;
        NORMAL[0x82] = 222;
        NEGRITA[0x82] = 278;
        NORMAL[0x83] = 556;
        NEGRITA[0x83] = 556;
        NORMAL[0x84] = 333;
        NEGRITA[0x84] = 500;
        NORMAL[0x85] = 1000;
        NEGRITA[0x85] = 1000;
        NORMAL[0x86] = 556;
        NEGRITA[0x86] = 556;
        NORMAL[0x87] = 556;
        NEGRITA[0x87] = 556;
        NORMAL[0x88] = 333;
        NEGRITA[0x88] = 333;
        NORMAL[0x89] = 1000;
        NEGRITA[0x89] = 1000;
        NORMAL[0x8A] = 667;
        NEGRITA[0x8A] = 667;
        NORMAL[0x8B] = 333;
        NEGRITA[0x8B] = 333;
        NORMAL[0x8C] = 1000;
        NEGRITA[0x8C] = 1000;
        NORMAL[0x8E] = 611;
        NEGRITA[0x8E] = 611;
        NORMAL[0x91] = 222;
        NEGRITA[0x91] = 278;
        NORMAL[0x92] = 222;
        NEGRITA[0x92] = 278;
        NORMAL[0x93] = 333;
        NEGRITA[0x93] = 500;
        NORMAL[0x94] = 333;
        NEGRITA[0x94] = 500;
        NORMAL[0x95] = 350;
        NEGRITA[0x95] = 350;
        NORMAL[0x96] = 556;
        NEGRITA[0x96] = 556;
        NORMAL[0x97] = 1000;
        NEGRITA[0x97] = 1000;
        NORMAL[0x98] = 333;
        NEGRITA[0x98] = 333;
        NORMAL[0x99] = 1000;
        NEGRITA[0x99] = 1000;
        NORMAL[0x9A] = 500;
        NEGRITA[0x9A] = 556;
        NORMAL[0x9B] = 333;
        NEGRITA[0x9B] = 333;
        NORMAL[0x9C] = 944;
        NEGRITA[0x9C] = 944;
        NORMAL[0x9E] = 500;
        NEGRITA[0x9E] = 500;
        NORMAL[0x9F] = 667;
        NEGRITA[0x9F] = 667;

        // Latin-1 letters and symbols 0xA0..0xFF.
        int[] altoNormal = {
            278, 333, 556, 556, 556, 556, 260, 556, 333, 737, 370, 556, 584, 333, 737, 333,
            400, 584, 333, 333, 333, 556, 537, 278, 333, 333, 365, 556, 834, 834, 834, 611,
            667, 667, 667, 667, 667, 667, 1000, 722, 667, 667, 667, 667, 278, 278, 278, 278,
            722, 722, 778, 778, 778, 778, 778, 584, 778, 722, 722, 722, 722, 667, 667, 611,
            556, 556, 556, 556, 556, 556, 889, 500, 556, 556, 556, 556, 278, 278, 278, 278,
            556, 556, 556, 556, 556, 556, 556, 584, 611, 556, 556, 556, 556, 500, 556, 500
        };
        int[] altoNegrita = {
            278, 333, 556, 556, 556, 556, 280, 556, 333, 737, 370, 556, 584, 333, 737, 333,
            400, 584, 333, 333, 333, 611, 556, 278, 333, 333, 365, 556, 834, 834, 834, 611,
            722, 722, 722, 722, 722, 722, 1000, 722, 667, 667, 667, 667, 278, 278, 278, 278,
            722, 722, 778, 778, 778, 778, 778, 584, 778, 722, 722, 722, 722, 667, 667, 611,
            556, 556, 556, 556, 556, 556, 889, 556, 556, 556, 556, 556, 278, 278, 278, 278,
            611, 611, 611, 611, 611, 611, 611, 584, 611, 611, 611, 611, 611, 556, 611, 556
        };
        System.arraycopy(altoNormal, 0, NORMAL, 0xA0, altoNormal.length);
        System.arraycopy(altoNegrita, 0, NEGRITA, 0xA0, altoNegrita.length);
    }

    private AnchosHelvetica() {
    }

    /**
     * Measured advance width of {@code contenido} in points at {@code tamano}, using the metrics of
     * the font the writer will select: {@code Helvetica-Bold} when {@code negrita} is true and
     * {@code Helvetica} otherwise.
     *
     * <p>The string is encoded exactly as the writer encodes it (Windows-1252), then each emitted
     * byte is looked up in the table, so the measurement cannot drift from the bytes on the page.
     */
    public static float ancho(String contenido, float tamano, boolean negrita) {
        if (contenido == null || contenido.isEmpty()) {
            return 0f;
        }
        int[] tabla = negrita ? NEGRITA : NORMAL;
        byte[] bytes = contenido.getBytes(WINDOWS_1252);
        int suma = 0;
        for (byte b : bytes) {
            int ancho = tabla[b & 0xFF];
            suma += ancho == 0 ? ANCHO_DESCONOCIDO : ancho;
        }
        return suma * tamano / UNIDADES_POR_EM;
    }
}
