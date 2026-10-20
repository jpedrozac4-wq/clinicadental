package com.empresa.calzado.erp.logistica;

import com.empresa.calzado.erp.seguridad.SystemUser;
import com.empresa.calzado.erp.ventas.Orden;
import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "despacho_envio")
public class DespachoEnvio {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_despacho")
    private Integer id;

    @OneToOne(optional = false)
    @JoinColumn(name = "id_orden", nullable = false, unique = true)
    private Orden orden;

    @Column(name = "tipo_transporte", nullable = false, length = 30)
    private String tipoTransporte; // FLOTA_PROPIA_LOCAL, COURIER_EXTERNO

    @Column(name = "courier_nombre", length = 80)
    private String courierNombre;

    @ManyToOne
    @JoinColumn(name = "id_user_chofer")
    private SystemUser chofer;

    @Column(name = "numero_guia_tracking", nullable = false, unique = true, length = 50)
    private String numeroGuiaTracking;

    @Column(name = "codigo_secreto_otp", nullable = false, length = 6)
    private String codigoSecretoOtp;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_despacho_estado", nullable = false)
    private DespachoEstado despachoEstado;

    @Column(name = "intentos_entrega", nullable = false)
    private Short intentosEntrega = 0;

    @Column(name = "otp_verificado", nullable = false)
    private Boolean otpVerificado = false;

    @Column(name = "fecha_preparacion_bodega")
    private OffsetDateTime fechaPreparacionBodega;

    @Column(name = "fecha_salida_ruta")
    private OffsetDateTime fechaSalidaRuta;

    @Column(name = "fecha_entrega_real")
    private OffsetDateTime fechaEntregaReal;

    @Column(name = "sla_limite_preparacion_24h", nullable = false)
    private OffsetDateTime slaLimitePreparacion24h;

    @Column(name = "sla_limite_entrega_5d", nullable = false)
    private OffsetDateTime slaLimiteEntrega5d;

    @Column(name = "created_by")
    private Integer createdBy;

    @Column(name = "updated_by")
    private Integer updatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public DespachoEnvio() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Orden getOrden() { return orden; }
    public void setOrden(Orden orden) { this.orden = orden; }
    public String getTipoTransporte() { return tipoTransporte; }
    public void setTipoTransporte(String tipoTransporte) { this.tipoTransporte = tipoTransporte; }
    public String getCourierNombre() { return courierNombre; }
    public void setCourierNombre(String courierNombre) { this.courierNombre = courierNombre; }
    public SystemUser getChofer() { return chofer; }
    public void setChofer(SystemUser chofer) { this.chofer = chofer; }
    public String getNumeroGuiaTracking() { return numeroGuiaTracking; }
    public void setNumeroGuiaTracking(String numeroGuiaTracking) { this.numeroGuiaTracking = numeroGuiaTracking; }
    public String getCodigoSecretoOtp() { return codigoSecretoOtp; }
    public void setCodigoSecretoOtp(String codigoSecretoOtp) { this.codigoSecretoOtp = codigoSecretoOtp; }
    public DespachoEstado getDespachoEstado() { return despachoEstado; }
    public void setDespachoEstado(DespachoEstado despachoEstado) { this.despachoEstado = despachoEstado; }
    public Short getIntentosEntrega() { return intentosEntrega; }
    public void setIntentosEntrega(Short intentosEntrega) { this.intentosEntrega = intentosEntrega; }
    public Boolean getOtpVerificado() { return otpVerificado; }
    public void setOtpVerificado(Boolean otpVerificado) { this.otpVerificado = otpVerificado; }
    public OffsetDateTime getFechaPreparacionBodega() { return fechaPreparacionBodega; }
    public void setFechaPreparacionBodega(OffsetDateTime fechaPreparacionBodega) { this.fechaPreparacionBodega = fechaPreparacionBodega; }
    public OffsetDateTime getFechaSalidaRuta() { return fechaSalidaRuta; }
    public void setFechaSalidaRuta(OffsetDateTime fechaSalidaRuta) { this.fechaSalidaRuta = fechaSalidaRuta; }
    public OffsetDateTime getFechaEntregaReal() { return fechaEntregaReal; }
    public void setFechaEntregaReal(OffsetDateTime fechaEntregaReal) { this.fechaEntregaReal = fechaEntregaReal; }
    public OffsetDateTime getSlaLimitePreparacion24h() { return slaLimitePreparacion24h; }
    public void setSlaLimitePreparacion24h(OffsetDateTime slaLimitePreparacion24h) { this.slaLimitePreparacion24h = slaLimitePreparacion24h; }
    public OffsetDateTime getSlaLimiteEntrega5d() { return slaLimiteEntrega5d; }
    public void setSlaLimiteEntrega5d(OffsetDateTime slaLimiteEntrega5d) { this.slaLimiteEntrega5d = slaLimiteEntrega5d; }
    public Integer getCreatedBy() { return createdBy; }
    public void setCreatedBy(Integer createdBy) { this.createdBy = createdBy; }
    public Integer getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Integer updatedBy) { this.updatedBy = updatedBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
