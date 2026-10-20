package com.empresa.calzado.erp.pos.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Read view of a cash session: register, branch, cashier and the persisted amounts. */
public record SesionCajaResumen(
    Integer id,
    String estado,
    OffsetDateTime fechaApertura,
    OffsetDateTime fechaCierre,
    BigDecimal montoApertura,
    BigDecimal montoCierreEfectivo,
    BigDecimal totalVentasSistema,
    BigDecimal diferenciaArqueo,
    Integer cajaId,
    String numeroCaja,
    String sucursalNombre,
    Integer bodegaId,
    String bodegaNombre,
    String cajero
) {}
