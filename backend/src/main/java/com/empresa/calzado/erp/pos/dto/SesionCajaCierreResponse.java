package com.empresa.calzado.erp.pos.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Close-of-turn view: the persisted arqueo plus the two intermediate values
 * ({@code ventasEnEfectivo} and {@code efectivoEsperado}) that let the cashier see the arithmetic
 * behind {@code diferenciaArqueo}.
 */
public record SesionCajaCierreResponse(
    Integer id,
    String estado,
    OffsetDateTime fechaApertura,
    OffsetDateTime fechaCierre,
    BigDecimal montoApertura,
    BigDecimal montoCierreEfectivo,
    BigDecimal totalVentasSistema,
    BigDecimal ventasEnEfectivo,
    BigDecimal efectivoEsperado,
    BigDecimal diferenciaArqueo,
    Integer cajaId,
    String numeroCaja,
    String sucursalNombre,
    Integer bodegaId,
    String bodegaNombre,
    String cajero
) {}
