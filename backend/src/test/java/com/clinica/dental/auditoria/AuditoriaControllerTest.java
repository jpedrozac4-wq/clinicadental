package com.clinica.dental.auditoria;

import com.clinica.dental.auditoria.dto.AuditoriaPaginaResponse;
import com.clinica.dental.auditoria.dto.AuditoriaRegistroResponse;
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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato HTTP de {@code GET /api/auditoria} (S19) con MockMvc standalone.
 *
 * <p>La guarda {@code OP_AUDITORIA_VER} vive en {@code SecurityConfig} y no se prueba aca: estas
 * pruebas fijan los nombres del JSON y el tratamiento de los filtros.
 */
@ExtendWith(MockitoExtension.class)
class AuditoriaControllerTest {

    @Mock
    private AuditoriaService service;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new AuditoriaController(service))
            .setControllerAdvice(new ManejadorErroresClinica())
            .build();
    }

    private static AuditoriaPaginaResponse pagina() {
        return new AuditoriaPaginaResponse(
            List.of(new AuditoriaRegistroResponse(12L,
                OffsetDateTime.of(2026, 3, 4, 9, 30, 0, 0, ZoneOffset.ofHours(-4)),
                "PACIENTE_CREAR", "Alta de un paciente", "paciente", "7", "ana.admin",
                "{\"idPaciente\": 7}")),
            0, 25, 1, 1);
    }

    @Test
    void laConsultaDevuelveLaPaginaConLosNombresDelContrato() throws Exception {
        when(service.consultar(null, null, null, null, null, null)).thenReturn(pagina());

        mvc.perform(get("/api/auditoria"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.contenido[0].idLog").value(12))
            .andExpect(jsonPath("$.contenido[0].fecha").exists())
            .andExpect(jsonPath("$.contenido[0].accion").value("PACIENTE_CREAR"))
            .andExpect(jsonPath("$.contenido[0].descripcionAccion").value("Alta de un paciente"))
            .andExpect(jsonPath("$.contenido[0].tabla").value("paciente"))
            .andExpect(jsonPath("$.contenido[0].registro").value("7"))
            .andExpect(jsonPath("$.contenido[0].usuario").value("ana.admin"))
            .andExpect(jsonPath("$.contenido[0].detalle").value("{\"idPaciente\": 7}"))
            .andExpect(jsonPath("$.pagina").value(0))
            .andExpect(jsonPath("$.tamanio").value(25))
            .andExpect(jsonPath("$.totalElementos").value(1))
            .andExpect(jsonPath("$.totalPaginas").value(1));
    }

    @Test
    void losFiltrosDeLaPeticionLleganAlServicio() throws Exception {
        when(service.consultar(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
            "PACIENTE_CREAR", "ana.admin", 2, 50)).thenReturn(pagina());

        mvc.perform(get("/api/auditoria")
                .param("desde", "2026-03-01")
                .param("hasta", "2026-03-31")
                .param("accion", "PACIENTE_CREAR")
                .param("usuario", "ana.admin")
                .param("pagina", "2")
                .param("tamanio", "50"))
            .andExpect(status().isOk());

        verify(service).consultar(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
            "PACIENTE_CREAR", "ana.admin", 2, 50);
    }

    @Test
    void unaAccionDesconocidaDevuelve400() throws Exception {
        when(service.consultar(null, null, "NO_EXISTE", null, null, null))
            .thenThrow(new ClinicaBusinessException(HttpStatus.BAD_REQUEST,
                "Accion de auditoria desconocida: NO_EXISTE"));

        mvc.perform(get("/api/auditoria").param("accion", "NO_EXISTE"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("ERROR"))
            .andExpect(jsonPath("$.message").value("Accion de auditoria desconocida: NO_EXISTE"));
    }

    @Test
    void unaPaginaFueraDeRangoDevuelveContenidoVacioConLosTotalesReales() throws Exception {
        when(service.consultar(null, null, null, null, 9, null))
            .thenReturn(new AuditoriaPaginaResponse(List.of(), 9, 25, 30, 2));

        mvc.perform(get("/api/auditoria").param("pagina", "9"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.contenido").isEmpty())
            .andExpect(jsonPath("$.pagina").value(9))
            .andExpect(jsonPath("$.totalElementos").value(30))
            .andExpect(jsonPath("$.totalPaginas").value(2));
    }

    @Test
    void unaFechaQueNoEsIsoDevuelve400SinLlegarAlServicio() throws Exception {
        mvc.perform(get("/api/auditoria").param("desde", "04-03-2026"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }
}
