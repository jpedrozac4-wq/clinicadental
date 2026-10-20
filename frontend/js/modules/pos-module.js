/* ============================================================================
   VISTA: PUNTO DE VENTA (POS DE TIENDA FISICA) Y DEVOLUCIONES OMNICANAL
   ----------------------------------------------------------------------------
   Etapa 0: terminal operativo contra /api/pos. La sesion de caja, el ticket PDF
   y el arqueo se completan en la tarea del modulo POS.
   ============================================================================ */
import { CONFIG } from "../config.js";
import { fetchJson } from "../services/api.js";
import { NotificationService } from "../services/notification-service.js";
import { el, qs, money, sectionHeader, table } from "../core/dom.js";

export class PosView {

    constructor() {
        this.items = [];
    }

    async mount(container) {
        this.container = container;
        container.replaceChildren(
            sectionHeader("Terminal punto de venta", "Cobro y devoluciones"),
            el("div", { class: "pos-layout" }, [this.buildSalePanel(), this.buildTicketPanel()]),
            this.buildReturnPanel()
        );

        this.renderTicket();
        return null;
    }

    buildSalePanel() {
        return el("div", { class: "card pos-cart-panel" }, [
            el("div", { class: "card-title-row" }, [
                el("h3", { text: "Venta en caja" }),
                el("span", { class: "badge badge-warning", text: "Sin sesión de caja" })
            ]),
            el("div", { class: "toolbar" }, [
                el("div", { class: "form-group toolbar-grow" }, [
                    el("label", { for: "pos-scan-sku", text: "SKU o código de barras" }),
                    el("input", {
                        type: "text", id: "pos-scan-sku", class: "form-control",
                        placeholder: "Ej: TV001-40", value: "TV001-40"
                    })
                ]),
                el("button", {
                    class: "btn btn-primary",
                    text: "Agregar ítem",
                    on: { click: () => this.addItem() }
                })
            ]),
            el("div", { class: "table-scroll" }, [
                table(["Ítem / SKU", "Cantidad", "Precio", "Subtotal", ""],
                    this.items.map(item => this.itemRow(item)),
                    { emptyMessage: "Sin ítems en el ticket." })
            ]),
            el("div", { class: "grid-2 mt-2" }, [
                el("div", { class: "form-group" }, [
                    el("label", { for: "pos-nit-input", text: "NIT / cédula" }),
                    el("input", { type: "text", id: "pos-nit-input", class: "form-control", value: "CF" })
                ]),
                el("div", { class: "form-group" }, [
                    el("label", { for: "pos-razon-input", text: "Razón social" }),
                    el("input", { type: "text", id: "pos-razon-input", class: "form-control", value: "Consumidor Final" })
                ])
            ])
        ]);
    }

    itemRow(item) {
        return [
            el("span", { class: "cell-strong", text: item.sku }),
            el("span", { text: String(item.cantidad) }),
            el("span", { text: money(item.precio) }),
            el("span", { class: "cell-strong", text: money(item.precio * item.cantidad) }),
            el("button", {
                class: "btn btn-ghost btn-sm",
                text: "Quitar",
                on: { click: () => this.removeItem(item.sku) }
            })
        ];
    }

    buildTicketPanel() {
        this.totalElement = el("div", { class: "pos-total-value", text: money(0) });
        this.ticketLines = el("div", { class: "ticket-lines" });

        return el("div", { class: "card mb-0" }, [
            el("h3", { text: "Vista previa del ticket (80 mm)" }),
            el("div", { class: "pos-ticket-preview" }, [
                el("div", { class: "ticket-header" }, [
                    el("div", { text: "CALZADO TRADICIONAL S.A." }),
                    el("div", { text: "NIT: 1792019284001" }),
                    el("div", { text: "SUCURSAL CENTRO HISTÓRICO" })
                ]),
                this.ticketLines,
                el("div", { class: "ticket-total" }, [
                    el("span", { text: "TOTAL COBRADO:" }),
                    this.totalElement
                ]),
                el("div", { class: "ticket-footnote", text: "Documento simulado sin validez tributaria" })
            ]),
            el("button", {
                class: "btn btn-success btn-block",
                id: "btn-pos-cobrar",
                text: "Cobrar venta",
                on: { click: () => this.cobrar() }
            })
        ]);
    }

