package com.clinica.dental.recordatorio;

import com.clinica.dental.cita.Cita;
import jakarta.persistence.*;

import java.time.OffsetDateTime;

/**
 * Bitacora de recordatorios de una cita (S5, S15).
 *
 * <p>Cada cita programada deja aqui exactamente una fila pendiente con fecha objetivo 24 horas antes
 * del inicio. El proceso programado la despacha y sobrescribe el mismo registro con el resultado
 * real: {@code ENVIADA} con su hora, {@code SIMULADA} cuando no hay SMTP configurado o
 * {@code FALLIDA} con el texto del error. La fila nunca se borra, porque es el historial que se
 * consulta desde la interfaz.
 */
@Entity
@Table(name = "notificacion_cita")
public class NotificacionCita {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_notificacion")
    private Integer id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "id_cita", nullable = false)
    private Cita cita;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "id_canal", nullable = false)
    private CanalNotificacion canal;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "id_estado_notificacion", nullable = false)
    private EstadoNotificacion estado;

    @Column(name = "destino", nullable = false, length = 120)
    private String destino;

    @Column(name = "fecha_objetivo", nullable = false)
    private OffsetDateTime fechaObjetivo;

    @Column(name = "mensaje", nullable = false, columnDefinition = "text")
    private String mensaje;

    @Column(name = "enviado_en")
    private OffsetDateTime enviadoEn;

    @Column(name = "error", length = 300)
    private String error;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public NotificacionCita() {
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Cita getCita() { return cita; }
    public void setCita(Cita cita) { this.cita = cita; }
    public CanalNotificacion getCanal() { return canal; }
    public void setCanal(CanalNotificacion canal) { this.canal = canal; }
    public EstadoNotificacion getEstado() { return estado; }
    public void setEstado(EstadoNotificacion estado) { this.estado = estado; }
    public String getDestino() { return destino; }
    public void setDestino(String destino) { this.destino = destino; }
    public OffsetDateTime getFechaObjetivo() { return fechaObjetivo; }
    public void setFechaObjetivo(OffsetDateTime fechaObjetivo) { this.fechaObjetivo = fechaObjetivo; }
    public String getMensaje() { return mensaje; }
    public void setMensaje(String mensaje) { this.mensaje = mensaje; }
    public OffsetDateTime getEnviadoEn() { return enviadoEn; }
    public void setEnviadoEn(OffsetDateTime enviadoEn) { this.enviadoEn = enviadoEn; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
