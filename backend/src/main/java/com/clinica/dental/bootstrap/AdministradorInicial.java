package com.clinica.dental.bootstrap;

import com.clinica.dental.seguridad.Role;
import com.clinica.dental.seguridad.SystemUser;
import com.clinica.dental.seguridad.SystemUserRepository;
import com.clinica.dental.usuario.RolRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Crea el administrador inicial al arrancar la aplicacion (S23).
 *
 * <p>Es lo que permite operar una base limpia: el bootstrap de produccion deja
 * catalogos, roles, permisos y el catalogo base de tratamientos, pero ningun
 * usuario. Sin este componente habria que insertar el primer administrador a
 * mano en PostgreSQL.
 *
 * <p>Reglas, en este orden:
 * <ol>
 *   <li>Si ya existe al menos un administrador activo, no se toca nada. Un
 *       reinicio no reescribe la contrasenia de nadie.</li>
 *   <li>Si no hay ningun administrador activo y la contrasenia esta vacia, el
 *       arranque <strong>falla</strong>. Prefiero un fallo explicito antes que
 *       crear una cuenta con una contrasenia conocida o en blanco.</li>
 *   <li>Si el username o el email ya estan tomados por otra cuenta, el arranque
 *       falla con la instruccion concreta: promover esa cuenta a administrador
 *       desde la aplicacion, o arrancar con otro {@code CLINICA_ADMIN_USERNAME}.
 *       No se promueve sola, porque ascender a administrador a una cuenta
 *       existente es una decision de seguridad y no un efecto colateral de
 *       reiniciar un contenedor.</li>
 * </ol>
 *
 * <p>La contrasenia nunca se registra en el log, ni en claro ni hasheada.
 */
@Component
public class AdministradorInicial implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdministradorInicial.class);

    /** Rol canonico que define a un administrador en {@code role.role_name}. */
    static final String ROL_ADMINISTRADOR = "ROLE_ADMINISTRADOR";

    private final SystemUserRepository usuarios;
    private final RolRepository roles;
    private final PasswordEncoder passwordEncoder;

    private final String username;
    private final String password;
    private final String email;
    private final String fullName;

    public AdministradorInicial(SystemUserRepository usuarios,
                                RolRepository roles,
                                PasswordEncoder passwordEncoder,
                                @Value("${clinica.bootstrap.admin.username:admin}") String username,
                                @Value("${clinica.bootstrap.admin.password:}") String password,
                                @Value("${clinica.bootstrap.admin.email:admin@clinicaldental.com}") String email,
                                @Value("${clinica.bootstrap.admin.full-name:Administrador de la Clinica}") String fullName) {
        this.usuarios = usuarios;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.password = password;
        this.email = email;
        this.fullName = fullName;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (usuarios.contarActivosPorRol(ROL_ADMINISTRADOR) > 0) {
            log.info("Administrador inicial: ya existe al menos un administrador activo; no se crea ninguno.");
            return;
        }

        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                "No hay ningun administrador activo y CLINICA_ADMIN_PASSWORD esta vacia. "
                    + "Defini esa variable en el .env (o en el entorno del contenedor) y volve a arrancar. "
                    + "No se crea un administrador con contrasenia en blanco.");
        }

        String usuario = requerido(username, "CLINICA_ADMIN_USERNAME");
        String correo = requerido(email, "CLINICA_ADMIN_EMAIL");

        Role rol = roles.findByRoleName(ROL_ADMINISTRADOR)
            .orElseThrow(() -> new IllegalStateException(
                "El rol " + ROL_ADMINISTRADOR + " no existe en la base. "
                    + "El volumen no se inicializo con bootstrap_produccion.sql: "
                    + "revisa docker-compose.yml y recrea la base."));

        usuarios.findByUsername(usuario).ifPresent(existente -> {
            throw new IllegalStateException(
                "El usuario '" + usuario + "' ya existe pero no es administrador activo. "
                    + "Reactivalo y asignale el rol Administrador desde la aplicacion, "
                    + "o arranca con otro CLINICA_ADMIN_USERNAME. No se promueve automaticamente.");
        });

        usuarios.findByEmail(correo).ifPresent(existente -> {
            throw new IllegalStateException(
                "El email '" + correo + "' ya pertenece a otra cuenta. "
                    + "Usa otro CLINICA_ADMIN_EMAIL: no se reutiliza el correo de un usuario existente.");
        });

        SystemUser administrador = new SystemUser();
        administrador.setUsername(usuario);
        administrador.setPasswordHash(passwordEncoder.encode(password));
        administrador.setHashMethod("BCRYPT");
        administrador.setFullName(requerido(fullName, "CLINICA_ADMIN_FULLNAME"));
        administrador.setEmail(correo);
        administrador.setActive(true);
        administrador.setRoles(Set.of(rol));

        usuarios.save(administrador);
        log.info("Administrador inicial '{}' creado con el rol {}. Cambia la contrasenia desde la aplicacion.",
            usuario, ROL_ADMINISTRADOR);
    }

    /** Falla con el nombre de la variable cuando un valor obligatorio llega vacio. */
    private static String requerido(String valor, String variable) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException(
                "La variable " + variable + " esta vacia y hace falta para crear el administrador inicial.");
        }
        return valor.trim();
    }
}
