package com.clinica.dental.pago;

import com.clinica.dental.auditoria.AccionAuditoria;
import com.clinica.dental.auditoria.AuditoriaService;
import com.clinica.dental.auditoria.DetalleAuditoria;
import com.clinica.dental.factura.EstadoFactura;
import com.clinica.dental.factura.EstadoFacturaRepository;
import com.clinica.dental.factura.Factura;
import com.clinica.dental.factura.FacturaRepository;
import com.clinica.dental.paciente.Paciente;
import com.clinica.dental.pago.dto.PagoRequest;
import com.clinica.dental.pago.dto.PagoResponse;
import com.clinica.dental.shared.ClinicaBusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Pagos totales y parciales con saldo y estados de la factura (S10). */
@ExtendWith(MockitoExtension.class)
class PagoServiceTest {

    @Mock private PagoRepository pagos;
    @Mock private MetodoPagoRepository metodos;
    @Mock private FacturaRepository facturas;
    @Mock private EstadoFacturaRepository estados;
    @Mock private AuditoriaService auditoria;

    @InjectMocks private PagoService service;

    private static Paciente paciente() {
        Paciente p = new Paciente();
        p.setId(1);
        p.setNombres("Ana María");
        p.setApellidos("Mendoza Rojas");
        return p;
    }

    private static EstadoFactura estado(String code) {
        EstadoFactura e = new EstadoFactura();
        e.setId(1);
        e.setCode(code);
        e.setDescription(code);
        e.setActive(true);
        return e;
    }

    private static Factura factura(String total, String saldo, String codeEstado) {
        Factura f = new Factura();
        f.setId(50);
        f.setNumero("F-000002");
        f.setPaciente(paciente());
        f.setEstado(estado(codeEstado));
        f.setFechaEmision(OffsetDateTime.now().minusDays(2));
        f.setTotal(new BigDecimal(total));
        f.setSaldo(new BigDecimal(saldo));
        return f;
    }

    private static MetodoPago metodo(int id, String code) {
        MetodoPago m = new MetodoPago();
        m.setId(id);
        m.setCode(code);
        m.setDescription(code);
        m.setActive(true);
        return m;
    }

    private static PagoRequest pedido(String monto) {
        return new PagoRequest(new BigDecimal(monto), 1, null);
    }

    private void catalogoDeEstados() {
        // Cada caso usa solo uno de los dos estados destino, por eso los stubs son tolerantes.
        lenient().when(estados.findByCode(EstadoFactura.PAGO_PARCIAL))
            .thenReturn(Optional.of(estado(EstadoFactura.PAGO_PARCIAL)));
        lenient().when(estados.findByCode(EstadoFactura.PAGADA))
            .thenReturn(Optional.of(estado(EstadoFactura.PAGADA)));
    }

    @Test
    void unPagoParcialDejaLaFacturaEnPagoParcialConElSaldoRecalculado() {
        Factura factura = factura("550.00", "550.00", EstadoFactura.EMITIDA);
        when(facturas.findById(50)).thenReturn(Optional.of(factura));
        when(metodos.findByIdAndActiveTrue(1)).thenReturn(Optional.of(metodo(1, "EFECTIVO")));
        when(pagos.sumarPagos(50)).thenReturn(new BigDecimal("0.00"));
        catalogoDeEstados();
        when(pagos.save(any(Pago.class))).thenAnswer(inv -> {
            Pago p = inv.getArgument(0);
            p.setId(90);
            return p;
        });

        PagoResponse registrado = service.registrar(50, pedido("200.00"));

        assertThat(registrado.id()).isEqualTo(90);
        assertThat(registrado.monto()).isEqualByComparingTo("200.00");
        assertThat(registrado.metodoPago()).isEqualTo("EFECTIVO");
        assertThat(factura.getSaldo()).isEqualByComparingTo("350.00");
        assertThat(factura.getEstado().getCode()).isEqualTo(EstadoFactura.PAGO_PARCIAL);
        verify(facturas).save(factura);
    }

