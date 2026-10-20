package com.empresa.calzado.erp.compras;

import com.empresa.calzado.erp.seguridad.SystemUser;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orden_compra")
public class OrdenCompra {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_orden_compra")
    private Integer id;

    @Column(name = "numero_oc", nullable = false, unique = true, length = 35)
    private String numeroOc;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_proveedor", nullable = false)
    private Proveedor proveedor;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_user_compras", nullable = false)
    private SystemUser userCompras;

    @Column(name = "estado", nullable = false, length = 30)
    private String estado = "BORRADOR";

    @Column(name = "total_estimado", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalEstimado;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @OneToMany(mappedBy = "ordenCompra", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrdenCompraLinea> lineas = new ArrayList<>();

    public OrdenCompra() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getNumeroOc() { return numeroOc; }
    public void setNumeroOc(String numeroOc) { this.numeroOc = numeroOc; }
    public Proveedor getProveedor() { return proveedor; }
    public void setProveedor(Proveedor proveedor) { this.proveedor = proveedor; }
    public SystemUser getUserCompras() { return userCompras; }
    public void setUserCompras(SystemUser userCompras) { this.userCompras = userCompras; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public BigDecimal getTotalEstimado() { return totalEstimado; }
    public void setTotalEstimado(BigDecimal totalEstimado) { this.totalEstimado = totalEstimado; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public List<OrdenCompraLinea> getLineas() { return lineas; }
    public void setLineas(List<OrdenCompraLinea> lineas) { this.lineas = lineas; }
}
