package com.empresa.calzado.erp.pos;

import com.empresa.calzado.erp.facturacion.PagoTransaccion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Payment transactions written by the POS counter sale.
 *
 * <p>The entity lives in the {@code facturacion} domain, but the POS is its only writer, and the
 * delegation brief scoped new files to the {@code pos} package, so the repository lives here as an
 * additive change instead of editing the {@code facturacion} surface.
 */
@Repository
public interface PagoTransaccionRepository extends JpaRepository<PagoTransaccion, Integer> {
}
