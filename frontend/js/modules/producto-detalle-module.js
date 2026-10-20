/* ============================================================================
   MODULO DE DETALLE DE PRODUCTO (TALLAS, EQUIVALENCIA Y LISTA DE ESPERA)
   ----------------------------------------------------------------------------
   Vista de detalle dentro de la misma pagina: se oculta la grilla del catalogo
   y se muestra esta seccion. No se registra ninguna ruta porque el storefront
   publico no pasa por core/router.js (que exige sesion autenticada).
   ============================================================================ */
import { CONFIG, apiUrl } from "../config.js";
import { fetchJson } from "../services/api.js";
import { el, clear, money, loadingState, errorState } from "../core/dom.js";
import { NotificationService } from "../services/notification-service.js";

/** Muestra u oculta una seccion por id usando la clase utilitaria .is-hidden. */
function setHidden(id, hidden) {
    const node = document.getElementById(id);
    if (node) node.classList.toggle("is-hidden", hidden);
}

export class ProductoDetalleModule {

    constructor({ onAddToCart } = {}) {
        this.container = document.querySelector("#producto-detalle");
        this.onAddToCart = typeof onAddToCart === "function" ? onAddToCart : null;

        this.producto = null;
        this.detalle = null;
        this.tallasCatalogo = [];
        this.seleccion = null;
    }

    /** Abre el detalle de un modelo del catalogo y carga sus SKUs. */
    async open(producto) {
        if (!this.container || !producto) return;

        this.producto = producto;
        this.seleccion = null;
        this.detalle = null;
        this.tallasCatalogo = [];

        this.showDetailView();
        clear(this.container);
        this.container.append(loadingState("Cargando tallas y equivalencias…"));

        try {
            const [detalle, tallas] = await Promise.all([
                fetchJson(apiUrl(CONFIG.ENDPOINTS.CATALOGO, producto.id)),
                fetchJson(CONFIG.ENDPOINTS.CATALOGO_TALLAS).catch(() => [])
            ]);
            this.detalle = detalle;
            this.tallasCatalogo = Array.isArray(tallas) ? tallas : [];
            this.render();
        } catch (error) {
            clear(this.container);
            this.container.append(errorState(error && error.message
                ? error.message
                : "No se pudo cargar el detalle del modelo."));
        }
    }

    /** Vuelve a la grilla del catalogo. */
    close() {
        this.hideDetailView();
        if (this.container) clear(this.container);
        this.producto = null;
        this.detalle = null;
        this.seleccion = null;
    }

    showDetailView() {
        setHidden("catalogo", true);
        setHidden("filtros-catalogo", true);
        setHidden("catalogo-grid", true);
        if (this.container) this.container.classList.remove("is-hidden");
    }

    hideDetailView() {
        setHidden("catalogo", false);
        setHidden("filtros-catalogo", false);
        setHidden("catalogo-grid", false);
        if (this.container) this.container.classList.add("is-hidden");
    }

    currentSkus() {
        return this.detalle && Array.isArray(this.detalle.skus) ? this.detalle.skus : [];
    }

    render() {
        if (!this.container) return;
        const detalle = this.detalle || {};
        const skus = this.currentSkus();

        clear(this.container);

        const backButton = el("button", {
            class: "btn btn-outline btn-sm",
            type: "button",
            text: "← Volver al catálogo",
            on: { click: () => this.close() }
        });

        const header = el("div", { class: "card-header" }, [
            el("h2", { text: detalle.nombre || this.producto.nombre || "Detalle del calzado" }),
            el("div", { class: "card-header-actions" }, [backButton])
        ]);

        const facts = el("div", { class: "detail-facts" }, [
            el("p", { class: "text-muted", text: `Material exterior: ${detalle.materialExterior || "—"}` }),
            el("p", { class: "text-muted", text: `Tipo de suela: ${detalle.tipoSuela || "—"}` }),
            el("p", { class: "detail-price", text: money(detalle.precioEfectivo ?? this.producto.precioEfectivo) }),
            el("p", { class: "text-muted", text: "Precio de referencia del modelo; cada talla muestra su precio vigente." })
        ]);

        const sizes = el("div", {}, [
            el("p", { class: "detail-label", text: "Tallas disponibles y equivalencia en centímetros" }),
            this.buildSizeSelector(skus),
            this.buildSelectionSummary(),
            this.buildActions(skus)
        ]);

        this.container.append(header, el("div", { class: "grid-2" }, [facts, sizes]));
    }

    buildSizeSelector(skus) {
        const byTalla = new Map();
        skus.forEach(sku => byTalla.set(this.tallaKey(sku.tallaEur), sku));

        const rows = this.tallasCatalogo.length
            ? this.tallasCatalogo
            : skus.map(sku => ({ tallaEur: sku.tallaEur, tallaCmMx: sku.tallaCmMx }));

        if (!rows.length) {
            return el("p", { class: "text-muted", text: "Este modelo todavía no tiene tallas ni SKUs publicados." });
        }

        const selector = el("div", { class: "size-selector" });
        rows.forEach(talla => selector.append(this.buildSizePill(talla, byTalla.get(this.tallaKey(talla.tallaEur)))));
        return selector;
    }

