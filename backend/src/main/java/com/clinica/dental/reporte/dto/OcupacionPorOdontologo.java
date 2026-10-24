package com.clinica.dental.reporte.dto;

/** Citas atendidas por un odontologo dentro del rango consultado (S21). */
public record OcupacionPorOdontologo(Integer idDentista, String odontologo, Long cantidad) {
}
