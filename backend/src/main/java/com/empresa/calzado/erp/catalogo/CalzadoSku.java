package com.empresa.calzado.erp.catalogo;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "calzado_sku")
public class CalzadoSku {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_calzado_sku")
    private Integer id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_modelo_color", nullable = false)
    private ModeloColor modeloColor;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_talla", nullable = false)
    private TallaEquivalencia talla;

    @Column(name = "sku", nullable = false, unique = true, length = 30)
    private String sku;

    @Column(name = "codigo_barras_ean", length = 14)
    private String codigoBarrasEan;

    @Column(name = "precio_venta_vigente", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioVentaVigente;

    @Column(name = "peso_gramos", nullable = false)
    private Integer pesoGramos = 800;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "created_by")
    private Integer createdBy;

    @Column(name = "updated_by")
    private Integer updatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public CalzadoSku() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public ModeloColor getModeloColor() { return modeloColor; }
    public void setModeloColor(ModeloColor modeloColor) { this.modeloColor = modeloColor; }
    public TallaEquivalencia getTalla() { return talla; }
    public void setTalla(TallaEquivalencia talla) { this.talla = talla; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public String getCodigoBarrasEan() { return codigoBarrasEan; }
    public void setCodigoBarrasEan(String codigoBarrasEan) { this.codigoBarrasEan = codigoBarrasEan; }
    public BigDecimal getPrecioVentaVigente() { return precioVentaVigente; }
    public void setPrecioVentaVigente(BigDecimal precioVentaVigente) { this.precioVentaVigente = precioVentaVigente; }
    public Integer getPesoGramos() { return pesoGramos; }
    public void setPesoGramos(Integer pesoGramos) { this.pesoGramos = pesoGramos; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public Integer getCreatedBy() { return createdBy; }
    public void setCreatedBy(Integer createdBy) { this.createdBy = createdBy; }
    public Integer getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Integer updatedBy) { this.updatedBy = updatedBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
