package com.empresa.calzado.erp.pos;

import com.empresa.calzado.erp.catalogo.CalzadoSku;
import com.empresa.calzado.erp.catalogo.CalzadoSkuRepository;
import com.empresa.calzado.erp.facturacion.*;
import com.empresa.calzado.erp.inventario.*;
import com.empresa.calzado.erp.logistica.DespachoEnvio;
import com.empresa.calzado.erp.seguridad.SystemUser;
import com.empresa.calzado.erp.ventas.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@RestController
@RequestMapping("/api/pos")
@CrossOrigin(origins = {"http://localhost", "http://localhost:80", "http://localhost:8080", "http://127.0.0.1"})
public class PosController {

    private static final String BODEGA_TIPO_ACTUAL = "ACTUAL";
    private static final String BODEGA_TIPO_LIQUIDACION = "LIQUIDACION";
    private static final String ESTADO_APTO_PRODUCTO_ACTUAL = "APTO_PRODUCTO_ACTUAL";
    private static final String ESTADO_DANADO_OUTLET = "DANADO_OUTLET";
    private static final int DIAS_LIMITE_DEVOLUCION = 15;
    /** The account every POS sale resolves; it is the anonymous counter customer, never credited. */
    private static final String CONSUMIDOR_FINAL_EMAIL = "cf@calzadoempresa.com";

    private final CalzadoSkuRepository skuRepository;
    private final BodegaRepository bodegaRepository;
    private final CustomerRepository customerRepository;
    private final OrdenRepository ordenRepository;
    private final OrdenEstadoRepository ordenEstadoRepository;
    private final FacturaRepository facturaRepository;
    private final FacturaTipoRepository facturaTipoRepository;
    private final FacturaEstadoRepository facturaEstadoRepository;
    private final PagoMetodoRepository pagoMetodoRepository;
    private final InventarioService inventarioService;
    private final DevolucionRepository devolucionRepository;
    private final PagoTransaccionRepository pagoTransaccionRepository;
    private final PosSesionService posSesionService;
    private final TicketPosService ticketPosService;
    private final DespachoEnvioPorOrdenRepository despachoEnvioRepository;
    private final NotaCreditoService notaCreditoService;
    private final NumeracionFacturaService numeracionFacturaService;

    public PosController(
        CalzadoSkuRepository skuRepository,
        BodegaRepository bodegaRepository,
        CustomerRepository customerRepository,
        OrdenRepository ordenRepository,
        OrdenEstadoRepository ordenEstadoRepository,
        FacturaRepository facturaRepository,
        FacturaTipoRepository facturaTipoRepository,
        FacturaEstadoRepository facturaEstadoRepository,
        PagoMetodoRepository pagoMetodoRepository,
        InventarioService inventarioService,
        DevolucionRepository devolucionRepository,
        PagoTransaccionRepository pagoTransaccionRepository,
        PosSesionService posSesionService,
        TicketPosService ticketPosService,
        DespachoEnvioPorOrdenRepository despachoEnvioRepository,
        NotaCreditoService notaCreditoService,
        NumeracionFacturaService numeracionFacturaService
    ) {
        this.skuRepository = skuRepository;
        this.bodegaRepository = bodegaRepository;
        this.customerRepository = customerRepository;
        this.ordenRepository = ordenRepository;
        this.ordenEstadoRepository = ordenEstadoRepository;
        this.facturaRepository = facturaRepository;
        this.facturaTipoRepository = facturaTipoRepository;
        this.facturaEstadoRepository = facturaEstadoRepository;
        this.pagoMetodoRepository = pagoMetodoRepository;
        this.inventarioService = inventarioService;
        this.devolucionRepository = devolucionRepository;
        this.pagoTransaccionRepository = pagoTransaccionRepository;
        this.posSesionService = posSesionService;
        this.ticketPosService = ticketPosService;
        this.despachoEnvioRepository = despachoEnvioRepository;
        this.notaCreditoService = notaCreditoService;
        this.numeracionFacturaService = numeracionFacturaService;
    }

