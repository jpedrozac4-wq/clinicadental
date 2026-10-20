package com.empresa.calzado.erp.catalogo;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "cat_tipo_calzado")
public class TipoCalzado {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tipo_calzado")
    private Integer id;

    @Column(name = "codigo_letra", nullable = false, unique = true, length = 1)
    private String codigoLetra;

    @Column(name = "nombre", nullable = false, length = 50)
    private String nombre;

    @Column(name = "descripcion")
    private String descripcion;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public TipoCalzado() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getCodigoLetra() { return codigoLetra; }
    public void setCodigoLetra(String codigoLetra) { this.codigoLetra = codigoLetra; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
