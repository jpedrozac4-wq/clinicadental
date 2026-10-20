package com.empresa.calzado.erp.soporte;

import com.empresa.calzado.erp.ventas.Customer;
import com.empresa.calzado.erp.ventas.CustomerRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/soporte")
@CrossOrigin(origins = {"http://localhost", "http://localhost:80", "http://localhost:8080", "http://127.0.0.1"})
public class SoporteController {

    private final TicketSoporteRepository ticketRepository;
    private final CustomerRepository customerRepository;
    private final MotivoQuejaRepository motivoRepository;
    private final TicketEstadoRepository estadoRepository;

    public SoporteController(
        TicketSoporteRepository ticketRepository,
        CustomerRepository customerRepository,
        MotivoQuejaRepository motivoRepository,
        TicketEstadoRepository estadoRepository
    ) {
        this.ticketRepository = ticketRepository;
        this.customerRepository = customerRepository;
        this.motivoRepository = motivoRepository;
        this.estadoRepository = estadoRepository;
    }

    @GetMapping("/tickets")
    public ResponseEntity<List<Map<String, Object>>> listarTickets() {
        List<TicketSoporte> tickets = ticketRepository.findAll();
        List<Map<String, Object>> response = tickets.stream().map(t -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", t.getId());
            m.put("numeroTicket", t.getNumeroTicket());
            m.put("cliente", t.getCustomer().getNombreCompleto());
            m.put("motivo", t.getMotivoQueja().getCode());
            m.put("estado", t.getTicketEstado().getCode());
            m.put("areaDerivada", t.getAreaDerivada());
            m.put("descripcion", t.getDescripcion());
            m.put("fechaCreacion", t.getCreatedAt().toString());
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/tickets")
    public ResponseEntity<Map<String, Object>> crearTicket(@RequestBody CrearTicketRequest req) {
        Customer customer = customerRepository.findByEmail(req.email())
            .orElseGet(() -> {
                Customer c = new Customer();
                c.setNombreCompleto(req.nombreCliente() != null ? req.nombreCliente() : "Cliente");
                c.setEmail(req.email());
                return customerRepository.save(c);
            });

        MotivoQueja motivo = motivoRepository.findByCode(req.motivoCode())
            .orElseGet(() -> motivoRepository.findAll().stream().findFirst().orElseThrow());

        TicketEstado estadoNuevo = estadoRepository.findByCode("NUEVO")
            .orElseGet(() -> estadoRepository.findAll().stream().findFirst().orElseThrow());

        TicketSoporte t = new TicketSoporte();
        t.setNumeroTicket("TCK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        t.setCustomer(customer);
        t.setMotivoQueja(motivo);
        t.setTicketEstado(estadoNuevo);
        t.setAreaDerivada(req.areaDerivada() != null ? req.areaDerivada() : "NINGUNA");
        t.setDescripcion(req.descripcion());

        TicketSoporte guardado = ticketRepository.save(t);

        return ResponseEntity.ok(Map.of(
            "status", "SUCCESS",
            "numeroTicket", guardado.getNumeroTicket(),
            "mensaje", "Ticket de soporte registrado y puesto en cola para atención dentro del SLA de 6 horas."
        ));
    }

    public record CrearTicketRequest(String email, String nombreCliente, String motivoCode, String areaDerivada, String descripcion) {}
}
