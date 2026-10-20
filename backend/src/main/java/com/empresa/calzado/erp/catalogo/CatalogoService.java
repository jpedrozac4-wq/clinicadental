package com.empresa.calzado.erp.catalogo;

import com.empresa.calzado.erp.inventario.*;
import com.empresa.calzado.erp.seguridad.SystemUser;
import com.empresa.calzado.erp.seguridad.SystemUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

@Service
public class CatalogoService {

    private final ModeloRepository modeloRepository;
    private final TipoCalzadoRepository tipoCalzadoRepository;
    private final MarcaRepository marcaRepository;
    private final ColorRepository colorRepository;
    private final ModeloColorRepository modeloColorRepository;
    private final TallaEquivalenciaRepository tallaRepository;
    private final CalzadoSkuRepository skuRepository;
    private final BodegaRepository bodegaRepository;
    private final InventarioStockRepository stockRepository;
    private final KardexMovimientoRepository kardexRepository;
    private final MovimientoStockTipoRepository movimientoTipoRepository;
    private final SystemUserRepository userRepository;
    private final ImagenCalzadoStorage imagenStorage;

    public CatalogoService(
        ModeloRepository modeloRepository,
        TipoCalzadoRepository tipoCalzadoRepository,
        MarcaRepository marcaRepository,
        ColorRepository colorRepository,
        ModeloColorRepository modeloColorRepository,
        TallaEquivalenciaRepository tallaRepository,
        CalzadoSkuRepository skuRepository,
        BodegaRepository bodegaRepository,
        InventarioStockRepository stockRepository,
        KardexMovimientoRepository kardexRepository,
        MovimientoStockTipoRepository movimientoTipoRepository,
        SystemUserRepository userRepository,
        ImagenCalzadoStorage imagenStorage
    ) {
        this.modeloRepository = modeloRepository;
        this.tipoCalzadoRepository = tipoCalzadoRepository;
        this.marcaRepository = marcaRepository;
        this.colorRepository = colorRepository;
        this.modeloColorRepository = modeloColorRepository;
        this.tallaRepository = tallaRepository;
        this.skuRepository = skuRepository;
        this.bodegaRepository = bodegaRepository;
        this.stockRepository = stockRepository;
        this.kardexRepository = kardexRepository;
        this.movimientoTipoRepository = movimientoTipoRepository;
        this.userRepository = userRepository;
        this.imagenStorage = imagenStorage;
    }

    /**
     * Stores the legacy multipart upload through the managed storage component. An absent image
     * yields {@code null} rather than an invented default path.
     */
    public String guardarImagenEnDisco(MultipartFile file) {
        return imagenStorage.guardar(file);
    }

