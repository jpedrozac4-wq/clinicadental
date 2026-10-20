package com.empresa.calzado.erp.facturacion;

import com.empresa.calzado.erp.ventas.Customer;
import com.empresa.calzado.erp.ventas.Orden;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "factura")
public class Factura {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_invoice")
    private Integer id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_orden", nullable = false)
    private Orden orden;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_customer", nullable = false)
    private Customer customer;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_factura_tipo", nullable = false)
    private FacturaTipo facturaTipo;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_factura_estado", nullable = false)
    private FacturaEstado facturaEstado;

    @Column(name = "numero_correlativo", nullable = false, unique = true, length = 35)
    private String numeroCorrelativo;

    @Column(name = "nit_cliente", nullable = false, length = 30)
    private String nitCliente = "CF";

    @Column(name = "razon_social", nullable = false, length = 120)
    private String razonSocial;

    @Column(name = "monto_subtotal", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoSubtotal;

    @Column(name = "monto_iva", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoIva;

    @Column(name = "monto_recargo_pasarela", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoRecargoPasarela = BigDecimal.ZERO;

    @Column(name = "invoice_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal invoiceTotal;

    @Column(name = "es_simulado", nullable = false)
    private Boolean esSimulado = true;

    @Column(name = "pdf_url", length = 255)
    private String pdfUrl;

    @Column(name = "created_by")
    private Integer createdBy;

    @Column(name = "updated_by")
    private Integer updatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @OneToMany(mappedBy = "factura", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FacturaLinea> lineas = new ArrayList<>();

    public Factura() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Orden getOrden() { return orden; }
    public void setOrden(Orden orden) { this.orden = orden; }
    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }
    public FacturaTipo getFacturaTipo() { return facturaTipo; }
    public void setFacturaTipo(FacturaTipo facturaTipo) { this.facturaTipo = facturaTipo; }
    public FacturaEstado getFacturaEstado() { return facturaEstado; }
    public void setFacturaEstado(FacturaEstado facturaEstado) { this.facturaEstado = facturaEstado; }
    public String getNumeroCorrelativo() { return numeroCorrelativo; }
    public void setNumeroCorrelativo(String numeroCorrelativo) { this.numeroCorrelativo = numeroCorrelativo; }
    public String getNitCliente() { return nitCliente; }
    public void setNitCliente(String nitCliente) { this.nitCliente = nitCliente; }
    public String getRazonSocial() { return razonSocial; }
    public void setRazonSocial(String razonSocial) { this.razonSocial = razonSocial; }
    public BigDecimal getMontoSubtotal() { return montoSubtotal; }
    public void setMontoSubtotal(BigDecimal montoSubtotal) { this.montoSubtotal = montoSubtotal; }
    public BigDecimal getMontoIva() { return montoIva; }
    public void setMontoIva(BigDecimal montoIva) { this.montoIva = montoIva; }
    public BigDecimal getMontoRecargoPasarela() { return montoRecargoPasarela; }
    public void setMontoRecargoPasarela(BigDecimal montoRecargoPasarela) { this.montoRecargoPasarela = montoRecargoPasarela; }
    public BigDecimal getInvoiceTotal() { return invoiceTotal; }
    public void setInvoiceTotal(BigDecimal invoiceTotal) { this.invoiceTotal = invoiceTotal; }
    public Boolean getEsSimulado() { return esSimulado; }
    public void setEsSimulado(Boolean esSimulado) { this.esSimulado = esSimulado; }
    public String getPdfUrl() { return pdfUrl; }
    public void setPdfUrl(String pdfUrl) { this.pdfUrl = pdfUrl; }
    public Integer getCreatedBy() { return createdBy; }
    public void setCreatedBy(Integer createdBy) { this.createdBy = createdBy; }
    public Integer getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Integer updatedBy) { this.updatedBy = updatedBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public List<FacturaLinea> getLineas() { return lineas; }
    public void setLineas(List<FacturaLinea> lineas) { this.lineas = lineas; }
}
