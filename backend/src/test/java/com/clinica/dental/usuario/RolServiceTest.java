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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Roles y matriz de permisos editable (S18). */
@ExtendWith(MockitoExtension.class)
class RolServiceTest {

    @Mock private RolRepository roles;
    @Mock private PermisoRepository permisos;
    @Mock private SystemUserRepository usuarios;
    @Mock private AuditoriaService auditoria;

    @InjectMocks private RolService service;

    private static Permission permiso(String nombre) {
        Permission p = new Permission();
        p.setPermissionName(nombre);
        p.setModulo("PRUEBA");
        p.setAction("READ");
        return p;
    }

    private static Role rol(int id, String nombre, String descripcion, Permission... matriz) {
        Role r = new Role();
        r.setId(id);
        r.setRoleName(nombre);
        r.setDescription(descripcion);
        r.getPermissions().addAll(Arrays.asList(matriz));
        return r;
    }

    /** Los permisos pedidos existen en el catalogo: se devuelve uno por cada nombre valido. */
    private void resolverPermisosExistentes() {
        when(permisos.findByPermissionNameIn(anyCollection())).thenAnswer(inv -> {
            List<String> nombres = inv.getArgument(0);
            return nombres.stream().map(RolServiceTest::permiso).toList();
        });
    }

    // ---------------------------------------------------------------- listado

    @Test
    void listarDevuelveCadaRolConSuMatrizDePermisos() {
        when(roles.findAllByOrderByRoleNameAsc()).thenReturn(List.of(
            rol(1, "ROLE_DENTISTA", "Odontologo", permiso("CITA_VER"), permiso("PACIENTE_VER"))));

        List<RolResponse> lista = service.listar();

        assertThat(lista).singleElement().satisfies(r -> {
            assertThat(r.id()).isEqualTo(1);
            assertThat(r.roleName()).isEqualTo("ROLE_DENTISTA");
            assertThat(r.description()).isEqualTo("Odontologo");
            assertThat(r.permissions()).containsExactly("CITA_VER", "PACIENTE_VER");
        });
    }

    // ------------------------------------------------------------------ alta

    @Test
    void crearNormalizaElNombreDelRolYGustaLaMatrizPedida() {
        when(roles.findByRoleName("ROLE_AUDITOR")).thenReturn(Optional.empty());
        resolverPermisosExistentes();
        when(roles.save(any(Role.class))).thenAnswer(inv -> {
            Role r = inv.getArgument(0);
            r.setId(7);
            return r;
        });

        RolResponse creado = service.crear(
            new RolRequest("auditor", "Consulta la bitacora", List.of("AUDITORIA_VER")));

        assertThat(creado.id()).isEqualTo(7);
        assertThat(creado.roleName()).isEqualTo("ROLE_AUDITOR");
        assertThat(creado.permissions()).containsExactly("AUDITORIA_VER");
    }

    @Test
    void crearConUnNombreDuplicadoDevuelve409YNoGuarda() {
        when(roles.findByRoleName("ROLE_DENTISTA"))
            .thenReturn(Optional.of(rol(2, "ROLE_DENTISTA", "Odontologo")));

        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.crear(new RolRequest("DENTISTA", "Duplicado", List.of("CITA_VER"))),
            ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verify(roles, never()).save(any(Role.class));
    }

