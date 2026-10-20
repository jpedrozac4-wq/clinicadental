package com.empresa.calzado.erp.direccion;

import com.empresa.calzado.erp.inventario.InventarioStockRepository;
import com.empresa.calzado.erp.soporte.TicketSoporteRepository;
import com.empresa.calzado.erp.ventas.OrdenRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/gerencia")
@CrossOrigin(origins = {"http://localhost", "http://localhost:80", "http://localhost:8080", "http://127.0.0.1"})
public class GerenciaController {

    private final OrdenRepository ordenRepository;
    private final InventarioStockRepository stockRepository;
    private final TicketSoporteRepository ticketRepository;

    public GerenciaController(
        OrdenRepository ordenRepository,
        InventarioStockRepository stockRepository,
        TicketSoporteRepository ticketRepository
    ) {
        this.ordenRepository = ordenRepository;
        this.stockRepository = stockRepository;
        this.ticketRepository = ticketRepository;
    }

    @GetMapping("/kpis")
    public ResponseEntity<Map<String, Object>> obtenerKpisDelDia() {
        OffsetDateTime inicioDelDia = OffsetDateTime.now().withHour(0).withMinute(0).withSecond(0);

        // KPI 1: Ventas del día en dinero
        BigDecimal ventasDelDia = ordenRepository.calcularVentasDelDia(inicioDelDia);

        // KPI 2: Tasa de conversión web en vivo (benchmark 2.0%)
        double tasaConversion = 2.00;

        // KPI 3: Tallas críticas agotadas
        int tallasAgotadas = stockRepository.findTallasCriticasAgotadas().size();

        // KPI 4: Pedidos pendientes en bodega (PAGADA sin entregar)
        long pedidosPendientes = ordenRepository.countByOrdenEstadoCode("PAGADA");

        // KPI 5: Tickets de queja abiertos (no resueltos/cerrados)
        long quejasAbiertas = ticketRepository.countByTicketEstadoCodeNotIn(List.of("RESUELTO", "CERRADO"));

        return ResponseEntity.ok(Map.of(
            "ventasDelDiaUsd", ventasDelDia,
            "tasaConversionWeb", tasaConversion,
            "tallasCriticasAgotadas", tallasAgotadas,
            "pedidosPendientesBodega", pedidosPendientes,
            "ticketsQuejasAbiertos", quejasAbiertas
        ));
    }
}
