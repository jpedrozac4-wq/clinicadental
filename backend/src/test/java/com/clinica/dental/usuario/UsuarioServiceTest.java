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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Administracion de usuarios del sistema (S18, S20). */
@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock private SystemUserRepository usuarios;
    @Mock private RolRepository roles;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuditoriaService auditoria;

    @InjectMocks private UsuarioService service;

    private static Role rol(String nombre) {
        Role r = new Role();
        r.setRoleName(nombre);
        return r;
    }

    private static SystemUser usuario(int id, String username, String fullName, String email,
                                      boolean activo, String rolNombre) {
        SystemUser u = new SystemUser();
        u.setId(id);
        u.setUsername(username);
        u.setFullName(fullName);
        u.setEmail(email);
        u.setActive(activo);
        u.setPasswordHash("hash-viejo");
        if (rolNombre != null) {
            u.getRoles().add(rol(rolNombre));
        }
        return u;
    }

    private static UsuarioRequest request(String username, String email, String password, String rol) {
        return new UsuarioRequest(username, "Ana Lucía Mendoza", email, password, rol);
    }

    // ---------------------------------------------------------------- listado

    @Test
    void listarSinFiltrosConsultaTodoElPadron() {
        when(usuarios.buscar(isNull(), isNull(), isNull()))
            .thenReturn(List.of(usuario(1, "admin", "Ana Mendoza", "ana@clinica.com", true, "ROLE_ADMINISTRADOR")));

        List<UsuarioResponse> lista = service.listar(null, null, null);

        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).username()).isEqualTo("admin");
        assertThat(lista.get(0).rol()).isEqualTo("ROLE_ADMINISTRADOR");
        assertThat(lista.get(0).activo()).isTrue();
    }

    @Test
    void listarAceptaElRolEnSuFormaCortaYLoNormaliza() {
        when(usuarios.buscar(eq("ROLE_RECEPCIONISTA"), any(), any())).thenReturn(List.of());

        service.listar("recepcionista", null, null);

        verify(usuarios).buscar("ROLE_RECEPCIONISTA", null, null);
    }

    @Test
    void listarAceptaElRolEnSuFormaCompleta() {
        when(usuarios.buscar(eq("ROLE_RECEPCIONISTA"), any(), any())).thenReturn(List.of());

        service.listar("ROLE_RECEPCIONISTA", null, null);

        verify(usuarios).buscar("ROLE_RECEPCIONISTA", null, null);
    }

    @Test
    void listarConUnRolIlegibleDevuelve400YNoConsulta() {
        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.listar("###", null, null), ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(usuarios, never()).buscar(any(), any(), any());
    }

    @Test
    void listarPorEstadoInactivoPideLosDadosDeBaja() {
        when(usuarios.buscar(any(), eq(Boolean.FALSE), any())).thenReturn(List.of());

        service.listar(null, "inactivo", null);

        verify(usuarios).buscar(null, Boolean.FALSE, null);
    }

    @Test
    void listarConUnEstadoDesconocidoDevuelve400() {
        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.listar(null, "suspendido", null), ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(usuarios, never()).buscar(any(), any(), any());
    }

    @Test
    void listarNormalizaLaBusquedaLibreAMinusculas() {
        when(usuarios.buscar(any(), any(), any())).thenReturn(List.of());

        service.listar(null, null, "  ANA  ");

        verify(usuarios).buscar(null, null, "%ana%");
    }

    /** S20: el selector de agenda solo puede ofrecer odontologos vigentes. */
    @Test
    void listarOdontologosSinEstadoDevuelveSoloLosActivos() {
        when(usuarios.buscar(eq("ROLE_DENTISTA"), eq(Boolean.TRUE), any()))
            .thenReturn(List.of(usuario(4, "dentista1", "Dra. Valeria Ríos", "v@clinica.com",
                true, "ROLE_DENTISTA")));

        List<UsuarioResponse> odontologos = service.listar("DENTISTA", null, null);

        verify(usuarios).buscar("ROLE_DENTISTA", Boolean.TRUE, null);
        assertThat(odontologos).singleElement().satisfies(u -> {
            assertThat(u.id()).isEqualTo(4);
            assertThat(u.nombre()).isEqualTo("Dra. Valeria Ríos");
        });
    }

    /** S18: el administrador si puede pedir los odontologos dados de baja explicitamente. */
    @Test
    void listarOdontologosInactivosRespetaElEstadoExplicito() {
        when(usuarios.buscar(any(), eq(Boolean.FALSE), any())).thenReturn(List.of());

        service.listar("DENTISTA", "inactivo", null);

        verify(usuarios).buscar("ROLE_DENTISTA", Boolean.FALSE, null);
    }

    // ------------------------------------------------------------------ alta

    @Test
    void crearHasheaLaContraseniaYAsignaElRol() {
        when(roles.findByRoleName("ROLE_DENTISTA")).thenReturn(Optional.of(rol("ROLE_DENTISTA")));
        when(passwordEncoder.encode("Secreta123")).thenReturn("hash-nuevo");
        when(usuarios.save(any(SystemUser.class))).thenAnswer(inv -> {
            SystemUser u = inv.getArgument(0);
            u.setId(9);
            return u;
        });

        UsuarioResponse creado = service.crear(
            request("dentista3", "nuevo@clinica.com", "Secreta123", "DENTISTA"));

        assertThat(creado.id()).isEqualTo(9);
        assertThat(creado.rol()).isEqualTo("ROLE_DENTISTA");
        assertThat(creado.activo()).isTrue();

        ArgumentCaptor<SystemUser> guardado = ArgumentCaptor.forClass(SystemUser.class);
        verify(usuarios).save(guardado.capture());
        assertThat(guardado.getValue().getPasswordHash()).isEqualTo("hash-nuevo");
        assertThat(guardado.getValue().getRoles()).hasSize(1);
    }

    @Test
    void crearConUsernameDuplicadoDevuelve409YNoGuarda() {
        when(roles.findByRoleName("ROLE_DENTISTA")).thenReturn(Optional.of(rol("ROLE_DENTISTA")));
        when(usuarios.findByUsername("dentista3"))
            .thenReturn(Optional.of(usuario(9, "dentista3", "Otro", "otro@clinica.com", true, "ROLE_DENTISTA")));

        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.crear(request("dentista3", "nuevo@clinica.com", "Secreta123", "DENTISTA")),
            ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verify(usuarios, never()).save(any(SystemUser.class));
    }

    @Test
    void crearConEmailDuplicadoDevuelve409YNoGuarda() {
        when(roles.findByRoleName("ROLE_DENTISTA")).thenReturn(Optional.of(rol("ROLE_DENTISTA")));
        when(usuarios.findByEmail("nuevo@clinica.com"))
            .thenReturn(Optional.of(usuario(9, "otro", "Otro", "nuevo@clinica.com", true, "ROLE_DENTISTA")));

        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.crear(request("dentista3", "nuevo@clinica.com", "Secreta123", "DENTISTA")),
            ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verify(usuarios, never()).save(any(SystemUser.class));
    }

    @Test
    void crearSinContraseniaDevuelve400YNoGuarda() {
        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.crear(request("dentista3", "nuevo@clinica.com", "   ", "DENTISTA")),
            ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(usuarios, never()).save(any(SystemUser.class));
    }

    @Test
    void crearSinUsernameDevuelve400YNoGuarda() {
        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.crear(request("", "nuevo@clinica.com", "Secreta123", "DENTISTA")),
            ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(usuarios, never()).save(any(SystemUser.class));
    }

    @Test
    void crearConEmailMalformadoDevuelve400YNoGuarda() {
        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.crear(request("dentista3", "nuevo(at)clinica", "Secreta123", "DENTISTA")),
            ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(usuarios, never()).save(any(SystemUser.class));
    }

    @Test
    void crearConUnRolInexistenteDevuelve400YNoGuarda() {
        when(roles.findByRoleName("ROLE_AUDITOR")).thenReturn(Optional.empty());

        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.crear(request("auditor", "auditor@clinica.com", "Secreta123", "AUDITOR")),
            ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(usuarios, never()).save(any(SystemUser.class));
    }

    // --------------------------------------------------------------- edicion

    @Test
    void actualizarSinContraseniaConservaElHashActual() {
        SystemUser existente = usuario(9, "dentista3", "Ana Mendoza", "ana@clinica.com", true, "ROLE_DENTISTA");
        when(usuarios.findById(9)).thenReturn(Optional.of(existente));
        when(roles.findByRoleName("ROLE_RECEPCIONISTA")).thenReturn(Optional.of(rol("ROLE_RECEPCIONISTA")));
        when(usuarios.findByUsername("dentista3")).thenReturn(Optional.of(existente));
        when(usuarios.save(any(SystemUser.class))).thenAnswer(inv -> inv.getArgument(0));

        UsuarioResponse actualizado = service.actualizar(9,
            request("dentista3", "ana@clinica.com", null, "RECEPCIONISTA"));

        verify(passwordEncoder, never()).encode(anyString());
        assertThat(existente.getPasswordHash()).isEqualTo("hash-viejo");
        assertThat(actualizado.rol()).isEqualTo("ROLE_RECEPCIONISTA");
        assertThat(existente.getRoles()).hasSize(1);
    }

    @Test
    void actualizarConContraseniaLaVuelveAHashear() {
        SystemUser existente = usuario(9, "dentista3", "Ana Mendoza", "ana@clinica.com", true, "ROLE_DENTISTA");
        when(usuarios.findById(9)).thenReturn(Optional.of(existente));
        when(roles.findByRoleName("ROLE_DENTISTA")).thenReturn(Optional.of(rol("ROLE_DENTISTA")));
        when(usuarios.findByUsername("dentista3")).thenReturn(Optional.of(existente));
        when(passwordEncoder.encode("Nueva123")).thenReturn("hash-nuevo");
        when(usuarios.save(any(SystemUser.class))).thenAnswer(inv -> inv.getArgument(0));

        service.actualizar(9, request("dentista3", "ana@clinica.com", "Nueva123", "DENTISTA"));

        assertThat(existente.getPasswordHash()).isEqualTo("hash-nuevo");
    }

    @Test
    void actualizarConUsernameDeOtroUsuarioDevuelve409YNoGuarda() {
        SystemUser existente = usuario(9, "dentista3", "Ana Mendoza", "ana@clinica.com", true, "ROLE_DENTISTA");
        when(usuarios.findById(9)).thenReturn(Optional.of(existente));
        when(roles.findByRoleName("ROLE_DENTISTA")).thenReturn(Optional.of(rol("ROLE_DENTISTA")));
        when(usuarios.findByUsername("recepcion1"))
            .thenReturn(Optional.of(usuario(5, "recepcion1", "Otra", "otra@clinica.com", true, "ROLE_RECEPCIONISTA")));

        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.actualizar(9, request("recepcion1", "ana@clinica.com", null, "DENTISTA")),
            ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verify(usuarios, never()).save(any(SystemUser.class));
    }

    @Test
    void actualizarConEmailDeOtroUsuarioDevuelve409YNoGuarda() {
        SystemUser existente = usuario(9, "dentista3", "Ana Mendoza", "ana@clinica.com", true, "ROLE_DENTISTA");
        when(usuarios.findById(9)).thenReturn(Optional.of(existente));
        when(roles.findByRoleName("ROLE_DENTISTA")).thenReturn(Optional.of(rol("ROLE_DENTISTA")));
        when(usuarios.findByUsername("dentista3")).thenReturn(Optional.of(existente));
        when(usuarios.findByEmail("otra@clinica.com"))
            .thenReturn(Optional.of(usuario(5, "recepcion1", "Otra", "otra@clinica.com", true, "ROLE_RECEPCIONISTA")));

        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.actualizar(9, request("dentista3", "otra@clinica.com", null, "DENTISTA")),
            ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verify(usuarios, never()).save(any(SystemUser.class));
    }

    @Test
    void actualizarUnUsuarioInexistenteDevuelve404() {
        when(usuarios.findById(99)).thenReturn(Optional.empty());

        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.actualizar(99, request("fantasma", "f@clinica.com", "Secreta123", "DENTISTA")),
            ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(usuarios, never()).save(any(SystemUser.class));
    }

    // ------------------------------------------------------------------ baja

    @Test
    void darDeBajaDesactivaElUsuarioSinBorrarlo() {
        SystemUser existente = usuario(9, "dentista3", "Ana Mendoza", "ana@clinica.com", true, "ROLE_DENTISTA");
        when(usuarios.findById(9)).thenReturn(Optional.of(existente));
        when(usuarios.save(any(SystemUser.class))).thenAnswer(inv -> inv.getArgument(0));

        service.darDeBaja(9, "admin");

        assertThat(existente.getActive()).isFalse();
        verify(usuarios).save(existente);
        verify(usuarios, never()).delete(any(SystemUser.class));
    }

    @Test
    void darDeBajaDosVecesEsIdempotente() {
        SystemUser yaInactivo = usuario(9, "dentista3", "Ana Mendoza", "ana@clinica.com", false, "ROLE_DENTISTA");
        when(usuarios.findById(9)).thenReturn(Optional.of(yaInactivo));

        service.darDeBaja(9, "admin");

        verify(usuarios, never()).save(any(SystemUser.class));
    }

    @Test
    void darDeBajaAlPropioActorDevuelve409YNoGuarda() {
        SystemUser actor = usuario(1, "admin", "Ana Mendoza", "ana@clinica.com", true, "ROLE_ADMINISTRADOR");
        when(usuarios.findById(1)).thenReturn(Optional.of(actor));

        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.darDeBaja(1, "admin"), ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verify(usuarios, never()).save(any(SystemUser.class));
    }

    @Test
    void darDeBajaAlUltimoAdministradorActivoDevuelve409YNoGuarda() {
        SystemUser unico = usuario(1, "admin", "Ana Mendoza", "ana@clinica.com", true, "ROLE_ADMINISTRADOR");
        when(usuarios.findById(1)).thenReturn(Optional.of(unico));
        when(usuarios.contarActivosPorRol("ROLE_ADMINISTRADOR")).thenReturn(1L);

        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.darDeBaja(1, "otro-admin"), ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verify(usuarios, never()).save(any(SystemUser.class));
    }

    @Test
    void darDeBajaAUnAdministradorCuandoQuedaOtroSiProcede() {
        SystemUser segundo = usuario(2, "admin2", "Luis Prado", "luis@clinica.com", true, "ROLE_ADMINISTRADOR");
        when(usuarios.findById(2)).thenReturn(Optional.of(segundo));
        when(usuarios.contarActivosPorRol("ROLE_ADMINISTRADOR")).thenReturn(2L);
        when(usuarios.save(any(SystemUser.class))).thenAnswer(inv -> inv.getArgument(0));

        service.darDeBaja(2, "admin");

        assertThat(segundo.getActive()).isFalse();
        verify(usuarios).save(segundo);
    }

    @Test
    void darDeBajaAUnUsuarioInexistenteDevuelve404() {
        when(usuarios.findById(99)).thenReturn(Optional.empty());

        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.darDeBaja(99, "admin"), ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // -------------------------------------------------- auditoria de operaciones (S19)

    @Test
    void auditaElAltaDelUsuarioSinPublicarLaContrasenia() {
        when(roles.findByRoleName("ROLE_DENTISTA")).thenReturn(Optional.of(rol("ROLE_DENTISTA")));
        when(passwordEncoder.encode("Secreta123")).thenReturn("hash-nuevo");
        when(usuarios.save(any(SystemUser.class))).thenAnswer(inv -> {
            SystemUser u = inv.getArgument(0);
            u.setId(9);
            return u;
        });

        service.crear(request("dentista3", "nuevo@clinica.com", "Secreta123", "DENTISTA"));

        ArgumentCaptor<DetalleAuditoria> detalle = ArgumentCaptor.forClass(DetalleAuditoria.class);
        verify(auditoria).registrar(eq(AccionAuditoria.USUARIO_CREAR), eq(9), detalle.capture());
        assertThat(detalle.getValue().valores())
            .containsEntry("username", "dentista3")
            .containsEntry("rol", "ROLE_DENTISTA")
            .containsEntry("activo", Boolean.TRUE)
            .doesNotContainKeys("password", "passwordHash", "hash");
        assertThat(detalle.getValue().valores().toString())
            .doesNotContain("Secreta123")
            .doesNotContain("hash-nuevo");
    }

    @Test
    void auditaLaEdicionDelUsuarioConElRolQueQuedo() {
        when(usuarios.findById(9)).thenReturn(Optional.of(
            usuario(9, "dentista3", "Ana Mendoza", "viejo@clinica.com", true, "ROLE_DENTISTA")));
        when(roles.findByRoleName("ROLE_RECEPCIONISTA"))
            .thenReturn(Optional.of(rol("ROLE_RECEPCIONISTA")));
        when(usuarios.save(any(SystemUser.class))).thenAnswer(inv -> inv.getArgument(0));

        service.actualizar(9, request("dentista3", "nuevo@clinica.com", null, "RECEPCIONISTA"));

        ArgumentCaptor<DetalleAuditoria> detalle = ArgumentCaptor.forClass(DetalleAuditoria.class);
        verify(auditoria).registrar(eq(AccionAuditoria.USUARIO_ACTUALIZAR), eq(9), detalle.capture());
        assertThat(detalle.getValue().valores())
            .containsEntry("rol", "ROLE_RECEPCIONISTA")
            .containsEntry("username", "dentista3");
    }

    @Test
    void auditaLaBajaLogicaDelUsuario() {
        when(usuarios.findById(9)).thenReturn(Optional.of(
            usuario(9, "recepcion1", "Rosa Vargas", "rosa@clinica.com", true, "ROLE_RECEPCIONISTA")));
        when(usuarios.save(any(SystemUser.class))).thenAnswer(inv -> inv.getArgument(0));

        service.darDeBaja(9, "admin");

        ArgumentCaptor<DetalleAuditoria> detalle = ArgumentCaptor.forClass(DetalleAuditoria.class);
        verify(auditoria).registrar(eq(AccionAuditoria.USUARIO_BAJA), eq(9), detalle.capture());
        assertThat(detalle.getValue().valores()).containsEntry("activo", Boolean.FALSE);
    }

    @Test
    void unaBajaRepetidaNoDejaFilaDeAuditoria() {
        when(usuarios.findById(9)).thenReturn(Optional.of(
            usuario(9, "recepcion1", "Rosa Vargas", "rosa@clinica.com", false, "ROLE_RECEPCIONISTA")));

        service.darDeBaja(9, "admin");

        verifyNoInteractions(auditoria);
    }

    @Test
    void unaAutoBajaYLaBajaDelUltimoAdministradorNoDejanFilaDeAuditoria() {
        when(usuarios.findById(1)).thenReturn(Optional.of(
            usuario(1, "admin", "Ana Mendoza", "ana@clinica.com", true, "ROLE_ADMINISTRADOR")));
        when(usuarios.contarActivosPorRol("ROLE_ADMINISTRADOR")).thenReturn(1L);

        catchThrowableOfType(() -> service.darDeBaja(1, "admin"), ClinicaBusinessException.class);
        catchThrowableOfType(() -> service.darDeBaja(1, "otro-admin"), ClinicaBusinessException.class);

        verifyNoInteractions(auditoria);
    }

    @Test
    void unAltaConUsernameDuplicadoNoDejaFilaDeAuditoria() {
        when(roles.findByRoleName("ROLE_DENTISTA")).thenReturn(Optional.of(rol("ROLE_DENTISTA")));
        when(usuarios.findByUsername("dentista3")).thenReturn(Optional.of(
            usuario(9, "dentista3", "Otro", "otro@clinica.com", true, "ROLE_DENTISTA")));

        catchThrowableOfType(
            () -> service.crear(request("dentista3", "nuevo@clinica.com", "Secreta123", "DENTISTA")),
            ClinicaBusinessException.class);

        verifyNoInteractions(auditoria);
    }

    @Test
    void unaEdicionConUsernameDeOtroUsuarioNoDejaFilaDeAuditoria() {
        when(usuarios.findById(9)).thenReturn(Optional.of(
            usuario(9, "dentista3", "Ana Mendoza", "ana@clinica.com", true, "ROLE_DENTISTA")));
        when(roles.findByRoleName("ROLE_DENTISTA")).thenReturn(Optional.of(rol("ROLE_DENTISTA")));
        when(usuarios.findByUsername("ocupado")).thenReturn(Optional.of(
            usuario(10, "ocupado", "Otro", "otro@clinica.com", true, "ROLE_DENTISTA")));

        catchThrowableOfType(
            () -> service.actualizar(9, request("ocupado", "ana@clinica.com", null, "DENTISTA")),
            ClinicaBusinessException.class);

        verifyNoInteractions(auditoria);
    }
}
