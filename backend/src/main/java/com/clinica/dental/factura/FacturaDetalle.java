package com.clinica.dental.factura;

import com.clinica.dental.realizado.TratamientoRealizado;
import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * Linea del comprobante: un tratamiento realizado con el costo que tenia al registrarse (S9).
 *
 * <p>{@code id_realizado} es unico en la base, de modo que un procedimiento se factura una sola vez.
 * El monto no se recalcula desde el catalogo: se copia el costo congelado del realizado.
 */
@Entity
@Table(name = "factura_detalle")
public class FacturaDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_detalle")
    private Integer id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "id_factura", nullable = false)
    private Factura factura;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "id_realizado", nullable = false, unique = true)
    private TratamientoRealizado realizado;

    @Column(name = "descripcion", nullable = false, length = 200)
    private String descripcion;

    @Column(name = "monto", nullable = false, precision = 10, scale = 2)
    private BigDecimal monto;

    public FacturaDetalle() {
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Factura getFactura() { return factura; }
    public void setFactura(Factura factura) { this.factura = factura; }
    public TratamientoRealizado getRealizado() { return realizado; }
    public void setRealizado(TratamientoRealizado realizado) { this.realizado = realizado; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal monto) { this.monto = monto; }
}
