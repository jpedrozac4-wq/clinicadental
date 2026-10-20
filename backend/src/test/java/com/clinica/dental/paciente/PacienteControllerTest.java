package com.clinica.dental.paciente;

import com.clinica.dental.paciente.dto.PacienteRequest;
import com.clinica.dental.paciente.dto.PacienteResponse;
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

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato HTTP del modulo de pacientes (S1, S2) con MockMvc standalone.
 */
@ExtendWith(MockitoExtension.class)
class PacienteControllerTest {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    @Mock
    private PacienteService service;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new PacienteController(service))
            .setControllerAdvice(new ManejadorErroresClinica())
            .build();
    }

    private static String cuerpo(String email) throws Exception {
        return JSON.writeValueAsString(new PacienteRequest("Ana Lucía", "Mendoza Rojas",
            LocalDate.of(1990, 3, 14), "777-12345", "Av. Busch 1450", email));
    }

    private static PacienteResponse ficha() {
        return new PacienteResponse(3, "Ana Lucía", "Mendoza Rojas",
            LocalDate.of(1990, 3, 14), "777-12345", "Av. Busch 1450", "ana.mendoza@example.com");
    }

    @Test
    void altaValidaDevuelve201ConLaFichaCreada() throws Exception {
        when(service.crear(any(PacienteRequest.class))).thenReturn(ficha());

        mvc.perform(post("/api/pacientes").contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("ana.mendoza@example.com")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(3))
            .andExpect(jsonPath("$.nombres").value("Ana Lucía"));
    }

    @Test
    void altaConEmailInvalidoDevuelve400YNoLlegaAlServicio() throws Exception {
        mvc.perform(post("/api/pacientes").contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("sin-arroba")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("ERROR"))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("email")));

        verify(service, org.mockito.Mockito.never()).crear(any(PacienteRequest.class));
    }

    @Test
    void consultaDeUnPacienteInexistenteDevuelve404() throws Exception {
        when(service.obtener(99)).thenThrow(
            new ClinicaBusinessException(HttpStatus.NOT_FOUND, "Paciente no encontrado"));

        mvc.perform(get("/api/pacientes/99"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value("ERROR"))
            .andExpect(jsonPath("$.message").value("Paciente no encontrado"));
    }

    @Test
    void listadoDevuelveLasFichasVigentes() throws Exception {
        when(service.listar("men")).thenReturn(List.of(ficha()));

        mvc.perform(get("/api/pacientes").param("texto", "men"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].apellidos").value("Mendoza Rojas"));
    }

    @Test
    void edicionDevuelveLaFichaActualizada() throws Exception {
        when(service.actualizar(eq(3), any(PacienteRequest.class))).thenReturn(ficha());

        mvc.perform(put("/api/pacientes/3").contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo("ana.mendoza@example.com")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("ana.mendoza@example.com"));
    }

    @Test
    void bajaDevuelve204SinCuerpo() throws Exception {
        mvc.perform(delete("/api/pacientes/3"))
            .andExpect(status().isNoContent());

        verify(service).darDeBaja(3);
    }
}
