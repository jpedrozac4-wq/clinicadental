package com.empresa.calzado.erp.ventas;

import com.empresa.calzado.erp.catalogo.CalzadoSku;
import com.empresa.calzado.erp.inventario.Bodega;
import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "reserva_checkout")
public class ReservaCheckout {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_reserva")
    private Integer id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_orden", nullable = false)
    private Orden orden;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_calzado_sku", nullable = false)
    private CalzadoSku calzadoSku;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_bodega", nullable = false)
    private Bodega bodega;

    @Column(name = "cantidad", nullable = false)
    private Integer cantidad;

    @Column(name = "fecha_expiracion", nullable = false)
    private OffsetDateTime fechaExpiracion;

    @Column(name = "estado", nullable = false, length = 20)
    private String estado = "ACTIVA";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public ReservaCheckout() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Orden getOrden() { return orden; }
    public void setOrden(Orden orden) { this.orden = orden; }
    public CalzadoSku getCalzadoSku() { return calzadoSku; }
    public void setCalzadoSku(CalzadoSku calzadoSku) { this.calzadoSku = calzadoSku; }
    public Bodega getBodega() { return bodega; }
    public void setBodega(Bodega bodega) { this.bodega = bodega; }
    public Integer getCantidad() { return cantidad; }
    public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }
    public OffsetDateTime getFechaExpiracion() { return fechaExpiracion; }
    public void setFechaExpiracion(OffsetDateTime fechaExpiracion) { this.fechaExpiracion = fechaExpiracion; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
