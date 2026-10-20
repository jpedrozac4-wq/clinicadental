package com.clinica.dental.factura;

import com.clinica.dental.paciente.Paciente;
import com.clinica.dental.realizado.TratamientoRealizado;
import com.clinica.dental.tratamiento.Tratamiento;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprobante PDF de la factura (S9).
 *
 * <p>El comprobante se arma con el motor propio de {@code reportes/pdf}: no hay dependencias de
 * terceros y el texto viaja en Windows-1252, así que se puede verificar sobre los bytes emitidos.
 */
class FacturaPdfServiceTest {

    private final FacturaPdfService service = new FacturaPdfService();

    private static Paciente paciente() {
        Paciente p = new Paciente();
        p.setId(1);
        p.setNombres("Ana María");
        p.setApellidos("Mendoza Rojas");
        return p;
    }

    private static Factura factura(String total, String saldo) {
        EstadoFactura estado = new EstadoFactura();
        estado.setId(1);
        estado.setCode(EstadoFactura.PAGO_PARCIAL);

        Factura f = new Factura();
        f.setId(50);
        f.setNumero("F-000002");
        f.setPaciente(paciente());
        f.setEstado(estado);
        f.setFechaEmision(OffsetDateTime.now());
        f.setTotal(new BigDecimal(total));
        f.setSaldo(new BigDecimal(saldo));
        return f;
    }

    private static FacturaDetalle linea(Factura factura, int id, String descripcion, String monto) {
        Tratamiento tratamiento = new Tratamiento();
        tratamiento.setId(2);
        tratamiento.setNombre(descripcion);

        TratamientoRealizado realizado = new TratamientoRealizado();
        realizado.setId(id);
        realizado.setPaciente(factura.getPaciente());
        realizado.setTratamiento(tratamiento);
        realizado.setCosto(new BigDecimal(monto));

        FacturaDetalle detalle = new FacturaDetalle();
        detalle.setId(id);
        detalle.setFactura(factura);
        detalle.setRealizado(realizado);
        detalle.setDescripcion(descripcion);
        detalle.setMonto(new BigDecimal(monto));
        return detalle;
    }

    @Test
    void elComprobanteEsUnPdfValidoConElNumeroElPacienteYElDetalle() {
        Factura factura = factura("550.00", "150.00");
        List<FacturaDetalle> detalle = List.of(
            linea(factura, 7, "Obturación de resina", "400.00"),
            linea(factura, 8, "Consulta y diagnóstico", "150.00"));

        String pdf = new String(service.generar(factura, detalle), StandardCharsets.ISO_8859_1);

        assertThat(pdf).startsWith("%PDF-1.4");
        assertThat(pdf.trim()).endsWith("%%EOF");
        assertThat(pdf).contains("Comprobante de pago");
        assertThat(pdf).contains("F-000002");
        assertThat(pdf).contains("Mendoza Rojas");
        assertThat(pdf).contains("Obturación de resina");
        assertThat(pdf).contains("Consulta y diagnóstico");
    }

    @Test
    void elComprobanteMuestraTotalPagadoYSaldoSinAgregarImpuestos() {
        Factura factura = factura("550.00", "150.00");
        List<FacturaDetalle> detalle = List.of(
            linea(factura, 7, "Obturación de resina", "400.00"),
            linea(factura, 8, "Consulta y diagnóstico", "150.00"));

        String pdf = new String(service.generar(factura, detalle), StandardCharsets.ISO_8859_1);

        assertThat(pdf).contains("Total:");
        assertThat(pdf).contains("550.00");
        assertThat(pdf).contains("Pagado:");
        assertThat(pdf).contains("400.00");
        assertThat(pdf).contains("Saldo:");
        assertThat(pdf).contains("150.00");
        // L5: la factura no agrega IVA, impuestos ni recargos.
        assertThat(pdf).doesNotContain("IVA");
        assertThat(pdf).doesNotContain("Impuesto");
        assertThat(pdf).doesNotContain("Recargo");
    }

    @Test
    void unaFacturaSinDetalleIgualEmiteUnComprobanteLegible() {
        Factura factura = factura("450.00", "0.00");

        String pdf = new String(service.generar(factura, List.of()), StandardCharsets.ISO_8859_1);

        assertThat(pdf).startsWith("%PDF-1.4");
        assertThat(pdf).contains("F-000002");
    }
}
