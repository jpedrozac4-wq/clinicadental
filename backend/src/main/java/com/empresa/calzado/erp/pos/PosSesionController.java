package com.empresa.calzado.erp.pos;

import com.empresa.calzado.erp.pos.dto.CajaPosResumen;
import com.empresa.calzado.erp.pos.dto.SesionActualResponse;
import com.empresa.calzado.erp.pos.dto.SesionCajaCierreResponse;
import com.empresa.calzado.erp.pos.dto.SesionCajaResumen;
import com.empresa.calzado.erp.ventas.CheckoutBusinessException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Thin HTTP surface for the POS cash-session lifecycle. Every rule lives in {@link PosSesionService};
 * this controller only maps the authenticated user and the request body onto it.
 *
 * <p>It is a separate controller from {@link PosController} so neither becomes a god class: sales and
 * returns stay in {@code PosController}, session/arqueo endpoints live here.
 */
@RestController
@RequestMapping("/api/pos")
@CrossOrigin(origins = {"http://localhost", "http://localhost:80", "http://localhost:8080", "http://127.0.0.1"})
public class PosSesionController {

    private final PosSesionService posSesionService;

    public PosSesionController(PosSesionService posSesionService) {
        this.posSesionService = posSesionService;
    }

    @GetMapping("/cajas")
    public ResponseEntity<List<CajaPosResumen>> listarCajas() {
        return ResponseEntity.ok(posSesionService.listarCajas());
    }

    @PostMapping("/sesiones")
    public ResponseEntity<SesionCajaResumen> abrirSesion(
            @RequestBody AbrirSesionRequest request, Authentication auth) {
        return ResponseEntity.ok(posSesionService.abrirSesion(
            auth.getName(), request.idCajaPos(), request.montoApertura()));
    }

    @GetMapping("/sesiones")
    public ResponseEntity<List<SesionCajaResumen>> listarSesiones(
            @RequestParam(required = false) String estado) {
        return ResponseEntity.ok(posSesionService.listarSesiones(estado));
    }

    /**
     * The cashier's current turn. Always HTTP 200: the frontend badge polls it, and a 404 for the
     * normal "no open turn" case would log a console error on every poll.
     */
    @GetMapping("/sesiones/actual")
    public ResponseEntity<SesionActualResponse> sesionActual(Authentication auth) {
        return ResponseEntity.ok(posSesionService.sesionActual(auth.getName()));
    }

    @PostMapping("/sesiones/{id}/cerrar")
    public ResponseEntity<SesionCajaCierreResponse> cerrarSesion(
            @PathVariable Integer id, @RequestBody CerrarSesionRequest request, Authentication auth) {
        return ResponseEntity.ok(posSesionService.cerrarSesion(id, request.montoCierreEfectivo(), auth));
    }

    @PatchMapping("/sesiones/{id}/auditar")
    public ResponseEntity<SesionCajaResumen> auditarSesion(@PathVariable Integer id) {
        return ResponseEntity.ok(posSesionService.auditarSesion(id));
    }

    public record AbrirSesionRequest(Integer idCajaPos, BigDecimal montoApertura) {}

    public record CerrarSesionRequest(BigDecimal montoCierreEfectivo) {}

    @ExceptionHandler(CheckoutBusinessException.class)
    public ResponseEntity<Map<String, Object>> manejarErrorDeNegocio(CheckoutBusinessException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ERROR");
        body.put("message", ex.getMessage());
        return ResponseEntity.status(ex.getStatus()).body(body);
    }
}
