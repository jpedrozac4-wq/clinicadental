package com.clinica.dental.auditoria;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Detalle que acompana a una fila de auditoria (S19).
 *
 * <p>Es un objeto JSON pequeno, construido en el punto de llamada con {@code con(clave, valor)}.
 * {@code con} ignora los valores {@code null}: el detalle publica solo lo que efectivamente ocurrio
 * y nunca inventa claves vacias, de modo que {@code Map.of} no aplica y no se puede tropezar con su
 * rechazo a los nulos.
 *
 * <p>Los valores se normalizan a tipos que Jackson serializa sin ambiguedad: las fechas van como
 * texto ISO-8601, los enums por su nombre y los montos como {@link BigDecimal}, que se escribe como
 * numero. Nunca se agregan contrasenias ni hashes: las operaciones de usuario solo publican
 * identidad, rol y estado.
 */
public final class DetalleAuditoria {

    private final Map<String, Object> valores = new LinkedHashMap<>();

    private DetalleAuditoria() {
    }

    /** Detalle vacio, listo para encadenar {@code con}. */
    public static DetalleAuditoria nuevo() {
        return new DetalleAuditoria();
    }

    /** Agrega un par clave/valor; un valor {@code null} deja el detalle intacto. */
    public DetalleAuditoria con(String clave, Object valor) {
        if (clave != null && valor != null) {
            valores.put(clave, normalizar(valor));
        }
        return this;
    }

    /** Vista de solo lectura de los pares agregados, en el orden en que se construyeron. */
    public Map<String, Object> valores() {
        return Collections.unmodifiableMap(valores);
    }

    private static Object normalizar(Object valor) {
        if (valor instanceof OffsetDateTime fecha) {
            return fecha.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }
        if (valor instanceof LocalDate fecha) {
            return fecha.toString();
        }
        if (valor instanceof Enum<?> constante) {
            return constante.name();
        }
        return valor;
    }
}
