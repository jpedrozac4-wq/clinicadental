package com.clinica.dental.auditoria;

import com.clinica.dental.auditoria.dto.AuditoriaPaginaResponse;
import com.clinica.dental.auditoria.dto.AuditoriaRegistroResponse;
import com.clinica.dental.shared.ClinicaBusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Locale;

/**
 * Auditoria de operaciones criticas (S19).
 *
 * <h2>Escritura</h2>
 *
 * <p>{@link #registrar} se invoca al final de cada metodo {@code @Transactional} de negocio, todavia
 * dentro de esa transaccion. La propagacion es {@link Propagation#REQUIRED} a proposito: la fila de
 * auditoria entra o sale junto con la operacion. No hay {@code REQUIRES_NEW} ni escucha
 * {@code AFTER_COMMIT}, y el servicio no atrapa ninguna excepcion, de modo que si el insert de
 * auditoria falla la transaccion completa se revierte. Ese es el contrato de S19: una operacion de
 * negocio sin su fila de auditoria no es una operacion valida.
 *
 * <p>Cada operacion exitosa produce exactamente una fila. Las operaciones rechazadas (400/404/409) y
 * las idempotentes que no escriben nada no llegan hasta aca, porque el servicio de negocio retorna o
 * lanza antes.
 *
 * <h2>Consulta</h2>
 *
 * <p>{@link #consultar} es de solo lectura y traduce los filtros del endpoint a la proyeccion del
 * repositorio. Un codigo de accion que no existe en el catalogo se rechaza con {@code 400}: un filtro
 * mal escrito no debe parecer una bitacora sin movimientos. Una pagina fuera de rango devuelve
 * {@code contenido} vacio con los totales reales, que es lo que la pantalla necesita para no perder
 * el contexto de paginacion.
 */
@Service
public class AuditoriaService {

    /** Tamanio de pagina por defecto del endpoint (S19). */
    static final int TAMANIO_POR_DEFECTO = 25;

    /** Tope duro de filas por pagina: protege la respuesta de un cliente que pida demasiado. */
    static final int TAMANIO_MAXIMO = 100;

    /** Zona con la que las fechas {@code YYYY-MM-DD} se traducen a los instantes de la base. */
    private static final ZoneId ZONA = ZoneId.systemDefault();

    /**
     * Cotas con las que se sustituye un filtro de fecha ausente.
     *
     * <p>No son {@code null} a proposito: PostgreSQL no puede inferir el tipo de un parametro
     * {@code OffsetDateTime} nulo en {@code ? IS NULL} y responde {@code 42P18}. Un rango abierto se
     * representa con extremos que ninguna fila real alcanza, en UTC para no depender de la zona.
     */
    private static final OffsetDateTime SIN_LIMITE_INFERIOR =
        OffsetDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime SIN_LIMITE_SUPERIOR =
        OffsetDateTime.of(9999, 12, 31, 23, 59, 59, 0, ZoneOffset.UTC);

    /** Longitudes reales de las columnas que la bitacora recorta en lugar de rechazar. */
    private static final int LONGITUD_TABLA = 150;
    private static final int LONGITUD_REGISTRO = 200;
    private static final int LONGITUD_IP = 80;
    private static final int LONGITUD_AGENTE = 255;

    private static final String NOTA_SIN_ACTOR =
        "Operacion ejecutada sin actor autenticado (proceso programado o bootstrap)";
    private static final String NOTA_ACTOR_DESCONOCIDO =
        "Operacion ejecutada por un actor que no esta en el padron de usuarios: ";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final AuditLogRepository bitacora;
    private final LogAccionRepository acciones;
    private final ContextoAuditoria contexto;

    public AuditoriaService(AuditLogRepository bitacora,
                            LogAccionRepository acciones,
                            ContextoAuditoria contexto) {
        this.bitacora = bitacora;
        this.acciones = acciones;
        this.contexto = contexto;
    }

    // ------------------------------------------------------------------ escritura

    /** Registra la operacion sin detalle adicional. */
    @Transactional
    public void registrar(AccionAuditoria accion, Object registroPk) {
        registrar(accion, registroPk, DetalleAuditoria.nuevo(), null);
    }

    /** Registra la operacion con el detalle de lo que cambio. */
    @Transactional
    public void registrar(AccionAuditoria accion, Object registroPk, DetalleAuditoria detalle) {
        registrar(accion, registroPk, detalle, null);
    }

    /**
     * Registra la operacion en la transaccion de negocio en curso.
     *
     * @param accion     operacion auditada; provee el codigo del catalogo y la tabla
     * @param registroPk clave primaria de la fila afectada
     * @param detalle    JSON pequeno con lo que cambio; puede ser {@code null}
     * @param notas      aclaracion del llamador; en ausencia de actor autenticado se reemplaza por la
     *                   nota de origen, porque en ese caso {@code id_user} queda en {@code NULL} y la
     *                   fila tiene que explicarse por si misma
     */
    @Transactional
    public void registrar(AccionAuditoria accion, Object registroPk, DetalleAuditoria detalle, String notas) {
        if (accion == null) {
            throw new IllegalArgumentException("La accion auditada es obligatoria");
        }

        AuditLog fila = new AuditLog();
        // El catalogo se resuelve primero: si falta una accion, la operacion falla antes de armar la fila.
        fila.setAccion(catalogo(accion));
        ContextoAuditoria.Actor actor = contexto.actor();
        fila.setTabla(recortar(accion.getTabla(), LONGITUD_TABLA));
        fila.setRegistro(recortar(registroPk, LONGITUD_REGISTRO));
        fila.setDatos(json(detalle));
        fila.setUsuario(actor.usuario());
        fila.setIpAddress(recortar(contexto.ipAddress(), LONGITUD_IP));
        fila.setUserAgent(recortar(contexto.userAgent(), LONGITUD_AGENTE));
        fila.setNotas(notasDe(actor, notas));

        bitacora.save(fila);
    }

