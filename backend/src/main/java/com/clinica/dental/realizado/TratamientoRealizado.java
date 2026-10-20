package com.clinica.dental.realizado;

import com.clinica.dental.cita.Cita;
import com.clinica.dental.paciente.Paciente;
import com.clinica.dental.seguridad.SystemUser;
import com.clinica.dental.tratamiento.Tratamiento;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Procedimiento efectivamente realizado a un paciente (S8).
 *
 * <p>{@code costo} se copia del catalogo al registrar y queda congelado: editar el precio del
 * tratamiento no reescribe el historial clinico ni lo ya facturado.
 */
@Entity
@Table(name = "tratamiento_realizado")
public class TratamientoRealizado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_realizado")
    private Integer id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "id_paciente", nullable = false)
    private Paciente paciente;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "id_tratamiento", nullable = false)
    private Tratamiento tratamiento;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_cita")
    private Cita cita;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "id_dentista", nullable = false)
    private SystemUser dentista;

    @Column(name = "fecha_realizacion", nullable = false)
    private OffsetDateTime fechaRealizacion;

    @Column(name = "detalle_procedimiento", nullable = false)
    private String detalleProcedimiento;

    @Column(name = "observaciones")
    private String observaciones;

    @Column(name = "costo", nullable = false, precision = 10, scale = 2)
    private BigDecimal costo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public TratamientoRealizado() {
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Paciente getPaciente() { return paciente; }
    public void setPaciente(Paciente paciente) { this.paciente = paciente; }
    public Tratamiento getTratamiento() { return tratamiento; }
    public void setTratamiento(Tratamiento tratamiento) { this.tratamiento = tratamiento; }
    public Cita getCita() { return cita; }
    public void setCita(Cita cita) { this.cita = cita; }
    public SystemUser getDentista() { return dentista; }
    public void setDentista(SystemUser dentista) { this.dentista = dentista; }
    public OffsetDateTime getFechaRealizacion() { return fechaRealizacion; }
    public void setFechaRealizacion(OffsetDateTime fechaRealizacion) { this.fechaRealizacion = fechaRealizacion; }
    public String getDetalleProcedimiento() { return detalleProcedimiento; }
    public void setDetalleProcedimiento(String detalleProcedimiento) { this.detalleProcedimiento = detalleProcedimiento; }
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
    public BigDecimal getCosto() { return costo; }
    public void setCosto(BigDecimal costo) { this.costo = costo; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
