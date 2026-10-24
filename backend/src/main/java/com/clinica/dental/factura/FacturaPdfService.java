package com.clinica.dental.factura;

import com.clinica.dental.reportes.pdf.DocumentoPdf;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Comprobante PDF de una factura (S9).
 *
 * <p>Reusa el motor propio de {@code reportes/pdf} (sin dependencias de terceros) sobre una hoja A4.
 * El comprobante detalla cada tratamiento con su costo congelado, el total, lo pagado y el saldo.
 * No agrega impuestos ni recargos: el total es exactamente la suma del detalle (L5).
 */
@Service
public class FacturaPdfService {

    private static final float TAMANO_TEXTO = 10f;
    private static final float TAMANO_TOTAL = 11f;
    private static final float TAMANO_NOTA = 8f;
    private static final float MARGEN = 56f;

    private static final DateTimeFormatter FECHA =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.ROOT);

    /** Genera el comprobante de {@code factura} con las lineas de su detalle. */
    public byte[] generar(Factura factura, List<FacturaDetalle> detalles) {
        DocumentoPdf documento = new DocumentoPdf(DocumentoPdf.ANCHO_A4, DocumentoPdf.ALTO_A4)
            .margenes(MARGEN, MARGEN, MARGEN, MARGEN);

        documento.textoCentrado("CLINICA DENTAL", 16f, true);
        documento.textoCentrado("Comprobante de pago", 11f, false);
        documento.lineaHorizontal();

        documento.filaTabla("Comprobante:", factura.getNumero(), TAMANO_TEXTO, false);
        documento.filaTabla("Fecha de emision:", fecha(factura.getFechaEmision()), TAMANO_TEXTO, false);
        documento.filaTabla("Paciente:", pacienteDe(factura), TAMANO_TEXTO, false);
        documento.filaTabla("Estado:", factura.getEstado().getCode(), TAMANO_TEXTO, false);
        documento.lineaHorizontal();

        documento.textoLinea("Detalle de tratamientos realizados", TAMANO_TEXTO, true);
        for (FacturaDetalle detalle : detalles) {
            documento.filaTabla(detalle.getDescripcion(), dinero(detalle.getMonto()), TAMANO_TEXTO, false);
        }
        documento.lineaHorizontal();

        documento.filaTabla("Total:", dinero(factura.getTotal()), TAMANO_TOTAL, true, false);
        documento.filaTabla("Pagado:", dinero(factura.getTotalPagado()), TAMANO_TEXTO, false);
        documento.filaTabla("Saldo:", dinero(factura.getSaldo()), TAMANO_TEXTO, true, true);

        documento.textoLinea("Total = suma del costo de cada tratamiento realizado.", TAMANO_NOTA, false);
        return documento.generar();
    }

    private static String pacienteDe(Factura factura) {
        return factura.getPaciente().getNombres() + " " + factura.getPaciente().getApellidos();
    }

    private static String fecha(OffsetDateTime fecha) {
        return fecha == null ? "-" : FECHA.format(fecha);
    }

    private static String dinero(BigDecimal monto) {
        return "Bs " + String.format(Locale.ROOT, "%.2f", monto == null ? BigDecimal.ZERO : monto);
    }
}
