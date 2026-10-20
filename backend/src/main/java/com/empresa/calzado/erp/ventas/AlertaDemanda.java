package com.empresa.calzado.erp.ventas;

import com.empresa.calzado.erp.catalogo.CalzadoSku;
import jakarta.persistence.*;
import java.time.OffsetDateTime;

/**
 * A "notify me when my size is back" lead captured by the storefront on a sold-out SKU.
 *
 * <p>Maps the pre-existing {@code alerta_demanda} table. No DDL is added: the table already exists
 * in {@code schema.sql}, and {@code spring.jpa.hibernate.ddl-auto} is {@code none}.
 */
@Entity
@Table(name = "alerta_demanda")
public class AlertaDemanda {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_alerta")
    private Integer id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_calzado_sku", nullable = false)
    private CalzadoSku calzadoSku;

    @Column(name = "email_cliente", nullable = false, length = 100)
    private String emailCliente;

    @Column(name = "notificado", nullable = false)
    private Boolean notificado = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public AlertaDemanda() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public CalzadoSku getCalzadoSku() { return calzadoSku; }
    public void setCalzadoSku(CalzadoSku calzadoSku) { this.calzadoSku = calzadoSku; }
    public String getEmailCliente() { return emailCliente; }
    public void setEmailCliente(String emailCliente) { this.emailCliente = emailCliente; }
    public Boolean getNotificado() { return notificado; }
    public void setNotificado(Boolean notificado) { this.notificado = notificado; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
