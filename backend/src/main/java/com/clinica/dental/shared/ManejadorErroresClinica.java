package com.clinica.dental.shared;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Traduce las fallas del dominio a la carga de error canonica de la aplicacion.
 *
 * <p>La aplicacion expone siempre el mismo contrato de error {@code {"status":"ERROR","message":...}}
 * para que el cliente HTTP del frontend pueda mostrar un mensaje unico sin ramificar por endpoint.
 */
@RestControllerAdvice
public class ManejadorErroresClinica {

    @ExceptionHandler(ClinicaBusinessException.class)
    public ResponseEntity<Map<String, Object>> manejarErrorDeNegocio(ClinicaBusinessException ex) {
        return error(ex.getStatus(), ex.getMessage());
    }

    /** Datos que rompen una restriccion de la base: se informa como solicitud invalida. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> manejarViolacionDeIntegridad(DataIntegrityViolationException ex) {
        return error(HttpStatus.BAD_REQUEST,
            "Los datos enviados violan una restriccion de integridad de la base de datos");
    }

    /** Bean Validation sobre el cuerpo de la peticion. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> manejarValidacion(MethodArgumentNotValidException ex) {
        String detalle = ex.getBindingResult().getFieldErrors().stream()
            .map(ManejadorErroresClinica::describir)
            .sorted()
            .collect(Collectors.joining("; "));
        return error(HttpStatus.BAD_REQUEST, detalle.isEmpty() ? "Datos invalidos" : detalle);
    }

    /** JSON malformado o con un tipo incompatible en algun campo. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> manejarCuerpoIlegible(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "El cuerpo de la peticion no es JSON valido para este recurso");
    }

    private static String describir(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ERROR");
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
