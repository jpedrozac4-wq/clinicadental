package com.empresa.calzado.erp.catalogo;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/catalogo")
@CrossOrigin(origins = {"http://localhost", "http://localhost:80", "http://localhost:8080", "http://127.0.0.1"})
public class CatalogoController {

    private final ModeloRepository modeloRepository;
    private final CalzadoSkuRepository skuRepository;
    private final TipoCalzadoRepository tipoCalzadoRepository;
    private final MarcaRepository marcaRepository;
    private final ColorRepository colorRepository;
    private final TallaEquivalenciaRepository tallaRepository;
    private final CatalogoService catalogoService;

    public CatalogoController(
        ModeloRepository modeloRepository,
        CalzadoSkuRepository skuRepository,
        TipoCalzadoRepository tipoCalzadoRepository,
        MarcaRepository marcaRepository,
        ColorRepository colorRepository,
        TallaEquivalenciaRepository tallaRepository,
        CatalogoService catalogoService
    ) {
        this.modeloRepository = modeloRepository;
        this.skuRepository = skuRepository;
        this.tipoCalzadoRepository = tipoCalzadoRepository;
        this.marcaRepository = marcaRepository;
        this.colorRepository = colorRepository;
        this.tallaRepository = tallaRepository;
        this.catalogoService = catalogoService;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listarModelos(
        @RequestParam(required = false) Integer tipoId,
        @RequestParam(required = false) Integer marcaId,
        @RequestParam(required = false) String genero,
        @RequestParam(required = false) Boolean esOutlet
    ) {
        List<Modelo> modelos = modeloRepository.filtrarModelos(tipoId, marcaId, genero, esOutlet);

        List<Map<String, Object>> response = modelos.stream().map(m -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", m.getId());
            map.put("nombre", m.getNombre());
            map.put("tipo", m.getTipoCalzado().getNombre());
            map.put("marca", m.getMarca().getNombre());
            map.put("genero", m.getGenero());
            map.put("temporada", m.getTemporada());
            map.put("precioLista", m.getPrecioListaBase());
            map.put("esOutlet", m.getEsOutlet());
            map.put("descuento", m.getPorcentajeDescuento());
            map.put("precioEfectivo", m.getPrecioEfectivo());

            // SKU representativo y foto principal
            List<CalzadoSku> skus = skuRepository.findByModeloColorModeloId(m.getId());
            if (!skus.isEmpty()) {
                map.put("sku", skus.get(0).getSku());
                map.put("fotoUrl", skus.get(0).getModeloColor().getFotoPrincipalUrl());
            } else {
                map.put("sku", "SIN-SKU");
                map.put("fotoUrl", null);
            }
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/tipos")
    public ResponseEntity<List<TipoCalzado>> listarTipos() {
        return ResponseEntity.ok(tipoCalzadoRepository.findByActiveTrue());
    }

    @GetMapping("/marcas")
    public ResponseEntity<List<Marca>> listarMarcas() {
        return ResponseEntity.ok(marcaRepository.findByActiveTrue());
    }

    @GetMapping("/colores")
    public ResponseEntity<List<Color>> listarColores() {
        return ResponseEntity.ok(colorRepository.findByActiveTrue());
    }

    @GetMapping("/tallas")
    public ResponseEntity<List<TallaEquivalencia>> listarTallas() {
        return ResponseEntity.ok(tallaRepository.findByActiveTrueOrderByTallaEurAsc());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> detalleModelo(@PathVariable Integer id) {
        Modelo m = modeloRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Modelo no encontrado"));

        List<CalzadoSku> skus = skuRepository.findByModeloColorModeloId(id);

        Map<String, Object> map = new HashMap<>();
        map.put("id", m.getId());
        map.put("nombre", m.getNombre());
        map.put("materialExterior", m.getMaterialExterior());
        map.put("tipoSuela", m.getTipoSuela());
        map.put("precioEfectivo", m.getPrecioEfectivo());
        map.put("skus", skus.stream().map(s -> Map.of(
            "sku", s.getSku(),
            "tallaEur", s.getTalla().getTallaEur(),
            "tallaCmMx", s.getTalla().getTallaCmMx(),
            "precio", s.getPrecioVentaVigente()
        )).collect(Collectors.toList()));

        return ResponseEntity.ok(map);
    }

    @PostMapping(value = "/crear-con-imagen", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> crearModeloConImagen(
        @RequestParam("nombre") String nombre,
        @RequestParam("idTipoCalzado") Integer idTipoCalzado,
        @RequestParam("idMarca") Integer idMarca,
        @RequestParam("idColor") Integer idColor,
        @RequestParam("precioLista") BigDecimal precioLista,
        @RequestParam(value = "materialExterior", required = false) String materialExterior,
        @RequestParam(value = "tipoSuela", required = false) String tipoSuela,
        @RequestParam(value = "tipoCierre", required = false) String tipoCierre,
        @RequestParam(value = "genero", required = false) String genero,
        @RequestParam(value = "temporada", required = false) String temporada,
        @RequestParam(value = "esOutlet", required = false, defaultValue = "false") Boolean esOutlet,
        @RequestParam(value = "porcentajeDescuento", required = false, defaultValue = "0") BigDecimal porcentajeDescuento,
        @RequestParam(value = "stockInicialPorTalla", required = false, defaultValue = "10") Integer stockInicial,
        @RequestParam(value = "imagen", required = false) MultipartFile imagen
    ) {
        String fotoUrl = catalogoService.guardarImagenEnDisco(imagen);

        Modelo modeloCreado = catalogoService.crearModeloCompleto(
            idTipoCalzado,
            idMarca,
            idColor,
            nombre,
            materialExterior,
            null,
            tipoSuela,
            tipoCierre,
            genero,
            temporada,
            precioLista,
            esOutlet,
            porcentajeDescuento,
            fotoUrl,
            null, // genera todas las tallas activas automáticamente
            null, // ingresa a Bodega 2 por defecto
            stockInicial
        );

        List<CalzadoSku> skusGenerados = skuRepository.findByModeloColorModeloId(modeloCreado.getId());

        // Map.of rejects a null value, and fotoUrl is legitimately null when no image was sent.
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("status", "SUCCESS");
        respuesta.put("id", modeloCreado.getId());
        respuesta.put("nombre", modeloCreado.getNombre());
        respuesta.put("fotoUrl", fotoUrl);
        respuesta.put("precioEfectivo", modeloCreado.getPrecioEfectivo());
        respuesta.put("totalSkusGenerados", skusGenerados.size());
        respuesta.put("skus", skusGenerados.stream().map(CalzadoSku::getSku).collect(Collectors.toList()));
        respuesta.put("mensaje", "Calzado creado exitosamente en PostgreSQL con SKUs y stock inicial generados.");
        return ResponseEntity.ok(respuesta);
    }
}
