package com.clinica.dental.usuario;

import com.clinica.dental.auditoria.AccionAuditoria;
import com.clinica.dental.auditoria.AuditoriaService;
import com.clinica.dental.auditoria.DetalleAuditoria;
import com.clinica.dental.seguridad.Role;
import com.clinica.dental.seguridad.SystemUser;
import com.clinica.dental.seguridad.SystemUserRepository;
import com.clinica.dental.shared.ClinicaBusinessException;
import com.clinica.dental.usuario.dto.UsuarioRequest;
import com.clinica.dental.usuario.dto.UsuarioResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Administracion de los usuarios del sistema (S18, S20).
 *
 * <p>Cada metodo publico resuelve una sola operacion de negocio y deja la transaccion cerrada al
 * salir, de modo que un worker posterior pueda envolverlo para escribir auditoria (S19) sin tener
 * que partir este servicio.
 *
 * <p>Invariantes que se sostienen aca y no en la base: username y email unicos (409), el sistema
 * siempre conserva al menos un administrador activo (409) y nadie puede darse de baja a si mismo
 * (409).
 *
 * <p>Cada alta, edicion y baja deja su fila de auditoria (S19) al final del metodo, dentro de la
 * misma transaccion. El detalle publica identidad, rol y estado: nunca la contrasenia ni su hash.
 * Las operaciones rechazadas por una invariante no auditan nada porque no escriben nada.
 */
@Service
public class UsuarioService {

    /** Rol canonico que la agenda consulta para poblar el selector de odontologos (S20). */
    static final String ROL_DENTISTA = "ROLE_DENTISTA";
    /** Rol cuya baja dejaria al sistema sin quien administre usuarios y permisos. */
    static final String ROL_ADMINISTRADOR = "ROLE_ADMINISTRADOR";

    private static final String PREFIJO_ROL = "ROLE_";
    private static final Pattern NOMBRE_ROL = Pattern.compile("^ROLE_[A-Z0-9_]+$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[A-Za-z]{2,}$");

    private final SystemUserRepository usuarios;
    private final RolRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoria;

    public UsuarioService(SystemUserRepository usuarios, RolRepository roles,
                          PasswordEncoder passwordEncoder, AuditoriaService auditoria) {
        this.usuarios = usuarios;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.auditoria = auditoria;
    }

    /**
     * Padron filtrable por rol, estado y texto libre (username, nombre completo o email).
     *
     * <p>{@code rol} acepta la forma corta ({@code DENTISTA}) o la canonica ({@code ROLE_DENTISTA}).
     * {@code estado} acepta {@code activo|inactivo}; sin estado se devuelve todo el padron, porque
     * las bajas logicas solo son alcanzables desde este listado.
     *
     * <p>Excepcion documentada de S20: pedir {@code rol=DENTISTA} sin {@code estado} devuelve solo
     * odontologos activos, para que el formulario de agenda nunca ofrezca un profesional dado de
     * baja. Un {@code estado} explicito sigue mandando cuando el administrador lo pide.
     */
    @Transactional(readOnly = true)
    public List<UsuarioResponse> listar(String rol, String estado, String q) {
        String rolFiltro = normalizarRol(rol);
        Boolean activo = normalizarEstado(estado);
        if (activo == null && ROL_DENTISTA.equals(rolFiltro)) {
            activo = Boolean.TRUE;
        }
        String texto = (q == null || q.isBlank()) ? null : "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        return usuarios.buscar(rolFiltro, activo, texto).stream().map(UsuarioResponse::desde).toList();
    }

    /** Alta de un usuario con contrasenia obligatoria y un unico rol asignado (S18). */
    @Transactional
    public UsuarioResponse crear(UsuarioRequest request) {
        validar(request);
        exigir(request.password(), "La contrasenia es obligatoria para crear un usuario");
        Role rol = rolExistente(request.rol());
        verificarDisponibilidad(request.username(), request.email(), null);

        SystemUser usuario = new SystemUser();
        usuario.setUsername(request.username().trim());
        usuario.setFullName(request.fullName().trim());
        usuario.setEmail(request.email().trim());
        usuario.setPasswordHash(passwordEncoder.encode(request.password()));
        usuario.setActive(true);
        usuario.getRoles().add(rol);

        SystemUser guardado = usuarios.save(usuario);
        auditar(AccionAuditoria.USUARIO_CREAR, guardado);
        return UsuarioResponse.desde(guardado);
    }

