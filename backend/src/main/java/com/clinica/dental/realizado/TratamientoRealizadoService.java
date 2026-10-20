package com.clinica.dental.realizado;

import com.clinica.dental.cita.Cita;
import com.clinica.dental.cita.CitaRepository;
import com.clinica.dental.cita.CitaService;
import com.clinica.dental.paciente.Paciente;
import com.clinica.dental.paciente.PacienteRepository;
import com.clinica.dental.realizado.dto.RealizadoRequest;
import com.clinica.dental.realizado.dto.RealizadoResponse;
import com.clinica.dental.seguridad.SystemUser;
import com.clinica.dental.seguridad.SystemUserRepository;
import com.clinica.dental.shared.ClinicaBusinessException;
import com.clinica.dental.tratamiento.Tratamiento;
import com.clinica.dental.tratamiento.TratamientoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Registro de tratamientos realizados por paciente (S8).
 *
 * <p>El costo se congela con el precio del catalogo, y si el procedimiento viene de una cita, esa
 * cita queda {@code ATENDIDA}: la agenda y el historial clinico no pueden contarse historias
 * distintas.
 */
@Service
public class TratamientoRealizadoService {

    private final TratamientoRealizadoRepository realizados;
    private final PacienteRepository pacientes;
    private final TratamientoRepository tratamientos;
    private final SystemUserRepository usuarios;
    private final CitaRepository citas;
    private final CitaService citaService;

    public TratamientoRealizadoService(TratamientoRealizadoRepository realizados,
                                       PacienteRepository pacientes,
                                       TratamientoRepository tratamientos,
                                       SystemUserRepository usuarios,
                                       CitaRepository citas,
                                       CitaService citaService) {
        this.realizados = realizados;
        this.pacientes = pacientes;
        this.tratamientos = tratamientos;
        this.usuarios = usuarios;
        this.citas = citas;
        this.citaService = citaService;
    }

    @Transactional
    public RealizadoResponse registrar(RealizadoRequest request) {
        validar(request);

        Paciente paciente = pacientes.findByIdAndActiveTrue(request.idPaciente())
            .orElseThrow(() -> new ClinicaBusinessException(HttpStatus.NOT_FOUND,
                "Paciente no encontrado o dado de baja"));
        Tratamiento tratamiento = tratamientos.findByIdAndActiveTrue(request.idTratamiento())
            .orElseThrow(() -> new ClinicaBusinessException(HttpStatus.NOT_FOUND,
                "Tratamiento no encontrado o inactivo"));
        SystemUser dentista = usuarios.findById(request.idDentista())
            .filter(u -> Boolean.TRUE.equals(u.getActive()))
            .orElseThrow(() -> new ClinicaBusinessException(HttpStatus.NOT_FOUND,
                "Dentista no encontrado"));

        Cita cita = null;
        if (request.idCita() != null) {
            cita = citas.findById(request.idCita())
                .orElseThrow(() -> new ClinicaBusinessException(HttpStatus.NOT_FOUND,
                    "Cita no encontrada"));
            if (!cita.getPaciente().getId().equals(paciente.getId())) {
                throw new ClinicaBusinessException(HttpStatus.CONFLICT,
                    "La cita indicada no pertenece al paciente");
            }
        }

        TratamientoRealizado realizado = new TratamientoRealizado();
        realizado.setPaciente(paciente);
        realizado.setTratamiento(tratamiento);
        realizado.setCita(cita);
        realizado.setDentista(dentista);
        realizado.setFechaRealizacion(request.fechaRealizacion());
        realizado.setDetalleProcedimiento(request.detalleProcedimiento().trim());
        realizado.setObservaciones(request.observaciones() == null ? null : request.observaciones().trim());
        realizado.setCosto(tratamiento.getPrecio());

        TratamientoRealizado guardado = realizados.save(realizado);

        if (cita != null) {
            citaService.marcarAtendida(cita.getId(), paciente.getId());
        }
        return RealizadoResponse.desde(guardado);
    }

    @Transactional(readOnly = true)
    public List<RealizadoResponse> listar(Integer idPaciente) {
        List<TratamientoRealizado> encontrados = idPaciente == null
            ? realizados.findAllByOrderByFechaRealizacionDesc()
            : realizados.findByPacienteIdOrderByFechaRealizacionDesc(idPaciente);
        return encontrados.stream().map(RealizadoResponse::desde).toList();
    }

    private static void validar(RealizadoRequest request) {
        if (request == null) {
            throw new ClinicaBusinessException("Los datos del tratamiento realizado son obligatorios");
        }
        if (request.detalleProcedimiento() == null || request.detalleProcedimiento().isBlank()) {
            throw new ClinicaBusinessException("El detalle del procedimiento es obligatorio");
        }
        if (request.fechaRealizacion() == null) {
            throw new ClinicaBusinessException("La fecha de realizacion es obligatoria");
        }
        if (request.fechaRealizacion().isAfter(OffsetDateTime.now())) {
            throw new ClinicaBusinessException(HttpStatus.UNPROCESSABLE_ENTITY,
                "La fecha de realizacion no puede ser futura");
        }
    }
}
