package com.empresa.calzado.erp.ventas;

import com.empresa.calzado.erp.catalogo.CalzadoSku;
import com.empresa.calzado.erp.catalogo.CalzadoSkuRepository;
import com.empresa.calzado.erp.facturacion.Factura;
import com.empresa.calzado.erp.facturacion.FacturaEstado;
import com.empresa.calzado.erp.facturacion.FacturaEstadoRepository;
import com.empresa.calzado.erp.facturacion.FacturaLinea;
import com.empresa.calzado.erp.facturacion.FacturaRepository;
import com.empresa.calzado.erp.facturacion.FacturaTipo;
import com.empresa.calzado.erp.facturacion.FacturaTipoRepository;
import com.empresa.calzado.erp.facturacion.NumeracionFacturaService;
import com.empresa.calzado.erp.facturacion.PagoMetodo;
import com.empresa.calzado.erp.facturacion.PagoMetodoRepository;
import com.empresa.calzado.erp.facturacion.PagoTransaccion;
import com.empresa.calzado.erp.pos.PagoTransaccionRepository;
import com.empresa.calzado.erp.reportes.FacturaVentaPdfService;
import com.empresa.calzado.erp.inventario.Bodega;
import com.empresa.calzado.erp.inventario.InventarioService;
import com.empresa.calzado.erp.inventario.InventarioStock;
import com.empresa.calzado.erp.inventario.InventarioStockRepository;
import com.empresa.calzado.erp.logistica.DespachoEnvio;
import com.empresa.calzado.erp.logistica.DespachoEnvioRepository;
import com.empresa.calzado.erp.logistica.DespachoEstado;
import com.empresa.calzado.erp.logistica.DespachoEstadoRepository;
import com.empresa.calzado.erp.pos.Sucursal;
import com.empresa.calzado.erp.pos.SucursalRepository;
import com.empresa.calzado.erp.seguridad.SystemUser;
import com.empresa.calzado.erp.seguridad.SystemUserRepository;
import com.empresa.calzado.erp.ventas.dto.CotizacionEnvio;
import com.empresa.calzado.erp.ventas.dto.LineaCheckout;
import com.empresa.calzado.erp.ventas.dto.OrdenConsulta;
import com.empresa.calzado.erp.ventas.dto.OrdenConsultaLinea;
import com.empresa.calzado.erp.ventas.dto.SucursalRetiro;
import com.empresa.calzado.erp.ventas.dto.ValidacionCupon;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the shipping tariff resolution, coupon validation and coupon-aware checkout.
 *
 * <p>Repositories are mocked: the project intentionally has no H2 or Testcontainers, so these
 * tests target the service logic, not SQL execution.
 */
@ExtendWith(MockitoExtension.class)
class CheckoutServiceTest {

    @Mock private OrdenRepository ordenRepository;
    @Mock private OrdenEstadoRepository ordenEstadoRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private CalzadoSkuRepository skuRepository;
    @Mock private InventarioStockRepository inventarioStockRepository;
    @Mock private ReservaCheckoutRepository reservaRepository;
    @Mock private InventarioService inventarioService;
    @Mock private FacturaRepository facturaRepository;
    @Mock private FacturaTipoRepository facturaTipoRepository;
    @Mock private FacturaEstadoRepository facturaEstadoRepository;
    @Mock private PagoMetodoRepository pagoMetodoRepository;
    @Mock private DespachoEnvioRepository despachoRepository;
    @Mock private DespachoEstadoRepository despachoEstadoRepository;
    @Mock private SystemUserRepository userRepository;
    @Mock private TarifarioEnvioRepository tarifarioEnvioRepository;
    @Mock private CuponDescuentoRepository cuponDescuentoRepository;
    @Mock private SucursalRepository sucursalRepository;
    @Mock private AlertaDemandaRepository alertaDemandaRepository;
    @Mock private NumeracionFacturaService numeracionFacturaService;
    @Mock private FacturaVentaPdfService facturaVentaPdfService;
    @Mock private PagoTransaccionRepository pagoTransaccionRepository;

    @InjectMocks private CheckoutService service;

    // ---------------------------------------------------------------------
    // Zone resolution
    // ---------------------------------------------------------------------

    @Test
    void cotizaConElPrefijoExactoDeLaZona() {
        when(tarifarioEnvioRepository.findByActiveTrue()).thenReturn(List.of(
            zona("Metropolitana", "0901", "25.00", "4.50", "70.00"),
            zona("Nacional", "0902", "100.00", "8.50", "90.00")));

        CotizacionEnvio quote = service.cotizarEnvio("0901-1234", new BigDecimal("50.00"));

        assertThat(quote.zonaNombre()).isEqualTo("Metropolitana");
        assertThat(quote.costoEnvio()).isEqualByComparingTo("4.50");
        assertThat(quote.envioGratis()).isFalse();
        assertThat(quote.umbralCompraEnvioGratis()).isEqualByComparingTo("70.00");
    }

    @Test
    void prefiereElPrefijoMasLargoCuandoDosZonasCoinciden() {
        when(tarifarioEnvioRepository.findByActiveTrue()).thenReturn(List.of(
            zona("Base 090", "090", "50.00", "6.00", "80.00"),
            zona("Metropolitana", "0901", "25.00", "4.50", "70.00")));

        CotizacionEnvio quote = service.cotizarEnvio("0901-1234", new BigDecimal("50.00"));

        assertThat(quote.zonaNombre()).isEqualTo("Metropolitana");
        assertThat(quote.costoEnvio()).isEqualByComparingTo("4.50");
    }

    @Test
    void empataPrefijoYDesempataPorLaMenorDistancia() {
        when(tarifarioEnvioRepository.findByActiveTrue()).thenReturn(List.of(
            zona("Lejana", "090", "100.00", "9.00", "100.00"),
            zona("Cercana", "090", "50.00", "6.00", "80.00")));

        CotizacionEnvio quote = service.cotizarEnvio("0901-1234", new BigDecimal("50.00"));

        assertThat(quote.zonaNombre()).isEqualTo("Cercana");
        assertThat(quote.costoEnvio()).isEqualByComparingTo("6.00");
    }

    @Test
    void sinCoincidenciaUsaLaZonaActivaDeMenorDistancia() {
        when(tarifarioEnvioRepository.findByActiveTrue()).thenReturn(List.of(
            zona("Nacional", "0902", "500.00", "14.00", "120.00"),
            zona("Metropolitana", "0901", "25.00", "4.50", "70.00")));

        CotizacionEnvio quote = service.cotizarEnvio("9999", new BigDecimal("50.00"));

        assertThat(quote.zonaNombre()).isEqualTo("Metropolitana");
        assertThat(quote.costoEnvio()).isEqualByComparingTo("4.50");
    }

    @Test
    void sinTarifasActivasCaeAlDefaultLegacy() {
        when(tarifarioEnvioRepository.findByActiveTrue()).thenReturn(List.of());

        CotizacionEnvio quote = service.cotizarEnvio("0901", new BigDecimal("50.00"));

        assertThat(quote.zonaNombre()).isNotBlank();
        assertThat(quote.costoEnvio()).isEqualByComparingTo("4.50");
        assertThat(quote.umbralCompraEnvioGratis()).isEqualByComparingTo("70.00");
        assertThat(quote.envioGratis()).isFalse();
    }

    @Test
    void elUmbralExactoDaEnvioGratis() {
        when(tarifarioEnvioRepository.findByActiveTrue()).thenReturn(List.of(
            zona("Metropolitana", "0901", "25.00", "4.50", "70.00")));

        CotizacionEnvio quote = service.cotizarEnvio("0901", new BigDecimal("70.00"));

        assertThat(quote.envioGratis()).isTrue();
        assertThat(quote.costoEnvio()).isEqualByComparingTo("0.00");
    }

    @Test
    void unCentavoBajoElUmbralCobraFlete() {
        when(tarifarioEnvioRepository.findByActiveTrue()).thenReturn(List.of(
            zona("Metropolitana", "0901", "25.00", "4.50", "70.00")));

        CotizacionEnvio quote = service.cotizarEnvio("0901", new BigDecimal("69.99"));

        assertThat(quote.envioGratis()).isFalse();
        assertThat(quote.costoEnvio()).isEqualByComparingTo("4.50");
    }

