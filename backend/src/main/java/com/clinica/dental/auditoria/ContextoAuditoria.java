package com.clinica.dental.auditoria;

import com.clinica.dental.seguridad.SystemUser;
import com.clinica.dental.seguridad.SystemUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Resuelve quien ejecuta la operacion auditada y desde donde (S19).
 *
 * <p>El actor es el sujeto del JWT, que es el {@code username} del padron. Se resuelve contra la
 * base porque {@code audit_log.id_user} es una FK real y no un texto libre.
 *
 * <p>Fuera de una peticion HTTP (proceso programado, bootstrap, o una llamada interna) no hay
 * {@link RequestContextHolder} ni {@code SecurityContext}: en ese caso {@link #actor()} devuelve un
 * actor sin usuario y {@link AuditoriaService} deja {@code id_user} en {@code NULL} declarando el
 * origen en {@code notes}.
 */
@Component
public class ContextoAuditoria {

    /** Cabecera que agrega el proxy reverso con la IP original del cliente. */
    private static final String CABECERA_IP_REAL = "X-Forwarded-For";

    private final SystemUserRepository usuarios;

    public ContextoAuditoria(SystemUserRepository usuarios) {
        this.usuarios = usuarios;
    }

    /**
     * Identidad del actor de la peticion.
     *
     * @param username sujeto autenticado, o {@code null} si la peticion es anonima
     * @param usuario  fila del padron que corresponde a ese username, o {@code null} si no existe
     */
    public record Actor(String username, SystemUser usuario) {

        /** Un actor sin fila en el padron no puede completar la FK {@code id_user}. */
        public boolean registrado() {
            return usuario != null;
        }
    }

    /** Actor de la peticion en curso; nunca {@code null}, puede venir sin username ni usuario. */
    public Actor actor() {
        String username = username();
        if (username == null) {
            return new Actor(null, null);
        }
        return new Actor(username, usuarios.findByUsername(username).orElse(null));
    }

    /** Sujeto autenticado del {@code SecurityContext}, ignorando la autenticacion anonima. */
    public String username() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion == null
            || !autenticacion.isAuthenticated()
            || autenticacion instanceof AnonymousAuthenticationToken) {
            return null;
        }
        String nombre = autenticacion.getName();
        return nombre == null || nombre.isBlank() ? null : nombre.trim();
    }

    /**
     * IP del cliente. Detras de Nginx la conexion siempre viene del proxy, asi que se prefiere el
     * primer salto de {@code X-Forwarded-For} y solo se cae a la conexion directa si no hay cabecera.
     */
    public String ipAddress() {
        HttpServletRequest peticion = peticionActual();
        if (peticion == null) {
            return null;
        }
        String reenviada = peticion.getHeader(CABECERA_IP_REAL);
        if (reenviada != null && !reenviada.isBlank()) {
            String primera = reenviada.split(",")[0].trim();
            if (!primera.isEmpty() && !"unknown".equalsIgnoreCase(primera)) {
                return primera;
            }
        }
        String remota = peticion.getRemoteAddr();
        return remota == null || remota.isBlank() ? null : remota.trim();
    }

    /** {@code User-Agent} de la peticion en curso, o {@code null} si la operacion no vino de HTTP. */
    public String userAgent() {
        HttpServletRequest peticion = peticionActual();
        if (peticion == null) {
            return null;
        }
        String agente = peticion.getHeader("User-Agent");
        return agente == null || agente.isBlank() ? null : agente.trim();
    }

    private static HttpServletRequest peticionActual() {
        RequestAttributes atributos = RequestContextHolder.getRequestAttributes();
        if (atributos instanceof ServletRequestAttributes servlet) {
            return servlet.getRequest();
        }
        return null;
    }
}
