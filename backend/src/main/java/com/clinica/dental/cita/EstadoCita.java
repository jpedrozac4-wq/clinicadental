package com.clinica.dental.cita;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/** Catalogo de estados del ciclo de vida de una cita (S4, S6). */
@Entity
@Table(name = "cat_estado_cita")
public class EstadoCita {

    public static final String PROGRAMADA = "PROGRAMADA";
    public static final String CONFIRMADA = "CONFIRMADA";
    public static final String REPROGRAMADA = "REPROGRAMADA";
    public static final String ATENDIDA = "ATENDIDA";
    public static final String CANCELADA = "CANCELADA";
    public static final String NO_ASISTIO = "NO_ASISTIO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_estado_cita")
    private Integer id;

    @Column(name = "code", nullable = false, unique = true, length = 30)
    private String code;

    @Column(name = "description", nullable = false, length = 150)
    private String description;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public EstadoCita() {
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
