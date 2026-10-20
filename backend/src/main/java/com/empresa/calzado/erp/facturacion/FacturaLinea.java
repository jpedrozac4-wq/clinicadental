package com.empresa.calzado.erp.facturacion;

import com.empresa.calzado.erp.catalogo.CalzadoSku;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "factura_linea")
public class FacturaLinea {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_invoice_line")
    private Integer id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_invoice", nullable = false)
    private Factura factura;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_calzado_sku", nullable = false)
    private CalzadoSku calzadoSku;

    @Column(name = "cantidad", nullable = false)
    private Integer cantidad;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "line_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal lineTotal;

    public FacturaLinea() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Factura getFactura() { return factura; }
    public void setFactura(Factura factura) { this.factura = factura; }
    public CalzadoSku getCalzadoSku() { return calzadoSku; }
    public void setCalzadoSku(CalzadoSku calzadoSku) { this.calzadoSku = calzadoSku; }
    public Integer getCantidad() { return cantidad; }
    public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public BigDecimal getLineTotal() { return lineTotal; }
    public void setLineTotal(BigDecimal lineTotal) { this.lineTotal = lineTotal; }
}
