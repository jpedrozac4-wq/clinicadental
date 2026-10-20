package com.empresa.calzado.erp.catalogo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the managed image directory. Every test uses an isolated {@link TempDir}, so the
 * repository's {@code storage/} directory is never touched.
 */
class ImagenCalzadoStorageTest {

    private static final String PREFIX = "/storage/calzado/";

    @TempDir
    Path tempDir;

    private ImagenCalzadoStorage storage;

    @BeforeEach
    void setUp() {
        storage = new ImagenCalzadoStorage(tempDir);
    }

    @Test
    void guardarStoresARealFileAndReturnsAManagedUrl() throws Exception {
        MultipartFile archivo = new MockMultipartFile(
            "imagen", "foto.jpg", "image/jpeg", "contenido".getBytes(StandardCharsets.UTF_8));

        String url = storage.guardar(archivo);

        assertThat(url).startsWith(PREFIX);
        String nombre = url.substring(PREFIX.length());
        assertThat(tempDir.resolve(nombre)).exists();
        assertThat(Files.readString(tempDir.resolve(nombre))).isEqualTo("contenido");
    }

    @Test
    void guardarReturnsNullForNullAndEmptyFiles() {
        assertThat(storage.guardar(null)).isNull();
        MultipartFile vacio = new MockMultipartFile("imagen", "foto.jpg", "image/jpeg", new byte[0]);
        assertThat(storage.guardar(vacio)).isNull();
    }

    @Test
    void guardarSanitizesAFileNameThatTriesToTraverse() {
        MultipartFile archivo = new MockMultipartFile(
            "imagen", "../../evil name.jpg", "image/jpeg", "x".getBytes(StandardCharsets.UTF_8));

        String nombre = storage.guardar(archivo).substring(PREFIX.length());

        assertThat(nombre).doesNotContain("/").doesNotContain("\\").doesNotContain("..");
    }

    @Test
    void guardarSanitizesADirectoryTraversalInTheExtension() {
        MultipartFile archivo = new MockMultipartFile(
            "imagen", "foto.jpg/../../x", "image/jpeg", "x".getBytes(StandardCharsets.UTF_8));

        String nombre = storage.guardar(archivo).substring(PREFIX.length());

        assertThat(nombre).doesNotContain("/").doesNotContain("\\").doesNotContain("..");
    }

    @Test
    void guardarFallsBackToJpgAndSanitizesLongOrDirtyExtensions() {
        String sinPunto = nombreDe(storage.guardar(new MockMultipartFile(
            "imagen", "foto", "image/jpeg", "x".getBytes(StandardCharsets.UTF_8))));
        assertThat(sinPunto).endsWith(".jpg");

        String largo = nombreDe(storage.guardar(new MockMultipartFile(
            "imagen", "foto.abcdefghijkl", "image/jpeg", "x".getBytes(StandardCharsets.UTF_8))));
        assertThat(largo).endsWith(".abcdefgh");

        String sucio = nombreDe(storage.guardar(new MockMultipartFile(
            "imagen", "foto.w!e@b#p", "image/jpeg", "x".getBytes(StandardCharsets.UTF_8))));
        assertThat(sucio).endsWith(".webp");
    }

    @Test
    void borrarDeletesAManagedFileAndReturnsTrue() throws Exception {
        Path archivo = tempDir.resolve("calzado_1_abc.jpg");
        Files.writeString(archivo, "x");

        assertThat(storage.borrar(PREFIX + "calzado_1_abc.jpg")).isTrue();
        assertThat(archivo).doesNotExist();
    }

    @Test
    void borrarIsIdempotentForAMissingManagedFile() {
        assertThat(storage.borrar(PREFIX + "no_existe.jpg")).isTrue();
        assertThat(storage.borrar(PREFIX + "no_existe.jpg")).isTrue();
    }

    @Test
    void borrarRefusesUnmanagedAndTraversalUrlsWithoutTouchingADecoy() throws Exception {
        Path decoy = tempDir.resolve("decoy.jpg");
        Files.writeString(decoy, "protegido");
        Path sub = tempDir.resolve("sub");
        Files.createDirectories(sub);

        assertThat(storage.borrar("/assets/img/calzado/default-shoe.webp")).isFalse();
        assertThat(storage.borrar("/etc/passwd")).isFalse();
        assertThat(storage.borrar("C:\\Windows\\System32\\config")).isFalse();
        assertThat(storage.borrar(null)).isFalse();
        assertThat(storage.borrar("   ")).isFalse();
        // Traversal that normalizes onto the decoy must be refused, not resolved.
        assertThat(storage.borrar(PREFIX + "sub/../decoy.jpg")).isFalse();
        assertThat(storage.borrar(PREFIX + "..")).isFalse();
        assertThat(decoy).exists();
    }

    @Test
    void esGestionadaOnlyAcceptsTheManagedPrefix() {
        assertThat(storage.esGestionada(PREFIX + "foto.jpg")).isTrue();
        assertThat(storage.esGestionada("/assets/img/calzado/default-shoe.webp")).isFalse();
        assertThat(storage.esGestionada(null)).isFalse();
        assertThat(storage.esGestionada("")).isFalse();
    }

    private static String nombreDe(String url) {
        return url.substring(PREFIX.length());
    }
}
