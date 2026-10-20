package com.empresa.calzado.erp.pos;

import com.empresa.calzado.erp.inventario.Bodega;
import com.empresa.calzado.erp.pos.dto.CajaPosResumen;
import com.empresa.calzado.erp.pos.dto.SesionActualResponse;
import com.empresa.calzado.erp.pos.dto.SesionCajaCierreResponse;
import com.empresa.calzado.erp.pos.dto.SesionCajaResumen;
import com.empresa.calzado.erp.seguridad.SystemUser;
import com.empresa.calzado.erp.seguridad.SystemUserRepository;
import com.empresa.calzado.erp.ventas.CheckoutBusinessException;
import com.empresa.calzado.erp.ventas.OrdenRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

/**
 * Cash-session lifecycle: opening, the real cash count (arqueo), closing and auditing.
 *
 * <p>Two uniqueness rules hold here and they are not decoration: {@code orden} carries the cashier
 * and the POS channel but has no column linking it to a session, and DDL is frozen, so a session's
 * sales are attributed by {@code canalVenta = 'FISICO_TIENDA_POS'} + {@code id_user_cajero} + the
 * {@code fecha_apertura..fecha_cierre} window. "One open session per register" and "one open session
 * per cashier" are exactly what makes that attribution exact instead of approximate, which is why
 * the arqueo can be trusted.
 */
@Service
public class PosSesionService {

    /** The channel every counter sale is written with; the attribution query keys on it. */
    public static final String CANAL_VENTA_POS = "FISICO_TIENDA_POS";
    public static final String ESTADO_ABIERTA = "ABIERTA";
    public static final String ESTADO_CERRADA = "CERRADA";
    public static final String ESTADO_AUDITADA = "AUDITADA";

    /**
     * Supervisor permission that authorises auditing a cash count and closing another cashier's
     * turn. It already exists in the role seed; closing must not invent a new permission.
     */
    public static final String PERMISO_ARQUEO_AUDITAR = "OP_POS_ARQUEO_AUDITAR";

    /**
     * Cash means one of these method codes. The schema has no {@code es_efectivo} column, so this set
     * is the single source of truth for which payment methods count towards the physical drawer;
     * change it here and nowhere else.
     */
    public static final Set<String> CODIGOS_METODO_EFECTIVO = Set.of("EFECTIVO", "EFECTIVO_CONTRA_ENTREGA");

    private final CajaPosRepository cajaPosRepository;
    private final SesionCajaRepository sesionCajaRepository;
    private final SystemUserRepository userRepository;
    private final OrdenRepository ordenRepository;

    public PosSesionService(
        CajaPosRepository cajaPosRepository,
        SesionCajaRepository sesionCajaRepository,
        SystemUserRepository userRepository,
        OrdenRepository ordenRepository
    ) {
        this.cajaPosRepository = cajaPosRepository;
        this.sesionCajaRepository = sesionCajaRepository;
        this.userRepository = userRepository;
        this.ordenRepository = ordenRepository;
    }

    @Transactional(readOnly = true)
    public List<CajaPosResumen> listarCajas() {
        return cajaPosRepository.findByActiveTrueOrderByIdAsc().stream()
            .map(caja -> {
                Integer sesionAbiertaId = sesionCajaRepository
                    .findFirstByCajaPosIdAndEstado(caja.getId(), ESTADO_ABIERTA)
                    .map(SesionCaja::getId)
                    .orElse(null);
                Sucursal sucursal = caja.getSucursal();
                Bodega bodega = sucursal.getBodega();
                return new CajaPosResumen(
                    caja.getId(),
                    caja.getNumeroCaja(),
                    caja.getNombreTerminal(),
                    sucursal.getId(),
                    sucursal.getNombre(),
                    bodega.getId(),
                    bodega.getNombre(),
                    sesionAbiertaId);
            })
            .toList();
    }

    @Transactional(readOnly = true)
    public List<SesionCajaResumen> listarSesiones(String estado) {
        List<SesionCaja> sesiones = (estado == null || estado.isBlank())
            ? sesionCajaRepository.findAllByOrderByFechaAperturaDesc()
            : sesionCajaRepository.findByEstadoOrderByFechaAperturaDesc(estado);
        return sesiones.stream().map(this::resumen).toList();
    }