    @PostMapping("/cobrar")
    @Transactional
    public ResponseEntity<Map<String, Object>> cobrarVentaPos(
            @RequestBody VentaPosRequest req, Authentication auth) {
        // BREAKING CONTRACT CHANGE: a counter sale now requires an open session for the authenticated
        // cashier. Selling on a closed register was exactly the defect the fake frontend hid; without
        // a session there is no branch warehouse and no way to attribute the sale to a turn.
        SesionCaja sesion = posSesionService.sesionAbiertaObligatoria(auth.getName());
        SystemUser cajero = sesion.getCajero();
        // The warehouse of the sale is the branch warehouse of the session's register, not a global
        // "first web-dispatchable" guess. A branch can only sell from its own stock.
        Bodega bodega = sesion.getCajaPos().getSucursal().getBodega();

        // Payment method is required and resolved before any stock movement, so an unknown method
        // rejects the sale without reserving inventory or writing anything.
        PagoMetodo metodoPago = resolverMetodoPago(req.metodoPago());

        CalzadoSku sku = skuRepository.findBySku(req.sku())
            .orElseThrow(() -> new CheckoutBusinessException(HttpStatus.BAD_REQUEST,
                "SKU no encontrado: " + req.sku()));

        if (req.cantidad() <= 0) {
            throw new CheckoutBusinessException(HttpStatus.BAD_REQUEST, "La cantidad debe ser mayor a cero");
        }

        // Same reserve-then-consolidate sequence the web flow uses, inside this transaction. The
        // pessimistic lock taken here is the availability check, so a POS sale can never sell more
        // than cantidad_disponible and a rejection leaves no order, line, kardex or invoice behind.
        inventarioService.bloquearStockPesimista(bodega.getId(), sku.getId(), req.cantidad());

        Customer customer = customerRepository.findByEmail(CONSUMIDOR_FINAL_EMAIL)
            .orElseGet(() -> {
                Customer cf = new Customer();
                cf.setNombreCompleto("Consumidor Final");
                cf.setEmail(CONSUMIDOR_FINAL_EMAIL);
                cf.setTipoDocumento("CF");
                return customerRepository.save(cf);
            });

        BigDecimal total = sku.getPrecioVentaVigente().multiply(BigDecimal.valueOf(req.cantidad()));

        OrdenEstado pagada = ordenEstadoRepository.findByCode("PAGADA").orElseThrow();
        Orden orden = new Orden();
        orden.setNumeroOrden("POS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        orden.setCustomer(customer);
        orden.setCanalVenta(PosSesionService.CANAL_VENTA_POS);
        orden.setTipoEntrega("RETIRO_TIENDA_CLICK_COLLECT");
        orden.setUserCajero(cajero);
        orden.setBodegaDespacho(bodega);
        orden.setOrdenEstado(pagada);
        orden.setSubtotalProductos(total);
        orden.setTotalAPagar(total);
        orden.setFechaExpiracionTtl2h(OffsetDateTime.now().plusHours(2));

        OrdenLinea linea = new OrdenLinea();
        linea.setOrden(orden);
        linea.setCalzadoSku(sku);
        linea.setCantidad(req.cantidad());
        linea.setUnitPrice(sku.getPrecioVentaVigente());
        linea.setLineTotal(total);
        orden.getLineas().add(linea);

        Orden ordenGuardada = ordenRepository.save(orden);

        // Consolidate the reservation taken above and register the kardex exit. Consolidation only
        // clears cantidad_reservada; cantidad_disponible was already reduced by the reserve step.
        inventarioService.consolidarVentaKardex(bodega.getId(), sku, req.cantidad(), (long) ordenGuardada.getId(), cajero, "SALIDA_VENTA_POS");

        // Generar Factura / Boleta POS
        FacturaTipo tipoBoleta = facturaTipoRepository.findByCode("BOLETA")
            .orElseGet(() -> facturaTipoRepository.findByCode("FACTURA").orElseThrow());
        FacturaEstado estadoEmitida = facturaEstadoRepository.findByCode("EMITIDA").orElseThrow();

        // RN-FIN-01: the boleta shares the single gross convention, so its subtotal and tax are
        // extracted from the amount charged and invoice_total is that gross. It also itemises the
        // sale by writing factura_linea.
        ConvencionIva.Desglose desglose = ConvencionIva.desglosar(total);

        Factura factura = new Factura();
        factura.setOrden(ordenGuardada);
        factura.setCustomer(customer);
        factura.setFacturaTipo(tipoBoleta);
        factura.setFacturaEstado(estadoEmitida);
        factura.setNumeroCorrelativo(numeracionFacturaService.siguiente(tipoBoleta));
        factura.setNitCliente(req.nit() != null ? req.nit() : "CF");
        factura.setRazonSocial(req.razonSocial() != null ? req.razonSocial() : "Consumidor Final");
        factura.setMontoSubtotal(desglose.subtotal());
        factura.setMontoIva(desglose.iva());
        factura.setInvoiceTotal(desglose.total());
        factura.setEsSimulado(true);
        for (OrdenLinea lineaVendida : ordenGuardada.getLineas()) {
            FacturaLinea facturaLinea = new FacturaLinea();
            facturaLinea.setFactura(factura);
            facturaLinea.setCalzadoSku(lineaVendida.getCalzadoSku());
            facturaLinea.setCantidad(lineaVendida.getCantidad());
            facturaLinea.setUnitPrice(lineaVendida.getUnitPrice());
            facturaLinea.setLineTotal(lineaVendida.getLineTotal());
            factura.getLineas().add(facturaLinea);
        }
        Factura facturaGuardada = facturaRepository.save(factura);

        // Payment record for the invoice. Cash methods carry 0.00 commission, so their net equals the
        // gross; card methods retain their percentage plus fixed fee exactly like the web checkout.
        BigDecimal comision = ComisionPasarela.calcular(metodoPago, total);
        BigDecimal montoNetoBancario = total.subtract(comision).setScale(2, RoundingMode.HALF_UP);

        PagoTransaccion pago = new PagoTransaccion();
        pago.setFactura(facturaGuardada);
        pago.setPagoMetodo(metodoPago);
        pago.setEstadoPago("APROBADO");
        pago.setMontoBruto(total);
        pago.setComisionRetenida(comision);
        pago.setMontoNetoBancario(montoNetoBancario);
        pago.setReferenciaTransaccion("SIM-TX-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        pagoTransaccionRepository.save(pago);

        // The ticket is rendered from the persisted sale and written to storage/facturas. The real
        // url is returned additively; every legacy field below stays exactly as it was.
        String pdfUrl = ticketPosService.emitir(facturaGuardada, pago, sesion);
        facturaGuardada.setPdfUrl(pdfUrl);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "SUCCESS");
        body.put("ticketNumero", facturaGuardada.getNumeroCorrelativo());
        body.put("sku", sku.getSku());
        body.put("totalCobrado", total);
        // Additive session and payment information; the three legacy fields above are unchanged.
        body.put("sesionId", sesion.getId());
        body.put("cajaId", sesion.getCajaPos().getId());
        body.put("sucursalNombre", sesion.getCajaPos().getSucursal().getNombre());
        body.put("bodegaId", bodega.getId());
        body.put("cajero", cajero.getUsername());
        body.put("metodoPago", metodoPago.getCode());
        body.put("comisionRetenida", comision);
        body.put("montoNetoBancario", montoNetoBancario);
        body.put("pdfUrl", pdfUrl);
        body.put("mensaje", "Venta en tienda física registrada con éxito. Stock descargado en Kardex.");
        return ResponseEntity.ok(body);
    }

    @PostMapping("/devolucion")
    @Transactional
    public ResponseEntity<Map<String, Object>> procesarDevolucion(
            @RequestBody DevolucionRequest req, Authentication auth) {
        // RN-POS-05: the customer may present either the invoice or the order number.
        Factura factura = resolverFacturaDevolucion(req);
        validarCabeceraDevolucion(req);

        // The physical return refunds store balance, so the credited customer must be known before
        // any write. The shared anonymous counter account is never credited: it would pool every
        // refund of the country into one balance.
        Customer clienteAcreditado = resolverClienteAcreditado(factura, req);

        // The return is processed at the authenticated cashier's open branch, and by that cashier.
        // Resolving the session before any write keeps a closed register from creating a document.
        SesionCaja sesion = posSesionService.sesionAbiertaObligatoria(auth.getName());

        // Destination warehouses come from their type codes, never from hardcoded ids and never from
        // the order's dispatch warehouse. A missing type is a broken invariant, so it fails loudly.
        Bodega bodegaActual = resolverBodegaPorTipo(BODEGA_TIPO_ACTUAL);
        Bodega bodegaLiquidacion = resolverBodegaPorTipo(BODEGA_TIPO_LIQUIDACION);

        // Resolving the credit-note catalogue before any write keeps a broken catalogue from
        // leaving a return without its document.
        FacturaTipo tipoNotaCredito = facturaTipoRepository.findByCode("NOTA_CREDITO")
            .orElseThrow(() -> new IllegalStateException(
                "Invariante rota: no existe el tipo de factura NOTA_CREDITO"));
        FacturaEstado estadoNotaCredito = facturaEstadoRepository.findByCode("EMITIDA")
            .orElseThrow(() -> new IllegalStateException(
                "Invariante rota: no existe el estado de factura EMITIDA"));

        // The 15-day window is computed by the system, never supplied by the cashier (RN-SOP-03).
        int diasTranscurridos = calcularDiasTranscurridos(factura);
        boolean fueraDePlazo = diasTranscurridos > DIAS_LIMITE_DEVOLUCION;

        Map<String, OrdenLinea> vendidasPorSku = indexarLineasVendidasDeLaOrden(factura);
        List<Devolucion> devolucionesPrevias = devolucionRepository.findByFacturaOrigenId(factura.getId());
        Map<String, Integer> yaDevueltoPorSku = sumarDevolucionesPrevias(devolucionesPrevias);

        Devolucion dev = new Devolucion();
        // Readable, unique and inside VARCHAR(35): DEV-<correlativo factura>-<secuencia>. The credit
        // note reuses the same suffix under the NC- prefix, so both documents stay traceable and
        // the credit note stays inside the column too.
        String sufijoDevolucion = factura.getNumeroCorrelativo() + "-" + (devolucionesPrevias.size() + 1);
        dev.setNumeroDevolucion("DEV-" + sufijoDevolucion);
        dev.setFacturaOrigen(factura);
        dev.setSucursal(sesion.getCajaPos().getSucursal());
        dev.setCajero(sesion.getCajero());
        dev.setMotivoDevolucion(req.motivo().trim());
        dev.setDiasTranscurridos(diasTranscurridos);
        // suela_intacta and empaque_original are the cashier's physical assertions; the system cannot
        // compute them and must not reject the return on them. RN-POS-05 accepts a damaged item and
        // routes it to the outlet instead.
        dev.setSuelaIntacta(req.suelaIntacta());
        dev.setEmpaqueOriginal(req.empaqueOriginal());

        BigDecimal montoTotalReembolso = BigDecimal.ZERO;
        boolean algunaLineaALiquidacion = false;

        for (LineaDevolucionRequest lineaReq : req.lineas()) {
            OrdenLinea vendida = validarLineaVendidaDeLaOrden(lineaReq, vendidasPorSku, yaDevueltoPorSku);

            // The column is named estado_calzado, but its CHECK only allows the two destination
            // classifications, so it encodes the destination, not the damage alone: a damaged line
            // or one past the window goes to the outlet, everything else stays current.
            boolean aLiquidacion = lineaReq.danado() || fueraDePlazo;
            DevolucionLinea linea = new DevolucionLinea();
            linea.setDevolucion(dev);
            linea.setCalzadoSku(vendida.getCalzadoSku());
            linea.setCantidad(lineaReq.cantidad());
            // The charged unit price from orden_linea, never a current catalogue price. orden_linea
            // also has descuento_unitario, but the coupon discount is applied at the order level
            // (orden.descuento_aplicado), so the per-line discount is zero in practice and unit_price
            // is the price actually charged.
            linea.setUnitPrice(vendida.getUnitPrice());
            linea.setEstadoCalzado(aLiquidacion ? ESTADO_DANADO_OUTLET : ESTADO_APTO_PRODUCTO_ACTUAL);
            dev.getLineas().add(linea);

            montoTotalReembolso = montoTotalReembolso
                .add(vendida.getUnitPrice().multiply(BigDecimal.valueOf(lineaReq.cantidad())));
            algunaLineaALiquidacion = algunaLineaALiquidacion || aLiquidacion;
        }

        // The refund is the sum over what was actually returned, never the invoice total.
        dev.setMontoTotalReembolso(montoTotalReembolso.setScale(2, RoundingMode.HALF_UP));
        // One return, one document, one number, one headline destination: LIQUIDACION when any line
        // goes to the outlet, otherwise ACTUAL. Each line's stock still re-enters its own warehouse.
        Bodega bodegaTitular = algunaLineaALiquidacion ? bodegaLiquidacion : bodegaActual;
        dev.setBodegaDestino(bodegaTitular);

        Devolucion devGuardada = devolucionRepository.save(dev);

        // The credit note is a factura row of type NOTA_CREDITO. Prices are tax-inclusive, so the
        // tax is extracted from the refund and monto_subtotal + monto_iva == invoice_total holds by
        // construction: monto_iva is defined as the remainder of invoice_total.
        BigDecimal reembolso = devGuardada.getMontoTotalReembolso();
        ConvencionIva.Desglose desgloseNota = ConvencionIva.desglosar(reembolso);
        BigDecimal montoSubtotalNota = desgloseNota.subtotal();
        BigDecimal montoIvaNota = desgloseNota.iva();

        Factura notaCredito = new Factura();
        // The credit note names its own beneficiary: the customer whose store balance receives the
        // refund, not necessarily the customer the anonymous sale was rung under. The link to the
        // original sale stays on id_orden (and devolucion.id_invoice_origen).
        notaCredito.setOrden(factura.getOrden());
        notaCredito.setCustomer(clienteAcreditado);
        notaCredito.setFacturaTipo(tipoNotaCredito);
        notaCredito.setFacturaEstado(estadoNotaCredito);
        notaCredito.setNumeroCorrelativo("NC-" + sufijoDevolucion);
        notaCredito.setNitCliente(clienteAcreditado.getNumeroDocumento() != null
            ? clienteAcreditado.getNumeroDocumento() : "CF");
        notaCredito.setRazonSocial(clienteAcreditado.getNombreCompleto());
        notaCredito.setMontoSubtotal(montoSubtotalNota);
        notaCredito.setMontoIva(montoIvaNota);
        // A credit note charges no gateway fee; es_simulado keeps the entity default.
        notaCredito.setMontoRecargoPasarela(BigDecimal.ZERO);
        notaCredito.setInvoiceTotal(reembolso);
        Factura notaCreditoGuardada = facturaRepository.save(notaCredito);

        // Join the return and its document. The return is managed inside this transaction, so the
        // FK is flushed without a second save.
        devGuardada.setNotaCredito(notaCreditoGuardada);

        // A physical return refunds store balance, never cash: the arqueo is untouched and no
        // pago_transaccion row is written.
        BigDecimal saldoActual = clienteAcreditado.getSaldoFavorWallet() != null
            ? clienteAcreditado.getSaldoFavorWallet() : BigDecimal.ZERO;
        clienteAcreditado.setSaldoFavorWallet(
            saldoActual.add(reembolso).setScale(2, RoundingMode.HALF_UP));
        customerRepository.save(clienteAcreditado);

        // The url is the writer's returned value, never rebuilt by hand (and never lowercased).
        String notaCreditoPdfUrl = notaCreditoService.emitir(devGuardada, notaCreditoGuardada);

        List<Map<String, Object>> lineasRespuesta = new ArrayList<>();
        for (DevolucionLinea linea : devGuardada.getLineas()) {
            boolean aLiquidacion = ESTADO_DANADO_OUTLET.equals(linea.getEstadoCalzado());
            Bodega destino = aLiquidacion ? bodegaLiquidacion : bodegaActual;
            inventarioService.reingresarStockDevolucion(
                destino.getId(), linea.getCalzadoSku(), linea.getCantidad(), sesion.getCajero(), devGuardada.getId());

            Map<String, Object> detalle = new LinkedHashMap<>();
            detalle.put("sku", linea.getCalzadoSku().getSku());
            detalle.put("cantidad", linea.getCantidad());
            detalle.put("unitPrice", linea.getUnitPrice());
            detalle.put("estadoCalzado", linea.getEstadoCalzado());
            detalle.put("destinoBodegaId", destino.getId());
            detalle.put("destinoBodega", destino.getNombre());
            detalle.put("destinoBodegaTipo", aLiquidacion ? BODEGA_TIPO_LIQUIDACION : BODEGA_TIPO_ACTUAL);
            lineasRespuesta.add(detalle);
        }

        String mensaje = "Devolucion registrada y stock reingresado segun su clasificacion.";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "DEVOLUCION_APROBADA");
        body.put("numeroDevolucion", devGuardada.getNumeroDevolucion());
        body.put("destinoPrincipal", algunaLineaALiquidacion ? BODEGA_TIPO_LIQUIDACION : BODEGA_TIPO_ACTUAL);
        body.put("enrutadoA", bodegaTitular.getNombre());
        body.put("montoTotalReembolso", devGuardada.getMontoTotalReembolso());
        // Legacy key kept additively for callers that already read montoReembolso.
        body.put("montoReembolso", devGuardada.getMontoTotalReembolso());
        body.put("lineas", lineasRespuesta);
        // Additive credit-note and store-balance information; the legacy keys above are unchanged.
        body.put("notaCreditoCorrelativo", notaCreditoGuardada.getNumeroCorrelativo());
        body.put("notaCreditoPdfUrl", notaCreditoPdfUrl);
        body.put("saldoFavorCliente", clienteAcreditado.getSaldoFavorWallet());
        body.put("mensaje", mensaje);
        body.put("message", mensaje);
        return ResponseEntity.ok(body);
    }

