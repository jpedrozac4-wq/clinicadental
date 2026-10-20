package com.clinica.dental.tratamiento;

import com.clinica.dental.tratamiento.dto.TratamientoRequest;
import com.clinica.dental.tratamiento.dto.TratamientoResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** API del catalogo de tratamientos dentales (S7). */
@RestController
@RequestMapping("/api/tratamientos")
public class TratamientoController {

    private final TratamientoService service;

    public TratamientoController(TratamientoService service) {
        this.service = service;
    }

    @GetMapping
    public List<TratamientoResponse> listar() {
        return service.listar();
    }

    @PostMapping
    public ResponseEntity<TratamientoResponse> crear(@Valid @RequestBody TratamientoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(request));
    }

    @PutMapping("/{id}")
    public TratamientoResponse actualizar(@PathVariable Integer id,
                                          @Valid @RequestBody TratamientoRequest request) {
        return service.actualizar(id, request);
    }
}
