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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Catalogue master-data administration: back-office reads and model CRUD.
 *
 * <p>Business rules enforced here:
 * <ul>
 *   <li>{@code porcentajeDescuento} is 0..100 exclusive of 100 and a value greater than 0 forces
 *       {@code esOutlet}. The value 100 is rejected because it would leave the effective price at
 *       zero, which both the storefront and the {@code precio_venta_vigente > 0} database
 *       constraint forbid.</li>
 *   <li>{@code esOutlet = true} with a 0 discount is deliberately allowed: a model can be flagged as
 *       outlet before a discount is published, and its effective price then equals the base price.</li>
 *   <li>Changing the base price or the outlet discount recomputes {@code precioVentaVigente} on
 *       every SKU of the model.</li>
 *   <li>Web visibility is the logical removal; no model is ever physically deleted.</li>
 *   <li>{@code correlativo} is 1..999, unique per (tipo, marca), auto-assigned to the next free
 *       value when omitted.</li>
 * </ul>
 *
 * <p>It intentionally does not touch {@link CatalogoService} or the public storefront endpoints.
 */
@Service
public class CatalogoAdminService {

    private static final Set<String> GENEROS_VALIDOS = Set.of("HOMBRE", "MUJER", "UNISEX", "NINO");
    private static final int CORRELATIVO_MIN = 1;
    private static final int CORRELATIVO_MAX = 999;
    private static final BigDecimal DESCUENTO_MIN = BigDecimal.ZERO;
    /** Exclusive upper bound: 100 would leave the effective price at zero. */
    private static final BigDecimal DESCUENTO_MAX = BigDecimal.valueOf(100);
    private static final int PESO_MIN = 1;
    private static final int PESO_MAX = 10000;
    private static final int PESO_DEFAULT = 800;
    /** Upper bound of the {@code talla_eur precision = 3, scale = 1} column. */
    private static final BigDecimal TALLA_EUR_MAX = new BigDecimal("99.9");
    private static final Pattern EAN_PATTERN = Pattern.compile("^[0-9]{8,14}$");

    private final ModeloRepository modeloRepository;
    private final ModeloColorRepository modeloColorRepository;
    private final CalzadoSkuRepository skuRepository;
    private final TipoCalzadoRepository tipoCalzadoRepository;
    private final MarcaRepository marcaRepository;
    private final TallaEquivalenciaRepository tallaRepository;
    private final ColorRepository colorRepository;
    private final ImagenCalzadoStorage imagenStorage;

    public CatalogoAdminService(
        ModeloRepository modeloRepository,
        ModeloColorRepository modeloColorRepository,
        CalzadoSkuRepository skuRepository,
        TipoCalzadoRepository tipoCalzadoRepository,
        MarcaRepository marcaRepository,
        TallaEquivalenciaRepository tallaRepository,
        ColorRepository colorRepository,
        ImagenCalzadoStorage imagenStorage
    ) {
        this.modeloRepository = modeloRepository;
        this.modeloColorRepository = modeloColorRepository;
        this.skuRepository = skuRepository;
        this.tipoCalzadoRepository = tipoCalzadoRepository;
        this.marcaRepository = marcaRepository;
        this.tallaRepository = tallaRepository;
        this.colorRepository = colorRepository;
        this.imagenStorage = imagenStorage;
    }

    // ------------------------------------------------------------------
    // Reads
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ModeloAdminResumen> listarModelos(
        String q, Integer marcaId, Integer tipoId, Boolean outlet, Boolean visibleWeb) {
        String patron = (q == null || q.isBlank())
            ? null
            : "%" + q.trim().toLowerCase(Locale.ROOT) + "%";

        return modeloRepository.buscarAdmin(patron, marcaId, tipoId, outlet, visibleWeb).stream()
            .map(this::aResumen)
            .toList();
    }

    @Transactional(readOnly = true)
    public ModeloAdminDetalle obtenerModelo(Integer id) {
        Modelo modelo = buscarModelo(id);
        return aDetalle(modelo, cargarColores(id), cargarSkus(id));
    }

    @Transactional(readOnly = true)
    public List<SkuAdmin> listarSkus(Integer modeloId) {
        if (modeloId == null) {
            throw new CatalogoBusinessException("El parametro modeloId es obligatorio");
        }
        if (!modeloRepository.existsById(modeloId)) {
            throw noEncontrado(modeloId);
        }
        return cargarSkus(modeloId);
    }

    // ------------------------------------------------------------------
    // Model CRUD
    // ------------------------------------------------------------------

