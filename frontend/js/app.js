/* ============================================================================
   APLICACION PRINCIPAL E-COMMERCE B2C (TIENDA DIGITAL)
   ============================================================================ */
import { CatalogoModule } from "./modules/catalogo-module.js";
import { CheckoutModule, ConsultaOrdenModule } from "./modules/checkout-module.js";
import { TrackingModule } from "./modules/tracking-module.js";
import { CarritoModule } from "./modules/carrito-module.js";
import { ProductoDetalleModule } from "./modules/producto-detalle-module.js";
import { CarritoService } from "./services/carrito-service.js";
import { NotificationService } from "./services/notification-service.js";

document.addEventListener("DOMContentLoaded", () => {
    // 1. Inicializar Módulo de Checkout real (cotización, cupón, orden y pago)
    const checkoutModule = new CheckoutModule();

    // 2. Inicializar Carrito (badge de cabecera + panel)
    const carrito = new CarritoModule({
        // "Continuar compra" abre el checkout con las líneas reales del carrito.
        onContinuar: () => checkoutModule.open()
    });
    carrito.init();

    // 3. Inicializar Vista de Detalle de Producto
    const detalle = new ProductoDetalleModule({
        onAddToCart: (item) => {
            CarritoService.add(item);
            NotificationService.success(`Agregado al carrito: ${item.nombre} (talla ${item.tallaEur}).`);
        }
    });

    // 4. Inicializar Módulo de Catálogo con filtros facetados reales
    const catalogoContainer = document.querySelector("#catalogo-grid");
    const catalogo = new CatalogoModule(catalogoContainer, {
        onSelectProduct: (producto) => detalle.open(producto)
    });
    catalogo.init();

    // 5. Inicializar Módulo de Tracking
    const trackingModule = new TrackingModule();

    // 6. Inicializar consulta pública de una orden (correo + número de orden)
    const consultaOrden = new ConsultaOrdenModule();
    consultaOrden.init();

    // Cierre del modal de checkout
    const btnCerrarCheckout = document.querySelector("#btn-close-checkout");
    if (btnCerrarCheckout) {
        btnCerrarCheckout.addEventListener("click", () => checkoutModule.close());
    }

    const modalCheckout = document.querySelector("#modal-checkout");
    if (modalCheckout) {
        modalCheckout.addEventListener("click", (event) => {
            if (event.target === modalCheckout) checkoutModule.close();
        });
    }

    // Listeners de Tracking
    const btnTracking = document.querySelector("#btn-consultar-tracking");
    if (btnTracking) {
        btnTracking.addEventListener("click", () => trackingModule.consultar());
    }

    const btnValidarOtp = document.querySelector("#btn-validar-otp");
    if (btnValidarOtp) {
        btnValidarOtp.addEventListener("click", () => trackingModule.validarOtp());
    }
});
