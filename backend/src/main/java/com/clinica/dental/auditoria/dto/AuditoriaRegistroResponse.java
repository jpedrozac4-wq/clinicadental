package com.clinica.dental.auditoria.dto;

import java.time.OffsetDateTime;

/**
 * Una fila de la bitacora tal como la publica {@code GET /api/auditoria} (S19).
 *
 * <p>Es tambien el destino de la proyeccion JPQL del repositorio, de modo que la consulta y el
 * contrato HTTP no puedan divergir: lo que la query selecciona es exactamente lo que se serializa.
 *
 * @param idLog             identificador de la fila de {@code audit_log}
 * @param fecha             instante en que se registro la operacion
 * @param accion            codigo de {@code cat_log_accion}
 * @param descripcionAccion descripcion legible del codigo
 * @param tabla             tabla de negocio afectada
 * @param registro          clave primaria de la fila afectada
 * @param usuario           username del actor, o {@code null} si no hubo actor autenticado
 * @param detalle           columna {@code data} (JSONB) renderizada como texto
 */
public record AuditoriaRegistroResponse(
    Long idLog,
    OffsetDateTime fecha,
    String accion,
    String descripcionAccion,
    String tabla,
    String registro,
    String usuario,
    String detalle
) {
}
