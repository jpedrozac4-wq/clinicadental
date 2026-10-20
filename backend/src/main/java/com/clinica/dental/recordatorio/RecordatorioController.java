package com.clinica.dental.recordatorio;

import com.clinica.dental.recordatorio.dto.RecordatorioResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** API de recordatorios de citas (S5). */
@RestController
@RequestMapping("/api")
public class RecordatorioController {

    private final RecordatorioService service;

    public RecordatorioController(RecordatorioService service) {
        this.service = service;
    }

    /** Historial de recordatorios de una cita, consultable desde la interfaz (S5). */
    @GetMapping("/citas/{id}/recordatorios")
    public List<RecordatorioResponse> historial(@PathVariable Integer id) {
        return service.historial(id);
    }

    /** Fuerza un despacho de los recordatorios vencidos y devuelve la bitacora procesada (S5). */
    @PostMapping("/recordatorios/procesar")
    public List<RecordatorioResponse> procesar() {
        return service.procesar();
    }
}
