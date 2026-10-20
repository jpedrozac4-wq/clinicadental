package com.empresa.calzado.erp.pos;

import com.empresa.calzado.erp.seguridad.SystemUser;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "sesion_caja")
public class SesionCaja {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_sesion_caja")
    private Integer id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_caja_pos", nullable = false)
    private CajaPos cajaPos;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_user_cajero", nullable = false)
    private SystemUser cajero;

    @Column(name = "monto_apertura", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoApertura;

    @Column(name = "monto_cierre_efectivo", precision = 10, scale = 2)
    private BigDecimal montoCierreEfectivo;

    @Column(name = "total_ventas_sistema", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalVentasSistema = BigDecimal.ZERO;

    @Column(name = "diferencia_arqueo", precision = 10, scale = 2)
    private BigDecimal diferenciaArqueo = BigDecimal.ZERO;

    @Column(name = "estado", nullable = false, length = 20)
    private String estado = "ABIERTA"; // ABIERTA, CERRADA, AUDITADA

    @Column(name = "fecha_apertura", nullable = false)
    private OffsetDateTime fechaApertura = OffsetDateTime.now();

    @Column(name = "fecha_cierre")
    private OffsetDateTime fechaCierre;

    public SesionCaja() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public CajaPos getCajaPos() { return cajaPos; }
    public void setCajaPos(CajaPos cajaPos) { this.cajaPos = cajaPos; }
    public SystemUser getCajero() { return cajero; }
    public void setCajero(SystemUser cajero) { this.cajero = cajero; }
    public BigDecimal getMontoApertura() { return montoApertura; }
    public void setMontoApertura(BigDecimal montoApertura) { this.montoApertura = montoApertura; }
    public BigDecimal getMontoCierreEfectivo() { return montoCierreEfectivo; }
    public void setMontoCierreEfectivo(BigDecimal montoCierreEfectivo) { this.montoCierreEfectivo = montoCierreEfectivo; }
    public BigDecimal getTotalVentasSistema() { return totalVentasSistema; }
    public void setTotalVentasSistema(BigDecimal totalVentasSistema) { this.totalVentasSistema = totalVentasSistema; }
    public BigDecimal getDiferenciaArqueo() { return diferenciaArqueo; }
    public void setDiferenciaArqueo(BigDecimal diferenciaArqueo) { this.diferenciaArqueo = diferenciaArqueo; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public OffsetDateTime getFechaApertura() { return fechaApertura; }
    public void setFechaApertura(OffsetDateTime fechaApertura) { this.fechaApertura = fechaApertura; }
    public OffsetDateTime getFechaCierre() { return fechaCierre; }
    public void setFechaCierre(OffsetDateTime fechaCierre) { this.fechaCierre = fechaCierre; }
}
