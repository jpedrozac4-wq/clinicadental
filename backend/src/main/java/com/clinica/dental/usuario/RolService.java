package com.clinica.dental.usuario;

import com.clinica.dental.auditoria.AccionAuditoria;
import com.clinica.dental.auditoria.AuditoriaService;
import com.clinica.dental.auditoria.DetalleAuditoria;
import com.clinica.dental.seguridad.Permission;
import com.clinica.dental.seguridad.Role;
import com.clinica.dental.seguridad.SystemUserRepository;
import com.clinica.dental.shared.ClinicaBusinessException;
import com.clinica.dental.usuario.dto.RolRequest;
import com.clinica.dental.usuario.dto.RolResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Roles y su matriz de permisos (S18).
 *
 * <p>La matriz se guarda por reemplazo total: lo que llega es lo que queda. Los tres roles base
 * ({@code ROLE_ADMINISTRADOR}, {@code ROLE_DENTISTA}, {@code ROLE_RECEPCIONISTA}) no se renombran ni
 * se eliminan, pero su matriz si es editable; {@code ROLE_ADMINISTRADOR} conserva siempre el
 * catalogo completo para que el sistema nunca se quede sin quien lo administre.
 *
 * <p>Todo cambio de matriz rige en el siguiente login: el JWT ya emitido conserva las autoridades
 * con las que fue firmado.
 *
 * <p>El alta, la edicion y la eliminacion dejan su fila de auditoria (S19) al final del metodo,
 * dentro de la misma transaccion. Un rol base protegido, un nombre duplicado o un rol con usuarios
 * asignados no auditan nada porque la operacion no llega a ocurrir.
 */
@Service
public class RolService {

    static final String ROL_ADMINISTRADOR = "ROLE_ADMINISTRADOR";
    private static final String PREFIJO_ROL = "ROLE_";

    /** Roles que el codigo y la seguridad por ruta referencian de forma explicita. */
    private static final Set<String> ROLES_BASE = Set.of(
        ROL_ADMINISTRADOR, "ROLE_DENTISTA", "ROLE_RECEPCIONISTA");

    private final RolRepository roles;
    private final PermisoRepository permisos;
    private final SystemUserRepository usuarios;
    private final AuditoriaService auditoria;