    @Transactional
    public Modelo crearModeloCompleto(
        Integer idTipoCalzado,
        Integer idMarca,
        Integer idColor,
        String nombre,
        String materialExterior,
        String materialForro,
        String tipoSuela,
        String tipoCierre,
        String genero,
        String temporada,
        BigDecimal precioLista,
        Boolean esOutlet,
        BigDecimal porcentajeDescuento,
        String fotoUrl,
        List<Integer> tallasIds,
        Integer idBodegaInicial,
        Integer stockPorTalla
    ) {
        TipoCalzado tipo = tipoCalzadoRepository.findById(idTipoCalzado)
            .orElseThrow(() -> new IllegalArgumentException("Tipo de calzado no encontrado"));
        Marca marca = marcaRepository.findById(idMarca)
            .orElseThrow(() -> new IllegalArgumentException("Marca no encontrada"));
        Color color = colorRepository.findById(idColor)
            .orElseThrow(() -> new IllegalArgumentException("Color no encontrado"));

        // Calcular el próximo correlativo numérico de 3 dígitos (ej: 001, 002, 012)
        int correlativo = 1;
        List<Modelo> existentes = modeloRepository.findAll();
        for (Modelo m : existentes) {
            if (m.getTipoCalzado().getId().equals(tipo.getId()) && m.getMarca().getId().equals(marca.getId())) {
                if (m.getCorrelativo() >= correlativo) {
                    correlativo = m.getCorrelativo() + 1;
                }
            }
        }

        Modelo modelo = new Modelo();
        modelo.setTipoCalzado(tipo);
        modelo.setMarca(marca);
        modelo.setCorrelativo(correlativo);
        modelo.setNombre(nombre);
        modelo.setMaterialExterior(materialExterior != null ? materialExterior : "Cuero");
        modelo.setMaterialForro(materialForro != null ? materialForro : "Textil");
        modelo.setTipoSuela(tipoSuela != null ? tipoSuela : "Goma Vulcanizada");
        modelo.setTipoCierre(tipoCierre != null ? tipoCierre : "Cordones");
        modelo.setGenero(genero != null ? genero : "UNISEX");
        modelo.setTemporada(temporada != null ? temporada : "General 2025");
        modelo.setPrecioListaBase(precioLista != null ? precioLista : BigDecimal.valueOf(50.00));
        modelo.setEsOutlet(Boolean.TRUE.equals(esOutlet));
        modelo.setPorcentajeDescuento(porcentajeDescuento != null ? porcentajeDescuento : BigDecimal.ZERO);
        modelo.setEsVisibleWeb(true);
        Modelo modeloGuardado = modeloRepository.save(modelo);

        // Crear Variante de Color con la imagen
        ModeloColor modeloColor = new ModeloColor();
        modeloColor.setModelo(modeloGuardado);
        modeloColor.setColor(color);
        modeloColor.setFotoPrincipalUrl(fotoUrl);
        ModeloColor mcGuardado = modeloColorRepository.save(modeloColor);

        // Generar los SKUs algorítmicos: [Tipo][Marca][Correlativo3Dígitos]-[Talla] (ej. TV001-40)
        List<TallaEquivalencia> tallas = (tallasIds != null && !tallasIds.isEmpty())
            ? tallaRepository.findAllById(tallasIds)
            : tallaRepository.findByActiveTrueOrderByTallaEurAsc();

        Bodega bodega = null;
        if (idBodegaInicial != null) {
            bodega = bodegaRepository.findById(idBodegaInicial).orElse(null);
        }
        if (bodega == null) {
            bodega = bodegaRepository.findFirstByEsDespachableWebTrueAndActiveTrue().orElse(null);
        }

        SystemUser admin = userRepository.findByUsername("admin").orElse(null);
        MovimientoStockTipo movTipo = movimientoTipoRepository.findByCode("INGRESO_PROVEEDOR").orElse(null);

        // A brand-new model has exactly one colour variant, so there are no sibling names to
        // disambiguate against.
        String tokenColor = CodigoSku.tokenColor(color.getNombre(), List.of());

        for (TallaEquivalencia t : tallas) {
            String codigoSku = CodigoSku.generar(
                tipo.getCodigoLetra(),
                marca.getCodigoLetra(),
                correlativo,
                t.getTallaEur(),
                tokenColor
            );

            CalzadoSku sku = new CalzadoSku();
            sku.setModeloColor(mcGuardado);
            sku.setTalla(t);
            sku.setSku(codigoSku);
            sku.setPrecioVentaVigente(modeloGuardado.getPrecioEfectivo());
            sku.setPesoGramos(800);
            sku.setActive(true);
            CalzadoSku skuGuardado = skuRepository.save(sku);

            // Inicializar Stock en Bodega si se especificó
            if (bodega != null && stockPorTalla != null && stockPorTalla > 0) {
                InventarioStock stock = new InventarioStock();
                stock.setBodega(bodega);
                stock.setCalzadoSku(skuGuardado);
                stock.setCantidadDisponible(stockPorTalla);
                stock.setCantidadReservada(0);
                stock.setStockSeguridadMinimo(2);
                stockRepository.save(stock);

                if (movTipo != null && admin != null) {
                    KardexMovimiento kardex = new KardexMovimiento();
                    kardex.setBodega(bodega);
                    kardex.setCalzadoSku(skuGuardado);
                    kardex.setMovimientoTipo(movTipo);
                    kardex.setCantidadMovimiento(stockPorTalla);
                    kardex.setStockAnterior(0);
                    kardex.setStockPosterior(stockPorTalla);
                    kardex.setDocumentoTipo("ALTA_PRODUCTO");
                    kardex.setDocumentoId((long) modeloGuardado.getId());
                    kardex.setUser(admin);
                    kardexRepository.save(kardex);
                }
            }
        }

        return modeloGuardado;
    }
}
