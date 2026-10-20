package com.clinica.dental.factura;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FacturaDetalleRepository extends JpaRepository<FacturaDetalle, Integer> {

    List<FacturaDetalle> findByFacturaIdOrderByIdAsc(Integer idFactura);

    /** Un tratamiento realizado ya facturado no puede volver a facturarse (S9). */
    boolean existsByRealizadoId(Integer idRealizado);
}
