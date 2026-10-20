package com.empresa.calzado.erp.ventas;

import com.empresa.calzado.erp.catalogo.CalzadoSku;
import com.empresa.calzado.erp.catalogo.CalzadoSkuRepository;
import com.empresa.calzado.erp.facturacion.*;
import com.empresa.calzado.erp.inventario.Bodega;
import com.empresa.calzado.erp.inventario.InventarioService;
import com.empresa.calzado.erp.inventario.InventarioStock;
import com.empresa.calzado.erp.inventario.InventarioStockRepository;
import com.empresa.calzado.erp.logistica.*;
import com.empresa.calzado.erp.pos.PagoTransaccionRepository;
import com.empresa.calzado.erp.pos.Sucursal;
import com.empresa.calzado.erp.pos.SucursalRepository;
import com.empresa.calzado.erp.reportes.FacturaVentaPdfService;
import com.empresa.calzado.erp.seguridad.SystemUser;
import com.empresa.calzado.erp.seguridad.SystemUserRepository;
import com.empresa.calzado.erp.ventas.dto.CotizacionEnvio;
import com.empresa.calzado.erp.ventas.dto.LineaCheckout;
import com.empresa.calzado.erp.ventas.dto.OrdenConsulta;
import com.empresa.calzado.erp.ventas.dto.OrdenConsultaLinea;
import com.empresa.calzado.erp.ventas.dto.SucursalRetiro;
import com.empresa.calzado.erp.ventas.dto.ValidacionCupon;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class CheckoutService {

    // Last-resort defaults used only when tarifario_envio has no active rows at all. They
    // reproduce the pre-tariff constants (base 4.50 / threshold 70.00) so an unseeded or emptied
    // table cannot break checkout and the mocked-repository tests stay green.
    private static final BigDecimal LEGACY_FLETE_BASE = new BigDecimal("4.50");
    private static final BigDecimal LEGACY_UMBRAL_ENVIO_GRATIS = new BigDecimal("70.00");
    private static final String LEGACY_ZONA_NOMBRE = "Zona base (tarifario no configurado)";

    /**
     * Shared 400 message for {@code GET /api/checkout/ordenes} when either query parameter is
     * absent or blank. Both parameters are mandatory: the endpoint is public, so requiring the
     * order number as well as the email keeps a guessed email from listing a customer's history.
     */
    public static final String PARAMETROS_CONSULTA_ORDEN_REQUERIDOS =
        "Los parametros email y numeroOrden son obligatorios";

    // Pragmatic email check: one non-empty local part, one @, a non-empty domain and a dot inside
    // it. Deliberately not full RFC 5322 (far more permissive and complex than a public capture
    // form needs) and deliberately not stricter, because an over-strict pattern rejects valid
    // addresses. The length bound mirrors the email_cliente VARCHAR(100) column so an oversized
    // address is a 400 instead of a database error.
    private static final Pattern EMAIL_CLIENTE = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int EMAIL_CLIENTE_MAX_LENGTH = 100;

    private final OrdenRepository ordenRepository;
    private final OrdenEstadoRepository ordenEstadoRepository;
    private final CustomerRepository customerRepository;
    private final CalzadoSkuRepository skuRepository;
    private final InventarioStockRepository inventarioStockRepository;
    private final ReservaCheckoutRepository reservaRepository;
    private final InventarioService inventarioService;
    private final FacturaRepository facturaRepository;
    private final FacturaTipoRepository facturaTipoRepository;
    private final FacturaEstadoRepository facturaEstadoRepository;
    private final PagoMetodoRepository pagoMetodoRepository;
    private final DespachoEnvioRepository despachoRepository;
    private final DespachoEstadoRepository despachoEstadoRepository;
    private final SystemUserRepository userRepository;
    private final TarifarioEnvioRepository tarifarioEnvioRepository;
    private final CuponDescuentoRepository cuponDescuentoRepository;
    private final SucursalRepository sucursalRepository;
    private final AlertaDemandaRepository alertaDemandaRepository;
    private final NumeracionFacturaService numeracionFacturaService;
    private final FacturaVentaPdfService facturaVentaPdfService;
    private final PagoTransaccionRepository pagoTransaccionRepository;

    public CheckoutService(
        OrdenRepository ordenRepository,
        OrdenEstadoRepository ordenEstadoRepository,
        CustomerRepository customerRepository,
        CalzadoSkuRepository skuRepository,
        InventarioStockRepository inventarioStockRepository,
        ReservaCheckoutRepository reservaRepository,
        InventarioService inventarioService,
        FacturaRepository facturaRepository,
        FacturaTipoRepository facturaTipoRepository,
        FacturaEstadoRepository facturaEstadoRepository,
        PagoMetodoRepository pagoMetodoRepository,
        DespachoEnvioRepository despachoRepository,
        DespachoEstadoRepository despachoEstadoRepository,
        SystemUserRepository userRepository,
        TarifarioEnvioRepository tarifarioEnvioRepository,
        CuponDescuentoRepository cuponDescuentoRepository,
        SucursalRepository sucursalRepository,
        AlertaDemandaRepository alertaDemandaRepository,
        NumeracionFacturaService numeracionFacturaService,
        FacturaVentaPdfService facturaVentaPdfService,
        PagoTransaccionRepository pagoTransaccionRepository
    ) {
        this.ordenRepository = ordenRepository;
        this.ordenEstadoRepository = ordenEstadoRepository;
        this.customerRepository = customerRepository;
        this.skuRepository = skuRepository;
        this.inventarioStockRepository = inventarioStockRepository;
        this.reservaRepository = reservaRepository;
        this.inventarioService = inventarioService;
        this.facturaRepository = facturaRepository;
        this.facturaTipoRepository = facturaTipoRepository;
        this.facturaEstadoRepository = facturaEstadoRepository;
        this.pagoMetodoRepository = pagoMetodoRepository;
        this.despachoRepository = despachoRepository;
        this.despachoEstadoRepository = despachoEstadoRepository;
        this.userRepository = userRepository;
        this.tarifarioEnvioRepository = tarifarioEnvioRepository;
        this.cuponDescuentoRepository = cuponDescuentoRepository;
        this.sucursalRepository = sucursalRepository;
        this.alertaDemandaRepository = alertaDemandaRepository;
        this.numeracionFacturaService = numeracionFacturaService;
        this.facturaVentaPdfService = facturaVentaPdfService;
        this.pagoTransaccionRepository = pagoTransaccionRepository;
    }

    /**
     * Quotes the shipping cost for a postal code and a cart subtotal.
     *
     * <p>The controller validates the inputs before calling it.
     */
    public CotizacionEnvio cotizarEnvio(String codigoPostal, BigDecimal subtotalProductos) {
        ZonaResuelta zona = resolverZona(codigoPostal);
        boolean envioGratis = subtotalProductos.compareTo(zona.umbralEnvioGratis()) >= 0;
        BigDecimal costoEnvio = envioGratis ? BigDecimal.ZERO : zona.costoFleteBase();
        return new CotizacionEnvio(zona.zonaNombre(), costoEnvio, envioGratis, zona.umbralEnvioGratis());
    }

    /**
     * Validates a coupon against a cart subtotal.
     *
     * <p>Business rejections are returned with {@code valido=false} instead of thrown, because the
     * storefront renders the message to the shopper.
     */
    public ValidacionCupon validarCupon(String codigoCupon, BigDecimal subtotalProductos) {
        String codigo = codigoCupon == null ? "" : codigoCupon.trim();
        CuponValidado resultado = evaluarCupon(codigo, normalizarSubtotal(subtotalProductos));
        if (!resultado.valido()) {
            return new ValidacionCupon(false, codigo, null, BigDecimal.ZERO, resultado.mensaje());
        }
        return new ValidacionCupon(
            true,
            resultado.cupon().getCodigoCupon(),
            resultado.cupon().getTipoDescuento(),
            resultado.descuento(),
            null
        );
    }

    /**
     * Lists the branches a shopper can choose for click & collect pickup.
     *
     * <p>Only active branches are returned. The response is a small DTO, never the entity, so the
     * storefront only receives the fields the picker renders.
     */
    public List<SucursalRetiro> listarSucursalesParaRetiro() {
        return sucursalRepository.findByActiveTrueOrderByIdAsc().stream()
            .map(sucursal -> new SucursalRetiro(sucursal.getId(), sucursal.getNombre()))
            .toList();
    }

    /**
     * Resolves the shipping zone for a postal code.
     *
     * <p>Among the active rows whose {@code codigo_postal_prefijo} is a prefix of the postal code,
     * the longest prefix wins; on an exact prefix-length tie the row with the lowest
     * {@code distancia_km_max} wins. With no match, the active row with the lowest distance is the
     * base zone. With no active rows at all, the legacy constants keep checkout working.
     */
    private ZonaResuelta resolverZona(String codigoPostal) {
        List<TarifarioEnvio> activas = tarifarioEnvioRepository.findByActiveTrue();
        if (activas == null || activas.isEmpty()) {
            // Last-resort default: an unseeded or emptied tarifario_envio table must never break
            // checkout, so reproduce the legacy constants (base 4.50 / threshold 70.00).
            return new ZonaResuelta(LEGACY_ZONA_NOMBRE, LEGACY_FLETE_BASE, LEGACY_UMBRAL_ENVIO_GRATIS);
        }

        String postal = codigoPostal == null ? "" : codigoPostal.trim();
        TarifarioEnvio mejor = null;
        int mejorLongitudPrefijo = -1;
        for (TarifarioEnvio tarifa : activas) {
            String prefijo = tarifa.getCodigoPostalPrefijo();
            if (prefijo == null || prefijo.isEmpty() || !postal.startsWith(prefijo)) {
                continue;
            }
            int longitud = prefijo.length();
            boolean masEspecifico = longitud > mejorLongitudPrefijo;
            boolean mismoPrefijoMasCercano = longitud == mejorLongitudPrefijo
                && mejor != null
                && tarifa.getDistanciaKmMax().compareTo(mejor.getDistanciaKmMax()) < 0;
            if (masEspecifico || mismoPrefijoMasCercano) {
                mejor = tarifa;
                mejorLongitudPrefijo = longitud;
            }
        }

        if (mejor == null) {
            // No prefix matched: fall back to the base zone, the active row with the lowest
            // distancia_km_max.
            mejor = activas.stream()
                .min(Comparator.comparing(TarifarioEnvio::getDistanciaKmMax))
                .orElseThrow();
        }
        return new ZonaResuelta(mejor.getZonaNombre(), mejor.getCostoFleteBase(), mejor.getUmbralCompraEnvioGratis());
    }

    /**
     * Applies the coupon business rules in the order the shopper experiences them: existence,
     * validity window, minimum purchase and remaining uses. The returned entity is the canonical
     * database row, so callers persist the canonical code and id.
     */
    private CuponValidado evaluarCupon(String codigo, BigDecimal subtotal) {
        Optional<CuponDescuento> encontrado =
            cuponDescuentoRepository.findByCodigoCuponIgnoreCaseAndActiveTrue(codigo);
        if (encontrado.isEmpty()) {
            return CuponValidado.rechazado("El cupon no existe o no esta activo");
        }

        CuponDescuento cupon = encontrado.get();
        OffsetDateTime ahora = OffsetDateTime.now();
        if (ahora.isBefore(cupon.getFechaInicio()) || ahora.isAfter(cupon.getFechaFin())) {
            return CuponValidado.rechazado("El cupon no esta vigente en la fecha actual");
        }
        if (subtotal.compareTo(cupon.getMontoMinimoCompra()) < 0) {
            return CuponValidado.rechazado(
                "El monto minimo de compra para este cupon es " + cupon.getMontoMinimoCompra());
        }
        if (cupon.getUsosActuales() >= cupon.getLimiteUsos()) {
            return CuponValidado.rechazado("El cupon ya alcanzo su limite de usos");
        }
        return CuponValidado.valido(cupon, calcularDescuento(cupon, subtotal));
    }

    /**
     * Computes the discount rounded HALF_UP to 2 decimals and capped at the subtotal, so it can
     * never be negative nor exceed the cart.
     */
    private static BigDecimal calcularDescuento(CuponDescuento cupon, BigDecimal subtotal) {
        BigDecimal descuento;
        if ("PORCENTAJE".equals(cupon.getTipoDescuento())) {
            descuento = subtotal
                .multiply(cupon.getValorDescuento())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        } else {
            descuento = cupon.getValorDescuento();
        }
        if (descuento.compareTo(subtotal) > 0) {
            descuento = subtotal;
        }
        return descuento.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Normalises a cart subtotal for coupon evaluation. A null or negative value is treated as
     * zero so it fails the minimum-purchase rule instead of throwing.
     */
    private static BigDecimal normalizarSubtotal(BigDecimal subtotalProductos) {
        if (subtotalProductos == null || subtotalProductos.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        return subtotalProductos;
    }

    private record ZonaResuelta(String zonaNombre, BigDecimal costoFleteBase, BigDecimal umbralEnvioGratis) {}

    private record CuponValidado(boolean valido, CuponDescuento cupon, BigDecimal descuento, String mensaje) {
        static CuponValidado rechazado(String mensaje) {
            return new CuponValidado(false, null, BigDecimal.ZERO, mensaje);
        }

        static CuponValidado valido(CuponDescuento cupon, BigDecimal descuento) {
            return new CuponValidado(true, cupon, descuento, null);
        }
    }

    @Transactional
    public Orden crearOrdenConReserva2Horas(String emailCliente, String nombreCliente, String skuCodigo, int cantidad, String codigoCupon, String codigoPostal) {
        return crearOrdenConReserva2Horas(emailCliente, nombreCliente, skuCodigo, cantidad, codigoCupon, codigoPostal, null, null);
    }

    @Transactional
    public Orden crearOrdenConReserva2Horas(String emailCliente, String nombreCliente, String skuCodigo, int cantidad, String codigoCupon, String codigoPostal, String tipoEntrega, Integer idSucursalRetiro) {
        return crearOrdenConReserva2Horas(emailCliente, nombreCliente, skuCodigo, cantidad, codigoCupon, codigoPostal, tipoEntrega, idSucursalRetiro, null);
    }

    @Transactional
    public Orden crearOrdenConReserva2Horas(String emailCliente, String nombreCliente, String skuCodigo, int cantidad, String codigoCupon, String codigoPostal, String tipoEntrega, Integer idSucursalRetiro, List<LineaCheckout> lineas) {
        // Precedence between the two request shapes: when `lineas` is present and non-empty it wins;
        // when it is absent (null) the single `sku`/`cantidad` pair is used as a one-line order. An
        // explicitly empty list is rejected rather than silently falling back, so a storefront bug
        // can never turn a multi-item cart into a single-item order.
        List<SolicitudLinea> solicitudes = resolverSolicitudes(lineas, skuCodigo, cantidad);

        Customer customer = customerRepository.findByEmail(emailCliente)
            .orElseGet(() -> {
                Customer nuevo = new Customer();
                nuevo.setEmail(emailCliente);
                nuevo.setNombreCompleto(nombreCliente);
                nuevo.setEsInvitado(true);
                return customerRepository.save(nuevo);
            });

        // Resolve every SKU and price up front. An unknown SKU is a 400 before any stock lookup or
        // write, so a malformed cart can never leave a partial order behind.
        List<LineaResuelta> lineasResueltas = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (SolicitudLinea solicitud : solicitudes) {
            CalzadoSku sku = skuRepository.findBySku(solicitud.sku())
                .orElseThrow(() -> new CheckoutBusinessException(HttpStatus.BAD_REQUEST,
                    "El SKU " + solicitud.sku() + " no existe"));
            BigDecimal unitPrice = sku.getPrecioVentaVigente();
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(solicitud.cantidad()));
            lineasResueltas.add(new LineaResuelta(sku, solicitud.cantidad(), unitPrice, lineTotal));
            subtotal = subtotal.add(lineTotal);
        }

        // Validate the delivery mode before touching stock so a malformed pickup request can never
        // reserve inventory. An absent tipoEntrega keeps the historical ENVIO_DOMICILIO behaviour.
        String entrega = (tipoEntrega == null || tipoEntrega.isBlank())
            ? "ENVIO_DOMICILIO"
            : tipoEntrega.trim();
        Sucursal sucursalRetiro = null;
        if ("RETIRO_TIENDA_CLICK_COLLECT".equals(entrega)) {
            if (idSucursalRetiro == null) {
                throw new CheckoutBusinessException(HttpStatus.BAD_REQUEST,
                    "El parametro idSucursalRetiro es obligatorio para retiro en tienda");
            }
            sucursalRetiro = sucursalRepository.findById(idSucursalRetiro)
                .filter(Sucursal::getActive)
                .orElseThrow(() -> new CheckoutBusinessException(HttpStatus.BAD_REQUEST,
                    "La sucursal de retiro no existe o no esta activa"));
        } else if (!"ENVIO_DOMICILIO".equals(entrega)) {
            throw new CheckoutBusinessException(HttpStatus.BAD_REQUEST,
                "El tipo de entrega no es valido");
        }

        // The whole order ships from one warehouse: `orden.id_bodega_despacho` is a single NOT NULL
        // column and `orden_linea` has no warehouse column, so the schema cannot represent a
        // per-line dispatch warehouse today. Pick the lowest-id web-dispatchable warehouse that has
        // enough available stock for every line. Candidates come back ordered by ascending bodega
        // id, so the choice is deterministic instead of dependent on query plan luck. When no single
        // warehouse can serve all lines this is a 400 that names the SKUs, never a partial order.
        Bodega bodega = resolverBodegaUnica(lineasResueltas);

        // Pessimistic database lock, taken for every line before anything is persisted
        for (LineaResuelta linea : lineasResueltas) {
            inventarioService.bloquearStockPesimista(bodega.getId(), linea.sku().getId(), linea.cantidad());
        }

        OrdenEstado estadoPendiente = ordenEstadoRepository.findByCode("PENDIENTE_PAGO")
            .orElseThrow(() -> new IllegalStateException("Estado PENDIENTE_PAGO no existe"));

        BigDecimal flete;
        if ("RETIRO_TIENDA_CLICK_COLLECT".equals(entrega)) {
            // RN-POS-04: store pickup bonifies shipping to zero and never consults the tariff.
            flete = BigDecimal.ZERO;
        } else {
            // Same resolverZona path as /cotizar, so the cost charged here always matches the quote
            // the shopper saw. When codigoPostal is absent or blank, resolverZona falls back to the
            // base zone (the active row with the lowest distancia_km_max), preserving today's 4.50 / 70.
            ZonaResuelta zona = resolverZona(codigoPostal);
            boolean envioGratis = subtotal.compareTo(zona.umbralEnvioGratis()) >= 0;
            flete = envioGratis ? BigDecimal.ZERO : zona.costoFleteBase();
        }

        CuponDescuento cupon = null;
        BigDecimal descuento = BigDecimal.ZERO;
        if (codigoCupon != null && !codigoCupon.isBlank()) {
            CuponValidado cuponValidado = evaluarCupon(codigoCupon.trim(), subtotal);
            if (!cuponValidado.valido()) {
                throw new CheckoutBusinessException(HttpStatus.BAD_REQUEST, cuponValidado.mensaje());
            }
            cupon = cuponValidado.cupon();
            descuento = cuponValidado.descuento();
        }

        BigDecimal comision = subtotal.multiply(BigDecimal.valueOf(0.035)).add(BigDecimal.valueOf(0.30)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.add(flete).add(comision).subtract(descuento);
        if (total.compareTo(BigDecimal.ZERO) < 0) {
            total = BigDecimal.ZERO;
        }

        Orden orden = new Orden();
        orden.setNumeroOrden("ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        orden.setCustomer(customer);
        orden.setCanalVenta("ONLINE_ECOMMERCE");
        orden.setTipoEntrega(entrega);
        orden.setSucursalRetiro(sucursalRetiro);
        orden.setBodegaDespacho(bodega);
        orden.setOrdenEstado(estadoPendiente);
        orden.setSubtotalProductos(subtotal);
        orden.setCostoEnvio(flete);
        orden.setComisionPasarela(comision);
        orden.setDescuentoAplicado(descuento);
        orden.setCupon(cupon);
        orden.setTotalAPagar(total);
        orden.setFechaExpiracionTtl2h(OffsetDateTime.now().plusHours(2));

        for (LineaResuelta linea : lineasResueltas) {
            OrdenLinea ordenLinea = new OrdenLinea();
            ordenLinea.setOrden(orden);
            ordenLinea.setCalzadoSku(linea.sku());
            ordenLinea.setCantidad(linea.cantidad());
            ordenLinea.setUnitPrice(linea.unitPrice());
            ordenLinea.setLineTotal(linea.lineTotal());
            // The discount stays at the order level for now, so descuento_unitario keeps its zero
            // default and line_total is exactly unit price times quantity.
            orden.getLineas().add(ordenLinea);
        }

        Orden guardada = ordenRepository.save(orden);

        // Temporary reservation record, one per line, all in the same warehouse
        for (LineaResuelta linea : lineasResueltas) {
            ReservaCheckout reserva = new ReservaCheckout();
            reserva.setOrden(guardada);
            reserva.setCalzadoSku(linea.sku());
            reserva.setBodega(bodega);
            reserva.setCantidad(linea.cantidad());
            reserva.setFechaExpiracion(guardada.getFechaExpiracionTtl2h());
            reserva.setEstado("ACTIVA");
            reservaRepository.save(reserva);
        }

        return guardada;
    }

    /**
     * Normalises the two request shapes into a single list of lines. Precedence: a present and
     * non-empty list wins; an absent list falls back to the single sku/cantidad pair. An empty list
     * is rejected so a cart bug never silently drops items. Every line is validated before any
     * lookup or write, so a malformed request cannot create a partial order.
     */
    private static List<SolicitudLinea> resolverSolicitudes(List<LineaCheckout> lineas, String skuCodigo, int cantidad) {
        if (lineas == null) {
            // Absent list: the historical single-SKU form, unchanged.
            return List.of(new SolicitudLinea(skuCodigo, cantidad));
        }
        if (lineas.isEmpty()) {
            throw new CheckoutBusinessException(HttpStatus.BAD_REQUEST,
                "La lista de lineas no puede estar vacia");
        }
        List<SolicitudLinea> solicitudes = new ArrayList<>();
        // Repeated SKUs are rejected instead of merged: merging would silently rewrite what the
        // shopper submitted and complicate the one-reservation-per-line rule, so the storefront must
        // send each SKU once.
        Set<String> vistos = new HashSet<>();
        for (LineaCheckout linea : lineas) {
            if (linea == null || linea.sku() == null || linea.sku().isBlank()) {
                throw new CheckoutBusinessException(HttpStatus.BAD_REQUEST,
                    "El SKU de cada linea es obligatorio");
            }
            if (linea.cantidad() == null || linea.cantidad() <= 0) {
                throw new CheckoutBusinessException(HttpStatus.BAD_REQUEST,
                    "La cantidad de cada linea debe ser mayor a cero");
            }
            String codigo = linea.sku().trim();
            if (!vistos.add(codigo)) {
                throw new CheckoutBusinessException(HttpStatus.BAD_REQUEST,
                    "El SKU " + codigo + " esta repetido en la orden");
            }
            solicitudes.add(new SolicitudLinea(codigo, linea.cantidad()));
        }
        return solicitudes;
    }

    /**
     * Finds the single web-dispatchable warehouse that can serve every line, choosing the lowest
     * bodega id. Each line's candidates are already ordered by ascending bodega id and only include
     * active warehouses flagged for web dispatch with enough available stock, so intersecting them
     * yields a deterministic choice. With no common warehouse, checkout fails with a 400 naming the
     * SKUs that cannot be served together.
     */
    private Bodega resolverBodegaUnica(List<LineaResuelta> lineas) {
        List<List<InventarioStock>> candidatosPorLinea = new ArrayList<>();
        for (LineaResuelta linea : lineas) {
            candidatosPorLinea.add(
                inventarioStockRepository.findStockDespachableWebPorSku(linea.sku().getId(), linea.cantidad()));
        }
        List<Set<Integer>> idsPorLinea = candidatosPorLinea.stream()
            .<Set<Integer>>map(candidatos -> candidatos.stream()
                .map(stock -> stock.getBodega().getId())
                .collect(Collectors.toCollection(LinkedHashSet::new)))
            .toList();
        for (InventarioStock stock : candidatosPorLinea.get(0)) {
            Integer idBodega = stock.getBodega().getId();
            if (idsPorLinea.stream().allMatch(ids -> ids.contains(idBodega))) {
                return stock.getBodega();
            }
        }
        throw new CheckoutBusinessException(HttpStatus.BAD_REQUEST, mensajeSinBodegaComun(lineas));
    }

    private static String mensajeSinBodegaComun(List<LineaResuelta> lineas) {
        // Preserve the historical single-SKU message exactly; only multi-line orders name the items.
        if (lineas.size() == 1) {
            return "No hay stock disponible para el SKU solicitado";
        }
        String skus = lineas.stream().map(linea -> linea.sku().getSku()).collect(Collectors.joining(", "));
        return "No hay una bodega con stock suficiente para servir juntos los SKU: " + skus;
    }

    private record SolicitudLinea(String sku, int cantidad) {}

    private record LineaResuelta(CalzadoSku sku, int cantidad, BigDecimal unitPrice, BigDecimal lineTotal) {}

    @Transactional
    public Factura procesarPagoSimuladoYGenerarOtp(String numeroOrden, String metodoPagoCode) {
        Orden orden = ordenRepository.findByNumeroOrden(numeroOrden)
            .orElseThrow(() -> new IllegalArgumentException("Orden " + numeroOrden + " no encontrada"));

        if (!"PENDIENTE_PAGO".equals(orden.getOrdenEstado().getCode())) {
            throw new IllegalStateException("La orden no se encuentra en estado PENDIENTE_PAGO");
        }

        OrdenEstado estadoPagada = ordenEstadoRepository.findByCode("PAGADA")
            .orElseThrow(() -> new IllegalStateException("Estado PAGADA no configurado"));
        orden.setOrdenEstado(estadoPagada);
        ordenRepository.save(orden);

        // Layer 1 of the double-sale fix: a paid order's reservations stop being ACTIVA and become
        // CONCRETADA. The scheduled sweep only ever looks at ACTIVA reservations, so without this
        // the stock would be added back to cantidad_disponible two hours later and the same
        // physical unit could be sold twice. CONCRETADA is the terminal state that
        // reserva_checkout.estado already allows for a reservation that became a sale.
        for (ReservaCheckout reserva : reservaRepository.findByOrdenId(orden.getId())) {
            reserva.setEstado("CONCRETADA");
            reservaRepository.save(reserva);
        }

        // Usage is consumed only when the order is actually paid, so an abandoned unpaid order
        // does not burn a use. An order created while the last use was still available keeps its
        // discount even if the coupon is exhausted before payment.
        if (orden.getCupon() != null) {
            CuponDescuento cupon = orden.getCupon();
            cupon.setUsosActuales(cupon.getUsosActuales() + 1);
            cuponDescuentoRepository.save(cupon);
        }

        SystemUser admin = userRepository.findByUsername("admin")
            .orElseThrow(() -> new IllegalStateException("Usuario admin no existe"));

        // Formalizar salida en Kardex para cada línea. Consolidation clears the reservation taken
        // at checkout, so it is only valid because crearOrdenConReserva2Horas reserved first.
        for (OrdenLinea linea : orden.getLineas()) {
            inventarioService.consolidarVentaKardex(
                orden.getBodegaDespacho().getId(),
                linea.getCalzadoSku(),
                linea.getCantidad(),
                (long) orden.getId(),
                admin,
                "SALIDA_VENTA_WEB"
            );
        }

        // Generar Factura Simulada
        FacturaTipo tipoFactura = facturaTipoRepository.findByCode("FACTURA")
            .orElseThrow(() -> new IllegalStateException("Tipo FACTURA no existe"));
        FacturaEstado estadoEmitida = facturaEstadoRepository.findByCode("EMITIDA")
            .orElseThrow(() -> new IllegalStateException("Estado EMITIDA no existe"));

        // RN-FIN-01: the document total is what the customer actually paid, split with the single
        // gross convention. invoice_total is the gross, so subtotal + iva == total by construction.
        ConvencionIva.Desglose desglose = ConvencionIva.desglosar(orden.getTotalAPagar());

        Factura factura = new Factura();
        factura.setOrden(orden);
        factura.setCustomer(orden.getCustomer());
        factura.setFacturaTipo(tipoFactura);
        factura.setFacturaEstado(estadoEmitida);
        factura.setNumeroCorrelativo(numeracionFacturaService.siguiente(tipoFactura));
        factura.setNitCliente(orden.getCustomer().getNumeroDocumento() != null ? orden.getCustomer().getNumeroDocumento() : "CF");
        factura.setRazonSocial(orden.getCustomer().getNombreCompleto());
        factura.setMontoSubtotal(desglose.subtotal());
        factura.setMontoIva(desglose.iva());
        factura.setMontoRecargoPasarela(orden.getComisionPasarela());
        factura.setInvoiceTotal(desglose.total());
        factura.setEsSimulado(true);
        // RN-FIN-01: the document itemises the sale, so the order lines become factura_linea rows.
        for (OrdenLinea linea : orden.getLineas()) {
            FacturaLinea facturaLinea = new FacturaLinea();
            facturaLinea.setFactura(factura);
            facturaLinea.setCalzadoSku(linea.getCalzadoSku());
            facturaLinea.setCantidad(linea.getCantidad());
            facturaLinea.setUnitPrice(linea.getUnitPrice());
            facturaLinea.setLineTotal(linea.getLineTotal());
            factura.getLineas().add(facturaLinea);
        }
        Factura facturaGuardada = facturaRepository.save(factura);

        // RN-FIN-02: the web sale records its payment transaction so the gross income, the surcharge
        // charged to the customer (factura.monto_recargo_pasarela) and the commission retained by the
        // gateway can be reconciled. The gross is the invoice total; the net is gross - commission.
        PagoMetodo metodoPago = pagoMetodoRepository.findByCode(metodoPagoCode)
            .orElseThrow(() -> new IllegalStateException("Metodo de pago no existe: " + metodoPagoCode));
        BigDecimal comisionRetenida = ComisionPasarela.calcular(metodoPago, facturaGuardada.getInvoiceTotal());
        PagoTransaccion pago = new PagoTransaccion();
        pago.setFactura(facturaGuardada);
        pago.setPagoMetodo(metodoPago);
        pago.setEstadoPago("APROBADO");
        pago.setMontoBruto(facturaGuardada.getInvoiceTotal());
        pago.setComisionRetenida(comisionRetenida);
        pago.setMontoNetoBancario(facturaGuardada.getInvoiceTotal()
            .subtract(comisionRetenida).setScale(2, RoundingMode.HALF_UP));
        pago.setReferenciaTransaccion("SIM-TX-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        pagoTransaccionRepository.save(pago);

        // RN-FIN-01: generate the real PDF and keep the url the writer returned (never rebuild it
        // by hand and never lowercase it, which is the defect this replaces).
        String pdfUrl = facturaVentaPdfService.emitir(facturaGuardada);
        facturaGuardada.setPdfUrl(pdfUrl);

        // Generar Despacho con Código OTP de 6 dígitos
        SecureRandom random = new SecureRandom();
        int otpNum = 100000 + random.nextInt(900000);
        String otp = String.valueOf(otpNum);

        DespachoEstado estadoPreparado = despachoEstadoRepository.findByCode("PREPARADO")
            .orElseThrow(() -> new IllegalStateException("Estado PREPARADO no configurado"));

        DespachoEnvio despacho = new DespachoEnvio();
        despacho.setOrden(orden);
        despacho.setTipoTransporte("FLOTA_PROPIA_LOCAL");
        despacho.setCourierNombre("Furgoneta Reparto Local 01");
        despacho.setNumeroGuiaTracking("TRK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        despacho.setCodigoSecretoOtp(otp);
        despacho.setDespachoEstado(estadoPreparado);
        despacho.setSlaLimitePreparacion24h(OffsetDateTime.now().plusHours(24));
        despacho.setSlaLimiteEntrega5d(OffsetDateTime.now().plusDays(5));
        despachoRepository.save(despacho);

        return facturaGuardada;
    }

    /**
     * Captures a demand alert for a sold-out SKU.
     *
     * <p>The SKU is resolved by its code, the identifier the storefront shows a shopper, so the
     * public endpoint never receives an internal id. A repeated capture of the same
     * (sku, email) pair is a no-op: the endpoint is idempotent.
     */
    @Transactional
    public void registrarAlertaDemanda(String skuCodigo, String emailCliente) {
        String codigo = skuCodigo == null ? "" : skuCodigo.trim();
        if (codigo.isEmpty()) {
            throw new CheckoutBusinessException(HttpStatus.BAD_REQUEST, "El parametro sku es obligatorio");
        }
        String correo = emailCliente == null ? "" : emailCliente.trim();
        if (correo.isEmpty()) {
            throw new CheckoutBusinessException(HttpStatus.BAD_REQUEST, "El parametro emailCliente es obligatorio");
        }
        if (correo.length() > EMAIL_CLIENTE_MAX_LENGTH || !EMAIL_CLIENTE.matcher(correo).matches()) {
            throw new CheckoutBusinessException(HttpStatus.BAD_REQUEST,
                "El correo del cliente no tiene un formato valido");
        }

        CalzadoSku sku = skuRepository.findBySku(codigo)
            .orElseThrow(() -> new CheckoutBusinessException(HttpStatus.BAD_REQUEST,
                "El SKU indicado no existe"));

        // Dedup on the pair (sku, email): the table has no unique constraint on it, so this
        // existence check is the idempotency guard. A second capture returns without inserting.
        if (alertaDemandaRepository.existsByCalzadoSkuIdAndEmailCliente(sku.getId(), correo)) {
            return;
        }

        AlertaDemanda alerta = new AlertaDemanda();
        alerta.setCalzadoSku(sku);
        alerta.setEmailCliente(correo);
        alerta.setNotificado(false);
        alertaDemandaRepository.save(alerta);
    }

    /**
     * Returns the public view of one order when the order number and the customer email both match
     * the same row.
     *
     * <p>Privacy decision: this public endpoint deliberately requires BOTH parameters and returns
     * the order only on an exact pair match. A bare lookup by email would let anyone who knows or
     * guesses an email read that customer's order history. The "wrong email for a valid order" and
     * the "order number does not exist" cases return the exact same 404, on purpose, so the endpoint
     * cannot be used to enumerate valid order numbers.
     */
    @Transactional(readOnly = true)
    public OrdenConsulta consultarOrden(String email, String numeroOrden) {
        String correo = email == null ? "" : email.trim();
        String numero = numeroOrden == null ? "" : numeroOrden.trim();
        if (correo.isEmpty() || numero.isEmpty()) {
            throw new CheckoutBusinessException(HttpStatus.BAD_REQUEST, PARAMETROS_CONSULTA_ORDEN_REQUERIDOS);
        }

        Orden orden = ordenRepository.findByNumeroOrden(numero)
            .filter(encontrada -> encontrada.getCustomer() != null
                && encontrada.getCustomer().getEmail() != null
                && encontrada.getCustomer().getEmail().equalsIgnoreCase(correo))
            .orElseThrow(() -> new CheckoutBusinessException(HttpStatus.NOT_FOUND,
                "No se encontro una orden con el numero y correo indicados"));

        return new OrdenConsulta(
            orden.getNumeroOrden(),
            orden.getOrdenEstado().getCode(),
            orden.getOrdenEstado().getDescription(),
            orden.getTipoEntrega(),
            orden.getSubtotalProductos(),
            orden.getCostoEnvio(),
            orden.getDescuentoAplicado(),
            orden.getTotalAPagar(),
            orden.getCreatedAt().toString(),
            orden.getLineas().stream()
                .map(linea -> new OrdenConsultaLinea(
                    linea.getCalzadoSku().getSku(),
                    linea.getCantidad(),
                    linea.getUnitPrice(),
                    linea.getLineTotal()))
                .toList()
        );
    }

    @Scheduled(fixedRate = 60000) // Se ejecuta cada 60 segundos
    @Transactional
    public void liberarReservasExpiradas() {
        OffsetDateTime ahora = OffsetDateTime.now();
        // The query already restricts to reservations whose order is still PENDIENTE_PAGO, so the
        // sweep never even loads a paid order's reservation. The in-loop guard below is layer 2 of
        // the double-sale fix: if that predicate ever regresses, releasing a paid order's stock
        // would put an already-sold unit back on sale, so the sweep re-verifies before touching
        // inventory instead of trusting the writer that concreted the reservation.
        List<ReservaCheckout> expiradas = reservaRepository.findReservasActivasExpiradasDeOrdenPendiente(ahora);

        for (ReservaCheckout reserva : expiradas) {
            Orden orden = reserva.getOrden();
            if (orden == null || orden.getOrdenEstado() == null
                || !"PENDIENTE_PAGO".equals(orden.getOrdenEstado().getCode())) {
                continue;
            }

            inventarioService.revertirReservaStock(
                reserva.getBodega().getId(),
                reserva.getCalzadoSku().getId(),
                reserva.getCantidad()
            );
            reserva.setEstado("EXPIRADA_LIBERADA");
            reservaRepository.save(reserva);

            OrdenEstado estadoCancelada = ordenEstadoRepository.findByCode("EXPIRADA_CANCELADA").orElse(null);
            if (estadoCancelada != null) {
                orden.setOrdenEstado(estadoCancelada);
                ordenRepository.save(orden);
            }
        }
    }
}
