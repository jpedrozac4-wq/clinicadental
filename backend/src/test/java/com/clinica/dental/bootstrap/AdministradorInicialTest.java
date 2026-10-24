package com.clinica.dental.bootstrap;

import com.clinica.dental.seguridad.Role;
import com.clinica.dental.seguridad.SystemUser;
import com.clinica.dental.seguridad.SystemUserRepository;
import com.clinica.dental.usuario.RolRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Contrato del administrador inicial (S23).
 *
 * <p>Lo que se prueba no es "crea un usuario", sino las cuatro decisiones que
 * hacen seguro arrancar una base limpia: no pisa un administrador existente,
 * no crea nada sin contrasenia, no promueve cuentas ajenas y falla con un
 * mensaje accionable cuando el bootstrap de la base no esta aplicado.
 */
@ExtendWith(MockitoExtension.class)
class AdministradorInicialTest {

    private static final String PASSWORD = "ClaveDePrueba-123!";
    private static final String HASH = "$2a$12$hashgeneradoporelcodificador";

    @Mock
    private SystemUserRepository usuarios;
    @Mock
    private RolRepository roles;
    @Mock
    private PasswordEncoder passwordEncoder;

    private Role rolAdministrador;

    @BeforeEach
    void prepararRol() {
        rolAdministrador = new Role();
        rolAdministrador.setId(1);
        rolAdministrador.setRoleName(AdministradorInicial.ROL_ADMINISTRADOR);
    }

    private AdministradorInicial componente(String username, String password, String email, String fullName) {
        return new AdministradorInicial(usuarios, roles, passwordEncoder, username, password, email, fullName);
    }

    private AdministradorInicial componenteEstandar() {
        return componente("admin", PASSWORD, "admin@clinicaldental.com", "Administrador de la Clinica");
    }

    @Test
    @DisplayName("Si ya hay un administrador activo no crea nada ni exige contrasenia")
    void conAdministradorActivoNoHaceNada() {
        when(usuarios.contarActivosPorRol(AdministradorInicial.ROL_ADMINISTRADOR)).thenReturn(1L);

        // Contrasenia vacia a proposito: no debe importar, porque no se crea nada.
        componente("admin", "", "admin@clinicaldental.com", "Administrador").run(null);

        verify(usuarios, never()).save(any(SystemUser.class));
        verifyNoInteractions(roles, passwordEncoder);
    }

    @Test
    @DisplayName("Sin administrador y sin contrasenia falla con un mensaje que nombra la variable")
    void sinContraseniaFalla() {
        when(usuarios.contarActivosPorRol(AdministradorInicial.ROL_ADMINISTRADOR)).thenReturn(0L);

        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> componente("admin", "   ", "admin@clinicaldental.com", "Administrador").run(null));

