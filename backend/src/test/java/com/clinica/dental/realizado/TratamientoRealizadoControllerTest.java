package com.clinica.dental.realizado;

import com.clinica.dental.realizado.dto.RealizadoRequest;
import com.clinica.dental.realizado.dto.RealizadoResponse;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrato HTTP de tratamientos realizados (S8). */
@ExtendWith(MockitoExtension.class)
class TratamientoRealizadoControllerTest {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    @Mock
    private TratamientoRealizadoService service;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new TratamientoRealizadoController(service))
            .setControllerAdvice(new ManejadorErroresClinica())
            .build();
    }

    private static RealizadoResponse registrado() {
        return new RealizadoResponse(70, 1, "Carlos Alberto Quispe Flores", 2,
            "Obturación de resina", null, 3, "Dr. Martín Salas",
            OffsetDateTime.now().minusHours(2), "Obturación en pieza 26", null,
            new BigDecimal("400.00"));
    }

    @Test
    void registrarDevuelve201ConElCostoCongelado() throws Exception {
        when(service.registrar(any(RealizadoRequest.class))).thenReturn(registrado());

        String cuerpo = JSON.writeValueAsString(new RealizadoRequest(1, 2, 3,
            OffsetDateTime.now().minusHours(2), "Obturación en pieza 26", null, null));

        mvc.perform(post("/api/tratamientos-realizados").contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.costo").value(400.00))
            .andExpect(jsonPath("$.tratamiento").value("Obturación de resina"));
    }

    @Test
    void registrarSinDetalleDevuelve400() throws Exception {
        String cuerpo = JSON.writeValueAsString(new RealizadoRequest(1, 2, 3,
            OffsetDateTime.now().minusHours(2), "  ", null, null));

        mvc.perform(post("/api/tratamientos-realizados").contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("ERROR"));

        verify(service, org.mockito.Mockito.never()).registrar(any(RealizadoRequest.class));
    }

    @Test
    void registrarSobreUnaCitaAjenaDevuelve409() throws Exception {
        when(service.registrar(any(RealizadoRequest.class)))
            .thenThrow(new ClinicaBusinessException(HttpStatus.CONFLICT,
                "La cita indicada no pertenece al paciente"));

        String cuerpo = JSON.writeValueAsString(new RealizadoRequest(1, 2, 3,
            OffsetDateTime.now().minusHours(2), "Obturación", null, 20));

        mvc.perform(post("/api/tratamientos-realizados").contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo))
            .andExpect(status().isConflict());
    }

    @Test
    void elListadoFiltraPorPaciente() throws Exception {
        when(service.listar(1)).thenReturn(java.util.List.of(registrado()));

        mvc.perform(get("/api/tratamientos-realizados").param("idPaciente", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].paciente").value("Carlos Alberto Quispe Flores"));
    }
}
