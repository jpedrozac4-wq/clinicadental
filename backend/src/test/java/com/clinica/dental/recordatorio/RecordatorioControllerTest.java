package com.clinica.dental.recordatorio;

import com.clinica.dental.recordatorio.dto.RecordatorioResponse;
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

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrato HTTP de los recordatorios de citas (S5). */
@ExtendWith(MockitoExtension.class)
class RecordatorioControllerTest {

    private static final OffsetDateTime OBJETIVO = OffsetDateTime.parse("2026-10-21T09:00:00-04:00");

    @Mock
    private RecordatorioService service;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new RecordatorioController(service))
            .setControllerAdvice(new ManejadorErroresClinica())
            .build();
    }

    private static RecordatorioResponse recordatorio(String estado, OffsetDateTime enviadoEn, String error) {
        return new RecordatorioResponse(7, 50, OBJETIVO.plusDays(1), CanalNotificacion.EMAIL, estado,
            "ana.mendoza@example.com", OBJETIVO,
            "Recordatorio: su cita de Limpieza dental en Clínica Dental es el 22/10/2026 09:00.", enviadoEn, error);
    }

    @Test
    void procesarDevuelveLosRecordatoriosDespachados() throws Exception {
        when(service.procesar()).thenReturn(List.of(
            recordatorio(EstadoNotificacion.SIMULADA, OBJETIVO, "SMTP no configurado")));

        mvc.perform(post("/api/recordatorios/procesar"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(7))
            .andExpect(jsonPath("$[0].estado").value(EstadoNotificacion.SIMULADA))
            .andExpect(jsonPath("$[0].destino").value("ana.mendoza@example.com"));
    }

    @Test
    void procesarSinVencidosDevuelveUnaListaVacia() throws Exception {
        when(service.procesar()).thenReturn(List.of());

        mvc.perform(post("/api/recordatorios/procesar"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void elHistorialDeRecordatoriosSeConsultaDesdeLaCita() throws Exception {
        when(service.historial(50)).thenReturn(List.of(
            recordatorio(EstadoNotificacion.ENVIADA, OBJETIVO, null)));

        mvc.perform(get("/api/citas/50/recordatorios"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].idCita").value(50))
            .andExpect(jsonPath("$[0].canal").value(CanalNotificacion.EMAIL))
            .andExpect(jsonPath("$[0].mensaje").value(
                "Recordatorio: su cita de Limpieza dental en Clínica Dental es el 22/10/2026 09:00."));
    }

    @Test
    void elHistorialDeUnaCitaInexistenteDevuelve404() throws Exception {
        when(service.historial(99)).thenThrow(
            new ClinicaBusinessException(HttpStatus.NOT_FOUND, "Cita no encontrada"));

        mvc.perform(get("/api/citas/99/recordatorios"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value("ERROR"))
            .andExpect(jsonPath("$.message").value("Cita no encontrada"));
    }
}
