package com.clinica.dental.auditoria;

import com.clinica.dental.auditoria.dto.AuditoriaPaginaResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * API de consulta de la bitacora de auditoria (S19).
 *
 * <p>Es de solo lectura: la bitacora la escriben los servicios de negocio dentro de su propia
 * transaccion, no este endpoint. La guarda {@code OP_AUDITORIA_VER} vive en {@code SecurityConfig}.
 *
 * <p>Contrato de {@code GET /api/auditoria}:
 * <ul>
 *   <li>{@code desde} y {@code hasta} son {@code YYYY-MM-DD} opcionales e inclusivos en ambos
 *       extremos; sin ellos no se filtra por fecha;</li>
 *   <li>{@code accion} es un codigo de {@code cat_log_accion}; uno desconocido devuelve {@code 400};</li>
 *   <li>{@code usuario} es el username del actor, sin distinguir mayusculas solo si el padron lo
 *       hubiera normalizado: se compara exactamente como viaja en el JWT;</li>
 *   <li>{@code pagina} arranca en 0 y {@code tamanio} es opcional (25 por defecto, 100 como maximo);</li>
 *   <li>las filas se devuelven de la mas reciente a la mas antigua.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/auditoria")
public class AuditoriaController {

    private final AuditoriaService service;

    public AuditoriaController(AuditoriaService service) {
        this.service = service;
    }

    @GetMapping
    public AuditoriaPaginaResponse consultar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String accion,
            @RequestParam(required = false) String usuario,
            @RequestParam(required = false) Integer pagina,
            @RequestParam(required = false) Integer tamanio) {
        return service.consultar(desde, hasta, accion, usuario, pagina, tamanio);
    }
}
