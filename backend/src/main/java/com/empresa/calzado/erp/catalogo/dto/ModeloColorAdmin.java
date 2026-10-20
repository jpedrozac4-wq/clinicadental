package com.empresa.calzado.erp.catalogo.dto;

/**
 * Admin-facing projection of a colour variant of a {@code Modelo}.
 */
public record ModeloColorAdmin(
    Integer id,
    Integer colorId,
    String color,
    String codigoHex,
    String fotoPrincipalUrl,
    String galeriaFotosJson
) {}
