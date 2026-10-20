package com.empresa.calzado.erp.pos;

import com.empresa.calzado.erp.inventario.Bodega;
import com.empresa.calzado.erp.pos.dto.SesionCajaCierreResponse;
import com.empresa.calzado.erp.pos.dto.SesionCajaResumen;
import com.empresa.calzado.erp.seguridad.SystemUser;
import com.empresa.calzado.erp.seguridad.SystemUserRepository;
import com.empresa.calzado.erp.ventas.CheckoutBusinessException;
import com.empresa.calzado.erp.ventas.OrdenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the cash-session lifecycle: opening rules, the real cash count (arqueo) and the
 * audit transition.
 *
 * <p>Repositories are mocked because the project has no H2 and no Testcontainers. The attribution
 * tests therefore pin the exact query arguments the service sends to
 * {@link OrdenRepository#sumTotalVentasAtribuidas} and
 * {@link OrdenRepository#sumVentasEfectivoAtribuidas}; the SQL predicate itself can only be proven
 * against a live database.
 */
@ExtendWith(MockitoExtension.class)
class PosSesionServiceTest {

    @Mock private CajaPosRepository cajaPosRepository;
    @Mock private SesionCajaRepository sesionCajaRepository;
    @Mock private SystemUserRepository userRepository;
    @Mock private OrdenRepository ordenRepository;

    private PosSesionService service() {
        return new PosSesionService(cajaPosRepository, sesionCajaRepository, userRepository, ordenRepository);
    }

    // ---------------------------------------------------------------- apertura

    @Test
    void abrirSesionUsaElCajeroAutenticadoYGuardaLaSesionAbierta() {
        SystemUser cajero = usuario(7, "cajero2");
        CajaPos caja = caja(3, true, 11, "Tienda Centro", 21, "Bodega Centro");
        when(userRepository.findByUsername("cajero2")).thenReturn(Optional.of(cajero));
        when(cajaPosRepository.findById(3)).thenReturn(Optional.of(caja));
        when(sesionCajaRepository.existsByCajaPosIdAndEstado(3, "ABIERTA")).thenReturn(false);
        when(sesionCajaRepository.existsByCajeroIdAndEstado(7, "ABIERTA")).thenReturn(false);
        when(sesionCajaRepository.save(any(SesionCaja.class))).thenAnswer(invocation -> {
            SesionCaja sesion = invocation.getArgument(0);
            sesion.setId(100);
            return sesion;
        });

        SesionCajaResumen resumen = service().abrirSesion("cajero2", 3, new BigDecimal("120.00"));

        assertThat(resumen.id()).isEqualTo(100);
        assertThat(resumen.estado()).isEqualTo("ABIERTA");
        assertThat(resumen.cajero()).isEqualTo("cajero2");
        assertThat(resumen.montoApertura()).isEqualByComparingTo("120.00");
        assertThat(resumen.cajaId()).isEqualTo(3);
        assertThat(resumen.sucursalNombre()).isEqualTo("Tienda Centro");
        assertThat(resumen.bodegaId()).isEqualTo(21);
        assertThat(resumen.bodegaNombre()).isEqualTo("Bodega Centro");

        ArgumentCaptor<SesionCaja> guardada = ArgumentCaptor.forClass(SesionCaja.class);
        verify(sesionCajaRepository).save(guardada.capture());
        assertThat(guardada.getValue().getCajero().getUsername()).isEqualTo("cajero2");
        assertThat(guardada.getValue().getEstado()).isEqualTo("ABIERTA");
        assertThat(guardada.getValue().getTotalVentasSistema()).isEqualByComparingTo("0.00");
    }

    @Test
    void abrirSesionRechazaUnaSegundaSesionEnLaMismaCaja() {
        when(userRepository.findByUsername("cajero1")).thenReturn(Optional.of(usuario(1, "cajero1")));
        when(cajaPosRepository.findById(3)).thenReturn(Optional.of(caja(3, true, 11, "Tienda", 21, "Bodega")));
        when(sesionCajaRepository.existsByCajaPosIdAndEstado(3, "ABIERTA")).thenReturn(true);

        assertThatThrownBy(() -> service().abrirSesion("cajero1", 3, new BigDecimal("10.00")))
            .isInstanceOf(CheckoutBusinessException.class)
            .hasMessage("La caja ya tiene una sesion abierta");

        verify(sesionCajaRepository, never()).save(any(SesionCaja.class));
    }

    @Test
    void abrirSesionRechazaUnaSegundaSesionDelMismoCajeroEnOtraCaja() {
        when(userRepository.findByUsername("cajero1")).thenReturn(Optional.of(usuario(1, "cajero1")));
        when(cajaPosRepository.findById(4)).thenReturn(Optional.of(caja(4, true, 11, "Tienda", 21, "Bodega")));
        when(sesionCajaRepository.existsByCajaPosIdAndEstado(4, "ABIERTA")).thenReturn(false);
        when(sesionCajaRepository.existsByCajeroIdAndEstado(1, "ABIERTA")).thenReturn(true);

        assertThatThrownBy(() -> service().abrirSesion("cajero1", 4, new BigDecimal("10.00")))
            .isInstanceOf(CheckoutBusinessException.class)
            .hasMessage("El cajero ya tiene una sesion abierta");

        verify(sesionCajaRepository, never()).save(any(SesionCaja.class));
    }

    @Test
    void abrirSesionRechazaUnMontoDeAperturaAusente() {
        when(userRepository.findByUsername("cajero1")).thenReturn(Optional.of(usuario(1, "cajero1")));
        when(cajaPosRepository.findById(3)).thenReturn(Optional.of(caja(3, true, 11, "Tienda", 21, "Bodega")));

        assertThatThrownBy(() -> service().abrirSesion("cajero1", 3, null))
            .isInstanceOf(CheckoutBusinessException.class)
            .hasMessage("El monto de apertura es obligatorio");

        verify(sesionCajaRepository, never()).save(any(SesionCaja.class));
    }

    @Test
    void abrirSesionRechazaUnMontoDeAperturaNegativo() {
        when(userRepository.findByUsername("cajero1")).thenReturn(Optional.of(usuario(1, "cajero1")));
        when(cajaPosRepository.findById(3)).thenReturn(Optional.of(caja(3, true, 11, "Tienda", 21, "Bodega")));

        assertThatThrownBy(() -> service().abrirSesion("cajero1", 3, new BigDecimal("-1.00")))
            .isInstanceOf(CheckoutBusinessException.class)
            .hasMessage("El monto de apertura no puede ser negativo");

        verify(sesionCajaRepository, never()).save(any(SesionCaja.class));
    }

    @Test
    void abrirSesionRechazaUnaCajaInexistente() {
        when(userRepository.findByUsername("cajero1")).thenReturn(Optional.of(usuario(1, "cajero1")));
        when(cajaPosRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().abrirSesion("cajero1", 99, new BigDecimal("10.00")))
            .isInstanceOf(CheckoutBusinessException.class)
            .hasMessage("La caja no existe o no esta activa");

        verify(sesionCajaRepository, never()).save(any(SesionCaja.class));
    }

    @Test
    void abrirSesionRechazaUnaCajaInactiva() {
        when(userRepository.findByUsername("cajero1")).thenReturn(Optional.of(usuario(1, "cajero1")));
        when(cajaPosRepository.findById(3)).thenReturn(Optional.of(caja(3, false, 11, "Tienda", 21, "Bodega")));

        assertThatThrownBy(() -> service().abrirSesion("cajero1", 3, new BigDecimal("10.00")))
            .isInstanceOf(CheckoutBusinessException.class)
            .hasMessage("La caja no existe o no esta activa");

        verify(sesionCajaRepository, never()).save(any(SesionCaja.class));
    }

    // ------------------------------------------------------------------ cierre

    @Test
    void cerrarSesionConCuadreExactoDejaDiferenciaCero() {
        OffsetDateTime apertura = OffsetDateTime.now().minusHours(3);
        SesionCaja sesion = sesionAbierta(50, 7, "cajero2", new BigDecimal("100.00"), apertura);
        stubCierre(sesion, "50.00", "50.00");

        SesionCajaCierreResponse r = service().cerrarSesion(50, new BigDecimal("150.00"), autenticacion("cajero2"));

        assertThat(r.totalVentasSistema()).isEqualByComparingTo("50.00");
        assertThat(r.ventasEnEfectivo()).isEqualByComparingTo("50.00");
        assertThat(r.efectivoEsperado()).isEqualByComparingTo("150.00");
        assertThat(r.diferenciaArqueo()).isEqualByComparingTo("0.00");
        assertThat(r.estado()).isEqualTo("CERRADA");
        assertThat(r.fechaCierre()).isNotNull();
        assertThat(sesion.getEstado()).isEqualTo("CERRADA");
        assertThat(sesion.getMontoCierreEfectivo()).isEqualByComparingTo("150.00");
        assertThat(sesion.getDiferenciaArqueo()).isEqualByComparingTo("0.00");
    }

    @Test
    void cerrarSesionConSobranteDejaDiferenciaPositiva() {
        OffsetDateTime apertura = OffsetDateTime.now().minusHours(3);
        SesionCaja sesion = sesionAbierta(50, 7, "cajero2", new BigDecimal("100.00"), apertura);
        stubCierre(sesion, "50.00", "50.00");

        SesionCajaCierreResponse r = service().cerrarSesion(50, new BigDecimal("160.00"), autenticacion("cajero2"));

        assertThat(r.efectivoEsperado()).isEqualByComparingTo("150.00");
        assertThat(r.diferenciaArqueo()).isEqualByComparingTo("10.00");
        assertThat(sesion.getDiferenciaArqueo()).isEqualByComparingTo("10.00");
    }

    @Test
    void cerrarSesionConFaltanteDejaDiferenciaNegativa() {
        OffsetDateTime apertura = OffsetDateTime.now().minusHours(3);
        SesionCaja sesion = sesionAbierta(50, 7, "cajero2", new BigDecimal("100.00"), apertura);
        stubCierre(sesion, "50.00", "50.00");

        SesionCajaCierreResponse r = service().cerrarSesion(50, new BigDecimal("140.00"), autenticacion("cajero2"));

        assertThat(r.efectivoEsperado()).isEqualByComparingTo("150.00");
        assertThat(r.diferenciaArqueo()).isEqualByComparingTo("-10.00");
        assertThat(sesion.getDiferenciaArqueo()).isEqualByComparingTo("-10.00");
    }

    @Test
    void cerrarSesionSoloTarjetaNoSumaEfectivoYEsperaElMontoDeApertura() {
        OffsetDateTime apertura = OffsetDateTime.now().minusHours(3);
        SesionCaja sesion = sesionAbierta(50, 7, "cajero2", new BigDecimal("100.00"), apertura);
        stubCierre(sesion, "80.00", "0.00");

        SesionCajaCierreResponse r = service().cerrarSesion(50, new BigDecimal("100.00"), autenticacion("cajero2"));

        // Every sale counts towards totalVentasSistema, but a card-only turn adds nothing to the
        // drawer, so the expected cash is exactly the opening float.
        assertThat(r.totalVentasSistema()).isEqualByComparingTo("80.00");
        assertThat(r.ventasEnEfectivo()).isEqualByComparingTo("0.00");
        assertThat(r.efectivoEsperado()).isEqualByComparingTo("100.00");
        assertThat(r.diferenciaArqueo()).isEqualByComparingTo("0.00");
    }

    @Test
    void cerrarSesionSinVentasEsperaElMontoDeApertura() {
        OffsetDateTime apertura = OffsetDateTime.now().minusHours(3);
        SesionCaja sesion = sesionAbierta(50, 7, "cajero2", new BigDecimal("100.00"), apertura);
        stubCierre(sesion, "0.00", "0.00");

        SesionCajaCierreResponse r = service().cerrarSesion(50, new BigDecimal("100.00"), autenticacion("cajero2"));

        assertThat(r.totalVentasSistema()).isEqualByComparingTo("0.00");
        assertThat(r.ventasEnEfectivo()).isEqualByComparingTo("0.00");
        assertThat(r.efectivoEsperado()).isEqualByComparingTo("100.00");
        assertThat(r.diferenciaArqueo()).isEqualByComparingTo("0.00");
    }

    @Test
    void cerrarSesionRechazaUnaSesionYaCerrada() {
        SesionCaja sesion = sesionAbierta(50, 7, "cajero2", new BigDecimal("100.00"), OffsetDateTime.now());
        sesion.setEstado("CERRADA");
        when(sesionCajaRepository.findById(50)).thenReturn(Optional.of(sesion));

        assertThatThrownBy(() -> service().cerrarSesion(50, new BigDecimal("100.00"), autenticacion("cajero2")))
            .isInstanceOf(CheckoutBusinessException.class)
            .hasMessage("La sesion de caja no esta abierta");

        verify(sesionCajaRepository, never()).save(any(SesionCaja.class));
    }

    @Test
    void cerrarSesionRechazaUnMontoDeCierreNegativo() {
        SesionCaja sesion = sesionAbierta(50, 7, "cajero2", new BigDecimal("100.00"), OffsetDateTime.now());
        when(sesionCajaRepository.findById(50)).thenReturn(Optional.of(sesion));

        assertThatThrownBy(() -> service().cerrarSesion(50, new BigDecimal("-1.00"), autenticacion("cajero2")))
            .isInstanceOf(CheckoutBusinessException.class)
            .hasMessage("El monto de cierre no puede ser negativo");

        verify(sesionCajaRepository, never()).save(any(SesionCaja.class));
    }

    @Test
    void cerrarSesionRechazaAUnCajeroQueNoEsElDuenoYNoTocaLaFila() {
        OffsetDateTime apertura = OffsetDateTime.now().minusHours(2);
        SesionCaja sesion = sesionAbierta(50, 7, "cajero2", new BigDecimal("100.00"), apertura);
        String estadoAntes = sesion.getEstado();
        OffsetDateTime fechaCierreAntes = sesion.getFechaCierre();
        BigDecimal montoCierreAntes = sesion.getMontoCierreEfectivo();
        BigDecimal diferenciaAntes = sesion.getDiferenciaArqueo();
        BigDecimal totalVentasAntes = sesion.getTotalVentasSistema();
        when(sesionCajaRepository.findById(50)).thenReturn(Optional.of(sesion));

        assertThatThrownBy(() -> service().cerrarSesion(50, new BigDecimal("150.00"),
                autenticacion("cajero1", "OP_POS_CAJA_APERTURA_CIERRE")))
            .isInstanceOf(CheckoutBusinessException.class)
            .hasMessage("No tiene permiso para cerrar la sesion de caja de otro cajero");

        assertThat(sesion.getEstado()).isEqualTo(estadoAntes);
        assertThat(sesion.getFechaCierre()).isEqualTo(fechaCierreAntes);
        assertThat(sesion.getMontoCierreEfectivo()).isEqualTo(montoCierreAntes);
        assertThat(sesion.getDiferenciaArqueo()).isEqualTo(diferenciaAntes);
        assertThat(sesion.getTotalVentasSistema()).isEqualTo(totalVentasAntes);
        verify(sesionCajaRepository, never()).save(any(SesionCaja.class));
        verify(ordenRepository, never()).sumTotalVentasAtribuidas(any(), any(), any(), any());
    }

    @Test
    void unSupervisorConPermisoDeArqueoPuedeCerrarLaSesionDeOtroCajero() {
        OffsetDateTime apertura = OffsetDateTime.now().minusHours(2);
        SesionCaja sesion = sesionAbierta(50, 7, "cajero2", new BigDecimal("100.00"), apertura);
        stubCierre(sesion, "50.00", "50.00");

        SesionCajaCierreResponse r = service().cerrarSesion(50, new BigDecimal("150.00"),
            autenticacion("supervisor", "OP_POS_ARQUEO_AUDITAR"));

        assertThat(r.estado()).isEqualTo("CERRADA");
        assertThat(sesion.getEstado()).isEqualTo("CERRADA");
    }

    // ------------------------------------------------------------------ auditoria

    @Test
    void auditarSesionPasaUnaSesionCerradaAAuditada() {
        SesionCaja sesion = sesionAbierta(50, 7, "cajero2", new BigDecimal("100.00"), OffsetDateTime.now());
        sesion.setEstado("CERRADA");
        when(sesionCajaRepository.findById(50)).thenReturn(Optional.of(sesion));
        when(sesionCajaRepository.save(any(SesionCaja.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SesionCajaResumen r = service().auditarSesion(50);

        assertThat(r.estado()).isEqualTo("AUDITADA");
        assertThat(sesion.getEstado()).isEqualTo("AUDITADA");
    }

    @Test
    void auditarSesionRechazaUnaSesionAbierta() {
        SesionCaja sesion = sesionAbierta(50, 7, "cajero2", new BigDecimal("100.00"), OffsetDateTime.now());
        when(sesionCajaRepository.findById(50)).thenReturn(Optional.of(sesion));

        assertThatThrownBy(() -> service().auditarSesion(50))
            .isInstanceOf(CheckoutBusinessException.class)
            .hasMessage("La sesion de caja no esta cerrada");

        verify(sesionCajaRepository, never()).save(any(SesionCaja.class));
    }

    @Test
    void auditarSesionRechazaUnaSesionYaAuditada() {
        SesionCaja sesion = sesionAbierta(50, 7, "cajero2", new BigDecimal("100.00"), OffsetDateTime.now());
        sesion.setEstado("AUDITADA");
        when(sesionCajaRepository.findById(50)).thenReturn(Optional.of(sesion));

        assertThatThrownBy(() -> service().auditarSesion(50))
            .isInstanceOf(CheckoutBusinessException.class)
            .hasMessage("La sesion de caja no esta cerrada");

        verify(sesionCajaRepository, never()).save(any(SesionCaja.class));
    }

    // ------------------------------------------------------------------ atribucion

    @Test
    void laAtribucionUsaLaVentanaDelTurnoYElCanalPos() {
        OffsetDateTime apertura = OffsetDateTime.now().minusHours(2);
        SesionCaja sesion = sesionAbierta(50, 7, "cajero2", new BigDecimal("100.00"), apertura);
        stubCierre(sesion, "50.00", "50.00");

        service().cerrarSesion(50, new BigDecimal("150.00"), autenticacion("cajero2"));

        ArgumentCaptor<String> canal = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<OffsetDateTime> desde = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> hasta = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(ordenRepository).sumTotalVentasAtribuidas(canal.capture(), any(), desde.capture(), hasta.capture());

        // A POS order outside fecha_apertura..fecha_cierre is excluded by the window predicate.
        assertThat(canal.getValue()).isEqualTo("FISICO_TIENDA_POS");
        assertThat(desde.getValue()).isEqualTo(apertura);
        assertThat(hasta.getValue()).isAfterOrEqualTo(apertura);
    }

    @Test
    void laAtribucionDeVentasUsaElCajeroDeLaSesion() {
        OffsetDateTime apertura = OffsetDateTime.now().minusHours(2);
        SesionCaja sesion = sesionAbierta(50, 7, "cajero2", new BigDecimal("100.00"), apertura);
        stubCierre(sesion, "50.00", "50.00");

        service().cerrarSesion(50, new BigDecimal("150.00"), autenticacion("cajero2"));

        ArgumentCaptor<Integer> cajeroId = ArgumentCaptor.forClass(Integer.class);
        verify(ordenRepository).sumTotalVentasAtribuidas(any(), cajeroId.capture(), any(), any());

        // An order from a different cashier is excluded by the id_user_cajero predicate.
        assertThat(cajeroId.getValue()).isEqualTo(7);
    }

    @Test
    void laAtribucionDeEfectivoFiltraPorLosCodigosDeMetodoDeEfectivo() {
        OffsetDateTime apertura = OffsetDateTime.now().minusHours(2);
        SesionCaja sesion = sesionAbierta(50, 7, "cajero2", new BigDecimal("100.00"), apertura);
        stubCierre(sesion, "50.00", "50.00");

        service().cerrarSesion(50, new BigDecimal("150.00"), autenticacion("cajero2"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> codigos = ArgumentCaptor.forClass(Collection.class);
        verify(ordenRepository).sumVentasEfectivoAtribuidas(any(), any(), any(), any(), codigos.capture());

        // A card or wallet order is excluded because its method code is not in the cash set.
        assertThat(codigos.getValue()).containsExactlyInAnyOrder("EFECTIVO", "EFECTIVO_CONTRA_ENTREGA");
    }

    // ------------------------------------------------------------------ helpers

    private void stubCierre(SesionCaja sesion, String totalVentas, String ventasEfectivo) {
        when(sesionCajaRepository.findById(sesion.getId())).thenReturn(Optional.of(sesion));
        when(ordenRepository.sumTotalVentasAtribuidas(any(), any(), any(), any()))
            .thenReturn(new BigDecimal(totalVentas));
        when(ordenRepository.sumVentasEfectivoAtribuidas(any(), any(), any(), any(), any()))
            .thenReturn(new BigDecimal(ventasEfectivo));
        when(sesionCajaRepository.save(any(SesionCaja.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static SystemUser usuario(Integer id, String username) {
        SystemUser user = new SystemUser();
        user.setId(id);
        user.setUsername(username);
        return user;
    }

    private static Authentication autenticacion(String username, String... authorities) {
        return new UsernamePasswordAuthenticationToken(username, null,
            Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
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

    private static SesionCaja sesionAbierta(
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
