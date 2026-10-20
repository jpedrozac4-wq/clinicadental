package com.empresa.calzado.erp.catalogo.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Admin-facing full detail of a {@code Modelo}: editable attributes plus its colour variants and
 * its generated SKUs.
 */
public record ModeloAdminDetalle(
    Integer id,
    Integer tipoCalzadoId,
    String tipoCalzado,
    Integer marcaId,
    String marca,
    Integer correlativo,
    String nombre,
    String materialExterior,
    String materialForro,
    String tipoSuela,
    String tipoCierre,
    String genero,
    String temporada,
    BigDecimal precioListaBase,
    BigDecimal precioEfectivo,
    Boolean esOutlet,
    BigDecimal porcentajeDescuento,
    Boolean esVisibleWeb,
    List<ModeloColorAdmin> colores,
    List<SkuAdmin> skus
) {}
