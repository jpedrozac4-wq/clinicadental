package com.empresa.calzado.erp.ventas.dto;

/**
 * One pickable branch returned by {@code GET /api/checkout/sucursales}.
 *
 * <p>Deliberately not the {@code Sucursal} entity: the storefront picker only needs a stable key
 * ({@code id}) and a label ({@code nombre}). Field names are Spanish because they are the JSON
 * contract consumed verbatim by the storefront ({@code frontend/js/config.js} ->
 * {@code CHECKOUT_SUCURSALES}).
 */
public record SucursalRetiro(Integer id, String nombre) {}
