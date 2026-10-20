package com.empresa.calzado.erp.inventario;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "cat_movimiento_stock_tipo")
public class MovimientoStockTipo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_movimiento_tipo")
    private Integer id;

    @Column(name = "code", nullable = false, unique = true, length = 40)
    private String code;

    @Column(name = "description", nullable = false, length = 150)
    private String description;

    @Column(name = "es_incremento", nullable = false)
    private Boolean esIncremento;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public MovimientoStockTipo() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Boolean getEsIncremento() { return esIncremento; }
    public void setEsIncremento(Boolean esIncremento) { this.esIncremento = esIncremento; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