    /**
     * Resolves the invoice a return belongs to. The customer may present the invoice or the order
     * (RN-POS-05); an order resolves through its invoice because a {@code factura} row always carries
     * {@code id_orden}.
     *
     * <p>Both branches resolve a SALE document only. A credit note shares {@code id_orden} with the
     * sale it credits and carries its own correlative number, so accepting one here would let a
     * customer be refunded again past what was sold, and would defeat the accumulated over-return
     * guard by keying it on the credit note instead of the sale.
     */
    private Factura resolverFacturaDevolucion(DevolucionRequest req) {
        boolean tieneFactura = req.numeroFactura() != null && !req.numeroFactura().isBlank();
        boolean tieneOrden = req.numeroOrden() != null && !req.numeroOrden().isBlank();
        if (!tieneFactura && !tieneOrden) {
            throw new CheckoutBusinessException(HttpStatus.BAD_REQUEST,
                "Debe indicar el numero de factura o el numero de orden");
        }
        if (tieneFactura) {
            String numeroFactura = req.numeroFactura().trim();
            return facturaRepository.findVentaByNumeroCorrelativo(numeroFactura)
                .orElseThrow(() -> new CheckoutBusinessException(HttpStatus.BAD_REQUEST,
                    "Factura no encontrada: " + numeroFactura));
        }
        String numeroOrden = req.numeroOrden().trim();
        Orden orden = ordenRepository.findByNumeroOrden(numeroOrden)
            .orElseThrow(() -> new CheckoutBusinessException(HttpStatus.BAD_REQUEST,
                "Orden no encontrada: " + numeroOrden));
        List<Factura> ventas = facturaRepository.findVentasByOrdenId(orden.getId());
        if (ventas.isEmpty()) {
            throw new CheckoutBusinessException(HttpStatus.BAD_REQUEST,
                "Factura no encontrada para la orden: " + numeroOrden);
        }
        // An order with two sale documents is a broken invariant, not a client mistake: it must not
        // be reported as a 400 and must not be resolved by silently taking the first row.
        if (ventas.size() > 1) {
            throw new IllegalStateException("La orden " + numeroOrden
                + " tiene mas de un comprobante de venta; invariante de facturacion roto");
        }
        return ventas.get(0);
    }

