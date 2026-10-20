package com.empresa.calzado.erp.inventario;

import com.empresa.calzado.erp.catalogo.CalzadoSku;
import com.empresa.calzado.erp.seguridad.SystemUser;
import com.empresa.calzado.erp.ventas.CheckoutBusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Triangulation of the inventory arithmetic shared by the web checkout, the scheduled sweep and the
 * POS counter sale. Repositories are mocked; no H2 or Testcontainers are used.
 *
 * <p>Reserve moves {@code cantidad_disponible -> cantidad_reservada}, consolidation clears the
 * reservation, and the sweep's release moves it back. Pinning the three moves here makes it obvious
 * that releasing a paid order's reservation would put an already-sold unit back on sale.
 */
@ExtendWith(MockitoExtension.class)
class InventarioServiceTest {

    @Mock private InventarioStockRepository stockRepository;
    @Mock private KardexMovimientoRepository kardexRepository;
    @Mock private MovimientoStockTipoRepository movimientoTipoRepository;
    @Mock private BodegaRepository bodegaRepository;

    @InjectMocks private InventarioService service;

    @Test
    void bloquearStockMueveDisponibleAReservada() {
        InventarioStock stock = stockCon(10, 0);
        when(stockRepository.findStockConBloqueoPesimista(1, 10)).thenReturn(Optional.of(stock));

        service.bloquearStockPesimista(1, 10, 3);

        assertThat(stock.getCantidadDisponible()).isEqualTo(7);
        assertThat(stock.getCantidadReservada()).isEqualTo(3);
    }

    @Test
    void bloquearStockRechazaCuandoNoAlcanzaYNoMutaNada() {
        InventarioStock stock = stockCon(2, 0);
        when(stockRepository.findStockConBloqueoPesimista(1, 10)).thenReturn(Optional.of(stock));

        assertThatThrownBy(() -> service.bloquearStockPesimista(1, 10, 5))
            .isInstanceOf(CheckoutBusinessException.class)
            .hasMessage("Stock insuficiente: Disponible=2, Solicitado=5");

        assertThat(stock.getCantidadDisponible()).isEqualTo(2);
        assertThat(stock.getCantidadReservada()).isZero();
    }

    @Test
    void revertirReservaDevuelveLaReservaADisponible() {
        InventarioStock stock = stockCon(7, 3);
        when(stockRepository.findByBodegaIdAndCalzadoSkuId(1, 10)).thenReturn(Optional.of(stock));

        service.revertirReservaStock(1, 10, 3);

        assertThat(stock.getCantidadDisponible()).isEqualTo(10);
        assertThat(stock.getCantidadReservada()).isZero();
    }

    @Test
    void consolidarVentaLimpiaLaReservaSinTocarLaDisponible() {
        InventarioStock stock = stockCon(7, 3);
        when(stockRepository.findByBodegaIdAndCalzadoSkuId(1, 10)).thenReturn(Optional.of(stock));
        MovimientoStockTipo salidaPos = new MovimientoStockTipo();
        salidaPos.setCode("SALIDA_VENTA_POS");
        when(movimientoTipoRepository.findByCode("SALIDA_VENTA_POS")).thenReturn(Optional.of(salidaPos));

        service.consolidarVentaKardex(1, sku(10), 3, 99L, new SystemUser(), "SALIDA_VENTA_POS");

        assertThat(stock.getCantidadDisponible()).isEqualTo(7);
        assertThat(stock.getCantidadReservada()).isZero();
        verify(kardexRepository).save(any(KardexMovimiento.class));
    }

    @Test
    void reingresarStockDevolucionSumaLaCantidadYEscribeElKardex() {
        InventarioStock stock = stockCon(4, 0);
        when(stockRepository.findStockConBloqueoPesimista(2, 10)).thenReturn(Optional.of(stock));
        MovimientoStockTipo reingreso = new MovimientoStockTipo();
        reingreso.setCode("REINGRESO_DEVOLUCION");
        when(movimientoTipoRepository.findByCode("REINGRESO_DEVOLUCION")).thenReturn(Optional.of(reingreso));

        service.reingresarStockDevolucion(2, sku(10), 3, new SystemUser(), 88);

        assertThat(stock.getCantidadDisponible()).isEqualTo(7);
        assertThat(stock.getCantidadReservada()).isZero();

        ArgumentCaptor<KardexMovimiento> kardex = ArgumentCaptor.forClass(KardexMovimiento.class);
        verify(kardexRepository).save(kardex.capture());
        assertThat(kardex.getValue().getMovimientoTipo().getCode()).isEqualTo("REINGRESO_DEVOLUCION");
        assertThat(kardex.getValue().getCantidadMovimiento()).isEqualTo(3);
        assertThat(kardex.getValue().getStockAnterior()).isEqualTo(4);
        assertThat(kardex.getValue().getStockPosterior()).isEqualTo(7);
        assertThat(kardex.getValue().getDocumentoTipo()).isEqualTo("DEVOLUCION");
        assertThat(kardex.getValue().getDocumentoId()).isEqualTo(88L);
    }

    @Test
    void reingresarStockDevolucionCreaLaFilaConCerosCuandoNoExiste() {
        when(stockRepository.findStockConBloqueoPesimista(3, 10)).thenReturn(Optional.empty());
        Bodega bodega = new Bodega();
        bodega.setId(3);
        when(bodegaRepository.findById(3)).thenReturn(Optional.of(bodega));
        MovimientoStockTipo reingreso = new MovimientoStockTipo();
        reingreso.setCode("REINGRESO_DEVOLUCION");
        when(movimientoTipoRepository.findByCode("REINGRESO_DEVOLUCION")).thenReturn(Optional.of(reingreso));

        service.reingresarStockDevolucion(3, sku(10), 2, new SystemUser(), 88);

        ArgumentCaptor<InventarioStock> guardado = ArgumentCaptor.forClass(InventarioStock.class);
        verify(stockRepository).save(guardado.capture());
        assertThat(guardado.getValue().getCantidadDisponible()).isEqualTo(2);
        assertThat(guardado.getValue().getCantidadReservada()).isZero();
        assertThat(guardado.getValue().getBodega().getId()).isEqualTo(3);
        verify(kardexRepository).save(any(KardexMovimiento.class));
    }

    private static InventarioStock stockCon(int disponible, int reservada) {
        Bodega bodega = new Bodega();
        bodega.setId(1);
        InventarioStock stock = new InventarioStock();
        stock.setBodega(bodega);
        stock.setCalzadoSku(sku(10));
        stock.setCantidadDisponible(disponible);
        stock.setCantidadReservada(reservada);
        return stock;
    }

    private static CalzadoSku sku(int id) {
        CalzadoSku sku = new CalzadoSku();
        sku.setId(id);
        return sku;
    }
}
