package com.empresa.calzado.erp.ventas;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "tarifario_envio")
public class TarifarioEnvio {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tarifa")
    private Integer id;

    @Column(name = "zona_nombre", nullable = false, length = 80)
    private String zonaNombre;

    @Column(name = "codigo_postal_prefijo", length = 20)
    private String codigoPostalPrefijo;

    @Column(name = "distancia_km_max", nullable = false, precision = 8, scale = 2)
    private BigDecimal distanciaKmMax;

    @Column(name = "costo_flete_base", nullable = false, precision = 10, scale = 2)
    private BigDecimal costoFleteBase;

    @Column(name = "umbral_compra_envio_gratis", nullable = false, precision = 10, scale = 2)
    private BigDecimal umbralCompraEnvioGratis = BigDecimal.valueOf(70.00);

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    public TarifarioEnvio() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getZonaNombre() { return zonaNombre; }
    public void setZonaNombre(String zonaNombre) { this.zonaNombre = zonaNombre; }
    public String getCodigoPostalPrefijo() { return codigoPostalPrefijo; }
    public void setCodigoPostalPrefijo(String codigoPostalPrefijo) { this.codigoPostalPrefijo = codigoPostalPrefijo; }
    public BigDecimal getDistanciaKmMax() { return distanciaKmMax; }
    public void setDistanciaKmMax(BigDecimal distanciaKmMax) { this.distanciaKmMax = distanciaKmMax; }
    public BigDecimal getCostoFleteBase() { return costoFleteBase; }
    public void setCostoFleteBase(BigDecimal costoFleteBase) { this.costoFleteBase = costoFleteBase; }
    public BigDecimal getUmbralCompraEnvioGratis() { return umbralCompraEnvioGratis; }
    public void setUmbralCompraEnvioGratis(BigDecimal umbralCompraEnvioGratis) { this.umbralCompraEnvioGratis = umbralCompraEnvioGratis; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}
