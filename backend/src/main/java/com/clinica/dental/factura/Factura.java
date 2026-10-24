package com.clinica.dental.factura;

import com.clinica.dental.paciente.Paciente;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Comprobante de cobro de una o varias prestaciones realizadas (S9).
 *
 * <p>{@code total} es la suma del costo congelado del detalle: la factura no agrega impuestos ni
 * recargos, y {@code saldo} es lo que falta cobrar (S9, S10). El {@code numero} se toma de una
 * serie correlativa sin huecos y solo se asigna cuando la emision ya paso todas sus validaciones.
 */
@Entity
@Table(name = "factura")
public class Factura {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_factura")
    private Integer id;

    @Column(name = "numero", nullable = false, unique = true, length = 20)
    private String numero;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "id_paciente", nullable = false)
    private Paciente paciente;

    @Column(name = "fecha_emision", nullable = false)
    private OffsetDateTime fechaEmision = OffsetDateTime.now();

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "id_estado_factura", nullable = false)
    private EstadoFactura estado;

    @Column(name = "total", nullable = false, precision = 10, scale = 2)
    private BigDecimal total;

    @Column(name = "saldo", nullable = false, precision = 10, scale = 2)
    private BigDecimal saldo;

    @Column(name = "observaciones")
    private String observaciones;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public Factura() {
    }

    /** Total efectivamente cobrado: la diferencia entre el total del comprobante y su saldo. */
    public BigDecimal getTotalPagado() {
        if (total == null || saldo == null) {
            return BigDecimal.ZERO;
        }
        return total.subtract(saldo);
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getNumero() { return numero; }
    public void setNumero(String numero) { this.numero = numero; }
    public Paciente getPaciente() { return paciente; }
    public void setPaciente(Paciente paciente) { this.paciente = paciente; }
    public OffsetDateTime getFechaEmision() { return fechaEmision; }
    public void setFechaEmision(OffsetDateTime fechaEmision) { this.fechaEmision = fechaEmision; }
    public EstadoFactura getEstado() { return estado; }
    public void setEstado(EstadoFactura estado) { this.estado = estado; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }
    public BigDecimal getSaldo() { return saldo; }
    public void setSaldo(BigDecimal saldo) { this.saldo = saldo; }
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
