package com.empresa.calzado.erp.catalogo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Owns the managed directory where catalogue images are stored and is the only place that
 * touches those files.
 *
 * <p>Managed images live under {@code storage/calzado} and are published as
 * {@code /storage/calzado/<nombre>} by {@code WebConfig}. An upload always produces a synthetic,
 * collision-resistant filename; the original filename is never used verbatim, so a crafted name
 * can never inject a path separator or a {@code ..} traversal into the stored path.
 *
 * <p>Deletion accepts only a url under {@link #URL_PREFIX}. The remaining path is reduced to its
 * last segment and rejected when it is empty, contains {@code /} or {@code \}, contains a
 * {@code ..} segment, or resolves outside the storage root after {@link Path#normalize()}. Any
 * url that fails those checks (an unmanaged url, a foreign absolute path, a traversal) is refused
 * and never deletes anything.
 */
@Component
public class ImagenCalzadoStorage {

    private static final Logger log = LoggerFactory.getLogger(ImagenCalzadoStorage.class);
    private static final String URL_PREFIX = "/storage/calzado/";
    private static final String EXTENSION_FALLBACK = ".jpg";
    private static final int EXTENSION_MAX_LENGTH = 8;
    private static final int UUID_TOKEN_LENGTH = 6;

    private final Path storageRoot;

    public ImagenCalzadoStorage() {
        this(Paths.get("storage", "calzado"));
    }

    ImagenCalzadoStorage(Path storageRoot) {
        this.storageRoot = storageRoot;
        try {
            Files.createDirectories(storageRoot);
        } catch (IOException e) {
            log.warn("No se pudo crear el directorio de imagenes de calzado {}: {}",
                storageRoot, e.getMessage());
        }
    }

    /**
     * Stores the uploaded image and returns its public url, or {@code null} when the file is
     * absent or empty. The stored extension is taken from the original filename but sanitized to
     * at most {@value #EXTENSION_MAX_LENGTH} alphanumeric characters, falling back to
     * {@code .jpg} when nothing usable remains.
     *
     * @throws CatalogoBusinessException when the file cannot be copied to the storage root
     */
    public String guardar(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            return null;
        }
        String nombre = "calzado_" + System.currentTimeMillis() + "_"
            + UUID.randomUUID().toString().substring(0, UUID_TOKEN_LENGTH)
            + extensionSegura(archivo.getOriginalFilename());
        Path destino = storageRoot.resolve(nombre);
        try (InputStream is = archivo.getInputStream()) {
            Files.copy(is, destino, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new CatalogoBusinessException(
                "Error al almacenar la imagen del calzado en disco: " + e.getMessage());
        }
        return URL_PREFIX + nombre;
    }

    /**
     * Deletes the file behind a managed url.
     *
     * @return {@code true} when the url is managed and the file is gone (or was already gone),
     *     {@code false} when the url is null, blank, unmanaged or unsafe
     */
    public boolean borrar(String url) {
        Path objetivo = resolverGestionada(url);
        if (objetivo == null) {
            return false;
        }
        try {
            Files.deleteIfExists(objetivo);
            return true;
        } catch (IOException e) {
            log.warn("No se pudo borrar la imagen de calzado {}: {}", objetivo, e.getMessage());
            return false;
        }
    }

    /**
     * Returns {@code true} only for urls under {@value #URL_PREFIX}, without validating the
     * remaining path.
     */
    public boolean esGestionada(String url) {
        return url != null && url.startsWith(URL_PREFIX);
    }

    /**
     * Resolves a managed url to a file directly inside the storage root, or {@code null} when the
     * url is unmanaged or unsafe.
     */
    private Path resolverGestionada(String url) {
        if (!esGestionada(url)) {
            return null;
        }
        String resto = url.substring(URL_PREFIX.length());
        Path segmento = Paths.get(resto).getFileName();
        if (segmento == null) {
            return null;
        }
        String nombre = segmento.toString();
        if (nombre.isBlank()
            || nombre.contains("/")
            || nombre.contains("\\")
            || nombre.contains("..")
            || resto.contains("/")
            || resto.contains("\\")) {
            return null;
        }
        Path resuelto = storageRoot.resolve(nombre).normalize();
        if (!resuelto.startsWith(storageRoot.normalize())) {
            return null;
        }
        return resuelto;
    }

    /**
     * Builds a safe extension from the original filename: only alphanumerics survive, at most
     * {@value #EXTENSION_MAX_LENGTH} of them, always prefixed with a dot.
     */
    private static String extensionSegura(String originalName) {
        if (originalName == null) {
            return EXTENSION_FALLBACK;
        }
        int punto = originalName.lastIndexOf('.');
        if (punto < 0 || punto == originalName.length() - 1) {
            return EXTENSION_FALLBACK;
        }
        String limpio = originalName.substring(punto + 1).replaceAll("[^A-Za-z0-9]", "");
        if (limpio.isEmpty()) {
            return EXTENSION_FALLBACK;
        }
        if (limpio.length() > EXTENSION_MAX_LENGTH) {
            limpio = limpio.substring(0, EXTENSION_MAX_LENGTH);
        }
        return "." + limpio;
    }
}
