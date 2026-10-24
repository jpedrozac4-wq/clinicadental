package com.clinica.dental.auditoria;

import com.clinica.dental.auditoria.dto.AuditoriaRegistroResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;

/**
 * Acceso a la bitacora de auditoria (S19).
 *
 * <p>{@link #buscar} filtra por rango de fechas, codigo de accion y username del actor, y ordena de
 * la fila mas reciente a la mas antigua. Es una proyeccion a {@link AuditoriaRegistroResponse} para
 * que la consulta y el contrato HTTP compartan forma: el {@code jsonb} de {@code data} viaja como el
 * texto que la base devuelve.
 *
 * <p>Los filtros de texto son opcionales y se declaran como {@code :parametro IS NULL}, siguiendo la
 * convencion del resto del modulo: PostgreSQL resuelve un parametro de texto sin tipo como
 * {@code text}. Los filtros de fecha NO usan ese patron: un {@code OffsetDateTime} nulo dentro de
 * {@code ? IS NULL} hace que PostgreSQL no pueda inferir el tipo y la consulta falla con
 * {@code 42P18 could not determine data type of parameter}. El servicio los acota con centinelas,
 * asi siempre llegan con valor y la comparacion directa le da el tipo al parametro.
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query(value = """
        SELECT new com.clinica.dental.auditoria.dto.AuditoriaRegistroResponse(
            a.id,
            a.creadoEn,
            a.accion.code,
            a.accion.description,
            a.tabla,
            a.registro,
            u.username,
            a.datos)
          FROM AuditLog a
          JOIN a.accion catalogo
          LEFT JOIN a.usuario u
         WHERE a.creadoEn >= :desde
           AND a.creadoEn < :hasta
           AND (:accion IS NULL OR catalogo.code = :accion)
           AND (:usuario IS NULL OR u.username = :usuario)
         ORDER BY a.creadoEn DESC, a.id DESC
        """,
        countQuery = """
        SELECT COUNT(a)
          FROM AuditLog a
          JOIN a.accion catalogo
          LEFT JOIN a.usuario u
         WHERE a.creadoEn >= :desde
           AND a.creadoEn < :hasta
           AND (:accion IS NULL OR catalogo.code = :accion)
           AND (:usuario IS NULL OR u.username = :usuario)
        """)
    Page<AuditoriaRegistroResponse> buscar(@Param("desde") OffsetDateTime desde,
                                           @Param("hasta") OffsetDateTime hasta,
                                           @Param("accion") String accion,
                                           @Param("usuario") String usuario,
                                           Pageable pagina);
}
