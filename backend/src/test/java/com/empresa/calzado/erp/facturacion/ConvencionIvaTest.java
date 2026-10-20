package com.empresa.calzado.erp.facturacion;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exhaustive tests for the single tax convention the whole invoicing code shares (project Decision
 * 33): prices are GROSS with VAT included, so the document subtotal and tax are extracted from the
 * gross total and the identity {@code subtotal + iva == total} holds by construction.
 *
 * <p>The list below deliberately mixes exact and non-exact divisions, plus the live values already
 * observed (65.00, 89.29 + 10.71 == 100.00, 58.04, 71.93). A spot check on a single amount would
 * not prove the rule; every case is asserted.
 */
class ConvencionIvaTest {

    private static final List<String> MONTOS_BRUTOS = List.of(
        "0.00", "0.01", "0.02", "0.05", "0.10", "0.99",
        "1.00", "1.12", "2.24", "7.77", "10.00", "50.00",
        "58.04", "65.00", "71.93", "89.29", "100.00", "123.45",
        "333.33", "999.99", "1000.00", "123456.78"
    );

    @Test
    void laTasaEsUnUnicoValorNombradoDelDocePorCiento() {
        assertThat(ConvencionIva.TASA).isEqualByComparingTo("0.12");
        assertThat(ConvencionIva.DIVISOR).isEqualByComparingTo("1.12");
    }

    @Test
    void subtotalMasIvaEsExactamenteElTotalParaTodosLosMontos() {
        for (String bruto : MONTOS_BRUTOS) {
            BigDecimal total = new BigDecimal(bruto);
            ConvencionIva.Desglose desglose = ConvencionIva.desglosar(total);

            assertThat(desglose.total())
                .as("total de %s", bruto)
                .isEqualByComparingTo(total);
            assertThat(desglose.subtotal().add(desglose.iva()))
                .as("subtotal + iva de %s", bruto)
                .isEqualByComparingTo(desglose.total());
        }
    }

    @Test
    void elSubtotalEsElBrutoDivididoPorUnoPuntoDoceConRedondeoHalfUp() {
        for (String bruto : MONTOS_BRUTOS) {
            BigDecimal total = new BigDecimal(bruto);
            BigDecimal esperado = total.divide(ConvencionIva.DIVISOR, 2, RoundingMode.HALF_UP);

            assertThat(ConvencionIva.desglosar(total).subtotal())
                .as("subtotal de %s", bruto)
                .isEqualByComparingTo(esperado);
        }
    }

    @Test
    void elIvaEsElRestoDelTotalNuncaElDocePorCientoDelSubtotalBruto() {
        // The defect this convention replaces: iva = subtotal * 0.12 computed on a VAT-inclusive
        // base. Here the tax is defined as total - subtotal, so the identity cannot break.
        ConvencionIva.Desglose cien = ConvencionIva.desglosar(new BigDecimal("100.00"));
        assertThat(cien.subtotal()).isEqualByComparingTo("89.29");
        assertThat(cien.iva()).isEqualByComparingTo("10.71");
        assertThat(cien.subtotal().add(cien.iva())).isEqualByComparingTo("100.00");

        // 65.00 / 1.12 does not come out exact (58.0357...): HALF_UP gives 58.04 and the tax is the
        // 6.96 remainder, so the identity survives a non-exact division.
        ConvencionIva.Desglose sesentaYCinco = ConvencionIva.desglosar(new BigDecimal("65.00"));
        assertThat(sesentaYCinco.subtotal()).isEqualByComparingTo("58.04");
        assertThat(sesentaYCinco.iva()).isEqualByComparingTo("6.96");
        assertThat(sesentaYCinco.subtotal().add(sesentaYCinco.iva())).isEqualByComparingTo("65.00");
    }

    @Test
    void ningunComponenteEsNegativo() {
        for (String bruto : MONTOS_BRUTOS) {
            ConvencionIva.Desglose desglose = ConvencionIva.desglosar(new BigDecimal(bruto));
            assertThat(desglose.subtotal()).as("subtotal de %s", bruto)
                .isGreaterThanOrEqualTo(BigDecimal.ZERO);
            assertThat(desglose.iva()).as("iva de %s", bruto)
                .isGreaterThanOrEqualTo(BigDecimal.ZERO);
        }
    }

    @Test
    void elBrutoNuloSeTrataComoCero() {
        ConvencionIva.Desglose desglose = ConvencionIva.desglosar(null);

        assertThat(desglose.subtotal()).isEqualByComparingTo("0.00");
        assertThat(desglose.iva()).isEqualByComparingTo("0.00");
        assertThat(desglose.total()).isEqualByComparingTo("0.00");
    }

    @Test
    void losComponentesSiempreTienenDosDecimales() {
        ConvencionIva.Desglose desglose = ConvencionIva.desglosar(new BigDecimal("65.005"));
        assertThat(desglose.subtotal().scale()).isEqualTo(2);
        assertThat(desglose.iva().scale()).isEqualTo(2);
        assertThat(desglose.total().scale()).isEqualTo(2);
    }
}
