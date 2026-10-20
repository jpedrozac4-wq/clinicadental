package com.empresa.calzado.erp.reportes;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Storage tests for the managed {@code storage/facturas} directory. Every test uses an isolated
 * {@link TempDir}, so the repository's {@code storage/} directory is never touched.
 */
class FacturaPdfStorageTest {

    private static final String PREFIX = FacturaPdfStorage.URL_PREFIX;

    @TempDir
    Path tempDir;

    @Test
    void guardarEscribeLosMismosBytesYDevuelveUnaUrlBajoElPrefijo() throws Exception {
        FacturaPdfStorage storage = new FacturaPdfStorage(tempDir);
        byte[] contenido = {0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34, 0x0A};

        String url = storage.guardar("TKT-ABC123", contenido);

        assertThat(url).startsWith(PREFIX);
        assertThat(url).endsWith("TKT-ABC123.pdf");
        Path archivo = tempDir.resolve("TKT-ABC123.pdf");
        assertThat(archivo).exists();
        assertThat(Files.readAllBytes(archivo)).isEqualTo(contenido);
    }

    @Test
    void guardarCreaElDirectorioSiNoExiste() {
        Path raiz = tempDir.resolve("no_existe");
        FacturaPdfStorage storage = new FacturaPdfStorage(raiz);

        String url = storage.guardar("TKT-1", new byte[]{1});

        assertThat(url).startsWith(PREFIX);
        assertThat(raiz).isDirectory();
    }

    @Test
    void unCorrelativoConBarrasNoPuedeEscaparDelDirectorio() {
        FacturaPdfStorage storage = new FacturaPdfStorage(tempDir);

        String url = storage.guardar("../../evil", new byte[]{1});

        assertThat(url).startsWith(PREFIX);
        assertThat(url).doesNotContain("..");
        String nombre = url.substring(PREFIX.length());
        assertThat(nombre).doesNotContain("/");
        Path resuelto = tempDir.resolve(nombre).normalize();
        assertThat(resuelto.startsWith(tempDir.normalize())).isTrue();
        assertThat(tempDir.getParent().resolve("evil.pdf")).doesNotExist();
    }

    @Test
    void unCorrelativoConBarraInvertidaOPuntosNoPuedeEscaparDelDirectorio() {
        FacturaPdfStorage storage = new FacturaPdfStorage(tempDir);

        String url = storage.guardar("..\\..\\evil", new byte[]{1});

        assertThat(url).doesNotContain("..").doesNotContain("\\");
        String nombre = url.substring(PREFIX.length());
        Path resuelto = tempDir.resolve(nombre).normalize();
        assertThat(resuelto.startsWith(tempDir.normalize())).isTrue();
    }

    @Test
    void elNombreDelArchivoNuncaContieneSeparadores() {
        FacturaPdfStorage storage = new FacturaPdfStorage(tempDir);

        String url = storage.guardar("a/b\\c..d", new byte[]{1});
        String nombre = url.substring(PREFIX.length());

        assertThat(nombre).doesNotContain("/").doesNotContain("\\");
        assertThat(nombre).endsWith(".pdf");
    }
}
