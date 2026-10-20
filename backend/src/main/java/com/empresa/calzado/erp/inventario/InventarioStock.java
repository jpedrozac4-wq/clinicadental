package com.empresa.calzado.erp.inventario;

import com.empresa.calzado.erp.catalogo.CalzadoSku;
import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "inventario_stock")
public class InventarioStock {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_stock")
    private Integer id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_bodega", nullable = false)
    private Bodega bodega;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_calzado_sku", nullable = false)
    private CalzadoSku calzadoSku;

    @Column(name = "cantidad_disponible", nullable = false)
    private Integer cantidadDisponible = 0;

    @Column(name = "cantidad_reservada", nullable = false)
    private Integer cantidadReservada = 0;

    @Column(name = "stock_seguridad_minimo", nullable = false)
    private Integer stockSeguridadMinimo = 2;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public InventarioStock() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Bodega getBodega() { return bodega; }
    public void setBodega(Bodega bodega) { this.bodega = bodega; }
    public CalzadoSku getCalzadoSku() { return calzadoSku; }
    public void setCalzadoSku(CalzadoSku calzadoSku) { this.calzadoSku = calzadoSku; }
    public Integer getCantidadDisponible() { return cantidadDisponible; }
    public void setCantidadDisponible(Integer cantidadDisponible) { this.cantidadDisponible = cantidadDisponible; }
    public Integer getCantidadReservada() { return cantidadReservada; }
    public void setCantidadReservada(Integer cantidadReservada) { this.cantidadReservada = cantidadReservada; }
    public Integer getStockSeguridadMinimo() { return stockSeguridadMinimo; }
    public void setStockSeguridadMinimo(Integer stockSeguridadMinimo) { this.stockSeguridadMinimo = stockSeguridadMinimo; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
