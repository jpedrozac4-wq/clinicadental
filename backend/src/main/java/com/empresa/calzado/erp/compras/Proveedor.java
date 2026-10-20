package com.empresa.calzado.erp.compras;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "proveedor")
public class Proveedor {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_proveedor")
    private Integer id;

    @Column(name = "razon_social", nullable = false, length = 120)
    private String razonSocial;

    @Column(name = "contacto_nombre", length = 80)
    private String contactoNombre;

    @Column(name = "telefono", length = 25)
    private String telefono;

    @Column(name = "email", length = 100)
    private String email;

    @Column(name = "direccion", columnDefinition = "text")
    private String direccion;

    @Column(name = "lead_time_dias_promedio", nullable = false)
    private Integer leadTimeDiasPromedio = 7;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    public Proveedor() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getRazonSocial() { return razonSocial; }
    public void setRazonSocial(String razonSocial) { this.razonSocial = razonSocial; }
    public String getContactoNombre() { return contactoNombre; }
    public void setContactoNombre(String contactoNombre) { this.contactoNombre = contactoNombre; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }
    public Integer getLeadTimeDiasPromedio() { return leadTimeDiasPromedio; }
    public void setLeadTimeDiasPromedio(Integer leadTimeDiasPromedio) { this.leadTimeDiasPromedio = leadTimeDiasPromedio; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}
