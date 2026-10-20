package com.empresa.calzado.erp.catalogo;

import com.empresa.calzado.erp.catalogo.dto.ColorVarianteCreateRequest;
import com.empresa.calzado.erp.catalogo.dto.ModeloAdminDetalle;
import com.empresa.calzado.erp.catalogo.dto.ModeloAdminResumen;
import com.empresa.calzado.erp.catalogo.dto.ModeloColorAdmin;
import com.empresa.calzado.erp.catalogo.dto.ModeloCreateRequest;
import com.empresa.calzado.erp.catalogo.dto.ModeloUpdateRequest;
import com.empresa.calzado.erp.catalogo.dto.OutletUpdateRequest;
import com.empresa.calzado.erp.catalogo.dto.SkuActiveUpdateRequest;
import com.empresa.calzado.erp.catalogo.dto.SkuAdmin;
import com.empresa.calzado.erp.catalogo.dto.SkuCreateRequest;
import com.empresa.calzado.erp.catalogo.dto.SkuUpdateRequest;
import com.empresa.calzado.erp.catalogo.dto.TallaActiveUpdateRequest;
import com.empresa.calzado.erp.catalogo.dto.TallaAdmin;
import com.empresa.calzado.erp.catalogo.dto.TallaCreateRequest;
import com.empresa.calzado.erp.catalogo.dto.TallaUpdateRequest;
import com.empresa.calzado.erp.catalogo.dto.VisibilidadWebUpdateRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Back-office catalogue administration. The route prefix is protected by {@code SecurityConfig}
 * with {@code OP_CATALOGO_CREAR_EDITAR} / {@code ROLE_ADMIN}.
 *
 * <p>Every response is a DTO; no JPA entity is returned or accepted. Business failures are mapped
 * to a readable {@code {"status":"ERROR","message":...}} envelope rather than a bare 500.
 */
@RestController
@RequestMapping("/api/catalogo/admin")
public class CatalogoAdminController {

    private final CatalogoAdminService service;

    public CatalogoAdminController(CatalogoAdminService service) {
        this.service = service;
    }

