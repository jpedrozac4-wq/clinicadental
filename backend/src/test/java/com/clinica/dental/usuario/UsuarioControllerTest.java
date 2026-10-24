package com.clinica.dental.usuario;

import com.clinica.dental.shared.ClinicaBusinessException;
import com.clinica.dental.shared.ManejadorErroresClinica;
import com.clinica.dental.usuario.dto.UsuarioRequest;
import com.clinica.dental.usuario.dto.UsuarioResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrato HTTP del padron de usuarios (S18, S20). */
@ExtendWith(MockitoExtension.class)
class UsuarioControllerTest {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    @Mock private UsuarioService service;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new UsuarioController(service))
            .setControllerAdvice(new ManejadorErroresClinica())
            .build();
    }

    private static UsuarioResponse usuario(int id, String username, String rol, boolean activo) {
        return new UsuarioResponse(id, username, "Ana Lucía Mendoza", "Ana Lucía Mendoza",
            username + "@clinica.com", rol, activo);
    }

    private static UsernamePasswordAuthenticationToken actor(String username) {
        return new UsernamePasswordAuthenticationToken(username, null, List.of());
    }

    @Test
    void listarDevuelveLosUsuariosSinExponerElHash() throws Exception {
        when(service.listar(isNull(), isNull(), isNull()))
            .thenReturn(List.of(usuario(1, "admin", "ROLE_ADMINISTRADOR", true)));

        mvc.perform(get("/api/usuarios"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].username").value("admin"))
            .andExpect(jsonPath("$[0].rol").value("ROLE_ADMINISTRADOR"))
            .andExpect(jsonPath("$[0].activo").value(true))
            .andExpect(jsonPath("$[0].passwordHash").doesNotExist())
            .andExpect(jsonPath("$[0].password").doesNotExist());
    }

    @Test
    void listarTrasladaLosFiltrosDeRolEstadoYBusqueda() throws Exception {
        when(service.listar(eq("DENTISTA"), eq("activo"), eq("ana"))).thenReturn(List.of());

        mvc.perform(get("/api/usuarios").param("rol", "DENTISTA").param("estado", "activo").param("q", "ana"))
            .andExpect(status().isOk());

        verify(service).listar("DENTISTA", "activo", "ana");
    }

    @Test
    void listarOdontologosDevuelveIdYNombreParaElSelectorDeAgenda() throws Exception {
        when(service.listar(eq("DENTISTA"), isNull(), isNull()))
            .thenReturn(List.of(usuario(4, "dentista1", "ROLE_DENTISTA", true)));

        mvc.perform(get("/api/usuarios").param("rol", "DENTISTA"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(4))
            .andExpect(jsonPath("$[0].nombre").value("Ana Lucía Mendoza"));
    }

    @Test
    void crearDevuelve201ConElUsuarioCreado() throws Exception {
        when(service.crear(any(UsuarioRequest.class)))
            .thenReturn(usuario(9, "dentista3", "ROLE_DENTISTA", true));

        String cuerpo = JSON.writeValueAsString(
            new UsuarioRequest("dentista3", "Ana Lucía Mendoza", "ana@clinica.com", "Secreta123", "DENTISTA"));

        mvc.perform(post("/api/usuarios").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(9))
            .andExpect(jsonPath("$.rol").value("ROLE_DENTISTA"));
    }

    @Test
    void crearConUsernameEnBlancoDevuelve400YNoLlamaAlServicio() throws Exception {
        String cuerpo = JSON.writeValueAsString(
            new UsuarioRequest("   ", "Ana Lucía Mendoza", "ana@clinica.com", "Secreta123", "DENTISTA"));

        mvc.perform(post("/api/usuarios").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(containsString("username")));

        verify(service, never()).crear(any(UsuarioRequest.class));
    }

    @Test
    void crearConEmailMalformadoDevuelve400YNoLlamaAlServicio() throws Exception {
        String cuerpo = JSON.writeValueAsString(
            new UsuarioRequest("dentista3", "Ana Lucía Mendoza", "ana(at)clinica", "Secreta123", "DENTISTA"));

        mvc.perform(post("/api/usuarios").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
            .andExpect(status().isBadRequest());

        verify(service, never()).crear(any(UsuarioRequest.class));
    }

    @Test
    void crearConUsernameDuplicadoDevuelve409() throws Exception {
        when(service.crear(any(UsuarioRequest.class)))
            .thenThrow(new ClinicaBusinessException(HttpStatus.CONFLICT,
                "El username ya esta registrado"));

        String cuerpo = JSON.writeValueAsString(
            new UsuarioRequest("dentista3", "Ana Lucía Mendoza", "ana@clinica.com", "Secreta123", "DENTISTA"));

        mvc.perform(post("/api/usuarios").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value(containsString("username")));
    }

    @Test
    void actualizarSinContraseniaDevuelve200() throws Exception {
        when(service.actualizar(eq(9), any(UsuarioRequest.class)))
            .thenReturn(usuario(9, "dentista3", "ROLE_RECEPCIONISTA", true));

        String cuerpo = JSON.writeValueAsString(
            new UsuarioRequest("dentista3", "Ana Lucía Mendoza", "ana@clinica.com", null, "RECEPCIONISTA"));

        mvc.perform(put("/api/usuarios/9").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rol").value("ROLE_RECEPCIONISTA"));
    }

    @Test
    void darDeBajaDevuelve204YReportaElActorAutenticado() throws Exception {
        mvc.perform(delete("/api/usuarios/9").principal(actor("admin")))
            .andExpect(status().isNoContent());

        verify(service).darDeBaja(9, "admin");
    }

    @Test
    void darseDeBajaASiMismoDevuelve409() throws Exception {
        org.mockito.Mockito.doThrow(new ClinicaBusinessException(HttpStatus.CONFLICT,
                "Un usuario no puede darse de baja a si mismo"))
            .when(service).darDeBaja(eq(1), eq("admin"));

        mvc.perform(delete("/api/usuarios/1").principal(actor("admin")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value(containsString("si mismo")));
    }
}
