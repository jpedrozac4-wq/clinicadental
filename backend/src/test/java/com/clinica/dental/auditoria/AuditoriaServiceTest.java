package com.clinica.dental.auditoria;

import com.clinica.dental.auditoria.dto.AuditoriaPaginaResponse;
import com.clinica.dental.auditoria.dto.AuditoriaRegistroResponse;
import com.clinica.dental.seguridad.SystemUser;
import com.clinica.dental.shared.ClinicaBusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Escritura transaccional y consulta paginada de la bitacora (S19).
 *
 * <p>El repositorio, el catalogo y el contexto estan mockeados: estas pruebas fijan la fila que se
 * arma, el detalle que se serializa, la nota de origen cuando no hay actor y la traduccion de los
 * filtros del endpoint a la consulta.
 */
@ExtendWith(MockitoExtension.class)
class AuditoriaServiceTest {

    private static final ZoneId ZONA = ZoneId.systemDefault();

    @Mock private AuditLogRepository bitacora;
    @Mock private LogAccionRepository acciones;
    @Mock private ContextoAuditoria contexto;

    @InjectMocks private AuditoriaService service;

    private static LogAccion catalogo(String codigo) {
        LogAccion accion = new LogAccion();
        accion.setId(3);
        accion.setCode(codigo);
        accion.setDescription("Descripcion de " + codigo);
        accion.setActive(true);
        return accion;
    }

    private static SystemUser usuario(int id, String username) {
        SystemUser u = new SystemUser();
        u.setId(id);
        u.setUsername(username);
        return u;
    }

    private static ContextoAuditoria.Actor actor(String username, SystemUser usuario) {
        return new ContextoAuditoria.Actor(username, usuario);
    }

    private static AuditoriaRegistroResponse fila() {
        return new AuditoriaRegistroResponse(1L, OffsetDateTime.now(), "PACIENTE_CREAR",
            "Alta de un paciente", "paciente", "7", "ana.admin", "{\"idPaciente\": 7}");
    }

    private AuditLog capturarFilaGuardada() {
        ArgumentCaptor<AuditLog> fila = ArgumentCaptor.forClass(AuditLog.class);
        verify(bitacora).save(fila.capture());
        return fila.getValue();
    }

    // ------------------------------------------------------------------ escritura

    @Test
    void registraLaFilaConElCodigoDelCatalogoLaTablaYLaClaveAfectada() {
        LogAccion catalogo = catalogo("PACIENTE_CREAR");
        when(acciones.findByCode("PACIENTE_CREAR")).thenReturn(Optional.of(catalogo));
        when(contexto.actor()).thenReturn(actor("ana.admin", usuario(4, "ana.admin")));
        when(contexto.ipAddress()).thenReturn("190.104.10.7");
        when(contexto.userAgent()).thenReturn("Mozilla/5.0 ClinicaDental");

        service.registrar(AccionAuditoria.PACIENTE_CREAR, 7,
            DetalleAuditoria.nuevo().con("idPaciente", 7).con("email", "ana@example.com"));

        AuditLog fila = capturarFilaGuardada();
        assertThat(fila.getAccion()).isSameAs(catalogo);
        assertThat(fila.getTabla()).isEqualTo("paciente");
        assertThat(fila.getRegistro()).isEqualTo("7");
        assertThat(fila.getUsuario().getUsername()).isEqualTo("ana.admin");
        assertThat(fila.getIpAddress()).isEqualTo("190.104.10.7");
        assertThat(fila.getUserAgent()).isEqualTo("Mozilla/5.0 ClinicaDental");
        assertThat(fila.getNotas()).isNull();
        assertThat(fila.getDatos()).isEqualTo("{\"idPaciente\":7,\"email\":\"ana@example.com\"}");
    }

    @Test
    void unaOperacionSinActorDejaElUsuarioNuloYDeclaraElOrigen() {
        when(acciones.findByCode("PAGO_REGISTRAR")).thenReturn(Optional.of(catalogo("PAGO_REGISTRAR")));
        when(contexto.actor()).thenReturn(actor(null, null));

        service.registrar(AccionAuditoria.PAGO_REGISTRAR, 90);

        AuditLog fila = capturarFilaGuardada();
        assertThat(fila.getUsuario()).isNull();
        assertThat(fila.getNotas()).contains("sin actor autenticado");
        assertThat(fila.getDatos()).isEqualTo("{}");
    }

