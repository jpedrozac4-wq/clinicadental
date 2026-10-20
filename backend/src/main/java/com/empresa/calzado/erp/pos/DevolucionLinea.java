package com.empresa.calzado.erp.pos;

import com.empresa.calzado.erp.catalogo.CalzadoSku;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "devolucion_linea")
public class DevolucionLinea {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_devolucion_linea")
    private Integer id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_devolucion", nullable = false)
    private Devolucion devolucion;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_calzado_sku", nullable = false)
    private CalzadoSku calzadoSku;

    @Column(name = "cantidad", nullable = false)
    private Integer cantidad;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "estado_calzado", nullable = false, length = 30)
    private String estadoCalzado; // APTO_PRODUCTO_ACTUAL, DANADO_OUTLET

    public DevolucionLinea() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Devolucion getDevolucion() { return devolucion; }
    public void setDevolucion(Devolucion devolucion) { this.devolucion = devolucion; }
    public CalzadoSku getCalzadoSku() { return calzadoSku; }
    public void setCalzadoSku(CalzadoSku calzadoSku) { this.calzadoSku = calzadoSku; }
    public Integer getCantidad() { return cantidad; }
    public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public String getEstadoCalzado() { return estadoCalzado; }
    public void setEstadoCalzado(String estadoCalzado) { this.estadoCalzado = estadoCalzado; }
}
