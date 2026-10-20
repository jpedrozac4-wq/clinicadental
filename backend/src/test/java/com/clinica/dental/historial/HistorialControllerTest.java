package com.clinica.dental.historial;

import com.clinica.dental.historial.dto.HistorialResponse;
import com.clinica.dental.shared.ClinicaBusinessException;
import com.clinica.dental.shared.ManejadorErroresClinica;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrato HTTP del historial clinico (S3). */
@ExtendWith(MockitoExtension.class)
class HistorialControllerTest {

    @Mock
    private HistorialService service;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new HistorialController(service))
            .setControllerAdvice(new ManejadorErroresClinica())
            .build();
    }

    @Test
    void elHistorialDevuelveCitasYTratamientos() throws Exception {
        when(service.dePaciente(1)).thenReturn(new HistorialResponse(
            1, "Ana Lucía Mendoza Rojas",
            List.of(new HistorialResponse.CitaHistorial(9, OffsetDateTime.now().minusDays(4),
                "Consulta y diagnóstico", "ATENDIDA", "Primera consulta")),
            List.of(new HistorialResponse.RealizadoHistorial(4, OffsetDateTime.now().minusDays(7),
                "Ortodoncia - control mensual", "Cambio de arcos y ligas", new BigDecimal("450.00")))));

        mvc.perform(get("/api/pacientes/1/historial"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paciente").value("Ana Lucía Mendoza Rojas"))
            .andExpect(jsonPath("$.citas[0].estado").value("ATENDIDA"))
            .andExpect(jsonPath("$.tratamientos[0].costo").value(450.00));
    }

    @Test
    void elHistorialDeUnPacienteInexistenteDevuelve404() throws Exception {
        when(service.dePaciente(99))
            .thenThrow(new ClinicaBusinessException(HttpStatus.NOT_FOUND, "Paciente no encontrado"));

        mvc.perform(get("/api/pacientes/99/historial"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Paciente no encontrado"));
    }
}
