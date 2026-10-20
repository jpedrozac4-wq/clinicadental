package com.empresa.calzado.erp.pos;

import com.empresa.calzado.erp.catalogo.CalzadoSku;
import com.empresa.calzado.erp.catalogo.CalzadoSkuRepository;
import com.empresa.calzado.erp.facturacion.Factura;
import com.empresa.calzado.erp.facturacion.FacturaEstado;
import com.empresa.calzado.erp.facturacion.FacturaEstadoRepository;
import com.empresa.calzado.erp.facturacion.FacturaRepository;
import com.empresa.calzado.erp.facturacion.FacturaTipo;
import com.empresa.calzado.erp.facturacion.FacturaTipoRepository;
import com.empresa.calzado.erp.facturacion.NumeracionFacturaService;
import com.empresa.calzado.erp.facturacion.PagoMetodo;
import com.empresa.calzado.erp.facturacion.PagoMetodoRepository;
import com.empresa.calzado.erp.facturacion.PagoTransaccion;
import com.empresa.calzado.erp.inventario.Bodega;
import com.empresa.calzado.erp.inventario.BodegaRepository;
import com.empresa.calzado.erp.inventario.InventarioService;
import com.empresa.calzado.erp.inventario.InventarioStock;
import com.empresa.calzado.erp.inventario.InventarioStockRepository;
import com.empresa.calzado.erp.inventario.KardexMovimiento;
import com.empresa.calzado.erp.inventario.KardexMovimientoRepository;
import com.empresa.calzado.erp.inventario.MovimientoStockTipo;
import com.empresa.calzado.erp.inventario.MovimientoStockTipoRepository;
import com.empresa.calzado.erp.logistica.DespachoEnvio;
import com.empresa.calzado.erp.seguridad.SystemUser;
import com.empresa.calzado.erp.seguridad.SystemUserRepository;
import com.empresa.calzado.erp.ventas.Customer;
import com.empresa.calzado.erp.ventas.CustomerRepository;
import com.empresa.calzado.erp.ventas.Orden;
import com.empresa.calzado.erp.ventas.OrdenEstado;
import com.empresa.calzado.erp.ventas.OrdenEstadoRepository;
import com.empresa.calzado.erp.ventas.OrdenLinea;
import com.empresa.calzado.erp.ventas.OrdenRepository;
import com.empresa.calzado.erp.pos.TicketPosPdf;
import com.empresa.calzado.erp.pos.TicketPosService;
import com.empresa.calzado.erp.reportes.FacturaPdfStorage;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stock-integrity and session/payment tests for the POS counter sale.
 *
 * <p>{@link InventarioService} is the real service over mocked repositories, so the arithmetic on
 * {@code cantidad_disponible} and {@code cantidad_reservada} is observable, while the HTTP surface
 * is pinned with MockMvc standalone. No H2 and no Testcontainers are used.
 */
@ExtendWith(MockitoExtension.class)
class PosControllerTest {

    @Mock private CalzadoSkuRepository skuRepository;
    @Mock private BodegaRepository bodegaRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private OrdenRepository ordenRepository;
    @Mock private OrdenEstadoRepository ordenEstadoRepository;
    @Mock private FacturaRepository facturaRepository;
    @Mock private FacturaTipoRepository facturaTipoRepository;
    @Mock private FacturaEstadoRepository facturaEstadoRepository;
    @Mock private PagoMetodoRepository pagoMetodoRepository;
    @Mock private InventarioStockRepository stockRepository;
    @Mock private KardexMovimientoRepository kardexRepository;
    @Mock private MovimientoStockTipoRepository movimientoTipoRepository;
    @Mock private SystemUserRepository userRepository;
    @Mock private DevolucionRepository devolucionRepository;
    @Mock private DespachoEnvioPorOrdenRepository despachoEnvioPorOrdenRepository;
    @Mock private CajaPosRepository cajaPosRepository;
    @Mock private SesionCajaRepository sesionCajaRepository;
    @Mock private PagoTransaccionRepository pagoTransaccionRepository;
    @Mock private NumeracionFacturaService numeracionFacturaService;

