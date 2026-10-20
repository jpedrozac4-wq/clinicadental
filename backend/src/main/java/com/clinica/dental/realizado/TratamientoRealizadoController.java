package com.clinica.dental.realizado;

import com.clinica.dental.realizado.dto.RealizadoRequest;
import com.clinica.dental.realizado.dto.RealizadoResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** API de tratamientos realizados (S8). */
@RestController
@RequestMapping("/api/tratamientos-realizados")
public class TratamientoRealizadoController {

    private final TratamientoRealizadoService service;

    public TratamientoRealizadoController(TratamientoRealizadoService service) {
        this.service = service;
    }

    @GetMapping
    public List<RealizadoResponse> listar(
            @RequestParam(required = false) Integer idPaciente) {
        return service.listar(idPaciente);
    }

    @PostMapping
    public ResponseEntity<RealizadoResponse> registrar(@Valid @RequestBody RealizadoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.registrar(request));
    }
}
