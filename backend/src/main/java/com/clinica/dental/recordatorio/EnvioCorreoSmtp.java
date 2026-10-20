package com.clinica.dental.recordatorio;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Envio de recordatorios por SMTP configurable (S5, S15).
 *
 * <p>La configuracion vive en {@code spring.mail.*}, alimentada por las variables de entorno
 * {@code CLINICA_SMTP_*}. Cuando {@code spring.mail.host} esta vacio el sistema no intenta conectar:
 * informa {@link #estaConfigurado()} en falso y el recordatorio queda registrado como
 * {@code SIMULADA} en lugar de fallar.
 */
@Component
public class EnvioCorreoSmtp implements EnvioCorreo {

    /** Clave de Spring Boot con la que se declara el servidor SMTP (CLINICA_SMTP_HOST). */
    static final String CLAVE_HOST = "spring.mail.host";

    /** Remitente de los recordatorios; por defecto usa el usuario SMTP configurado. */
    static final String CLAVE_REMITENTE = "clinica.recordatorios.remitente";

    private static final String REMITENTE_POR_DEFECTO = "recordatorios@clinica-dental.local";

    private static final Logger log = LoggerFactory.getLogger(EnvioCorreoSmtp.class);

    private final ObjectProvider<JavaMailSender> remitentes;
    private final Environment entorno;

    public EnvioCorreoSmtp(ObjectProvider<JavaMailSender> remitentes, Environment entorno) {
        this.remitentes = remitentes;
        this.entorno = entorno;
    }

    @Override
    public boolean estaConfigurado() {
        String host = entorno.getProperty(CLAVE_HOST);
        return host != null && !host.isBlank();
    }

    @Override
    public void enviar(String destino, String asunto, String mensaje) {
        if (!estaConfigurado()) {
            throw new IllegalStateException("SMTP no configurado: falta spring.mail.host "
                + "(variable de entorno CLINICA_SMTP_HOST)");
        }
        JavaMailSender remitente = remitentes.getIfAvailable();
        if (remitente == null) {
            throw new IllegalStateException("No hay un JavaMailSender disponible para enviar recordatorios");
        }

        MimeMessage correo = remitente.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(correo, false, "UTF-8");
            helper.setFrom(remitenteConfigurado());
            helper.setTo(destino);
            helper.setSubject(asunto);
            helper.setText(mensaje, false);
        } catch (MessagingException ex) {
            throw new IllegalStateException("No se pudo preparar el correo del recordatorio: "
                + ex.getMessage(), ex);
        }

        log.debug("Enviando recordatorio por SMTP a {} desde {}", destino, remitenteConfigurado());
        remitente.send(correo);
    }

    private String remitenteConfigurado() {
        String remitente = entorno.getProperty(CLAVE_REMITENTE);
        if (remitente != null && !remitente.isBlank()) {
            return remitente.trim();
        }
        String usuario = entorno.getProperty("spring.mail.username");
        return usuario == null || usuario.isBlank() ? REMITENTE_POR_DEFECTO : usuario.trim();
    }
}
