package com.empresa.calzado.erp.catalogo.dto;

import java.math.BigDecimal;

/**
 * Request body to create a {@code Modelo}. It creates only the model header: colour variants and
 * SKUs are created by the dedicated sub-units and are intentionally absent here.
 *
 * <p>{@code correlativo} is optional: when omitted the service assigns the next free value for the
 * given (tipoCalzado, marca) pair.
 */
public record ModeloCreateRequest(
    Integer idTipoCalzado,
    Integer idMarca,
    Integer correlativo,
    String nombre,
    String materialExterior,
    String materialForro,
    String tipoSuela,
    String tipoCierre,
    String genero,
    String temporada,
    BigDecimal precioListaBase,
    Boolean esOutlet,
    BigDecimal porcentajeDescuento,
    Boolean esVisibleWeb
) {}
