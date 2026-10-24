package com.clinica.dental.usuario;

import com.clinica.dental.shared.ClinicaBusinessException;
import com.clinica.dental.shared.ManejadorErroresClinica;
import com.clinica.dental.usuario.dto.PermisoResponse;
import com.clinica.dental.usuario.dto.RolRequest;
import com.clinica.dental.usuario.dto.RolResponse;
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

import java.util.List;

import static org.hamcrest.Matchers.containsString;
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

/** Contrato HTTP de roles, permisos y matriz editable (S18). */
@ExtendWith(MockitoExtension.class)
class RolControllerTest {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    @Mock private RolService roles;
    @Mock private PermisoService permisos;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders
            .standaloneSetup(new RolController(roles), new PermisoController(permisos))
            .setControllerAdvice(new ManejadorErroresClinica())
            .build();
    }

    @Test
    void listarRolesDevuelveElNombreLaDescripcionYLosPermisos() throws Exception {
        when(roles.listar()).thenReturn(List.of(
            new RolResponse(1, "ROLE_ADMINISTRADOR", "Administrador", List.of("CITA_VER", "USUARIOS_ADMIN"))));

        mvc.perform(get("/api/roles"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].roleName").value("ROLE_ADMINISTRADOR"))
            .andExpect(jsonPath("$[0].description").value("Administrador"))
            .andExpect(jsonPath("$[0].permissions[1]").value("USUARIOS_ADMIN"));
    }

    @Test
    void crearRolDevuelve201() throws Exception {
        when(roles.crear(any(RolRequest.class)))
            .thenReturn(new RolResponse(7, "ROLE_AUDITOR", "Consulta", List.of("AUDITORIA_VER")));

        String cuerpo = JSON.writeValueAsString(
            new RolRequest("AUDITOR", "Consulta", List.of("AUDITORIA_VER")));

        mvc.perform(post("/api/roles").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.roleName").value("ROLE_AUDITOR"));
    }

    @Test
    void crearRolConUnPermisoDesconocidoDevuelve400() throws Exception {
        when(roles.crear(any(RolRequest.class)))
            .thenThrow(new ClinicaBusinessException("Permisos desconocidos: PERMISO_INVENTADO"));

        String cuerpo = JSON.writeValueAsString(
            new RolRequest("AUDITOR", "Consulta", List.of("PERMISO_INVENTADO")));

        mvc.perform(post("/api/roles").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(containsString("PERMISO_INVENTADO")));
    }

    @Test
    void actualizarRolDevuelveLaMatrizReemplazada() throws Exception {
        when(roles.actualizar(eq(2), any(RolRequest.class)))
            .thenReturn(new RolResponse(2, "ROLE_DENTISTA", "Odontologo", List.of("CITA_VER")));

        String cuerpo = JSON.writeValueAsString(
            new RolRequest("DENTISTA", "Odontologo", List.of("CITA_VER")));

        mvc.perform(put("/api/roles/2").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.permissions[0]").value("CITA_VER"));
    }

    @Test
    void renombrarUnRolBaseDevuelve409() throws Exception {
        when(roles.actualizar(eq(2), any(RolRequest.class)))
            .thenThrow(new ClinicaBusinessException(HttpStatus.CONFLICT,
                "Los roles base no pueden renombrarse"));

        String cuerpo = JSON.writeValueAsString(
            new RolRequest("ROLE_ODONTOLOGO", "Odontologo", List.of("CITA_VER")));

        mvc.perform(put("/api/roles/2").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
            .andExpect(status().isConflict());
    }

    @Test
    void eliminarUnRolDevuelve204() throws Exception {
        mvc.perform(delete("/api/roles/7"))
            .andExpect(status().isNoContent());

        verify(roles).eliminar(7);
    }

    @Test
    void catalogoDePermisosDevuelveModuloAccionYDescripcion() throws Exception {
        when(permisos.catalogo()).thenReturn(List.of(
            new PermisoResponse("PACIENTE_VER", "PACIENTE", "READ", "Consultar pacientes")));

        mvc.perform(get("/api/permisos"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].permissionName").value("PACIENTE_VER"))
            .andExpect(jsonPath("$[0].modulo").value("PACIENTE"))
            .andExpect(jsonPath("$[0].action").value("READ"))
            .andExpect(jsonPath("$[0].description").value("Consultar pacientes"));
    }
}