    @Transactional
    public ModeloAdminDetalle crearModelo(ModeloCreateRequest request) {
        if (request == null) {
            throw new CatalogoBusinessException("El cuerpo de la solicitud es obligatorio");
        }

        String nombre = textoObligatorio(request.nombre(), "nombre", 100);
        String materialExterior = textoObligatorio(request.materialExterior(), "materialExterior", 60);
        String materialForro = textoOpcional(request.materialForro(), "materialForro", 60);
        String tipoSuela = textoObligatorio(request.tipoSuela(), "tipoSuela", 60);
        String tipoCierre = textoObligatorio(request.tipoCierre(), "tipoCierre", 60);
        String genero = generoValido(request.genero());
        String temporada = textoObligatorio(request.temporada(), "temporada", 40);
        BigDecimal precio = precioValido(request.precioListaBase());
        BigDecimal descuento = descuentoValido(request.porcentajeDescuento());
        // Rule: a discount greater than 0 always marks the model as outlet.
        boolean esOutlet = Boolean.TRUE.equals(request.esOutlet())
            || descuento.compareTo(BigDecimal.ZERO) > 0;

        Integer correlativo = request.correlativo() == null ? null : correlativoEnRango(request.correlativo());

        TipoCalzado tipo = resolverTipo(request.idTipoCalzado());
        Marca marca = resolverMarca(request.idMarca());
        if (correlativo == null) {
            correlativo = siguienteCorrelativo(tipo.getId(), marca.getId());
        } else {
            verificarCorrelativoUnico(correlativo, tipo.getId(), marca.getId(), null);
        }

        Modelo modelo = new Modelo();
        modelo.setTipoCalzado(tipo);
        modelo.setMarca(marca);
        modelo.setCorrelativo(correlativo);
        modelo.setNombre(nombre);
        modelo.setMaterialExterior(materialExterior);
        modelo.setMaterialForro(materialForro);
        modelo.setTipoSuela(tipoSuela);
        modelo.setTipoCierre(tipoCierre);
        modelo.setGenero(genero);
        modelo.setTemporada(temporada);
        modelo.setPrecioListaBase(precio);
        modelo.setEsOutlet(esOutlet);
        modelo.setPorcentajeDescuento(descuento);
        modelo.setEsVisibleWeb(request.esVisibleWeb() == null ? Boolean.TRUE : request.esVisibleWeb());

        Modelo guardado = modeloRepository.save(modelo);
        return aDetalle(guardado, List.of(), List.of());
    }

    @Transactional
    public ModeloAdminDetalle actualizarModelo(Integer id, ModeloUpdateRequest request) {
        Modelo modelo = buscarModelo(id);
        if (request == null) {
            throw new CatalogoBusinessException("El cuerpo de la solicitud es obligatorio");
        }

        TipoCalzado tipo = request.idTipoCalzado() == null
            ? modelo.getTipoCalzado()
            : resolverTipo(request.idTipoCalzado());
        Marca marca = request.idMarca() == null
            ? modelo.getMarca()
            : resolverMarca(request.idMarca());

        String nombre = request.nombre() == null
            ? modelo.getNombre() : textoObligatorio(request.nombre(), "nombre", 100);
        String materialExterior = request.materialExterior() == null
            ? modelo.getMaterialExterior() : textoObligatorio(request.materialExterior(), "materialExterior", 60);
        String materialForro = request.materialForro() == null
            ? modelo.getMaterialForro() : textoOpcional(request.materialForro(), "materialForro", 60);
        String tipoSuela = request.tipoSuela() == null
            ? modelo.getTipoSuela() : textoObligatorio(request.tipoSuela(), "tipoSuela", 60);
        String tipoCierre = request.tipoCierre() == null
            ? modelo.getTipoCierre() : textoObligatorio(request.tipoCierre(), "tipoCierre", 60);
        String genero = request.genero() == null ? modelo.getGenero() : generoValido(request.genero());
        String temporada = request.temporada() == null
            ? modelo.getTemporada() : textoObligatorio(request.temporada(), "temporada", 40);
        BigDecimal precio = request.precioListaBase() == null
            ? modelo.getPrecioListaBase() : precioValido(request.precioListaBase());
        BigDecimal descuento = request.porcentajeDescuento() == null
            ? modelo.getPorcentajeDescuento() : descuentoValido(request.porcentajeDescuento());
        boolean esOutlet = (request.esOutlet() == null
            ? Boolean.TRUE.equals(modelo.getEsOutlet()) : request.esOutlet())
            || descuento.compareTo(BigDecimal.ZERO) > 0;
        Integer correlativo = correlativoEnRango(
            request.correlativo() == null ? modelo.getCorrelativo() : request.correlativo());
        verificarCorrelativoUnico(correlativo, tipo.getId(), marca.getId(), id);
        verificarIdentidadEditable(modelo, tipo.getId(), marca.getId(), correlativo);

        modelo.setTipoCalzado(tipo);
        modelo.setMarca(marca);
        modelo.setCorrelativo(correlativo);
        modelo.setNombre(nombre);
        modelo.setMaterialExterior(materialExterior);
        modelo.setMaterialForro(materialForro);
        modelo.setTipoSuela(tipoSuela);
        modelo.setTipoCierre(tipoCierre);
        modelo.setGenero(genero);
        modelo.setTemporada(temporada);
        modelo.setPrecioListaBase(precio);
        modelo.setEsOutlet(esOutlet);
        modelo.setPorcentajeDescuento(descuento);
        if (request.esVisibleWeb() != null) {
            modelo.setEsVisibleWeb(request.esVisibleWeb());
        }

        Modelo guardado = modeloRepository.save(modelo);
        recalcularPreciosSku(guardado);
        return aDetalle(guardado, cargarColores(id), cargarSkus(id));
    }

