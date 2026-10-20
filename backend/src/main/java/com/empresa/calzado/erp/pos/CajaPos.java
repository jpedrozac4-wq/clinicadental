package com.empresa.calzado.erp.pos;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "caja_pos")
public class CajaPos {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_caja_pos")
    private Integer id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_sucursal", nullable = false)
    private Sucursal sucursal;

    @Column(name = "numero_caja", nullable = false, length = 20)
    private String numeroCaja;

    @Column(name = "nombre_terminal", nullable = false, length = 60)
    private String nombreTerminal;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public CajaPos() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Sucursal getSucursal() { return sucursal; }
    public void setSucursal(Sucursal sucursal) { this.sucursal = sucursal; }
    public String getNumeroCaja() { return numeroCaja; }
    public void setNumeroCaja(String numeroCaja) { this.numeroCaja = numeroCaja; }
    public String getNombreTerminal() { return nombreTerminal; }
    public void setNombreTerminal(String nombreTerminal) { this.nombreTerminal = nombreTerminal; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
