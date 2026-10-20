package com.clinica.dental.tratamiento;

import com.clinica.dental.shared.ClinicaBusinessException;
import com.clinica.dental.tratamiento.dto.TratamientoRequest;
import com.clinica.dental.tratamiento.dto.TratamientoResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/** Reglas del catalogo de tratamientos dentales (S7). */
@Service
public class TratamientoService {

    private final TratamientoRepository repository;

    public TratamientoService(TratamientoRepository repository) {
        this.repository = repository;
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
        return TratamientoResponse.desde(repository.save(tratamiento));
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
        return TratamientoResponse.desde(repository.save(tratamiento));
    }

    @Transactional(readOnly = true)
    public List<TratamientoResponse> listar() {
        return repository.findByActiveTrueOrderByNombreAsc().stream()
            .map(TratamientoResponse::desde)
            .toList();
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
