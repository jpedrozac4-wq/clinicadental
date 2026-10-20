/* ============================================================================
   MODULO DE CATALOGO Y FILTROS FACETADOS (E-COMMERCE)
   ----------------------------------------------------------------------------
   Los filtros ya no se aplican en el cliente: cada cambio reconstruye la query
   contra GET /api/catalogo (tipoId, marcaId, genero, esOutlet) y se vuelve a
   pedir la lista al backend. La tarjeta abre la vista de detalle del modelo.
   ============================================================================ */
import { CONFIG, apiQuery } from "../config.js";
import { fetchJson } from "../services/api.js";
import { el, clear, money, loadingState, emptyState } from "../core/dom.js";

export class CatalogoModule {

    constructor(containerElement, { onSelectProduct } = {}) {
        this.container = containerElement;
        this.onSelectProduct = typeof onSelectProduct === "function" ? onSelectProduct : null;

        this.productos = [];
        this.filtros = { tipoId: "", marcaId: "", genero: "", esOutlet: false };
        this.requestId = 0;

        this.tipoSelect = null;
        this.marcaSelect = null;
        this.generoSelect = null;
        this.outletCheck = null;
        this.limpiarButton = null;
    }

    async init() {
        this.wireFiltros();
        await Promise.all([this.cargarFacetas(), this.cargarProductos()]);
    }

    wireFiltros() {
        this.tipoSelect = document.querySelector("#filtro-tipo");
        this.marcaSelect = document.querySelector("#filtro-marca");
        this.generoSelect = document.querySelector("#filtro-genero");
        this.outletCheck = document.querySelector("#filtro-outlet");
        this.limpiarButton = document.querySelector("#btn-limpiar-filtros");

        if (this.tipoSelect) {
            this.tipoSelect.addEventListener("change", () => this.setFiltros({ tipoId: this.tipoSelect.value }));
        }
        if (this.marcaSelect) {
            this.marcaSelect.addEventListener("change", () => this.setFiltros({ marcaId: this.marcaSelect.value }));
        }
        if (this.generoSelect) {
            this.generoSelect.addEventListener("change", () => this.setFiltros({ genero: this.generoSelect.value }));
        }
        if (this.outletCheck) {
            this.outletCheck.addEventListener("change", () => this.setFiltros({ esOutlet: this.outletCheck.checked }));
        }
        if (this.limpiarButton) {
            this.limpiarButton.addEventListener("click", () => this.limpiarFiltros());
        }
    }

    /** Puebla los selectores con las facetas reales del backend. */
    async cargarFacetas() {
        const [tipos, marcas] = await Promise.all([
            fetchJson(CONFIG.ENDPOINTS.CATALOGO_TIPOS).catch(() => []),
            fetchJson(CONFIG.ENDPOINTS.CATALOGO_MARCAS).catch(() => [])
        ]);
        this.fillSelect(this.tipoSelect, "Todos los tipos", tipos);
        this.fillSelect(this.marcaSelect, "Todas las marcas", marcas);
    }

    fillSelect(select, placeholder, entities) {
        if (!select) return;
        select.replaceChildren();
        select.append(el("option", { value: "", text: placeholder }));
        (Array.isArray(entities) ? entities : []).forEach(entity => {
            select.append(el("option", { value: String(entity.id), text: entity.nombre || String(entity.id) }));
        });
    }

    /** URL exacta de la consulta actual contra el backend. */
    buildUrl() {
        return apiQuery(CONFIG.ENDPOINTS.CATALOGO, {
            tipoId: this.filtros.tipoId,
            marcaId: this.filtros.marcaId,
            genero: this.filtros.genero,
            esOutlet: this.filtros.esOutlet ? "true" : ""
        });
    }

    /** Fusiona los filtros indicados y vuelve a consultar al backend. */
    setFiltros(parciales) {
        this.filtros = { ...this.filtros, ...parciales };
        return this.cargarProductos();
    }

