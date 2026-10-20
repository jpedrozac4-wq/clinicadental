package com.empresa.calzado.erp.catalogo;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the single source of truth of the algorithmic SKU code.
 *
 * <p>The size and colour expectations come from the codes already stored by the seed data
 * ({@code TV001-39} for the Verde variant of model TV001) plus the colour token that the current
 * format appends for every new code.
 */
class CodigoSkuTest {

    @Test
    void integerTallaHasNoDecimals() {
        assertThat(CodigoSku.generar("T", "V", 1, new BigDecimal("39.0"), "VE")).isEqualTo("TV001-39-VE");
        assertThat(CodigoSku.generar("T", "V", 1, new BigDecimal("40.0"), "VE")).isEqualTo("TV001-40-VE");
    }

    @Test
    void halfTallaKeepsOneDecimal() {
        assertThat(CodigoSku.generar("T", "V", 1, new BigDecimal("39.5"), "VE")).isEqualTo("TV001-39.5-VE");
        assertThat(CodigoSku.generar("T", "V", 1, new BigDecimal("40.5"), "VE")).isEqualTo("TV001-40.5-VE");
    }

    @Test
    void tallaDoesNotKeepTrailingZeros() {
        assertThat(CodigoSku.generar("T", "V", 1, new BigDecimal("39.50"), "VE")).isEqualTo("TV001-39.5-VE");
    }

    @Test
    void correlativoIsPaddedToThreeDigits() {
        assertThat(CodigoSku.generar("T", "V", 1, new BigDecimal("39.0"), "VE")).isEqualTo("TV001-39-VE");
        assertThat(CodigoSku.generar("T", "V", 12, new BigDecimal("39.0"), "VE")).isEqualTo("TV012-39-VE");
        assertThat(CodigoSku.generar("T", "V", 999, new BigDecimal("39.0"), "VE")).isEqualTo("TV999-39-VE");
    }

    @Test
    void keepsTheSeedIdentityInTheCode() {
        assertThat(CodigoSku.generar("T", "V", 1, new BigDecimal("39.0"), "VE")).startsWith("TV001-39");
        assertThat(CodigoSku.generar("B", "G", 12, new BigDecimal("40.0"), "CA")).startsWith("BG012-40");
        assertThat(CodigoSku.generar("T", "V", 2, new BigDecimal("39.0"), "BL")).startsWith("TV002-39");
    }

    @Test
    void rejectsNullLetterCodesTallaAndColorToken() {
        assertThatThrownBy(() -> CodigoSku.generar(null, "V", 1, new BigDecimal("39.0"), "VE"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CodigoSku.generar("T", null, 1, new BigDecimal("39.0"), "VE"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CodigoSku.generar("T", "V", 1, null, "VE"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CodigoSku.generar("T", "V", 1, new BigDecimal("39.0"), " "))
            .isInstanceOf(CatalogoBusinessException.class);
    }

    @Test
    void rejectsCorrelativoOutsideOneToNineNineNine() {
        assertThatThrownBy(() -> CodigoSku.generar("T", "V", 0, new BigDecimal("39.0"), "VE"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CodigoSku.generar("T", "V", 1000, new BigDecimal("39.0"), "VE"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void colourTokenUsesTwoLettersWhenThereAreNoSiblings() {
        assertThat(CodigoSku.tokenColor("Verde", List.of())).isEqualTo("VE");
        assertThat(CodigoSku.tokenColor("Negro", List.of())).isEqualTo("NE");
        assertThat(CodigoSku.tokenColor("Blanco", List.of())).isEqualTo("BL");
    }

    @Test
    void colourTokenFoldsAccentsAndDropsSeparators() {
        assertThat(CodigoSku.tokenColor("Café", List.of())).isEqualTo("CA");
        assertThat(CodigoSku.tokenColor("Azul Marino", List.of())).isEqualTo("AZ");
    }

    @Test
    void colourTokenGrowsUntilItDisambiguatesSiblings() {
        // Both names start with AZ, so two letters would collide inside the same model.
        assertThat(CodigoSku.tokenColor("Azul Marino", List.of("Azul Cielo"))).isEqualTo("AZULM");
        assertThat(CodigoSku.tokenColor("Azul Cielo", List.of("Azul Marino"))).isEqualTo("AZULC");
        assertThat(CodigoSku.tokenColor("Negro", List.of("Verde"))).isEqualTo("NE");
    }

    @Test
    void colourTokenFailsWhenTheNameHasNoUsableLetters() {
        assertThatThrownBy(() -> CodigoSku.tokenColor(null, List.of()))
            .isInstanceOf(CatalogoBusinessException.class);
        assertThatThrownBy(() -> CodigoSku.tokenColor("---", List.of()))
            .isInstanceOf(CatalogoBusinessException.class);
    }

    @Test
    void colourTokenFailsWhenItCannotDisambiguateWithinSixLetters() {
        assertThatThrownBy(() -> CodigoSku.tokenColor("Azulmarino", List.of("Azulmarino Segundo")))
            .isInstanceOf(CatalogoBusinessException.class)
            .hasMessageContaining("token de color");
    }
}
