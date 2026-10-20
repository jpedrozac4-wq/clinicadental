package com.empresa.calzado.erp.ventas;

import com.empresa.calzado.erp.inventario.Bodega;
import com.empresa.calzado.erp.pos.Sucursal;
import com.empresa.calzado.erp.seguridad.SystemUser;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orden")
public class Orden {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_orden")
    private Integer id;

    @Column(name = "numero_orden", nullable = false, unique = true, length = 35)
    private String numeroOrden;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_customer", nullable = false)
    private Customer customer;

    @Column(name = "canal_venta", nullable = false, length = 30)
    private String canalVenta; // ONLINE_ECOMMERCE, FISICO_TIENDA_POS

    @Column(name = "tipo_entrega", nullable = false, length = 30)
    private String tipoEntrega; // ENVIO_DOMICILIO, RETIRO_TIENDA_CLICK_COLLECT

    @ManyToOne
    @JoinColumn(name = "id_user_cajero")
    private SystemUser userCajero;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_bodega_despacho", nullable = false)
    private Bodega bodegaDespacho;

    // id_sucursal_retiro already exists in schema.sql; mapped additively so a click & collect
    // order records where the shopper will pick it up. It stays null for home-delivery orders.
    @ManyToOne
    @JoinColumn(name = "id_sucursal_retiro")
    private Sucursal sucursalRetiro;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_orden_estado", nullable = false)
    private OrdenEstado ordenEstado;

    @Column(name = "subtotal_productos", nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotalProductos;

    @Column(name = "costo_envio", nullable = false, precision = 10, scale = 2)
    private BigDecimal costoEnvio = BigDecimal.ZERO;

    @Column(name = "comision_pasarela", nullable = false, precision = 10, scale = 2)
    private BigDecimal comisionPasarela = BigDecimal.ZERO;

    @Column(name = "descuento_aplicado", nullable = false, precision = 10, scale = 2)
    private BigDecimal descuentoAplicado = BigDecimal.ZERO;

    // id_cupon already exists in schema.sql; mapped additively so checkout can persist the coupon
    // that produced descuento_aplicado. It stays null for orders without a coupon.
    @ManyToOne
    @JoinColumn(name = "id_cupon")
    private CuponDescuento cupon;

    @Column(name = "total_a_pagar", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAPagar;

    @Column(name = "fecha_expiracion_ttl_2h", nullable = false)
    private OffsetDateTime fechaExpiracionTtl2h;

    @Column(name = "created_by")
    private Integer createdBy;

    @Column(name = "updated_by")
    private Integer updatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @OneToMany(mappedBy = "orden", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrdenLinea> lineas = new ArrayList<>();

    public Orden() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getNumeroOrden() { return numeroOrden; }
    public void setNumeroOrden(String numeroOrden) { this.numeroOrden = numeroOrden; }
    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }
    public String getCanalVenta() { return canalVenta; }
    public void setCanalVenta(String canalVenta) { this.canalVenta = canalVenta; }
    public String getTipoEntrega() { return tipoEntrega; }
    public void setTipoEntrega(String tipoEntrega) { this.tipoEntrega = tipoEntrega; }
    public SystemUser getUserCajero() { return userCajero; }
    public void setUserCajero(SystemUser userCajero) { this.userCajero = userCajero; }
    public Bodega getBodegaDespacho() { return bodegaDespacho; }
    public void setBodegaDespacho(Bodega bodegaDespacho) { this.bodegaDespacho = bodegaDespacho; }
    public Sucursal getSucursalRetiro() { return sucursalRetiro; }
    public void setSucursalRetiro(Sucursal sucursalRetiro) { this.sucursalRetiro = sucursalRetiro; }
    public OrdenEstado getOrdenEstado() { return ordenEstado; }
    public void setOrdenEstado(OrdenEstado ordenEstado) { this.ordenEstado = ordenEstado; }
    public BigDecimal getSubtotalProductos() { return subtotalProductos; }
    public void setSubtotalProductos(BigDecimal subtotalProductos) { this.subtotalProductos = subtotalProductos; }
    public BigDecimal getCostoEnvio() { return costoEnvio; }
    public void setCostoEnvio(BigDecimal costoEnvio) { this.costoEnvio = costoEnvio; }
    public BigDecimal getComisionPasarela() { return comisionPasarela; }
    public void setComisionPasarela(BigDecimal comisionPasarela) { this.comisionPasarela = comisionPasarela; }
    public BigDecimal getDescuentoAplicado() { return descuentoAplicado; }
    public void setDescuentoAplicado(BigDecimal descuentoAplicado) { this.descuentoAplicado = descuentoAplicado; }
    public CuponDescuento getCupon() { return cupon; }
    public void setCupon(CuponDescuento cupon) { this.cupon = cupon; }
    public BigDecimal getTotalAPagar() { return totalAPagar; }
    public void setTotalAPagar(BigDecimal totalAPagar) { this.totalAPagar = totalAPagar; }
    public OffsetDateTime getFechaExpiracionTtl2h() { return fechaExpiracionTtl2h; }
    public void setFechaExpiracionTtl2h(OffsetDateTime fechaExpiracionTtl2h) { this.fechaExpiracionTtl2h = fechaExpiracionTtl2h; }
    public Integer getCreatedBy() { return createdBy; }
    public void setCreatedBy(Integer createdBy) { this.createdBy = createdBy; }
    public Integer getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Integer updatedBy) { this.updatedBy = updatedBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public List<OrdenLinea> getLineas() { return lineas; }
    public void setLineas(List<OrdenLinea> lineas) { this.lineas = lineas; }
}