    @Transactional
    public ModeloAdminDetalle actualizarOutlet(Integer id, OutletUpdateRequest request) {
        Modelo modelo = buscarModelo(id);
        if (request == null) {
            throw new CatalogoBusinessException("El cuerpo de la solicitud es obligatorio");
        }
        BigDecimal descuento = descuentoValido(request.porcentajeDescuento());
        // Rule: a discount greater than 0 always marks the model as outlet.
        boolean esOutlet = Boolean.TRUE.equals(request.esOutlet())
            || descuento.compareTo(BigDecimal.ZERO) > 0;

        modelo.setEsOutlet(esOutlet);
        modelo.setPorcentajeDescuento(descuento);

        Modelo guardado = modeloRepository.save(modelo);
        recalcularPreciosSku(guardado);
        return aDetalle(guardado, cargarColores(id), cargarSkus(id));
    }

    @Transactional
    public ModeloAdminDetalle actualizarVisibilidad(Integer id, VisibilidadWebUpdateRequest request) {
        Modelo modelo = buscarModelo(id);
        if (request == null || request.esVisibleWeb() == null) {
            throw new CatalogoBusinessException("El campo esVisibleWeb es obligatorio");
        }
        // Logical removal / restore: only the flag changes, never the row.
        modelo.setEsVisibleWeb(request.esVisibleWeb());

        Modelo guardado = modeloRepository.save(modelo);
        return aDetalle(guardado, cargarColores(id), cargarSkus(id));
    }

    // ------------------------------------------------------------------
    // SKU CRUD
    // ------------------------------------------------------------------

    /**
     * Creates an SKU for an existing colour variant and size.
     *
     * <p>The code is never taken from the request: it is derived from the model identity and the
     * size through {@link CodigoSku}. The sale price always mirrors the model's effective price, so
     * an outlet discount is honoured without duplicating the calculation here.
     */
    @Transactional
    public SkuAdmin crearSku(SkuCreateRequest request) {
        if (request == null) {
            throw new CatalogoBusinessException("El cuerpo de la solicitud es obligatorio");
        }
        if (request.idModeloColor() == null) {
            throw new CatalogoBusinessException("El parametro idModeloColor es obligatorio");
        }
        if (request.idTalla() == null) {
            throw new CatalogoBusinessException("El parametro idTalla es obligatorio");
        }

        ModeloColor modeloColor = modeloColorRepository.findById(request.idModeloColor())
            .orElseThrow(() -> new CatalogoBusinessException(
                HttpStatus.NOT_FOUND, "Variante de color no encontrada: " + request.idModeloColor()));
        TallaEquivalencia talla = tallaRepository.findById(request.idTalla())
            .orElseThrow(() -> new CatalogoBusinessException(
                HttpStatus.NOT_FOUND, "Talla no encontrada: " + request.idTalla()));

        if (!Boolean.TRUE.equals(talla.getActive())) {
            throw new CatalogoBusinessException("La talla no esta activa: " + request.idTalla());
        }

        // Fail with a readable 400-class error before uq_sku_variante_talla blows up as a raw
        // integrity violation on insert.
        if (skuRepository.existsByModeloColorIdAndTallaId(request.idModeloColor(), request.idTalla())) {
            throw new CatalogoBusinessException("Ya existe un SKU para esa variante de color y talla");
        }

        int pesoGramos = pesoValido(request.pesoGramos());
        String codigoBarrasEan = eanValido(request.codigoBarrasEan());
        if (codigoBarrasEan != null && skuRepository.existsByCodigoBarrasEan(codigoBarrasEan)) {
            throw new CatalogoBusinessException(
                "Ya existe un SKU con el codigo de barras " + codigoBarrasEan);
        }

        Modelo modelo = modeloColor.getModelo();
        // The code carries a colour token, so two colour variants of the same model never derive
        // the same sku for the same size. The token is computed once, at creation, and is never
        // recalculated: a code already printed or scanned must stay stable.
        String tokenColor = CodigoSku.tokenColor(
            modeloColor.getColor() == null ? null : modeloColor.getColor().getNombre(),
            nombresDeLosOtrosColores(modelo, modeloColor.getId()));
        String codigo = CodigoSku.generar(
            modelo.getTipoCalzado().getCodigoLetra(),
            modelo.getMarca().getCodigoLetra(),
            modelo.getCorrelativo(),
            talla.getTallaEur(),
            tokenColor);
        if (skuRepository.existsBySku(codigo)) {
            throw new CatalogoBusinessException(
                "El codigo de SKU " + codigo + " ya existe; revise la variante de color y la talla");
        }

        CalzadoSku sku = new CalzadoSku();
        sku.setModeloColor(modeloColor);
        sku.setTalla(talla);
        sku.setSku(codigo);
        sku.setCodigoBarrasEan(codigoBarrasEan);
        sku.setPrecioVentaVigente(modelo.getPrecioEfectivo());
        sku.setPesoGramos(pesoGramos);
        sku.setActive(true);

        return aSkuAdmin(skuRepository.save(sku));
    }

