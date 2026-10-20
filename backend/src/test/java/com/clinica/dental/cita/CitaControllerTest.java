package com.clinica.dental.cita;

import com.clinica.dental.cita.dto.CitaRequest;
import com.clinica.dental.cita.dto.CitaResponse;
import com.clinica.dental.cita.dto.ReprogramarRequest;
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

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrato HTTP de la agenda (S4, S6). */
@ExtendWith(MockitoExtension.class)
class CitaControllerTest {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    @Mock
    private CitaService service;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new CitaController(service))
            .setControllerAdvice(new ManejadorErroresClinica())
            .build();
    }

    private static OffsetDateTime enDosDias() {
        return OffsetDateTime.now().plusDays(2).truncatedTo(ChronoUnit.MINUTES);
    }

    private static CitaResponse cita(String estado) {
        return new CitaResponse(10, 1, "Ana Lucía Mendoza Rojas", 2, "Limpieza dental",
            3, "Dra. Valeria Ríos", estado, enDosDias(), enDosDias().plusMinutes(45),
            45, "Control", null, null);
    }

    @Test
    void programarUnaCitaDevuelve201() throws Exception {
        when(service.crear(any(CitaRequest.class))).thenReturn(cita(EstadoCita.PROGRAMADA));

        String cuerpo = JSON.writeValueAsString(
            new CitaRequest(1, 2, 3, enDosDias(), "Control"));

        mvc.perform(post("/api/citas").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.estado").value("PROGRAMADA"))
            .andExpect(jsonPath("$.duracionMinutos").value(45));
    }

    @Test
    void programarSinPacienteDevuelve400() throws Exception {
        String cuerpo = JSON.writeValueAsString(new CitaRequest(null, 2, 3, enDosDias(), null));

        mvc.perform(post("/api/citas").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(
                org.hamcrest.Matchers.containsString("paciente")));

        verify(service, org.mockito.Mockito.never()).crear(any(CitaRequest.class));
    }

    @Test
    void programarEnUnHorarioOcupadoDevuelve409() throws Exception {
        when(service.crear(any(CitaRequest.class)))
            .thenThrow(new ClinicaBusinessException(HttpStatus.CONFLICT,
                "El dentista ya tiene una cita en ese horario"));

        String cuerpo = JSON.writeValueAsString(new CitaRequest(1, 2, 3, enDosDias(), null));

        mvc.perform(post("/api/citas").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value(
                org.hamcrest.Matchers.containsString("horario")));
    }

    @Test
    void programarEnElPasadoDevuelve422() throws Exception {
        when(service.crear(any(CitaRequest.class)))
            .thenThrow(new ClinicaBusinessException(HttpStatus.UNPROCESSABLE_ENTITY,
                "La fecha y hora de la cita debe ser futura"));

        String cuerpo = JSON.writeValueAsString(
            new CitaRequest(1, 2, 3, OffsetDateTime.now().minusDays(1), null));

        mvc.perform(post("/api/citas").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void reprogramarDevuelveLaNuevaCitaVinculada() throws Exception {
        CitaResponse reprogramada = new CitaResponse(11, 1, "Ana Lucía Mendoza Rojas", 2,
            "Limpieza dental", 3, "Dra. Valeria Ríos", EstadoCita.PROGRAMADA, enDosDias(),
            enDosDias().plusMinutes(45), 45, "Control", null, 10);
        when(service.reprogramar(eq(10), any(ReprogramarRequest.class))).thenReturn(reprogramada);

        String cuerpo = JSON.writeValueAsString(new ReprogramarRequest(enDosDias(), null));

        mvc.perform(patch("/api/citas/10/reprogramar").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.idCitaOrigen").value(10));
    }

    @Test
    void cancelarDevuelve204SinCuerpo() throws Exception {
        mvc.perform(patch("/api/citas/10/cancelar"))
            .andExpect(status().isNoContent());

        verify(service).cancelar(10);
    }

    @Test
    void laAgendaDevuelveLasCitasDelRango() throws Exception {
        when(service.agenda(any(), any())).thenReturn(List.of(cita(EstadoCita.CONFIRMADA)));

        mvc.perform(get("/api/citas"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].paciente").value("Ana Lucía Mendoza Rojas"));
    }
}
