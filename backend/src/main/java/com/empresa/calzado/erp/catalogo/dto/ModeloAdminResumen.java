package com.empresa.calzado.erp.catalogo.dto;

import java.math.BigDecimal;

/**
 * Admin-facing summary of a {@code Modelo} for list views.
 *
 * <p>Unlike the public catalogue, this projection includes models with
 * {@code esVisibleWeb = false} (the logical removal).
 */
public record ModeloAdminResumen(
    Integer id,
    Integer tipoCalzadoId,
    String tipoCalzado,
    Integer marcaId,
    String marca,
    Integer correlativo,
    String nombre,
    String genero,
    String temporada,
    BigDecimal precioListaBase,
    BigDecimal precioEfectivo,
    Boolean esOutlet,
    BigDecimal porcentajeDescuento,
    Boolean esVisibleWeb
) {}