    private LogAccion catalogo(AccionAuditoria accion) {
        return acciones.findByCode(accion.getCodigo())
            .orElseThrow(() -> new IllegalStateException(
                "Catalogo de acciones de auditoria incompleto: " + accion.getCodigo()));
    }

    /**
     * Nota de la fila. Con actor registrado manda la del llamador; sin actor, la fila declara el
     * origen en lugar de quedar con un {@code id_user} nulo sin explicacion (S19).
     */
    private static String notasDe(ContextoAuditoria.Actor actor, String notas) {
        if (actor.registrado()) {
            return recortar(notas, null);
        }
        String origen = actor.username() == null
            ? NOTA_SIN_ACTOR
            : NOTA_ACTOR_DESCONOCIDO + actor.username();
        return notas == null || notas.isBlank() ? origen : origen + ". " + notas.trim();
    }

    private static String json(DetalleAuditoria detalle) {
        try {
            return JSON.writeValueAsString(detalle == null ? DetalleAuditoria.nuevo().valores() : detalle.valores());
        } catch (JsonProcessingException ex) {
            // Sin JSON no hay auditoria: se deja subir para que la transaccion de negocio se revierta.
            throw new IllegalStateException("No se pudo serializar el detalle de auditoria", ex);
        }
    }

    private static String recortar(Object valor, Integer longitudMaxima) {
        if (valor == null) {
            return null;
        }
        String texto = String.valueOf(valor).trim();
        if (texto.isEmpty()) {
            return null;
        }
        return longitudMaxima == null || texto.length() <= longitudMaxima
            ? texto
            : texto.substring(0, longitudMaxima);
    }

    // ------------------------------------------------------------------- consulta

    /** Bitacora paginada, de la mas reciente a la mas antigua (S19). */
    @Transactional(readOnly = true)
    public AuditoriaPaginaResponse consultar(LocalDate desde, LocalDate hasta, String accion,
                                             String usuario, Integer pagina, Integer tamanio) {
        // Los filtros de fecha nunca viajan nulos: PostgreSQL no puede inferir el tipo de un
        // OffsetDateTime nulo en "? IS NULL" (42P18) y la consulta entera falla. Con centinelas
        // la comparacion directa le da el tipo al parametro.
        OffsetDateTime inicio = desde == null ? SIN_LIMITE_INFERIOR : inicioDelDia(desde);
        OffsetDateTime fin = hasta == null ? SIN_LIMITE_SUPERIOR : inicioDelDia(hasta.plusDays(1));
        if (desde != null && hasta != null && hasta.isBefore(desde)) {
            throw new ClinicaBusinessException(HttpStatus.BAD_REQUEST,
                "El rango de fechas es invalido: desde no puede ser posterior a hasta");
        }

        String codigo = codigoDeAccion(accion);
        String username = usuario == null || usuario.isBlank() ? null : usuario.trim();
        int numeroPagina = pagina == null || pagina < 0 ? 0 : pagina;
        int filas = tamanioDePagina(tamanio);

        Page<AuditoriaRegistroResponse> resultado =
            bitacora.buscar(inicio, fin, codigo, username, PageRequest.of(numeroPagina, filas));

        return new AuditoriaPaginaResponse(
            resultado.getContent(),
            numeroPagina,
            filas,
            resultado.getTotalElements(),
            resultado.getTotalPages());
    }

    /**
     * Normaliza y valida el filtro de accion contra {@code cat_log_accion}.
     *
     * @return el codigo canonical, o {@code null} si no se pidio filtro
     * @throws ClinicaBusinessException {@code 400} si el codigo no existe en el catalogo
     */
    private String codigoDeAccion(String accion) {
        if (accion == null || accion.isBlank()) {
            return null;
        }
        String codigo = accion.trim().toUpperCase(Locale.ROOT);
        if (acciones.findByCode(codigo).isEmpty()) {
            throw new ClinicaBusinessException(HttpStatus.BAD_REQUEST,
                "Accion de auditoria desconocida: " + codigo);
        }
        return codigo;
    }

    /** Aplica el tamanio por defecto y el tope duro, sin rechazar al cliente por pedir de mas. */
    private static int tamanioDePagina(Integer tamanio) {
        if (tamanio == null || tamanio <= 0) {
            return TAMANIO_POR_DEFECTO;
        }
        return Math.min(tamanio, TAMANIO_MAXIMO);
    }

    private static OffsetDateTime inicioDelDia(LocalDate fecha) {
        return fecha.atStartOfDay(ZONA).toOffsetDateTime();
    }
}
