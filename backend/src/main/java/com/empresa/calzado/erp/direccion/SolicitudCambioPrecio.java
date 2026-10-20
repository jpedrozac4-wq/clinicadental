package com.empresa.calzado.erp.direccion;

import com.empresa.calzado.erp.seguridad.SystemUser;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "solicitud_cambio_precio")
public class SolicitudCambioPrecio {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_solicitud_precio")
    private Integer id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_user_ventas", nullable = false)
    private SystemUser userVentas;

    @ManyToOne
    @JoinColumn(name = "id_user_gerente")
    private SystemUser userGerente;

    @Column(name = "tipo_solicitud", nullable = false, length = 30)
    private String tipoSolicitud; // REBAJA_OUTLET, ACTUALIZACION_LISTA, NUEVO_PRODUCTO

    @Column(name = "estado", nullable = false, length = 30)
    private String estado = "PENDIENTE_APROBACION"; // PENDIENTE_APROBACION, APROBADA, RECHAZADA

    @Column(name = "motivo_rechazo", columnDefinition = "text")
    private String motivoRechazo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @OneToMany(mappedBy = "solicitudCambioPrecio", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SolicitudCambioPrecioLinea> lineas = new ArrayList<>();

    public SolicitudCambioPrecio() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public SystemUser getUserVentas() { return userVentas; }
    public void setUserVentas(SystemUser userVentas) { this.userVentas = userVentas; }
    public SystemUser getUserGerente() { return userGerente; }
    public void setUserGerente(SystemUser userGerente) { this.userGerente = userGerente; }
    public String getTipoSolicitud() { return tipoSolicitud; }
    public void setTipoSolicitud(String tipoSolicitud) { this.tipoSolicitud = tipoSolicitud; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public String getMotivoRechazo() { return motivoRechazo; }
    public void setMotivoRechazo(String motivoRechazo) { this.motivoRechazo = motivoRechazo; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public List<SolicitudCambioPrecioLinea> getLineas() { return lineas; }
    public void setLineas(List<SolicitudCambioPrecioLinea> lineas) { this.lineas = lineas; }
}
