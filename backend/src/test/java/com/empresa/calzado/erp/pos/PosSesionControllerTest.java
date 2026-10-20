package com.empresa.calzado.erp.pos;

import com.empresa.calzado.erp.inventario.Bodega;
import com.empresa.calzado.erp.seguridad.SystemUser;
import com.empresa.calzado.erp.seguridad.SystemUserRepository;
import com.empresa.calzado.erp.ventas.OrdenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-surface tests for the POS cash-session lifecycle.
 *
 * <p>The controller runs over a real {@link PosSesionService} with mocked repositories, so the
 * wiring, the authenticated-cashier contract and the error envelope are exercised while the SQL
 * stays out of scope (no H2, no Testcontainers).
 */
@ExtendWith(MockitoExtension.class)
class PosSesionControllerTest {

    @Mock private CajaPosRepository cajaPosRepository;
    @Mock private SesionCajaRepository sesionCajaRepository;
    @Mock private SystemUserRepository userRepository;
    @Mock private OrdenRepository ordenRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        PosSesionService service =
            new PosSesionService(cajaPosRepository, sesionCajaRepository, userRepository, ordenRepository);
        mockMvc = MockMvcBuilders.standaloneSetup(new PosSesionController(service)).build();
    }

    @Test
    void lasCajasActivasIncluyenSuSesionAbierta() throws Exception {
        when(cajaPosRepository.findByActiveTrueOrderByIdAsc())
            .thenReturn(List.of(caja(3, true, 11, "Tienda Centro", 21, "Bodega Centro")));
        SesionCaja abierta = new SesionCaja();
        abierta.setId(77);
        when(sesionCajaRepository.findFirstByCajaPosIdAndEstado(3, "ABIERTA")).thenReturn(Optional.of(abierta));

        mockMvc.perform(get("/api/pos/cajas"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(3))
            .andExpect(jsonPath("$[0].numeroCaja").value("CAJA-01"))
            .andExpect(jsonPath("$[0].nombreTerminal").value("Terminal Mostrador"))
            .andExpect(jsonPath("$[0].sucursalId").value(11))
            .andExpect(jsonPath("$[0].sucursalNombre").value("Tienda Centro"))
            .andExpect(jsonPath("$[0].bodegaId").value(21))
            .andExpect(jsonPath("$[0].bodegaNombre").value("Bodega Centro"))
            .andExpect(jsonPath("$[0].sesionAbiertaId").value(77));
    }

    @Test
    void abrirSesionTomaElCajeroDelUsuarioAutenticado() throws Exception {
        when(userRepository.findByUsername("cajero2")).thenReturn(Optional.of(usuario(7, "cajero2")));
        when(cajaPosRepository.findById(3))
            .thenReturn(Optional.of(caja(3, true, 11, "Tienda Centro", 21, "Bodega Centro")));
        when(sesionCajaRepository.existsByCajaPosIdAndEstado(3, "ABIERTA")).thenReturn(false);
        when(sesionCajaRepository.existsByCajeroIdAndEstado(7, "ABIERTA")).thenReturn(false);
        when(sesionCajaRepository.save(any(SesionCaja.class))).thenAnswer(invocation -> {
            SesionCaja sesion = invocation.getArgument(0);
            sesion.setId(100);
            return sesion;
        });

        mockMvc.perform(post("/api/pos/sesiones")
                .principal(autenticacion("cajero2"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idCajaPos\":3,\"montoApertura\":120.00}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(100))
            .andExpect(jsonPath("$.estado").value("ABIERTA"))
            .andExpect(jsonPath("$.cajero").value("cajero2"))
            .andExpect(jsonPath("$.cajaId").value(3))
            .andExpect(jsonPath("$.sucursalNombre").value("Tienda Centro"))
            .andExpect(jsonPath("$.bodegaId").value(21));
    }

    @Test
    void abrirSesionRechazadaDevuelveElEnvelopeDeErrorYNoGuardaNada() throws Exception {
        when(userRepository.findByUsername("cajero1")).thenReturn(Optional.of(usuario(1, "cajero1")));
        when(cajaPosRepository.findById(99)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/pos/sesiones")
                .principal(autenticacion("cajero1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idCajaPos\":99,\"montoApertura\":10.00}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("ERROR"))
            .andExpect(jsonPath("$.message").value("La caja no existe o no esta activa"));

        verify(sesionCajaRepository, never()).save(any(SesionCaja.class));
    }

    @Test
    void laSesionActualSinTurnoAbiertoDevuelve200ConAbiertaFalse() throws Exception {
        when(userRepository.findByUsername("cajero1")).thenReturn(Optional.of(usuario(1, "cajero1")));
        when(sesionCajaRepository.findFirstByCajeroIdAndEstado(1, "ABIERTA")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/pos/sesiones/actual").principal(autenticacion("cajero1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.abierta").value(false));
    }

    @Test
    void laSesionActualConTurnoAbiertoDevuelveLaSesion() throws Exception {
        when(userRepository.findByUsername("cajero1")).thenReturn(Optional.of(usuario(1, "cajero1")));
        when(sesionCajaRepository.findFirstByCajeroIdAndEstado(1, "ABIERTA"))
            .thenReturn(Optional.of(sesion(50, 1, "cajero1", new BigDecimal("100.00"), OffsetDateTime.now().minusHours(1))));

        mockMvc.perform(get("/api/pos/sesiones/actual").principal(autenticacion("cajero1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.abierta").value(true))
            .andExpect(jsonPath("$.sesion.id").value(50))
            .andExpect(jsonPath("$.sesion.estado").value("ABIERTA"))
            .andExpect(jsonPath("$.sesion.cajero").value("cajero1"));
    }

    @Test
    void listarSesionesFiltraPorEstado() throws Exception {
        SesionCaja cerrada = sesion(50, 7, "cajero2", new BigDecimal("100.00"), OffsetDateTime.now().minusHours(3));
        cerrada.setEstado("CERRADA");
        when(sesionCajaRepository.findByEstadoOrderByFechaAperturaDesc("CERRADA")).thenReturn(List.of(cerrada));

        mockMvc.perform(get("/api/pos/sesiones").param("estado", "CERRADA"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(50))
            .andExpect(jsonPath("$[0].estado").value("CERRADA"))
            .andExpect(jsonPath("$[0].cajero").value("cajero2"));
    }

    @Test
    void cerrarSesionDevuelveLaAritmeticaDelArqueo() throws Exception {
        OffsetDateTime apertura = OffsetDateTime.now().minusHours(2);
        SesionCaja sesion = sesion(50, 7, "cajero2", new BigDecimal("100.00"), apertura);
        when(sesionCajaRepository.findById(50)).thenReturn(Optional.of(sesion));
        when(ordenRepository.sumTotalVentasAtribuidas(any(), any(), any(), any()))
            .thenReturn(new BigDecimal("50.00"));
        when(ordenRepository.sumVentasEfectivoAtribuidas(any(), any(), any(), any(), any()))
            .thenReturn(new BigDecimal("50.00"));
        when(sesionCajaRepository.save(any(SesionCaja.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/pos/sesiones/50/cerrar")
                .principal(autenticacion("cajero2"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"montoCierreEfectivo\":150.00}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("CERRADA"))
            .andExpect(content().string(containsString("\"totalVentasSistema\":50.00")))
            .andExpect(content().string(containsString("\"ventasEnEfectivo\":50.00")))
            .andExpect(content().string(containsString("\"efectivoEsperado\":150.00")))
            .andExpect(content().string(containsString("\"diferenciaArqueo\":0.00")));
    }

    @Test
    void cerrarSesionDeOtroCajeroEsRechazadaCon403YNoCambiaLaFila() throws Exception {
        OffsetDateTime apertura = OffsetDateTime.now().minusHours(2);
        SesionCaja sesion = sesion(50, 7, "cajero2", new BigDecimal("100.00"), apertura);
        String estadoAntes = sesion.getEstado();
        OffsetDateTime fechaCierreAntes = sesion.getFechaCierre();
        BigDecimal montoCierreAntes = sesion.getMontoCierreEfectivo();
        BigDecimal diferenciaAntes = sesion.getDiferenciaArqueo();
        BigDecimal totalVentasAntes = sesion.getTotalVentasSistema();
        when(sesionCajaRepository.findById(50)).thenReturn(Optional.of(sesion));

        mockMvc.perform(post("/api/pos/sesiones/50/cerrar")
                .principal(autenticacion("cajero1", "OP_POS_CAJA_APERTURA_CIERRE"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"montoCierreEfectivo\":150.00}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value("ERROR"))
            .andExpect(jsonPath("$.message").value("No tiene permiso para cerrar la sesion de caja de otro cajero"));

        verify(sesionCajaRepository, never()).save(any(SesionCaja.class));
        verify(ordenRepository, never()).sumTotalVentasAtribuidas(any(), any(), any(), any());
        assertThat(sesion.getEstado()).isEqualTo(estadoAntes);
        assertThat(sesion.getFechaCierre()).isEqualTo(fechaCierreAntes);
        assertThat(sesion.getMontoCierreEfectivo()).isEqualTo(montoCierreAntes);
        assertThat(sesion.getDiferenciaArqueo()).isEqualTo(diferenciaAntes);
        assertThat(sesion.getTotalVentasSistema()).isEqualTo(totalVentasAntes);
    }

    @Test
    void unSupervisorConPermisoDeArqueoPuedeCerrarLaSesionDeOtroCajero() throws Exception {
        OffsetDateTime apertura = OffsetDateTime.now().minusHours(2);
        SesionCaja sesion = sesion(50, 7, "cajero2", new BigDecimal("100.00"), apertura);
        when(sesionCajaRepository.findById(50)).thenReturn(Optional.of(sesion));
        when(ordenRepository.sumTotalVentasAtribuidas(any(), any(), any(), any()))
            .thenReturn(new BigDecimal("50.00"));
        when(ordenRepository.sumVentasEfectivoAtribuidas(any(), any(), any(), any(), any()))
            .thenReturn(new BigDecimal("50.00"));
        when(sesionCajaRepository.save(any(SesionCaja.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/pos/sesiones/50/cerrar")
                .principal(autenticacion("supervisor", "OP_POS_ARQUEO_AUDITAR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"montoCierreEfectivo\":150.00}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("CERRADA"));

        assertThat(sesion.getEstado()).isEqualTo("CERRADA");
    }

    @Test
    void auditarSesionDevuelveLaSesionAuditada() throws Exception {
        SesionCaja sesion = sesion(50, 7, "cajero2", new BigDecimal("100.00"), OffsetDateTime.now().minusHours(3));
        sesion.setEstado("CERRADA");
        when(sesionCajaRepository.findById(50)).thenReturn(Optional.of(sesion));
        when(sesionCajaRepository.save(any(SesionCaja.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(patch("/api/pos/sesiones/50/auditar"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(50))
            .andExpect(jsonPath("$.estado").value("AUDITADA"));
    }

    private static Authentication autenticacion(String username, String... authorities) {
        return new UsernamePasswordAuthenticationToken(username, null,
            Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
    }

    private static SystemUser usuario(Integer id, String username) {
        SystemUser user = new SystemUser();
        user.setId(id);
        user.setUsername(username);
        return user;
    }

    private static CajaPos caja(
        Integer id, boolean active, Integer sucursalId, String sucursalNombre,
        Integer bodegaId, String bodegaNombre
    ) {
        Bodega bodega = new Bodega();
        bodega.setId(bodegaId);
        bodega.setNombre(bodegaNombre);
        Sucursal sucursal = new Sucursal();
        sucursal.setId(sucursalId);
        sucursal.setNombre(sucursalNombre);
        sucursal.setBodega(bodega);
        CajaPos caja = new CajaPos();
        caja.setId(id);
        caja.setNumeroCaja("CAJA-01");
        caja.setNombreTerminal("Terminal Mostrador");
        caja.setActive(active);
        caja.setSucursal(sucursal);
        return caja;
    }

    private static SesionCaja sesion(
        Integer id, Integer cajeroId, String username, BigDecimal montoApertura, OffsetDateTime fechaApertura
    ) {
        SesionCaja sesion = new SesionCaja();
        sesion.setId(id);
        sesion.setCajero(usuario(cajeroId, username));
        sesion.setCajaPos(caja(3, true, 11, "Tienda Centro", 21, "Bodega Centro"));
        sesion.setMontoApertura(montoApertura);
        sesion.setEstado("ABIERTA");
        sesion.setFechaApertura(fechaApertura);
        return sesion;
    }
}