    @Test
    void unActorQueNoEstaEnElPadronDejaElUsuarioNuloYLoDeclara() {
        when(acciones.findByCode("CITA_CREAR")).thenReturn(Optional.of(catalogo("CITA_CREAR")));
        when(contexto.actor()).thenReturn(actor("fantasma", null));

        service.registrar(AccionAuditoria.CITA_CREAR, 50);

        AuditLog fila = capturarFilaGuardada();
        assertThat(fila.getUsuario()).isNull();
        assertThat(fila.getNotas()).contains("fantasma");
    }

    @Test
    void conActorRegistradoSeConservaLaNotaDelLlamador() {
        when(acciones.findByCode("CITA_REPROGRAMAR")).thenReturn(Optional.of(catalogo("CITA_REPROGRAMAR")));
        when(contexto.actor()).thenReturn(actor("ana.admin", usuario(4, "ana.admin")));

        service.registrar(AccionAuditoria.CITA_REPROGRAMAR, 51, DetalleAuditoria.nuevo(),
            "Reprogramada a pedido del paciente");

        assertThat(capturarFilaGuardada().getNotas()).isEqualTo("Reprogramada a pedido del paciente");
    }

    @Test
    void sinActorSeConservaLaNotaDelLlamadorAdemasDelOrigen() {
        when(acciones.findByCode("ROL_CREAR")).thenReturn(Optional.of(catalogo("ROL_CREAR")));
        when(contexto.actor()).thenReturn(actor(null, null));

        service.registrar(AccionAuditoria.ROL_CREAR, 7, DetalleAuditoria.nuevo(), "Bootstrap");

        assertThat(capturarFilaGuardada().getNotas())
            .contains("sin actor autenticado")
            .contains("Bootstrap");
    }

    @Test
    void unCodigoFueraDelCatalogoFallaYNoEscribeNada() {
        when(acciones.findByCode("ROL_ELIMINAR")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registrar(AccionAuditoria.ROL_ELIMINAR, 7))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("ROL_ELIMINAR");

        verify(bitacora, never()).save(any(AuditLog.class));
    }

    @Test
    void recortaLaIpElAgenteYLaClaveALoLargoDeSusColumnas() {
        when(acciones.findByCode("PACIENTE_BAJA")).thenReturn(Optional.of(catalogo("PACIENTE_BAJA")));
        when(contexto.actor()).thenReturn(actor(null, null));
        when(contexto.ipAddress()).thenReturn("i".repeat(120));
        when(contexto.userAgent()).thenReturn("a".repeat(400));

        service.registrar(AccionAuditoria.PACIENTE_BAJA, "9".repeat(250));

        AuditLog fila = capturarFilaGuardada();
        assertThat(fila.getIpAddress()).hasSize(80);
        assertThat(fila.getUserAgent()).hasSize(255);
        assertThat(fila.getRegistro()).hasSize(200);
    }

    // ------------------------------------------------------------------- consulta

    @Test
    void consultaElRangoDeFechasComoIntervaloInclusivoConLaPaginaPedida() {
        when(bitacora.buscar(any(), any(), isNull(), isNull(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(fila()), PageRequest.of(0, 25), 1));

        AuditoriaPaginaResponse pagina = service.consultar(
            LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), null, null, 0, null);

        ArgumentCaptor<OffsetDateTime> desde = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> hasta = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<Pageable> paginacion = ArgumentCaptor.forClass(Pageable.class);
        verify(bitacora).buscar(desde.capture(), hasta.capture(), isNull(), isNull(), paginacion.capture());

        assertThat(desde.getValue())
            .isEqualTo(LocalDate.of(2026, 3, 1).atStartOfDay(ZONA).toOffsetDateTime());
        // El fin es exclusivo: el dia "hasta" completo entra en el rango.
        assertThat(hasta.getValue())
            .isEqualTo(LocalDate.of(2026, 4, 1).atStartOfDay(ZONA).toOffsetDateTime());
        assertThat(paginacion.getValue().getPageNumber()).isZero();
        assertThat(paginacion.getValue().getPageSize()).isEqualTo(AuditoriaService.TAMANIO_POR_DEFECTO);
        assertThat(pagina.contenido()).hasSize(1);
        assertThat(pagina.totalElementos()).isEqualTo(1);
        assertThat(pagina.pagina()).isZero();
        assertThat(pagina.tamanio()).isEqualTo(AuditoriaService.TAMANIO_POR_DEFECTO);
    }

