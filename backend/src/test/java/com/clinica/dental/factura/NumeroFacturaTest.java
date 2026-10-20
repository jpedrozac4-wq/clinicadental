package com.clinica.dental.factura;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Numeracion correlativa del comprobante: serie F-000001, F-000002, ... sin huecos (S9). */
class NumeroFacturaTest {

    @Test
    void laSerieArrancaEnF000001() {
        assertThat(NumeroFactura.siguiente(null)).isEqualTo("F-000001");
    }

    @Test
    void elNumeroSiguienteContinuaLaSerieCorrelativa() {
        assertThat(NumeroFactura.siguiente("F-000001")).isEqualTo("F-000002");
        assertThat(NumeroFactura.siguiente("F-000009")).isEqualTo("F-000010");
        assertThat(NumeroFactura.siguiente("F-000099")).isEqualTo("F-000100");
    }

    @Test
    void unNumeroSinPrefijoIgualSeInterpretaComoCorrelativo() {
        assertThat(NumeroFactura.siguiente("000123")).isEqualTo("F-000124");
    }

    @Test
    void unNumeroIlegibleNoSeInterpretaComoCero() {
        assertThatThrownBy(() -> NumeroFactura.siguiente("F-"))
            .isInstanceOf(IllegalStateException.class);
    }
}
