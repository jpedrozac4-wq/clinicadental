package com.empresa.calzado.erp.ventas;

import com.empresa.calzado.erp.facturacion.Factura;
import com.empresa.calzado.erp.ventas.dto.CotizacionEnvio;
import com.empresa.calzado.erp.ventas.dto.LineaCheckout;
import com.empresa.calzado.erp.ventas.dto.OrdenConsulta;
import com.empresa.calzado.erp.ventas.dto.SucursalRetiro;
import com.empresa.calzado.erp.ventas.dto.ValidacionCupon;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/checkout")
@CrossOrigin(origins = {"http://localhost", "http://localhost:80", "http://localhost:8080", "http://127.0.0.1"})
public class CheckoutController {

    private final CheckoutService checkoutService;

    public CheckoutController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    @PostMapping("/iniciar")
    public ResponseEntity<Map<String, Object>> iniciarCheckout(@RequestBody IniciarCheckoutRequest request) {
        // Precedence: a present `lineas` list wins; when it is absent the single sku/cantidad pair is
        // used as a one-line order. The controller forwards whichever shape the caller sent, and the
        // service owns the validation of an explicitly empty list.
        Orden orden = request.lineas() == null
            ? checkoutService.crearOrdenConReserva2Horas(
                request.email(),
                request.nombre(),
                request.sku(),
                request.cantidad(),
                request.codigoCupon(),
                request.codigoPostal(),
                request.tipoEntrega(),
                request.idSucursalRetiro()
            )
            : checkoutService.crearOrdenConReserva2Horas(
                request.email(),
                request.nombre(),
                request.sku(),
                request.cantidad(),
                request.codigoCupon(),
                request.codigoPostal(),
                request.tipoEntrega(),
                request.idSucursalRetiro(),
                request.lineas()
            );

        return ResponseEntity.ok(Map.of(
            "status", "RESERVA_ACTIVA",
            "numeroOrden", orden.getNumeroOrden(),
            "subtotal", orden.getSubtotalProductos(),
            "costoEnvio", orden.getCostoEnvio(),
            "descuentoAplicado", orden.getDescuentoAplicado(),
            "comisionPasarela", orden.getComisionPasarela(),
            "totalAPagar", orden.getTotalAPagar(),
            "expiraEn", orden.getFechaExpiracionTtl2h().toString(),
            "mensaje", "Stock reservado por 2 horas durante el proceso de pago"
        ));
    }

    @GetMapping("/sucursales")
    public ResponseEntity<List<SucursalRetiro>> listarSucursalesParaRetiro() {
        return ResponseEntity.ok(checkoutService.listarSucursalesParaRetiro());
    }

    @PostMapping("/pagar")
    public ResponseEntity<Map<String, Object>> procesarPago(@RequestBody PagarOrdenRequest request) {
        Factura factura = checkoutService.procesarPagoSimuladoYGenerarOtp(
            request.numeroOrden(),
            request.metodoPago() != null ? request.metodoPago() : "TARJETA_SIMULADA"
        );

        return ResponseEntity.ok(Map.of(
            "status", "PAGADA",
            "facturaCorrelativo", factura.getNumeroCorrelativo(),
            "nitCliente", factura.getNitCliente(),
            "totalPagado", factura.getInvoiceTotal(),
            "pdfUrl", factura.getPdfUrl(),
            "mensaje", "Pago aprobado exitosamente. Orden enviada a preparación con código secreto OTP generado."
        ));
    }

    @PostMapping("/cotizar")
    public ResponseEntity<CotizacionEnvio> cotizarEnvio(@RequestBody CotizarEnvioRequest request) {
        if (request.codigoPostal() == null || request.codigoPostal().isBlank()) {
            throw new CheckoutBusinessException(HttpStatus.BAD_REQUEST, "El parametro codigoPostal es obligatorio");
        }
        if (request.subtotalProductos() == null || request.subtotalProductos().compareTo(BigDecimal.ZERO) < 0) {
            throw new CheckoutBusinessException(HttpStatus.BAD_REQUEST, "El parametro subtotalProductos es obligatorio y no puede ser negativo");
        }
        return ResponseEntity.ok(checkoutService.cotizarEnvio(request.codigoPostal(), request.subtotalProductos()));
    }

    @PostMapping("/cupon/validar")
    public ResponseEntity<ValidacionCupon> validarCupon(@RequestBody ValidarCuponRequest request) {
        if (request.codigoCupon() == null || request.codigoCupon().isBlank()) {
            throw new CheckoutBusinessException(HttpStatus.BAD_REQUEST, "El parametro codigoCupon es obligatorio");
        }
        return ResponseEntity.ok(checkoutService.validarCupon(request.codigoCupon(), request.subtotalProductos()));
    }

    @ExceptionHandler(CheckoutBusinessException.class)
    public ResponseEntity<Map<String, Object>> manejarErrorDeNegocio(CheckoutBusinessException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ERROR");
        body.put("message", ex.getMessage());
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    @PostMapping("/alerta-demanda")
    public ResponseEntity<Map<String, Object>> registrarAlertaDemanda(@RequestBody AlertaDemandaRequest request) {
        if (request.sku() == null || request.sku().isBlank()) {
            throw new CheckoutBusinessException(HttpStatus.BAD_REQUEST, "El parametro sku es obligatorio");
        }
        if (request.emailCliente() == null || request.emailCliente().isBlank()) {
            throw new CheckoutBusinessException(HttpStatus.BAD_REQUEST, "El parametro emailCliente es obligatorio");
        }
        checkoutService.registrarAlertaDemanda(request.sku(), request.emailCliente());
        // Same body whether the alert was newly captured or already existed, so a repeated capture
        // is indistinguishable from the first one.
        return ResponseEntity.ok(Map.of(
            "status", "REGISTRADO",
            "mensaje", "Te avisaremos cuando este SKU vuelva a tener stock"
        ));
    }

    /**
     * Public order consultation. Both {@code email} and {@code numeroOrden} are mandatory and must
     * belong to the same order: this is a deliberate privacy decision, not a "list my orders"
     * endpoint, because {@code /api/checkout/**} is {@code permitAll}.
     */
    @GetMapping("/ordenes")
    public ResponseEntity<OrdenConsulta> consultarOrden(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String numeroOrden) {
        if (email == null || email.isBlank() || numeroOrden == null || numeroOrden.isBlank()) {
            throw new CheckoutBusinessException(
                HttpStatus.BAD_REQUEST, CheckoutService.PARAMETROS_CONSULTA_ORDEN_REQUERIDOS);
        }
        return ResponseEntity.ok(checkoutService.consultarOrden(email, numeroOrden));
    }

    public record IniciarCheckoutRequest(String email, String nombre, String sku, int cantidad, String codigoCupon, String codigoPostal, String tipoEntrega, Integer idSucursalRetiro, List<LineaCheckout> lineas) {}    public record PagarOrdenRequest(String numeroOrden, String metodoPago) {}
    public record CotizarEnvioRequest(String codigoPostal, BigDecimal subtotalProductos) {}
    public record ValidarCuponRequest(String codigoCupon, BigDecimal subtotalProductos) {}
    public record AlertaDemandaRequest(String sku, String emailCliente) {}
}
