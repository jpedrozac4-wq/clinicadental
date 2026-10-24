package com.clinica.dental.auditoria;

import com.clinica.dental.auditoria.dto.AuditoriaRegistroResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrato del texto de la consulta de la bitacora (S19).
 *
 * <p>Estas pruebas fijan las tres decisiones que un cambio descuidado puede romper en silencio: la
 * proyeccion apunta al DTO que publica el endpoint, los cuatro filtros siguen siendo opcionales y el
 * orden es del mas reciente al mas antiguo.
 *
 * <p>La consulta completa se compilo contra el metamodelo real de Hibernate y contra el esquema de
 * PostgreSQL durante la implementacion (incluido {@code hibernate.hbm2ddl.auto=validate}), pero esa
 * comprobacion necesita una base y por eso no queda como prueba automatica: la suite del proyecto es
 * de unidad y corre sin base de datos.
 */
class AuditLogRepositoryQueryTest {

    private static Query anotacion() {
        try {
            Method buscar = AuditLogRepository.class.getMethod("buscar", OffsetDateTime.class,
                OffsetDateTime.class, String.class, String.class, Pageable.class);
            Query anotacion = buscar.getAnnotation(Query.class);
            assertThat(anotacion).isNotNull();
            return anotacion;
        } catch (NoSuchMethodException ex) {
            throw new AssertionError("AuditLogRepository ya no declara la consulta paginada", ex);
        }
    }

    @Test
    void laProyeccionApuntaAlDtoQuePublicaElEndpoint() {
        assertThat(anotacion().value())
            .contains("new " + AuditoriaRegistroResponse.class.getName() + "(");
    }

    @Test
    void losCuatroFiltrosSiguenSiendoOpcionales() {
        assertThat(anotacion().value())
            .contains("(:desde IS NULL OR a.creadoEn >= :desde)")
            .contains("(:hasta IS NULL OR a.creadoEn < :hasta)")
            .contains("(:accion IS NULL OR catalogo.code = :accion)")
            .contains("(:usuario IS NULL OR u.username = :usuario)");
    }

    @Test
    void elOrdenEsDelMasRecienteAlMasAntiguoYDesempataPorId() {
        assertThat(anotacion().value()).contains("ORDER BY a.creadoEn DESC, a.id DESC");
    }

    @Test
    void elConteoExisteYNoArrastraElOrden() {
        assertThat(anotacion().countQuery())
            .isNotBlank()
            .contains("SELECT COUNT(a)")
            .doesNotContain("ORDER BY");
    }

    @Test
    void elRangoDeFechasEsInclusivoEnElExtremoSuperiorConUnLimiteExclusivo() {
        // "hasta" se traduce a un limite exclusivo (>= desde y < hasta) para no perder el ultimo dia.
        assertThat(anotacion().value()).contains("a.creadoEn < :hasta");
    }
}
