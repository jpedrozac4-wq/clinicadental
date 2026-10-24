package com.clinica.dental.factura;

import java.util.Locale;

/**
 * Numeracion correlativa del comprobante (S9).
 *
 * <p>La serie visible es {@code F-000001}, {@code F-000002}, ... El siguiente numero se deriva del
 * ultimo emitido, no de un contador en memoria ni de una secuencia de base de datos: una secuencia
 * dejaria huecos cada vez que una emision se rechaza o falla, y el requisito los prohibe.
 */
public final class NumeroFactura {

    private static final String PREFIJO = "F-";
    private static final int DIGITOS = 6;

    private NumeroFactura() {
    }

    /** Numero que sigue a {@code ultimoNumero}; con {@code null} arranca la serie en {@code F-000001}. */
    public static String siguiente(String ultimoNumero) {
        return formatear(correlativo(ultimoNumero) + 1);
    }

    private static int correlativo(String numero) {
        if (numero == null || numero.isBlank()) {
            return 0;
        }
        String digitos = numero.replaceAll("[^0-9]", "");
        if (digitos.isEmpty()) {
            throw new IllegalStateException("Numero de factura ilegible: " + numero);
        }
        try {
            return Integer.parseInt(digitos);
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("Numero de factura ilegible: " + numero, ex);
        }
    }

    private static String formatear(int correlativo) {
        return PREFIJO + String.format(Locale.ROOT, "%0" + DIGITOS + "d", correlativo);
    }
}