    /**
     * The authenticated cashier's current turn. Never throws for "no open turn": the response is
     * {@code abierta=false}, so the polling badge always gets HTTP 200.
     */
    @Transactional(readOnly = true)
    public SesionActualResponse sesionActual(String username) {
        return userRepository.findByUsername(username)
            .flatMap(cajero -> sesionCajaRepository.findFirstByCajeroIdAndEstado(cajero.getId(), ESTADO_ABIERTA))
            .map(sesion -> new SesionActualResponse(true, resumen(sesion)))
            .orElseGet(() -> new SesionActualResponse(false, null));
    }

    @Transactional
    public SesionCajaResumen abrirSesion(String username, Integer idCajaPos, BigDecimal montoApertura) {
        SystemUser cajero = cajeroAutenticado(username);

        if (idCajaPos == null) {
            throw new CheckoutBusinessException(HttpStatus.BAD_REQUEST, "La caja es obligatoria");
        }
        CajaPos caja = cajaPosRepository.findById(idCajaPos)
            .filter(registradora -> Boolean.TRUE.equals(registradora.getActive()))
            .orElseThrow(() -> new CheckoutBusinessException(HttpStatus.BAD_REQUEST,
                "La caja no existe o no esta activa"));

        if (montoApertura == null) {
            throw new CheckoutBusinessException(HttpStatus.BAD_REQUEST, "El monto de apertura es obligatorio");
        }
        if (montoApertura.compareTo(BigDecimal.ZERO) < 0) {
            throw new CheckoutBusinessException(HttpStatus.BAD_REQUEST,
                "El monto de apertura no puede ser negativo");
        }

        // The two uniqueness rules that keep sales attribution exact (see the class comment).
        if (sesionCajaRepository.existsByCajaPosIdAndEstado(caja.getId(), ESTADO_ABIERTA)) {
            throw new CheckoutBusinessException(HttpStatus.BAD_REQUEST, "La caja ya tiene una sesion abierta");
        }
        if (sesionCajaRepository.existsByCajeroIdAndEstado(cajero.getId(), ESTADO_ABIERTA)) {
            throw new CheckoutBusinessException(HttpStatus.BAD_REQUEST, "El cajero ya tiene una sesion abierta");
        }

        SesionCaja sesion = new SesionCaja();
        sesion.setCajaPos(caja);
        sesion.setCajero(cajero);
        sesion.setMontoApertura(montoApertura);
        sesion.setTotalVentasSistema(BigDecimal.ZERO);
        sesion.setDiferenciaArqueo(BigDecimal.ZERO);
        sesion.setEstado(ESTADO_ABIERTA);
        sesion.setFechaApertura(OffsetDateTime.now());

        return resumen(sesionCajaRepository.save(sesion));
    }

    @Transactional
    public SesionCajaCierreResponse cerrarSesion(Integer idSesion, BigDecimal montoCierreEfectivo, Authentication auth) {
        SesionCaja sesion = sesionCajaRepository.findById(idSesion)
            .orElseThrow(() -> new CheckoutBusinessException(HttpStatus.NOT_FOUND,
                "La sesion de caja no existe"));

        validarPermisoDeCierre(sesion, auth);

        if (!ESTADO_ABIERTA.equals(sesion.getEstado())) {
            throw new CheckoutBusinessException(HttpStatus.BAD_REQUEST, "La sesion de caja no esta abierta");
        }
        if (montoCierreEfectivo == null) {
            throw new CheckoutBusinessException(HttpStatus.BAD_REQUEST, "El monto de cierre es obligatorio");
        }
        if (montoCierreEfectivo.compareTo(BigDecimal.ZERO) < 0) {
            throw new CheckoutBusinessException(HttpStatus.BAD_REQUEST,
                "El monto de cierre no puede ser negativo");
        }

        OffsetDateTime ahora = OffsetDateTime.now();
        Integer cajeroId = sesion.getCajero().getId();
        OffsetDateTime desde = sesion.getFechaApertura();

        // The real sales, recovered from the channel + cashier + window attribution.
        BigDecimal totalVentasSistema = ordenRepository.sumTotalVentasAtribuidas(
            CANAL_VENTA_POS, cajeroId, desde, ahora);
        BigDecimal ventasEnEfectivo = ordenRepository.sumVentasEfectivoAtribuidas(
            CANAL_VENTA_POS, cajeroId, desde, ahora, CODIGOS_METODO_EFECTIVO);

        BigDecimal efectivoEsperado = sesion.getMontoApertura().add(ventasEnEfectivo);
        BigDecimal diferenciaArqueo = montoCierreEfectivo.subtract(efectivoEsperado);

        sesion.setMontoCierreEfectivo(montoCierreEfectivo);
        sesion.setTotalVentasSistema(totalVentasSistema);
        sesion.setDiferenciaArqueo(diferenciaArqueo);
        sesion.setFechaCierre(ahora);
        sesion.setEstado(ESTADO_CERRADA);
        SesionCaja cerrada = sesionCajaRepository.save(sesion);

        return new SesionCajaCierreResponse(
            cerrada.getId(),
            cerrada.getEstado(),
            cerrada.getFechaApertura(),
            cerrada.getFechaCierre(),
            cerrada.getMontoApertura(),
            cerrada.getMontoCierreEfectivo(),
            cerrada.getTotalVentasSistema(),
            ventasEnEfectivo,
            efectivoEsperado,
            cerrada.getDiferenciaArqueo(),
            cerrada.getCajaPos().getId(),
            cerrada.getCajaPos().getNumeroCaja(),
            cerrada.getCajaPos().getSucursal().getNombre(),
            cerrada.getCajaPos().getSucursal().getBodega().getId(),
            cerrada.getCajaPos().getSucursal().getBodega().getNombre(),
            cerrada.getCajero().getUsername());
    }

