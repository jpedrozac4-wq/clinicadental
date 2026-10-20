package com.empresa.calzado.erp.catalogo;

import org.springframework.http.HttpStatus;

/**
 * Business failure raised by the catalogue administration. It carries the HTTP status the admin
 * controller should expose, so callers get a readable {@code {"status":"ERROR","message":...}}
 * payload instead of a bare 500.
 */
public class CatalogoBusinessException extends RuntimeException {

    private final HttpStatus status;

    public CatalogoBusinessException(String message) {
        this(HttpStatus.BAD_REQUEST, message);
    }

    public CatalogoBusinessException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
