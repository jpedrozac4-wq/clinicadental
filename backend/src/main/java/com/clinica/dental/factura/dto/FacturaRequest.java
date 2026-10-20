package com.clinica.dental.factura.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Emision de un comprobante: paciente y tratamientos realizados que se facturan (S9).
 *
 * <p>Los montos no se envian: cada linea toma el costo congelado del tratamiento realizado y el
 * total se calcula como su suma.
 */
public record FacturaRequest(
    @NotNull(message = "el paciente es obligatorio")
    Integer idPaciente,

    @NotEmpty(message = "la factura debe incluir al menos un tratamiento realizado")
    List<Integer> idsRealizado,

    String observaciones
) {
}
