package com.clinica.dental.auditoria;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contrato del detalle JSON que acompana a una fila de auditoria (S19).
 *
 * <p>El detalle lo construyen los servicios de negocio, asi que sus reglas se prueban aisladas: sin
 * nulos, con tipos que Jackson serializa sin ambiguedad y en el orden en que se agregaron.
 */
class DetalleAuditoriaTest {

    private enum Accion {
        PROGRAMADA
    }

    @Test
    void omiteLasClavesCuyoValorEsNulo() {
        DetalleAuditoria detalle = DetalleAuditoria.nuevo()
            .con("idPaciente", 7)
            .con("idCita", null)
            .con("estado", "PROGRAMADA");

        assertThat(detalle.valores())
            .containsOnlyKeys("idPaciente", "estado")
            .containsEntry("idPaciente", 7);
    }

    @Test
    void lasFechasViajanComoTextoIso8601() {
        OffsetDateTime fecha = OffsetDateTime.of(2026, 3, 4, 9, 30, 0, 0, ZoneOffset.ofHours(-4));

        DetalleAuditoria detalle = DetalleAuditoria.nuevo().con("fechaHoraInicio", fecha);

        assertThat(detalle.valores()).containsEntry("fechaHoraInicio", "2026-03-04T09:30:00-04:00");
    }

    @Test
    void lasFechasSinHoraViajanComoFechaPlana() {
        DetalleAuditoria detalle = DetalleAuditoria.nuevo()
            .con("fechaNacimiento", LocalDate.of(1990, 3, 14));

        assertThat(detalle.valores()).containsEntry("fechaNacimiento", "1990-03-14");
    }

    @Test
    void losEnumsViajanPorSuNombre() {
        DetalleAuditoria detalle = DetalleAuditoria.nuevo().con("estado", Accion.PROGRAMADA);

        assertThat(detalle.valores()).containsEntry("estado", "PROGRAMADA");
    }

    @Test
    void losMontosSeConservanComoNumero() {
        DetalleAuditoria detalle = DetalleAuditoria.nuevo().con("total", new BigDecimal("350.00"));

        assertThat(detalle.valores().get("total")).isEqualTo(new BigDecimal("350.00"));
    }

    @Test
    void conservaElOrdenEnQueSeAgregaronLasClaves() {
        DetalleAuditoria detalle = DetalleAuditoria.nuevo()
            .con("idFactura", 50)
            .con("numero", "F-000001")
            .con("total", new BigDecimal("550.00"));

        assertThat(detalle.valores().keySet()).containsExactly("idFactura", "numero", "total");
    }

    @Test
    void laVistaDeValoresEsInmutable() {
        DetalleAuditoria detalle = DetalleAuditoria.nuevo().con("idPaciente", 7);

        assertThatThrownBy(() -> detalle.valores().put("otro", 1))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
