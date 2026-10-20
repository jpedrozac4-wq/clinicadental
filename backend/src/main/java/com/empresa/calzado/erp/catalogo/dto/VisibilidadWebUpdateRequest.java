package com.empresa.calzado.erp.catalogo.dto;

/**
 * Request body for {@code PATCH /modelos/{id}/visibilidad-web}.
 *
 * <p>Web visibility is the logical removal: there is no physical delete of a model.
 */
public record VisibilidadWebUpdateRequest(
    Boolean esVisibleWeb
) {}
