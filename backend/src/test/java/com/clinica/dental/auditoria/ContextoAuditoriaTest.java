package com.clinica.dental.auditoria;

import com.clinica.dental.seguridad.SystemUser;
import com.clinica.dental.seguridad.SystemUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Resolucion del actor, la IP y el agente de la peticion auditada (S19).
 *
 * <p>El caso importante es el de la operacion sin actor: el proceso programado y el bootstrap no
 * tienen {@code SecurityContext} ni peticion HTTP, y la fila tiene que quedar con {@code id_user}
 * nulo sin reventar.
 */
@ExtendWith(MockitoExtension.class)
class ContextoAuditoriaTest {

    @Mock
    private SystemUserRepository usuarios;

    @AfterEach
    void limpiarContextos() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    private ContextoAuditoria contexto() {
        return new ContextoAuditoria(usuarios);
    }

    private static SystemUser usuario(int id, String username) {
        SystemUser u = new SystemUser();
        u.setId(id);
        u.setUsername(username);
        return u;
    }

    private static void autenticar(String username) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(username, null, AuthorityUtils.NO_AUTHORITIES));
    }

    private static void montarPeticion(MockHttpServletRequest peticion) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(peticion));
    }

    // ------------------------------------------------------------------- actor

    @Test
    void resuelveElUsuarioDelPadronQueCorrespondeAlSujetoDelJwt() {
        autenticar("ana.admin");
        when(usuarios.findByUsername("ana.admin")).thenReturn(Optional.of(usuario(4, "ana.admin")));

        ContextoAuditoria.Actor actor = contexto().actor();

        assertThat(actor.username()).isEqualTo("ana.admin");
        assertThat(actor.registrado()).isTrue();
        assertThat(actor.usuario().getId()).isEqualTo(4);
    }

    @Test
    void sinAutenticacionDevuelveUnActorVacio() {
        ContextoAuditoria.Actor actor = contexto().actor();

        assertThat(actor.username()).isNull();
        assertThat(actor.registrado()).isFalse();
    }

    @Test
    void laAutenticacionAnonimaNoEsUnActor() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
            "clave-anonima", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThat(contexto().username()).isNull();
        assertThat(contexto().actor().registrado()).isFalse();
    }

    @Test
    void unSujetoAutenticadoQueNoEstaEnElPadronNoCompletaLaFk() {
        autenticar("usuario-borrado");
        when(usuarios.findByUsername("usuario-borrado")).thenReturn(Optional.empty());

        ContextoAuditoria.Actor actor = contexto().actor();

        assertThat(actor.username()).isEqualTo("usuario-borrado");
        assertThat(actor.registrado()).isFalse();
    }

    @Test
    void sinPeticionHttpNoHayIpNiAgente() {
        assertThat(contexto().ipAddress()).isNull();
        assertThat(contexto().userAgent()).isNull();
    }

    // ------------------------------------------------------------ ip y agente

    @Test
    void prefiereElPrimerSaltoDeLaCabeceraDelProxy() {
        MockHttpServletRequest peticion = new MockHttpServletRequest();
        peticion.addHeader("X-Forwarded-For", "190.104.10.7, 10.0.0.4");
        peticion.setRemoteAddr("10.0.0.4");
        montarPeticion(peticion);

        assertThat(contexto().ipAddress()).isEqualTo("190.104.10.7");
    }

    @Test
    void caeALaConexionDirectaCuandoNoHayCabecera() {
        MockHttpServletRequest peticion = new MockHttpServletRequest();
        peticion.setRemoteAddr("192.168.1.20");
        montarPeticion(peticion);

        assertThat(contexto().ipAddress()).isEqualTo("192.168.1.20");
    }

    @Test
    void ignoraLaCabeceraDelProxyCuandoVieneSinValorUtil() {
        MockHttpServletRequest peticion = new MockHttpServletRequest();
        peticion.addHeader("X-Forwarded-For", "unknown");
        peticion.setRemoteAddr("192.168.1.20");
        montarPeticion(peticion);

        assertThat(contexto().ipAddress()).isEqualTo("192.168.1.20");
    }

    @Test
    void devuelveElAgenteDeLaPeticion() {
        MockHttpServletRequest peticion = new MockHttpServletRequest();
        peticion.addHeader("User-Agent", "Mozilla/5.0 ClinicaDental");
        montarPeticion(peticion);

        assertThat(contexto().userAgent()).isEqualTo("Mozilla/5.0 ClinicaDental");
    }
}