    /**
     * Updates the editable attributes of an SKU.
     *
     * <p>Only the barcode and the weight are editable. The size is not: the SKU code embeds it, so
     * changing it would leave the printed or scanned code inconsistent with the row (the same
     * reasoning used by {@code verificarIdentidadEditable} for the model). Create the missing SKU
     * and logically deactivate the wrong one instead.
     *
     * <p>A {@code null} field keeps the stored value; an empty {@code codigoBarrasEan} clears it.
     */
    @Transactional
    public SkuAdmin actualizarSku(Integer id, SkuUpdateRequest request) {
        CalzadoSku sku = buscarSku(id);
        if (request == null) {
            throw new CatalogoBusinessException("El cuerpo de la solicitud es obligatorio");
        }
        if (request.codigoBarrasEan() != null) {
            String codigoBarrasEan = eanValido(request.codigoBarrasEan());
            if (codigoBarrasEan != null && skuRepository.existsByCodigoBarrasEanAndIdNot(codigoBarrasEan, sku.getId())) {
                throw new CatalogoBusinessException(
                    "Ya existe un SKU con el codigo de barras " + codigoBarrasEan);
            }
            sku.setCodigoBarrasEan(codigoBarrasEan);
        }
        if (request.pesoGramos() != null) {
            sku.setPesoGramos(pesoValido(request.pesoGramos()));
        }
        return aSkuAdmin(skuRepository.save(sku));
    }

    /**
     * Logical removal / restore: only the active flag changes, the row is never deleted.
     */
    @Transactional
    public SkuAdmin actualizarSkuActivo(Integer id, SkuActiveUpdateRequest request) {
        CalzadoSku sku = buscarSku(id);
        if (request == null || request.active() == null) {
            throw new CatalogoBusinessException("El campo active es obligatorio");
        }
        sku.setActive(request.active());
        return aSkuAdmin(skuRepository.save(sku));
    }

    // ------------------------------------------------------------------
    // Size CRUD
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<TallaAdmin> listarTallas() {
        return tallaRepository.findAllByOrderByTallaEurAsc().stream()
            .map(this::aTallaAdmin)
            .toList();
    }

    /**
     * Creates a size. {@code tallaEur} is validated against the {@code precision = 3, scale = 1}
     * column before the insert, and a duplicate fails here with a readable 400-class error instead
     * of surfacing as a raw unique-constraint violation.
     */
    @Transactional
    public TallaAdmin crearTalla(TallaCreateRequest request) {
        if (request == null) {
            throw new CatalogoBusinessException("El cuerpo de la solicitud es obligatorio");
        }
        BigDecimal tallaEur = tallaEurValida(request.tallaEur());
        BigDecimal tallaCmMx = tallaCmMxValido(request.tallaCmMx());
        if (tallaRepository.existsByTallaEur(tallaEur)) {
            throw new CatalogoBusinessException("Ya existe una talla con tallaEur " + tallaEur);
        }

        TallaEquivalencia talla = new TallaEquivalencia();
        talla.setTallaEur(tallaEur);
        talla.setTallaUsHombre(request.tallaUsHombre());
        talla.setTallaUsMujer(request.tallaUsMujer());
        talla.setTallaUk(request.tallaUk());
        talla.setTallaCmMx(tallaCmMx);
        talla.setActive(true);
        return aTallaAdmin(tallaRepository.save(talla));
    }

