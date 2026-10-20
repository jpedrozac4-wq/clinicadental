package com.empresa.calzado.erp.catalogo;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the {@code jsonb} mapping of {@link ModeloColor#getGaleriaFotosJson()}.
 *
 * <p>Without {@code @JdbcTypeCode(SqlTypes.JSON)} Hibernate binds the String as {@code varchar} and
 * PostgreSQL rejects the insert with SQLState 42804 ("column galeria_fotos_json is of type jsonb but
 * expression is of type character varying"), which surfaces as a 500 on the first colour variant
 * created. The mocked service and controller tests cannot observe that, because no SQL runs there,
 * so the mapping itself is asserted here.
 */
class ModeloColorJsonMappingTest {

    @Test
    void galeriaFotosJsonIsMappedAsJson() throws NoSuchFieldException {
        Field campo = ModeloColor.class.getDeclaredField("galeriaFotosJson");

        JdbcTypeCode anotacion = campo.getAnnotation(JdbcTypeCode.class);

        assertThat(anotacion)
            .as("galeriaFotosJson debe declarar @JdbcTypeCode(SqlTypes.JSON) para bindear jsonb")
            .isNotNull();
        assertThat(anotacion.value()).isEqualTo(SqlTypes.JSON);
    }
}
