package com.clinica.dental.recordatorio;

/**
 * Colaborador de envio de recordatorios.
 *
 * <p>Es una interfaz deliberadamente pequena para que la suite pueda sustituirla por un doble y
 * ningun test abra una conexion real con un servidor SMTP (S5, S15).
 */
public interface EnvioCorreo {

    /** Indica si hay un servidor configurado; sin el, el recordatorio queda {@code SIMULADA}. */
    boolean estaConfigurado();

    /**
     * Entrega el recordatorio al destino indicado.
     *
     * @throws RuntimeException si el servidor rechaza o no responde el envio
     */
    void enviar(String destino, String asunto, String mensaje);
}
