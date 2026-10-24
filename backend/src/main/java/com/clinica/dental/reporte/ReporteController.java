package com.clinica.dental.reporte;

import com.clinica.dental.reporte.dto.IngresosResponse;
import com.clinica.dental.reporte.dto.OcupacionResponse;
import com.clinica.dental.reporte.dto.ProduccionResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Reportes y estadisticas de solo lectura (S21).
 *
 * <p>Los tres endpoints son {@code GET}, no aceptan cuerpo ni escriben nada, y quedan bajo la guarda
 * {@code OP_REPORTE_VER} de {@code SecurityConfig}.
 *
 * <p>Todos comparten el mismo contrato de rango y por eso la misma resolucion de {@code desde} y
 * {@code hasta}:
 * <ul>
 *   <li>las dos fechas son opcionales y se envian como {@code YYYY-MM-DD};</li>
 *   <li>si falta {@code desde} se usa el primer dia del mes en curso, y si falta {@code hasta} se usa
 *       el ultimo dia del mismo mes;</li>
 *   <li>el rango es inclusivo en ambos extremos;</li>
 *   <li>un rango con {@code desde} posterior a {@code hasta} devuelve {@code 400};</li>
 *   <li>una fecha que no parsea como {@code YYYY-MM-DD} devuelve {@code 400}.</li>
 * </ul>
 *
 * <p>Sin datos cada endpoint responde estructuras vacias con los totales en cero, nunca {@code null}.
 */
@RestController
@RequestMapping("/api/reportes")
public class ReporteController {

    private final ReporteService service;

    public ReporteController(ReporteService service) {
        this.service = service;
    }

    /**
     * Total cobrado en el rango, desglosado por metodo de pago y por mes.
     *
     * <p>Sin {@code desde} se usa el primer dia del mes en curso y sin {@code hasta} el ultimo dia del
     * mes en curso. Solo cuentan los pagos de facturas no anuladas.
     */
    @GetMapping("/ingresos")
    public IngresosResponse ingresos(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return service.ingresos(desde, hasta);
    }

    /**
     * Tratamientos realizados en el rango, con cantidad e importe por odontologo.
     *
     * <p>Sin {@code desde} se usa el primer dia del mes en curso y sin {@code hasta} el ultimo dia del
     * mes en curso.
     */
    @GetMapping("/produccion")
    public ProduccionResponse produccion(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return service.produccion(desde, hasta);
    }

    /**
     * Citas cuya fecha y hora de inicio cae en el rango, por estado y por odontologo.
     *
     * <p>Sin {@code desde} se usa el primer dia del mes en curso y sin {@code hasta} el ultimo dia del
     * mes en curso.
     */
    @GetMapping("/ocupacion")
    public OcupacionResponse ocupacion(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return service.ocupacion(desde, hasta);
    }
}
