package com.clinica.dental.recordatorio;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Configuracion SMTP del envio de recordatorios (S5, S15).
 *
 * <p>La suite nunca abre una conexion: solo se ejerce la deteccion de configuracion y los guardas
 * previos al despacho.
 */
class EnvioCorreoSmtpTest {

    private static ObjectProvider<JavaMailSender> sinRemitenteDeSpring() {
        return new DefaultListableBeanFactory().getBeanProvider(JavaMailSender.class);
    }

    @Test
    void sinHostConfiguradoNoSeConsideraListoParaEnviar() {
        EnvioCorreoSmtp smtp = new EnvioCorreoSmtp(sinRemitenteDeSpring(), new MockEnvironment());

        assertThat(smtp.estaConfigurado()).isFalse();
    }

    @Test
    void unHostEnBlancoCuentaComoNoConfigurado() {
        MockEnvironment entorno = new MockEnvironment().withProperty("spring.mail.host", "   ");

        EnvioCorreoSmtp smtp = new EnvioCorreoSmtp(sinRemitenteDeSpring(), entorno);

        assertThat(smtp.estaConfigurado()).isFalse();
    }

    @Test
    void sinHostElEnvioFallaConUnMensajeExplicitoSinAbrirConexion() {
        EnvioCorreoSmtp smtp = new EnvioCorreoSmtp(sinRemitenteDeSpring(), new MockEnvironment());

        assertThatThrownBy(() -> smtp.enviar("ana.mendoza@example.com", "Recordatorio", "Mensaje"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("SMTP no configurado");
    }

    @Test
    void conHostConfiguradoQuedaListoParaEnviar() {
        MockEnvironment entorno = new MockEnvironment()
            .withProperty("spring.mail.host", "smtp.correo.test")
            .withProperty("spring.mail.username", "recordatorios@clinica.test");

        EnvioCorreoSmtp smtp = new EnvioCorreoSmtp(sinRemitenteDeSpring(), entorno);

        assertThat(smtp.estaConfigurado()).isTrue();
    }

    @Test
    void conHostConfiguradoSinRemitenteDeSpringElEnvioFallaAntesDeConectar() {
        MockEnvironment entorno = new MockEnvironment()
            .withProperty("spring.mail.host", "smtp.correo.test");

        EnvioCorreoSmtp smtp = new EnvioCorreoSmtp(sinRemitenteDeSpring(), entorno);

        assertThatThrownBy(() -> smtp.enviar("ana.mendoza@example.com", "Recordatorio", "Mensaje"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("JavaMailSender");
    }
}