    /**
     * Updates the editable attributes of a size.
     *
     * <p>{@code tallaEur} is deliberately not accepted: the size part of an SKU code already
     * printed or scanned was derived from it at creation, so it must never change. A {@code null}
     * field keeps the stored value.
     */
    @Transactional
    public TallaAdmin actualizarTalla(Integer id, TallaUpdateRequest request) {
        TallaEquivalencia talla = buscarTalla(id);
        if (request == null) {
            throw new CatalogoBusinessException("El cuerpo de la solicitud es obligatorio");
        }
        if (request.tallaUsHombre() != null) {
            talla.setTallaUsHombre(request.tallaUsHombre());
        }
        if (request.tallaUsMujer() != null) {
            talla.setTallaUsMujer(request.tallaUsMujer());
        }
        if (request.tallaUk() != null) {
            talla.setTallaUk(request.tallaUk());
        }
        if (request.tallaCmMx() != null) {
            talla.setTallaCmMx(tallaCmMxValido(request.tallaCmMx()));
        }
        return aTallaAdmin(tallaRepository.save(talla));
    }

    /**
     * Logical removal / restore: only the active flag changes, the row is never deleted. This is
     * the only logical-delete path for a size.
     */
    @Transactional
    public TallaAdmin actualizarTallaActivo(Integer id, TallaActiveUpdateRequest request) {
        TallaEquivalencia talla = buscarTalla(id);
        if (request == null || request.active() == null) {
            throw new CatalogoBusinessException("El parametro active es obligatorio");
        }
        talla.setActive(request.active());
        return aTallaAdmin(tallaRepository.save(talla));
    }

    private BigDecimal tallaEurValida(BigDecimal tallaEur) {
        if (tallaEur == null) {
            throw new CatalogoBusinessException("El parametro tallaEur es obligatorio");
        }
        if (tallaEur.compareTo(BigDecimal.ZERO) <= 0) {
            throw new CatalogoBusinessException("El parametro tallaEur debe ser mayor que 0");
        }
        // The column is precision = 3, scale = 1: 0.1..99.9 with at most one decimal.
        if (tallaEur.scale() > 1 || tallaEur.compareTo(TALLA_EUR_MAX) > 0) {
            throw new CatalogoBusinessException(
                "El parametro tallaEur debe estar entre 0.1 y 99.9 con un maximo de un decimal");
        }
        // Normalise to the column scale so the stored value and the uniqueness check agree.
        return tallaEur.setScale(1, RoundingMode.UNNECESSARY);
    }

    private BigDecimal tallaCmMxValido(BigDecimal tallaCmMx) {
        if (tallaCmMx == null) {
            throw new CatalogoBusinessException("El parametro tallaCmMx es obligatorio");
        }
        if (tallaCmMx.compareTo(BigDecimal.ZERO) <= 0) {
            throw new CatalogoBusinessException("El parametro tallaCmMx debe ser mayor que 0");
        }
        return tallaCmMx;
    }

    private TallaEquivalencia buscarTalla(Integer id) {
        if (id == null) {
            throw new CatalogoBusinessException("El identificador de la talla es obligatorio");
        }
        return tallaRepository.findById(id).orElseThrow(() -> new CatalogoBusinessException(
            HttpStatus.NOT_FOUND, "Talla no encontrada: " + id));
    }

    private TallaAdmin aTallaAdmin(TallaEquivalencia talla) {
        return new TallaAdmin(
            talla.getId(),
            talla.getTallaEur(),
            talla.getTallaUsHombre(),
            talla.getTallaUsMujer(),
            talla.getTallaUk(),
            talla.getTallaCmMx(),
            talla.getActive());
    }

    // ------------------------------------------------------------------
    // Colour variants
    // ------------------------------------------------------------------

    /**
     * Adds a colour variant to a model. The (modelo, color) pair is unique through
     * {@code uq_modelo_color}; a repeated pair fails here with a readable 400-class error instead
     * of surfacing as a raw integrity violation. The new variant starts with no photo.
     */
    @Transactional
    public ModeloColorAdmin agregarColor(Integer modeloId, ColorVarianteCreateRequest request) {
        Modelo modelo = buscarModelo(modeloId);
        if (request == null || request.idColor() == null) {
            throw new CatalogoBusinessException("El campo idColor es obligatorio");
        }
        Color color = colorRepository.findById(request.idColor())
            .orElseThrow(() -> new CatalogoBusinessException(HttpStatus.NOT_FOUND,
                "Color no encontrado: " + request.idColor()));
        if (modeloColorRepository.existsByModeloIdAndColorId(modeloId, request.idColor())) {
            throw new CatalogoBusinessException("La variante de color ya existe para ese modelo");
        }

        ModeloColor variante = new ModeloColor();
        variante.setModelo(modelo);
        variante.setColor(color);
        // A new variant never carries an invented photo path.
        variante.setFotoPrincipalUrl(null);
        return aColorAdmin(modeloColorRepository.save(variante));
    }