    private MockMvc mockMvc;
    private InventarioService inventarioService;
    private PosSesionService posSesionService;
    private TicketPosService ticketPosService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        inventarioService =
            new InventarioService(stockRepository, kardexRepository, movimientoTipoRepository, bodegaRepository);
        posSesionService =
            new PosSesionService(cajaPosRepository, sesionCajaRepository, userRepository, ordenRepository);
        ticketPosService =
            new TicketPosService(new TicketPosPdf(), new FacturaPdfStorage(tempDir));
        mockMvc = mockMvcCon(
            new NotaCreditoService(new NotaCreditoPdf(), new FacturaPdfStorage(tempDir)));
    }

    /** Builds the controller with an explicit credit-note service, so a test can spy the writer. */
    private MockMvc mockMvcCon(NotaCreditoService notaCreditoService) {
        PosController controller = new PosController(
            skuRepository,
            bodegaRepository,
            customerRepository,
            ordenRepository,
            ordenEstadoRepository,
            facturaRepository,
            facturaTipoRepository,
            facturaEstadoRepository,
            pagoMetodoRepository,
            inventarioService,
            devolucionRepository,
            pagoTransaccionRepository,
            posSesionService,
            ticketPosService,
            despachoEnvioPorOrdenRepository,
            notaCreditoService,
            numeracionFacturaService);
        return MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void elCobrarDevuelveUnaUrlRealYElArchivoDelTicketExiste() throws Exception {
        InventarioStock stock = stockCon(1, 10, 0);
        stubSesionAbierta(1, "cajero1");
        stubVentaPosComun();
        lenient().when(stockRepository.findStockConBloqueoPesimista(1, 10)).thenReturn(Optional.of(stock));
        lenient().when(stockRepository.findByBodegaIdAndCalzadoSkuId(1, 10)).thenReturn(Optional.of(stock));

        MvcResult resultado = mockMvc.perform(post("/api/pos/cobrar")
                .principal(autenticacion("cajero1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"SKU-POS\",\"cantidad\":1,\"nit\":\"CF\",\"razonSocial\":\"Consumidor Final\",\"metodoPago\":\"EFECTIVO\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ticketNumero").exists())
            .andExpect(jsonPath("$.totalCobrado").exists())
            .andReturn();

        String pdfUrl = JsonPath.read(resultado.getResponse().getContentAsString(), "$.pdfUrl");
        assertThat(pdfUrl).startsWith(FacturaPdfStorage.URL_PREFIX);
        Path archivo = tempDir.resolve(pdfUrl.substring(FacturaPdfStorage.URL_PREFIX.length()));
        assertThat(archivo).exists();
        assertThat(Files.readAllBytes(archivo)).startsWith("%PDF-1.4".getBytes(StandardCharsets.ISO_8859_1));

        ArgumentCaptor<Factura> factura = ArgumentCaptor.forClass(Factura.class);
        verify(facturaRepository).save(factura.capture());
        assertThat(factura.getValue().getPdfUrl()).isEqualTo(pdfUrl);
    }

    @Test
    void unaVentaPosDescuentaExactamenteLaCantidadVendidaDeLaDisponible() throws Exception {
        InventarioStock stock = stockCon(1, 10, 0);
        stubSesionAbierta(1, "cajero1");
        stubVentaPosComun();
        lenient().when(stockRepository.findStockConBloqueoPesimista(1, 10)).thenReturn(Optional.of(stock));
        lenient().when(stockRepository.findByBodegaIdAndCalzadoSkuId(1, 10)).thenReturn(Optional.of(stock));

        assertThat(stock.getCantidadDisponible()).as("disponible antes").isEqualTo(10);
        assertThat(stock.getCantidadReservada()).as("reservada antes").isZero();

        mockMvc.perform(post("/api/pos/cobrar")
                .principal(autenticacion("cajero1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"SKU-POS\",\"cantidad\":3,\"nit\":\"CF\",\"razonSocial\":\"Consumidor Final\",\"metodoPago\":\"EFECTIVO\"}"))
            .andExpect(status().isOk());

        assertThat(stock.getCantidadDisponible()).as("disponible despues").isEqualTo(7);
        assertThat(stock.getCantidadReservada()).as("reservada despues").isZero();
    }

    @Test
    void unaVentaPosMayorAlDisponibleEsErrorDeNegocioYNoCreaNada() throws Exception {
        InventarioStock stock = stockCon(1, 2, 0);
        stubSesionAbierta(1, "cajero1");
        stubVentaPosComun();
        lenient().when(stockRepository.findStockConBloqueoPesimista(1, 10)).thenReturn(Optional.of(stock));
        lenient().when(stockRepository.findByBodegaIdAndCalzadoSkuId(1, 10)).thenReturn(Optional.of(stock));

        mockMvc.perform(post("/api/pos/cobrar")
                .principal(autenticacion("cajero1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"SKU-POS\",\"cantidad\":5,\"nit\":\"CF\",\"razonSocial\":\"Consumidor Final\",\"metodoPago\":\"EFECTIVO\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("ERROR"))
            .andExpect(jsonPath("$.message").value("Stock insuficiente: Disponible=2, Solicitado=5"));

        assertThat(stock.getCantidadDisponible()).as("disponible tras el rechazo").isEqualTo(2);
        assertThat(stock.getCantidadReservada()).as("reservada tras el rechazo").isZero();
        verify(ordenRepository, never()).save(any(Orden.class));
        verify(kardexRepository, never()).save(any(KardexMovimiento.class));
        verify(facturaRepository, never()).save(any(Factura.class));
        verify(pagoTransaccionRepository, never()).save(any(PagoTransaccion.class));
    }

    @Test
    void unaVentaPosDeUnSkuSinStockEsErrorDeNegocioYNoCreaNada() throws Exception {
        stubSesionAbierta(1, "cajero1");
        stubVentaPosComun();
        lenient().when(stockRepository.findStockConBloqueoPesimista(1, 10)).thenReturn(Optional.empty());
        lenient().when(stockRepository.findByBodegaIdAndCalzadoSkuId(1, 10)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/pos/cobrar")
                .principal(autenticacion("cajero1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"SKU-POS\",\"cantidad\":1,\"nit\":\"CF\",\"razonSocial\":\"Consumidor Final\",\"metodoPago\":\"EFECTIVO\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("ERROR"))
            .andExpect(jsonPath("$.message").value("No hay stock disponible para el SKU solicitado"));

        verify(ordenRepository, never()).save(any(Orden.class));
        verify(kardexRepository, never()).save(any(KardexMovimiento.class));
        verify(facturaRepository, never()).save(any(Factura.class));
        verify(pagoTransaccionRepository, never()).save(any(PagoTransaccion.class));
    }

    @Test
    void unaVentaPosSinSesionAbiertaEsRechazadaYNoCreaNada() throws Exception {
        SystemUser cajero = new SystemUser();
        cajero.setId(1);
        cajero.setUsername("cajero1");
        when(userRepository.findByUsername("cajero1")).thenReturn(Optional.of(cajero));
        when(sesionCajaRepository.findFirstByCajeroIdAndEstado(1, "ABIERTA")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/pos/cobrar")
                .principal(autenticacion("cajero1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"SKU-POS\",\"cantidad\":1,\"nit\":\"CF\",\"razonSocial\":\"Consumidor Final\",\"metodoPago\":\"EFECTIVO\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("ERROR"))
            .andExpect(jsonPath("$.message").value("El cajero no tiene una sesion de caja abierta"));

        verify(ordenRepository, never()).save(any(Orden.class));
        verify(facturaRepository, never()).save(any(Factura.class));
        verify(pagoTransaccionRepository, never()).save(any(PagoTransaccion.class));
    }

    @Test
    void unaVentaPosSinMetodoDePagoEsRechazadaYNoCreaNada() throws Exception {
        stubSesionAbierta(1, "cajero1");

        mockMvc.perform(post("/api/pos/cobrar")
                .principal(autenticacion("cajero1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"SKU-POS\",\"cantidad\":1,\"nit\":\"CF\",\"razonSocial\":\"Consumidor Final\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("ERROR"))
            .andExpect(jsonPath("$.message").value("El metodo de pago es obligatorio"));

        verify(ordenRepository, never()).save(any(Orden.class));
        verify(pagoTransaccionRepository, never()).save(any(PagoTransaccion.class));
    }

    @Test
    void unaVentaPosConMetodoDePagoDesconocidoEsRechazadaYNoCreaNada() throws Exception {
        stubSesionAbierta(1, "cajero1");
        when(pagoMetodoRepository.findByCode("NO_EXISTE")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/pos/cobrar")
                .principal(autenticacion("cajero1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"SKU-POS\",\"cantidad\":1,\"nit\":\"CF\",\"razonSocial\":\"Consumidor Final\",\"metodoPago\":\"NO_EXISTE\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("ERROR"))
            .andExpect(jsonPath("$.message").value("El metodo de pago no existe"));

        verify(ordenRepository, never()).save(any(Orden.class));
        verify(pagoTransaccionRepository, never()).save(any(PagoTransaccion.class));
    }

    @Test
    void laVentaPosTomaLaBodegaDeLaSesionYNoLaPrimeraBodegaWeb() throws Exception {
        InventarioStock stock = stockCon(7, 10, 0);
        stubSesionAbierta(7, "cajero1");
        stubVentaPosComun();
        Bodega bodegaWeb = new Bodega();
        bodegaWeb.setId(1);
        lenient().when(bodegaRepository.findFirstByEsDespachableWebTrueAndActiveTrue()).thenReturn(Optional.of(bodegaWeb));
        lenient().when(stockRepository.findStockConBloqueoPesimista(7, 10)).thenReturn(Optional.of(stock));
        lenient().when(stockRepository.findByBodegaIdAndCalzadoSkuId(7, 10)).thenReturn(Optional.of(stock));

        mockMvc.perform(post("/api/pos/cobrar")
                .principal(autenticacion("cajero1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"SKU-POS\",\"cantidad\":1,\"nit\":\"CF\",\"razonSocial\":\"Consumidor Final\",\"metodoPago\":\"EFECTIVO\"}"))
            .andExpect(status().isOk());

        verify(stockRepository).findStockConBloqueoPesimista(7, 10);
        verify(bodegaRepository, never()).findFirstByEsDespachableWebTrueAndActiveTrue();
    }

    @Test
    void laVentaPosUsaElCajeroDeLaSesionYNoUnUsuarioFijo() throws Exception {
        InventarioStock stock = stockCon(1, 10, 0);
        stubSesionAbierta(1, "cajero9");
        stubVentaPosComun();
        lenient().when(stockRepository.findStockConBloqueoPesimista(1, 10)).thenReturn(Optional.of(stock));
        lenient().when(stockRepository.findByBodegaIdAndCalzadoSkuId(1, 10)).thenReturn(Optional.of(stock));

        mockMvc.perform(post("/api/pos/cobrar")
                .principal(autenticacion("cajero9"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"SKU-POS\",\"cantidad\":1,\"nit\":\"CF\",\"razonSocial\":\"Consumidor Final\",\"metodoPago\":\"EFECTIVO\"}"))
            .andExpect(status().isOk());

        ArgumentCaptor<Orden> orden = ArgumentCaptor.forClass(Orden.class);
        verify(ordenRepository).save(orden.capture());
        assertThat(orden.getValue().getUserCajero().getUsername()).isEqualTo("cajero9");
    }

    @Test
    void laVentaPosRegistraElPagoConElMetodoYElMontoCorrectos() throws Exception {
        InventarioStock stock = stockCon(1, 10, 0);
        stubSesionAbierta(1, "cajero1");
        stubVentaPosComun();
        PagoMetodo tarjeta = new PagoMetodo();
        tarjeta.setCode("TARJETA_SIMULADA");
        tarjeta.setComisionPorcentaje(new BigDecimal("3.50"));
        tarjeta.setComisionFija(new BigDecimal("0.30"));
        when(pagoMetodoRepository.findByCode("TARJETA_SIMULADA")).thenReturn(Optional.of(tarjeta));
        lenient().when(stockRepository.findStockConBloqueoPesimista(1, 10)).thenReturn(Optional.of(stock));
        lenient().when(stockRepository.findByBodegaIdAndCalzadoSkuId(1, 10)).thenReturn(Optional.of(stock));

        mockMvc.perform(post("/api/pos/cobrar")
                .principal(autenticacion("cajero1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"SKU-POS\",\"cantidad\":1,\"nit\":\"CF\",\"razonSocial\":\"Consumidor Final\",\"metodoPago\":\"TARJETA_SIMULADA\"}"))
            .andExpect(status().isOk());

        ArgumentCaptor<PagoTransaccion> pago = ArgumentCaptor.forClass(PagoTransaccion.class);
        verify(pagoTransaccionRepository).save(pago.capture());
        assertThat(pago.getValue().getPagoMetodo().getCode()).isEqualTo("TARJETA_SIMULADA");
        assertThat(pago.getValue().getEstadoPago()).isEqualTo("APROBADO");
        assertThat(pago.getValue().getMontoBruto()).isEqualByComparingTo("50.00");
        // Same formula the web checkout uses: total * comision_porcentaje / 100 + comision_fija,
        // rounded HALF_UP to 2 decimals (50.00 * 3.50 / 100 + 0.30 = 2.05).
        assertThat(pago.getValue().getComisionRetenida()).isEqualByComparingTo("2.05");
        assertThat(pago.getValue().getMontoNetoBancario()).isEqualByComparingTo("47.95");
        assertThat(pago.getValue().getReferenciaTransaccion()).startsWith("SIM-TX-");
    }

    @Test
    void laBoletaPosUsaElCorrelativoSecuencialYLaConvencionDeIvaCompartida() throws Exception {
        InventarioStock stock = stockCon(1, 10, 0);
        stubSesionAbierta(1, "cajero1");
        stubVentaPosComun();
        when(numeracionFacturaService.siguiente(any(FacturaTipo.class))).thenReturn("TKT-000042");
        lenient().when(stockRepository.findStockConBloqueoPesimista(1, 10)).thenReturn(Optional.of(stock));
        lenient().when(stockRepository.findByBodegaIdAndCalzadoSkuId(1, 10)).thenReturn(Optional.of(stock));

        mockMvc.perform(post("/api/pos/cobrar")
                .principal(autenticacion("cajero1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"SKU-POS\",\"cantidad\":1,\"nit\":\"CF\",\"razonSocial\":\"Consumidor Final\",\"metodoPago\":\"EFECTIVO\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ticketNumero").value("TKT-000042"));

        ArgumentCaptor<Factura> captor = ArgumentCaptor.forClass(Factura.class);
        verify(facturaRepository).save(captor.capture());
        Factura boleta = captor.getValue();
        assertThat(boleta.getNumeroCorrelativo()).isEqualTo("TKT-000042");
        assertThat(boleta.getInvoiceTotal()).isEqualByComparingTo("50.00");
        // 50.00 / 1.12 = 44.64 (HALF_UP) and the tax is the 5.36 remainder, so the identity holds.
        assertThat(boleta.getMontoSubtotal()).isEqualByComparingTo("44.64");
        assertThat(boleta.getMontoIva()).isEqualByComparingTo("5.36");
        assertThat(boleta.getMontoSubtotal().add(boleta.getMontoIva()))
            .isEqualByComparingTo(boleta.getInvoiceTotal());
        // The invoice itemises the sale: the POS path now writes factura_linea too.
        assertThat(boleta.getLineas()).hasSize(1);
        assertThat(boleta.getLineas().get(0).getCalzadoSku().getSku()).isEqualTo("SKU-POS");
        assertThat(boleta.getLineas().get(0).getCantidad()).isEqualTo(1);
        assertThat(boleta.getLineas().get(0).getLineTotal()).isEqualByComparingTo("50.00");

        ArgumentCaptor<FacturaTipo> tipo = ArgumentCaptor.forClass(FacturaTipo.class);
        verify(numeracionFacturaService).siguiente(tipo.capture());
        assertThat(tipo.getValue().getCode()).isEqualTo("BOLETA");
    }

    @Test
    void unSkuDesconocidoEnCobrarDevuelve400ConElEnvelopeYNoCreaNada() throws Exception {
        stubSesionAbierta(1, "cajero1");
        stubVentaPosComun();

        mockMvc.perform(post("/api/pos/cobrar")
                .principal(autenticacion("cajero1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"NO-EXISTE-999\",\"cantidad\":1,\"nit\":\"CF\",\"razonSocial\":\"Consumidor Final\",\"metodoPago\":\"EFECTIVO\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("ERROR"))
            .andExpect(jsonPath("$.message").value("SKU no encontrado: NO-EXISTE-999"));

        verify(ordenRepository, never()).save(any(Orden.class));
        verify(kardexRepository, never()).save(any(KardexMovimiento.class));
        verify(facturaRepository, never()).save(any(Factura.class));
        verify(pagoTransaccionRepository, never()).save(any(PagoTransaccion.class));
    }

    // ------------------------------------------------------------------
    // Devolucion (T10.3a): per-line re-entry, algorithmic window, no over-return
    // ------------------------------------------------------------------

    @Test
    void unaDevolucionConFacturaDesconocidaDevuelve400ConElEnvelopeYNoCreaNada() throws Exception {
        when(facturaRepository.findVentaByNumeroCorrelativo("NO-EXISTE")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/pos/devolucion")
                .principal(autenticacion("cajero1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(devolucionJson("NO-EXISTE", null, "Talla incorrecta", true, true,
                    "[{\"sku\":\"TV001-40\",\"cantidad\":1,\"danado\":false}]")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("ERROR"))
            .andExpect(jsonPath("$.message").value("Factura no encontrada: NO-EXISTE"));

        verify(devolucionRepository, never()).save(any(Devolucion.class));
        verify(kardexRepository, never()).save(any(KardexMovimiento.class));
    }

    @Test
    void unaDevolucionSinFacturaNiOrdenDevuelve400() throws Exception {
        mockMvc.perform(post("/api/pos/devolucion")
                .principal(autenticacion("cajero1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(devolucionJson(null, null, "Talla incorrecta", true, true,
                    "[{\"sku\":\"TV001-40\",\"cantidad\":1,\"danado\":false}]")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Debe indicar el numero de factura o el numero de orden"));
    }

    @Test
    void unaDevolucionSeResuelvePorNumeroDeOrdenCuandoNoHayFactura() throws Exception {
        Factura factura = facturaCon(5, "TKT-1234", OffsetDateTime.now().minusDays(2),
            lineaVendida(10, "TV001-40", 3, "50.00"));
        when(ordenRepository.findByNumeroOrden("ORD-77")).thenReturn(Optional.of(factura.getOrden()));
        when(facturaRepository.findVentasByOrdenId(77)).thenReturn(List.of(factura));
        stubSesionAbierta(21, "cajero1");
        stubDevolucionComun();
        stubStock(2, 10, 4);

        mockMvc.perform(post("/api/pos/devolucion")
                .principal(autenticacion("cajero1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(devolucionJson(null, "ORD-77", "Talla incorrecta", true, true,
                    "[{\"sku\":\"TV001-40\",\"cantidad\":1,\"danado\":false}]")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.numeroDevolucion").value("DEV-TKT-1234-1"));
    }

    @Test
    void unaOrdenConDosComprobantesDeVentaNoSeResuelveTomandoElPrimeroYNoEscribeNada() throws Exception {
        // Two sale documents for one order is a broken invariant. It must surface as an explicit
        // failure instead of a Hibernate NonUniqueResultException or a silent pick of the first row.
        Factura primera = facturaCon(5, "TKT-1234", OffsetDateTime.now().minusDays(2),
            lineaVendida(10, "TV001-40", 3, "50.00"));
        Factura segunda = facturaCon(6, "TKT-9999", OffsetDateTime.now().minusDays(2),
            lineaVendida(10, "TV001-40", 3, "50.00"));
        when(ordenRepository.findByNumeroOrden("ORD-77")).thenReturn(Optional.of(primera.getOrden()));
        when(facturaRepository.findVentasByOrdenId(77)).thenReturn(List.of(primera, segunda));
        stubSesionAbierta(21, "cajero1");
        stubDevolucionComun();
        stubStock(2, 10, 4);

        assertThatThrownBy(() -> mockMvc.perform(post("/api/pos/devolucion")
                .principal(autenticacion("cajero1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(devolucionJson(null, "ORD-77", "Talla incorrecta", true, true,
                    "[{\"sku\":\"TV001-40\",\"cantidad\":1,\"danado\":false}]"))))
            .hasStackTraceContaining("mas de un comprobante de venta");

        verify(devolucionRepository, never()).save(any(Devolucion.class));
        verify(kardexRepository, never()).save(any(KardexMovimiento.class));
        verify(facturaRepository, never()).save(any(Factura.class));
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    void unaDevolucionConElNumeroDeUnaNotaDeCreditoEsRechazadaYNoEscribeNada() throws Exception {
        // A credit note carries its own correlative number and shares id_orden with the sale it
        // credits. Accepting one as the invoice would refund the customer again past what was sold,
        // and would key the accumulated over-return guard on the credit note instead of the sale.
        when(facturaRepository.findVentaByNumeroCorrelativo("NC-TKT-1234-1")).thenReturn(Optional.empty());
        stubSesionAbierta(21, "cajero1");
        stubDevolucionComun();
        stubStock(2, 10, 4);

        mockMvc.perform(post("/api/pos/devolucion")
                .principal(autenticacion("cajero1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(devolucionJson("NC-TKT-1234-1", null, "Nota de credito como factura", true, true,
                    "[{\"sku\":\"TV001-40\",\"cantidad\":1,\"danado\":false}]")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("ERROR"))
            .andExpect(jsonPath("$.message").value("Factura no encontrada: NC-TKT-1234-1"));

        verify(devolucionRepository, never()).save(any(Devolucion.class));
        verify(kardexRepository, never()).save(any(KardexMovimiento.class));
        verify(facturaRepository, never()).save(any(Factura.class));
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    void unaDevolucionSeAceptaCuandoLaFacturaNoTieneLineasPeroLaOrdenSi() throws Exception {
        // The real-world state today: both sale paths write orden_linea, but nothing writes
        // factura_linea, so the order is the authoritative record of what was sold. A return whose
        // invoice has no lines must still be accepted because the order carries them.
        Factura factura = facturaCon(5, "TKT-1234", OffsetDateTime.now().minusDays(2),
            lineaVendida(10, "TV001-40", 3, "50.00"));
        assertThat(factura.getLineas()).isEmpty();
        when(facturaRepository.findVentaByNumeroCorrelativo("TKT-1234")).thenReturn(Optional.of(factura));
        stubSesionAbierta(21, "cajero1");
        stubDevolucionComun();
        stubStock(2, 10, 0);

        mockMvc.perform(post("/api/pos/devolucion")
                .principal(autenticacion("cajero1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(devolucionJson("TKT-1234", null, "Talla incorrecta", true, true,
                    "[{\"sku\":\"TV001-40\",\"cantidad\":1,\"danado\":false}]")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.lineas[0].sku").value("TV001-40"))
            .andExpect(jsonPath("$.lineas[0].unitPrice").value(50.00));
    }

    @Test
    void unaDevolucionDeUnaOrdenDesconocidaDevuelve400() throws Exception {
        when(ordenRepository.findByNumeroOrden("ORD-X")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/pos/devolucion")
                .principal(autenticacion("cajero1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(devolucionJson(null, "ORD-X", "Talla incorrecta", true, true,
                    "[{\"sku\":\"TV001-40\",\"cantidad\":1,\"danado\":false}]")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Orden no encontrada: ORD-X"));
    }

    @Test
    void unaDevolucionSinLineasDevuelve400() throws Exception {
        when(facturaRepository.findVentaByNumeroCorrelativo("TKT-1234"))
            .thenReturn(Optional.of(facturaCon(5, "TKT-1234", OffsetDateTime.now(), lineaVendida(10, "TV001-40", 3, "50.00"))));

        mockMvc.perform(post("/api/pos/devolucion")
                .principal(autenticacion("cajero1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(devolucionJson("TKT-1234", null, "Talla incorrecta", true, true, "[]")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("La devolucion debe incluir al menos una linea"));
    }

    @Test
    void unaDevolucionConMotivoVacioDevuelve400() throws Exception {
        when(facturaRepository.findVentaByNumeroCorrelativo("TKT-1234"))
            .thenReturn(Optional.of(facturaCon(5, "TKT-1234", OffsetDateTime.now(), lineaVendida(10, "TV001-40", 3, "50.00"))));

        mockMvc.perform(post("/api/pos/devolucion")
                .principal(autenticacion("cajero1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(devolucionJson("TKT-1234", null, "   ", true, true,
                    "[{\"sku\":\"TV001-40\",\"cantidad\":1,\"danado\":false}]")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("El motivo de la devolucion es obligatorio"));
    }

    @Test
    void unaDevolucionConMotivoDemasiadoLargoDevuelve400() throws Exception {
        when(facturaRepository.findVentaByNumeroCorrelativo("TKT-1234"))
            .thenReturn(Optional.of(facturaCon(5, "TKT-1234", OffsetDateTime.now(), lineaVendida(10, "TV001-40", 3, "50.00"))));

        String largo = "x".repeat(61);
        mockMvc.perform(post("/api/pos/devolucion")
                .principal(autenticacion("cajero1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(devolucionJson("TKT-1234", null, largo, true, true,
                    "[{\"sku\":\"TV001-40\",\"cantidad\":1,\"danado\":false}]")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("El motivo de la devolucion no puede superar 60 caracteres"));
    }

    @Test
    void unaDevolucionSinSuelaIntactaDevuelve400() throws Exception {
        when(facturaRepository.findVentaByNumeroCorrelativo("TKT-1234"))
            .thenReturn(Optional.of(facturaCon(5, "TKT-1234", OffsetDateTime.now(), lineaVendida(10, "TV001-40", 3, "50.00"))));

        mockMvc.perform(post("/api/pos/devolucion")
                .principal(autenticacion("cajero1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(devolucionJson("TKT-1234", null, "Talla incorrecta", null, true,
                    "[{\"sku\":\"TV001-40\",\"cantidad\":1,\"danado\":false}]")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Debe indicar si la suela esta intacta"));
    }

    @Test
    void unaDevolucionSinEmpaqueOriginalDevuelve400() throws Exception {
        when(facturaRepository.findVentaByNumeroCorrelativo("TKT-1234"))
            .thenReturn(Optional.of(facturaCon(5, "TKT-1234", OffsetDateTime.now(), lineaVendida(10, "TV001-40", 3, "50.00"))));

        mockMvc.perform(post("/api/pos/devolucion")
                .principal(autenticacion("cajero1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(devolucionJson("TKT-1234", null, "Talla incorrecta", true, null,
                    "[{\"sku\":\"TV001-40\",\"cantidad\":1,\"danado\":false}]")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Debe indicar si el empaque es original"));
    }

    @Test
    void unaDevolucionDeUnSkuNoVendidoDevuelve400() throws Exception {
        Factura factura = facturaCon(5, "TKT-1234", OffsetDateTime.now().minusDays(2),
            lineaVendida(10, "TV001-40", 3, "50.00"));
        when(facturaRepository.findVentaByNumeroCorrelativo("TKT-1234")).thenReturn(Optional.of(factura));
        stubSesionAbierta(21, "cajero1");
        stubDevolucionComun();

        mockMvc.perform(post("/api/pos/devolucion")
                .principal(autenticacion("cajero1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(devolucionJson("TKT-1234", null, "Talla incorrecta", true, true,
                    "[{\"sku\":\"NO-VENDIDO-99\",\"cantidad\":1,\"danado\":false}]")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("El SKU no pertenece a la venta: NO-VENDIDO-99"));

        verify(devolucionRepository, never()).save(any(Devolucion.class));
    }

    @Test
    void unaDevolucionConCantidadCeroDevuelve400() throws Exception {
        Factura factura = facturaCon(5, "TKT-1234", OffsetDateTime.now().minusDays(2),
            lineaVendida(10, "TV001-40", 3, "50.00"));
        when(facturaRepository.findVentaByNumeroCorrelativo("TKT-1234")).thenReturn(Optional.of(factura));
        stubSesionAbierta(21, "cajero1");
        stubDevolucionComun();

        mockMvc.perform(post("/api/pos/devolucion")
                .principal(autenticacion("cajero1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(devolucionJson("TKT-1234", null, "Talla incorrecta", true, true,
                    "[{\"sku\":\"TV001-40\",\"cantidad\":0,\"danado\":false}]")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("La cantidad a devolver debe ser mayor a cero para el SKU: TV001-40"));
    }

    @Test
    void unaDevolucionQueSuperaLoVendidoDevuelve400NombrandoSkuVendidoYDevuelto() throws Exception {
        Factura factura = facturaCon(5, "TKT-1234", OffsetDateTime.now().minusDays(2),
            lineaVendida(10, "TV001-40", 3, "50.00"));
        when(facturaRepository.findVentaByNumeroCorrelativo("TKT-1234")).thenReturn(Optional.of(factura));
        when(devolucionRepository.findByFacturaOrigenId(5)).thenReturn(List.of(
            devolucionPrevia("DEV-TKT-1234-1", 10, "TV001-40", 2)));
        stubSesionAbierta(21, "cajero1");
        stubDevolucionComun();

        mockMvc.perform(post("/api/pos/devolucion")
                .principal(autenticacion("cajero1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(devolucionJson("TKT-1234", null, "Talla incorrecta", true, true,
                    "[{\"sku\":\"TV001-40\",\"cantidad\":2,\"danado\":false}]")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(
                "La cantidad devuelta supera lo vendido para el SKU: TV001-40 (Vendido=3, YaDevuelto=2)"));

        verify(devolucionRepository, never()).save(any(Devolucion.class));
        verify(kardexRepository, never()).save(any(KardexMovimiento.class));
    }

    @Test
    void unaDevolucionAptaReingresaStockEnActualYReembolsaSoloLoDevuelto() throws Exception {
        Factura factura = facturaCon(5, "TKT-1234", OffsetDateTime.now().minusDays(3),
            lineaVendida(10, "TV001-40", 3, "50.00"));
        // The invoice total is deliberately much larger than the refund: the refund must come from
        // the returned lines, never from factura.invoice_total.
        factura.setInvoiceTotal(new BigDecimal("999.00"));
        when(facturaRepository.findVentaByNumeroCorrelativo("TKT-1234")).thenReturn(Optional.of(factura));
        stubSesionAbierta(21, "cajero1");
        stubDevolucionComun();
        InventarioStock stock = stubStock(2, 10, 4);

        mockMvc.perform(post("/api/pos/devolucion")
                .principal(autenticacion("cajero1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(devolucionJson("TKT-1234", null, "Talla incorrecta", true, true,
                    "[{\"sku\":\"TV001-40\",\"cantidad\":2,\"danado\":false}]")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DEVOLUCION_APROBADA"))
            .andExpect(jsonPath("$.numeroDevolucion").value("DEV-TKT-1234-1"))
            .andExpect(jsonPath("$.destinoPrincipal").value("ACTUAL"))
            .andExpect(jsonPath("$.montoTotalReembolso").value(100.00))
            .andExpect(jsonPath("$.montoReembolso").value(100.00))
            .andExpect(jsonPath("$.lineas[0].sku").value("TV001-40"))
            .andExpect(jsonPath("$.lineas[0].cantidad").value(2))
            .andExpect(jsonPath("$.lineas[0].unitPrice").value(50.00))
            .andExpect(jsonPath("$.lineas[0].estadoCalzado").value("APTO_PRODUCTO_ACTUAL"))
            .andExpect(jsonPath("$.lineas[0].destinoBodegaTipo").value("ACTUAL"));

        assertThat(stock.getCantidadDisponible()).as("stock reingresado en ACTUAL").isEqualTo(6);

        ArgumentCaptor<Devolucion> dev = ArgumentCaptor.forClass(Devolucion.class);
        verify(devolucionRepository).save(dev.capture());
        assertThat(dev.getValue().getMontoTotalReembolso()).isEqualByComparingTo("100.00");
        assertThat(dev.getValue().getDiasTranscurridos()).isEqualTo(3);
        assertThat(dev.getValue().getBodegaDestino().getId()).isEqualTo(2);
        assertThat(dev.getValue().getLineas()).hasSize(1);
        assertThat(dev.getValue().getLineas().get(0).getEstadoCalzado()).isEqualTo("APTO_PRODUCTO_ACTUAL");
        assertThat(dev.getValue().getLineas().get(0).getUnitPrice()).isEqualByComparingTo("50.00");

        ArgumentCaptor<KardexMovimiento> kardex = ArgumentCaptor.forClass(KardexMovimiento.class);
        verify(kardexRepository).save(kardex.capture());
        assertThat(kardex.getValue().getMovimientoTipo().getCode()).isEqualTo("REINGRESO_DEVOLUCION");
        assertThat(kardex.getValue().getBodega().getId()).isEqualTo(2);
        assertThat(kardex.getValue().getCantidadMovimiento()).isEqualTo(2);
        assertThat(kardex.getValue().getStockAnterior()).isEqualTo(4);
        assertThat(kardex.getValue().getStockPosterior()).isEqualTo(6);
        assertThat(kardex.getValue().getDocumentoTipo()).isEqualTo("DEVOLUCION");
        assertThat(kardex.getValue().getDocumentoId()).isEqualTo(88L);
    }

    @Test
    void unaLineaDanadaReingresaEnLiquidacionYElDestinoTitularEsLiquidacion() throws Exception {
        Factura factura = facturaCon(5, "TKT-1234", OffsetDateTime.now().minusDays(2),
            lineaVendida(10, "TV001-40", 3, "50.00"));
        when(facturaRepository.findVentaByNumeroCorrelativo("TKT-1234")).thenReturn(Optional.of(factura));
        stubSesionAbierta(21, "cajero1");
        stubDevolucionComun();
        InventarioStock stock = stubStock(3, 10, 1);

        mockMvc.perform(post("/api/pos/devolucion")
                .principal(autenticacion("cajero1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(devolucionJson("TKT-1234", null, "Producto danado", false, false,
                    "[{\"sku\":\"TV001-40\",\"cantidad\":1,\"danado\":true}]")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.destinoPrincipal").value("LIQUIDACION"))
            .andExpect(jsonPath("$.enrutadoA").value("Bodega 3 (Liquidacion Outlet)"))
            .andExpect(jsonPath("$.lineas[0].estadoCalzado").value("DANADO_OUTLET"))
            .andExpect(jsonPath("$.lineas[0].destinoBodegaTipo").value("LIQUIDACION"));

        assertThat(stock.getCantidadDisponible()).as("stock reingresado en LIQUIDACION").isEqualTo(2);
    }

    @Test
    void unaDevolucionFueraDePlazoSeEnrutaALiquidacionAunqueLaLineaNoEsteDanada() throws Exception {
        Factura factura = facturaCon(5, "TKT-1234", OffsetDateTime.now().minusDays(20),
            lineaVendida(10, "TV001-40", 3, "50.00"));
        when(facturaRepository.findVentaByNumeroCorrelativo("TKT-1234")).thenReturn(Optional.of(factura));
        stubSesionAbierta(21, "cajero1");
        stubDevolucionComun();
        InventarioStock stock = stubStock(3, 10, 1);

        mockMvc.perform(post("/api/pos/devolucion")
                .principal(autenticacion("cajero1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(devolucionJson("TKT-1234", null, "Compra vieja", true, true,
                    "[{\"sku\":\"TV001-40\",\"cantidad\":1,\"danado\":false}]")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.destinoPrincipal").value("LIQUIDACION"))
            .andExpect(jsonPath("$.lineas[0].estadoCalzado").value("DANADO_OUTLET"));

        ArgumentCaptor<Devolucion> dev = ArgumentCaptor.forClass(Devolucion.class);
        verify(devolucionRepository).save(dev.capture());
        assertThat(dev.getValue().getDiasTranscurridos()).isEqualTo(20);
        assertThat(stock.getCantidadDisponible()).isEqualTo(2);
    }

    @Test
    void unaDevolucionMixtaParteElStockPorClasificacionConUnSoloComprobante() throws Exception {
        Factura factura = facturaCon(5, "TKT-1234", OffsetDateTime.now().minusDays(2),
            lineaVendida(10, "TV001-40", 3, "50.00"),
            lineaVendida(11, "TV002-41", 3, "60.00"));
        when(facturaRepository.findVentaByNumeroCorrelativo("TKT-1234")).thenReturn(Optional.of(factura));
        stubSesionAbierta(21, "cajero1");
        stubDevolucionComun();
        InventarioStock stockApto = stubStock(2, 10, 5);
        InventarioStock stockOut = stubStock(3, 11, 0);

        mockMvc.perform(post("/api/pos/devolucion")
                .principal(autenticacion("cajero1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(devolucionJson("TKT-1234", null, "Devolucion mixta", true, true,
                    "[{\"sku\":\"TV001-40\",\"cantidad\":1,\"danado\":false},"
                        + "{\"sku\":\"TV002-41\",\"cantidad\":1,\"danado\":true}]")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.destinoPrincipal").value("LIQUIDACION"))
            .andExpect(jsonPath("$.montoTotalReembolso").value(110.00))
            .andExpect(jsonPath("$.lineas.length()").value(2));

        assertThat(stockApto.getCantidadDisponible()).as("apto en ACTUAL").isEqualTo(6);
        assertThat(stockOut.getCantidadDisponible()).as("danado en LIQUIDACION").isEqualTo(1);

        ArgumentCaptor<KardexMovimiento> kardex = ArgumentCaptor.forClass(KardexMovimiento.class);
        verify(kardexRepository, org.mockito.Mockito.times(2)).save(kardex.capture());
        assertThat(kardex.getAllValues()).extracting(k -> k.getBodega().getId())
            .containsExactlyInAnyOrder(2, 3);
    }

    @Test
    void unaSegundaDevolucionNumeraDosYDescuentaLoYaDevuelto() throws Exception {
        Factura factura = facturaCon(5, "TKT-1234", OffsetDateTime.now().minusDays(2),
            lineaVendida(10, "TV001-40", 3, "50.00"));
        when(facturaRepository.findVentaByNumeroCorrelativo("TKT-1234")).thenReturn(Optional.of(factura));
        when(devolucionRepository.findByFacturaOrigenId(5)).thenReturn(List.of(
            devolucionPrevia("DEV-TKT-1234-1", 10, "TV001-40", 1)));
        stubSesionAbierta(21, "cajero1");
        stubDevolucionComun();
        stubStock(2, 10, 5);

        mockMvc.perform(post("/api/pos/devolucion")
                .principal(autenticacion("cajero1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(devolucionJson("TKT-1234", null, "Segunda devolucion", true, true,
                    "[{\"sku\":\"TV001-40\",\"cantidad\":2,\"danado\":false}]")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.numeroDevolucion").value("DEV-TKT-1234-2"));
    }

    @Test
    void laDevolucionUsaElPrecioVendidoEnLaFacturaYNoElPrecioDeCatalogo() throws Exception {
        // The invoice charged 40.00 for this SKU while the catalogue price is irrelevant: the line
        // refund must use the charged unit_price, not a current price.
        Factura factura = facturaCon(5, "TKT-1234", OffsetDateTime.now().minusDays(2),
            lineaVendida(10, "TV001-40", 3, "40.00"));
        when(facturaRepository.findVentaByNumeroCorrelativo("TKT-1234")).thenReturn(Optional.of(factura));
        stubSesionAbierta(21, "cajero1");
        stubDevolucionComun();
        stubStock(2, 10, 0);

        mockMvc.perform(post("/api/pos/devolucion")
                .principal(autenticacion("cajero1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(devolucionJson("TKT-1234", null, "Talla incorrecta", true, true,
                    "[{\"sku\":\"TV001-40\",\"cantidad\":3,\"danado\":false}]")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.montoTotalReembolso").value(120.00))
            .andExpect(jsonPath("$.lineas[0].unitPrice").value(40.00));
    }

    @Test
    void laDevolucionRegistraLasAsercionesFisicasDelCajeroSinRechazarPorSuelaOEmpaque() throws Exception {
        // RN-POS-05 accepts a damaged item and routes it to the outlet; sole and packaging are
        // physical assertions the system cannot compute, so they are recorded, never used to reject.
        Factura factura = facturaCon(5, "TKT-1234", OffsetDateTime.now().minusDays(2),
            lineaVendida(10, "TV001-40", 3, "50.00"));
        when(facturaRepository.findVentaByNumeroCorrelativo("TKT-1234")).thenReturn(Optional.of(factura));
        stubSesionAbierta(21, "cajero1");
        stubDevolucionComun();
        stubStock(2, 10, 0);

        mockMvc.perform(post("/api/pos/devolucion")
                .principal(autenticacion("cajero1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(devolucionJson("TKT-1234", null, "Sin caja", false, false,
                    "[{\"sku\":\"TV001-40\",\"cantidad\":1,\"danado\":false}]")))
            .andExpect(status().isOk());

        ArgumentCaptor<Devolucion> dev = ArgumentCaptor.forClass(Devolucion.class);
        verify(devolucionRepository).save(dev.capture());
        assertThat(dev.getValue().getSuelaIntacta()).isFalse();
        assertThat(dev.getValue().getEmpaqueOriginal()).isFalse();
    }

    @Test
    void laDevolucionUsaLaFechaDeEntregaRealCuandoExisteDespacho() throws Exception {
        // The invoice is 30 days old, but the OTP-validated delivery happened 2 days ago, so the
        // return is inside the window and the line stays APTO.
        Factura factura = facturaCon(5, "TKT-1234", OffsetDateTime.now().minusDays(30),
            lineaVendida(10, "TV001-40", 3, "50.00"));
        when(facturaRepository.findVentaByNumeroCorrelativo("TKT-1234")).thenReturn(Optional.of(factura));
        DespachoEnvio despacho = new DespachoEnvio();
        despacho.setFechaEntregaReal(OffsetDateTime.now().minusDays(2));
        when(despachoEnvioPorOrdenRepository.findByOrdenId(77)).thenReturn(Optional.of(despacho));
        stubSesionAbierta(21, "cajero1");
        stubDevolucionComun();
        stubStock(2, 10, 0);

        mockMvc.perform(post("/api/pos/devolucion")
                .principal(autenticacion("cajero1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(devolucionJson("TKT-1234", null, "Talla incorrecta", true, true,
                    "[{\"sku\":\"TV001-40\",\"cantidad\":1,\"danado\":false}]")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.lineas[0].estadoCalzado").value("APTO_PRODUCTO_ACTUAL"));

        ArgumentCaptor<Devolucion> dev = ArgumentCaptor.forClass(Devolucion.class);
        verify(devolucionRepository).save(dev.capture());
        assertThat(dev.getValue().getDiasTranscurridos()).isEqualTo(2);
    }

    @Test
    void laDevolucionNoTocaElArqueoNiEscribePagoTransaccion() throws Exception {
        Factura factura = facturaCon(5, "TKT-1234", OffsetDateTime.now().minusDays(2),
            lineaVendida(10, "TV001-40", 3, "50.00"));
        when(facturaRepository.findVentaByNumeroCorrelativo("TKT-1234")).thenReturn(Optional.of(factura));
        stubSesionAbierta(21, "cajero1");
        stubDevolucionComun();
        stubStock(2, 10, 0);
        SesionCaja sesion = sesionAbierta(21, cajero("cajero1"));
        sesion.setMontoApertura(new BigDecimal("100.00"));
        sesion.setTotalVentasSistema(new BigDecimal("250.00"));
        sesion.setDiferenciaArqueo(new BigDecimal("5.00"));
        when(sesionCajaRepository.findFirstByCajeroIdAndEstado(1, "ABIERTA"))
            .thenReturn(Optional.of(sesion));

        mockMvc.perform(post("/api/pos/devolucion")
                .principal(autenticacion("cajero1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(devolucionJson("TKT-1234", null, "Talla incorrecta", true, true,
                    "[{\"sku\":\"TV001-40\",\"cantidad\":1,\"danado\":false}]")))
            .andExpect(status().isOk());

        // The physical return refunds store balance, never cash, so the credit note is issued but
        // the arqueo stays untouched and no payment row is written.
        ArgumentCaptor<Factura> nota = ArgumentCaptor.forClass(Factura.class);
        verify(facturaRepository).save(nota.capture());
        assertThat(nota.getValue().getFacturaTipo().getCode()).isEqualTo("NOTA_CREDITO");
        verify(pagoTransaccionRepository, never()).save(any(PagoTransaccion.class));
        assertThat(sesion.getMontoApertura()).isEqualByComparingTo("100.00");
        assertThat(sesion.getTotalVentasSistema()).isEqualByComparingTo("250.00");
        assertThat(sesion.getDiferenciaArqueo()).isEqualByComparingTo("5.00");
        assertThat(sesion.getMontoCierreEfectivo()).isNull();
    }

    @Test
    void laDevolucionEmiteNotaCreditoConIdentidadDeDineroYUrlRealDelEscritor() throws Exception {
        Factura factura = facturaCon(5, "TKT-1234", OffsetDateTime.now().minusDays(3),
            lineaVendida(10, "TV001-40", 3, "50.00"));
        when(facturaRepository.findVentaByNumeroCorrelativo("TKT-1234")).thenReturn(Optional.of(factura));
        stubSesionAbierta(21, "cajero1");
        stubDevolucionComun();
        stubStock(2, 10, 0);

        MvcResult resultado = mockMvc.perform(post("/api/pos/devolucion")
                .principal(autenticacion("cajero1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(devolucionJson("TKT-1234", null, "Talla incorrecta", true, true,
                    "[{\"sku\":\"TV001-40\",\"cantidad\":2,\"danado\":false}]")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.numeroDevolucion").value("DEV-TKT-1234-1"))
            .andExpect(jsonPath("$.notaCreditoCorrelativo").value("NC-TKT-1234-1"))
            .andExpect(jsonPath("$.saldoFavorCliente").value(100.00))
            .andReturn();

        ArgumentCaptor<Factura> captor = ArgumentCaptor.forClass(Factura.class);
        verify(facturaRepository).save(captor.capture());
        Factura nota = captor.getValue();
        assertThat(nota.getFacturaTipo().getCode()).isEqualTo("NOTA_CREDITO");
        assertThat(nota.getFacturaEstado().getCode()).isEqualTo("EMITIDA");
        assertThat(nota.getOrden().getId()).isEqualTo(77);
        assertThat(nota.getNumeroCorrelativo()).isEqualTo("NC-TKT-1234-1");
        assertThat(nota.getNitCliente()).isEqualTo("1712345678");
        assertThat(nota.getRazonSocial()).isEqualTo("Cliente Identificado");
        // The already-identified sale names that same customer on the credit note.
        assertThat(nota.getCustomer().getEmail()).isEqualTo("cliente@correo.com");
        assertThat(nota.getMontoRecargoPasarela()).isEqualByComparingTo("0.00");
        assertThat(nota.getLineas()).isEmpty();
        // The money identity is asserted, not assumed: subtotal + iva must equal the refund exactly.
        assertThat(nota.getInvoiceTotal()).isEqualByComparingTo("100.00");
        assertThat(nota.getMontoSubtotal()).isEqualByComparingTo("89.29");
        assertThat(nota.getMontoIva()).isEqualByComparingTo("10.71");
        assertThat(nota.getMontoSubtotal().add(nota.getMontoIva()))
            .isEqualByComparingTo(nota.getInvoiceTotal());

        String url = JsonPath.read(resultado.getResponse().getContentAsString(), "$.notaCreditoPdfUrl");
        assertThat(url).isEqualTo(FacturaPdfStorage.URL_PREFIX + nota.getNumeroCorrelativo() + ".pdf");
        assertThat(url).contains("NC-TKT-1234-1").doesNotContain("nc-tkt-1234-1");
        assertThat(nota.getPdfUrl()).isEqualTo(url);
        Path archivo = tempDir.resolve(url.substring(FacturaPdfStorage.URL_PREFIX.length()));
        assertThat(archivo).exists();
        assertThat(Files.readAllBytes(archivo)).startsWith("%PDF-1.4".getBytes(StandardCharsets.ISO_8859_1));

        ArgumentCaptor<Devolucion> dev = ArgumentCaptor.forClass(Devolucion.class);
        verify(devolucionRepository).save(dev.capture());
        assertThat(dev.getValue().getNotaCredito()).isSameAs(nota);
    }

    @Test
    void laUrlDeLaNotaCreditoEsLaQueDevuelveElEscritorYNoUnaConstruidaAMano() throws Exception {
        FacturaPdfStorage almacen = org.mockito.Mockito.spy(new FacturaPdfStorage(tempDir));
        org.mockito.Mockito.doReturn("/storage/facturas/SENTINEL-NC.pdf")
            .when(almacen).guardar(any(String.class), any(byte[].class));
        MockMvc mvc = mockMvcCon(new NotaCreditoService(new NotaCreditoPdf(), almacen));

        Factura factura = facturaCon(5, "TKT-1234", OffsetDateTime.now().minusDays(2),
            lineaVendida(10, "TV001-40", 3, "50.00"));
        when(facturaRepository.findVentaByNumeroCorrelativo("TKT-1234")).thenReturn(Optional.of(factura));
        stubSesionAbierta(21, "cajero1");
        stubDevolucionComun();
        stubStock(2, 10, 0);

        mvc.perform(post("/api/pos/devolucion")
                .principal(autenticacion("cajero1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(devolucionJson("TKT-1234", null, "Talla incorrecta", true, true,
                    "[{\"sku\":\"TV001-40\",\"cantidad\":1,\"danado\":false}]")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notaCreditoPdfUrl").value("/storage/facturas/SENTINEL-NC.pdf"));

        // The controller must return exactly what the writer returned; a hand-built or lowercased
        // url would not match this sentinel.
        verify(almacen).guardar(org.mockito.ArgumentMatchers.eq("NC-TKT-1234-1"), any(byte[].class));
    }

    @Test
    void laDevolucionDeUnConsumidorFinalSinEmailEsRechazadaYNoEscribeNada() throws Exception {
        Factura factura = facturaCon(5, "TKT-1234", OffsetDateTime.now().minusDays(2),
            lineaVendida(10, "TV001-40", 3, "50.00"));
        factura.setCustomer(consumidorFinalAnonimo());
        when(facturaRepository.findVentaByNumeroCorrelativo("TKT-1234")).thenReturn(Optional.of(factura));
        stubSesionAbierta(21, "cajero1");
        stubDevolucionComun();
        stubStock(2, 10, 0);

        mockMvc.perform(post("/api/pos/devolucion")
                .principal(autenticacion("cajero1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(devolucionJson("TKT-1234", null, "Talla incorrecta", true, true,
                    "[{\"sku\":\"TV001-40\",\"cantidad\":1,\"danado\":false}]")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("ERROR"))
            .andExpect(jsonPath("$.message").value(
                "Debe identificar al cliente con su correo para acreditar el saldo a favor"));

        verify(devolucionRepository, never()).save(any(Devolucion.class));
        verify(kardexRepository, never()).save(any(KardexMovimiento.class));
        verify(facturaRepository, never()).save(any(Factura.class));
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    void laDevolucionDeUnConsumidorFinalConEmailDesconocidoEsRechazadaYNoEscribeNada() throws Exception {
        Factura factura = facturaCon(5, "TKT-1234", OffsetDateTime.now().minusDays(2),
            lineaVendida(10, "TV001-40", 3, "50.00"));
        factura.setCustomer(consumidorFinalAnonimo());
        when(facturaRepository.findVentaByNumeroCorrelativo("TKT-1234")).thenReturn(Optional.of(factura));
        when(customerRepository.findByEmail("nuevo@correo.com")).thenReturn(Optional.empty());
        stubSesionAbierta(21, "cajero1");
        stubDevolucionComun();
        stubStock(2, 10, 0);

        mockMvc.perform(post("/api/pos/devolucion")
                .principal(autenticacion("cajero1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(devolucionJsonConEmail("TKT-1234", "nuevo@correo.com")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("ERROR"))
            .andExpect(jsonPath("$.message").value(
                "No existe un cliente con el correo indicado; identifique al cliente para acreditar el saldo a favor"));

        verify(devolucionRepository, never()).save(any(Devolucion.class));
        verify(kardexRepository, never()).save(any(KardexMovimiento.class));
        verify(facturaRepository, never()).save(any(Factura.class));
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    void laDevolucionDeUnConsumidorFinalConElCorreoDelMostradorEsRechazadaYNoEscribeNada() throws Exception {
        // The placeholder itself resolves to an existing customer, so "unknown email" does not
        // catch it. Crediting it would pool every anonymous refund into one shared wallet.
        Factura factura = facturaCon(5, "TKT-1234", OffsetDateTime.now().minusDays(2),
            lineaVendida(10, "TV001-40", 3, "50.00"));
        factura.setCustomer(consumidorFinalAnonimo());
        when(facturaRepository.findVentaByNumeroCorrelativo("TKT-1234")).thenReturn(Optional.of(factura));
        when(customerRepository.findByEmail("cf@calzadoempresa.com"))
            .thenReturn(Optional.of(consumidorFinalAnonimo()));
        stubSesionAbierta(21, "cajero1");
        stubDevolucionComun();
        stubStock(2, 10, 0);

        mockMvc.perform(post("/api/pos/devolucion")
                .principal(autenticacion("cajero1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(devolucionJsonConEmail("TKT-1234", "cf@calzadoempresa.com")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("ERROR"))
            .andExpect(jsonPath("$.message").value(
                "No se puede acreditar el saldo a favor en la cuenta de consumidor final; identifique al cliente real"));

        verify(devolucionRepository, never()).save(any(Devolucion.class));
        verify(kardexRepository, never()).save(any(KardexMovimiento.class));
        verify(facturaRepository, never()).save(any(Factura.class));
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    void laDevolucionDeUnConsumidorFinalConEmailIdentificadoAcreditaSuSaldo() throws Exception {
        Factura factura = facturaCon(5, "TKT-1234", OffsetDateTime.now().minusDays(2),
            lineaVendida(10, "TV001-40", 3, "50.00"));
        factura.setCustomer(consumidorFinalAnonimo());
        when(facturaRepository.findVentaByNumeroCorrelativo("TKT-1234")).thenReturn(Optional.of(factura));
        Customer identificado = clienteIdentificado(9, "juan@correo.com", "5.00");
        when(customerRepository.findByEmail("juan@correo.com")).thenReturn(Optional.of(identificado));
        stubSesionAbierta(21, "cajero1");
        stubDevolucionComun();
        stubStock(2, 10, 0);

        mockMvc.perform(post("/api/pos/devolucion")
                .principal(autenticacion("cajero1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(devolucionJsonConEmail("TKT-1234", "juan@correo.com")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notaCreditoCorrelativo").value("NC-TKT-1234-1"))
            .andExpect(jsonPath("$.saldoFavorCliente").value(55.00));

        assertThat(identificado.getSaldoFavorWallet()).isEqualByComparingTo("55.00");
        verify(customerRepository).save(identificado);
        // The credit note's beneficiary identity is asserted in
        // laNotaCreditoNombraAlClienteIdentificadoYNoAlConsumidorFinal.
    }

    @Test
    void laNotaCreditoNombraAlClienteIdentificadoYNoAlConsumidorFinal() throws Exception {
        // The sale was anonymous, so the cashier identifies a person at the counter. The credit
        // note is the instrument that embodies the store balance, so it must name that person, not
        // the shared placeholder the sale was rung under.
        Factura factura = facturaCon(5, "TKT-1234", OffsetDateTime.now().minusDays(2),
            lineaVendida(10, "TV001-40", 3, "50.00"));
        factura.setCustomer(consumidorFinalAnonimo());
        when(facturaRepository.findVentaByNumeroCorrelativo("TKT-1234")).thenReturn(Optional.of(factura));
        Customer identificado = clienteIdentificado(9, "juan@correo.com", "5.00");
        when(customerRepository.findByEmail("juan@correo.com")).thenReturn(Optional.of(identificado));
        stubSesionAbierta(21, "cajero1");
        stubDevolucionComun();
        stubStock(2, 10, 0);

        mockMvc.perform(post("/api/pos/devolucion")
                .principal(autenticacion("cajero1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(devolucionJsonConEmail("TKT-1234", "juan@correo.com")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.saldoFavorCliente").value(55.00));

        ArgumentCaptor<Factura> captor = ArgumentCaptor.forClass(Factura.class);
        verify(facturaRepository).save(captor.capture());
        Factura nota = captor.getValue();
        assertThat(nota.getCustomer().getEmail()).isEqualTo("juan@correo.com");
        assertThat(nota.getNitCliente()).isEqualTo("1712345678");
        assertThat(nota.getRazonSocial()).isEqualTo("Cliente Identificado");
        // The link to the original sale is untouched: the credit note still points at the order.
        assertThat(nota.getOrden().getId()).isEqualTo(77);
        assertThat(nota.getOrden().getNumeroOrden()).isEqualTo("ORD-77");
        ArgumentCaptor<Devolucion> dev = ArgumentCaptor.forClass(Devolucion.class);
        verify(devolucionRepository).save(dev.capture());
        assertThat(dev.getValue().getFacturaOrigen().getId()).isEqualTo(5);
        assertThat(dev.getValue().getFacturaOrigen().getNumeroCorrelativo()).isEqualTo("TKT-1234");
    }

    @Test
    void unaDevolucionFallaSiFaltaElTipoNotaCreditoYNoEscribeNada() throws Exception {
        Factura factura = facturaCon(5, "TKT-1234", OffsetDateTime.now().minusDays(2),
            lineaVendida(10, "TV001-40", 3, "50.00"));
        when(facturaRepository.findVentaByNumeroCorrelativo("TKT-1234")).thenReturn(Optional.of(factura));
        stubSesionAbierta(21, "cajero1");
        stubDevolucionComun();
        when(facturaTipoRepository.findByCode("NOTA_CREDITO")).thenReturn(Optional.empty());
        stubStock(2, 10, 0);

        assertThatThrownBy(() -> mockMvc.perform(post("/api/pos/devolucion")
                .principal(autenticacion("cajero1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(devolucionJson("TKT-1234", null, "Talla incorrecta", true, true,
                    "[{\"sku\":\"TV001-40\",\"cantidad\":1,\"danado\":false}]"))))
            .hasRootCauseInstanceOf(IllegalStateException.class)
            .hasRootCauseMessage("Invariante rota: no existe el tipo de factura NOTA_CREDITO");

        verify(devolucionRepository, never()).save(any(Devolucion.class));
        verify(kardexRepository, never()).save(any(KardexMovimiento.class));
        verify(facturaRepository, never()).save(any(Factura.class));
    }

    @Test
    void unaDevolucionFallaSiFaltaLaBodegaActual() throws Exception {
        Factura factura = facturaCon(5, "TKT-1234", OffsetDateTime.now().minusDays(2),
            lineaVendida(10, "TV001-40", 3, "50.00"));
        when(facturaRepository.findVentaByNumeroCorrelativo("TKT-1234")).thenReturn(Optional.of(factura));
        stubSesionAbierta(21, "cajero1");
        when(bodegaRepository.findByBodegaTipoCode("ACTUAL")).thenReturn(List.of());
        lenient().when(bodegaRepository.findByBodegaTipoCode("LIQUIDACION"))
            .thenReturn(List.of(bodega(3, "Bodega 3 (Liquidacion Outlet)")));

        // A missing warehouse type is a broken invariant, so the controller fails loudly instead of
        // silently falling back to the order's dispatch warehouse. MockMvc standalone rethrows the
        // unhandled exception (the real app would map it to a 500).
        assertThatThrownBy(() -> mockMvc.perform(post("/api/pos/devolucion")
                .principal(autenticacion("cajero1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(devolucionJson("TKT-1234", null, "Talla incorrecta", true, true,
                    "[{\"sku\":\"TV001-40\",\"cantidad\":1,\"danado\":false}]"))))
            .hasRootCauseInstanceOf(IllegalStateException.class)
            .hasRootCauseMessage("Invariante rota: no existe una bodega con tipo ACTUAL");
    }

    private void stubDevolucionComun() {
        lenient().when(bodegaRepository.findByBodegaTipoCode("ACTUAL"))
            .thenReturn(List.of(bodega(2, "Bodega 2 (Producto Actual)")));
        lenient().when(bodegaRepository.findByBodegaTipoCode("LIQUIDACION"))
            .thenReturn(List.of(bodega(3, "Bodega 3 (Liquidacion Outlet)")));
        MovimientoStockTipo reingreso = new MovimientoStockTipo();
        reingreso.setCode("REINGRESO_DEVOLUCION");
        lenient().when(movimientoTipoRepository.findByCode("REINGRESO_DEVOLUCION"))
            .thenReturn(Optional.of(reingreso));
        lenient().when(kardexRepository.save(any(KardexMovimiento.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(devolucionRepository.save(any(Devolucion.class))).thenAnswer(invocation -> {
            Devolucion dev = invocation.getArgument(0);
            if (dev.getId() == null) {
                dev.setId(88);
            }
            return dev;
        });
        FacturaTipo tipoNotaCredito = new FacturaTipo();
        tipoNotaCredito.setCode("NOTA_CREDITO");
        lenient().when(facturaTipoRepository.findByCode("NOTA_CREDITO"))
            .thenReturn(Optional.of(tipoNotaCredito));
        FacturaEstado estadoEmitida = new FacturaEstado();
        estadoEmitida.setCode("EMITIDA");
        lenient().when(facturaEstadoRepository.findByCode("EMITIDA"))
            .thenReturn(Optional.of(estadoEmitida));
        lenient().when(facturaRepository.save(any(Factura.class))).thenAnswer(invocation -> {
            Factura factura = invocation.getArgument(0);
            if (factura.getId() == null) {
                factura.setId(200);
            }
            return factura;
        });
        lenient().when(customerRepository.save(any(Customer.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private InventarioStock stubStock(int bodegaId, int skuId, int disponible) {
        InventarioStock stock = new InventarioStock();
        stock.setBodega(bodega(bodegaId, "Bodega " + bodegaId));
        CalzadoSku sku = new CalzadoSku();
        sku.setId(skuId);
        stock.setCalzadoSku(sku);
        stock.setCantidadDisponible(disponible);
        stock.setCantidadReservada(0);
        lenient().when(stockRepository.findStockConBloqueoPesimista(bodegaId, skuId))
            .thenReturn(Optional.of(stock));
        return stock;
    }

    private static Bodega bodega(int id, String nombre) {
        Bodega bodega = new Bodega();
        bodega.setId(id);
        bodega.setNombre(nombre);
        return bodega;
    }

    private static Factura facturaCon(Integer id, String numero, OffsetDateTime createdAt, OrdenLinea... lineas) {
        Orden orden = new Orden();
        orden.setId(77);
        orden.setNumeroOrden("ORD-77");
        Factura factura = new Factura();
        factura.setId(id);
        factura.setNumeroCorrelativo(numero);
        factura.setCreatedAt(createdAt);
        factura.setInvoiceTotal(new BigDecimal("999.00"));
        factura.setOrden(orden);
        // Every return needs a customer to credit; the anonymous placeholder is set explicitly by
        // the tests that exercise the identify-the-customer rule.
        factura.setCustomer(clienteIdentificado(1, "cliente@correo.com", "0.00"));
        factura.setNitCliente("1712345678");
        factura.setRazonSocial("Cliente Identificado");
        for (OrdenLinea linea : lineas) {
            linea.setOrden(orden);
            orden.getLineas().add(linea);
        }
        return factura;
    }

    private static OrdenLinea lineaVendida(int skuId, String sku, int cantidad, String precio) {
        CalzadoSku calzadoSku = new CalzadoSku();
        calzadoSku.setId(skuId);
        calzadoSku.setSku(sku);
        OrdenLinea linea = new OrdenLinea();
        linea.setCalzadoSku(calzadoSku);
        linea.setCantidad(cantidad);
        linea.setUnitPrice(new BigDecimal(precio));
        return linea;
    }

    private static Devolucion devolucionPrevia(String numero, int skuId, String sku, int cantidad) {
        Devolucion devolucion = new Devolucion();
        devolucion.setNumeroDevolucion(numero);
        DevolucionLinea linea = new DevolucionLinea();
        CalzadoSku calzadoSku = new CalzadoSku();
        calzadoSku.setId(skuId);
        calzadoSku.setSku(sku);
        linea.setCalzadoSku(calzadoSku);
        linea.setCantidad(cantidad);
        linea.setDevolucion(devolucion);
        devolucion.getLineas().add(linea);
        return devolucion;
    }

    /** A one-line, identified-customer return used by the anonymous-customer tests. */
    private static String devolucionJsonConEmail(String numeroFactura, String emailCliente) {
        return "{\"numeroFactura\":\"" + numeroFactura + "\","
            + "\"motivo\":\"Talla incorrecta\",\"suelaIntacta\":true,\"empaqueOriginal\":true,"
            + "\"emailCliente\":\"" + emailCliente + "\","
            + "\"lineas\":[{\"sku\":\"TV001-40\",\"cantidad\":1,\"danado\":false}]}";
    }

    private static String devolucionJson(String numeroFactura, String numeroOrden, String motivo,
            Boolean suelaIntacta, Boolean empaqueOriginal, String lineasJson) {
        StringBuilder sb = new StringBuilder("{");
        if (numeroFactura != null) {
            sb.append("\"numeroFactura\":\"").append(numeroFactura).append("\",");
        }
        if (numeroOrden != null) {
            sb.append("\"numeroOrden\":\"").append(numeroOrden).append("\",");
        }
        if (motivo != null) {
            sb.append("\"motivo\":\"").append(motivo).append("\",");
        }
        if (suelaIntacta != null) {
            sb.append("\"suelaIntacta\":").append(suelaIntacta).append(",");
        }
        if (empaqueOriginal != null) {
            sb.append("\"empaqueOriginal\":").append(empaqueOriginal).append(",");
        }
        sb.append("\"lineas\":").append(lineasJson).append("}");
        return sb.toString();
    }

    private static Customer clienteIdentificado(int id, String email, String saldo) {
        Customer customer = new Customer();
        customer.setId(id);
        customer.setEmail(email);
        customer.setTipoDocumento("CEDULA");
        customer.setNumeroDocumento("1712345678");
        customer.setNombreCompleto("Cliente Identificado");
        customer.setSaldoFavorWallet(new BigDecimal(saldo));
        return customer;
    }

    private static Customer consumidorFinalAnonimo() {
        Customer customer = new Customer();
        customer.setId(1);
        customer.setEmail("cf@calzadoempresa.com");
        customer.setTipoDocumento("CF");
        customer.setNumeroDocumento("CONSUMIDOR-FINAL");
        customer.setNombreCompleto("Consumidor Final Mostrador");
        customer.setSaldoFavorWallet(BigDecimal.ZERO);
        return customer;
    }

    private static SystemUser cajero(String username) {
        SystemUser cajero = new SystemUser();
        cajero.setId(1);
        cajero.setUsername(username);
        return cajero;
    }

    private static SesionCaja sesionAbierta(Integer bodegaId, SystemUser cajero) {
        return sesionConBodega(bodegaId, cajero);
    }

    /**
     * Stubs everything the happy path touches. All lenient because a rejected sale must stop before
     * the order, kardex and invoice writes, leaving some of these stubs unused by design.
     */
    private void stubVentaPosComun() {
        CalzadoSku sku = new CalzadoSku();
        sku.setId(10);
        sku.setSku("SKU-POS");
        sku.setPrecioVentaVigente(new BigDecimal("50.00"));
        lenient().when(skuRepository.findBySku("SKU-POS")).thenReturn(Optional.of(sku));

        Customer customer = new Customer();
        customer.setEmail("cf@calzadoempresa.com");
        lenient().when(customerRepository.findByEmail("cf@calzadoempresa.com")).thenReturn(Optional.of(customer));

        OrdenEstado pagada = new OrdenEstado();
        pagada.setCode("PAGADA");
        lenient().when(ordenEstadoRepository.findByCode("PAGADA")).thenReturn(Optional.of(pagada));

        lenient().when(ordenRepository.save(any(Orden.class))).thenAnswer(invocation -> {
            Orden orden = invocation.getArgument(0);
            if (orden.getId() == null) {
                orden.setId(99);
            }
            return orden;
        });

        MovimientoStockTipo salidaPos = new MovimientoStockTipo();
        salidaPos.setCode("SALIDA_VENTA_POS");
        lenient().when(movimientoTipoRepository.findByCode("SALIDA_VENTA_POS")).thenReturn(Optional.of(salidaPos));
        lenient().when(kardexRepository.save(any(KardexMovimiento.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FacturaTipo boleta = new FacturaTipo();
        boleta.setCode("BOLETA");
        lenient().when(facturaTipoRepository.findByCode("BOLETA")).thenReturn(Optional.of(boleta));
        FacturaEstado emitida = new FacturaEstado();
        emitida.setCode("EMITIDA");
        lenient().when(facturaEstadoRepository.findByCode("EMITIDA")).thenReturn(Optional.of(emitida));
        lenient().when(facturaRepository.save(any(Factura.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(numeracionFacturaService.siguiente(any(FacturaTipo.class))).thenReturn("TKT-000001");

        PagoMetodo efectivo = new PagoMetodo();
        efectivo.setCode("EFECTIVO");
        efectivo.setComisionPorcentaje(new BigDecimal("0.00"));
        efectivo.setComisionFija(new BigDecimal("0.00"));
        lenient().when(pagoMetodoRepository.findByCode("EFECTIVO")).thenReturn(Optional.of(efectivo));

        lenient().when(pagoTransaccionRepository.save(any(PagoTransaccion.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void stubSesionAbierta(Integer bodegaId, String username) {
        SystemUser cajero = new SystemUser();
        cajero.setId(1);
        cajero.setUsername(username);
        lenient().when(userRepository.findByUsername(username)).thenReturn(Optional.of(cajero));
        lenient().when(sesionCajaRepository.findFirstByCajeroIdAndEstado(1, "ABIERTA"))
            .thenReturn(Optional.of(sesionConBodega(bodegaId, cajero)));
    }

    private static SesionCaja sesionConBodega(Integer bodegaId, SystemUser cajero) {
        Bodega bodega = new Bodega();
        bodega.setId(bodegaId);
        bodega.setNombre("Bodega " + bodegaId);
        Sucursal sucursal = new Sucursal();
        sucursal.setId(11);
        sucursal.setNombre("Tienda Centro");
        sucursal.setBodega(bodega);
        CajaPos caja = new CajaPos();
        caja.setId(3);
        caja.setNumeroCaja("CAJA-01");
        caja.setSucursal(sucursal);
        SesionCaja sesion = new SesionCaja();
        sesion.setId(50);
        sesion.setCajero(cajero);
        sesion.setCajaPos(caja);
        sesion.setEstado("ABIERTA");
        sesion.setMontoApertura(new BigDecimal("100.00"));
        sesion.setFechaApertura(OffsetDateTime.now().minusHours(2));
        return sesion;
    }

    private static Authentication autenticacion(String username) {
        return new UsernamePasswordAuthenticationToken(username, null, List.of());
    }

    private static InventarioStock stockCon(Integer bodegaId, int disponible, int reservada) {
        Bodega bodega = new Bodega();
        bodega.setId(bodegaId);
        CalzadoSku sku = new CalzadoSku();
        sku.setId(10);
        InventarioStock stock = new InventarioStock();
        stock.setBodega(bodega);
        stock.setCalzadoSku(sku);
        stock.setCantidadDisponible(disponible);
        stock.setCantidadReservada(reservada);
        return stock;
    }
}