    limpiarFiltros() {
        this.filtros = { tipoId: "", marcaId: "", genero: "", esOutlet: false };
        if (this.tipoSelect) this.tipoSelect.value = "";
        if (this.marcaSelect) this.marcaSelect.value = "";
        if (this.generoSelect) this.generoSelect.value = "";
        if (this.outletCheck) this.outletCheck.checked = false;
        return this.cargarProductos();
    }

    async cargarProductos() {
        this.requestId += 1;
        const currentRequest = this.requestId;

        this.renderLoading();

        let productos = [];
        try {
            productos = await fetchJson(this.buildUrl());
        } catch (err) {
            console.error("No se pudieron cargar los productos del backend:", err);
            productos = [];
        }

        // Una respuesta vieja que llega tarde no debe pisar el filtro vigente.
        if (currentRequest !== this.requestId) return;

        this.productos = Array.isArray(productos) ? productos : [];
        this.renderizar();
    }

    renderLoading() {
        if (!this.container) return;
        clear(this.container);
        this.container.append(loadingState("Cargando calzados…"));
    }

    renderizar() {
        if (!this.container) return;
        clear(this.container);

        if (this.productos.length === 0) {
            this.container.append(emptyState(
                "No se encontraron calzados con estos filtros.",
                "Probá quitar algún filtro o presioná Limpiar."
            ));
            return;
        }

        this.productos.forEach(prod => this.container.append(this.buildCard(prod)));
    }

    buildCard(prod) {
        const card = el("div", {
            class: "product-card",
            on: { click: () => this.selectProduct(prod) }
        });

        const topDiv = el("div");
        const imagePlaceholder = el("div", { class: "product-image-placeholder" }, [this.buildImage(prod)]);

        if (prod.esOutlet) {
            imagePlaceholder.append(el("span", {
                class: "badge badge-danger product-badge-top",
                text: `OUTLET -${prod.descuento || 30}%`
            }));
        }
        topDiv.append(imagePlaceholder);

        topDiv.append(el("span", { class: "product-sku", text: `SKU: ${prod.sku || "SIN-SKU"}` }));
        topDiv.append(el("h3", { class: "product-title", text: prod.nombre }));
        topDiv.append(el("p", {
            class: "product-meta",
            text: `${prod.marca || ""} | ${prod.tipo || ""} | ${prod.genero || ""}`
        }));
        card.append(topDiv);

        const bottomDiv = el("div");
        const priceBox = el("div", { class: "product-pricing" }, [
            el("span", { class: "product-price-current", text: money(prod.precioEfectivo || prod.precioLista) })
        ]);
        if (prod.esOutlet && prod.precioLista) {
            priceBox.append(el("span", { class: "product-price-original", text: money(prod.precioLista) }));
        }
        bottomDiv.append(priceBox);

        bottomDiv.append(el("button", {
            class: "btn btn-primary btn-block",
            type: "button",
            text: "Ver tallas y agregar"
        }));
        card.append(bottomDiv);

        return card;
    }

    selectProduct(prod) {
        if (this.onSelectProduct) this.onSelectProduct(prod);
    }

    /**
     * Construye la imagen del producto. Los SKUs sembrados apuntan a rutas
     * estaticas que no existen en disco, asi que cualquier ruta /assets/ se
     * trata como "sin foto" y, si la imagen falla al cargar, se cae al
     * marcador con el nombre del modelo en lugar de mostrar el icono roto.
     */
    buildImage(producto) {
        const fotoUrl = producto.fotoUrl;
        const esPlaceholder = !fotoUrl || fotoUrl.startsWith("/assets/");

        if (esPlaceholder) {
            return this.buildImageFallback(producto);
        }

        const img = el("img", { src: fotoUrl, alt: producto.nombre || "Calzado", loading: "lazy" });
        img.addEventListener("error", () => {
            img.replaceWith(this.buildImageFallback(producto));
        });
        return img;
    }

    buildImageFallback(producto) {
        return el("div", { class: "product-image-fallback" }, [
            el("span", { text: producto.nombre || "Calzado" }),
            el("span", { text: "Foto en proceso de carga" })
        ]);
    }
}
