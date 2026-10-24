package com.clinica.dental.pago;

import com.clinica.dental.factura.Factura;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Pago imputado a un comprobante, total o parcial (S10).
 *
 * <p>El pago es un hecho historico: no se modifica ni se borra. El saldo y el estado viven en la
 * factura y se recalculan con la suma de sus pagos.
 */
@Entity
@Table(name = "pago")
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_pago")
    private Integer id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "id_factura", nullable = false)
    private Factura factura;

    @Column(name = "fecha_pago", nullable = false)
    private OffsetDateTime fechaPago = OffsetDateTime.now();

    @Column(name = "monto", nullable = false, precision = 10, scale = 2)
    private BigDecimal monto;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "id_metodo_pago", nullable = false)
    private MetodoPago metodoPago;

    @Column(name = "referencia", length = 80)
    private String referencia;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public Pago() {
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Factura getFactura() { return factura; }
    public void setFactura(Factura factura) { this.factura = factura; }
    public OffsetDateTime getFechaPago() { return fechaPago; }
    public void setFechaPago(OffsetDateTime fechaPago) { this.fechaPago = fechaPago; }
    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal monto) { this.monto = monto; }
    public MetodoPago getMetodoPago() { return metodoPago; }
    public void setMetodoPago(MetodoPago metodoPago) { this.metodoPago = metodoPago; }
    public String getReferencia() { return referencia; }
    public void setReferencia(String referencia) { this.referencia = referencia; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
