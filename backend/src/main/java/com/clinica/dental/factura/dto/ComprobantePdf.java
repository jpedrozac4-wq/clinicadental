package com.clinica.dental.factura.dto;

/**
 * Comprobante listo para descargar: el numero correlativo define el nombre del archivo (S9).
 */
public record ComprobantePdf(String numero, byte[] contenido) {

    public String nombreArchivo() {
        return "factura-" + numero + ".pdf";
    }
}
