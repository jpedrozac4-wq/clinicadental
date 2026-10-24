package com.clinica.dental.usuario;

import com.clinica.dental.usuario.dto.PermisoResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** API de solo lectura del catalogo de permisos (S18). */
@RestController
@RequestMapping("/api/permisos")
public class PermisoController {

    private final PermisoService service;

    public PermisoController(PermisoService service) {
        this.service = service;
    }

    @GetMapping
    public List<PermisoResponse> catalogo() {
        return service.catalogo();
    }
}
