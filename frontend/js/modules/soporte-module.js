/* ============================================================================
   VISTA: CONTROL DE GESTION - HELP DESK Y RECLAMACIONES
   ----------------------------------------------------------------------------
   Etapa 0: radicacion y listado de tickets. Los cinco estados operativos, la
   derivacion interdepartamental y el escalamiento cada 6 horas se completan en
   la tarea del modulo de soporte.
   ============================================================================ */
import { CONFIG } from "../config.js";
import { fetchJson } from "../services/api.js";
import { NotificationService } from "../services/notification-service.js";
import { el, qs, clear, errorState, loadingState, sectionHeader, table, estadoBadgeClass, humanize } from "../core/dom.js";

export class SoporteView {

    constructor() {
        this.tickets = [];
    }

    async mount(container) {
        this.container = container;
        this.tableBody = el("div", { class: "table-scroll" });

        container.replaceChildren(
            sectionHeader("Help desk y reclamaciones", "Escalamiento cada 6 h"),
            this.buildForm(),
            el("div", { class: "card" }, [
                el("div", { class: "card-title-row" }, [
                    el("h3", { text: "Tickets en atención" }),
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

    buildForm() {
        return el("div", { class: "card" }, [
            el("div", { class: "card-title-row" }, [
                el("h3", { text: "Radicar nueva reclamación" }),
                el("span", { class: "badge badge-danger", text: "Tipificación obligatoria" })
            ]),
            el("div", { class: "grid-3" }, [
                el("div", { class: "form-group" }, [
                    el("label", { for: "ticket-email", text: "Email del cliente" }),
                    el("input", { type: "email", id: "ticket-email", class: "form-control", value: "carlos.mendoza@email.com" })
                ]),
                el("div", { class: "form-group" }, [
                    el("label", { for: "ticket-nombre", text: "Nombre del cliente" }),
                    el("input", { type: "text", id: "ticket-nombre", class: "form-control", value: "Carlos Mendoza" })
                ]),
                el("div", { class: "form-group" }, [
                    el("label", { for: "ticket-motivo", text: "Motivo tipificado" }),
                    el("select", { id: "ticket-motivo", class: "form-control" }, [
                        el("option", { value: "DISCREPANCIA_VISUAL_WEB", text: "Discrepancia visual web (color/diseño)" }),
                        el("option", { value: "RETRASO_ENTREGA", text: "Retraso en entrega" }),
                        el("option", { value: "TALLA_MODELO_INCORRECTO", text: "Talla o modelo incorrecto" }),
                        el("option", { value: "DEFECTO_FABRICACION", text: "Defecto de fabricación" }),
                        el("option", { value: "PROBLEMA_COBRO", text: "Problema en el cobro" }),
                        el("option", { value: "PRODUCTO_NO_DISPONIBLE", text: "Producto no disponible tras la compra" })
                    ])
                ])
            ]),
            el("div", { class: "grid-2" }, [
                el("div", { class: "form-group" }, [
                    el("label", { for: "ticket-area", text: "Área derivada" }),
                    el("select", { id: "ticket-area", class: "form-control" }, [
                        el("option", { value: "BODEGA", text: "Bodega" }),
                        el("option", { value: "TRANSPORTE", text: "Transporte" }),
                        el("option", { value: "VENTAS", text: "Ventas" }),
                        el("option", { value: "FINANZAS", text: "Finanzas" })
                    ])
                ]),
                el("div", { class: "form-group" }, [
                    el("label", { for: "ticket-desc", text: "Descripción del reclamo" }),
                    el("input", {
                        type: "text", id: "ticket-desc", class: "form-control",
                        value: "El tono del calzado no coincide con la foto publicada en la tienda online."
                    })
                ])
            ]),
            el("button", {
                class: "btn btn-primary",
                id: "btn-crear-ticket",
                text: "Radicar ticket",
                on: { click: () => this.create() }
            })
        ]);
    }

    async load() {
        this.tableBody.replaceChildren(loadingState("Consultando tickets…"));
        try {
            this.tickets = await fetchJson(CONFIG.ENDPOINTS.SOPORTE_TICKETS) || [];
            this.render();
        } catch (error) {
            clear(this.tableBody).append(errorState(error.message));
        }
    }

    render() {
        clear(this.tableBody).append(table(
            ["Ticket", "Cliente", "Motivo", "Área derivada", "Estado", "Descripción"],
            this.tickets.map(ticket => [
                el("span", { class: "cell-strong", text: ticket.numeroTicket || "—" }),
                el("span", { text: ticket.cliente || "—" }),
                el("span", { text: humanize(ticket.motivo) }),
                el("span", { class: "badge badge-warning", text: ticket.areaDerivada || "GENERAL" }),
                el("span", { class: estadoBadgeClass(ticket.estado), text: humanize(ticket.estado) }),
                el("span", { class: "text-sm", text: ticket.descripcion || "—" })
            ]),
            { emptyMessage: "No hay tickets registrados." }
        ));
    }

    async create() {
        const button = qs("#btn-crear-ticket");
        if (button) button.disabled = true;

        try {
            const data = await fetchJson(CONFIG.ENDPOINTS.SOPORTE_TICKETS, {
                method: "POST",
                body: {
                    email: qs("#ticket-email")?.value,
                    nombreCliente: qs("#ticket-nombre")?.value,
                    motivoCode: qs("#ticket-motivo")?.value,
                    areaDerivada: qs("#ticket-area")?.value,
                    descripcion: qs("#ticket-desc")?.value
                }
            });
            NotificationService.success(`Ticket creado: ${data.numeroTicket || "—"}.`);
            await this.load();
        } catch (error) {
            NotificationService.error(`No se pudo radicar el ticket: ${error.message}`);
        } finally {
            if (button) button.disabled = false;
        }
    }

    unmount() {
        this.container = null;
        this.tickets = [];
    }
}
