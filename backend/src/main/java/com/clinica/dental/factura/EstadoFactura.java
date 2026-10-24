package com.clinica.dental.factura;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/** Catalogo de estados fiscales del comprobante (S9, S10). */
@Entity
@Table(name = "cat_estado_factura")
public class EstadoFactura {

    public static final String EMITIDA = "EMITIDA";
    public static final String PAGO_PARCIAL = "PAGO_PARCIAL";
    public static final String PAGADA = "PAGADA";
    public static final String ANULADA = "ANULADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_estado_factura")
    private Integer id;

    @Column(name = "code", nullable = false, unique = true, length = 30)
    private String code;

    @Column(name = "description", nullable = false, length = 150)
    private String description;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public EstadoFactura() {
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