    buildSizePill(talla, sku) {
        const disponible = Boolean(sku);
        const precio = disponible ? Number(sku.precio) : null;

        const pill = el("button", {
            class: disponible ? "size-pill" : "size-pill is-unavailable",
            type: "button",
            title: disponible ? `SKU ${sku.sku} · ${money(precio)}` : "Talla sin SKU publicado"
        }, [
            el("span", { class: "size-pill-eur", text: this.formatTalla(talla.tallaEur) }),
            el("span", { class: "size-pill-cm", text: talla.tallaCmMx === null || talla.tallaCmMx === undefined
                ? "—"
                : `${this.formatTalla(talla.tallaCmMx)} cm` }),
            el("span", { class: "size-pill-price", text: disponible ? money(precio) : "Sin SKU" })
        ]);

        if (!disponible) {
            pill.disabled = true;
            return pill;
        }

        if (this.seleccion && this.seleccion.sku === sku.sku) pill.classList.add("selected");
        pill.addEventListener("click", () => this.seleccionar(talla, sku));
        return pill;
    }

    seleccionar(talla, sku) {
        this.seleccion = {
            sku: sku.sku,
            tallaEur: this.formatTalla(talla.tallaEur),
            tallaCmMx: this.formatTalla(talla.tallaCmMx),
            precio: Number(sku.precio),
            nombre: (this.detalle && this.detalle.nombre) || (this.producto && this.producto.nombre) || sku.sku,
            fotoUrl: (this.producto && this.producto.fotoUrl) || null
        };
        this.render();
    }

    buildSelectionSummary() {
        if (!this.seleccion) {
            return el("p", { class: "text-muted", text: "Elegí una talla para ver el precio final y agregarla al carrito." });
        }
        return el("p", {
            class: "detail-selection",
            text: `Seleccionado: talla ${this.seleccion.tallaEur} · ${this.seleccion.tallaCmMx} cm · ${money(this.seleccion.precio)}`
        });
    }

    buildActions(skus) {
        const addButton = el("button", {
            class: "btn btn-success btn-block",
            type: "button",
            text: "Agregar al carrito",
            on: { click: () => this.agregar() }
        });
        addButton.disabled = !this.seleccion;

        const backButton = el("button", {
            class: "btn btn-outline btn-block",
            type: "button",
            text: "Volver al catálogo",
            on: { click: () => this.close() }
        });

        return el("div", { class: "detail-actions" }, [addButton, this.buildAlerta(skus), backButton]);
    }

    buildAlerta(skus) {
        const emailInput = el("input", {
            type: "email",
            class: "form-control",
            placeholder: "tu@correo.com",
            autocomplete: "email"
        });
        const feedback = el("div", { class: "alert-box is-hidden" });

        const button = el("button", {
            class: "btn btn-outline btn-block",
            type: "button",
            text: "Avisame cuando llegue",
            on: { click: () => this.enviarAlerta(emailInput, feedback, skus) }
        });
        button.disabled = !this.resolveAlertSku(skus);

        return el("div", { class: "detail-alert" }, [
            el("p", { class: "detail-label", text: "¿No encontrás tu talla? Dejanos tu correo y te avisamos." }),
            el("div", { class: "detail-alert-form" }, [emailInput, button]),
            feedback
        ]);
    }

    /**
     * El backend exige un codigo SKU impreso. Si hay talla seleccionada se usa
     * ese SKU; si el modelo no publico SKUs, se cae al SKU representativo que
     * ya traia la tarjeta del catalogo.
     */
    resolveAlertSku(skus) {
        if (this.seleccion && this.seleccion.sku) return this.seleccion.sku;

        const sinSkus = !Array.isArray(skus) || skus.length === 0;
        const representativo = this.producto && this.producto.sku;
        if (sinSkus && representativo && representativo !== "SIN-SKU") return representativo;

        return null;
    }

    async enviarAlerta(emailInput, feedback, skus) {
        const sku = this.resolveAlertSku(skus);
        if (!sku) {
            NotificationService.warning("Seleccioná una talla disponible para avisarte.");
            return;
        }

        const email = (emailInput.value || "").trim();
        if (!email) {
            this.showFeedback(feedback, "danger", "Ingresá un correo electrónico para avisarte.");
            return;
        }

        try {
            const data = await fetchJson(CONFIG.ENDPOINTS.CHECKOUT_ALERTA_DEMANDA, {
                method: "POST",
                body: JSON.stringify({ sku, emailCliente: email })
            });
            this.showFeedback(feedback, "success",
                data && data.mensaje ? data.mensaje : "Te avisaremos cuando este SKU vuelva a tener stock.");
        } catch (error) {
            this.showFeedback(feedback, "danger",
                error && error.message ? error.message : "No se pudo registrar la alerta de demanda.");
        }
    }

    showFeedback(node, type, message) {
        if (!node) return;
        node.className = `alert-box ${type}`;
        node.textContent = message;
    }

    agregar() {
        if (!this.seleccion) {
            NotificationService.warning("Elegí una talla disponible antes de agregar.");
            return;
        }
        if (this.onAddToCart) {
            this.onAddToCart({
                sku: this.seleccion.sku,
                nombre: this.seleccion.nombre,
                tallaEur: this.seleccion.tallaEur,
                precio: this.seleccion.precio,
                cantidad: 1,
                fotoUrl: this.seleccion.fotoUrl
            });
        }
    }

    /** Clave estable para comparar tallas: 40 y 40.0 deben ser la misma talla. */
    tallaKey(value) {
        const number = Number(value);
        return Number.isFinite(number) ? number.toFixed(1) : String(value);
    }

    formatTalla(value) {
        const number = Number(value);
        if (!Number.isFinite(number)) return value === null || value === undefined ? "—" : String(value);
        return Number.isInteger(number) ? String(number) : number.toFixed(1);
    }
}
