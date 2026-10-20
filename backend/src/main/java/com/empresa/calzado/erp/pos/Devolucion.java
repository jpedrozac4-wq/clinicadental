package com.empresa.calzado.erp.pos;

import com.empresa.calzado.erp.facturacion.Factura;
import com.empresa.calzado.erp.inventario.Bodega;
import com.empresa.calzado.erp.seguridad.SystemUser;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "devolucion")
public class Devolucion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_devolucion")
    private Integer id;

    @Column(name = "numero_devolucion", nullable = false, unique = true, length = 35)
    private String numeroDevolucion;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_invoice_origen", nullable = false)
    private Factura facturaOrigen;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_sucursal", nullable = false)
    private Sucursal sucursal;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_user_cajero", nullable = false)
    private SystemUser cajero;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_bodega_destino", nullable = false)
    private Bodega bodegaDestino;

    @Column(name = "motivo_devolucion", nullable = false, length = 60)
    private String motivoDevolucion;

    @Column(name = "dias_transcurridos", nullable = false)
    private Integer diasTranscurridos;

    @Column(name = "suela_intacta", nullable = false)
    private Boolean suelaIntacta;

    @Column(name = "empaque_original", nullable = false)
    private Boolean empaqueOriginal;

    @Column(name = "monto_total_reembolso", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoTotalReembolso;

    /** The credit note (a {@code factura} row of type NOTA_CREDITO) issued for this return. */
    @ManyToOne
    @JoinColumn(name = "id_invoice_nota_credito")
    private Factura notaCredito;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @OneToMany(mappedBy = "devolucion", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DevolucionLinea> lineas = new ArrayList<>();

    public Devolucion() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getNumeroDevolucion() { return numeroDevolucion; }
    public void setNumeroDevolucion(String numeroDevolucion) { this.numeroDevolucion = numeroDevolucion; }
    public Factura getFacturaOrigen() { return facturaOrigen; }
    public void setFacturaOrigen(Factura facturaOrigen) { this.facturaOrigen = facturaOrigen; }
    public Sucursal getSucursal() { return sucursal; }
    public void setSucursal(Sucursal sucursal) { this.sucursal = sucursal; }
    public SystemUser getCajero() { return cajero; }
    public void setCajero(SystemUser cajero) { this.cajero = cajero; }
    public Bodega getBodegaDestino() { return bodegaDestino; }
    public void setBodegaDestino(Bodega bodegaDestino) { this.bodegaDestino = bodegaDestino; }
    public String getMotivoDevolucion() { return motivoDevolucion; }
    public void setMotivoDevolucion(String motivoDevolucion) { this.motivoDevolucion = motivoDevolucion; }
    public Integer getDiasTranscurridos() { return diasTranscurridos; }
    public void setDiasTranscurridos(Integer diasTranscurridos) { this.diasTranscurridos = diasTranscurridos; }
    public Boolean getSuelaIntacta() { return suelaIntacta; }
    public void setSuelaIntacta(Boolean suelaIntacta) { this.suelaIntacta = suelaIntacta; }
    public Boolean getEmpaqueOriginal() { return empaqueOriginal; }
    public void setEmpaqueOriginal(Boolean empaqueOriginal) { this.empaqueOriginal = empaqueOriginal; }
    public BigDecimal getMontoTotalReembolso() { return montoTotalReembolso; }
    public void setMontoTotalReembolso(BigDecimal montoTotalReembolso) { this.montoTotalReembolso = montoTotalReembolso; }
    public Factura getNotaCredito() { return notaCredito; }
    public void setNotaCredito(Factura notaCredito) { this.notaCredito = notaCredito; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public List<DevolucionLinea> getLineas() { return lineas; }
    public void setLineas(List<DevolucionLinea> lineas) { this.lineas = lineas; }
}