    buildReturnPanel() {
        return el("div", { class: "card mt-2" }, [
            el("div", { class: "card-title-row" }, [
                el("h3", { text: "Cambios y devoluciones omnicanal" }),
                el("span", { class: "badge badge-primary", text: "Garantía de 15 días" })
            ]),
            el("p", {
                class: "text-muted text-sm",
                text: "Si el calzado presenta daño o la compra superó los 15 días, se enruta a la bodega de liquidación; si está apto, reingresa a producto actual. Siempre emite nota de crédito."
            }),
            el("div", { class: "toolbar mt-2" }, [
                el("div", { class: "form-group" }, [
                    el("label", { for: "pos-dev-factura", text: "Número de factura" }),
                    el("input", { type: "text", id: "pos-dev-factura", class: "form-control", value: "FAC-000101" })
                ]),
                el("div", { class: "form-group" }, [
                    el("label", { for: "pos-dev-dias", text: "Días desde la entrega" }),
                    el("input", { type: "number", id: "pos-dev-dias", class: "form-control", value: "5" })
                ]),
                el("div", { class: "form-group" }, [
                    el("label", { for: "pos-dev-apto", text: "Estado del calzado" }),
                    el("select", { id: "pos-dev-apto", class: "form-control" }, [
                        el("option", { value: "true", text: "Apto (sin uso)" }),
                        el("option", { value: "false", text: "Dañado" })
                    ])
                ]),
                el("button", {
                    class: "btn btn-primary",
                    id: "btn-pos-devolucion",
                    text: "Procesar devolución",
                    on: { click: () => this.devolucion() }
                })
            ])
        ]);
    }

    addItem() {
        const sku = qs("#pos-scan-sku")?.value?.trim();
        if (!sku) {
            NotificationService.warning("Escaneá o digitá un SKU.");
            return;
        }
        const existing = this.items.find(item => item.sku === sku);
        if (existing) {
            existing.cantidad += 1;
        } else {
            this.items.push({ sku, cantidad: 1, precio: 65 });
        }
        this.rerenderItems();
    }

    removeItem(sku) {
        this.items = this.items.filter(item => item.sku !== sku);
        this.rerenderItems();
    }

    rerenderItems() {
        const scroll = this.container.querySelector(".table-scroll");
        if (scroll) {
            scroll.replaceChildren(
                table(["Ítem / SKU", "Cantidad", "Precio", "Subtotal", ""],
                    this.items.map(item => this.itemRow(item)),
                    { emptyMessage: "Sin ítems en el ticket." })
            );
        }
        this.renderTicket();
    }

    total() {
        return this.items.reduce((sum, item) => sum + item.precio * item.cantidad, 0);
    }

    renderTicket() {
        if (!this.ticketLines) return;
        this.ticketLines.replaceChildren();
        this.items.forEach(item => {
            this.ticketLines.append(el("div", { class: "ticket-line" }, [
                el("span", { text: `${item.cantidad}x ${item.sku}` }),
                el("span", { text: money(item.precio * item.cantidad) })
            ]));
        });
        this.totalElement.textContent = money(this.total());
    }

    async cobrar() {
        if (!this.items.length) {
            NotificationService.warning("Agregá al menos un ítem antes de cobrar.");
            return;
        }

        const button = qs("#btn-pos-cobrar");
        if (button) button.disabled = true;

        const nit = qs("#pos-nit-input")?.value || "CF";
        const razonSocial = qs("#pos-razon-input")?.value || "Consumidor Final";
        const tickets = [];
        let totalCobrado = 0;

        try {
            for (const item of this.items) {
                const data = await fetchJson(CONFIG.ENDPOINTS.POS_COBRAR, {
                    method: "POST",
                    body: { sku: item.sku, cantidad: item.cantidad, nit, razonSocial }
                });
                tickets.push(data.ticketNumero || "—");
                totalCobrado += Number(data.totalCobrado || 0);
            }
            NotificationService.success(
                `Venta concretada. Ticket(s): ${tickets.join(", ")} por ${money(totalCobrado)}. Stock descargado en Kardex.`
            );
            this.items = [];
            this.rerenderItems();
        } catch (error) {
            NotificationService.error(`No se pudo cobrar la venta: ${error.message}`);
        } finally {
            if (button) button.disabled = false;
        }
    }

    async devolucion() {
        const numeroFactura = qs("#pos-dev-factura")?.value?.trim();
        const dias = Number(qs("#pos-dev-dias")?.value || 0);
        const calzadoApto = qs("#pos-dev-apto")?.value === "true";

        if (!numeroFactura) {
            NotificationService.warning("Ingresá el número de factura de la devolución.");
            return;
        }

        try {
            const data = await fetchJson(CONFIG.ENDPOINTS.POS_DEVOLUCION, {
                method: "POST",
                body: { numeroFactura, motivo: "CAMBIO_TALLA", dias, calzadoApto }
            });
            NotificationService.info(
                `Devolución ${data.numeroDevolucion || "—"} procesada. Destino: ${data.enrutadoA || "—"}. Reembolso: ${money(data.montoReembolso || 0)}.`
            );
        } catch (error) {
            NotificationService.error(`No se pudo procesar la devolución: ${error.message}`);
        }
    }

    unmount() {
        this.items = [];
        this.container = null;
    }
}
