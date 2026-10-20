/* ============================================================================
   VISTA: DIRECCION / GERENCIA (CUADRO DE MANDO DE 5 KPIs)
   ----------------------------------------------------------------------------
   Consume /api/gerencia/kpis. Sin valores inventados: si el backend no
   responde, la vista lo informa en lugar de mostrar cifras falsas.
   ============================================================================ */
import { CONFIG } from "../config.js";
import { fetchJson } from "../services/api.js";
import { el, kpiCard, loadingState, errorState, sectionHeader, money } from "../core/dom.js";

export class GerenciaView {

    constructor() {
        this.kpis = null;
        // Bumped on every mount and unmount: a continuation that captured an older
        // value must not touch state or paint on the dead view.
        this.viewGeneration = 0;
    }

    async mount(container) {
        this.viewGeneration += 1;
        this.container = container;
        this.body = el("div", { class: "card" });
        container.replaceChildren(
            sectionHeader("Cuadro de mando ejecutivo", "5 KPIs en vivo"),
            this.body
        );
        await this.load();
        return null;
    }

    async load() {
        if (!this.body) return;

        const generation = this.viewGeneration;
        this.body.replaceChildren(loadingState("Consultando indicadores…"));
        try {
            const kpis = await fetchJson(CONFIG.ENDPOINTS.GERENCIA_KPIS);
            if (generation !== this.viewGeneration) return;
            this.kpis = kpis;
            this.body.replaceChildren(this.buildContent());
        } catch (error) {
            if (generation !== this.viewGeneration) return;
            this.body.replaceChildren(errorState(error.message));
        }
    }

    buildContent() {
        const kpis = this.kpis || {};
        return el("div", {}, [
            el("div", { class: "card-title-row" }, [
                el("h3", { text: "Indicadores del día" }),
                el("button", {
                    class: "btn btn-outline btn-sm",
                    text: "Actualizar",
                    on: { click: () => this.load() }
                })
            ]),
            el("div", { class: "kpi-container" }, [
                kpiCard("1. Ventas del día", money(kpis.ventasDelDiaUsd ?? 0), "Facturación omnicanal acumulada"),
                kpiCard("2. Conversión web", `${Number(kpis.tasaConversionWeb ?? 0).toFixed(2)}%`, "Benchmark sectorial: 2.00%"),
                kpiCard("3. Tallas críticas agotadas", `${kpis.tallasCriticasAgotadas ?? 0} modelos`, "Stock de seguridad protegido"),
                kpiCard("4. Pedidos en bodega", `${kpis.pedidosPendientesBodega ?? 0} pendientes`, "Semáforo de SLA < 24 h"),
                kpiCard("5. Quejas abiertas", `${kpis.ticketsQuejasAbiertos ?? 0} abiertos`, "Escalamiento cíclico cada 6 h")
            ])
        ]);
    }

    unmount() {
        // The generation bump is what makes any request still in flight completely inert.
        this.viewGeneration += 1;
        this.container = null;
        this.body = null;
        this.kpis = null;
    }
}
