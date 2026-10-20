package com.empresa.calzado.erp.compras;

import com.empresa.calzado.erp.catalogo.CalzadoSku;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "orden_compra_linea")
public class OrdenCompraLinea {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_oc_linea")
    private Integer id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_orden_compra", nullable = false)
    private OrdenCompra ordenCompra;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_calzado_sku", nullable = false)
    private CalzadoSku calzadoSku;

    @Column(name = "cantidad_solicitada_libre", nullable = false)
    private Integer cantidadSolicitadaLibre;

    @Column(name = "unit_cost", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitCost;

    public OrdenCompraLinea() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public OrdenCompra getOrdenCompra() { return ordenCompra; }
    public void setOrdenCompra(OrdenCompra ordenCompra) { this.ordenCompra = ordenCompra; }
    public CalzadoSku getCalzadoSku() { return calzadoSku; }
    public void setCalzadoSku(CalzadoSku calzadoSku) { this.calzadoSku = calzadoSku; }
    public Integer getCantidadSolicitadaLibre() { return cantidadSolicitadaLibre; }
    public void setCantidadSolicitadaLibre(Integer cantidadSolicitadaLibre) { this.cantidadSolicitadaLibre = cantidadSolicitadaLibre; }
    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }
}
