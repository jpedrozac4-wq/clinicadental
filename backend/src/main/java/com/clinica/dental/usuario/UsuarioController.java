package com.clinica.dental.usuario;

import com.clinica.dental.usuario.dto.UsuarioRequest;
import com.clinica.dental.usuario.dto.UsuarioResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * API del padron de usuarios del sistema (S18, S20).
 *
 * <p>La proteccion por permiso ({@code OP_USUARIOS_ADMIN}) vive en {@code SecurityConfig}; aca solo
 * se resuelve el contrato HTTP. La respuesta nunca publica el hash de la contrasenia.
 */
@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService service;

    public UsuarioController(UsuarioService service) {
        this.service = service;
    }

    /** Listado filtrable. {@code rol} acepta {@code DENTISTA} o {@code ROLE_DENTISTA}. */
    @GetMapping
    public List<UsuarioResponse> listar(
            @RequestParam(name = "rol", required = false) String rol,
            @RequestParam(name = "estado", required = false) String estado,
            @RequestParam(name = "q", required = false) String q) {
        return service.listar(rol, estado, q);
    }

    @PostMapping
    public ResponseEntity<UsuarioResponse> crear(@Valid @RequestBody UsuarioRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(request));
    }

    @PutMapping("/{id}")
    public UsuarioResponse actualizar(@PathVariable Integer id,
                                      @Valid @RequestBody UsuarioRequest request) {
        return service.actualizar(id, request);
    }

    /**
     * Baja logica. El actor autenticado se toma del token para que nadie pueda darse de baja a si
     * mismo y dejar la sesion sin acceso administrativo (S18).
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> darDeBaja(@PathVariable Integer id, Authentication authentication) {
        service.darDeBaja(id, authentication == null ? null : authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