    @Test
    void elPagoQueCompletaElSaldoDejaLaFacturaPagada() {
        Factura factura = factura("550.00", "200.00", EstadoFactura.PAGO_PARCIAL);
        when(facturas.findById(50)).thenReturn(Optional.of(factura));
        when(metodos.findByIdAndActiveTrue(1)).thenReturn(Optional.of(metodo(1, "TARJETA")));
        when(pagos.sumarPagos(50)).thenReturn(new BigDecimal("350.00"));
        catalogoDeEstados();
        when(pagos.save(any(Pago.class))).thenAnswer(inv -> inv.getArgument(0));

        service.registrar(50, pedido("200.00"));

        assertThat(factura.getSaldo()).isEqualByComparingTo("0.00");
        assertThat(factura.getEstado().getCode()).isEqualTo(EstadoFactura.PAGADA);
    }

    @Test
    void unPagoQueSuperaElTotalDevuelve409YNoRegistraNada() {
        Factura factura = factura("550.00", "150.00", EstadoFactura.PAGO_PARCIAL);
        when(facturas.findById(50)).thenReturn(Optional.of(factura));
        when(metodos.findByIdAndActiveTrue(1)).thenReturn(Optional.of(metodo(1, "EFECTIVO")));
        when(pagos.sumarPagos(50)).thenReturn(new BigDecimal("400.00"));

        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.registrar(50, pedido("200.00")), ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getMessage()).contains("no puede superar el total");
        verify(pagos, never()).save(any(Pago.class));
        verify(facturas, never()).save(any(Factura.class));
        assertThat(factura.getSaldo()).isEqualByComparingTo("150.00");
    }

    @Test
    void unPagoQueIgualaElSaldoPendienteSeAcepta() {
        Factura factura = factura("550.00", "150.00", EstadoFactura.PAGO_PARCIAL);
        when(facturas.findById(50)).thenReturn(Optional.of(factura));
        when(metodos.findByIdAndActiveTrue(1)).thenReturn(Optional.of(metodo(1, "EFECTIVO")));
        when(pagos.sumarPagos(50)).thenReturn(new BigDecimal("400.00"));
        catalogoDeEstados();
        when(pagos.save(any(Pago.class))).thenAnswer(inv -> inv.getArgument(0));

        service.registrar(50, pedido("150.00"));

        assertThat(factura.getSaldo()).isEqualByComparingTo("0.00");
        assertThat(factura.getEstado().getCode()).isEqualTo(EstadoFactura.PAGADA);
    }

    @Test
    void unMontoIgualACeroDevuelve400YNoRegistraNada() {
        when(facturas.findById(50)).thenReturn(Optional.of(factura("550.00", "550.00", EstadoFactura.EMITIDA)));

        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.registrar(50, pedido("0.00")), ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getMessage()).contains("mayor que cero");
        verify(pagos, never()).save(any(Pago.class));
    }

    @Test
    void unaFacturaInexistenteDevuelve404() {
        when(facturas.findById(99)).thenReturn(Optional.empty());

        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.registrar(99, pedido("100.00")), ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ex.getMessage()).contains("Factura no encontrada");
        verifyNoInteractions(pagos, metodos);
    }

    @Test
    void unMetodoDePagoInexistenteDevuelve404() {
        when(facturas.findById(50)).thenReturn(Optional.of(factura("550.00", "550.00", EstadoFactura.EMITIDA)));
        when(metodos.findByIdAndActiveTrue(1)).thenReturn(Optional.empty());

        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.registrar(50, pedido("100.00")), ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ex.getMessage()).contains("Método de pago no encontrado");
        verify(pagos, never()).save(any(Pago.class));
    }

    @Test
    void unaFacturaAnuladaNoAdmitePagos() {
        when(facturas.findById(50)).thenReturn(Optional.of(factura("550.00", "550.00", EstadoFactura.ANULADA)));

        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.registrar(50, pedido("100.00")), ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getMessage()).contains("anulada");
        verify(pagos, never()).save(any(Pago.class));
        verify(facturas, never()).save(any(Factura.class));
    }

    @Test
    void laReferenciaOpcionalSeGuardaRecortada() {
        when(facturas.findById(50)).thenReturn(Optional.of(factura("550.00", "550.00", EstadoFactura.EMITIDA)));
        when(metodos.findByIdAndActiveTrue(1)).thenReturn(Optional.of(metodo(1, "TRANSFERENCIA")));
        when(pagos.sumarPagos(50)).thenReturn(BigDecimal.ZERO);
        catalogoDeEstados();
        when(pagos.save(any(Pago.class))).thenAnswer(inv -> inv.getArgument(0));

        service.registrar(50, new PagoRequest(new BigDecimal("100.00"), 1, "  Recibo 1042  "));

        ArgumentCaptor<Pago> captor = ArgumentCaptor.forClass(Pago.class);
        verify(pagos).save(captor.capture());
        assertThat(captor.getValue().getReferencia()).isEqualTo("Recibo 1042");
        assertThat(captor.getValue().getFechaPago()).isNotNull();
    }

    @Test
    void listarDevuelveLosPagosDeLaFactura() {
        Factura factura = factura("550.00", "150.00", EstadoFactura.PAGO_PARCIAL);
        Pago pago = new Pago();
        pago.setId(90);
        pago.setFactura(factura);
        pago.setMonto(new BigDecimal("400.00"));
        pago.setMetodoPago(metodo(1, "EFECTIVO"));
        pago.setFechaPago(OffsetDateTime.now().minusDays(1));

        when(facturas.findById(50)).thenReturn(Optional.of(factura));
        when(pagos.findByFacturaIdOrderByFechaPagoAsc(50)).thenReturn(List.of(pago));

        List<PagoResponse> listado = service.listar(50);

        assertThat(listado).hasSize(1);
        assertThat(listado.get(0).monto()).isEqualByComparingTo("400.00");
        assertThat(listado.get(0).idFactura()).isEqualTo(50);
        assertThat(listado.get(0).metodoPago()).isEqualTo("EFECTIVO");
    }

    @Test
    void listarLosPagosDeUnaFacturaInexistenteDevuelve404() {
        when(facturas.findById(99)).thenReturn(Optional.empty());

        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.listar(99), ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(pagos, never()).findByFacturaIdOrderByFechaPagoAsc(any());
    }

    // -------------------------------------------------- auditoria de operaciones (S19)

    @Test
    void auditaElPagoConElEstadoYElSaldoResultantes() {
        Factura factura = factura("550.00", "550.00", EstadoFactura.EMITIDA);
        when(facturas.findById(50)).thenReturn(Optional.of(factura));
        when(metodos.findByIdAndActiveTrue(1)).thenReturn(Optional.of(metodo(1, "EFECTIVO")));
        when(pagos.sumarPagos(50)).thenReturn(new BigDecimal("0.00"));
        catalogoDeEstados();
        when(pagos.save(any(Pago.class))).thenAnswer(inv -> {
            Pago p = inv.getArgument(0);
            p.setId(90);
            return p;
        });

        service.registrar(50, pedido("200.00"));

        ArgumentCaptor<DetalleAuditoria> detalle = ArgumentCaptor.forClass(DetalleAuditoria.class);
        verify(auditoria).registrar(eq(AccionAuditoria.PAGO_REGISTRAR), eq(90), detalle.capture());
        assertThat(detalle.getValue().valores())
            .containsEntry("idFactura", 50)
            .containsEntry("metodo", "EFECTIVO")
            .containsEntry("estadoFactura", "PAGO_PARCIAL");
        assertThat((BigDecimal) detalle.getValue().valores().get("saldo"))
            .isEqualByComparingTo("350.00");
    }

    @Test
    void unPagoQueSuperaElTotalNoDejaFilaDeAuditoria() {
        Factura factura = factura("550.00", "100.00", EstadoFactura.PAGO_PARCIAL);
        when(facturas.findById(50)).thenReturn(Optional.of(factura));
        when(metodos.findByIdAndActiveTrue(1)).thenReturn(Optional.of(metodo(1, "EFECTIVO")));
        when(pagos.sumarPagos(50)).thenReturn(new BigDecimal("450.00"));

        catchThrowableOfType(() -> service.registrar(50, pedido("200.00")),
            ClinicaBusinessException.class);

        verifyNoInteractions(auditoria);
    }

    @Test
    void unaFacturaAnuladaNoDejaFilaDeAuditoria() {
        Factura factura = factura("550.00", "550.00", EstadoFactura.ANULADA);
        when(facturas.findById(50)).thenReturn(Optional.of(factura));

        catchThrowableOfType(() -> service.registrar(50, pedido("200.00")),
            ClinicaBusinessException.class);

        verifyNoInteractions(auditoria);
    }
}
