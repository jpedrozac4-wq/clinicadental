package com.clinica.dental.cita;

import com.clinica.dental.cita.dto.CitaRequest;
import com.clinica.dental.cita.dto.CitaResponse;
import com.clinica.dental.cita.dto.ReprogramarRequest;
import com.clinica.dental.paciente.Paciente;
import com.clinica.dental.paciente.PacienteRepository;
import com.clinica.dental.seguridad.SystemUser;
import com.clinica.dental.seguridad.SystemUserRepository;
import com.clinica.dental.shared.ClinicaBusinessException;
import com.clinica.dental.tratamiento.Tratamiento;
import com.clinica.dental.tratamiento.TratamientoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Agenda del consultorio (S4, S6).
 *
 * <p>Una cita ocupa un bloque concreto del dentista: la duracion sale del catalogo de tratamientos y
 * el fin se calcula en la base. Solo las citas {@code PROGRAMADA} y {@code CONFIRMADA} reservan
 * horario, y una cita ya {@code ATENDIDA} queda fuera de reprogramacion y cancelacion.
 */
@Service
public class CitaService {

    /** Vista por defecto de la agenda cuando el cliente no pide un rango. */
    private static final int DIAS_AGENDA_POR_DEFECTO = 30;

    private final CitaRepository citas;
    private final EstadoCitaRepository estados;
    private final PacienteRepository pacientes;
    private final TratamientoRepository tratamientos;
    private final SystemUserRepository usuarios;

    public CitaService(CitaRepository citas, EstadoCitaRepository estados, PacienteRepository pacientes,
                       TratamientoRepository tratamientos, SystemUserRepository usuarios) {
        this.citas = citas;
        this.estados = estados;
        this.pacientes = pacientes;
        this.tratamientos = tratamientos;
        this.usuarios = usuarios;
    }

    @Transactional
    public CitaResponse crear(CitaRequest request) {
        Paciente paciente = pacientes.findByIdAndActiveTrue(request.idPaciente())
            .orElseThrow(() -> new ClinicaBusinessException(HttpStatus.NOT_FOUND,
                "Paciente no encontrado o dado de baja"));
        Tratamiento tratamiento = tratamientos.findByIdAndActiveTrue(request.idTratamiento())
            .orElseThrow(() -> new ClinicaBusinessException(HttpStatus.NOT_FOUND,
                "Tratamiento no encontrado o inactivo"));
        SystemUser dentista = dentistaVigente(request.idDentista());
        verificarFechaFutura(request.fechaHoraInicio());
        verificarDisponibilidad(dentista.getId(), request.fechaHoraInicio(), tratamiento.getDuracionMinutos());

        Cita cita = new Cita();
        cita.setPaciente(paciente);
        cita.setTratamiento(tratamiento);
        cita.setDentista(dentista);
        cita.setEstado(estado(EstadoCita.PROGRAMADA));
        cita.setFechaHoraInicio(request.fechaHoraInicio());
        cita.setDuracionMinutos(tratamiento.getDuracionMinutos());
        cita.setMotivo(request.motivo() == null ? null : request.motivo().trim());
        return CitaResponse.desde(citas.save(cita));
    }

    /**
     * Reprograma una cita: la original queda {@code REPROGRAMADA} y nace una nueva {@code PROGRAMADA}
     * vinculada a ella, de modo que el historial muestre ambas sin perder trazabilidad (S6).
     */
    @Transactional
    public CitaResponse reprogramar(Integer id, ReprogramarRequest request) {
        Cita original = buscar(id);
        if (EstadoCita.ATENDIDA.equals(original.getEstado().getCode())) {
            throw new ClinicaBusinessException(HttpStatus.CONFLICT,
                "Una cita ya atendida no puede reprogramarse");
        }
        SystemUser dentista = request.idDentista() == null
            ? original.getDentista()
            : dentistaVigente(request.idDentista());
        verificarFechaFutura(request.fechaHoraInicio());
        verificarDisponibilidad(dentista.getId(), request.fechaHoraInicio(), original.getDuracionMinutos());

        original.setEstado(estado(EstadoCita.REPROGRAMADA));
        citas.save(original);

        Cita nueva = new Cita();
        nueva.setPaciente(original.getPaciente());
        nueva.setTratamiento(original.getTratamiento());
        nueva.setDentista(dentista);
        nueva.setEstado(estado(EstadoCita.PROGRAMADA));
        nueva.setFechaHoraInicio(request.fechaHoraInicio());
        nueva.setDuracionMinutos(original.getDuracionMinutos());
        nueva.setMotivo(original.getMotivo());
        nueva.setNotas(original.getNotas());
        nueva.setCitaOrigen(original);
        return CitaResponse.desde(citas.save(nueva));
    }

