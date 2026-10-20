package com.clinica.dental.paciente;

import com.clinica.dental.paciente.dto.PacienteRequest;
import com.clinica.dental.paciente.dto.PacienteResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * API de gestion de pacientes: alta, consulta, edicion y baja (S1, S2).
 * El historial clinico vive en su propio endpoint, bajo el modulo de tratamientos (S3).
 */
@RestController
@RequestMapping("/api/pacientes")
public class PacienteController {

    private final PacienteService service;

    public PacienteController(PacienteService service) {
        this.service = service;
    }

    @GetMapping
    public List<PacienteResponse> listar(@RequestParam(name = "texto", required = false) String texto) {
        return service.listar(texto);
    }

    @GetMapping("/{id}")
    public PacienteResponse obtener(@PathVariable Integer id) {
        return service.obtener(id);
    }

    @PostMapping
    public ResponseEntity<PacienteResponse> crear(@Valid @RequestBody PacienteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(request));
    }

    @PutMapping("/{id}")
    public PacienteResponse actualizar(@PathVariable Integer id, @Valid @RequestBody PacienteRequest request) {
        return service.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> darDeBaja(@PathVariable Integer id) {
        service.darDeBaja(id);
        return ResponseEntity.noContent().build();
    }
}
