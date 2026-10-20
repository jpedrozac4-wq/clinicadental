package com.empresa.calzado.erp.catalogo;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "modelo")
public class Modelo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_modelo")
    private Integer id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_tipo_calzado", nullable = false)
    private TipoCalzado tipoCalzado;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_marca", nullable = false)
    private Marca marca;

    @Column(name = "correlativo", nullable = false)
    private Integer correlativo;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "material_exterior", nullable = false, length = 60)
    private String materialExterior;

    @Column(name = "material_forro", length = 60)
    private String materialForro;

    @Column(name = "tipo_suela", nullable = false, length = 60)
    private String tipoSuela;

    @Column(name = "tipo_cierre", nullable = false, length = 60)
    private String tipoCierre;

    @Column(name = "genero", nullable = false, length = 20)
    private String genero;

    @Column(name = "temporada", nullable = false, length = 40)
    private String temporada;

    @Column(name = "precio_lista_base", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioListaBase;

    @Column(name = "es_outlet", nullable = false)
    private Boolean esOutlet = false;

    @Column(name = "porcentaje_descuento", nullable = false, precision = 5, scale = 2)
    private BigDecimal porcentajeDescuento = BigDecimal.ZERO;

    @Column(name = "es_visible_web", nullable = false)
    private Boolean esVisibleWeb = true;

    @Column(name = "created_by")
    private Integer createdBy;

    @Column(name = "updated_by")
    private Integer updatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public Modelo() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public TipoCalzado getTipoCalzado() { return tipoCalzado; }
    public void setTipoCalzado(TipoCalzado tipoCalzado) { this.tipoCalzado = tipoCalzado; }
    public Marca getMarca() { return marca; }
    public void setMarca(Marca marca) { this.marca = marca; }
    public Integer getCorrelativo() { return correlativo; }
    public void setCorrelativo(Integer correlativo) { this.correlativo = correlativo; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getMaterialExterior() { return materialExterior; }
    public void setMaterialExterior(String materialExterior) { this.materialExterior = materialExterior; }
    public String getMaterialForro() { return materialForro; }
    public void setMaterialForro(String materialForro) { this.materialForro = materialForro; }
    public String getTipoSuela() { return tipoSuela; }
    public void setTipoSuela(String tipoSuela) { this.tipoSuela = tipoSuela; }
    public String getTipoCierre() { return tipoCierre; }
    public void setTipoCierre(String tipoCierre) { this.tipoCierre = tipoCierre; }
    public String getGenero() { return genero; }
    public void setGenero(String genero) { this.genero = genero; }
    public String getTemporada() { return temporada; }
    public void setTemporada(String temporada) { this.temporada = temporada; }
    public BigDecimal getPrecioListaBase() { return precioListaBase; }
    public void setPrecioListaBase(BigDecimal precioListaBase) { this.precioListaBase = precioListaBase; }
    public Boolean getEsOutlet() { return esOutlet; }
    public void setEsOutlet(Boolean esOutlet) { this.esOutlet = esOutlet; }
    public BigDecimal getPorcentajeDescuento() { return porcentajeDescuento; }
    public void setPorcentajeDescuento(BigDecimal porcentajeDescuento) { this.porcentajeDescuento = porcentajeDescuento; }
    public Boolean getEsVisibleWeb() { return esVisibleWeb; }
    public void setEsVisibleWeb(Boolean esVisibleWeb) { this.esVisibleWeb = esVisibleWeb; }
    public Integer getCreatedBy() { return createdBy; }
    public void setCreatedBy(Integer createdBy) { this.createdBy = createdBy; }
    public Integer getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Integer updatedBy) { this.updatedBy = updatedBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    public BigDecimal getPrecioEfectivo() {
        if (Boolean.TRUE.equals(esOutlet) && porcentajeDescuento != null && porcentajeDescuento.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal factor = BigDecimal.ONE.subtract(porcentajeDescuento.divide(BigDecimal.valueOf(100)));
            return precioListaBase.multiply(factor);
        }
        return precioListaBase;
    }
}
