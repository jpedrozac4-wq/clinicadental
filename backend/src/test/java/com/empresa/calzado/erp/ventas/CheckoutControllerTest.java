package com.empresa.calzado.erp.ventas;

import com.empresa.calzado.erp.ventas.dto.CotizacionEnvio;
import com.empresa.calzado.erp.ventas.dto.LineaCheckout;
import com.empresa.calzado.erp.ventas.dto.OrdenConsulta;
import com.empresa.calzado.erp.ventas.dto.OrdenConsultaLinea;
import com.empresa.calzado.erp.ventas.dto.SucursalRetiro;
import com.empresa.calzado.erp.ventas.dto.ValidacionCupon;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract tests for the storefront checkout controller using MockMvc standalone.
 *
 * <p>The service is mocked, so these tests pin the HTTP surface: routes, JSON field names,
 * validation failures and the business-error envelope shared with the catalogue admin controller.
 */
@ExtendWith(MockitoExtension.class)
class CheckoutControllerTest {

    @Mock private CheckoutService checkoutService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new CheckoutController(checkoutService)).build();
    }

    @Test
    void cotizaEnvioYDevuelveElContratoDelStorefront() throws Exception {
        when(checkoutService.cotizarEnvio(eq("0901"), any(BigDecimal.class))).thenReturn(
            new CotizacionEnvio("Zona Metropolitana (Urbana)", new BigDecimal("4.50"), false, new BigDecimal("70.00")));

        mockMvc.perform(post("/api/checkout/cotizar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"codigoPostal\":\"0901\",\"subtotalProductos\":50.00}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.zonaNombre").value("Zona Metropolitana (Urbana)"))
            .andExpect(jsonPath("$.costoEnvio").value(4.50))
            .andExpect(jsonPath("$.envioGratis").value(false))
            .andExpect(jsonPath("$.umbralCompraEnvioGratis").value(70.00));
    }

    @Test
    void cotizarRechazaCodigoPostalVacio() throws Exception {
        mockMvc.perform(post("/api/checkout/cotizar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"codigoPostal\":\"  \",\"subtotalProductos\":50.00}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("ERROR"))
            .andExpect(jsonPath("$.message").value("El parametro codigoPostal es obligatorio"));
    }

    @Test
    void cotizarRechazaSubtotalNegativo() throws Exception {
        mockMvc.perform(post("/api/checkout/cotizar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"codigoPostal\":\"0901\",\"subtotalProductos\":-1.00}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("ERROR"))
            .andExpect(jsonPath("$.message").value("El parametro subtotalProductos es obligatorio y no puede ser negativo"));
    }

    @Test
    void validaCuponYDevuelveElContratoDelStorefront() throws Exception {
        when(checkoutService.validarCupon(eq("BIENVENIDA10"), any(BigDecimal.class))).thenReturn(
            new ValidacionCupon(true, "BIENVENIDA10", "PORCENTAJE", new BigDecimal("10.00"), null));

        mockMvc.perform(post("/api/checkout/cupon/validar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"codigoCupon\":\"BIENVENIDA10\",\"subtotalProductos\":100.00}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.valido").value(true))
            .andExpect(jsonPath("$.codigoCupon").value("BIENVENIDA10"))
            .andExpect(jsonPath("$.tipoDescuento").value("PORCENTAJE"))
            .andExpect(jsonPath("$.descuento").value(10.00));
    }

    @Test
    void validarCuponRechazaCodigoVacio() throws Exception {
        mockMvc.perform(post("/api/checkout/cupon/validar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"codigoCupon\":\"\",\"subtotalProductos\":100.00}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("ERROR"))
            .andExpect(jsonPath("$.message").value("El parametro codigoCupon es obligatorio"));
    }

    @Test
    void iniciarConCuponInvalidoDevuelveCuatrocientos() throws Exception {
        when(checkoutService.crearOrdenConReserva2Horas(any(), any(), any(), anyInt(), any(), any(), any(), any()))
            .thenThrow(new CheckoutBusinessException(HttpStatus.BAD_REQUEST, "El cupon no existe o no esta activo"));

        mockMvc.perform(post("/api/checkout/iniciar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@b.com\",\"nombre\":\"A\",\"sku\":\"SKU-1\",\"cantidad\":1,\"codigoCupon\":\"MALO\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("ERROR"))
            .andExpect(jsonPath("$.message").value("El cupon no existe o no esta activo"));
    }

    @Test
    void iniciarSinCuponSigueFuncionando() throws Exception {
        Orden orden = ordenBase();
        when(checkoutService.crearOrdenConReserva2Horas(any(), any(), any(), anyInt(), any(), any(), any(), any())).thenReturn(orden);

        mockMvc.perform(post("/api/checkout/iniciar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@b.com\",\"nombre\":\"A\",\"sku\":\"SKU-1\",\"cantidad\":1}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("RESERVA_ACTIVA"))
            .andExpect(jsonPath("$.totalAPagar").value(103.80))
            .andExpect(jsonPath("$.descuentoAplicado").value(0.00));
    }

    @Test
    void iniciarReenviaElCodigoPostalAlServicio() throws Exception {
        when(checkoutService.crearOrdenConReserva2Horas(any(), any(), any(), anyInt(), any(), eq("0902"), any(), any()))
            .thenReturn(ordenBase());

        mockMvc.perform(post("/api/checkout/iniciar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@b.com\",\"nombre\":\"A\",\"sku\":\"SKU-1\",\"cantidad\":1,\"codigoPostal\":\"0902\"}"))
            .andExpect(status().isOk());

        verify(checkoutService).crearOrdenConReserva2Horas("a@b.com", "A", "SKU-1", 1, null, "0902", null, null);
    }

    @Test
    void listarSucursalesDevuelveElContratoDelPicker() throws Exception {
        when(checkoutService.listarSucursalesParaRetiro()).thenReturn(List.of(
            new SucursalRetiro(1, "Tienda Centro"),
            new SucursalRetiro(2, "Tienda Mall")));

        mockMvc.perform(get("/api/checkout/sucursales"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(1))
            .andExpect(jsonPath("$[0].nombre").value("Tienda Centro"))
            .andExpect(jsonPath("$[1].id").value(2))
            .andExpect(jsonPath("$[1].nombre").value("Tienda Mall"));
    }

    @Test
    void iniciarReenviaElTipoDeEntregaYLaSucursalDeRetiro() throws Exception {
        when(checkoutService.crearOrdenConReserva2Horas(any(), any(), any(), anyInt(), any(), any(), any(), any()))
            .thenReturn(ordenBase());

        mockMvc.perform(post("/api/checkout/iniciar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@b.com\",\"nombre\":\"A\",\"sku\":\"SKU-1\",\"cantidad\":1,\"tipoEntrega\":\"RETIRO_TIENDA_CLICK_COLLECT\",\"idSucursalRetiro\":7}"))
            .andExpect(status().isOk());

        verify(checkoutService).crearOrdenConReserva2Horas(
            "a@b.com", "A", "SKU-1", 1, null, null, "RETIRO_TIENDA_CLICK_COLLECT", 7);
    }

    @Test
    void iniciarRetiroSinSucursalDevuelveCuatrocientos() throws Exception {
        when(checkoutService.crearOrdenConReserva2Horas(any(), any(), any(), anyInt(), any(), any(), any(), any()))
            .thenThrow(new CheckoutBusinessException(
                HttpStatus.BAD_REQUEST, "El parametro idSucursalRetiro es obligatorio para retiro en tienda"));

        mockMvc.perform(post("/api/checkout/iniciar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@b.com\",\"nombre\":\"A\",\"sku\":\"SKU-1\",\"cantidad\":1,\"tipoEntrega\":\"RETIRO_TIENDA_CLICK_COLLECT\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("ERROR"))
            .andExpect(jsonPath("$.message").value("El parametro idSucursalRetiro es obligatorio para retiro en tienda"));
    }

    @Test
    void iniciarSinStockDevuelveCuatrocientosConMensajeDeNegocio() throws Exception {
        when(checkoutService.crearOrdenConReserva2Horas(any(), any(), any(), anyInt(), any(), any(), any(), any()))
            .thenThrow(new CheckoutBusinessException(
                HttpStatus.BAD_REQUEST, "No hay stock disponible para el SKU solicitado"));

        mockMvc.perform(post("/api/checkout/iniciar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@b.com\",\"nombre\":\"A\",\"sku\":\"TV002-40\",\"cantidad\":1}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("ERROR"))
            .andExpect(jsonPath("$.message").value("No hay stock disponible para el SKU solicitado"));
    }

    // ---------------------------------------------------------------------
    // Demand alerts (POST /api/checkout/alerta-demanda)
    // ---------------------------------------------------------------------

    @Test
    void capturaUnaAlertaDeDemandaYDevuelveDoscientos() throws Exception {
        mockMvc.perform(post("/api/checkout/alerta-demanda")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"T01001-40\",\"emailCliente\":\"ana@correo.com\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REGISTRADO"));

        verify(checkoutService).registrarAlertaDemanda("T01001-40", "ana@correo.com");
    }

    @Test
    void alertaDemandaSinSkuDevuelveCuatrocientos() throws Exception {
        mockMvc.perform(post("/api/checkout/alerta-demanda")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"  \",\"emailCliente\":\"ana@correo.com\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("ERROR"))
            .andExpect(jsonPath("$.message").value("El parametro sku es obligatorio"));

        verify(checkoutService, never()).registrarAlertaDemanda(any(), any());
    }

    @Test
    void alertaDemandaSinCorreoDevuelveCuatrocientos() throws Exception {
        mockMvc.perform(post("/api/checkout/alerta-demanda")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"T01001-40\",\"emailCliente\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("ERROR"))
            .andExpect(jsonPath("$.message").value("El parametro emailCliente es obligatorio"));

        verify(checkoutService, never()).registrarAlertaDemanda(any(), any());
    }

    @Test
    void alertaDemandaConCorreoMalFormadoDevuelveCuatrocientos() throws Exception {
        doThrow(new CheckoutBusinessException(
            HttpStatus.BAD_REQUEST, "El correo del cliente no tiene un formato valido"))
            .when(checkoutService).registrarAlertaDemanda("T01001-40", "ana-correo.com");

        mockMvc.perform(post("/api/checkout/alerta-demanda")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"T01001-40\",\"emailCliente\":\"ana-correo.com\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("ERROR"))
            .andExpect(jsonPath("$.message").value("El correo del cliente no tiene un formato valido"));
    }

    // ---------------------------------------------------------------------
    // Order consultation (GET /api/checkout/ordenes)
    // ---------------------------------------------------------------------

    @Test
    void consultaLaOrdenConCorreoYNumero() throws Exception {
        OrdenConsulta consulta = new OrdenConsulta(
            "ORD-2024-001", "PAGADA", "Pago acreditado exitosamente", "ENVIO_DOMICILIO",
            new BigDecimal("100.00"), new BigDecimal("4.50"), new BigDecimal("10.00"),
            new BigDecimal("98.30"), "2025-01-15T10:30-05:00",
            List.of(new OrdenConsultaLinea("T01001-40", 2, new BigDecimal("50.00"), new BigDecimal("100.00"))));
        when(checkoutService.consultarOrden("ana@correo.com", "ORD-2024-001")).thenReturn(consulta);

        mockMvc.perform(get("/api/checkout/ordenes")
                .param("email", "ana@correo.com")
                .param("numeroOrden", "ORD-2024-001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.numeroOrden").value("ORD-2024-001"))
            .andExpect(jsonPath("$.estado").value("PAGADA"))
            .andExpect(jsonPath("$.estadoDescripcion").value("Pago acreditado exitosamente"))
            .andExpect(jsonPath("$.tipoEntrega").value("ENVIO_DOMICILIO"))
            .andExpect(jsonPath("$.subtotal").value(100.00))
            .andExpect(jsonPath("$.costoEnvio").value(4.50))
            .andExpect(jsonPath("$.descuentoAplicado").value(10.00))
            .andExpect(jsonPath("$.totalAPagar").value(98.30))
            .andExpect(jsonPath("$.fechaCreacion").exists())
            .andExpect(jsonPath("$.lineas[0].sku").value("T01001-40"))
            .andExpect(jsonPath("$.lineas[0].cantidad").value(2))
            .andExpect(jsonPath("$.lineas[0].precioUnitario").value(50.00))
            .andExpect(jsonPath("$.lineas[0].totalLinea").value(100.00));
    }

    @Test
    void consultaSinNumeroDeOrdenDevuelveCuatrocientos() throws Exception {
        mockMvc.perform(get("/api/checkout/ordenes").param("email", "ana@correo.com"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("ERROR"))
            .andExpect(jsonPath("$.message").value("Los parametros email y numeroOrden son obligatorios"));

        verify(checkoutService, never()).consultarOrden(any(), any());
    }

    @Test
    void consultaSinCorreoDevuelveCuatrocientos() throws Exception {
        mockMvc.perform(get("/api/checkout/ordenes").param("numeroOrden", "ORD-2024-001"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("ERROR"))
            .andExpect(jsonPath("$.message").value("Los parametros email y numeroOrden son obligatorios"));

        verify(checkoutService, never()).consultarOrden(any(), any());
    }

    @Test
    void consultaNoEncontradaDevuelveCuatrocientosCuatro() throws Exception {
        when(checkoutService.consultarOrden("otro@correo.com", "ORD-2024-001"))
            .thenThrow(new CheckoutBusinessException(
                HttpStatus.NOT_FOUND, "No se encontro una orden con el numero y correo indicados"));

        mockMvc.perform(get("/api/checkout/ordenes")
                .param("email", "otro@correo.com")
                .param("numeroOrden", "ORD-2024-001"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value("ERROR"))
            .andExpect(jsonPath("$.message").value("No se encontro una orden con el numero y correo indicados"));
    }

    @Test
    void iniciarConListaDeLineasReenviaLasLineasAlServicio() throws Exception {
        when(checkoutService.crearOrdenConReserva2Horas(any(), any(), any(), anyInt(), any(), any(), any(), any(), any()))
            .thenReturn(ordenBase());

        mockMvc.perform(post("/api/checkout/iniciar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@b.com\",\"nombre\":\"A\",\"lineas\":[{\"sku\":\"SKU-A\",\"cantidad\":2},{\"sku\":\"SKU-B\",\"cantidad\":1}]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("RESERVA_ACTIVA"));

        verify(checkoutService).crearOrdenConReserva2Horas(
            "a@b.com", "A", null, 0, null, null, null, null,
            List.of(new LineaCheckout("SKU-A", 2), new LineaCheckout("SKU-B", 1)));
    }

    @Test
    void iniciarConListaVaciaDevuelveCuatrocientos() throws Exception {
        when(checkoutService.crearOrdenConReserva2Horas(any(), any(), any(), anyInt(), any(), any(), any(), any(), any()))
            .thenThrow(new CheckoutBusinessException(HttpStatus.BAD_REQUEST, "La lista de lineas no puede estar vacia"));

        mockMvc.perform(post("/api/checkout/iniciar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@b.com\",\"nombre\":\"A\",\"lineas\":[]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("ERROR"))
            .andExpect(jsonPath("$.message").value("La lista de lineas no puede estar vacia"));
    }

    private static Orden ordenBase() {
        Orden orden = new Orden();
        orden.setNumeroOrden("ORD-1");
        orden.setSubtotalProductos(new BigDecimal("100.00"));
        orden.setCostoEnvio(BigDecimal.ZERO);
        orden.setComisionPasarela(new BigDecimal("3.80"));
        orden.setDescuentoAplicado(BigDecimal.ZERO);
        orden.setTotalAPagar(new BigDecimal("103.80"));
        orden.setFechaExpiracionTtl2h(OffsetDateTime.now().plusHours(2));
        return orden;
    }
}
