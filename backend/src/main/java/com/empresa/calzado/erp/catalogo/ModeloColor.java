package com.empresa.calzado.erp.catalogo;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;

@Entity
@Table(name = "modelo_color")
public class ModeloColor {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_modelo_color")
    private Integer id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_modelo", nullable = false)
    private Modelo modelo;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_color", nullable = false)
    private Color color;

    @Column(name = "foto_principal_url")
    private String fotoPrincipalUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "galeria_fotos_json", columnDefinition = "jsonb")
    private String galeriaFotosJson;

    @Column(name = "created_by")
    private Integer createdBy;

    @Column(name = "updated_by")
    private Integer updatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public ModeloColor() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Modelo getModelo() { return modelo; }
    public void setModelo(Modelo modelo) { this.modelo = modelo; }
    public Color getColor() { return color; }
    public void setColor(Color color) { this.color = color; }
    public String getFotoPrincipalUrl() { return fotoPrincipalUrl; }
    public void setFotoPrincipalUrl(String fotoPrincipalUrl) { this.fotoPrincipalUrl = fotoPrincipalUrl; }
    public String getGaleriaFotosJson() { return galeriaFotosJson; }
    public void setGaleriaFotosJson(String galeriaFotosJson) { this.galeriaFotosJson = galeriaFotosJson; }
    public Integer getCreatedBy() { return createdBy; }
    public void setCreatedBy(Integer createdBy) { this.createdBy = createdBy; }
    public Integer getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Integer updatedBy) { this.updatedBy = updatedBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
