package com.clinica.dental.historial;

import com.clinica.dental.cita.CitaRepository;
import com.clinica.dental.historial.dto.HistorialResponse;
import com.clinica.dental.paciente.Paciente;
import com.clinica.dental.paciente.PacienteRepository;
import com.clinica.dental.realizado.TratamientoRealizadoRepository;
import com.clinica.dental.shared.ClinicaBusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Historial clinico por paciente (S3).
 *
 * <p>Se consulta tambien para pacientes dados de baja (con {@code findById}, no
 * {@code findByIdAndActiveTrue}): dar de baja a alguien no puede borrar su historia clinica.
 */
@Service
public class HistorialService {

    private final PacienteRepository pacientes;
    private final CitaRepository citas;
    private final TratamientoRealizadoRepository realizados;

    public HistorialService(PacienteRepository pacientes, CitaRepository citas,
                            TratamientoRealizadoRepository realizados) {
        this.pacientes = pacientes;
        this.citas = citas;
        this.realizados = realizados;
    }

    @Transactional(readOnly = true)
    public HistorialResponse dePaciente(Integer idPaciente) {
        Paciente paciente = pacientes.findById(idPaciente)
            .orElseThrow(() -> new ClinicaBusinessException(HttpStatus.NOT_FOUND,
                "Paciente no encontrado"));

        List<HistorialResponse.CitaHistorial> citasDelPaciente = citas.historialDePaciente(idPaciente)
            .stream()
            .map(c -> new HistorialResponse.CitaHistorial(
                c.getId(), c.getFechaHoraInicio(), c.getTratamiento().getNombre(),
                c.getEstado().getCode(), c.getMotivo()))
            .toList();

        List<HistorialResponse.RealizadoHistorial> tratamientos = realizados
            .findByPacienteIdOrderByFechaRealizacionDesc(idPaciente)
            .stream()
            .map(r -> new HistorialResponse.RealizadoHistorial(
                r.getId(), r.getFechaRealizacion(), r.getTratamiento().getNombre(),
                r.getDetalleProcedimiento(), r.getCosto()))
            .toList();

        return new HistorialResponse(
            paciente.getId(),
            paciente.getNombres() + " " + paciente.getApellidos(),
            citasDelPaciente,
            tratamientos);
    }
}
