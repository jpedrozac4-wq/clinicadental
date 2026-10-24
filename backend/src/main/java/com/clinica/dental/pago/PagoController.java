package com.clinica.dental.pago;

import com.clinica.dental.pago.dto.PagoRequest;
import com.clinica.dental.pago.dto.PagoResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** API de pagos de una factura: registro y listado (S10). */
@RestController
@RequestMapping("/api/facturas/{idFactura}/pagos")
public class PagoController {

    private final PagoService service;

    public PagoController(PagoService service) {
        this.service = service;
    }

    @GetMapping
    public List<PagoResponse> listar(@PathVariable Integer idFactura) {
        return service.listar(idFactura);
    }

    @PostMapping
    public ResponseEntity<PagoResponse> registrar(@PathVariable Integer idFactura,
                                                  @Valid @RequestBody PagoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(service.registrar(idFactura, request));
    }
}
