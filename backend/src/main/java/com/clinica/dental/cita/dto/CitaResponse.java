package com.clinica.dental.cita.dto;

import com.clinica.dental.cita.Cita;

import java.time.OffsetDateTime;

public record CitaResponse(
    Integer id,
    Integer idPaciente,
    String paciente,
    Integer idTratamiento,
    String tratamiento,
    Integer idDentista,
    String dentista,
    String estado,
    OffsetDateTime fechaHoraInicio,
    OffsetDateTime fechaHoraFin,
    Integer duracionMinutos,
    String motivo,
    String notas,
    Integer idCitaOrigen
) {

    public static CitaResponse desde(Cita c) {
        return new CitaResponse(
            c.getId(),
            c.getPaciente().getId(),
            c.getPaciente().getNombres() + " " + c.getPaciente().getApellidos(),
            c.getTratamiento().getId(),
            c.getTratamiento().getNombre(),
            c.getDentista().getId(),
            c.getDentista().getFullName(),
            c.getEstado().getCode(),
            c.getFechaHoraInicio(),
            c.getFechaHoraInicio().plusMinutes(c.getDuracionMinutos()),
            c.getDuracionMinutos(),
            c.getMotivo(),
            c.getNotas(),
            c.getCitaOrigen() == null ? null : c.getCitaOrigen().getId());
    }
}