    @Transactional
    public SesionCajaResumen auditarSesion(Integer idSesion) {
        SesionCaja sesion = sesionCajaRepository.findById(idSesion)
            .orElseThrow(() -> new CheckoutBusinessException(HttpStatus.NOT_FOUND,
                "La sesion de caja no existe"));

        if (!ESTADO_CERRADA.equals(sesion.getEstado())) {
            throw new CheckoutBusinessException(HttpStatus.BAD_REQUEST, "La sesion de caja no esta cerrada");
        }

        sesion.setEstado(ESTADO_AUDITADA);
        return resumen(sesionCajaRepository.save(sesion));
    }

    /**
     * The authenticated cashier's open session, or a 400 when there is none.
     *
     * <p>This is deliberately required by the counter sale: selling on a closed register is exactly
     * the defect the fake frontend hides, and it is the reason the warehouse of a sale can be
     * resolved from the session's branch instead of a global "first web warehouse" guess.
     */
    @Transactional(readOnly = true)
    public SesionCaja sesionAbiertaObligatoria(String username) {
        SystemUser cajero = cajeroAutenticado(username);
        return sesionCajaRepository.findFirstByCajeroIdAndEstado(cajero.getId(), ESTADO_ABIERTA)
            .orElseThrow(() -> new CheckoutBusinessException(HttpStatus.BAD_REQUEST,
                "El cajero no tiene una sesion de caja abierta"));
    }

    /**
     * Closing a turn mutates the drawer that belongs to the session's cashier, so only that same
     * cashier or a holder of the arqueo-audit permission may close it. The check is explicit
     * because OP_POS_CAJA_APERTURA_CIERRE is shared by every cashier, and without it one cashier
     * could close another's turn and have the arqueo attributed to the wrong person.
     */
    private void validarPermisoDeCierre(SesionCaja sesion, Authentication auth) {
        boolean esPropietario = auth != null && auth.getName() != null
            && auth.getName().equals(sesion.getCajero().getUsername());
        boolean esSupervisor = auth != null && auth.getAuthorities().stream()
            .anyMatch(a -> PERMISO_ARQUEO_AUDITAR.equals(a.getAuthority()));
        if (!esPropietario && !esSupervisor) {
            throw new CheckoutBusinessException(HttpStatus.FORBIDDEN,
                "No tiene permiso para cerrar la sesion de caja de otro cajero");
        }
    }

    private SystemUser cajeroAutenticado(String username) {
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new CheckoutBusinessException(HttpStatus.BAD_REQUEST,
                "El usuario autenticado no existe"));
    }

    private SesionCajaResumen resumen(SesionCaja sesion) {
        CajaPos caja = sesion.getCajaPos();
        return new SesionCajaResumen(
            sesion.getId(),
            sesion.getEstado(),
            sesion.getFechaApertura(),
            sesion.getFechaCierre(),
            sesion.getMontoApertura(),
            sesion.getMontoCierreEfectivo(),
            sesion.getTotalVentasSistema(),
            sesion.getDiferenciaArqueo(),
            caja.getId(),
            caja.getNumeroCaja(),
            caja.getSucursal().getNombre(),
            caja.getSucursal().getBodega().getId(),
            caja.getSucursal().getBodega().getNombre(),
            sesion.getCajero().getUsername());
    }
}
