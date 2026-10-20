package com.empresa.calzado.erp.pos;

import com.empresa.calzado.erp.logistica.DespachoEnvio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Read-only lookup of a shipment by the order it belongs to.
 *
 * <p>The entity lives in the {@code logistica} domain, but {@code logistica/**} is read-only for this
 * unit and the existing {@code DespachoEnvioRepository} has no by-order query, so this additive
 * repository lives in {@code pos} (the same pattern already used by {@code PagoTransaccionRepository}).
 * It exists to read the OTP-validated delivery instant that anchors the return window.
 */
@Repository
public interface DespachoEnvioPorOrdenRepository extends JpaRepository<DespachoEnvio, Integer> {
    Optional<DespachoEnvio> findByOrdenId(Integer ordenId);
}