    /** Cancela una cita vigente. Repetir la cancelacion no vuelve a escribir (S6). */
    @Transactional
    public void cancelar(Integer id) {
        Cita cita = buscar(id);
        if (EstadoCita.ATENDIDA.equals(cita.getEstado().getCode())) {
            throw new ClinicaBusinessException(HttpStatus.CONFLICT,
                "Una cita ya atendida no puede cancelarse");
        }
        if (EstadoCita.CANCELADA.equals(cita.getEstado().getCode())) {
            return;
        }
        cita.setEstado(estado(EstadoCita.CANCELADA));
        citas.save(cita);
    }

    /** Marca la cita como atendida cuando se registra el tratamiento realizado (S8). */
    @Transactional
    public void marcarAtendida(Integer idCita, Integer idPaciente) {
        Cita cita = buscar(idCita);
        if (!cita.getPaciente().getId().equals(idPaciente)) {
            throw new ClinicaBusinessException(HttpStatus.CONFLICT,
                "La cita indicada no pertenece al paciente");
        }
        if (EstadoCita.ATENDIDA.equals(cita.getEstado().getCode())) {
            return;
        }
        cita.setEstado(estado(EstadoCita.ATENDIDA));
        citas.save(cita);
    }

    @Transactional(readOnly = true)
    public List<CitaResponse> agenda(OffsetDateTime desde, OffsetDateTime hasta) {
        OffsetDateTime inicio = desde != null
            ? desde
            : OffsetDateTime.now().truncatedTo(ChronoUnit.DAYS);
        OffsetDateTime fin = hasta != null ? hasta : inicio.plusDays(DIAS_AGENDA_POR_DEFECTO);
        return citas.agenda(inicio, fin).stream().map(CitaResponse::desde).toList();
    }

    @Transactional(readOnly = true)
    public CitaResponse obtener(Integer id) {
        return CitaResponse.desde(buscar(id));
    }

    private Cita buscar(Integer id) {
        return citas.findById(id)
            .orElseThrow(() -> new ClinicaBusinessException(HttpStatus.NOT_FOUND, "Cita no encontrada"));
    }

    private SystemUser dentistaVigente(Integer idDentista) {
        return usuarios.findById(idDentista)
            .filter(u -> Boolean.TRUE.equals(u.getActive()))
            .orElseThrow(() -> new ClinicaBusinessException(HttpStatus.NOT_FOUND,
                "Dentista no encontrado"));
    }

    private static void verificarFechaFutura(OffsetDateTime inicio) {
        if (inicio == null) {
            throw new ClinicaBusinessException("La fecha y hora de la cita son obligatorias");
        }
        if (!inicio.isAfter(OffsetDateTime.now())) {
            throw new ClinicaBusinessException(HttpStatus.UNPROCESSABLE_ENTITY,
                "La fecha y hora de la cita debe ser futura");
        }
    }

    private void verificarDisponibilidad(Integer idDentista, OffsetDateTime inicio, Integer duracionMinutos) {
        OffsetDateTime fin = inicio.plusMinutes(duracionMinutos);
        if (citas.contarSolapadas(idDentista, inicio, fin) > 0) {
            throw new ClinicaBusinessException(HttpStatus.CONFLICT,
                "El dentista ya tiene una cita en ese horario");
        }
    }

    private EstadoCita estado(String code) {
        return estados.findByCode(code)
            .orElseThrow(() -> new IllegalStateException("Catalogo de estados de cita incompleto: " + code));
    }
}
