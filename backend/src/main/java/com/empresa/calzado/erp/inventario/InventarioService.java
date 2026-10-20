package com.empresa.calzado.erp.inventario;

import com.empresa.calzado.erp.catalogo.CalzadoSku;
import com.empresa.calzado.erp.seguridad.SystemUser;
import com.empresa.calzado.erp.ventas.CheckoutBusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventarioService {

    /** Movement code seeded in {@code cat_movimiento_stock_tipo} for a return's stock re-entry. */
    public static final String MOVIMIENTO_REINGRESO_DEVOLUCION = "REINGRESO_DEVOLUCION";

    private final InventarioStockRepository stockRepository;
    private final KardexMovimientoRepository kardexRepository;
    private final MovimientoStockTipoRepository movimientoTipoRepository;
    private final BodegaRepository bodegaRepository;

    public InventarioService(
        InventarioStockRepository stockRepository,
        KardexMovimientoRepository kardexRepository,
        MovimientoStockTipoRepository movimientoTipoRepository,
        BodegaRepository bodegaRepository
    ) {
        this.stockRepository = stockRepository;
        this.kardexRepository = kardexRepository;
        this.movimientoTipoRepository = movimientoTipoRepository;
        this.bodegaRepository = bodegaRepository;
    }

    @Transactional
    public void bloquearStockPesimista(Integer bodegaId, Integer skuId, Integer cantidad) {
        InventarioStock stock = stockRepository.findStockConBloqueoPesimista(bodegaId, skuId)
            .orElseThrow(() -> new CheckoutBusinessException(HttpStatus.BAD_REQUEST,
                "No hay stock disponible para el SKU solicitado"));

        // Availability is a business rejection, not a server error: both the web checkout and the
        // POS counter sale must surface it through the shared ERROR envelope, and a POS sale that
        // fails here must leave no order, line, kardex or invoice behind.
        if (stock.getCantidadDisponible() < cantidad) {
            throw new CheckoutBusinessException(HttpStatus.BAD_REQUEST,
                "Stock insuficiente: Disponible=" + stock.getCantidadDisponible() + ", Solicitado=" + cantidad);
        }

        stock.setCantidadDisponible(stock.getCantidadDisponible() - cantidad);
        stock.setCantidadReservada(stock.getCantidadReservada() + cantidad);
        stockRepository.save(stock);
    }

    @Transactional
    public void revertirReservaStock(Integer bodegaId, Integer skuId, Integer cantidad) {
        InventarioStock stock = stockRepository.findByBodegaIdAndCalzadoSkuId(bodegaId, skuId)
            .orElseThrow(() -> new IllegalStateException("No existe inventario"));

        stock.setCantidadReservada(Math.max(0, stock.getCantidadReservada() - cantidad));
        stock.setCantidadDisponible(stock.getCantidadDisponible() + cantidad);
        stockRepository.save(stock);
    }

    /**
     * Clears the reservation that a sale consumes and records the physical stock exit in the
     * kardex.
     *
     * <p>The method has one single meaning: it consolidates a reservation that already exists, which
     * is why every caller must reserve first with {@link #bloquearStockPesimista}. The movement code
     * is data (SALIDA_VENTA_WEB vs SALIDA_VENTA_POS), not a boolean mode flag that makes the method
     * behave differently. The physical exit is already reflected in {@code cantidad_disponible} by
     * the reserve step, so this step only clears {@code cantidad_reservada}.
     */
    @Transactional
    public void consolidarVentaKardex(Integer bodegaId, CalzadoSku sku, Integer cantidad, Long ordenId, SystemUser usuario, String codigoMovimiento) {
        InventarioStock stock = stockRepository.findByBodegaIdAndCalzadoSkuId(bodegaId, sku.getId())
            .orElseThrow(() -> new IllegalStateException("No existe inventario"));

        // Clear the reservation taken by bloquearStockPesimista. cantidad_disponible is untouched
        // here: the reserve step already discounted it, so the sale is not deducted twice.
        stock.setCantidadReservada(Math.max(0, stock.getCantidadReservada() - cantidad));
        stockRepository.save(stock);

        MovimientoStockTipo movTipo = movimientoTipoRepository.findByCode(codigoMovimiento)
            .orElseThrow(() -> new IllegalStateException("Tipo de movimiento " + codigoMovimiento + " no configurado"));

        KardexMovimiento kardex = new KardexMovimiento();
        kardex.setBodega(stock.getBodega());
        kardex.setCalzadoSku(sku);
        kardex.setMovimientoTipo(movTipo);
        kardex.setCantidadMovimiento(-cantidad);
        kardex.setStockAnterior(stock.getCantidadDisponible() + cantidad);
        kardex.setStockPosterior(stock.getCantidadDisponible());
        kardex.setDocumentoTipo("ORDEN_VENTA");
        kardex.setDocumentoId(ordenId);
        kardex.setUser(usuario);
        kardexRepository.save(kardex);
    }

    /**
     * Re-enters returned stock into a warehouse and records the entry in the kardex.
     *
     * <p>Stock is arriving, so unlike a reservation the absence of the {@code (bodega, sku)} row is
     * not an error: the row is created with zeroed counters before the quantity is added. The caller
     * decides the destination warehouse from the line's classification ({@code APTO_PRODUCTO_ACTUAL}
     * goes to the {@code ACTUAL} warehouse, {@code DANADO_OUTLET} to {@code LIQUIDACION}); this
     * primitive only moves the stock and writes the movement, so the controller never touches
     * {@code inventario_stock} or the kardex directly. The document id is the return that produced
     * the entry.
     */
    @Transactional
    public void reingresarStockDevolucion(Integer bodegaId, CalzadoSku sku, int cantidad, SystemUser usuario, Integer devolucionId) {
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad a reingresar debe ser mayor a cero");
        }
        if (devolucionId == null) {
            throw new IllegalArgumentException("El id de la devolucion es obligatorio para el kardex");
        }

        InventarioStock stock = stockRepository.findStockConBloqueoPesimista(bodegaId, sku.getId())
            .orElseGet(() -> {
                Bodega bodega = bodegaRepository.findById(bodegaId)
                    .orElseThrow(() -> new IllegalStateException("Bodega no encontrada: " + bodegaId));
                InventarioStock nuevo = new InventarioStock();
                nuevo.setBodega(bodega);
                nuevo.setCalzadoSku(sku);
                nuevo.setCantidadDisponible(0);
                nuevo.setCantidadReservada(0);
                return nuevo;
            });

        int stockAnterior = stock.getCantidadDisponible();
        int stockPosterior = stockAnterior + cantidad;
        stock.setCantidadDisponible(stockPosterior);
        stockRepository.save(stock);

        MovimientoStockTipo movTipo = movimientoTipoRepository.findByCode(MOVIMIENTO_REINGRESO_DEVOLUCION)
            .orElseThrow(() -> new IllegalStateException(
                "Tipo de movimiento " + MOVIMIENTO_REINGRESO_DEVOLUCION + " no configurado"));

        KardexMovimiento kardex = new KardexMovimiento();
        kardex.setBodega(stock.getBodega());
        kardex.setCalzadoSku(sku);
        kardex.setMovimientoTipo(movTipo);
        kardex.setCantidadMovimiento(cantidad);
        kardex.setStockAnterior(stockAnterior);
        kardex.setStockPosterior(stockPosterior);
        kardex.setDocumentoTipo("DEVOLUCION");
        kardex.setDocumentoId(devolucionId.longValue());
        kardex.setUser(usuario);
        kardexRepository.save(kardex);
    }
}