        assertTrue(error.getMessage().contains("CLINICA_ADMIN_PASSWORD"), error.getMessage());
        verify(usuarios, never()).save(any(SystemUser.class));
    }

    @Test
    @DisplayName("Crea el administrador activo con el rol correcto y la contrasenia hasheada")
    void creaElAdministradorConLaContraseniaHasheada() {
        when(usuarios.contarActivosPorRol(AdministradorInicial.ROL_ADMINISTRADOR)).thenReturn(0L);
        when(roles.findByRoleName(AdministradorInicial.ROL_ADMINISTRADOR)).thenReturn(Optional.of(rolAdministrador));
        when(passwordEncoder.encode(PASSWORD)).thenReturn(HASH);

        componenteEstandar().run(null);

        ArgumentCaptor<SystemUser> captor = ArgumentCaptor.forClass(SystemUser.class);
        verify(usuarios).save(captor.capture());
        SystemUser creado = captor.getValue();

        assertEquals("admin", creado.getUsername());
        assertEquals("admin@clinicaldental.com", creado.getEmail());
        assertEquals("Administrador de la Clinica", creado.getFullName());
        assertTrue(creado.getActive());
        assertEquals("BCRYPT", creado.getHashMethod());
        assertEquals(1, creado.getRoles().size());
        assertSame(rolAdministrador, creado.getRoles().iterator().next());
    }

    @Test
    @DisplayName("Nunca guarda la contrasenia en claro")
    void nuncaGuardaLaContraseniaEnClaro() {
        when(usuarios.contarActivosPorRol(AdministradorInicial.ROL_ADMINISTRADOR)).thenReturn(0L);
        when(roles.findByRoleName(AdministradorInicial.ROL_ADMINISTRADOR)).thenReturn(Optional.of(rolAdministrador));
        when(passwordEncoder.encode(PASSWORD)).thenReturn(HASH);

        componenteEstandar().run(null);

        ArgumentCaptor<SystemUser> captor = ArgumentCaptor.forClass(SystemUser.class);
        verify(usuarios).save(captor.capture());
        assertEquals(HASH, captor.getValue().getPasswordHash());
        assertNotEquals(PASSWORD, captor.getValue().getPasswordHash());
    }

    @Test
    @DisplayName("Un administrador dado de baja no bloquea la creacion: cuenta solo los activos")
    void unAdministradorInactivoNoBloquea() {
        when(usuarios.contarActivosPorRol(AdministradorInicial.ROL_ADMINISTRADOR)).thenReturn(0L);
        when(roles.findByRoleName(AdministradorInicial.ROL_ADMINISTRADOR)).thenReturn(Optional.of(rolAdministrador));
        when(passwordEncoder.encode(anyString())).thenReturn(HASH);

        componenteEstandar().run(null);

        verify(usuarios).save(any(SystemUser.class));
    }

    @Test
    @DisplayName("Si el rol administrador no existe, falla y nombra el bootstrap de la base")
    void sinRolAdministradorFalla() {
        when(usuarios.contarActivosPorRol(AdministradorInicial.ROL_ADMINISTRADOR)).thenReturn(0L);
        when(roles.findByRoleName(AdministradorInicial.ROL_ADMINISTRADOR)).thenReturn(Optional.empty());

        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> componenteEstandar().run(null));

        assertTrue(error.getMessage().contains("bootstrap_produccion.sql"), error.getMessage());
        verify(usuarios, never()).save(any(SystemUser.class));
    }

    @Test
    @DisplayName("Si el username ya existe no lo promueve: falla con la instruccion concreta")
    void usernameTomadoFalla() {
        when(usuarios.contarActivosPorRol(AdministradorInicial.ROL_ADMINISTRADOR)).thenReturn(0L);
        when(roles.findByRoleName(AdministradorInicial.ROL_ADMINISTRADOR)).thenReturn(Optional.of(rolAdministrador));
        when(usuarios.findByUsername("admin")).thenReturn(Optional.of(new SystemUser()));

        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> componenteEstandar().run(null));

        assertTrue(error.getMessage().contains("CLINICA_ADMIN_USERNAME"), error.getMessage());
        verify(usuarios, never()).save(any(SystemUser.class));
    }

    @Test
    @DisplayName("Si el email ya pertenece a otra cuenta falla en vez de reutilizarlo")
    void emailTomadoFalla() {
        when(usuarios.contarActivosPorRol(AdministradorInicial.ROL_ADMINISTRADOR)).thenReturn(0L);
        when(roles.findByRoleName(AdministradorInicial.ROL_ADMINISTRADOR)).thenReturn(Optional.of(rolAdministrador));
        when(usuarios.findByUsername("admin")).thenReturn(Optional.empty());
        when(usuarios.findByEmail("admin@clinicaldental.com")).thenReturn(Optional.of(new SystemUser()));

        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> componenteEstandar().run(null));

        assertTrue(error.getMessage().contains("CLINICA_ADMIN_EMAIL"), error.getMessage());
        verify(usuarios, never()).save(any(SystemUser.class));
    }

    @Test
    @DisplayName("Un username en blanco falla nombrando su variable y no toca la base")
    void usernameVacioFalla() {
        when(usuarios.contarActivosPorRol(AdministradorInicial.ROL_ADMINISTRADOR)).thenReturn(0L);

        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> componente("  ", PASSWORD, "admin@clinicaldental.com", "Administrador").run(null));

        assertTrue(error.getMessage().contains("CLINICA_ADMIN_USERNAME"), error.getMessage());
        verify(usuarios, never()).save(any(SystemUser.class));
        // La validacion del username corre antes de resolver el rol: no se consulta.
        verifyNoInteractions(roles);
    }

    @Test
    @DisplayName("Un nombre completo en blanco falla antes de guardar")
    void nombreCompletoVacioFalla() {
        when(usuarios.contarActivosPorRol(AdministradorInicial.ROL_ADMINISTRADOR)).thenReturn(0L);
        when(roles.findByRoleName(AdministradorInicial.ROL_ADMINISTRADOR)).thenReturn(Optional.of(rolAdministrador));
        when(usuarios.findByUsername("admin")).thenReturn(Optional.empty());
        when(usuarios.findByEmail("admin@clinicaldental.com")).thenReturn(Optional.empty());

        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> componente("admin", PASSWORD, "admin@clinicaldental.com", " ").run(null));

        assertTrue(error.getMessage().contains("CLINICA_ADMIN_FULLNAME"), error.getMessage());
        verify(usuarios, never()).save(any(SystemUser.class));
        assertFalse(error.getMessage().isBlank());
    }
}
