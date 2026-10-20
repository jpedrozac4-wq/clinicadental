package com.empresa.calzado.erp.reportes;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * Owns the managed {@code storage/facturas} directory. Files written here are published by the
 * existing {@code /storage/**} resource handler configured in {@code shared/WebConfig}, so a
 * generated document is downloadable over HTTP without any new mapping.
 *
 * <p>The file name is built from the invoice correlativo, but only {@code [A-Za-z0-9_-]} survives;
 * every other character becomes {@code _}. That makes a path separator, a backslash or a
 * {@code ..} segment impossible, and the resolved path is additionally checked to stay inside the
 * managed directory.
 */
@Component
public class FacturaPdfStorage {

    public static final String URL_PREFIX = "/storage/facturas/";

    private static final String NOMBRE_POR_DEFECTO = "factura";
    private static final String EXTENSION = ".pdf";

    private final Path directorio;

    public FacturaPdfStorage() {
        this(Paths.get("storage", "facturas"));
    }

    public FacturaPdfStorage(Path directorio) {
        this.directorio = directorio;
    }

    /**
     * Writes {@code contenido} as {@code <correlativo saneado>.pdf} and returns its public url.
     *
     * @throws IllegalArgumentException when the resolved path would escape the managed directory
     * @throws IllegalStateException when the file cannot be written
     */
    public String guardar(String correlativo, byte[] contenido) {
        String nombre = nombreSeguro(correlativo);
        Path destino = directorio.resolve(nombre).normalize();
        if (!destino.startsWith(directorio.normalize())) {
            throw new IllegalArgumentException("Correlativo de factura invalido");
        }
        try {
            Files.createDirectories(directorio);
            Files.write(destino, contenido,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new IllegalStateException(
                "No se pudo almacenar el PDF de la factura: " + e.getMessage(), e);
        }
        return URL_PREFIX + nombre;
    }

    /**
     * Reduces the correlativo to a single safe path segment: ASCII letters, digits, {@code -} and
     * {@code _} survive, everything else becomes {@code _}. A blank correlativo falls back to
     * {@code factura}.
     */
    private static String nombreSeguro(String correlativo) {
        if (correlativo == null || correlativo.isBlank()) {
            return NOMBRE_POR_DEFECTO + EXTENSION;
        }
        StringBuilder sb = new StringBuilder(correlativo.length());
        for (int i = 0; i < correlativo.length(); i++) {
            char c = correlativo.charAt(i);
            boolean seguro = (c >= 'A' && c <= 'Z')
                || (c >= 'a' && c <= 'z')
                || (c >= '0' && c <= '9')
                || c == '-'
                || c == '_';
            sb.append(seguro ? c : '_');
        }
        return sb + EXTENSION;
    }
}
