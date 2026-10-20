package com.clinica.dental.reportes.pdf;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Minimal hand-written PDF 1.4 writer. It is shared infrastructure for the POS ticket, the
 * invoices and the reports, so it deliberately knows nothing about the domain and nothing about
 * Spring.
 *
 * <p>The correctness property that matters is the cross-reference table: every entry is exactly
 * 20 bytes, every offset is the byte offset of the {@code N 0 obj} header from the start of the
 * file, and {@code startxref} points at the {@code xref} keyword. Offsets are tracked while the
 * bytes are written into a {@link ByteArrayOutputStream}, never recomputed from characters.
 *
 * <p>Text is encoded as Windows-1252 (the font declares {@code /WinAnsiEncoding}), so Spanish
 * labels with accents render correctly, and the literal-string delimiters {@code (}, {@code )} and
 * {@code \} are escaped. All numbers and dates are formatted with {@link Locale#ROOT}.
 */
public final class DocumentoPdf {

    /** 80 mm thermal roll, in points. */
    public static final float ANCHO_TICKET = 226f;
    /** A long virtual roll; content that overflows breaks to a new page. */
    public static final float ALTO_TICKET = 800f;
    public static final float ANCHO_A4 = 595f;
    public static final float ALTO_A4 = 842f;

    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");
    private static final Charset ASCII = StandardCharsets.ISO_8859_1;
    private static final String PRODUCTOR = "Clínica Dental - Generador de documentos";
    private static final float INTERLINEADO = 1.4f;
    /** Minimum horizontal gap between a label and a value sharing the same baseline. */
    private static final float SEPARACION_MINIMA = 4f;

    private final float anchoPagina;
    private final float altoPagina;
    private final List<Pagina> paginas = new ArrayList<>();

    private float margenIzquierdo = 14f;
    private float margenDerecho = 14f;
    private float margenSuperior = 14f;
    private float margenInferior = 20f;

    private Pagina actual;
    private float cursorY;

    public DocumentoPdf(float anchoPagina, float altoPagina) {
        this.anchoPagina = anchoPagina;
        this.altoPagina = altoPagina;
        nuevaPagina();
    }

    public DocumentoPdf margenes(float izquierdo, float derecho, float superior, float inferior) {
        this.margenIzquierdo = izquierdo;
        this.margenDerecho = derecho;
        this.margenSuperior = superior;
        this.margenInferior = inferior;
        this.cursorY = altoPagina - margenSuperior;
        return this;
    }

    /** Absolute text at an explicit baseline position. */
    public void texto(float x, float y, String contenido, float tamano, boolean negrita) {
        actual.operaciones.add(operacionTexto(x, y, contenido, tamano, negrita));
    }

    /**
     * Measured width of {@code contenido} in points, from the AFM metrics of the font the writer
     * declares. This is the single measurement the whole layout uses: right-alignment, wrapping,
     * truncation and centring all route through it, so the arithmetic can never disagree with the
     * glyphs on the page.
     */
    public static float anchoTexto(String contenido, float tamano, boolean negrita) {
        return AnchosHelvetica.ancho(contenido, tamano, negrita);
    }

    /**
     * Reduces {@code texto} until its measured width is at most {@code anchoMax}, appending an
     * ellipsis when there is room for one.
     *
     * <p>This is the containment primitive, and it is a guarantee rather than an estimate. The
     * ellipsis is dropped before the guarantee is: if the three dots alone would exceed
     * {@code anchoMax}, the text is reduced without a marker, and if even a single glyph cannot
     * fit the result is empty instead of a fragment that crosses the margin.
     */
    public static String recortar(String texto, float tamano, boolean negrita, float anchoMax) {
        if (texto == null || texto.isEmpty() || anchoMax <= 0f) {
            return "";
        }
        if (anchoTexto(texto, tamano, negrita) <= anchoMax) {
            return texto;
        }
        String sufijo = anchoTexto("...", tamano, negrita) <= anchoMax ? "..." : "";
        int corte = texto.length();
        while (corte > 0 && anchoTexto(texto.substring(0, corte) + sufijo, tamano, negrita) > anchoMax) {
            corte--;
        }
        return texto.substring(0, corte) + sufijo;
    }

    /**
     * Cursor text; breaks the page automatically when it passes the bottom margin. The content is
     * reduced to the printable width first, so a single line can never cross the right margin.
     */
    public void textoLinea(String contenido, float tamano, boolean negrita) {
        asegurarEspacio();
        String visible = recortar(contenido, tamano, negrita, anchoUtil());
        texto(margenIzquierdo, cursorY, visible, tamano, negrita);
        cursorY -= altoLinea(tamano);
    }

    /**
     * Centred text, reduced to the printable width and centred inside the margins. Centring on the
     * page instead of on the printable region could push an over-wide line past the left margin,
     * so the printable region is the reference.
     */
    public void textoCentrado(String contenido, float tamano, boolean negrita) {
        asegurarEspacio();
        String visible = recortar(contenido, tamano, negrita, anchoUtil());
        float ancho = anchoTexto(visible, tamano, negrita);
        texto(margenIzquierdo + (anchoUtil() - ancho) / 2f, cursorY, visible, tamano, negrita);
        cursorY -= altoLinea(tamano);
    }

    /** A table row: left-aligned label, right-aligned value, optional separator line below. */
    public void filaTabla(String etiqueta, String valor, float tamano, boolean separador) {
        filaTabla(etiqueta, valor, tamano, false, separador);
    }

    public void filaTabla(String etiqueta, String valor, float tamano, boolean negrita, boolean separador) {
        asegurarEspacio();
        float derecha = anchoPagina - margenDerecho;
        String etiquetaVisible = recortar(etiqueta, tamano, negrita, anchoUtil());
        float finEtiqueta = margenIzquierdo + anchoTexto(etiquetaVisible, tamano, negrita);
        float anchoValor = anchoTexto(valor, tamano, negrita);

        if (anchoValor <= 0f || derecha - anchoValor >= finEtiqueta + SEPARACION_MINIMA) {
            // The value fits beside the label: keep the common single-line row.
            texto(margenIzquierdo, cursorY, etiquetaVisible, tamano, negrita);
            texto(derecha - anchoValor, cursorY, valor, tamano, negrita);
            cursorY -= altoLinea(tamano);
        } else {
            // The value would be printed on top of the label. Keep the label on its own line and
            // lay the value on continuation lines that use the full printable width, so the two
            // texts can never overlap and no fragment crosses the right margin.
            texto(margenIzquierdo, cursorY, etiquetaVisible, tamano, negrita);
            cursorY -= altoLinea(tamano);
            float anchoUtil = anchoUtil();
            for (String parte : envolverValor(valor, tamano, negrita, anchoUtil)) {
                asegurarEspacio();
                texto(derecha - anchoTexto(parte, tamano, negrita), cursorY, parte, tamano, negrita);
                cursorY -= altoLinea(tamano);
            }
        }
        if (separador) {
            lineaHorizontal();
        }
    }

    /**
     * Splits a value into fragments that each fit in the printable width, measuring with the real
     * glyph widths. It breaks at spaces so a receipt keeps words whole. A single word wider than
     * the printable width cannot be broken at a space, so it is reduced by {@link #recortar}:
     * the only lossy case, chosen over a character-level break because a mid-word break is
     * unreadable on a tiny roll and the reduction keeps the ink inside the margins.
     */
    private static List<String> envolverValor(String valor, float tamano, boolean negrita, float anchoMax) {
        List<String> lineas = new ArrayList<>();
        if (valor == null || valor.isEmpty() || anchoMax <= 0f) {
            return lineas;
        }
        String resto = valor;
        while (!resto.isEmpty()) {
            if (anchoTexto(resto, tamano, negrita) <= anchoMax) {
                lineas.add(resto);
                break;
            }
            int corte = ultimoEspacioQueCabe(resto, tamano, negrita, anchoMax);
            if (corte <= 0) {
                // A single word does not fit: reduce it so it cannot run off the page. The
                // remainder is dropped, matching the documented lossy single-token case.
                lineas.add(recortar(resto, tamano, negrita, anchoMax));
                break;
            }
            lineas.add(resto.substring(0, corte));
            resto = resto.substring(corte + 1).stripLeading();
        }
        return lineas;
    }

    /** Index of the last space whose prefix already fits in {@code anchoMax}, or -1 if none. */
    private static int ultimoEspacioQueCabe(String texto, float tamano, boolean negrita, float anchoMax) {
        int corte = -1;
        for (int i = 0; i < texto.length(); i++) {
            if (texto.charAt(i) == ' ' && anchoTexto(texto.substring(0, i), tamano, negrita) <= anchoMax) {
                corte = i;
            }
        }
        return corte;
    }

    public void lineaHorizontal() {
        float y = cursorY;
        actual.operaciones.add("0.5 w\n"
            + num(margenIzquierdo) + " " + num(y) + " m "
            + num(anchoPagina - margenDerecho) + " " + num(y) + " l S");
        cursorY -= 6f;
    }

    public void saltoPagina() {
        nuevaPagina();
    }

    public byte[] generar() {
        int totalPaginas = paginas.size();
        int totalObjetos = 5 + 2 * totalPaginas;
        long[] offsets = new long[totalObjetos + 1];

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        escribir(out, "%PDF-1.4\n");

        offsets[1] = out.size();
        escribir(out, "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");

        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < totalPaginas; i++) {
            if (i > 0) {
                kids.append(' ');
            }
            kids.append(7 + 2 * i).append(" 0 R");
        }
        offsets[2] = out.size();
        escribir(out, "2 0 obj\n<< /Type /Pages /Kids [" + kids + "] /Count " + totalPaginas + " >>\nendobj\n");

        offsets[3] = out.size();
        escribir(out, "3 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica "
            + "/Encoding /WinAnsiEncoding >>\nendobj\n");

        offsets[4] = out.size();
        escribir(out, "4 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold "
            + "/Encoding /WinAnsiEncoding >>\nendobj\n");

        offsets[5] = out.size();
        escribir(out, "5 0 obj\n<< /Producer (" + PRODUCTOR + ") /CreationDate (" + fechaPdf() + ") >>\nendobj\n");

        for (int i = 0; i < totalPaginas; i++) {
            int objContenido = 6 + 2 * i;
            int objPagina = 7 + 2 * i;
            byte[] contenido = componerContenido(paginas.get(i));

            offsets[objContenido] = out.size();
            escribir(out, objContenido + " 0 obj\n<< /Length " + contenido.length + " >>\nstream\n");
            escribir(out, contenido);
            escribir(out, "\nendstream\nendobj\n");

            offsets[objPagina] = out.size();
            escribir(out, objPagina + " 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 "
                + num(anchoPagina) + " " + num(altoPagina)
                + "] /Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents "
                + objContenido + " 0 R >>\nendobj\n");
        }

        long inicioXref = out.size();
        escribir(out, "xref\n0 " + (totalObjetos + 1) + "\n");
        escribir(out, "0000000000 65535 f \n");
        for (int i = 1; i <= totalObjetos; i++) {
            escribir(out, String.format(Locale.ROOT, "%010d 00000 n \n", offsets[i]));
        }
        escribir(out, "trailer\n<< /Size " + (totalObjetos + 1) + " /Root 1 0 R /Info 5 0 R >>\n"
            + "startxref\n" + inicioXref + "\n%%EOF\n");

        return out.toByteArray();
    }

    private void nuevaPagina() {
        actual = new Pagina();
        paginas.add(actual);
        cursorY = altoPagina - margenSuperior;
    }

    private void asegurarEspacio() {
        if (cursorY < margenInferior) {
            nuevaPagina();
        }
    }

    private static float altoLinea(float tamano) {
        return tamano * INTERLINEADO;
    }

    /** Printable width between the margins. Every reduction and alignment is bounded by it. */
    private float anchoUtil() {
        return anchoPagina - margenIzquierdo - margenDerecho;
    }

    private static String operacionTexto(float x, float y, String contenido, float tamano, boolean negrita) {
        String fuente = negrita ? "/F2" : "/F1";
        return "BT\n" + fuente + " " + num(tamano) + " Tf\n" + num(x) + " " + num(y) + " Td\n("
            + escapar(contenido) + ") Tj\nET";
    }

    /** Escapes the PDF literal-string delimiters and flattens line breaks. */
    private static String escapar(String contenido) {
        if (contenido == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(contenido.length() + 8);
        for (int i = 0; i < contenido.length(); i++) {
            char c = contenido.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '(' -> sb.append("\\(");
                case ')' -> sb.append("\\)");
                case '\n', '\r' -> sb.append(' ');
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private static byte[] componerContenido(Pagina pagina) {
        if (pagina.operaciones.isEmpty()) {
            return new byte[0];
        }
        return (String.join("\n", pagina.operaciones) + "\n").getBytes(WINDOWS_1252);
    }

    private static String num(float valor) {
        return String.format(Locale.ROOT, "%.2f", valor);
    }

    private static String fechaPdf() {
        OffsetDateTime ahora = OffsetDateTime.now(ZoneOffset.UTC);
        return String.format(Locale.ROOT, "D:%04d%02d%02d%02d%02d%02dZ",
            ahora.getYear(), ahora.getMonthValue(), ahora.getDayOfMonth(),
            ahora.getHour(), ahora.getMinute(), ahora.getSecond());
    }

    private static void escribir(ByteArrayOutputStream out, String texto) {
        out.writeBytes(texto.getBytes(ASCII));
    }

    private static void escribir(ByteArrayOutputStream out, byte[] bytes) {
        out.writeBytes(bytes);
    }

    private static final class Pagina {
        private final List<String> operaciones = new ArrayList<>();
    }
}
