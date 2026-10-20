package com.clinica.dental.factura;

import com.clinica.dental.factura.dto.ComprobantePdf;
import com.clinica.dental.factura.dto.FacturaRequest;
import com.clinica.dental.factura.dto.FacturaResponse;
import com.clinica.dental.paciente.Paciente;
import com.clinica.dental.paciente.PacienteRepository;
import com.clinica.dental.realizado.TratamientoRealizado;
import com.clinica.dental.realizado.TratamientoRealizadoRepository;
import com.clinica.dental.shared.ClinicaBusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Emision y consulta de comprobantes (S9).
 *
 * <p>Cada linea toma el costo congelado del tratamiento realizado, y el total es la suma de esas
 * lineas: la factura no agrega impuestos ni recargos (L5).
 *
 * <p>El numero correlativo se calcula al final, cuando la solicitud ya paso todas sus validaciones.
 * Asi un {@code id_realizado} repetido o ajeno se rechaza con {@code 409} sin consumir numeracion,
 * y la serie nunca queda con huecos.
 */
@Service
public class FacturaService {

    private final FacturaRepository facturas;
    private final FacturaDetalleRepository detalles;
    private final EstadoFacturaRepository estados;
    private final PacienteRepository pacientes;
    private final TratamientoRealizadoRepository realizados;
    private final FacturaPdfService pdf;

    public FacturaService(FacturaRepository facturas,
                          FacturaDetalleRepository detalles,
                          EstadoFacturaRepository estados,
                          PacienteRepository pacientes,
                          TratamientoRealizadoRepository realizados,
                          FacturaPdfService pdf) {
        this.facturas = facturas;
        this.detalles = detalles;
        this.estados = estados;
        this.pacientes = pacientes;
        this.realizados = realizados;
        this.pdf = pdf;
    }

    @Transactional
    public FacturaResponse emitir(FacturaRequest request) {
        if (request == null || request.idsRealizado() == null || request.idsRealizado().isEmpty()) {
            throw new ClinicaBusinessException("La factura debe incluir al menos un tratamiento realizado");
        }

        Paciente paciente = pacientes.findByIdAndActiveTrue(request.idPaciente())
            .orElseThrow(() -> new ClinicaBusinessException(HttpStatus.NOT_FOUND,
                "Paciente no encontrado o dado de baja"));

        List<TratamientoRealizado> facturables = resolverseFacturables(paciente, request.idsRealizado());

        BigDecimal total = facturables.stream()
            .map(TratamientoRealizado::getCosto)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);

        Factura factura = new Factura();
        factura.setNumero(siguienteNumero());
        factura.setPaciente(paciente);
        factura.setFechaEmision(OffsetDateTime.now());
        factura.setEstado(estado(EstadoFactura.EMITIDA));
        factura.setTotal(total);
        factura.setSaldo(total);
        factura.setObservaciones(recortar(request.observaciones()));
        Factura guardada = facturas.save(factura);

        List<FacturaDetalle> lineas = new ArrayList<>();
        for (TratamientoRealizado realizado : facturables) {
            FacturaDetalle linea = new FacturaDetalle();
            linea.setFactura(guardada);
            linea.setRealizado(realizado);
            linea.setDescripcion(realizado.getTratamiento().getNombre());
            linea.setMonto(realizado.getCosto());
            lineas.add(linea);
        }
        return FacturaResponse.desde(guardada, detalles.saveAll(lineas));
    }

    @Transactional(readOnly = true)
    public List<FacturaResponse> listar() {
        return facturas.findAllByOrderByNumeroDesc().stream()
            .map(factura -> FacturaResponse.desde(factura, detalles(factura.getId())))
            .toList();
    }

    @Transactional(readOnly = true)
    public FacturaResponse obtener(Integer id) {
        Factura factura = buscar(id);
        return FacturaResponse.desde(factura, detalles(factura.getId()));
    }

    @Transactional(readOnly = true)
    public ComprobantePdf comprobante(Integer id) {
        Factura factura = buscar(id);
        return new ComprobantePdf(factura.getNumero(), pdf.generar(factura, detalles(factura.getId())));
    }

    /**
     * Resuelve y valida los tratamientos realizados que se facturan: cada uno debe existir, ser del
     * paciente y no estar ya facturado (S9).
     */
    private List<TratamientoRealizado> resolverseFacturables(Paciente paciente, List<Integer> idsRealizado) {
        List<TratamientoRealizado> facturables = new ArrayList<>();
        Set<Integer> vistos = new LinkedHashSet<>();
        for (Integer idRealizado : idsRealizado) {
            if (idRealizado == null) {
                throw new ClinicaBusinessException(
                    "Cada linea de la factura debe indicar un tratamiento realizado");
            }
            if (!vistos.add(idRealizado)) {
                throw new ClinicaBusinessException(HttpStatus.CONFLICT,
                    "El tratamiento realizado " + idRealizado + " esta repetido en la factura");
            }
            TratamientoRealizado realizado = realizados.findById(idRealizado)
                .orElseThrow(() -> new ClinicaBusinessException(HttpStatus.NOT_FOUND,
                    "Tratamiento realizado no encontrado: " + idRealizado));
            if (!realizado.getPaciente().getId().equals(paciente.getId())) {
                throw new ClinicaBusinessException(HttpStatus.CONFLICT,
                    "El tratamiento realizado " + idRealizado + " no pertenece al paciente");
            }
            if (detalles.existsByRealizadoId(idRealizado)) {
                throw new ClinicaBusinessException(HttpStatus.CONFLICT,
                    "El tratamiento realizado " + idRealizado + " ya fue facturado");
            }
            facturables.add(realizado);
        }
        return facturables;
    }

    private String siguienteNumero() {
        return NumeroFactura.siguiente(facturas.ultimoNumero().orElse(null));
    }

    private List<FacturaDetalle> detalles(Integer idFactura) {
        return detalles.findByFacturaIdOrderByIdAsc(idFactura);
    }

    private Factura buscar(Integer id) {
        return facturas.findById(id)
            .orElseThrow(() -> new ClinicaBusinessException(HttpStatus.NOT_FOUND,
                "Factura no encontrada"));
    }

    private EstadoFactura estado(String code) {
        return estados.findByCode(code)
            .orElseThrow(() -> new IllegalStateException("Catalogo de estados de factura incompleto: " + code));
    }

    private static String recortar(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