    /**
     * Sets the main photo of a colour variant and removes the previous managed file.
     *
     * <p>The row is saved first, so a failed save never destroys the image the storefront is still
     * serving. An unmanaged previous url (for example a seeded {@code /assets/...} path) is never
     * deleted.
     */
    @Transactional
    public ModeloColorAdmin subirImagenColor(Integer modeloId, Integer colorId, MultipartFile imagen) {
        ModeloColor variante = buscarVariante(modeloId, colorId);
        if (imagen == null || imagen.isEmpty()) {
            throw new CatalogoBusinessException("El archivo de imagen es obligatorio");
        }
        String anterior = variante.getFotoPrincipalUrl();
        variante.setFotoPrincipalUrl(imagenStorage.guardar(imagen));
        ModeloColor guardada = modeloColorRepository.save(variante);
        // Delete only after a successful save, and only a file this component manages.
        if (imagenStorage.esGestionada(anterior)) {
            imagenStorage.borrar(anterior);
        }
        return aColorAdmin(guardada);
    }

    /**
     * Clears the main photo of a colour variant and removes the previous managed file after the
     * row is saved.
     */
    @Transactional
    public ModeloColorAdmin eliminarImagenColor(Integer modeloId, Integer colorId) {
        ModeloColor variante = buscarVariante(modeloId, colorId);
        String anterior = variante.getFotoPrincipalUrl();
        variante.setFotoPrincipalUrl(null);
        ModeloColor guardada = modeloColorRepository.save(variante);
        if (imagenStorage.esGestionada(anterior)) {
            imagenStorage.borrar(anterior);
        }
        return aColorAdmin(guardada);
    }

    // ------------------------------------------------------------------
    // Business helpers
    // ------------------------------------------------------------------

    /**
     * Recomputes {@code precioVentaVigente} on every SKU of the model from the model's effective
     * price. Called after any change to the base price or the outlet discount.
     */
    private void recalcularPreciosSku(Modelo modelo) {
        BigDecimal precioEfectivo = modelo.getPrecioEfectivo();
        for (CalzadoSku sku : skuRepository.findByModeloColorModeloId(modelo.getId())) {
            sku.setPrecioVentaVigente(precioEfectivo);
            skuRepository.save(sku);
        }
    }

    private Integer siguienteCorrelativo(Integer tipoCalzadoId, Integer marcaId) {
        Set<Integer> usados = new java.util.HashSet<>(
            modeloRepository.findByTipoCalzadoIdAndMarcaId(tipoCalzadoId, marcaId).stream()
                .map(Modelo::getCorrelativo)
                .toList());
        for (int candidato = CORRELATIVO_MIN; candidato <= CORRELATIVO_MAX; candidato++) {
            if (!usados.contains(candidato)) {
                return candidato;
            }
        }
        throw new CatalogoBusinessException(
            "No hay correlativos disponibles (1..999) para ese tipo de calzado y marca");
    }

    private int correlativoEnRango(Integer correlativo) {
        if (correlativo == null || correlativo < CORRELATIVO_MIN || correlativo > CORRELATIVO_MAX) {
            throw new CatalogoBusinessException("El correlativo debe estar entre 1 y 999");
        }
        return correlativo;
    }

    private void verificarCorrelativoUnico(Integer correlativo, Integer tipoCalzadoId, Integer marcaId, Integer excluirId) {
        boolean duplicado = excluirId == null
            ? modeloRepository.existsByTipoCalzadoIdAndMarcaIdAndCorrelativo(tipoCalzadoId, marcaId, correlativo)
            : modeloRepository.existsByTipoCalzadoIdAndMarcaIdAndCorrelativoAndIdNot(
                tipoCalzadoId, marcaId, correlativo, excluirId);
        if (duplicado) {
            throw new CatalogoBusinessException(
                "Ya existe un modelo con el correlativo " + correlativo + " para ese tipo de calzado y marca");
        }
    }

