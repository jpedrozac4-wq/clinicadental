package com.empresa.calzado.erp.logistica;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/tracking")
@CrossOrigin(origins = {"http://localhost", "http://localhost:80", "http://localhost:8080", "http://127.0.0.1"})
public class TrackingController {

    private final DespachoService despachoService;

    public TrackingController(DespachoService despachoService) {
        this.despachoService = despachoService;
    }

    @GetMapping("/{guia}")
    public ResponseEntity<Map<String, Object>> consultarTracking(@PathVariable String guia) {
        DespachoEnvio d = despachoService.consultarTracking(guia);

        return ResponseEntity.ok(Map.of(
            "numeroGuia", d.getNumeroGuiaTracking(),
            "transporte", d.getTipoTransporte(),
            "courier", d.getCourierNombre() != null ? d.getCourierNombre() : "Flota Propia",
            "estado", d.getDespachoEstado().getCode(),
            "otpRequerido", true,
            "otpVerificado", d.getOtpVerificado(),
            "slaLimiteEntrega", d.getSlaLimiteEntrega5d().toString()
        ));
    }

    @PostMapping("/entregar")
    public ResponseEntity<Map<String, Object>> confirmarEntrega(@RequestBody ConfirmarEntregaRequest req) {
        DespachoEnvio d = despachoService.validarEntregaConOtp(req.numeroGuia(), req.codigoOtp());

        return ResponseEntity.ok(Map.of(
            "status", "ENTREGADO",
            "numeroGuia", d.getNumeroGuiaTracking(),
            "fechaEntrega", d.getFechaEntregaReal().toString(),
            "mensaje", "Código OTP verificado correctamente. Paquete entregado físicamente al cliente."
        ));
    }

    public record ConfirmarEntregaRequest(String numeroGuia, String codigoOtp) {}
}