    @Test
    void crearConUnPermisoDesconocidoDevuelve400YNoGuarda() {
        when(roles.findByRoleName("ROLE_AUDITOR")).thenReturn(Optional.empty());
        when(permisos.findByPermissionNameIn(anyCollection())).thenReturn(List.of(permiso("AUDITORIA_VER")));

        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.crear(new RolRequest("AUDITOR", "Consulta",
                List.of("AUDITORIA_VER", "PERMISO_INVENTADO"))),
            ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getMessage()).contains("PERMISO_INVENTADO");
        verify(roles, never()).save(any(Role.class));
    }

    @Test
    void crearSinNombreDeRolDevuelve400YNoGuarda() {
        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.crear(new RolRequest("  ", "Sin nombre", List.of())),
            ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(roles, never()).save(any(Role.class));
    }

    // --------------------------------------------------------------- edicion

    @Test
    void actualizarReemplazaLaMatrizCompletaEnLugarDeAcumularla() {
        Role auditor = rol(7, "ROLE_AUDITOR", "Consulta",
            permiso("AUDITORIA_VER"), permiso("PACIENTE_VER"), permiso("CITA_VER"));
        when(roles.findById(7)).thenReturn(Optional.of(auditor));
        resolverPermisosExistentes();
        when(roles.save(any(Role.class))).thenAnswer(inv -> inv.getArgument(0));

        RolResponse actualizado = service.actualizar(7,
            new RolRequest("ROLE_AUDITOR", "Solo bitacora", List.of("AUDITORIA_VER")));

        assertThat(actualizado.permissions()).containsExactly("AUDITORIA_VER");
        assertThat(auditor.getPermissions()).hasSize(1);
        assertThat(auditor.getDescription()).isEqualTo("Solo bitacora");
    }

    @Test
    void actualizarUnRolBaseConOtroNombreDevuelve409YNoGuarda() {
        Role base = rol(2, "ROLE_DENTISTA", "Odontologo", permiso("CITA_VER"));
        when(roles.findById(2)).thenReturn(Optional.of(base));

        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.actualizar(2, new RolRequest("ROLE_ODONTOLOGO", "Odontologo", List.of("CITA_VER"))),
            ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verify(roles, never()).save(any(Role.class));
    }

    @Test
    void actualizarUnRolBaseSiPermiteEditarSuMatriz() {
        Role base = rol(2, "ROLE_DENTISTA", "Odontologo", permiso("CITA_VER"));
        when(roles.findById(2)).thenReturn(Optional.of(base));
        resolverPermisosExistentes();
        when(roles.save(any(Role.class))).thenAnswer(inv -> inv.getArgument(0));

        RolResponse actualizado = service.actualizar(2,
            new RolRequest("DENTISTA", "Odontologo", List.of("CITA_VER", "PACIENTE_VER")));

        assertThat(actualizado.permissions()).containsExactly("CITA_VER", "PACIENTE_VER");
    }

    @Test
    void actualizarElRolAdministradorConservaTodosLosPermisos() {
        Role admin = rol(1, "ROLE_ADMINISTRADOR", "Administrador", permiso("CITA_VER"));
        when(roles.findById(1)).thenReturn(Optional.of(admin));
        resolverPermisosExistentes();
        when(permisos.findAll()).thenReturn(List.of(
            permiso("CITA_VER"), permiso("PACIENTE_VER"), permiso("USUARIOS_ADMIN")));
        when(roles.save(any(Role.class))).thenAnswer(inv -> inv.getArgument(0));

        RolResponse actualizado = service.actualizar(1,
            new RolRequest("ROLE_ADMINISTRADOR", "Administrador", List.of("CITA_VER")));

        assertThat(actualizado.permissions())
            .containsExactly("CITA_VER", "PACIENTE_VER", "USUARIOS_ADMIN");
    }

    @Test
    void actualizarConUnNombreYaUsadoDevuelve409() {
        Role auditor = rol(7, "ROLE_AUDITOR", "Consulta", permiso("AUDITORIA_VER"));
        when(roles.findById(7)).thenReturn(Optional.of(auditor));
        when(roles.findByRoleName("ROLE_DENTISTA"))
            .thenReturn(Optional.of(rol(2, "ROLE_DENTISTA", "Odontologo")));

        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.actualizar(7, new RolRequest("DENTISTA", "Consulta", List.of("AUDITORIA_VER"))),
            ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verify(roles, never()).save(any(Role.class));
    }

    @Test
    void actualizarUnRolInexistenteDevuelve404() {
        when(roles.findById(99)).thenReturn(Optional.empty());

        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.actualizar(99, new RolRequest("ROLE_AUDITOR", "Consulta", List.of())),
            ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ------------------------------------------------------------------ baja

    @Test
    void eliminarUnRolBaseDevuelve409YNoBorra() {
        when(roles.findById(3)).thenReturn(Optional.of(rol(3, "ROLE_RECEPCIONISTA", "Recepcion")));

        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.eliminar(3), ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verify(roles, never()).delete(any(Role.class));
    }

    @Test
    void eliminarUnRolConUsuariosAsignadosDevuelve409YNoBorra() {
        when(roles.findById(7)).thenReturn(Optional.of(rol(7, "ROLE_AUDITOR", "Consulta")));
        when(usuarios.contarPorRol("ROLE_AUDITOR")).thenReturn(2L);

        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.eliminar(7), ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verify(roles, never()).delete(any(Role.class));
    }

    @Test
    void eliminarUnRolSinUsuariosLoBorra() {
        Role auditor = rol(7, "ROLE_AUDITOR", "Consulta");
        when(roles.findById(7)).thenReturn(Optional.of(auditor));
        when(usuarios.contarPorRol("ROLE_AUDITOR")).thenReturn(0L);

        service.eliminar(7);

        verify(roles).delete(auditor);
    }

    @Test
    void eliminarUnRolInexistenteDevuelve404() {
        when(roles.findById(99)).thenReturn(Optional.empty());

        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.eliminar(99), ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // -------------------------------------------------- auditoria de operaciones (S19)

    @Test
    void auditaElAltaDelRolConSuNombreYMatriz() {
        resolverPermisosExistentes();
        when(roles.findByRoleName("ROLE_AUDITOR")).thenReturn(Optional.empty());
        when(roles.save(any(Role.class))).thenAnswer(inv -> {
            Role r = inv.getArgument(0);
            r.setId(7);
            return r;
        });

        service.crear(new RolRequest("auditor", null, List.of("AUDITORIA_VER", "REPORTE_VER")));

        ArgumentCaptor<DetalleAuditoria> detalle = ArgumentCaptor.forClass(DetalleAuditoria.class);
        verify(auditoria).registrar(eq(AccionAuditoria.ROL_CREAR), eq(7), detalle.capture());
        assertThat(detalle.getValue().valores())
            .containsEntry("rol", "ROLE_AUDITOR")
            .containsEntry("permisos", 2);
    }

    @Test
    void auditaLaEdicionDeLaMatrizDeUnRol() {
        resolverPermisosExistentes();
        when(roles.findById(2)).thenReturn(Optional.of(
            rol(2, "ROLE_DENTISTA", "Odontologo", permiso("CITA_VER"))));
        when(roles.save(any(Role.class))).thenAnswer(inv -> inv.getArgument(0));

        service.actualizar(2, new RolRequest("ROLE_DENTISTA", "Odontologo",
            List.of("CITA_VER", "PACIENTE_VER")));

        ArgumentCaptor<DetalleAuditoria> detalle = ArgumentCaptor.forClass(DetalleAuditoria.class);
        verify(auditoria).registrar(eq(AccionAuditoria.ROL_ACTUALIZAR), eq(2), detalle.capture());
        assertThat(detalle.getValue().valores()).containsEntry("permisos", 2);
    }

    @Test
    void auditaLaEliminacionDeUnRolSinUsuarios() {
        Role auditor = rol(7, "ROLE_AUDITOR", "Consulta");
        when(roles.findById(7)).thenReturn(Optional.of(auditor));
        when(usuarios.contarPorRol("ROLE_AUDITOR")).thenReturn(0L);

        service.eliminar(7);

        ArgumentCaptor<DetalleAuditoria> detalle = ArgumentCaptor.forClass(DetalleAuditoria.class);
        verify(auditoria).registrar(eq(AccionAuditoria.ROL_ELIMINAR), eq(7), detalle.capture());
        assertThat(detalle.getValue().valores()).containsEntry("rol", "ROLE_AUDITOR");
    }

    @Test
    void unRolBaseProtegidoNoDejaFilaDeAuditoria() {
        when(roles.findById(3)).thenReturn(Optional.of(rol(3, "ROLE_RECEPCIONISTA", "Recepcion")));
        when(roles.findById(2)).thenReturn(Optional.of(rol(2, "ROLE_DENTISTA", "Odontologo")));

        catchThrowableOfType(() -> service.eliminar(3), ClinicaBusinessException.class);
        catchThrowableOfType(
            () -> service.actualizar(2, new RolRequest("ROLE_ODONTOLOGO", null, List.of())),
            ClinicaBusinessException.class);

        verifyNoInteractions(auditoria);
    }

    @Test
    void unAltaConNombreDuplicadoNoDejaFilaDeAuditoria() {
        when(roles.findByRoleName("ROLE_AUDITOR"))
            .thenReturn(Optional.of(rol(7, "ROLE_AUDITOR", "Consulta")));

        catchThrowableOfType(
            () -> service.crear(new RolRequest("auditor", null, List.of())),
            ClinicaBusinessException.class);

        verifyNoInteractions(auditoria);
    }

    @Test
    void unaEdicionConUnPermisoDesconocidoNoDejaFilaDeAuditoria() {
        when(roles.findById(2)).thenReturn(Optional.of(rol(2, "ROLE_DENTISTA", "Odontologo")));
        when(permisos.findByPermissionNameIn(anyCollection())).thenReturn(List.of());

        catchThrowableOfType(
            () -> service.actualizar(2, new RolRequest("ROLE_DENTISTA", "Odontologo",
                List.of("PERMISO_INEXISTENTE"))),
            ClinicaBusinessException.class);

        verifyNoInteractions(auditoria);
    }
}
