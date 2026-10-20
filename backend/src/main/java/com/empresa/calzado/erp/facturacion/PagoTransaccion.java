package com.empresa.calzado.erp.facturacion;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "pago_transaccion")
public class PagoTransaccion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_pago")
    private Integer id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_invoice", nullable = false)
    private Factura factura;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_pago_metodo", nullable = false)
    private PagoMetodo pagoMetodo;

    @Column(name = "estado_pago", nullable = false, length = 25)
    private String estadoPago; // APROBADO, RECHAZADO, PENDIENTE

    @Column(name = "monto_bruto", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoBruto;

    @Column(name = "comision_retenida", nullable = false, precision = 10, scale = 2)
    private BigDecimal comisionRetenida = BigDecimal.ZERO;

    @Column(name = "monto_neto_bancario", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoNetoBancario;

    @Column(name = "referencia_transaccion", length = 100)
    private String referenciaTransaccion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public PagoTransaccion() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Factura getFactura() { return factura; }
    public void setFactura(Factura factura) { this.factura = factura; }
    public PagoMetodo getPagoMetodo() { return pagoMetodo; }
    public void setPagoMetodo(PagoMetodo pagoMetodo) { this.pagoMetodo = pagoMetodo; }
    public String getEstadoPago() { return estadoPago; }
    public void setEstadoPago(String estadoPago) { this.estadoPago = estadoPago; }
    public BigDecimal getMontoBruto() { return montoBruto; }
    public void setMontoBruto(BigDecimal montoBruto) { this.montoBruto = montoBruto; }
    public BigDecimal getComisionRetenida() { return comisionRetenida; }
    public void setComisionRetenida(BigDecimal comisionRetenida) { this.comisionRetenida = comisionRetenida; }
    public BigDecimal getMontoNetoBancario() { return montoNetoBancario; }
    public void setMontoNetoBancario(BigDecimal montoNetoBancario) { this.montoNetoBancario = montoNetoBancario; }
    public String getReferenciaTransaccion() { return referenciaTransaccion; }
    public void setReferenciaTransaccion(String referenciaTransaccion) { this.referenciaTransaccion = referenciaTransaccion; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
