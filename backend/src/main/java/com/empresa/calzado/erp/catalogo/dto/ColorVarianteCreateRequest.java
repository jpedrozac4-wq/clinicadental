package com.empresa.calzado.erp.catalogo.dto;

/**
 * Request body to add a colour variant to a model: {@code idColor} references {@code cat_color},
 * and the (modelo, color) pair is unique through {@code uq_modelo_color}.
 */
public record ColorVarianteCreateRequest(Integer idColor) {}
