package com.empresa.calzado.erp.inventario;

import com.empresa.calzado.erp.catalogo.CalzadoSku;
import com.empresa.calzado.erp.seguridad.SystemUser;
import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "kardex_movimiento")
public class KardexMovimiento {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_kardex")
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_bodega", nullable = false)
    private Bodega bodega;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_calzado_sku", nullable = false)
    private CalzadoSku calzadoSku;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_movimiento_tipo", nullable = false)
    private MovimientoStockTipo movimientoTipo;

    @Column(name = "cantidad_movimiento", nullable = false)
    private Integer cantidadMovimiento;

    @Column(name = "stock_anterior", nullable = false)
    private Integer stockAnterior;

    @Column(name = "stock_posterior", nullable = false)
    private Integer stockPosterior;

    @Column(name = "documento_tipo", nullable = false, length = 40)
    private String documentoTipo;

    @Column(name = "documento_id", nullable = false)
    private Long documentoId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_user", nullable = false)
    private SystemUser user;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public KardexMovimiento() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Bodega getBodega() { return bodega; }
    public void setBodega(Bodega bodega) { this.bodega = bodega; }
    public CalzadoSku getCalzadoSku() { return calzadoSku; }
    public void setCalzadoSku(CalzadoSku calzadoSku) { this.calzadoSku = calzadoSku; }
    public MovimientoStockTipo getMovimientoTipo() { return movimientoTipo; }
    public void setMovimientoTipo(MovimientoStockTipo movimientoTipo) { this.movimientoTipo = movimientoTipo; }
    public Integer getCantidadMovimiento() { return cantidadMovimiento; }
    public void setCantidadMovimiento(Integer cantidadMovimiento) { this.cantidadMovimiento = cantidadMovimiento; }
    public Integer getStockAnterior() { return stockAnterior; }
    public void setStockAnterior(Integer stockAnterior) { this.stockAnterior = stockAnterior; }
    public Integer getStockPosterior() { return stockPosterior; }
    public void setStockPosterior(Integer stockPosterior) { this.stockPosterior = stockPosterior; }
    public String getDocumentoTipo() { return documentoTipo; }
    public void setDocumentoTipo(String documentoTipo) { this.documentoTipo = documentoTipo; }
    public Long getDocumentoId() { return documentoId; }
    public void setDocumentoId(Long documentoId) { this.documentoId = documentoId; }
    public SystemUser getUser() { return user; }
    public void setUser(SystemUser user) { this.user = user; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
