package com.clinica.dental.historial;

import com.clinica.dental.historial.dto.HistorialResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Historial de citas y tratamientos de un paciente (S3). */
@RestController
@RequestMapping("/api/pacientes/{idPaciente}/historial")
public class HistorialController {

    private final HistorialService service;

    public HistorialController(HistorialService service) {
        this.service = service;
    }

    @GetMapping
    public HistorialResponse historial(@PathVariable Integer idPaciente) {
        return service.dePaciente(idPaciente);
    }
}
