package com.clinica.dental.shared;

import org.springframework.http.HttpStatus;

/**
 * Falla de negocio del dominio clinico.
 *
 * <p>Transporta el status HTTP que el manejador global debe exponer, de modo que el cliente reciba
 * una carga legible {@code {"status":"ERROR","message":...}} en lugar de un 500 generico.
 */
public class ClinicaBusinessException extends RuntimeException {

    private final HttpStatus status;

    public ClinicaBusinessException(String message) {
        this(HttpStatus.BAD_REQUEST, message);
    }

    public ClinicaBusinessException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
