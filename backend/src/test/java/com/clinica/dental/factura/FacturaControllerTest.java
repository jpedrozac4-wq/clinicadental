package com.clinica.dental.factura;

import com.clinica.dental.factura.dto.FacturaRequest;
import com.clinica.dental.factura.dto.FacturaResponse;
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
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrato HTTP de facturación: emisión, consulta y comprobante PDF (S9). */
@ExtendWith(MockitoExtension.class)
class FacturaControllerTest {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    @Mock
    private FacturaService service;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new FacturaController(service))
            .setControllerAdvice(new ManejadorErroresClinica())
            .build();
    }

    private static FacturaResponse emitida() {
        return new FacturaResponse(
            50, "F-000005", 1, "Ana María Mendoza Rojas", OffsetDateTime.now(),
            EstadoFactura.EMITIDA, new BigDecimal("550.00"), new BigDecimal("550.00"),
            new BigDecimal("0.00"), null,
            List.of(
                new FacturaResponse.DetalleResponse(1, 7, "Obturación de resina", new BigDecimal("400.00")),
                new FacturaResponse.DetalleResponse(2, 8, "Consulta y diagnóstico", new BigDecimal("150.00"))));
    }

    @Test
    void emitirDevuelve201ConElDetalleYElTotal() throws Exception {
        when(service.emitir(any(FacturaRequest.class))).thenReturn(emitida());

        String cuerpo = JSON.writeValueAsString(new FacturaRequest(1, List.of(7, 8), null));

        mvc.perform(post("/api/facturas").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.numero").value("F-000005"))
            .andExpect(jsonPath("$.total").value(550.00))
            .andExpect(jsonPath("$.saldo").value(550.00))
            .andExpect(jsonPath("$.estado").value("EMITIDA"))
            .andExpect(jsonPath("$.detalle.length()").value(2))
            .andExpect(jsonPath("$.detalle[0].monto").value(400.00));
    }

    @Test
    void emitirSinTratamientosRealizadosDevuelve400() throws Exception {
        String cuerpo = JSON.writeValueAsString(new FacturaRequest(1, List.of(), null));

        mvc.perform(post("/api/facturas").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("ERROR"));

        verify(service, never()).emitir(any(FacturaRequest.class));
    }

    @Test
    void emitirConUnRealizadoRepetidoDevuelve409() throws Exception {
        when(service.emitir(any(FacturaRequest.class)))
            .thenThrow(new ClinicaBusinessException(HttpStatus.CONFLICT,
                "El tratamiento realizado 7 ya fue facturado"));

        String cuerpo = JSON.writeValueAsString(new FacturaRequest(1, List.of(7), null));

        mvc.perform(post("/api/facturas").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("El tratamiento realizado 7 ya fue facturado"));
    }

    @Test
    void elListadoDevuelveLasFacturasConSaldoYDetalle() throws Exception {
        when(service.listar()).thenReturn(List.of(emitida()));

        mvc.perform(get("/api/facturas"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].numero").value("F-000005"))
            .andExpect(jsonPath("$[0].detalle.length()").value(2));
    }

    @Test
    void obtenerDevuelveLaFacturaConSuDetalleYSaldo() throws Exception {
        when(service.obtener(50)).thenReturn(emitida());

        mvc.perform(get("/api/facturas/50"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(50))
            .andExpect(jsonPath("$.paciente").value("Ana María Mendoza Rojas"))
            .andExpect(jsonPath("$.saldo").value(550.00));
    }

    @Test
    void unaFacturaInexistenteDevuelve404() throws Exception {
        when(service.obtener(99))
            .thenThrow(new ClinicaBusinessException(HttpStatus.NOT_FOUND, "Factura no encontrada"));

        mvc.perform(get("/api/facturas/99"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Factura no encontrada"));
    }

    @Test
    void elComprobanteSeDevuelveComoPdfDescargable() throws Exception {
        byte[] comprobante = "%PDF-1.4\n%%EOF\n".getBytes(StandardCharsets.ISO_8859_1);
        when(service.comprobante(50)).thenReturn(
            new com.clinica.dental.factura.dto.ComprobantePdf("F-000005", comprobante));

        mvc.perform(get("/api/facturas/50/pdf"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andExpect(header().string("Content-Disposition",
                "attachment; filename=\"factura-F-000005.pdf\""))
            .andExpect(content().bytes(comprobante));
    }
}
