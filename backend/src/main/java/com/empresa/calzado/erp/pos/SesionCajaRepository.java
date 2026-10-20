package com.empresa.calzado.erp.pos;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Cash sessions (turnos). The two {@code existsBy...AndEstado} queries back the uniqueness rules of
 * {@link PosSesionService#abrirSesion}: one open session per register and one open session per
 * cashier. Those rules are what make sales attribution exact, because an order carries only the
 * cashier and the POS channel, not a session id.
 */
@Repository
public interface SesionCajaRepository extends JpaRepository<SesionCaja, Integer> {
    Optional<SesionCaja> findFirstByCajaPosIdAndEstado(Integer cajaPosId, String estado);

    Optional<SesionCaja> findFirstByCajeroIdAndEstado(Integer cajeroId, String estado);

    boolean existsByCajaPosIdAndEstado(Integer cajaPosId, String estado);

    boolean existsByCajeroIdAndEstado(Integer cajeroId, String estado);

    List<SesionCaja> findAllByOrderByFechaAperturaDesc();

    List<SesionCaja> findByEstadoOrderByFechaAperturaDesc(String estado);
}
