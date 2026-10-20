package com.clinica.dental.cita;

import com.clinica.dental.cita.dto.CitaRequest;
import com.clinica.dental.cita.dto.CitaResponse;
import com.clinica.dental.cita.dto.ReprogramarRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;

/** API de la agenda: programar, consultar, reprogramar y cancelar citas (S4, S6). */
@RestController
@RequestMapping("/api/citas")
public class CitaController {

    private final CitaService service;

    public CitaController(CitaService service) {
        this.service = service;
    }

    @GetMapping
    public List<CitaResponse> agenda(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime hasta) {
        return service.agenda(desde, hasta);
    }

    @GetMapping("/{id}")
    public CitaResponse obtener(@PathVariable Integer id) {
        return service.obtener(id);
    }

    @PostMapping
    public ResponseEntity<CitaResponse> crear(@Valid @RequestBody CitaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(request));
    }

    @PatchMapping("/{id}/reprogramar")
    public CitaResponse reprogramar(@PathVariable Integer id,
                                    @Valid @RequestBody ReprogramarRequest request) {
        return service.reprogramar(id, request);
    }

    @PatchMapping("/{id}/cancelar")
    public ResponseEntity<Void> cancelar(@PathVariable Integer id) {
        service.cancelar(id);
        return ResponseEntity.noContent().build();
    }
}
