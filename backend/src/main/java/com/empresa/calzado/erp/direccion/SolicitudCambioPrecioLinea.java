package com.empresa.calzado.erp.direccion;

import com.empresa.calzado.erp.catalogo.Modelo;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "solicitud_cambio_precio_linea")
public class SolicitudCambioPrecioLinea {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_solicitud_precio_linea")
    private Integer id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_solicitud_precio", nullable = false)
    private SolicitudCambioPrecio solicitudCambioPrecio;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_modelo", nullable = false)
    private Modelo modelo;

    @Column(name = "precio_anterior", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioAnterior;

    @Column(name = "precio_nuevo_propuesto", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioNuevoPropuesto;

    @Column(name = "porcentaje_descuento_outlet", nullable = false, precision = 5, scale = 2)
    private BigDecimal porcentajeDescuentoOutlet = BigDecimal.ZERO;

    public SolicitudCambioPrecioLinea() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public SolicitudCambioPrecio getSolicitudCambioPrecio() { return solicitudCambioPrecio; }
    public void setSolicitudCambioPrecio(SolicitudCambioPrecio solicitudCambioPrecio) { this.solicitudCambioPrecio = solicitudCambioPrecio; }
    public Modelo getModelo() { return modelo; }
    public void setModelo(Modelo modelo) { this.modelo = modelo; }
    public BigDecimal getPrecioAnterior() { return precioAnterior; }
    public void setPrecioAnterior(BigDecimal precioAnterior) { this.precioAnterior = precioAnterior; }
    public BigDecimal getPrecioNuevoPropuesto() { return precioNuevoPropuesto; }
    public void setPrecioNuevoPropuesto(BigDecimal precioNuevoPropuesto) { this.precioNuevoPropuesto = precioNuevoPropuesto; }
    public BigDecimal getPorcentajeDescuentoOutlet() { return porcentajeDescuentoOutlet; }
    public void setPorcentajeDescuentoOutlet(BigDecimal porcentajeDescuentoOutlet) { this.porcentajeDescuentoOutlet = porcentajeDescuentoOutlet; }
}