    @Test
    void sinFiltrosNoAcotaFechasNiUsuario() {
        when(bitacora.buscar(isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
            .thenReturn(Page.empty());

        service.consultar(null, null, "   ", "   ", null, null);

        verify(bitacora).buscar(isNull(), isNull(), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    void normalizaElCodigoDeAccionAntesDeConsultar() {
        when(acciones.findByCode("PACIENTE_CREAR")).thenReturn(Optional.of(catalogo("PACIENTE_CREAR")));
        when(bitacora.buscar(any(), any(), eq("PACIENTE_CREAR"), any(), any(Pageable.class)))
            .thenReturn(Page.empty());

        service.consultar(null, null, "  paciente_crear ", null, null, null);

        verify(bitacora).buscar(any(), any(), eq("PACIENTE_CREAR"), any(), any(Pageable.class));
    }

    @Test
    void unaAccionDesconocidaDevuelve400SinConsultarLaBitacora() {
        when(acciones.findByCode("NO_EXISTE")).thenReturn(Optional.empty());

        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.consultar(null, null, "no_existe", null, null, null),
            ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getMessage()).contains("NO_EXISTE");
        verifyNoInteractions(bitacora);
    }

    @Test
    void unRangoInvertidoDevuelve400SinConsultarLaBitacora() {
        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.consultar(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 3, 1),
                null, null, null, null),
            ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getMessage()).contains("rango de fechas");
        verifyNoInteractions(bitacora);
    }

    @Test
    void elMismoDiaComoDesdeYHastaSigueSiendoUnRangoValido() {
        when(bitacora.buscar(any(), any(), isNull(), isNull(), any(Pageable.class)))
            .thenReturn(Page.empty());

        service.consultar(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 1), null, null, null, null);

        verify(bitacora).buscar(any(), any(), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    void aplicaElTamanioPorDefectoYElTopeDeCienFilasPorPagina() {
        when(bitacora.buscar(any(), any(), any(), any(), any(Pageable.class))).thenReturn(Page.empty());

        service.consultar(null, null, null, null, null, null);
        service.consultar(null, null, null, null, null, 500);
        service.consultar(null, null, null, null, null, 0);

        ArgumentCaptor<Pageable> paginacion = ArgumentCaptor.forClass(Pageable.class);
        verify(bitacora, times(3)).buscar(any(), any(), any(), any(), paginacion.capture());
        assertThat(paginacion.getAllValues())
            .extracting(Pageable::getPageSize)
            .containsExactly(AuditoriaService.TAMANIO_POR_DEFECTO,
                AuditoriaService.TAMANIO_MAXIMO,
                AuditoriaService.TAMANIO_POR_DEFECTO);
    }

    @Test
    void unaPaginaFueraDeRangoDevuelveContenidoVacioConLosTotalesReales() {
        when(bitacora.buscar(any(), any(), any(), any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(9, 25), 30));

        AuditoriaPaginaResponse pagina = service.consultar(null, null, null, null, 9, null);

        assertThat(pagina.contenido()).isEmpty();
        assertThat(pagina.pagina()).isEqualTo(9);
        assertThat(pagina.totalElementos()).isEqualTo(30);
        assertThat(pagina.totalPaginas()).isEqualTo(2);
    }

    @Test
    void unaPaginaNegativaSeTrataComoLaPrimera() {
        when(bitacora.buscar(any(), any(), any(), any(), any(Pageable.class))).thenReturn(Page.empty());

        AuditoriaPaginaResponse pagina = service.consultar(null, null, null, null, -5, null);

        assertThat(pagina.pagina()).isZero();
        verify(bitacora).buscar(any(), any(), any(), any(), any(Pageable.class));
    }
}
