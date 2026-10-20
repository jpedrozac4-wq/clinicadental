package com.empresa.calzado.erp.facturacion;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The gateway commission formula shared by the web checkout and the POS sale:
 * {@code monto * comision_porcentaje / 100 + comision_fija}, rounded HALF_UP to two decimals.
 *
 * <p>The rate is not a literal here: it comes from the {@code cat_pago_metodo} row, so a cash method
 * seeded with {@code 0.00 / 0.00} retains no commission and the full amount becomes its net.
 */
public final class ComisionPasarela {

    private ComisionPasarela() {
    }

    public static BigDecimal calcular(PagoMetodo metodoPago, BigDecimal montoBruto) {
        if (metodoPago == null || montoBruto == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return montoBruto
            .multiply(metodoPago.getComisionPorcentaje())
            .divide(BigDecimal.valueOf(100))
            .add(metodoPago.getComisionFija())
            .setScale(2, RoundingMode.HALF_UP);
    }
}
