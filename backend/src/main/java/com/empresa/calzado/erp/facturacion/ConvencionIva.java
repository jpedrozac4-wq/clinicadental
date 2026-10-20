package com.empresa.calzado.erp.facturacion;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The one tax convention the whole invoicing code shares (project Decision 33): catalogue prices
 * are GROSS with VAT included, so a document's subtotal and tax are extracted from the gross total.
 *
 * <p>Defining the tax as {@code total - subtotal} instead of {@code subtotal * rate} makes
 * {@code subtotal + iva == total} true by construction, including when {@code total / 1.12} does not
 * come out exact. The previous code multiplied a VAT-inclusive base by the rate (double taxation in
 * the document) and left the total on a different base entirely.
 *
 * <p>The rate lives here once and is reused by the web invoice, the POS sale document and the credit
 * note.
 */
public final class ConvencionIva {

    /** The Ecuadorian VAT rate applied to the gross price. The only place the rate is written. */
    public static final BigDecimal TASA = new BigDecimal("0.12");

    /** Divisor that extracts the net subtotal from a gross, VAT-inclusive amount. */
    public static final BigDecimal DIVISOR = BigDecimal.ONE.add(TASA);

    private ConvencionIva() {
    }

    /**
     * Breakdown of a gross amount into net subtotal, tax and total. {@code subtotal + iva} always
     * equals {@code total} because {@code iva} is defined as the remainder.
     */
    public record Desglose(BigDecimal subtotal, BigDecimal iva, BigDecimal total) {
    }

    /**
     * Extracts the tax breakdown from a gross, VAT-inclusive amount, rounding HALF_UP to the two
     * decimals the {@code NUMERIC(10,2)} columns store.
     */
    public static Desglose desglosar(BigDecimal totalBruto) {
        BigDecimal total = totalBruto == null
            ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
            : totalBruto.setScale(2, RoundingMode.HALF_UP);
        BigDecimal subtotal = total.divide(DIVISOR, 2, RoundingMode.HALF_UP);
        BigDecimal iva = total.subtract(subtotal).setScale(2, RoundingMode.HALF_UP);
        return new Desglose(subtotal, iva, total);
    }
}
