package com.empresa.calzado.erp.soporte;

import com.empresa.calzado.erp.ventas.Customer;
import com.empresa.calzado.erp.ventas.Orden;
import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "ticket_soporte")
public class TicketSoporte {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_ticket")
    private Integer id;

    @Column(name = "numero_ticket", nullable = false, unique = true, length = 35)
    private String numeroTicket;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_customer", nullable = false)
    private Customer customer;

    @ManyToOne
    @JoinColumn(name = "id_orden")
    private Orden orden;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_motivo_queja", nullable = false)
    private MotivoQueja motivoQueja;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_ticket_estado", nullable = false)
    private TicketEstado ticketEstado;

    @Column(name = "area_derivada", nullable = false, length = 30)
    private String areaDerivada = "NINGUNA";

    @Column(name = "descripcion", nullable = false, columnDefinition = "text")
    private String descripcion;

    @Column(name = "resolucion_texto", columnDefinition = "text")
    private String resolucionTexto;

    @Column(name = "fecha_ultima_interaccion", nullable = false)
    private OffsetDateTime fechaUltimaInteraccion = OffsetDateTime.now();

    @Column(name = "alerta_estancado_6h_disparada", nullable = false)
    private Boolean alertaEstancado6hDisparada = false;

    @Column(name = "created_by")
    private Integer createdBy;

    @Column(name = "updated_by")
    private Integer updatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public TicketSoporte() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getNumeroTicket() { return numeroTicket; }
    public void setNumeroTicket(String numeroTicket) { this.numeroTicket = numeroTicket; }
    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }
    public Orden getOrden() { return orden; }
    public void setOrden(Orden orden) { this.orden = orden; }
    public MotivoQueja getMotivoQueja() { return motivoQueja; }
    public void setMotivoQueja(MotivoQueja motivoQueja) { this.motivoQueja = motivoQueja; }
    public TicketEstado getTicketEstado() { return ticketEstado; }
    public void setTicketEstado(TicketEstado ticketEstado) { this.ticketEstado = ticketEstado; }
    public String getAreaDerivada() { return areaDerivada; }
    public void setAreaDerivada(String areaDerivada) { this.areaDerivada = areaDerivada; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public String getResolucionTexto() { return resolucionTexto; }
    public void setResolucionTexto(String resolucionTexto) { this.resolucionTexto = resolucionTexto; }
    public OffsetDateTime getFechaUltimaInteraccion() { return fechaUltimaInteraccion; }
    public void setFechaUltimaInteraccion(OffsetDateTime fechaUltimaInteraccion) { this.fechaUltimaInteraccion = fechaUltimaInteraccion; }
    public Boolean getAlertaEstancado6hDisparada() { return alertaEstancado6hDisparada; }
    public void setAlertaEstancado6hDisparada(Boolean alertaEstancado6hDisparada) { this.alertaEstancado6hDisparada = alertaEstancado6hDisparada; }
    public Integer getCreatedBy() { return createdBy; }
    public void setCreatedBy(Integer createdBy) { this.createdBy = createdBy; }
    public Integer getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Integer updatedBy) { this.updatedBy = updatedBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
