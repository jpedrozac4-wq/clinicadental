package com.clinica.dental.cita;

import com.clinica.dental.paciente.Paciente;
import com.clinica.dental.seguridad.SystemUser;
import com.clinica.dental.tratamiento.Tratamiento;
import jakarta.persistence.*;

import java.time.OffsetDateTime;

/**
 * Cita de un paciente con un dentista (S4).
 *
 * <p>La duracion se toma del catalogo al agendar y el fin del bloque se deriva al exponer la cita.
 * El control de solapamiento lo resuelve la base sobre el mismo bloque inicio + duracion.
 */
@Entity
@Table(name = "cita")
public class Cita {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cita")
    private Integer id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "id_paciente", nullable = false)
    private Paciente paciente;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "id_tratamiento", nullable = false)
    private Tratamiento tratamiento;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "id_dentista", nullable = false)
    private SystemUser dentista;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "id_estado_cita", nullable = false)
    private EstadoCita estado;

    @Column(name = "fecha_hora_inicio", nullable = false)
    private OffsetDateTime fechaHoraInicio;

    @Column(name = "duracion_minutos", nullable = false)
    private Integer duracionMinutos;

    @Column(name = "motivo", length = 200)
    private String motivo;

    @Column(name = "notas")
    private String notas;

    /** Cita que esta reemplaza, cuando nace de una reprogramacion (S6). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_cita_origen")
    private Cita citaOrigen;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public Cita() {
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Paciente getPaciente() { return paciente; }
    public void setPaciente(Paciente paciente) { this.paciente = paciente; }
    public Tratamiento getTratamiento() { return tratamiento; }
    public void setTratamiento(Tratamiento tratamiento) { this.tratamiento = tratamiento; }
    public SystemUser getDentista() { return dentista; }
    public void setDentista(SystemUser dentista) { this.dentista = dentista; }
    public EstadoCita getEstado() { return estado; }
    public void setEstado(EstadoCita estado) { this.estado = estado; }
    public OffsetDateTime getFechaHoraInicio() { return fechaHoraInicio; }
    public void setFechaHoraInicio(OffsetDateTime fechaHoraInicio) { this.fechaHoraInicio = fechaHoraInicio; }
    public Integer getDuracionMinutos() { return duracionMinutos; }
    public void setDuracionMinutos(Integer duracionMinutos) { this.duracionMinutos = duracionMinutos; }
    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
    public String getNotas() { return notas; }
    public void setNotas(String notas) { this.notas = notas; }
    public Cita getCitaOrigen() { return citaOrigen; }
    public void setCitaOrigen(Cita citaOrigen) { this.citaOrigen = citaOrigen; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
