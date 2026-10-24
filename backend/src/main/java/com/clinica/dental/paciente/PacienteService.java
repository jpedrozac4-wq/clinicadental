package com.clinica.dental.paciente;

import com.clinica.dental.auditoria.AccionAuditoria;
import com.clinica.dental.auditoria.AuditoriaService;
import com.clinica.dental.auditoria.DetalleAuditoria;
import com.clinica.dental.paciente.dto.PacienteRequest;
import com.clinica.dental.paciente.dto.PacienteResponse;
import com.clinica.dental.shared.ClinicaBusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Reglas de negocio de la ficha del paciente (S1, S2).
 *
 * <p>La validacion vive aca y no solo en las anotaciones del DTO: el alta debe rechazarse aunque la
 * llamada no pase por el controlador, y ese rechazo tiene que dejar la base intacta.
 *
 * <p>Cada escritura deja su fila de auditoria (S19) al final del metodo, dentro de la misma
 * transaccion. Un alta o edicion rechazada y una baja repetida no auditan nada, porque tampoco
 * escriben nada.
 */
@Service
public class PacienteService {

    /** Formato aceptado: algo@dominio.tld, sin espacios. */
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[A-Za-z]{2,}$");

    private final PacienteRepository repository;
    private final AuditoriaService auditoria;

    public PacienteService(PacienteRepository repository, AuditoriaService auditoria) {
        this.repository = repository;
        this.auditoria = auditoria;
    }

    @Transactional
    public PacienteResponse crear(PacienteRequest request) {
        validar(request);
        Paciente paciente = new Paciente();
        aplicar(paciente, request);
        paciente.setActive(true);
        Paciente guardado = repository.save(paciente);
        auditoria.registrar(AccionAuditoria.PACIENTE_CREAR, guardado.getId(),
            DetalleAuditoria.nuevo()
                .con("idPaciente", guardado.getId())
                .con("email", guardado.getEmail()));
        return PacienteResponse.desde(guardado);
    }

    @Transactional(readOnly = true)
    public List<PacienteResponse> listar(String texto) {
        String filtro = (texto == null || texto.isBlank()) ? null : "%" + texto.trim().toLowerCase() + "%";
        return repository.buscar(filtro).stream().map(PacienteResponse::desde).toList();
    }

    @Transactional(readOnly = true)
    public PacienteResponse obtener(Integer id) {
        return PacienteResponse.desde(buscarVigente(id));
    }

    @Transactional
    public PacienteResponse actualizar(Integer id, PacienteRequest request) {
        validar(request);
        Paciente paciente = buscarVigente(id);
        aplicar(paciente, request);
        Paciente guardado = repository.save(paciente);
        auditoria.registrar(AccionAuditoria.PACIENTE_ACTUALIZAR, guardado.getId(),
            DetalleAuditoria.nuevo()
                .con("idPaciente", guardado.getId())
                .con("email", guardado.getEmail()));
        return PacienteResponse.desde(guardado);
    }

    /**
     * Baja logica del paciente. Es idempotente: repetirla sobre un paciente ya dado de baja no
     * vuelve a escribir ni audita, y el historial clinico y facturado queda intacto.
     */
    @Transactional
    public void darDeBaja(Integer id) {
        Paciente paciente = repository.findById(id)
            .orElseThrow(() -> new ClinicaBusinessException(HttpStatus.NOT_FOUND, "Paciente no encontrado"));
        if (Boolean.FALSE.equals(paciente.getActive())) {
            return;
        }
        paciente.setActive(false);
        repository.save(paciente);
        auditoria.registrar(AccionAuditoria.PACIENTE_BAJA, paciente.getId(),
            DetalleAuditoria.nuevo()
                .con("idPaciente", paciente.getId())
                .con("activo", Boolean.FALSE));
    }

    private Paciente buscarVigente(Integer id) {
        return repository.findByIdAndActiveTrue(id)
            .orElseThrow(() -> new ClinicaBusinessException(HttpStatus.NOT_FOUND, "Paciente no encontrado"));
    }

    private static void aplicar(Paciente paciente, PacienteRequest request) {
        paciente.setNombres(request.nombres().trim());
        paciente.setApellidos(request.apellidos().trim());
        paciente.setFechaNacimiento(request.fechaNacimiento());
        paciente.setTelefono(request.telefono().trim());
        paciente.setDireccion(request.direccion().trim());
        paciente.setEmail(request.email().trim());
    }

    private static void validar(PacienteRequest request) {
        if (request == null) {
            throw new ClinicaBusinessException("Los datos del paciente son obligatorios");
        }
        exigir(request.nombres(), "Los nombres del paciente son obligatorios");
        exigir(request.apellidos(), "Los apellidos del paciente son obligatorios");
        exigir(request.telefono(), "El telefono del paciente es obligatorio");
        exigir(request.direccion(), "La direccion del paciente es obligatoria");
        exigir(request.email(), "El email del paciente es obligatorio");
        if (request.fechaNacimiento() == null) {
            throw new ClinicaBusinessException("La fecha de nacimiento es obligatoria");
        }
        if (request.fechaNacimiento().isAfter(LocalDate.now())) {
            throw new ClinicaBusinessException("La fecha de nacimiento no puede ser futura");
        }
        if (!EMAIL.matcher(request.email().trim()).matches()) {
            throw new ClinicaBusinessException("El email del paciente no tiene un formato valido");
        }
    }

    private static void exigir(String valor, String mensaje) {
        if (valor == null || valor.isBlank()) {
            throw new ClinicaBusinessException(mensaje);
        }
    }
}