    /**
     * Guards the fields that the SKU code is built from.
     *
     * <p>An SKU is {@code [tipoCodigo][marcaCodigo][correlativo]-[tallaEur]}, so changing the type,
     * the brand or the correlativo of a model that already has SKUs would leave every existing code
     * inconsistent with its own model. Those three fields stay editable only while the model is
     * still a draft with no SKUs.
     */
    private void verificarIdentidadEditable(
        Modelo modelo, Integer tipoCalzadoId, Integer marcaId, Integer correlativo) {
        boolean cambiaIdentidad =
            !tipoCalzadoId.equals(modelo.getTipoCalzado().getId())
                || !marcaId.equals(modelo.getMarca().getId())
                || !correlativo.equals(modelo.getCorrelativo());

        if (!cambiaIdentidad) {
            return;
        }
        if (!skuRepository.findByModeloColorModeloId(modelo.getId()).isEmpty()) {
            throw new CatalogoBusinessException(
                "No se pueden cambiar el tipo de calzado, la marca ni el correlativo: los SKU ya "
                    + "generados incorporan esos datos en su codigo. Cree un modelo nuevo y traslade las tallas.");
        }
    }

    private TipoCalzado resolverTipo(Integer id) {
        if (id == null) {
            throw new CatalogoBusinessException("El tipo de calzado es obligatorio");
        }
        return tipoCalzadoRepository.findById(id)
            .orElseThrow(() -> new CatalogoBusinessException("Tipo de calzado no encontrado: " + id));
    }

    private Marca resolverMarca(Integer id) {
        if (id == null) {
            throw new CatalogoBusinessException("La marca es obligatoria");
        }
        return marcaRepository.findById(id)
            .orElseThrow(() -> new CatalogoBusinessException("Marca no encontrada: " + id));
    }

    private String textoObligatorio(String valor, String campo, int max) {
        if (valor == null || valor.isBlank()) {
            throw new CatalogoBusinessException("El campo " + campo + " es obligatorio");
        }
        String limpio = valor.trim();
        if (limpio.length() > max) {
            throw new CatalogoBusinessException("El campo " + campo + " no puede superar " + max + " caracteres");
        }
        return limpio;
    }

