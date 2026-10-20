/* ============================================================================
   MODULO DE CARRITO B2C (BADGE DE CABECERA + PANEL LATERAL)
   ----------------------------------------------------------------------------
   Renderiza el contador del header y el panel del carrito. No guarda estado
   propio: todo vive en CarritoService y esta vista se limita a reflejarlo.
   ============================================================================ */
import { el, clear, money } from "../core/dom.js";
import { CarritoService } from "../services/carrito-service.js";
import { NotificationService } from "../services/notification-service.js";

export class CarritoModule {

    constructor({ onContinuar } = {}) {
        this.onContinuar = typeof onContinuar === "function" ? onContinuar : null;

        this.button = document.querySelector("#btn-abrir-carrito");
        this.badge = document.querySelector("#carrito-badge");
        this.panel = document.querySelector("#modal-carrito");
        this.itemsContainer = document.querySelector("#carrito-items");
        this.subtotalNode = document.querySelector("#carrito-subtotal");
        this.btnContinuar = document.querySelector("#btn-continuar-carrito");
        this.btnCerrar = document.querySelector("#btn-close-carrito");
        this.btnVaciar = document.querySelector("#btn-vaciar-carrito");
    }

    init() {
        if (this.button) this.button.addEventListener("click", () => this.open());
        if (this.btnCerrar) this.btnCerrar.addEventListener("click", () => this.close());
        if (this.panel) {
            this.panel.addEventListener("click", event => {
                if (event.target === this.panel) this.close();
            });
        }
        if (this.btnContinuar) this.btnContinuar.addEventListener("click", () => this.continuar());
        if (this.btnVaciar) this.btnVaciar.addEventListener("click", () => this.vaciar());

        CarritoService.subscribe(() => this.render());
        this.render();
    }

    open() {
        if (this.panel) this.panel.classList.add("active");
    }

    close() {
        if (this.panel) this.panel.classList.remove("active");
    }

    async vaciar() {
        if (CarritoService.getCount() === 0) return;
        const confirmado = await NotificationService.confirm("¿Vaciar todo el carrito?");
        if (confirmado) CarritoService.clear();
    }

    /** "Continuar compra" cierra el panel y delega en el checkout existente. */
    continuar() {
        const items = CarritoService.getItems();
        if (!items.length) return;
        this.close();
        if (this.onContinuar) this.onContinuar(items);
    }

    render() {
        const items = CarritoService.getItems();
        const count = CarritoService.getCount();

        if (this.badge) {
            this.badge.textContent = String(count);
            this.badge.classList.toggle("is-hidden", count === 0);
        }
        if (this.subtotalNode) this.subtotalNode.textContent = money(CarritoService.getSubtotal());
        if (this.btnContinuar) this.btnContinuar.disabled = count === 0;

        if (!this.itemsContainer) return;
        clear(this.itemsContainer);

        if (!items.length) {
            this.itemsContainer.append(el("div", { class: "state-box" }, [
                el("p", { class: "state-title", text: "Tu carrito está vacío" }),
                el("p", { class: "text-muted", text: "Explorá el catálogo y agregá tallas para continuar la compra." })
            ]));
            return;
        }

        items.forEach(item => this.itemsContainer.append(this.buildItem(item)));
    }

    buildItem(item) {
        const total = money(Number(item.precio) * item.cantidad);

        return el("div", { class: "cart-item" }, [
            el("div", { class: "cart-item-info" }, [
                el("span", { class: "cart-item-name", text: item.nombre }),
                el("span", { class: "cart-item-meta", text: this.describe(item) }),
                el("span", { class: "cart-item-unit", text: `${money(item.precio)} c/u` })
            ]),
            el("div", { class: "cart-item-controls" }, [
                el("button", {
                    class: "btn btn-sm btn-ghost",
                    type: "button",
                    text: "−",
                    title: "Quitar una unidad",
                    on: { click: () => CarritoService.setCantidad(item.sku, item.cantidad - 1) }
                }),
                el("span", { class: "cart-item-qty", text: String(item.cantidad) }),
                el("button", {
                    class: "btn btn-sm btn-ghost",
                    type: "button",
                    text: "+",
                    title: "Agregar una unidad",
                    on: { click: () => CarritoService.setCantidad(item.sku, item.cantidad + 1) }
                }),
                el("button", {
                    class: "btn btn-sm btn-danger",
                    type: "button",
                    text: "Quitar",
                    on: { click: () => CarritoService.remove(item.sku) }
                })
            ]),
            el("span", { class: "cart-item-total", text: total })
        ]);
    }

    describe(item) {
        const sinTalla = item.tallaEur === null || item.tallaEur === undefined || item.tallaEur === "";
        const talla = sinTalla ? "Talla única" : `Talla ${item.tallaEur}`;
        return `SKU: ${item.sku} · ${talla}`;
    }
}
