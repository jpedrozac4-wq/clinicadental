package com.clinica.dental.factura;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface FacturaRepository extends JpaRepository<Factura, Integer> {

    /** Comprobantes del mas reciente al mas antiguo, ordenados por su numero correlativo. */
    List<Factura> findAllByOrderByNumeroDesc();

    /**
     * Ultimo numero emitido, base del correlativo sin huecos.
     *
     * <p>El numero no se reserva antes de validar: se deriva de los comprobantes ya persistidos, de
     * modo que una solicitud rechazada no consume numeracion. La restriccion {@code UNIQUE} sobre
     * {@code numero} cierra la carrera entre dos emisiones simultaneas.
     */
    @Query("SELECT MAX(f.numero) FROM Factura f")
    Optional<String> ultimoNumero();
}
