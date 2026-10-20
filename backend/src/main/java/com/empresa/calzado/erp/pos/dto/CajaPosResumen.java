package com.empresa.calzado.erp.pos.dto;

/**
 * One active cash register as the POS terminal picker renders it.
 *
 * <p>{@code sesionAbiertaId} is the id of an open session on that register, or {@code null} when the
 * register is free. A DTO, never the JPA entity, so the controller cannot leak lazy relations.
 */
public record CajaPosResumen(
    Integer id,
    String numeroCaja,
    String nombreTerminal,
    Integer sucursalId,
    String sucursalNombre,
    Integer bodegaId,
    String bodegaNombre,
    Integer sesionAbiertaId
) {}
