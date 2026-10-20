package com.empresa.calzado.erp.pos.dto;

/**
 * Body of {@code GET /api/pos/sesiones/actual}. Always serialised with HTTP 200 so the frontend
 * badge never logs a console error for the normal "no open turn" case.
 */
public record SesionActualResponse(boolean abierta, SesionCajaResumen sesion) {}
