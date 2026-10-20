package com.empresa.calzado.erp.catalogo;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Single source of truth for the algorithmic SKU code.
 *
 * <p>Format: {@code [tipoLetra][marcaLetra][correlativo padded to 3]-[tallaEur]-[colorToken]}. For
 * example, {@code T} + {@code V} + {@code 1} + {@code 39.0} + {@code VE} yields {@code TV001-39-VE}.
 *
 * <p>The colour token is part of the code because {@code calzado_sku.sku} is globally unique and the
 * POS and the checkout resolve a sale through {@link CalzadoSkuRepository#findBySku}. Without the
 * colour, two variants of the same model at the same size would derive the same code and the second
 * insert would fail against the unique constraint.
 *
 * <p>The size is rendered with the minimum number of decimals: an integral value has no decimal
 * part ({@code 39.0} -> {@code 39}) while a half size keeps exactly one ({@code 39.5} ->
 * {@code 39.5}). That is why the previous {@code intValue()} truncation cannot be used: it would
 * collide the codes of sizes {@code 39} and {@code 39.5}.
 */
public final class CodigoSku {

    private static final int CORRELATIVO_MIN = 1;
    private static final int CORRELATIVO_MAX = 999;
    private static final int TOKEN_COLOR_MIN = 2;
    /** Upper bound so the code stays far below the 30-character column. */
    private static final int TOKEN_COLOR_MAX = 6;

    private CodigoSku() {
    }

    /**
     * Builds the SKU code from the model identity, the size and the colour token.
     *
     * @throws IllegalArgumentException when a letter code or the size is null, or when the
     *     correlativo is outside 1..999
     * @throws CatalogoBusinessException when the colour token is missing or has an unusable shape
     */
    public static String generar(
        String codigoLetraTipo,
        String codigoLetraMarca,
        int correlativo,
        BigDecimal tallaEur,
        String tokenColor
    ) {
        if (codigoLetraTipo == null) {
            throw new IllegalArgumentException("El codigo de letra del tipo de calzado es obligatorio");
        }
        if (codigoLetraMarca == null) {
            throw new IllegalArgumentException("El codigo de letra de la marca es obligatorio");
        }
        if (tallaEur == null) {
            throw new IllegalArgumentException("La talla es obligatoria");
        }
        if (correlativo < CORRELATIVO_MIN || correlativo > CORRELATIVO_MAX) {
            throw new IllegalArgumentException("El correlativo debe estar entre 1 y 999");
        }
        if (tokenColor == null || tokenColor.isBlank()) {
            throw new CatalogoBusinessException("El token de color es obligatorio para generar el codigo de SKU");
        }
        return String.format(
            Locale.ROOT,
            "%s%s%03d-%s-%s",
            codigoLetraTipo,
            codigoLetraMarca,
            correlativo,
            formatoTalla(tallaEur),
            tokenColor);
    }

    /**
     * Derives the shortest colour token that is unique among the sibling colour names of the same
     * model.
     *
     * <p>Two letters are enough for a normal colour ({@code Verde} -> {@code VE}). When two sibling
     * colours share a prefix the token grows until it disambiguates them ({@code Azul Marino} ->
     * {@code AZULM} next to {@code Azul Cielo} -> {@code AZULC}), so the collision never reaches the
     * database as a duplicated code. Existing codes are never recomputed, so adding a colour later
     * cannot rewrite a code already printed or scanned.
     *
     * @param nombreColor              colour name of the variant being coded
     * @param otrosNombresDeColor      colour names of the other variants of the same model
     * @throws CatalogoBusinessException when the name has no usable letters or cannot be
     *     disambiguated within {@value #TOKEN_COLOR_MAX} letters
     */
    public static String tokenColor(String nombreColor, Collection<String> otrosNombresDeColor) {
        String base = normalizar(nombreColor);
        if (base.length() < TOKEN_COLOR_MIN) {
            throw new CatalogoBusinessException(
                "El nombre del color debe tener al menos " + TOKEN_COLOR_MIN + " letras: " + nombreColor);
        }
        List<String> otros = otrosNombresDeColor == null
            ? List.of()
            : otrosNombresDeColor.stream().filter(nombre -> nombre != null).map(CodigoSku::normalizar).toList();

        int maximo = Math.min(TOKEN_COLOR_MAX, base.length());
        for (int longitud = TOKEN_COLOR_MIN; longitud <= maximo; longitud++) {
            String candidato = base.substring(0, longitud);
            if (otros.stream().noneMatch(otro -> otro.startsWith(candidato))) {
                return candidato;
            }
        }
        throw new CatalogoBusinessException(
            "No se pudo derivar un token de color unico para " + nombreColor + " con un maximo de "
                + TOKEN_COLOR_MAX + " letras; renombre el color");
    }

    private static String formatoTalla(BigDecimal tallaEur) {
        return tallaEur.stripTrailingZeros().toPlainString();
    }

    /** Uppercase A-Z only: accents are folded so the code stays printable ASCII. */
    private static String normalizar(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new CatalogoBusinessException("El nombre del color es obligatorio");
        }
        String sinAcentos = Normalizer.normalize(valor, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        String soloLetras = sinAcentos.toUpperCase(Locale.ROOT).replaceAll("[^A-Z]", "");
        if (soloLetras.isEmpty()) {
            throw new CatalogoBusinessException("El nombre del color no tiene letras utilizables: " + valor);
        }
        return soloLetras;
    }
}
