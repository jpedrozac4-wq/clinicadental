/* ============================================================================
   VISTA: LOGISTICA - DESPACHOS, PICKING/PACKING Y SLA DE 24 HORAS
   ----------------------------------------------------------------------------
   Etapa 0: tablero de despachos con semaforo de SLA y avance de estados. La
   asignacion de ruta y la validacion OTP por transportista se completan en la
   tarea del modulo de logistica.
   ============================================================================ */
import { CONFIG } from "../config.js";
import { fetchJson } from "../services/api.js";
import { el, clear, dateTime, errorState, loadingState, sectionHeader, table, estadoBadgeClass, humanize } from "../core/dom.js";

export class LogisticaView {

    constructor() {
        this.despachos = [];
    }

    async mount(container) {
        this.container = container;
        this.tableBody = el("div", { class: "table-scroll" });

        container.replaceChildren(
            sectionHeader("Logística y despachos", "SLA de preparación < 24 h"),
            el("div", { class: "card" }, [
                el("div", { class: "card-title-row" }, [
                    el("h3", { text: "Despachos activos" }),
                    el("button", {
                        class: "btn btn-outline btn-sm",
                        text: "Actualizar",
                        on: { click: () => this.load() }
                    })
                ]),
                this.tableBody
            ])
        );

        await this.load();
        return null;
    }

    async load() {
        this.tableBody.replaceChildren(loadingState("Consultando despachos…"));
        try {
            this.despachos = await fetchJson(CONFIG.ENDPOINTS.LOGISTICA_DESPACHOS) || [];
            this.render();
        } catch (error) {
            clear(this.tableBody).append(errorState(error.message));
        }
    }

    render() {
        clear(this.tableBody).append(table(
            ["Orden", "Guía", "Transporte", "SLA de preparación", "Estado", "Entrega"],
            this.despachos.map(despacho => [
                el("span", { class: "cell-strong", text: despacho.numeroOrden || "—" }),
                el("span", { text: despacho.numeroGuia || "—" }),
                el("span", { text: humanize(despacho.tipoTransporte || "—") }),
                this.slaCell(despacho),
                el("span", { class: estadoBadgeClass(despacho.estado), text: humanize(despacho.estado) }),
                el("span", { class: "text-sm", text: despacho.fechaEntrega ? dateTime(despacho.fechaEntrega) : "Pendiente" })
            ]),
            { emptyMessage: "No hay despachos en curso." }
        ));
    }

    slaCell(despacho) {
        const horas = Number(despacho.horasEnBodega ?? 0);
        const vencido = horas > 24;
        return el("span", {
            class: vencido ? "badge badge-danger" : "badge badge-success",
            text: vencido ? `Vencido (${horas.toFixed(1)} h)` : `${horas.toFixed(1)} h transcurridas`
        });
    }

    unmount() {
        this.container = null;
        this.despachos = [];
    }
}
