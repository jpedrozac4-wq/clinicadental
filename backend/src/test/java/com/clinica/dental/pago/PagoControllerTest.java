package com.clinica.dental.pago;

import com.clinica.dental.pago.dto.PagoRequest;
import com.clinica.dental.pago.dto.PagoResponse;
import com.clinica.dental.shared.ClinicaBusinessException;
import com.clinica.dental.shared.ManejadorErroresClinica;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrato HTTP de pagos sobre una factura (S10). */
@ExtendWith(MockitoExtension.class)
class PagoControllerTest {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    @Mock
    private PagoService service;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new PagoController(service))
            .setControllerAdvice(new ManejadorErroresClinica())
            .build();
    }

    private static PagoResponse registrado() {
        return new PagoResponse(90, 50, OffsetDateTime.now(), new BigDecimal("200.00"),
            1, "EFECTIVO", "Recibo 1042");
    }

    @Test
    void registrarDevuelve201ConElPagoRegistrado() throws Exception {
        when(service.registrar(eq(50), any(PagoRequest.class))).thenReturn(registrado());

        String cuerpo = JSON.writeValueAsString(new PagoRequest(new BigDecimal("200.00"), 1, "Recibo 1042"));

        mvc.perform(post("/api/facturas/50/pagos").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.idFactura").value(50))
            .andExpect(jsonPath("$.monto").value(200.00))
            .andExpect(jsonPath("$.metodoPago").value("EFECTIVO"));
    }

    @Test
    void registrarSinMontoDevuelve400() throws Exception {
        String cuerpo = JSON.writeValueAsString(new PagoRequest(null, 1, null));

        mvc.perform(post("/api/facturas/50/pagos").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("ERROR"));

        verify(service, never()).registrar(any(), any(PagoRequest.class));
    }

    @Test
    void unPagoQueSuperaElTotalDevuelve409() throws Exception {
        when(service.registrar(eq(50), any(PagoRequest.class)))
            .thenThrow(new ClinicaBusinessException(HttpStatus.CONFLICT,
                "El total de pagos no puede superar el total de la factura"));

        String cuerpo = JSON.writeValueAsString(new PagoRequest(new BigDecimal("900.00"), 1, null));

        mvc.perform(post("/api/facturas/50/pagos").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("El total de pagos no puede superar el total de la factura"));
    }

    @Test
    void elListadoDevuelveLosPagosDeLaFactura() throws Exception {
        when(service.listar(50)).thenReturn(List.of(registrado()));

        mvc.perform(get("/api/facturas/50/pagos"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].monto").value(200.00))
            .andExpect(jsonPath("$[0].metodoPago").value("EFECTIVO"));
    }
}