    /**
     * Edicion de datos y rol. La contrasenia solo se re-hashea cuando llega: un formulario que no la
     * toca no puede invalidar el acceso del usuario (S18).
     */
    @Transactional
    public UsuarioResponse actualizar(Integer id, UsuarioRequest request) {
        validar(request);
        SystemUser usuario = buscar(id);
        Role rol = rolExistente(request.rol());
        verificarDisponibilidad(request.username(), request.email(), id);

        usuario.setUsername(request.username().trim());
        usuario.setFullName(request.fullName().trim());
        usuario.setEmail(request.email().trim());
        if (request.password() != null && !request.password().isBlank()) {
            usuario.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        // Un usuario tiene un rol en la interfaz: se reemplaza el conjunto completo.
        usuario.getRoles().clear();
        usuario.getRoles().add(rol);

        SystemUser guardado = usuarios.save(usuario);
        auditar(AccionAuditoria.USUARIO_ACTUALIZAR, guardado);
        return UsuarioResponse.desde(guardado);
    }

    /**
     * Baja logica e idempotente: el usuario queda {@code active=false} y conserva su historial.
     *
     * @param actorUsername usuario autenticado que ejecuta la baja, para impedir el auto-bloqueo
     */
    @Transactional
    public void darDeBaja(Integer id, String actorUsername) {
        SystemUser usuario = buscar(id);
        if (Boolean.FALSE.equals(usuario.getActive())) {
            return;
        }
        if (actorUsername != null && actorUsername.equals(usuario.getUsername())) {
            throw new ClinicaBusinessException(HttpStatus.CONFLICT,
                "Un usuario no puede darse de baja a si mismo");
        }
        verificarAdministradorRestante(usuario);

        usuario.setActive(false);
        usuarios.save(usuario);
        auditar(AccionAuditoria.USUARIO_BAJA, usuario);
    }

    /**
     * Una fila de auditoria por alta, edicion o baja de usuario (S19).
     *
     * <p>Publica identidad, rol y estado, que es lo que un revisor necesita para reconstruir quien
     * cambio que. La contrasenia y su hash no entran nunca en el detalle.
     */
    private void auditar(AccionAuditoria accion, SystemUser usuario) {
        auditoria.registrar(accion, usuario.getId(),
            DetalleAuditoria.nuevo()
                .con("idUsuario", usuario.getId())
                .con("username", usuario.getUsername())
                .con("rol", rolPrincipal(usuario))
                .con("activo", usuario.getActive()));
    }

    /** Rol con el que el usuario quedo en el padron; la interfaz administra uno solo. */
    private static String rolPrincipal(SystemUser usuario) {
        return usuario.getRoles().stream()
            .map(Role::getRoleName)
            .sorted()
            .findFirst()
            .orElse(null);
    }

    private void verificarAdministradorRestante(SystemUser usuario) {
        boolean esAdministrador = usuario.getRoles().stream()
            .anyMatch(rol -> ROL_ADMINISTRADOR.equals(rol.getRoleName()));
        if (esAdministrador && usuarios.contarActivosPorRol(ROL_ADMINISTRADOR) <= 1) {
            throw new ClinicaBusinessException(HttpStatus.CONFLICT,
                "El sistema debe conservar al menos un administrador activo");
        }
    }

    private void verificarDisponibilidad(String username, String email, Integer idPropio) {
        Optional<SystemUser> porUsername = usuarios.findByUsername(username.trim());
        if (porUsername.isPresent() && !porUsername.get().getId().equals(idPropio)) {
            throw new ClinicaBusinessException(HttpStatus.CONFLICT, "El username ya esta registrado");
        }
        Optional<SystemUser> porEmail = usuarios.findByEmail(email.trim());
        if (porEmail.isPresent() && !porEmail.get().getId().equals(idPropio)) {
            throw new ClinicaBusinessException(HttpStatus.CONFLICT, "El email ya esta registrado");
        }
    }

    private SystemUser buscar(Integer id) {
        return usuarios.findById(id)
            .orElseThrow(() -> new ClinicaBusinessException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
    }

    private Role rolExistente(String rol) {
        String canonico = normalizarRol(rol);
        return roles.findByRoleName(canonico)
            .orElseThrow(() -> new ClinicaBusinessException("El rol indicado no existe: " + canonico));
    }

    /** Acepta {@code DENTISTA} o {@code ROLE_DENTISTA} y siempre devuelve la forma canonica. */
    private static String normalizarRol(String rol) {
        if (rol == null || rol.isBlank()) {
            return null;
        }
        String limpio = rol.trim().toUpperCase(Locale.ROOT);
        String canonico = limpio.startsWith(PREFIJO_ROL) ? limpio : PREFIJO_ROL + limpio;
        if (!NOMBRE_ROL.matcher(canonico).matches()) {
            throw new ClinicaBusinessException("El rol indicado no es valido: " + rol);
        }
        return canonico;
    }

    private static Boolean normalizarEstado(String estado) {
        if (estado == null || estado.isBlank()) {
            return null;
        }
        return switch (estado.trim().toLowerCase(Locale.ROOT)) {
            case "activo" -> Boolean.TRUE;
            case "inactivo" -> Boolean.FALSE;
            default -> throw new ClinicaBusinessException("El estado debe ser 'activo' o 'inactivo'");
        };
    }

    private static void validar(UsuarioRequest request) {
        if (request == null) {
            throw new ClinicaBusinessException("Los datos del usuario son obligatorios");
        }
        exigir(request.username(), "El username es obligatorio");
        exigir(request.fullName(), "El nombre completo es obligatorio");
        exigir(request.email(), "El email es obligatorio");
        exigir(request.rol(), "El rol es obligatorio");
        if (!EMAIL.matcher(request.email().trim()).matches()) {
            throw new ClinicaBusinessException("El email del usuario no tiene un formato valido");
        }
    }

    private static void exigir(String valor, String mensaje) {
        if (valor == null || valor.isBlank()) {
            throw new ClinicaBusinessException(mensaje);
        }
    }
}
