package com.clinica.dental.recordatorio;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/** Resultado del intento de recordatorio: la bitacora de envios de S5 y S15. */
@Entity
@Table(name = "cat_estado_notificacion")
public class EstadoNotificacion {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String ENVIADA = "ENVIADA";
    public static final String SIMULADA = "SIMULADA";
    public static final String FALLIDA = "FALLIDA";
    public static final String CANCELADA = "CANCELADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_estado_notificacion")
    private Integer id;

    @Column(name = "code", nullable = false, unique = true, length = 30)
    private String code;

    @Column(name = "description", nullable = false, length = 150)
    private String description;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public EstadoNotificacion() {
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
