package com.empresa.calzado.erp.catalogo.dto;

import java.math.BigDecimal;

/**
 * Request body to update the editable attributes of a {@code Modelo}.
 *
 * <p>A {@code null} field means "keep the current value"; provided fields are validated. Changing
 * {@code precioListaBase} or the outlet discount recomputes the sale price of every SKU.
 */
public record ModeloUpdateRequest(
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
