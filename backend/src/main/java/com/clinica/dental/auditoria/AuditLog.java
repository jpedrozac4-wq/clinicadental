package com.clinica.dental.auditoria;

import com.clinica.dental.seguridad.SystemUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * Fila de la bitacora {@code audit_log} (S19).
 *
 * <p>{@code datos} es la columna {@code data} de tipo {@code jsonb}. Se mapea como {@link String}
 * con {@link SqlTypes#JSON}: Hibernate pasa el texto tal cual en ambos sentidos (no lo vuelve a
 * citar) y el dialecto de PostgreSQL agrega el {@code cast(? as jsonb)} que la base exige al
 * insertar. Asi la fila guarda exactamente el JSON que se serializo en {@link AuditoriaService}.
 *
 * <p>{@code usuario} es nullable a proposito: el proceso programado y el bootstrap escriben
 * auditoria sin actor autenticado y dejan la explicacion en {@code notas}.
 */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_log")
    private Long id;

    @Column(name = "table_name", length = 150)
    private String tabla;

    @Column(name = "record_pk", length = 200)
    private String registro;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "id_log_accion", nullable = false)
    private LogAccion accion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_user")
    private SystemUser usuario;

    /** Columna {@code data}: el detalle de la operacion ya serializado como JSON. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "data")
    private String datos;

    @Column(name = "ip_address", length = 80)
    private String ipAddress;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "notes")
    private String notas;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime creadoEn = OffsetDateTime.now();

    public AuditLog() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTabla() { return tabla; }
    public void setTabla(String tabla) { this.tabla = tabla; }
    public String getRegistro() { return registro; }
    public void setRegistro(String registro) { this.registro = registro; }
    public LogAccion getAccion() { return accion; }
    public void setAccion(LogAccion accion) { this.accion = accion; }
    public SystemUser getUsuario() { return usuario; }
    public void setUsuario(SystemUser usuario) { this.usuario = usuario; }
    public String getDatos() { return datos; }
    public void setDatos(String datos) { this.datos = datos; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public String getNotas() { return notas; }
    public void setNotas(String notas) { this.notas = notas; }
    public OffsetDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(OffsetDateTime creadoEn) { this.creadoEn = creadoEn; }
}