    public RolService(RolRepository roles, PermisoRepository permisos,
                      SystemUserRepository usuarios, AuditoriaService auditoria) {
        this.roles = roles;
        this.permisos = permisos;
        this.usuarios = usuarios;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public List<RolResponse> listar() {
        return roles.findAllByOrderByRoleNameAsc().stream().map(RolResponse::desde).toList();
    }

    /** Alta de un rol propio de la clinica con su matriz inicial (S18). */
    @Transactional
    public RolResponse crear(RolRequest request) {
        String nombre = nombreCanonico(request);
        if (roles.findByRoleName(nombre).isPresent()) {
            throw new ClinicaBusinessException(HttpStatus.CONFLICT, "El rol ya existe: " + nombre);
        }
        Set<Permission> matriz = resolverPermisos(request.permissions());

        Role rol = new Role();
        rol.setRoleName(nombre);
        rol.setDescription(descripcion(request));
        rol.getPermissions().addAll(matriz);
        Role guardado = roles.save(rol);
        auditar(AccionAuditoria.ROL_CREAR, guardado);
        return RolResponse.desde(guardado);
    }

    /** Edicion de descripcion, nombre y matriz completa del rol (S18). */
    @Transactional
    public RolResponse actualizar(Integer id, RolRequest request) {
        String nombre = nombreCanonico(request);
        Role rol = buscar(id);
        verificarRenombrado(rol, nombre);
        Set<Permission> matriz = resolverPermisos(request.permissions());
        if (ROL_ADMINISTRADOR.equals(rol.getRoleName())) {
            matriz = new LinkedHashSet<>(permisos.findAll());
        }

        rol.setRoleName(nombre);
        rol.setDescription(descripcion(request));
        rol.getPermissions().clear();
        rol.getPermissions().addAll(matriz);
        Role guardado = roles.save(rol);
        auditar(AccionAuditoria.ROL_ACTUALIZAR, guardado);
        return RolResponse.desde(guardado);
    }

    /** Baja fisica de un rol propio y sin usuarios asignados (S18). */
    @Transactional
    public void eliminar(Integer id) {
        Role rol = buscar(id);
        if (ROLES_BASE.contains(rol.getRoleName())) {
            throw new ClinicaBusinessException(HttpStatus.CONFLICT,
                "Los roles base del sistema no pueden eliminarse: " + rol.getRoleName());
        }
        if (usuarios.contarPorRol(rol.getRoleName()) > 0) {
            throw new ClinicaBusinessException(HttpStatus.CONFLICT,
                "El rol tiene usuarios asignados y no puede eliminarse");
        }
        DetalleAuditoria detalle = detalleDe(rol);
        roles.delete(rol);
        auditoria.registrar(AccionAuditoria.ROL_ELIMINAR, rol.getId(), detalle);
    }

    private void auditar(AccionAuditoria accion, Role guardado) {
        auditoria.registrar(accion, guardado.getId(), detalleDe(guardado));
    }

    /**
     * Detalle comun a las tres operaciones de rol: nombre y tamanio de la matriz aplicada. La matriz
     * completa puede ser larga y el detalle debe seguir siendo un JSON pequeno, asi que se publica su
     * cantidad; el estado exacto de la matriz queda en {@code role_permission}.
     */
    private static DetalleAuditoria detalleDe(Role rol) {
        return DetalleAuditoria.nuevo()
            .con("idRol", rol.getId())
            .con("rol", rol.getRoleName())
            .con("permisos", rol.getPermissions().size());
    }

    private void verificarRenombrado(Role rol, String nombreNuevo) {
        if (rol.getRoleName().equals(nombreNuevo)) {
            return;
        }
        if (ROLES_BASE.contains(rol.getRoleName())) {
            throw new ClinicaBusinessException(HttpStatus.CONFLICT,
                "Los roles base del sistema no pueden renombrarse: " + rol.getRoleName());
        }
        if (roles.findByRoleName(nombreNuevo).isPresent()) {
            throw new ClinicaBusinessException(HttpStatus.CONFLICT, "El rol ya existe: " + nombreNuevo);
        }
    }

    /**
     * Traduce los {@code permission_name} recibidos a entidades. Un nombre fuera del catalogo es un
     * error del cliente (400) y no debe dejar el rol a medio guardar.
     */
    private Set<Permission> resolverPermisos(List<String> nombres) {
        if (nombres == null || nombres.isEmpty()) {
            return Set.of();
        }
        List<String> pedidos = nombres.stream()
            .filter(nombre -> nombre != null && !nombre.isBlank())
            .map(String::trim)
            .distinct()
            .toList();
        if (pedidos.isEmpty()) {
            return Set.of();
        }

        List<Permission> encontrados = permisos.findByPermissionNameIn(pedidos);
        Set<String> disponibles = encontrados.stream()
            .map(Permission::getPermissionName)
            .collect(java.util.stream.Collectors.toSet());
        List<String> desconocidos = new ArrayList<>(pedidos);
        desconocidos.removeAll(disponibles);
        if (!desconocidos.isEmpty()) {
            throw new ClinicaBusinessException("Permisos desconocidos: " + String.join(", ", desconocidos));
        }
        return new LinkedHashSet<>(encontrados);
    }

    private Role buscar(Integer id) {
        return roles.findById(id)
            .orElseThrow(() -> new ClinicaBusinessException(HttpStatus.NOT_FOUND, "Rol no encontrado"));
    }

    /** Normaliza el nombre a la forma canonica {@code ROLE_*}, que es la que viaja en el JWT. */
    private static String nombreCanonico(RolRequest request) {
        if (request == null || request.roleName() == null || request.roleName().isBlank()) {
            throw new ClinicaBusinessException("El nombre del rol es obligatorio");
        }
        String limpio = request.roleName().trim().toUpperCase(Locale.ROOT);
        return limpio.startsWith(PREFIJO_ROL) ? limpio : PREFIJO_ROL + limpio;
    }

    private static String descripcion(RolRequest request) {
        return request.description() == null ? null : request.description().trim();
    }
}
