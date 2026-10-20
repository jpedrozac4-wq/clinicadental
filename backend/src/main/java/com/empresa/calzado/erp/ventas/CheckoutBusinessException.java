package com.empresa.calzado.erp.ventas;

import org.springframework.http.HttpStatus;

/**
 * Business-level checkout failure mapped by the controller to an HTTP status and the shared
 * {@code {"status":"ERROR","message":...}} envelope, mirroring
 * {@code catalogo.CatalogoBusinessException}.
 */
public class CheckoutBusinessException extends RuntimeException {

    private final HttpStatus status;

    public CheckoutBusinessException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