    private String textoOpcional(String valor, String campo, int max) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim();
        if (limpio.isEmpty()) {
            return null;
        }
        if (limpio.length() > max) {
            throw new CatalogoBusinessException("El campo " + campo + " no puede superar " + max + " caracteres");
        }
        return limpio;
    }

    private String generoValido(String genero) {
        if (genero == null || genero.isBlank()) {
            throw new CatalogoBusinessException("El campo genero es obligatorio");
        }
        String normalizado = genero.trim().toUpperCase(Locale.ROOT);
        if (!GENEROS_VALIDOS.contains(normalizado)) {
            throw new CatalogoBusinessException(
                "El genero debe ser uno de " + GENEROS_VALIDOS);
        }
        return normalizado;
    }

    private BigDecimal precioValido(BigDecimal precio) {
        if (precio == null) {
            throw new CatalogoBusinessException("El campo precioListaBase es obligatorio");
        }
        if (precio.compareTo(BigDecimal.ZERO) <= 0) {
            throw new CatalogoBusinessException("El precioListaBase debe ser mayor que 0");
        }
        return precio;
    }

    private BigDecimal descuentoValido(BigDecimal descuento) {
        if (descuento == null) {
            return BigDecimal.ZERO;
        }
        // 100 is excluded on purpose: it would set the effective price to zero, which the
        // precio_venta_vigente > 0 database constraint rejects. Fail here with a usable message
        // instead of letting the insert blow up later.
        if (descuento.compareTo(DESCUENTO_MIN) < 0 || descuento.compareTo(DESCUENTO_MAX) >= 0) {
            throw new CatalogoBusinessException(
                "El porcentajeDescuento debe ser mayor o igual a 0 y menor que 100: "
                    + "un descuento del 100% dejaria el precio de venta en 0");
        }
        return descuento;
    }

    private Modelo buscarModelo(Integer id) {
        if (id == null) {
            throw new CatalogoBusinessException("El identificador del modelo es obligatorio");
        }
        return modeloRepository.findById(id).orElseThrow(() -> noEncontrado(id));
    }

    private CatalogoBusinessException noEncontrado(Integer id) {
        return new CatalogoBusinessException(HttpStatus.NOT_FOUND, "Modelo no encontrado: " + id);
    }

    private CalzadoSku buscarSku(Integer id) {
        if (id == null) {
            throw new CatalogoBusinessException("El identificador del SKU es obligatorio");
        }
        return skuRepository.findById(id).orElseThrow(() -> new CatalogoBusinessException(
            HttpStatus.NOT_FOUND, "SKU no encontrado: " + id));
    }

    private ModeloColor buscarVariante(Integer modeloId, Integer colorId) {
        return modeloColorRepository.findByModeloIdAndColorId(modeloId, colorId)
            .orElseThrow(() -> new CatalogoBusinessException(HttpStatus.NOT_FOUND,
                "Variante de color no encontrada para el modelo " + modeloId + ": color " + colorId));
    }

    private int pesoValido(Integer pesoGramos) {
        if (pesoGramos == null) {
            return PESO_DEFAULT;
        }
        if (pesoGramos < PESO_MIN || pesoGramos > PESO_MAX) {
            throw new CatalogoBusinessException(
                "El pesoGramos debe ser mayor que 0 y menor o igual a " + PESO_MAX);
        }
        return pesoGramos;
    }

    private String eanValido(String codigoBarrasEan) {
        if (codigoBarrasEan == null) {
            return null;
        }
        String limpio = codigoBarrasEan.trim();
        if (limpio.isEmpty()) {
            return null;
        }
        if (!EAN_PATTERN.matcher(limpio).matches()) {
            throw new CatalogoBusinessException("El codigoBarrasEan debe tener entre 8 y 14 digitos");
        }
        return limpio;
    }

    /**
     * Colour names of the other variants of the same model, used only to disambiguate the colour
     * token of a new SKU code.
     */
    private List<String> nombresDeLosOtrosColores(Modelo modelo, Integer modeloColorId) {
        return modeloColorRepository.findByModeloIdOrderByIdAsc(modelo.getId()).stream()
            .filter(variante -> !variante.getId().equals(modeloColorId))
            .map(variante -> variante.getColor() == null ? null : variante.getColor().getNombre())
            .filter(nombre -> nombre != null)
            .toList();
    }

    private List<ModeloColorAdmin> cargarColores(Integer modeloId) {
        return modeloColorRepository.findByModeloIdOrderByIdAsc(modeloId).stream()
            .map(this::aColorAdmin)
            .toList();
    }

    private ModeloColorAdmin aColorAdmin(ModeloColor color) {
        return new ModeloColorAdmin(
            color.getId(),
            color.getColor() != null ? color.getColor().getId() : null,
            color.getColor() != null ? color.getColor().getNombre() : null,
            color.getColor() != null ? color.getColor().getCodigoHex() : null,
            color.getFotoPrincipalUrl(),
            color.getGaleriaFotosJson());
    }

    private List<SkuAdmin> cargarSkus(Integer modeloId) {
        return skuRepository.findByModeloColorModeloId(modeloId).stream()
            .map(this::aSkuAdmin)
            .toList();
    }

    private SkuAdmin aSkuAdmin(CalzadoSku sku) {
        return new SkuAdmin(
            sku.getId(),
            sku.getSku(),
            sku.getTalla() != null ? sku.getTalla().getTallaEur() : null,
            sku.getTalla() != null ? sku.getTalla().getTallaCmMx() : null,
            sku.getPrecioVentaVigente(),
            sku.getActive(),
            sku.getModeloColor() != null ? sku.getModeloColor().getId() : null,
            sku.getPesoGramos(),
            sku.getCodigoBarrasEan());
    }

    private ModeloAdminResumen aResumen(Modelo modelo) {
        return new ModeloAdminResumen(
            modelo.getId(),
            modelo.getTipoCalzado() != null ? modelo.getTipoCalzado().getId() : null,
            modelo.getTipoCalzado() != null ? modelo.getTipoCalzado().getNombre() : null,
            modelo.getMarca() != null ? modelo.getMarca().getId() : null,
            modelo.getMarca() != null ? modelo.getMarca().getNombre() : null,
            modelo.getCorrelativo(),
            modelo.getNombre(),
            modelo.getGenero(),
            modelo.getTemporada(),
            modelo.getPrecioListaBase(),
            modelo.getPrecioEfectivo(),
            modelo.getEsOutlet(),
            modelo.getPorcentajeDescuento(),
            modelo.getEsVisibleWeb());
    }

    private ModeloAdminDetalle aDetalle(Modelo modelo, List<ModeloColorAdmin> colores, List<SkuAdmin> skus) {
        return new ModeloAdminDetalle(
            modelo.getId(),
            modelo.getTipoCalzado() != null ? modelo.getTipoCalzado().getId() : null,
            modelo.getTipoCalzado() != null ? modelo.getTipoCalzado().getNombre() : null,
            modelo.getMarca() != null ? modelo.getMarca().getId() : null,
            modelo.getMarca() != null ? modelo.getMarca().getNombre() : null,
            modelo.getCorrelativo(),
            modelo.getNombre(),
            modelo.getMaterialExterior(),
            modelo.getMaterialForro(),
            modelo.getTipoSuela(),
            modelo.getTipoCierre(),
            modelo.getGenero(),
            modelo.getTemporada(),
            modelo.getPrecioListaBase(),
            modelo.getPrecioEfectivo(),
            modelo.getEsOutlet(),
            modelo.getPorcentajeDescuento(),
            modelo.getEsVisibleWeb(),
            colores,
            skus);
    }
}