    @GetMapping("/modelos")
    public ResponseEntity<List<ModeloAdminResumen>> listarModelos(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) Integer marcaId,
        @RequestParam(required = false) Integer tipoId,
        @RequestParam(required = false) Boolean outlet,
        @RequestParam(required = false) Boolean visibleWeb
    ) {
        return ResponseEntity.ok(service.listarModelos(q, marcaId, tipoId, outlet, visibleWeb));
    }

    @GetMapping("/modelos/{id}")
    public ResponseEntity<ModeloAdminDetalle> obtenerModelo(@PathVariable Integer id) {
        return ResponseEntity.ok(service.obtenerModelo(id));
    }

    @GetMapping("/skus")
    public ResponseEntity<List<SkuAdmin>> listarSkus(@RequestParam Integer modeloId) {
        return ResponseEntity.ok(service.listarSkus(modeloId));
    }

    @PostMapping("/modelos")
    public ResponseEntity<ModeloAdminDetalle> crearModelo(@RequestBody ModeloCreateRequest request) {
        return ResponseEntity.ok(service.crearModelo(request));
    }

    @PutMapping("/modelos/{id}")
    public ResponseEntity<ModeloAdminDetalle> actualizarModelo(
        @PathVariable Integer id, @RequestBody ModeloUpdateRequest request) {
        return ResponseEntity.ok(service.actualizarModelo(id, request));
    }

    @PatchMapping("/modelos/{id}/outlet")
    public ResponseEntity<ModeloAdminDetalle> actualizarOutlet(
        @PathVariable Integer id, @RequestBody OutletUpdateRequest request) {
        return ResponseEntity.ok(service.actualizarOutlet(id, request));
    }

    @PatchMapping("/modelos/{id}/visibilidad-web")
    public ResponseEntity<ModeloAdminDetalle> actualizarVisibilidad(
        @PathVariable Integer id, @RequestBody VisibilidadWebUpdateRequest request) {
        return ResponseEntity.ok(service.actualizarVisibilidad(id, request));
    }

    @PostMapping("/skus")
    public ResponseEntity<SkuAdmin> crearSku(@RequestBody(required = false) SkuCreateRequest request) {
        return ResponseEntity.ok(service.crearSku(request));
    }

    @PutMapping("/skus/{id}")
    public ResponseEntity<SkuAdmin> actualizarSku(
        @PathVariable Integer id, @RequestBody(required = false) SkuUpdateRequest request) {
        return ResponseEntity.ok(service.actualizarSku(id, request));
    }

    @PatchMapping("/skus/{id}/active")
    public ResponseEntity<SkuAdmin> actualizarSkuActivo(
        @PathVariable Integer id, @RequestBody(required = false) SkuActiveUpdateRequest request) {
        return ResponseEntity.ok(service.actualizarSkuActivo(id, request));
    }

    @GetMapping("/tallas")
    public ResponseEntity<List<TallaAdmin>> listarTallas() {
        return ResponseEntity.ok(service.listarTallas());
    }

    @PostMapping("/tallas")
    public ResponseEntity<TallaAdmin> crearTalla(
        @RequestBody(required = false) TallaCreateRequest request) {
        return ResponseEntity.ok(service.crearTalla(request));
    }

    /**
     * Updates the editable attributes of a size.
     *
     * <p>{@code tallaEur} is not part of the body: the size part of an SKU code already printed or
     * scanned was derived from it, so it is immutable after creation.
     */
    @PutMapping("/tallas/{id}")
    public ResponseEntity<TallaAdmin> actualizarTalla(
        @PathVariable Integer id, @RequestBody(required = false) TallaUpdateRequest request) {
        return ResponseEntity.ok(service.actualizarTalla(id, request));
    }

    /**
     * Logical removal / restore of a size: only the active flag changes. This is the only
     * logical-delete path for a size.
     */
    @PatchMapping("/tallas/{id}/active")
    public ResponseEntity<TallaAdmin> actualizarTallaActivo(
        @PathVariable Integer id, @RequestBody(required = false) TallaActiveUpdateRequest request) {
        return ResponseEntity.ok(service.actualizarTallaActivo(id, request));
    }

    /**
     * Adds a colour variant to a model.
     *
     * <p>{@code id} is the {@code modelo} id and the body references the {@code cat_color} id. The
     * variant is the (modelo, color) pair, which {@code uq_modelo_color} already makes unique.
     */
    @PostMapping("/modelos/{id}/colores")
    public ResponseEntity<ModeloColorAdmin> agregarColor(
        @PathVariable Integer id, @RequestBody(required = false) ColorVarianteCreateRequest request) {
        return ResponseEntity.ok(service.agregarColor(id, request));
    }

    /**
     * Uploads the main photo of a colour variant.
     *
     * <p>{@code id} is the {@code modelo} id and {@code colorId} is the {@code cat_color} id; the
     * variant is the (modelo, color) pair, unique through {@code uq_modelo_color}. The multipart
     * part is named {@code imagen}.
     */
    @PostMapping(value = "/modelos/{id}/colores/{colorId}/imagen", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ModeloColorAdmin> subirImagenColor(
        @PathVariable Integer id,
        @PathVariable Integer colorId,
        @RequestParam(value = "imagen", required = false) MultipartFile imagen) {
        return ResponseEntity.ok(service.subirImagenColor(id, colorId, imagen));
    }

    /**
     * Clears the main photo of a colour variant.
     *
     * <p>{@code id} is the {@code modelo} id and {@code colorId} is the {@code cat_color} id; the
     * variant is the (modelo, color) pair, unique through {@code uq_modelo_color}.
     */
    @DeleteMapping("/modelos/{id}/colores/{colorId}/imagen")
    public ResponseEntity<ModeloColorAdmin> eliminarImagenColor(
        @PathVariable Integer id, @PathVariable Integer colorId) {
        return ResponseEntity.ok(service.eliminarImagenColor(id, colorId));
    }

    @ExceptionHandler(CatalogoBusinessException.class)
    public ResponseEntity<Map<String, Object>> manejarErrorDeNegocio(CatalogoBusinessException ex) {
        return error(ex.getStatus(), ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> manejarViolacionDeIntegridad(DataIntegrityViolationException ex) {
        return error(HttpStatus.BAD_REQUEST,
            "Los datos enviados violan una restriccion de la base de datos del catalogo");
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ERROR");
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