    // ---------------------------------------------------------------------
    // Coupon validation
    // ---------------------------------------------------------------------

    @Test
    void descuentoPorcentual() {
        CuponDescuento cupon = cupon(1, "BIENVENIDA10", "PORCENTAJE", "10.00", "40.00", 500, 0,
            now().minusDays(1), now().plusDays(30));
        when(cuponDescuentoRepository.findByCodigoCuponIgnoreCaseAndActiveTrue("BIENVENIDA10"))
            .thenReturn(Optional.of(cupon));

        ValidacionCupon result = service.validarCupon("BIENVENIDA10", new BigDecimal("100.00"));

        assertThat(result.valido()).isTrue();
        assertThat(result.codigoCupon()).isEqualTo("BIENVENIDA10");
        assertThat(result.tipoDescuento()).isEqualTo("PORCENTAJE");
        assertThat(result.descuento()).isEqualByComparingTo("10.00");
        assertThat(result.mensaje()).isNull();
    }

    @Test
    void descuentoMontoFijo() {
        CuponDescuento cupon = cupon(2, "OUTLET50", "MONTO_FIJO", "15.00", "80.00", 100, 0,
            now().minusDays(1), now().plusDays(30));
        when(cuponDescuentoRepository.findByCodigoCuponIgnoreCaseAndActiveTrue("OUTLET50"))
            .thenReturn(Optional.of(cupon));

        ValidacionCupon result = service.validarCupon("OUTLET50", new BigDecimal("100.00"));

        assertThat(result.valido()).isTrue();
        assertThat(result.tipoDescuento()).isEqualTo("MONTO_FIJO");
        assertThat(result.descuento()).isEqualByComparingTo("15.00");
    }

    @Test
    void elDescuentoNoSuperaElSubtotal() {
        CuponDescuento cupon = cupon(3, "GRANDE", "MONTO_FIJO", "50.00", "0.00", 100, 0,
            now().minusDays(1), now().plusDays(30));
        when(cuponDescuentoRepository.findByCodigoCuponIgnoreCaseAndActiveTrue("GRANDE"))
            .thenReturn(Optional.of(cupon));

        ValidacionCupon result = service.validarCupon("GRANDE", new BigDecimal("30.00"));

        assertThat(result.valido()).isTrue();
        assertThat(result.descuento()).isEqualByComparingTo("30.00");
    }

    @Test
    void cuponVencido() {
        CuponDescuento cupon = cupon(4, "VIEJO", "PORCENTAJE", "10.00", "0.00", 100, 0,
            now().minusDays(30), now().minusDays(1));
        when(cuponDescuentoRepository.findByCodigoCuponIgnoreCaseAndActiveTrue("VIEJO"))
            .thenReturn(Optional.of(cupon));

        ValidacionCupon result = service.validarCupon("VIEJO", new BigDecimal("100.00"));

        assertThat(result.valido()).isFalse();
        assertThat(result.descuento()).isEqualByComparingTo("0.00");
        assertThat(result.mensaje()).contains("vigente");
    }

    @Test
    void cuponInactivo() {
        when(cuponDescuentoRepository.findByCodigoCuponIgnoreCaseAndActiveTrue("INACTIVO"))
            .thenReturn(Optional.empty());

        ValidacionCupon result = service.validarCupon("INACTIVO", new BigDecimal("100.00"));

        assertThat(result.valido()).isFalse();
        assertThat(result.mensaje()).isNotBlank();
    }

    @Test
    void cuponBajoElMinimoDeCompra() {
        CuponDescuento cupon = cupon(5, "MINIMO", "PORCENTAJE", "10.00", "80.00", 100, 0,
            now().minusDays(1), now().plusDays(30));
        when(cuponDescuentoRepository.findByCodigoCuponIgnoreCaseAndActiveTrue("MINIMO"))
            .thenReturn(Optional.of(cupon));

        ValidacionCupon result = service.validarCupon("MINIMO", new BigDecimal("50.00"));

        assertThat(result.valido()).isFalse();
        assertThat(result.mensaje()).contains("minimo");
    }

    @Test
    void cuponSinUsosDisponibles() {
        CuponDescuento cupon = cupon(6, "AGOTADO", "PORCENTAJE", "10.00", "0.00", 100, 100,
            now().minusDays(1), now().plusDays(30));
        when(cuponDescuentoRepository.findByCodigoCuponIgnoreCaseAndActiveTrue("AGOTADO"))
            .thenReturn(Optional.of(cupon));

        ValidacionCupon result = service.validarCupon("AGOTADO", new BigDecimal("100.00"));

        assertThat(result.valido()).isFalse();
        assertThat(result.mensaje()).contains("limite");
    }

    @Test
    void cuponDesconocido() {
        when(cuponDescuentoRepository.findByCodigoCuponIgnoreCaseAndActiveTrue("NOEXISTE"))
            .thenReturn(Optional.empty());

        ValidacionCupon result = service.validarCupon("NOEXISTE", new BigDecimal("100.00"));

        assertThat(result.valido()).isFalse();
        assertThat(result.codigoCupon()).isEqualTo("NOEXISTE");
        assertThat(result.descuento()).isEqualByComparingTo("0.00");
        assertThat(result.mensaje()).isNotBlank();
    }

    @Test
    void laBusquedaIgnoraMayusculasYDevuelveElCodigoCanonico() {
        CuponDescuento cupon = cupon(7, "BIENVENIDA10", "PORCENTAJE", "10.00", "40.00", 500, 0,
            now().minusDays(1), now().plusDays(30));
        when(cuponDescuentoRepository.findByCodigoCuponIgnoreCaseAndActiveTrue("bienvenida10"))
            .thenReturn(Optional.of(cupon));

        ValidacionCupon result = service.validarCupon("bienvenida10", new BigDecimal("100.00"));

        assertThat(result.valido()).isTrue();
        assertThat(result.codigoCupon()).isEqualTo("BIENVENIDA10");
    }

    // ---------------------------------------------------------------------
    // Coupon-aware checkout
    // ---------------------------------------------------------------------

    @Test
    void iniciarConCuponValidoGuardaElDescuentoYElTotal() {
        stubCreacion("SKU-1", "100.00");
        CuponDescuento cupon = cupon(9, "BIENVENIDA10", "PORCENTAJE", "10.00", "40.00", 500, 0,
            now().minusDays(1), now().plusDays(30));
        when(cuponDescuentoRepository.findByCodigoCuponIgnoreCaseAndActiveTrue("BIENVENIDA10"))
            .thenReturn(Optional.of(cupon));

        Orden orden = service.crearOrdenConReserva2Horas(
            "cliente@test.com", "Cliente Test", "SKU-1", 1, "BIENVENIDA10", null);

        assertThat(orden.getDescuentoAplicado()).isEqualByComparingTo("10.00");
        assertThat(orden.getCupon()).isNotNull();
        assertThat(orden.getCupon().getId()).isEqualTo(9);
        assertThat(orden.getCostoEnvio()).isEqualByComparingTo("0.00");
        // 100.00 subtotal + 0.00 free shipping + 3.80 commission - 10.00 discount
        assertThat(orden.getTotalAPagar()).isEqualByComparingTo("93.80");
    }

    @Test
    void iniciarConCuponInvalidoFallaYNoGuardaLaOrden() {
        stubLookupsCreacion("SKU-1", "100.00");
        when(cuponDescuentoRepository.findByCodigoCuponIgnoreCaseAndActiveTrue("MALO"))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.crearOrdenConReserva2Horas(
                "cliente@test.com", "Cliente Test", "SKU-1", 1, "MALO", null))
            .isInstanceOf(CheckoutBusinessException.class)
            .hasMessageContaining("cupon");

