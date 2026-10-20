package com.clinica.dental.tratamiento.dto;

import com.clinica.dental.tratamiento.Tratamiento;

import java.math.BigDecimal;

public record TratamientoResponse(
    Integer id,
    String codigo,
    String nombre,
    String descripcion,
    BigDecimal precio,
    Integer duracionMinutos
) {

    public static TratamientoResponse desde(Tratamiento t) {
        return new TratamientoResponse(t.getId(), t.getCodigo(), t.getNombre(),
            t.getDescripcion(), t.getPrecio(), t.getDuracionMinutos());
    }
}
