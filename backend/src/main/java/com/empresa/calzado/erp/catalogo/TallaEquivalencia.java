package com.empresa.calzado.erp.catalogo;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "talla_equivalencia")
public class TallaEquivalencia {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_talla")
    private Integer id;

    @Column(name = "talla_eur", nullable = false, unique = true, precision = 3, scale = 1)
    private BigDecimal tallaEur;

    @Column(name = "talla_us_hombre", precision = 3, scale = 1)
    private BigDecimal tallaUsHombre;

    @Column(name = "talla_us_mujer", precision = 3, scale = 1)
    private BigDecimal tallaUsMujer;

    @Column(name = "talla_uk", precision = 3, scale = 1)
    private BigDecimal tallaUk;

    @Column(name = "talla_cm_mx", nullable = false, precision = 3, scale = 1)
    private BigDecimal tallaCmMx;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public TallaEquivalencia() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public BigDecimal getTallaEur() { return tallaEur; }
    public void setTallaEur(BigDecimal tallaEur) { this.tallaEur = tallaEur; }
    public BigDecimal getTallaUsHombre() { return tallaUsHombre; }
    public void setTallaUsHombre(BigDecimal tallaUsHombre) { this.tallaUsHombre = tallaUsHombre; }
    public BigDecimal getTallaUsMujer() { return tallaUsMujer; }
    public void setTallaUsMujer(BigDecimal tallaUsMujer) { this.tallaUsMujer = tallaUsMujer; }
    public BigDecimal getTallaUk() { return tallaUk; }
    public void setTallaUk(BigDecimal tallaUk) { this.tallaUk = tallaUk; }
    public BigDecimal getTallaCmMx() { return tallaCmMx; }
    public void setTallaCmMx(BigDecimal tallaCmMx) { this.tallaCmMx = tallaCmMx; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
