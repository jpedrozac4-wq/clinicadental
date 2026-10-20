package com.empresa.calzado.erp.reportes;

import com.empresa.calzado.erp.facturacion.Factura;
import com.empresa.calzado.erp.facturacion.FacturaTipo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * The web invoice emitter must return exactly the url the storage writer produced and never rebuild
 * it by hand (the lowercased, invented url is the defect this replaces).
 */
class FacturaVentaPdfServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void devuelveLaUrlDelEscritorYLaEscribeEnLaFactura() {
        FacturaPdfStorage almacen = spy(new FacturaPdfStorage(tempDir));
        doReturn("/storage/facturas/SENTINEL-FAC.pdf").when(almacen).guardar(any(String.class), any(byte[].class));

        Factura factura = factura("FAC-000102");
        String url = new FacturaVentaPdfService(new FacturaVentaPdf(), almacen).emitir(factura);

        assertThat(url).isEqualTo("/storage/facturas/SENTINEL-FAC.pdf");
        assertThat(factura.getPdfUrl()).isEqualTo(url);
        verify(almacen).guardar(eq("FAC-000102"), any(byte[].class));
    }

    @Test
    void escribeUnPdfRealBajoLaUrlDevuelta() throws Exception {
        FacturaPdfStorage almacen = new FacturaPdfStorage(tempDir);
        Factura factura = factura("FAC-000103");

        String url = new FacturaVentaPdfService(new FacturaVentaPdf(), almacen).emitir(factura);

        assertThat(url).isEqualTo(FacturaPdfStorage.URL_PREFIX + "FAC-000103.pdf");
        Path archivo = tempDir.resolve(url.substring(FacturaPdfStorage.URL_PREFIX.length()));
        assertThat(archivo).exists();
        assertThat(Files.readAllBytes(archivo)).startsWith("%PDF-1.4".getBytes("ISO-8859-1"));
    }

    private static Factura factura(String correlativo) {
        FacturaTipo tipo = new FacturaTipo();
        tipo.setCode("FACTURA");
        Factura factura = new Factura();
        factura.setFacturaTipo(tipo);
        factura.setNumeroCorrelativo(correlativo);
        factura.setRazonSocial("Cliente Web");
        factura.setMontoSubtotal(new java.math.BigDecimal("89.29"));
        factura.setMontoIva(new java.math.BigDecimal("10.71"));
        factura.setInvoiceTotal(new java.math.BigDecimal("100.00"));
        return factura;
    }
}