        verify(ordenRepository, never()).save(any(Orden.class));
    }

    @Test
    void iniciarSinCuponConservaElComportamientoPrevio() {
        stubCreacion("SKU-1", "100.00");

        Orden orden = service.crearOrdenConReserva2Horas(
            "cliente@test.com", "Cliente Test", "SKU-1", 1, null, null);

        assertThat(orden.getDescuentoAplicado()).isEqualByComparingTo("0.00");
        assertThat(orden.getCupon()).isNull();
        assertThat(orden.getCostoEnvio()).isEqualByComparingTo("0.00");
        assertThat(orden.getComisionPasarela()).isEqualByComparingTo("3.80");
        assertThat(orden.getTotalAPagar()).isEqualByComparingTo("103.80");
    }

    @Test
    void elTotalNuncaEsNegativo() {
        stubCreacion("SKU-1", "10.00");
        CuponDescuento cupon = cupon(11, "GRANDE", "MONTO_FIJO", "100.00", "0.00", 100, 0,
            now().minusDays(1), now().plusDays(30));
        when(cuponDescuentoRepository.findByCodigoCuponIgnoreCaseAndActiveTrue("GRANDE"))
            .thenReturn(Optional.of(cupon));

        Orden orden = service.crearOrdenConReserva2Horas(
            "cliente@test.com", "Cliente Test", "SKU-1", 1, "GRANDE", null);

        // The discount is capped at the subtotal, so it can never push the total below zero.
        assertThat(orden.getDescuentoAplicado()).isEqualByComparingTo("10.00");
        assertThat(orden.getTotalAPagar()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        // 10.00 + 4.50 shipping + 0.65 commission - 10.00
        assertThat(orden.getTotalAPagar()).isEqualByComparingTo("5.15");
    }

    @Test
    void elUmbralDeEnvioGratisSeEvaluaAntesDelDescuento() {
        stubCreacion("SKU-1", "70.00");
        CuponDescuento cupon = cupon(12, "DESCUENTO", "MONTO_FIJO", "15.00", "0.00", 100, 0,
            now().minusDays(1), now().plusDays(30));
        when(cuponDescuentoRepository.findByCodigoCuponIgnoreCaseAndActiveTrue("DESCUENTO"))
            .thenReturn(Optional.of(cupon));

        Orden orden = service.crearOrdenConReserva2Horas(
            "cliente@test.com", "Cliente Test", "SKU-1", 1, "DESCUENTO", null);

        // The 70.00 subtotal meets the threshold before the 15.00 discount is applied.
        assertThat(orden.getCostoEnvio()).isEqualByComparingTo("0.00");
        assertThat(orden.getDescuentoAplicado()).isEqualByComparingTo("15.00");
        // 70.00 + 0.00 + 2.75 commission - 15.00
        assertThat(orden.getTotalAPagar()).isEqualByComparingTo("57.75");
    }

    @Test
    void elCostoCobradoCoincideConElCotizadoParaLaZona0902() {
        stubTarifarioCompleto();
        stubCreacion("SKU-1", "50.00");

        CotizacionEnvio cotizado = service.cotizarEnvio("0902", new BigDecimal("50.00"));
        Orden orden = service.crearOrdenConReserva2Horas(
            "cliente@test.com", "Cliente Test", "SKU-1", 1, null, "0902");

        assertThat(cotizado.costoEnvio()).isEqualByComparingTo("8.50");
        assertThat(orden.getCostoEnvio()).isEqualByComparingTo(cotizado.costoEnvio());
    }

    @Test
    void elCostoCobradoCoincideConElCotizadoParaLaZona0903() {
        stubTarifarioCompleto();
        stubCreacion("SKU-1", "50.00");

        CotizacionEnvio cotizado = service.cotizarEnvio("0903", new BigDecimal("50.00"));
        Orden orden = service.crearOrdenConReserva2Horas(
            "cliente@test.com", "Cliente Test", "SKU-1", 1, null, "0903");

        assertThat(cotizado.costoEnvio()).isEqualByComparingTo("14.00");
        assertThat(orden.getCostoEnvio()).isEqualByComparingTo(cotizado.costoEnvio());
    }

    @Test
    void elUmbralAplicadoEsElDeLaZonaDelCodigoPostalNoElDeLaZonaBase() {
        stubTarifarioCompleto();
        stubCreacion("SKU-1", "80.00");

        // 80.00 is above the base zone threshold (70) but below the 0902 threshold (90), so the
        // postal code's zone must charge 8.50 instead of inheriting the base zone's free shipping.
        CotizacionEnvio cotizado = service.cotizarEnvio("0902", new BigDecimal("80.00"));
        Orden orden = service.crearOrdenConReserva2Horas(
            "cliente@test.com", "Cliente Test", "SKU-1", 1, null, "0902");

        assertThat(cotizado.envioGratis()).isFalse();
        assertThat(cotizado.costoEnvio()).isEqualByComparingTo("8.50");
        assertThat(orden.getCostoEnvio()).isEqualByComparingTo("8.50");
    }

    @Test
    void sinCodigoPostalUsaLaZonaBaseComoHoy() {
        stubTarifarioCompleto();
        stubCreacion("SKU-1", "50.00");

        Orden orden = service.crearOrdenConReserva2Horas(
            "cliente@test.com", "Cliente Test", "SKU-1", 1, null, null);

        assertThat(orden.getCostoEnvio()).isEqualByComparingTo("4.50");
    }

    @Test
    void losUsosNoSeIncrementanAlCrearLaOrden() {
        stubCreacion("SKU-1", "100.00");
        CuponDescuento cupon = cupon(13, "BIENVENIDA10", "PORCENTAJE", "10.00", "40.00", 500, 0,
            now().minusDays(1), now().plusDays(30));
        when(cuponDescuentoRepository.findByCodigoCuponIgnoreCaseAndActiveTrue("BIENVENIDA10"))
            .thenReturn(Optional.of(cupon));

        Orden orden = service.crearOrdenConReserva2Horas(
            "cliente@test.com", "Cliente Test", "SKU-1", 1, "BIENVENIDA10", null);

        assertThat(orden.getCupon().getUsosActuales()).isZero();
        verify(cuponDescuentoRepository, never()).save(any(CuponDescuento.class));
    }

    @Test
    void losUsosSeIncrementanAlPagar() {
        CuponDescuento cupon = cupon(14, "BIENVENIDA10", "PORCENTAJE", "10.00", "40.00", 500, 0,
            now().minusDays(1), now().plusDays(30));
        Orden orden = ordenConCupon(cupon);
        stubPago(orden);

        service.procesarPagoSimuladoYGenerarOtp("ORD-TEST", "TARJETA_SIMULADA");

        assertThat(cupon.getUsosActuales()).isEqualTo(1);
        verify(cuponDescuentoRepository).save(cupon);
    }

    // ---------------------------------------------------------------------
    // Web invoice document (RN-FIN-01) and web payment reconciliation (RN-FIN-02)
    // ---------------------------------------------------------------------

    @Test
    void laFacturaWebEscribeLasLineasDeLaOrden() {
        Orden orden = ordenConCupon(null);
        stubPago(orden);

        service.procesarPagoSimuladoYGenerarOtp("ORD-TEST", "TARJETA_SIMULADA");

        Factura factura = facturaCapturada();
        assertThat(factura.getLineas()).hasSize(1);
        FacturaLinea linea = factura.getLineas().get(0);
        assertThat(linea.getFactura()).isSameAs(factura);
        assertThat(linea.getCalzadoSku()).isSameAs(orden.getLineas().get(0).getCalzadoSku());
        assertThat(linea.getCantidad()).isEqualTo(1);
        assertThat(linea.getUnitPrice()).isEqualByComparingTo("100.00");
        assertThat(linea.getLineTotal()).isEqualByComparingTo("100.00");
    }

    @Test
    void laFacturaWebUsaLaConvencionDeIvaCompartidaYLaIdentidadSeCumple() {
        Orden orden = ordenConCupon(null);
        stubPago(orden);

        service.procesarPagoSimuladoYGenerarOtp("ORD-TEST", "TARJETA_SIMULADA");

        // The order total is 93.80; 93.80 / 1.12 = 83.75 exact, so the tax is the 10.05 remainder.
        Factura factura = facturaCapturada();
        assertThat(factura.getInvoiceTotal()).isEqualByComparingTo("93.80");
        assertThat(factura.getMontoSubtotal()).isEqualByComparingTo("83.75");
        assertThat(factura.getMontoIva()).isEqualByComparingTo("10.05");
        assertThat(factura.getMontoSubtotal().add(factura.getMontoIva()))
            .isEqualByComparingTo(factura.getInvoiceTotal());
        assertThat(factura.getMontoRecargoPasarela()).isEqualByComparingTo("3.80");
    }

    @Test
    void laFacturaWebUsaElCorrelativoSecuencialYLaUrlDelEscritor() {
        Orden orden = ordenConCupon(null);
        stubPago(orden);

        Factura factura = service.procesarPagoSimuladoYGenerarOtp("ORD-TEST", "TARJETA_SIMULADA");

        assertThat(factura.getNumeroCorrelativo()).isEqualTo("FAC-000001");
        assertThat(factura.getPdfUrl()).isEqualTo("/storage/facturas/FAC-000001.pdf");
        verify(numeracionFacturaService).siguiente(any(FacturaTipo.class));
        verify(facturaVentaPdfService).emitir(factura);
    }

    @Test
    void elPagoWebRegistraBrutoRecargoYComisionRetenida() {
        Orden orden = ordenConCupon(null);
        stubPago(orden);

        service.procesarPagoSimuladoYGenerarOtp("ORD-TEST", "TARJETA_SIMULADA");

        ArgumentCaptor<PagoTransaccion> captor = ArgumentCaptor.forClass(PagoTransaccion.class);
        verify(pagoTransaccionRepository).save(captor.capture());
        PagoTransaccion pago = captor.getValue();
        assertThat(pago.getFactura()).isSameAs(facturaCapturada());
        assertThat(pago.getPagoMetodo().getCode()).isEqualTo("TARJETA_SIMULADA");
        assertThat(pago.getEstadoPago()).isEqualTo("APROBADO");
        // Gross is the invoice total; the retained commission comes from the payment method
        // (93.80 * 3.50 / 100 + 0.30 = 3.58) and the net is the difference.
        assertThat(pago.getMontoBruto()).isEqualByComparingTo("93.80");
        assertThat(pago.getComisionRetenida()).isEqualByComparingTo("3.58");
        assertThat(pago.getMontoNetoBancario()).isEqualByComparingTo("90.22");
        assertThat(pago.getReferenciaTransaccion()).startsWith("SIM-TX-");
    }

    private Factura facturaCapturada() {
        ArgumentCaptor<Factura> captor = ArgumentCaptor.forClass(Factura.class);
        verify(facturaRepository).save(captor.capture());
        return captor.getValue();
    }

    // ---------------------------------------------------------------------
    // Warehouse selection (defect: stock only outside the first web warehouse)
    // ---------------------------------------------------------------------

    @Test
    void elSkuConStockSoloEnLaSegundaBodegaWebSeCompraDeEsaBodega() {
        // Reproduces the live defect: checkout pinned the first web-dispatchable warehouse
        // (id 1 here), but this SKU only has an inventory row in warehouse 3.
        stubLookupsCreacion("SKU-TV002", "100.00");
        lenient().when(inventarioStockRepository.findStockDespachableWebPorSku(10, 1))
            .thenReturn(List.of(stockEnBodega(bodega(3))));
        when(ordenRepository.save(any(Orden.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reservaRepository.save(any(ReservaCheckout.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Orden orden = service.crearOrdenConReserva2Horas(
            "cliente@test.com", "Cliente Test", "SKU-TV002", 1, null, null);

        assertThat(orden.getBodegaDespacho().getId()).isEqualTo(3);
        verify(inventarioService).bloquearStockPesimista(3, 10, 1);
    }

    @Test
    void sinStockEnNingunaBodegaWebEsUnErrorDeNegocio() {
        stubLookupsCreacion("SKU-SIN-STOCK", "100.00");
        lenient().when(inventarioStockRepository.findStockDespachableWebPorSku(eq(10), anyInt()))
            .thenReturn(List.of());
        // Lenient: only reached while the defect is present; the fix throws before persisting.
        lenient().when(ordenRepository.save(any(Orden.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(reservaRepository.save(any(ReservaCheckout.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> service.crearOrdenConReserva2Horas(
                "cliente@test.com", "Cliente Test", "SKU-SIN-STOCK", 1, null, null))
            .isInstanceOf(CheckoutBusinessException.class)
            .hasMessage("No hay stock disponible para el SKU solicitado");

        verify(ordenRepository, never()).save(any(Orden.class));
        verify(reservaRepository, never()).save(any(ReservaCheckout.class));
    }

    // ---------------------------------------------------------------------
    // Multi-line cart checkout
    // ---------------------------------------------------------------------

    @Test
    void unaOrdenDeDosLineasCreaDosLineasConSubtotalSumado() {
        stubCreacionMultiLinea();
        stubSkuConStock(10, "SKU-A", "100.00", bodega(1));
        stubSkuConStock(11, "SKU-B", "50.00", bodega(1));

        Orden orden = service.crearOrdenConReserva2Horas(
            "cliente@test.com", "Cliente Test", null, 0, null, null, null, null,
            List.of(new LineaCheckout("SKU-A", 2), new LineaCheckout("SKU-B", 1)));

        assertThat(orden.getLineas()).hasSize(2);
        OrdenLinea primera = orden.getLineas().get(0);
        assertThat(primera.getCalzadoSku().getSku()).isEqualTo("SKU-A");
        assertThat(primera.getCantidad()).isEqualTo(2);
        assertThat(primera.getUnitPrice()).isEqualByComparingTo("100.00");
        assertThat(primera.getLineTotal()).isEqualByComparingTo("200.00");
        assertThat(primera.getDescuentoUnitario()).isEqualByComparingTo("0.00");
        OrdenLinea segunda = orden.getLineas().get(1);
        assertThat(segunda.getCalzadoSku().getSku()).isEqualTo("SKU-B");
        assertThat(segunda.getCantidad()).isEqualTo(1);
        assertThat(segunda.getLineTotal()).isEqualByComparingTo("50.00");
        assertThat(orden.getSubtotalProductos()).isEqualByComparingTo("250.00");
        assertThat(orden.getBodegaDespacho().getId()).isEqualTo(1);
    }

    @Test
    void laFormaDeUnSoloSkuSigueCreandoUnaSolaLinea() {
        stubCreacion("SKU-1", "50.00");

        Orden orden = service.crearOrdenConReserva2Horas(
            "cliente@test.com", "Cliente Test", "SKU-1", 2, null, null);

        assertThat(orden.getLineas()).hasSize(1);
        assertThat(orden.getLineas().get(0).getCantidad()).isEqualTo(2);
        assertThat(orden.getLineas().get(0).getLineTotal()).isEqualByComparingTo("100.00");
        assertThat(orden.getSubtotalProductos()).isEqualByComparingTo("100.00");
    }

    @Test
    void unaListaDeLineasVaciaEsUnErrorDeNegocio() {
        assertThatThrownBy(() -> service.crearOrdenConReserva2Horas(
                "cliente@test.com", "Cliente Test", null, 0, null, null, null, null, List.of()))
            .isInstanceOf(CheckoutBusinessException.class)
            .hasMessage("La lista de lineas no puede estar vacia");

        verify(ordenRepository, never()).save(any(Orden.class));
        verify(reservaRepository, never()).save(any(ReservaCheckout.class));
    }

    @Test
    void unaCantidadCeroONegativaEsUnErrorDeNegocio() {
        assertThatThrownBy(() -> service.crearOrdenConReserva2Horas(
                "cliente@test.com", "Cliente Test", null, 0, null, null, null, null,
                List.of(new LineaCheckout("SKU-A", 0))))
            .isInstanceOf(CheckoutBusinessException.class)
            .hasMessage("La cantidad de cada linea debe ser mayor a cero");

        assertThatThrownBy(() -> service.crearOrdenConReserva2Horas(
                "cliente@test.com", "Cliente Test", null, 0, null, null, null, null,
                List.of(new LineaCheckout("SKU-A", -3))))
            .isInstanceOf(CheckoutBusinessException.class)
            .hasMessage("La cantidad de cada linea debe ser mayor a cero");

        verify(ordenRepository, never()).save(any(Orden.class));
    }

    @Test
    void unSkuRepetidoEsRechazado() {
        assertThatThrownBy(() -> service.crearOrdenConReserva2Horas(
                "cliente@test.com", "Cliente Test", null, 0, null, null, null, null,
                List.of(new LineaCheckout("SKU-A", 1), new LineaCheckout("SKU-A", 2))))
            .isInstanceOf(CheckoutBusinessException.class)
            .hasMessage("El SKU SKU-A esta repetido en la orden");

        verify(ordenRepository, never()).save(any(Orden.class));
    }

    @Test
    void unSkuDesconocidoEsCuatrocientosYNoCreaNada() {
        stubCreacionMultiLinea();
        stubSkuConStock(10, "SKU-A", "100.00", bodega(1));
        when(skuRepository.findBySku("NO-EXISTE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.crearOrdenConReserva2Horas(
                "cliente@test.com", "Cliente Test", null, 0, null, null, null, null,
                List.of(new LineaCheckout("SKU-A", 1), new LineaCheckout("NO-EXISTE", 1))))
            .isInstanceOf(CheckoutBusinessException.class)
            .hasMessage("El SKU NO-EXISTE no existe");

        verify(ordenRepository, never()).save(any(Orden.class));
        verify(reservaRepository, never()).save(any(ReservaCheckout.class));
    }

    @Test
    void elCuponAplicaAlSubtotalMultiLineaYElMinimoSeEvaluaSobreEl() {
        stubCreacionMultiLinea();
        stubSkuConStock(10, "SKU-A", "40.00", bodega(1));
        stubSkuConStock(11, "SKU-B", "40.00", bodega(1));
        CuponDescuento cupon = cupon(30, "MITAD", "PORCENTAJE", "10.00", "80.00", 100, 0,
            now().minusDays(1), now().plusDays(30));
        when(cuponDescuentoRepository.findByCodigoCuponIgnoreCaseAndActiveTrue("MITAD"))
            .thenReturn(Optional.of(cupon));

        Orden orden = service.crearOrdenConReserva2Horas(
            "cliente@test.com", "Cliente Test", null, 0, "MITAD", null, null, null,
            List.of(new LineaCheckout("SKU-A", 1), new LineaCheckout("SKU-B", 1)));

        // The 80.00 minimum is met only by the sum of both lines; per line (40.00) it would be
        // rejected.
        assertThat(orden.getSubtotalProductos()).isEqualByComparingTo("80.00");
        assertThat(orden.getDescuentoAplicado()).isEqualByComparingTo("8.00");
        assertThat(orden.getCupon()).isNotNull();
    }

    @Test
    void unaOrdenMultiLineaSinBodegaComunNombraLosItemsYNoCreaNada() {
        stubCreacionMultiLinea();
        stubSkuConStock(10, "SKU-A", "10.00", bodega(1));
        stubSkuConStock(11, "SKU-B", "10.00", bodega(1));
        stubSkuConStock(12, "SKU-C", "10.00", bodega(2));

        assertThatThrownBy(() -> service.crearOrdenConReserva2Horas(
                "cliente@test.com", "Cliente Test", null, 0, null, null, null, null,
                List.of(new LineaCheckout("SKU-A", 1),
                        new LineaCheckout("SKU-B", 1),
                        new LineaCheckout("SKU-C", 1))))
            .isInstanceOf(CheckoutBusinessException.class)
            .hasMessage("No hay una bodega con stock suficiente para servir juntos los SKU: SKU-A, SKU-B, SKU-C");

        verify(ordenRepository, never()).save(any(Orden.class));
        verify(reservaRepository, never()).save(any(ReservaCheckout.class));
        verify(inventarioService, never()).bloquearStockPesimista(anyInt(), anyInt(), anyInt());
    }

    @Test
    void elEnvioGratisSeAlcanzaConLaSumaDeLasLineas() {
        stubTarifarioCompleto();
        stubCreacionMultiLinea();
        stubSkuConStock(10, "SKU-A", "40.00", bodega(1));
        stubSkuConStock(11, "SKU-B", "40.00", bodega(1));

        Orden orden = service.crearOrdenConReserva2Horas(
            "cliente@test.com", "Cliente Test", null, 0, null, "0901", null, null,
            List.of(new LineaCheckout("SKU-A", 1), new LineaCheckout("SKU-B", 1)));

        // The 0901 threshold is 70.00; each line alone (40.00) is below it.
        assertThat(orden.getSubtotalProductos()).isEqualByComparingTo("80.00");
        assertThat(orden.getCostoEnvio()).isEqualByComparingTo("0.00");
    }

    @Test
    void cadaLineaTieneSuPropiaReservaEnLaMismaBodega() {
        stubCreacionMultiLinea();
        stubSkuConStock(10, "SKU-A", "100.00", bodega(1));
        stubSkuConStock(11, "SKU-B", "50.00", bodega(1));

        service.crearOrdenConReserva2Horas(
            "cliente@test.com", "Cliente Test", null, 0, null, null, null, null,
            List.of(new LineaCheckout("SKU-A", 2), new LineaCheckout("SKU-B", 1)));

        ArgumentCaptor<ReservaCheckout> captor = ArgumentCaptor.forClass(ReservaCheckout.class);
        verify(reservaRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(r -> r.getCalzadoSku().getSku())
            .containsExactly("SKU-A", "SKU-B");
        assertThat(captor.getAllValues()).allSatisfy(r -> {
            assertThat(r.getBodega().getId()).isEqualTo(1);
            assertThat(r.getEstado()).isEqualTo("ACTIVA");
        });
        verify(inventarioService).bloquearStockPesimista(1, 10, 2);
        verify(inventarioService).bloquearStockPesimista(1, 11, 1);
    }

    // ---------------------------------------------------------------------
    // Click & collect (pickup)
    // ---------------------------------------------------------------------

    @Test
    void elRetiroEnTiendaFuerzaElTipoDeEntregaYBonificaElFlete() {
        stubCreacion("SKU-1", "50.00");
        lenient().when(sucursalRepository.findById(5)).thenReturn(Optional.of(sucursal(5, "Tienda Centro")));

        Orden orden = service.crearOrdenConReserva2Horas(
            "cliente@test.com", "Cliente Test", "SKU-1", 1, null, null,
            "RETIRO_TIENDA_CLICK_COLLECT", 5);

        assertThat(orden.getTipoEntrega()).isEqualTo("RETIRO_TIENDA_CLICK_COLLECT");
        assertThat(orden.getCostoEnvio()).isEqualByComparingTo("0.00");
        assertThat(orden.getSucursalRetiro()).isNotNull();
        assertThat(orden.getSucursalRetiro().getId()).isEqualTo(5);
    }

    @Test
    void elRetiroEnTiendaIgnoraElCodigoPostalYNoConsultaElTarifario() {
        stubCreacion("SKU-1", "10.00");
        lenient().when(sucursalRepository.findById(5)).thenReturn(Optional.of(sucursal(5, "Tienda Centro")));

        Orden orden = service.crearOrdenConReserva2Horas(
            "cliente@test.com", "Cliente Test", "SKU-1", 1, null, "0902",
            "RETIRO_TIENDA_CLICK_COLLECT", 5);

        // 10.00 is below any free-shipping threshold, so a tariff lookup would have charged a fee.
        assertThat(orden.getCostoEnvio()).isEqualByComparingTo("0.00");
        verify(tarifarioEnvioRepository, never()).findByActiveTrue();
    }

    @Test
    void elRetiroSinSucursalEsUnErrorDeNegocio() {
        stubLookupsCreacion("SKU-1", "50.00");
        lenient().when(ordenRepository.save(any(Orden.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> service.crearOrdenConReserva2Horas(
                "cliente@test.com", "Cliente Test", "SKU-1", 1, null, null,
                "RETIRO_TIENDA_CLICK_COLLECT", null))
            .isInstanceOf(CheckoutBusinessException.class)
            .hasMessage("El parametro idSucursalRetiro es obligatorio para retiro en tienda");

        verify(ordenRepository, never()).save(any(Orden.class));
        verify(inventarioService, never()).bloquearStockPesimista(anyInt(), anyInt(), anyInt());
    }

    @Test
    void elRetiroConSucursalDesconocidaEsUnErrorDeNegocio() {
        stubLookupsCreacion("SKU-1", "50.00");
        lenient().when(sucursalRepository.findById(99)).thenReturn(Optional.empty());
        lenient().when(ordenRepository.save(any(Orden.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> service.crearOrdenConReserva2Horas(
                "cliente@test.com", "Cliente Test", "SKU-1", 1, null, null,
                "RETIRO_TIENDA_CLICK_COLLECT", 99))
            .isInstanceOf(CheckoutBusinessException.class)
            .hasMessage("La sucursal de retiro no existe o no esta activa");

        verify(ordenRepository, never()).save(any(Orden.class));
    }

    @Test
    void elTipoDeEntregaAusenteConservaElEnvioADomicilio() {
        stubCreacion("SKU-1", "50.00");

        Orden orden = service.crearOrdenConReserva2Horas(
            "cliente@test.com", "Cliente Test", "SKU-1", 1, null, null, null, null);

        assertThat(orden.getTipoEntrega()).isEqualTo("ENVIO_DOMICILIO");
        assertThat(orden.getSucursalRetiro()).isNull();
        assertThat(orden.getCostoEnvio()).isEqualByComparingTo("4.50");
    }

    @Test
    void listarSucursalesParaRetiroDevuelveSoloIdYNombre() {
        when(sucursalRepository.findByActiveTrueOrderByIdAsc()).thenReturn(List.of(
            sucursal(1, "Tienda Centro"), sucursal(2, "Tienda Mall")));

        List<SucursalRetiro> sucursales = service.listarSucursalesParaRetiro();

        assertThat(sucursales).containsExactly(
            new SucursalRetiro(1, "Tienda Centro"),
            new SucursalRetiro(2, "Tienda Mall"));
    }

    // ---------------------------------------------------------------------
    // Reservation lifecycle (defect: a paid order released its stock two hours later)
    // ---------------------------------------------------------------------

    @Test
    void alPagarLaOrdenSusReservasDejanDeEstarActivas() {
        Orden orden = ordenConCupon(null);
        ReservaCheckout reserva = reservaDe(orden, "ACTIVA");
        stubPago(orden);
        when(reservaRepository.findByOrdenId(orden.getId())).thenReturn(List.of(reserva));
        when(reservaRepository.save(any(ReservaCheckout.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.procesarPagoSimuladoYGenerarOtp("ORD-TEST", "TARJETA_SIMULADA");

        assertThat(reserva.getEstado()).isEqualTo("CONCRETADA");
        verify(reservaRepository).save(reserva);
    }

    @Test
    void elBarridoNoLiberaStockDeUnaReservaCuyaOrdenYaEstaPagada() {
        Orden orden = ordenConCupon(null);
        orden.setOrdenEstado(estado("PAGADA"));
        ReservaCheckout reserva = reservaDe(orden, "ACTIVA");
        when(reservaRepository.findReservasActivasExpiradasDeOrdenPendiente(any(OffsetDateTime.class)))
            .thenReturn(List.of(reserva));

        service.liberarReservasExpiradas();

        verify(inventarioService, never()).revertirReservaStock(anyInt(), anyInt(), anyInt());
        assertThat(reserva.getEstado()).isEqualTo("ACTIVA");
        assertThat(orden.getOrdenEstado().getCode()).isEqualTo("PAGADA");
        verify(reservaRepository, never()).save(any(ReservaCheckout.class));
        verify(ordenRepository, never()).save(any(Orden.class));
    }

    @Test
    void elBarridoLiberaStockDeUnaReservaDeOrdenAunPendienteYExpirada() {
        Orden orden = ordenConCupon(null);
        orden.setOrdenEstado(estado("PENDIENTE_PAGO"));
        ReservaCheckout reserva = reservaDe(orden, "ACTIVA");
        when(reservaRepository.findReservasActivasExpiradasDeOrdenPendiente(any(OffsetDateTime.class)))
            .thenReturn(List.of(reserva));
        when(reservaRepository.save(any(ReservaCheckout.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ordenRepository.save(any(Orden.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ordenEstadoRepository.findByCode("EXPIRADA_CANCELADA")).thenReturn(Optional.of(estado("EXPIRADA_CANCELADA")));

        service.liberarReservasExpiradas();

        verify(inventarioService).revertirReservaStock(1, 10, 1);
        assertThat(reserva.getEstado()).isEqualTo("EXPIRADA_LIBERADA");
        assertThat(orden.getOrdenEstado().getCode()).isEqualTo("EXPIRADA_CANCELADA");
    }

    // ---------------------------------------------------------------------
    // Demand alerts (alerta_demanda)
    // ---------------------------------------------------------------------

    @Test
    void registraUnaAlertaDeDemandaParaUnSkuAgotado() {
        stubAlertaDemandaStateful();
        CalzadoSku sku = sku(20, "T01001-40");
        when(skuRepository.findBySku("T01001-40")).thenReturn(Optional.of(sku));

        service.registrarAlertaDemanda("T01001-40", "ana@correo.com");

        ArgumentCaptor<AlertaDemanda> captor = ArgumentCaptor.forClass(AlertaDemanda.class);
        verify(alertaDemandaRepository).save(captor.capture());
        assertThat(captor.getValue().getCalzadoSku()).isSameAs(sku);
        assertThat(captor.getValue().getEmailCliente()).isEqualTo("ana@correo.com");
        assertThat(captor.getValue().getNotificado()).isFalse();
        assertThat(paresAlerta).hasSize(1);
    }

    @Test
    void noDuplicaLaAlertaCuandoSeRepiteElMismoParSkuCorreo() {
        stubAlertaDemandaStateful();
        when(skuRepository.findBySku("T01001-40")).thenReturn(Optional.of(sku(20, "T01001-40")));

        service.registrarAlertaDemanda("T01001-40", "ana@correo.com");
        service.registrarAlertaDemanda("T01001-40", "ana@correo.com");

        verify(alertaDemandaRepository, times(1)).save(any(AlertaDemanda.class));
        assertThat(paresAlerta).hasSize(1);
    }

    @Test
    void registraUnaAlertaPorCadaSkuConElMismoCorreo() {
        stubAlertaDemandaStateful();
        when(skuRepository.findBySku("T01001-40")).thenReturn(Optional.of(sku(20, "T01001-40")));
        when(skuRepository.findBySku("T01001-41")).thenReturn(Optional.of(sku(21, "T01001-41")));

        service.registrarAlertaDemanda("T01001-40", "ana@correo.com");
        service.registrarAlertaDemanda("T01001-41", "ana@correo.com");

        verify(alertaDemandaRepository, times(2)).save(any(AlertaDemanda.class));
        assertThat(paresAlerta).hasSize(2);
    }

    @Test
    void rechazaUnaAlertaParaUnSkuDesconocido() {
        when(skuRepository.findBySku("NO-EXISTE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registrarAlertaDemanda("NO-EXISTE", "ana@correo.com"))
            .isInstanceOf(CheckoutBusinessException.class)
            .hasMessage("El SKU indicado no existe");

        verify(alertaDemandaRepository, never()).save(any());
    }

    @Test
    void rechazaUnaAlertaConCorreoVacio() {
        assertThatThrownBy(() -> service.registrarAlertaDemanda("T01001-40", "   "))
            .isInstanceOf(CheckoutBusinessException.class)
            .hasMessage("El parametro emailCliente es obligatorio");

        verify(skuRepository, never()).findBySku(any());
        verify(alertaDemandaRepository, never()).save(any());
    }

    @Test
    void rechazaUnaAlertaConCorreoMalFormado() {
        assertThatThrownBy(() -> service.registrarAlertaDemanda("T01001-40", "ana-correo.com"))
            .isInstanceOf(CheckoutBusinessException.class)
            .hasMessage("El correo del cliente no tiene un formato valido");

        verify(alertaDemandaRepository, never()).save(any());
    }

    // ---------------------------------------------------------------------
    // Order consultation (GET /api/checkout/ordenes)
    // ---------------------------------------------------------------------

    @Test
    void consultaLaOrdenCuandoElCorreoYElNumeroCoinciden() {
        Orden orden = ordenConsultable("ORD-2024-001", "PAGADA");
        when(ordenRepository.findByNumeroOrden("ORD-2024-001")).thenReturn(Optional.of(orden));

        OrdenConsulta consulta = service.consultarOrden("ana@correo.com", "ORD-2024-001");

        assertThat(consulta.numeroOrden()).isEqualTo("ORD-2024-001");
        assertThat(consulta.estado()).isEqualTo("PAGADA");
        assertThat(consulta.tipoEntrega()).isEqualTo("ENVIO_DOMICILIO");
        assertThat(consulta.subtotal()).isEqualByComparingTo("100.00");
        assertThat(consulta.costoEnvio()).isEqualByComparingTo("4.50");
        assertThat(consulta.descuentoAplicado()).isEqualByComparingTo("10.00");
        assertThat(consulta.totalAPagar()).isEqualByComparingTo("98.30");
        assertThat(consulta.fechaCreacion()).isEqualTo(orden.getCreatedAt().toString());
        assertThat(consulta.lineas()).containsExactly(
            new OrdenConsultaLinea("T01001-40", 2, new BigDecimal("50.00"), new BigDecimal("100.00")));
    }

    @Test
    void devuelveNoEncontradoCuandoElCorreoNoEsElDuenoDeLaOrden() {
        when(ordenRepository.findByNumeroOrden("ORD-2024-001"))
            .thenReturn(Optional.of(ordenConsultable("ORD-2024-001", "PAGADA")));

        assertThatThrownBy(() -> service.consultarOrden("otro@correo.com", "ORD-2024-001"))
            .isInstanceOf(CheckoutBusinessException.class)
            .hasMessage("No se encontro una orden con el numero y correo indicados");
    }

    @Test
    void elNoEncontradoPorCorreoIncorrectoEsIndistinguibleDeUnNumeroInexistente() {
        when(ordenRepository.findByNumeroOrden("ORD-2024-001"))
            .thenReturn(Optional.of(ordenConsultable("ORD-2024-001", "PAGADA")));
        when(ordenRepository.findByNumeroOrden("ORD-NO-EXISTE")).thenReturn(Optional.empty());

        Throwable porCorreo = catchThrowable(() -> service.consultarOrden("otro@correo.com", "ORD-2024-001"));
        Throwable porNumero = catchThrowable(() -> service.consultarOrden("ana@correo.com", "ORD-NO-EXISTE"));

        assertThat(porCorreo).isInstanceOf(CheckoutBusinessException.class);
        assertThat(porNumero).isInstanceOf(CheckoutBusinessException.class);
        assertThat(porCorreo.getMessage()).isEqualTo(porNumero.getMessage());
        assertThat(((CheckoutBusinessException) porCorreo).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(((CheckoutBusinessException) porNumero).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void rechazaLaConsultaSiFaltaElCorreoOLaOrden() {
        assertThatThrownBy(() -> service.consultarOrden(null, "ORD-2024-001"))
            .isInstanceOf(CheckoutBusinessException.class)
            .hasMessage("Los parametros email y numeroOrden son obligatorios");
        assertThatThrownBy(() -> service.consultarOrden("ana@correo.com", "  "))
            .isInstanceOf(CheckoutBusinessException.class)
            .hasMessage("Los parametros email y numeroOrden son obligatorios");

        verify(ordenRepository, never()).findByNumeroOrden(any());
    }

    @Test
    void devuelveUnaOrdenExpiradaConSuPropioEstado() {
        when(ordenRepository.findByNumeroOrden("ORD-2024-002"))
            .thenReturn(Optional.of(ordenConsultable("ORD-2024-002", "EXPIRADA_CANCELADA")));

        OrdenConsulta consulta = service.consultarOrden("ana@correo.com", "ORD-2024-002");

        assertThat(consulta.estado()).isEqualTo("EXPIRADA_CANCELADA");
    }

    // ---------------------------------------------------------------------
    // Fixtures
    // ---------------------------------------------------------------------

    private Set<String> paresAlerta;

    /**
     * Stateful in-memory double for {@link AlertaDemandaRepository}: it records the persisted
     * (sku id, email) pairs so a test can prove that a repeated capture leaves one row, not two.
     */
    private void stubAlertaDemandaStateful() {
        paresAlerta = new HashSet<>();
        when(alertaDemandaRepository.existsByCalzadoSkuIdAndEmailCliente(any(), any()))
            .thenAnswer(invocation -> paresAlerta.contains(
                invocation.getArgument(0) + "|" + invocation.getArgument(1)));
        when(alertaDemandaRepository.save(any(AlertaDemanda.class)))
            .thenAnswer(invocation -> {
                AlertaDemanda alerta = invocation.getArgument(0);
                paresAlerta.add(alerta.getCalzadoSku().getId() + "|" + alerta.getEmailCliente());
                return alerta;
            });
    }

    private static CalzadoSku sku(int id, String codigo) {
        CalzadoSku sku = new CalzadoSku();
        sku.setId(id);
        sku.setSku(codigo);
        sku.setPrecioVentaVigente(new BigDecimal("50.00"));
        return sku;
    }

    private static Orden ordenConsultable(String numeroOrden, String estadoCode) {
        Customer customer = new Customer();
        customer.setEmail("ana@correo.com");
        customer.setNombreCompleto("Ana Cliente");

        OrdenEstado estado = new OrdenEstado();
        estado.setCode(estadoCode);
        estado.setDescription("Estado de prueba");

        Orden orden = new Orden();
        orden.setId(55);
        orden.setNumeroOrden(numeroOrden);
        orden.setCustomer(customer);
        orden.setCanalVenta("ONLINE_ECOMMERCE");
        orden.setTipoEntrega("ENVIO_DOMICILIO");
        orden.setOrdenEstado(estado);
        orden.setSubtotalProductos(new BigDecimal("100.00"));
        orden.setCostoEnvio(new BigDecimal("4.50"));
        orden.setComisionPasarela(new BigDecimal("3.80"));
        orden.setDescuentoAplicado(new BigDecimal("10.00"));
        orden.setTotalAPagar(new BigDecimal("98.30"));
        orden.setCreatedAt(OffsetDateTime.parse("2025-01-15T10:30:00-05:00"));

        OrdenLinea linea = new OrdenLinea();
        linea.setOrden(orden);
        linea.setCalzadoSku(sku(20, "T01001-40"));
        linea.setCantidad(2);
        linea.setUnitPrice(new BigDecimal("50.00"));
        linea.setLineTotal(new BigDecimal("100.00"));
        orden.getLineas().add(linea);
        return orden;
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now();
    }

    private static TarifarioEnvio zona(String nombre, String prefijo, String distancia, String costo, String umbral) {
        TarifarioEnvio tarifa = new TarifarioEnvio();
        tarifa.setZonaNombre(nombre);
        tarifa.setCodigoPostalPrefijo(prefijo);
        tarifa.setDistanciaKmMax(new BigDecimal(distancia));
        tarifa.setCostoFleteBase(new BigDecimal(costo));
        tarifa.setUmbralCompraEnvioGratis(new BigDecimal(umbral));
        tarifa.setActive(true);
        return tarifa;
    }

    private void stubTarifarioCompleto() {
        when(tarifarioEnvioRepository.findByActiveTrue()).thenReturn(List.of(
            zona("Metropolitana", "0901", "25.00", "4.50", "70.00"),
            zona("Departamental", "0902", "100.00", "8.50", "90.00"),
            zona("Nacional", "0903", "500.00", "14.00", "120.00")));
    }

    private static CuponDescuento cupon(
        Integer id, String codigo, String tipo, String valor, String minimo,
        int limite, int usos, OffsetDateTime inicio, OffsetDateTime fin) {
        CuponDescuento cupon = new CuponDescuento();
        cupon.setId(id);
        cupon.setCodigoCupon(codigo);
        cupon.setTipoDescuento(tipo);
        cupon.setValorDescuento(new BigDecimal(valor));
        cupon.setMontoMinimoCompra(new BigDecimal(minimo));
        cupon.setLimiteUsos(limite);
        cupon.setUsosActuales(usos);
        cupon.setActive(true);
        cupon.setFechaInicio(inicio);
        cupon.setFechaFin(fin);
        return cupon;
    }

    private void stubCreacion(String skuCodigo, String precio) {
        stubLookupsCreacion(skuCodigo, precio);
        when(ordenRepository.save(any(Orden.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reservaRepository.save(any(ReservaCheckout.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    /**
     * Stubs the shared parts of a multi-line checkout: the guest customer, the pending state and the
     * two saves. Each test adds its own SKUs and per-SKU stock via {@link #stubSkuConStock}.
     */
    private void stubCreacionMultiLinea() {
        Customer customer = new Customer();
        customer.setEmail("cliente@test.com");
        customer.setNombreCompleto("Cliente Test");
        lenient().when(customerRepository.findByEmail("cliente@test.com")).thenReturn(Optional.of(customer));

        OrdenEstado pendiente = new OrdenEstado();
        pendiente.setCode("PENDIENTE_PAGO");
        lenient().when(ordenEstadoRepository.findByCode("PENDIENTE_PAGO")).thenReturn(Optional.of(pendiente));

        lenient().when(ordenRepository.save(any(Orden.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(reservaRepository.save(any(ReservaCheckout.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void stubSkuConStock(int id, String codigo, String precio, Bodega bodega) {
        when(skuRepository.findBySku(codigo)).thenReturn(Optional.of(skuConPrecio(id, codigo, precio)));
        lenient().when(inventarioStockRepository.findStockDespachableWebPorSku(eq(id), anyInt()))
            .thenReturn(List.of(stockEnBodega(bodega)));
    }

    private static CalzadoSku skuConPrecio(int id, String codigo, String precio) {
        CalzadoSku sku = new CalzadoSku();
        sku.setId(id);
        sku.setSku(codigo);
        sku.setPrecioVentaVigente(new BigDecimal(precio));
        return sku;
    }

    private void stubLookupsCreacion(String skuCodigo, String precio) {
        Customer customer = new Customer();
        customer.setEmail("cliente@test.com");
        customer.setNombreCompleto("Cliente Test");
        when(customerRepository.findByEmail("cliente@test.com")).thenReturn(Optional.of(customer));

        CalzadoSku sku = new CalzadoSku();
        sku.setId(10);
        sku.setSku(skuCodigo);
        sku.setPrecioVentaVigente(new BigDecimal(precio));
        when(skuRepository.findBySku(skuCodigo)).thenReturn(Optional.of(sku));

        Bodega bodega = new Bodega();
        bodega.setId(1);
        lenient().when(inventarioStockRepository.findStockDespachableWebPorSku(eq(10), anyInt()))
            .thenReturn(List.of(stockEnBodega(bodega)));

        OrdenEstado pendiente = new OrdenEstado();
        pendiente.setCode("PENDIENTE_PAGO");
        lenient().when(ordenEstadoRepository.findByCode("PENDIENTE_PAGO")).thenReturn(Optional.of(pendiente));
    }

    private static Bodega bodega(int id) {
        Bodega bodega = new Bodega();
        bodega.setId(id);
        return bodega;
    }

    private static InventarioStock stockEnBodega(Bodega bodega) {
        InventarioStock stock = new InventarioStock();
        stock.setBodega(bodega);
        stock.setCantidadDisponible(25);
        stock.setCantidadReservada(0);
        return stock;
    }

    private static Sucursal sucursal(int id, String nombre) {
        Sucursal sucursal = new Sucursal();
        sucursal.setId(id);
        sucursal.setNombre(nombre);
        sucursal.setActive(true);
        return sucursal;
    }

    private static OrdenEstado estado(String code) {
        OrdenEstado estado = new OrdenEstado();
        estado.setCode(code);
        return estado;
    }

    private static ReservaCheckout reservaDe(Orden orden, String estado) {
        ReservaCheckout reserva = new ReservaCheckout();
        reserva.setOrden(orden);
        reserva.setBodega(orden.getBodegaDespacho());
        reserva.setCalzadoSku(orden.getLineas().get(0).getCalzadoSku());
        reserva.setCantidad(1);
        reserva.setFechaExpiracion(OffsetDateTime.now().minusMinutes(5));
        reserva.setEstado(estado);
        return reserva;
    }

    private static Orden ordenConCupon(CuponDescuento cupon) {
        Customer customer = new Customer();
        customer.setEmail("cliente@test.com");
        customer.setNombreCompleto("Cliente Test");
        customer.setNumeroDocumento("1234567890");

        Bodega bodega = new Bodega();
        bodega.setId(1);

        OrdenEstado pendiente = new OrdenEstado();
        pendiente.setCode("PENDIENTE_PAGO");

        CalzadoSku sku = new CalzadoSku();
        sku.setId(10);

        Orden orden = new Orden();
        orden.setId(77);
        orden.setNumeroOrden("ORD-TEST");
        orden.setCustomer(customer);
        orden.setBodegaDespacho(bodega);
        orden.setOrdenEstado(pendiente);
        orden.setSubtotalProductos(new BigDecimal("100.00"));
        orden.setCostoEnvio(BigDecimal.ZERO);
        orden.setComisionPasarela(new BigDecimal("3.80"));
        orden.setDescuentoAplicado(new BigDecimal("10.00"));
        orden.setTotalAPagar(new BigDecimal("93.80"));
        orden.setCupon(cupon);

        OrdenLinea linea = new OrdenLinea();
        linea.setOrden(orden);
        linea.setCalzadoSku(sku);
        linea.setCantidad(1);
        linea.setUnitPrice(new BigDecimal("100.00"));
        linea.setLineTotal(new BigDecimal("100.00"));
        orden.getLineas().add(linea);
        return orden;
    }

    private void stubPago(Orden orden) {
        OrdenEstado pagada = new OrdenEstado();
        pagada.setCode("PAGADA");
        when(ordenRepository.findByNumeroOrden("ORD-TEST")).thenReturn(Optional.of(orden));
        when(ordenEstadoRepository.findByCode("PAGADA")).thenReturn(Optional.of(pagada));
        when(ordenRepository.save(any(Orden.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SystemUser admin = new SystemUser();
        admin.setUsername("admin");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));

        FacturaTipo tipo = new FacturaTipo();
        tipo.setCode("FACTURA");
        when(facturaTipoRepository.findByCode("FACTURA")).thenReturn(Optional.of(tipo));

        FacturaEstado emitida = new FacturaEstado();
        emitida.setCode("EMITIDA");
        when(facturaEstadoRepository.findByCode("EMITIDA")).thenReturn(Optional.of(emitida));
        when(facturaRepository.save(any(Factura.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(numeracionFacturaService.siguiente(any(FacturaTipo.class))).thenReturn("FAC-000001");
        when(facturaVentaPdfService.emitir(any(Factura.class))).thenReturn("/storage/facturas/FAC-000001.pdf");

        PagoMetodo tarjeta = new PagoMetodo();
        tarjeta.setCode("TARJETA_SIMULADA");
        tarjeta.setComisionPorcentaje(new BigDecimal("3.50"));
        tarjeta.setComisionFija(new BigDecimal("0.30"));
        when(pagoMetodoRepository.findByCode("TARJETA_SIMULADA")).thenReturn(Optional.of(tarjeta));
        when(pagoTransaccionRepository.save(any(PagoTransaccion.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        DespachoEstado preparado = new DespachoEstado();
        preparado.setCode("PREPARADO");
        when(despachoEstadoRepository.findByCode("PREPARADO")).thenReturn(Optional.of(preparado));
        when(despachoRepository.save(any(DespachoEnvio.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }
}
