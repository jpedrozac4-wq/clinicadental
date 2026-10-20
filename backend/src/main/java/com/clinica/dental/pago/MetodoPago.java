package com.clinica.dental.pago;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/** Catalogo de formas de pago aceptadas por el consultorio (S10). */
@Entity
@Table(name = "cat_metodo_pago")
public class MetodoPago {

    public static final String EFECTIVO = "EFECTIVO";
    public static final String TARJETA = "TARJETA";
    public static final String TRANSFERENCIA = "TRANSFERENCIA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_metodo_pago")
    private Integer id;

    @Column(name = "code", nullable = false, unique = true, length = 30)
    private String code;

    @Column(name = "description", nullable = false, length = 150)
    private String description;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public MetodoPago() {
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
