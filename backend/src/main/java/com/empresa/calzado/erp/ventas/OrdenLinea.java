package com.empresa.calzado.erp.ventas;

import com.empresa.calzado.erp.catalogo.CalzadoSku;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "orden_linea")
public class OrdenLinea {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_orden_linea")
    private Integer id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_orden", nullable = false)
    private Orden orden;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_calzado_sku", nullable = false)
    private CalzadoSku calzadoSku;

    @Column(name = "cantidad", nullable = false)
    private Integer cantidad;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "descuento_unitario", nullable = false, precision = 10, scale = 2)
    private BigDecimal descuentoUnitario = BigDecimal.ZERO;

    @Column(name = "line_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal lineTotal;

    public OrdenLinea() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Orden getOrden() { return orden; }
    public void setOrden(Orden orden) { this.orden = orden; }
    public CalzadoSku getCalzadoSku() { return calzadoSku; }
    public void setCalzadoSku(CalzadoSku calzadoSku) { this.calzadoSku = calzadoSku; }
    public Integer getCantidad() { return cantidad; }
    public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public BigDecimal getDescuentoUnitario() { return descuentoUnitario; }
    public void setDescuentoUnitario(BigDecimal descuentoUnitario) { this.descuentoUnitario = descuentoUnitario; }
    public BigDecimal getLineTotal() { return lineTotal; }
    public void setLineTotal(BigDecimal lineTotal) { this.lineTotal = lineTotal; }
}
