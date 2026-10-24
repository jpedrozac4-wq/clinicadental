package com.clinica.dental.auditoria.dto;

import java.util.List;

/**
 * Pagina de la bitacora de auditoria (S19).
 *
 * <p>El contrato es explicito y no depende de la serializacion de {@code org.springframework.data
 * .domain.Page}: el frontend consume {@code contenido} y los cuatro totales tal como aparecen aca.
 *
 * @param contenido      filas de la pagina, de la mas reciente a la mas antigua
 * @param pagina         numero de pagina pedido (base 0)
 * @param tamanio        tamanio de pagina efectivamente aplicado
 * @param totalElementos filas que cumplen el filtro en toda la bitacora
 * @param totalPaginas   cantidad de paginas con ese tamanio
 */
public record AuditoriaPaginaResponse(
    List<AuditoriaRegistroResponse> contenido,
    int pagina,
    int tamanio,
    long totalElementos,
    int totalPaginas
) {
}
