package com.clinica.dental.factura;

import com.clinica.dental.factura.dto.ComprobantePdf;
import com.clinica.dental.factura.dto.FacturaRequest;
import com.clinica.dental.factura.dto.FacturaResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** API de facturación: emisión, consulta y comprobante PDF (S9). */
@RestController
@RequestMapping("/api/facturas")
public class FacturaController {

    private final FacturaService service;

    public FacturaController(FacturaService service) {
        this.service = service;
    }

    @GetMapping
    public List<FacturaResponse> listar() {
        return service.listar();
    }

    @GetMapping("/{id}")
    public FacturaResponse obtener(@PathVariable Integer id) {
        return service.obtener(id);
    }

    @PostMapping
    public ResponseEntity<FacturaResponse> emitir(@Valid @RequestBody FacturaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.emitir(request));
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> comprobante(@PathVariable Integer id) {
        ComprobantePdf comprobante = service.comprobante(id);
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + comprobante.nombreArchivo() + "\"")
            .body(comprobante.contenido());
    }
}
