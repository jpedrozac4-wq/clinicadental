/* ============================================================================
   VISTA: INVENTARIO MULTIBODEGA Y KARDEX INMUTABLE
   ----------------------------------------------------------------------------
   Etapa 0: consulta de bodegas y kardex contra /api/inventario. Las solicitudes
   de abastecimiento y de baja se agregan en la tarea del modulo de inventario.
   ============================================================================ */
import { CONFIG, apiQuery } from "../config.js";
import { fetchJson } from "../services/api.js";
import { el, clear, dateTime, errorState, loadingState, sectionHeader, table } from "../core/dom.js";

export class InventarioView {

    constructor() {
        this.bodegas = [];
        this.kardex = [];
    }

    async mount(container) {
        this.container = container;
        this.bodegaGrid = el("div", { class: "grid-3" });
        this.kardexBody = el("div", { class: "table-scroll" });

        container.replaceChildren(
            sectionHeader("Inventario multibodega", "Kardex inmutable"),
            el("div", { class: "card" }, [
                el("div", { class: "card-title-row" }, [
                    el("h3", { text: "Bodegas operativas" }),
                    el("button", {
                        class: "btn btn-outline btn-sm",
                        text: "Actualizar",
                        on: { click: () => this.reload() }
                    })
                ]),
                this.bodegaGrid
            ]),
            el("div", { class: "card" }, [
                el("div", { class: "card-title-row" }, [
                    el("h3", { text: "Kardex de movimientos recientes" }),
                    el("span", { class: "badge badge-primary", text: "Trazabilidad append-only" })
                ]),
                this.kardexBody
            ])
        );

        await this.reload();
        return null;
    }

    async reload() {
        this.bodegaGrid.replaceChildren(loadingState("Consultando bodegas…"));
        this.kardexBody.replaceChildren(loadingState("Consultando kardex…"));

        const [bodegas, kardex] = await Promise.allSettled([
            fetchJson(CONFIG.ENDPOINTS.INVENTARIO_BODEGAS),
            fetchJson(apiQuery(CONFIG.ENDPOINTS.INVENTARIO_KARDEX, { bodegaId: 2 }))
        ]);

        this.renderBodegas(bodegas);
        this.renderKardex(kardex);
    }

    renderBodegas(result) {
        clear(this.bodegaGrid);
        if (result.status === "rejected") {
            this.bodegaGrid.append(errorState(result.reason.message));
            return;
        }
        this.bodegas = result.value || [];
        if (!this.bodegas.length) {
            this.bodegaGrid.append(el("p", { class: "text-muted", text: "No hay bodegas registradas." }));
            return;
        }
        this.bodegas.forEach(bodega => {
            this.bodegaGrid.append(el("div", { class: "card mb-0" }, [
                el("span", { class: bodegaBadgeClass(bodega.tipo), text: bodega.tipo || "—" }),
                el("h4", { class: "mt-1", text: bodega.nombre || "—" }),
                el("p", { class: "text-muted text-sm", text: bodega.ubicacion || "Ubicación estándar" })
            ]));
        });
    }

    renderKardex(result) {
        clear(this.kardexBody);
        if (result.status === "rejected") {
            this.kardexBody.append(errorState(result.reason.message));
            return;
        }
        this.kardex = (result.value || []).slice(0, 20);

        this.kardexBody.append(table(
            ["Fecha", "Bodega", "SKU", "Tipo", "Cantidad", "Saldo", "Documento"],
            this.kardex.map(mov => [
                el("span", { text: dateTime(mov.fecha) }),
                el("span", { text: mov.bodega || "—" }),
                el("span", { class: "cell-strong", text: mov.sku || "—" }),
                el("span", {
                    class: mov.cantidad > 0 ? "badge badge-success" : "badge badge-danger",
                    text: mov.tipoMovimiento || "—"
                }),
                el("span", {
                    class: mov.cantidad > 0 ? "mov-in" : "mov-out",
                    text: `${mov.cantidad > 0 ? "+" : ""}${mov.cantidad}`
                }),
                el("span", { text: String(mov.stockPosterior ?? "—") }),
                el("span", { text: `${mov.documentoTipo || "—"} #${mov.documentoId ?? "—"}` })
            ]),
            { emptyMessage: "Sin movimientos registrados para esta bodega." }
        ));
    }

    unmount() {
        this.container = null;
        this.bodegas = [];
        this.kardex = [];
    }
}

/** Clase de badge segun el tipo de bodega del catalogo. */
function bodegaBadgeClass(tipo) {
    const classes = {
        ACTUAL: "badge badge-success",
        RECEPCION: "badge badge-warning",
        LIQUIDACION: "badge badge-danger"
    };
    return classes[String(tipo || "").toUpperCase()] || "badge badge-primary";
}