    private void validarCabeceraDevolucion(DevolucionRequest req) {
        if (req.lineas() == null || req.lineas().isEmpty()) {
            throw new CheckoutBusinessException(HttpStatus.BAD_REQUEST,
                "La devolucion debe incluir al menos una linea");
        }
        String motivo = req.motivo() == null ? null : req.motivo().trim();
        if (motivo == null || motivo.isEmpty()) {
            throw new CheckoutBusinessException(HttpStatus.BAD_REQUEST,
                "El motivo de la devolucion es obligatorio");
        }
        // motivo_devolucion is VARCHAR(60); letting a longer value through would be a 500 from the DB.
        if (motivo.length() > 60) {
            throw new CheckoutBusinessException(HttpStatus.BAD_REQUEST,
                "El motivo de la devolucion no puede superar 60 caracteres");
        }
        if (req.suelaIntacta() == null) {
            throw new CheckoutBusinessException(HttpStatus.BAD_REQUEST,
                "Debe indicar si la suela esta intacta");
        }
        if (req.empaqueOriginal() == null) {
            throw new CheckoutBusinessException(HttpStatus.BAD_REQUEST,
                "Debe indicar si el empaque es original");
        }
    }

    private Bodega resolverBodegaPorTipo(String bodegaTipoCode) {
        return bodegaRepository.findByBodegaTipoCode(bodegaTipoCode).stream().findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "Invariante rota: no existe una bodega con tipo " + bodegaTipoCode));
    }

    private int calcularDiasTranscurridos(Factura factura) {
        // The window is anchored on the OTP-validated delivery when a shipment exists for the
        // invoice's order. A counter sale has no shipment and no OTP, so it falls back to the
        // invoice's creation instant; that fallback is the documented rule, not a cashier value.
        OffsetDateTime fechaBase = despachoEnvioRepository.findByOrdenId(factura.getOrden().getId())
            .map(DespachoEnvio::getFechaEntregaReal)
            .orElse(null);
        if (fechaBase == null) {
            fechaBase = factura.getCreatedAt() != null ? factura.getCreatedAt() : OffsetDateTime.now();
        }
        long dias = ChronoUnit.DAYS.between(fechaBase, OffsetDateTime.now());
        // dias_transcurridos has CHECK (dias_transcurridos >= 0); clamp a future delivery to zero.
        return (int) Math.max(0, dias);
    }

    /**
     * Indexes the sale's sold quantities by SKU from the order's lines.
     *
     * <p>The order is the authoritative record of what was sold: both sale paths write
     * {@code orden_linea} ({@code CheckoutService} for the web checkout and {@code PosController}
     * for POS), while nothing in the codebase writes {@code factura_linea} today, so the invoice's
     * line table is always empty. RN-POS-05 also lets the customer identify the sale by either the
     * invoice or the order number, so the order is a legitimate source of truth.
     */
    private Map<String, OrdenLinea> indexarLineasVendidasDeLaOrden(Factura factura) {
        Map<String, OrdenLinea> porSku = new HashMap<>();
        List<OrdenLinea> lineas = factura.getOrden() != null ? factura.getOrden().getLineas() : null;
        if (lineas != null) {
            for (OrdenLinea linea : lineas) {
                if (linea.getCalzadoSku() != null && linea.getCalzadoSku().getSku() != null) {
                    porSku.put(linea.getCalzadoSku().getSku(), linea);
                }
            }
        }
        return porSku;
    }

    private Map<String, Integer> sumarDevolucionesPrevias(List<Devolucion> devolucionesPrevias) {
        Map<String, Integer> acumulado = new HashMap<>();
        for (Devolucion previa : devolucionesPrevias) {
            if (previa.getLineas() == null) {
                continue;
            }
            for (DevolucionLinea linea : previa.getLineas()) {
                if (linea.getCalzadoSku() == null || linea.getCalzadoSku().getSku() == null) {
                    continue;
                }
                acumulado.merge(linea.getCalzadoSku().getSku(), linea.getCantidad(), Integer::sum);
            }
        }
        return acumulado;
    }

    /**
     * Validates a requested return line against the order's sold quantity and price.
     */
    private OrdenLinea validarLineaVendidaDeLaOrden(
            LineaDevolucionRequest linea, Map<String, OrdenLinea> vendidasPorSku,
            Map<String, Integer> yaDevueltoPorSku) {
        String sku = linea.sku() == null ? null : linea.sku().trim();
        OrdenLinea vendida = vendidasPorSku.get(sku);
        if (vendida == null) {
            throw new CheckoutBusinessException(HttpStatus.BAD_REQUEST,
                "El SKU no pertenece a la venta: " + sku);
        }
        if (linea.cantidad() <= 0) {
            throw new CheckoutBusinessException(HttpStatus.BAD_REQUEST,
                "La cantidad a devolver debe ser mayor a cero para el SKU: " + sku);
        }
        // No over-return, including across repeated returns of the same invoice and within one
        // request: the running map already holds what previous returns and earlier lines consumed.
        int vendido = vendida.getCantidad();
        int yaDevuelto = yaDevueltoPorSku.getOrDefault(sku, 0);
        if (yaDevuelto + linea.cantidad() > vendido) {
            throw new CheckoutBusinessException(HttpStatus.BAD_REQUEST,
                "La cantidad devuelta supera lo vendido para el SKU: " + sku
                    + " (Vendido=" + vendido + ", YaDevuelto=" + yaDevuelto + ")");
        }
        yaDevueltoPorSku.put(sku, yaDevuelto + linea.cantidad());
        return vendida;
    }

    /**
     * Resolves the customer whose store balance receives the refund. An identified sale credits its
     * own customer; an anonymous counter sale must be identified with an email that resolves to an
     * existing customer, and the whole return is rejected otherwise so nothing is written.
     */
    private Customer resolverClienteAcreditado(Factura factura, DevolucionRequest req) {
        Customer clienteVenta = factura.getCustomer();
        if (!esConsumidorFinalAnonimo(clienteVenta)) {
            return clienteVenta;
        }
        String email = req.emailCliente() == null ? null : req.emailCliente().trim();
        if (email == null || email.isEmpty()) {
            throw new CheckoutBusinessException(HttpStatus.BAD_REQUEST,
                "Debe identificar al cliente con su correo para acreditar el saldo a favor");
        }
        Customer identificado = customerRepository.findByEmail(email)
            .orElseThrow(() -> new CheckoutBusinessException(HttpStatus.BAD_REQUEST,
                "No existe un cliente con el correo indicado; identifique al cliente para acreditar el saldo a favor"));
        // The placeholder resolves to an existing customer, so "unknown email" does not catch it.
        // Crediting it would pool every anonymous refund into one shared wallet, which is the exact
        // harm this resolution exists to prevent.
        if (esConsumidorFinalAnonimo(identificado)) {
            throw new CheckoutBusinessException(HttpStatus.BAD_REQUEST,
                "No se puede acreditar el saldo a favor en la cuenta de consumidor final; identifique al cliente real");
        }
        return identificado;
    }

    /**
     * Single source of truth for the anonymous counter customer. It is keyed on the email because
     * that is the account every POS sale resolves; the seeded row is also CF / CONSUMIDOR-FINAL.
     * Requiring those two fields would miss a POS-created placeholder (its numero_documento is null)
     * and credit the shared account, which is exactly the harm to avoid.
     */
    private static boolean esConsumidorFinalAnonimo(Customer customer) {
        return customer != null && CONSUMIDOR_FINAL_EMAIL.equalsIgnoreCase(customer.getEmail());
    }

    /**
     * Resolves the required payment method. Unknown or missing is a 400 that creates nothing.
     */
    private PagoMetodo resolverMetodoPago(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            throw new CheckoutBusinessException(HttpStatus.BAD_REQUEST, "El metodo de pago es obligatorio");
        }
        return pagoMetodoRepository.findByCode(codigo.trim())
            .orElseThrow(() -> new CheckoutBusinessException(HttpStatus.BAD_REQUEST,
                "El metodo de pago no existe"));
    }

    public record VentaPosRequest(String sku, int cantidad, String nit, String razonSocial, String metodoPago) {}
    public record LineaDevolucionRequest(String sku, int cantidad, boolean danado) {}
    public record DevolucionRequest(String numeroFactura, String numeroOrden, String motivo,
                                    Boolean suelaIntacta, Boolean empaqueOriginal, String emailCliente,
                                    List<LineaDevolucionRequest> lineas) {}

    /**
     * Maps a business rejection (for example insufficient stock) to the shared
     * {@code {"status":"ERROR","message":...}} envelope, the same shape the checkout controller uses.
     */
    @ExceptionHandler(CheckoutBusinessException.class)
    public ResponseEntity<Map<String, Object>> manejarErrorDeNegocio(CheckoutBusinessException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ERROR");
        body.put("message", ex.getMessage());
        return ResponseEntity.status(ex.getStatus()).body(body);
    }
}
