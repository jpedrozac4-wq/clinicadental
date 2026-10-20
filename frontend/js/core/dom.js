/* ============================================================================
   UTILIDADES DE DOM Y FORMATO
   ----------------------------------------------------------------------------
   Helpers minimos para construir vistas sin innerHTML: evitan XSS por
   construccion y mantienen los modulos legibles.
   ============================================================================ */
import { CONFIG } from "../config.js";

/**
 * Crea un elemento.
 *
 * @param {string} tag Etiqueta HTML.
 * @param {object} [attrs] Atributos; `class`, `text`, `dataset` y `on` tienen trato especial.
 * @param {Array<Node|string>} [children] Hijos a insertar en orden.
 */
export function el(tag, attrs = {}, children = []) {
    const node = document.createElement(tag);

    Object.entries(attrs).forEach(([key, value]) => {
        if (value === undefined || value === null || value === false) return;

        if (key === "class") {
            node.className = value;
        } else if (key === "text") {
            node.textContent = value;
        } else if (key === "dataset") {
            Object.entries(value).forEach(([dataKey, dataValue]) => {
                node.dataset[dataKey] = dataValue;
            });
        } else if (key === "on") {
            Object.entries(value).forEach(([eventName, handler]) => {
                node.addEventListener(eventName, handler);
            });
        } else if (value === true) {
            node.setAttribute(key, "");
        } else {
            node.setAttribute(key, value);
        }
    });

    appendChildren(node, children);
    return node;
}

export function appendChildren(node, children) {
    const list = Array.isArray(children) ? children : [children];
    list.filter(child => child !== null && child !== undefined && child !== false)
        .forEach(child => node.append(child instanceof Node ? child : document.createTextNode(String(child))));
    return node;
}

export function clear(node) {
    if (node) node.replaceChildren();
    return node;
}

export function qs(selector, root = document) {
    return root.querySelector(selector);
}

export function qsa(selector, root = document) {
    return Array.from(root.querySelectorAll(selector));
}

/** Formatea un monto en la moneda del sistema. */
export function money(value, currency = CONFIG.CURRENCY) {
    const amount = Number(value);
    if (!Number.isFinite(amount)) return `$0.00 ${currency}`;
    return `$${amount.toFixed(2)} ${currency}`;
}

/** Formatea una fecha ISO a un formato corto y legible. */
export function dateTime(value) {
    if (!value) return "—";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return String(value);
    return date.toLocaleString("es-ES", {
        year: "numeric", month: "2-digit", day: "2-digit",
        hour: "2-digit", minute: "2-digit"
    });
}

export function dateOnly(value) {
    if (!value) return "—";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return String(value);
    return date.toLocaleDateString("es-ES", { year: "numeric", month: "2-digit", day: "2-digit" });
}

/** Etiqueta legible para un estado del backend (EN_RUTA -> En Ruta). */
export function humanize(value) {
    if (!value) return "—";
    return String(value)
        .toLowerCase()
        .split("_")
        .map(word => word.charAt(0).toUpperCase() + word.slice(1))
        .join(" ");
}

/** Clase de badge segun el estado, para semaforos consistentes. */
export function estadoBadgeClass(estado) {
    const value = String(estado || "").toUpperCase();
    if (["ENTREGADO", "ENTREGADA", "PAGADA", "RESUELTO", "CERRADO", "APROBADA", "ACTIVO"].includes(value)) {
        return "badge badge-success";
    }
    if (["EN_RUTA", "CARGADO", "PREPARADO", "PREPARADA", "EN_ANALISIS", "EN_BODEGA_TRANSPORTE", "PENDIENTE", "PENDIENTE_APROBACION"].includes(value)) {
        return "badge badge-warning";
    }
    if (["EXPIRADA", "CANCELADA", "RECHAZADA", "ANULADA", "NUEVO"].includes(value)) {
        return "badge badge-danger";
    }
    return "badge badge-primary";
}

/** Construye una tabla simple con encabezados y filas ya resueltas. */
export function table(headers, rows, { emptyMessage = "Sin registros para mostrar." } = {}) {
    const tableEl = el("table", { class: "data-table" }, [
        el("thead", {}, [
            el("tr", {}, headers.map(header => el("th", { text: header })))
        ]),
        el("tbody", {}, rows.length
            ? rows.map(cells => el("tr", {}, cells.map(cell =>
                cell instanceof Node ? el("td", {}, [cell]) : el("td", { text: cell }))))
            : [el("tr", {}, [el("td", { colspan: headers.length, class: "text-muted text-center", text: emptyMessage })])])
    ]);
    return tableEl;
}

/** Estado de carga reutilizable. */
export function loadingState(message = "Cargando información…") {
    return el("div", { class: "state-box" }, [
        el("div", { class: "spinner" }),
        el("p", { class: "text-muted", text: message })
    ]);
}

/** Estado vacio reutilizable. */
export function emptyState(message, hint) {
    return el("div", { class: "state-box" }, [
        el("p", { class: "state-title", text: message }),
        hint ? el("p", { class: "text-muted", text: hint }) : null
    ]);
}

/** Estado de error reutilizable. */
export function errorState(message) {
    return el("div", { class: "state-box state-box-error" }, [
        el("p", { class: "state-title", text: "No se pudo cargar la información" }),
        el("p", { class: "text-muted", text: message })
    ]);
}

/** Bloque de tarjeta KPI. */
export function kpiCard(label, value, sub) {
    return el("div", { class: "kpi-stat-card" }, [
        el("div", { class: "kpi-stat-label", text: label }),
        el("div", { class: "kpi-stat-value", text: value }),
        sub ? el("div", { class: "kpi-stat-sub", text: sub }) : null
    ]);
}

/** Cabecera de seccion con titulo y acciones opcionales. */
export function sectionHeader(title, badgeText, actions = []) {
    return el("div", { class: "card-header" }, [
        el("h2", { text: title }),
        el("div", { class: "card-header-actions" }, [
            badgeText ? el("span", { class: "badge badge-primary", text: badgeText }) : null,
            ...actions
        ])
    ]);
}
