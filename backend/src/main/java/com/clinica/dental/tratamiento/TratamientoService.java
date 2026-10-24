package com.clinica.dental.tratamiento;

import com.clinica.dental.auditoria.AccionAuditoria;
import com.clinica.dental.auditoria.AuditoriaService;
import com.clinica.dental.auditoria.DetalleAuditoria;
import com.clinica.dental.shared.ClinicaBusinessException;
import com.clinica.dental.tratamiento.dto.TratamientoRequest;
import com.clinica.dental.tratamiento.dto.TratamientoResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Reglas del catalogo de tratamientos dentales (S7).
 *
 * <p>El alta y la edicion dejan su fila de auditoria (S19) al final del metodo, dentro de la misma
 * transaccion. Un precio invalido o un codigo duplicado no audita nada porque no escribe nada.
 */
@Service
public class TratamientoService {

    private final TratamientoRepository repository;
    private final AuditoriaService auditoria;

    public TratamientoService(TratamientoRepository repository, AuditoriaService auditoria) {
        this.repository = repository;
        this.auditoria = auditoria;
    }

    @Transactional
    public TratamientoResponse crear(TratamientoRequest request) {
        validar(request);
        if (repository.existsByCodigoIgnoreCase(request.codigo().trim())) {
            throw new ClinicaBusinessException(HttpStatus.CONFLICT,
                "Ya existe un tratamiento con el codigo " + request.codigo().trim());
        }
        Tratamiento tratamiento = new Tratamiento();
        aplicar(tratamiento, request);
        tratamiento.setActive(true);
        Tratamiento guardado = repository.save(tratamiento);
        auditar(AccionAuditoria.TRATAMIENTO_CREAR, guardado);
        return TratamientoResponse.desde(guardado);
    }

    @Transactional
    public TratamientoResponse actualizar(Integer id, TratamientoRequest request) {
        validar(request);
        Tratamiento tratamiento = repository.findByIdAndActiveTrue(id)
            .orElseThrow(() -> new ClinicaBusinessException(HttpStatus.NOT_FOUND,
                "Tratamiento no encontrado"));
        if (repository.existsByCodigoIgnoreCaseAndIdNot(request.codigo().trim(), id)) {
            throw new ClinicaBusinessException(HttpStatus.CONFLICT,
                "Ya existe un tratamiento con el codigo " + request.codigo().trim());
        }
        aplicar(tratamiento, request);
        Tratamiento guardado = repository.save(tratamiento);
        auditar(AccionAuditoria.TRATAMIENTO_ACTUALIZAR, guardado);
        return TratamientoResponse.desde(guardado);
    }

    @Transactional(readOnly = true)
    public List<TratamientoResponse> listar() {
        return repository.findByActiveTrueOrderByNombreAsc().stream()
            .map(TratamientoResponse::desde)
            .toList();
    }

    /** Una fila de auditoria por alta o edicion, con el precio vigente que quedo en el catalogo. */
    private void auditar(AccionAuditoria accion, Tratamiento guardado) {
        auditoria.registrar(accion, guardado.getId(),
            DetalleAuditoria.nuevo()
                .con("idTratamiento", guardado.getId())
                .con("codigo", guardado.getCodigo())
                .con("precio", guardado.getPrecio()));
    }

    private static void aplicar(Tratamiento tratamiento, TratamientoRequest request) {
        tratamiento.setCodigo(request.codigo().trim().toUpperCase());
        tratamiento.setNombre(request.nombre().trim());
        tratamiento.setDescripcion(request.descripcion() == null ? null : request.descripcion().trim());
        tratamiento.setPrecio(request.precio());
        tratamiento.setDuracionMinutos(request.duracionMinutos());
    }

    private static void validar(TratamientoRequest request) {
        if (request == null) {
            throw new ClinicaBusinessException("Los datos del tratamiento son obligatorios");
        }
        if (request.codigo() == null || request.codigo().isBlank()) {
            throw new ClinicaBusinessException("El codigo del tratamiento es obligatorio");
        }
        if (request.nombre() == null || request.nombre().isBlank()) {
            throw new ClinicaBusinessException("El nombre del tratamiento es obligatorio");
        }
        if (request.precio() == null || request.precio().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ClinicaBusinessException("El precio del tratamiento debe ser mayor que cero");
        }
        if (request.duracionMinutos() == null || request.duracionMinutos() < 1) {
            throw new ClinicaBusinessException("La duracion del tratamiento debe ser al menos un minuto");
        }
    }
}
