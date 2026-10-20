package com.clinica.dental.tratamiento;

import com.clinica.dental.shared.ClinicaBusinessException;
import com.clinica.dental.shared.ManejadorErroresClinica;
import com.clinica.dental.tratamiento.dto.TratamientoRequest;
import com.clinica.dental.tratamiento.dto.TratamientoResponse;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrato HTTP del catalogo de tratamientos (S7). */
@ExtendWith(MockitoExtension.class)
class TratamientoControllerTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Mock
    private TratamientoService service;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new TratamientoController(service))
            .setControllerAdvice(new ManejadorErroresClinica())
            .build();
    }

    private static String cuerpo(String precio) throws Exception {
        return JSON.writeValueAsString(new TratamientoRequest("ODO-002", "Limpieza dental",
            "Destartraje con ultrasonido", new BigDecimal(precio), 45));
    }

    private static TratamientoResponse ficha() {
        return new TratamientoResponse(1, "ODO-002", "Limpieza dental",
            "Destartraje con ultrasonido", new BigDecimal("350.00"), 45);
    }

    @Test
    void altaDevuelve201ConElTratamientoCreado() throws Exception {
        when(service.crear(any(TratamientoRequest.class))).thenReturn(ficha());

        mvc.perform(post("/api/tratamientos").contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("350.00")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.codigo").value("ODO-002"))
            .andExpect(jsonPath("$.precio").value(350.00));
    }

    @Test
    void altaConPrecioCeroDevuelve400() throws Exception {
        mvc.perform(post("/api/tratamientos").contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("0.00")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("ERROR"));

        verify(service, org.mockito.Mockito.never()).crear(any(TratamientoRequest.class));
    }

    @Test
    void altaConCodigoDuplicadoDevuelve409() throws Exception {
        when(service.crear(any(TratamientoRequest.class)))
            .thenThrow(new ClinicaBusinessException(HttpStatus.CONFLICT,
                "Ya existe un tratamiento con el codigo ODO-002"));

        mvc.perform(post("/api/tratamientos").contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("350.00")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value(
                org.hamcrest.Matchers.containsString("ODO-002")));
    }

    @Test
    void edicionDevuelveElTratamientoActualizado() throws Exception {
        when(service.actualizar(org.mockito.ArgumentMatchers.eq(1), any(TratamientoRequest.class)))
            .thenReturn(ficha());

        mvc.perform(put("/api/tratamientos/1").contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("350.00")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.duracionMinutos").value(45));
    }

    @Test
    void listadoDevuelveElCatalogoVigente() throws Exception {
        when(service.listar()).thenReturn(List.of(ficha()));

        mvc.perform(get("/api/tratamientos"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].nombre").value("Limpieza dental"));
    }
}
