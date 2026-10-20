package com.empresa.calzado.erp.inventario;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/inventario")
@CrossOrigin(origins = {"http://localhost", "http://localhost:80", "http://localhost:8080", "http://127.0.0.1"})
public class InventarioController {

    private final BodegaRepository bodegaRepository;
    private final KardexMovimientoRepository kardexRepository;

    public InventarioController(BodegaRepository bodegaRepository, KardexMovimientoRepository kardexRepository) {
        this.bodegaRepository = bodegaRepository;
        this.kardexRepository = kardexRepository;
    }

    @GetMapping("/bodegas")
    public ResponseEntity<List<Map<String, Object>>> listarBodegas() {
        List<Bodega> bodegas = bodegaRepository.findAll();
        List<Map<String, Object>> response = bodegas.stream().map(b -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", b.getId());
            m.put("nombre", b.getNombre());
            m.put("tipo", b.getBodegaTipo().getCode());
            m.put("ubicacion", b.getUbicacion());
            m.put("despachableWeb", b.getEsDespachableWeb());
            m.put("active", b.getActive());
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/kardex")
    public ResponseEntity<List<Map<String, Object>>> listarKardex(@RequestParam(required = false, defaultValue = "2") Integer bodegaId) {
        List<KardexMovimiento> movimientos = kardexRepository.findByBodegaIdOrderByCreatedAtDesc(bodegaId);
        List<Map<String, Object>> response = movimientos.stream().map(k -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", k.getId());
            m.put("bodega", k.getBodega().getNombre());
            m.put("sku", k.getCalzadoSku().getSku());
            m.put("tipoMovimiento", k.getMovimientoTipo().getCode());
            m.put("cantidad", k.getCantidadMovimiento());
            m.put("stockAnterior", k.getStockAnterior());
            m.put("stockPosterior", k.getStockPosterior());
            m.put("documentoTipo", k.getDocumentoTipo());
            m.put("documentoId", k.getDocumentoId());
            m.put("usuario", k.getUser().getUsername());
            m.put("fecha", k.getCreatedAt().toString());
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }
}
