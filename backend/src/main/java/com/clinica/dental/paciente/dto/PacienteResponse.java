package com.clinica.dental.paciente.dto;

import com.clinica.dental.paciente.Paciente;

import java.time.LocalDate;

public record PacienteResponse(
    Integer id,
    String nombres,
    String apellidos,
    LocalDate fechaNacimiento,
    String telefono,
    String direccion,
    String email
) {

    public static PacienteResponse desde(Paciente p) {
        return new PacienteResponse(
            p.getId(), p.getNombres(), p.getApellidos(), p.getFechaNacimiento(),
            p.getTelefono(), p.getDireccion(), p.getEmail());
    }
}
