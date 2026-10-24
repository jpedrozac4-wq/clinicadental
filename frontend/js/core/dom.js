/* ============================================================================
   UTILIDADES DE DOM Y FORMATO
   ----------------------------------------------------------------------------
   Helpers minimos para construir vistas sin innerHTML: evitan XSS por
   construccion y mantienen los modulos legibles. Tambien centralizan el formato
   de fechas, edades y montos del dominio clinico.
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

    for (const [key, value] of Object.entries(attrs)) {
        if (value === undefined || value === null || value === false) continue;

        if (key === "class") {
            node.className = value;
        } else if (key === "text") {
            node.textContent = value;
        } else if (key === "dataset") {
            for (const [dataKey, dataValue] of Object.entries(value)) {
                node.dataset[dataKey] = dataValue;
            }
        } else if (key === "on") {
            for (const [eventName, handler] of Object.entries(value)) {
                node.addEventListener(eventName, handler);
            }
        } else if (value === true) {
            node.setAttribute(key, "");
        } else {
            node.setAttribute(key, value);
        }
    }

    appendChildren(node, children);
    return node;
}

export function appendChildren(node, children) {
    const list = Array.isArray(children) ? children : [children];
    for (const child of list) {
        if (child === null || child === undefined || child === false) continue;
        node.append(child instanceof Node ? child : document.createTextNode(String(child)));
    }
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
    if (value === null || value === undefined || value === "") return "—";
    const amount = Number(value);
    if (!Number.isFinite(amount)) return "—";
    return new Intl.NumberFormat(CONFIG.LOCALE, {
        style: "currency",
        currency,
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
    }).format(amount);
}

/**
 * Convierte el valor que llega del backend en un Date.
 * Las fechas sin hora ("2020-01-31") se construyen en horario local para que el
 * dia no se corra un dia hacia atras en zonas con offset negativo.
 */
export function parseDate(value) {
    if (!value) return null;
    if (typeof value === "string" && /^\d{4}-\d{2}-\d{2}$/.test(value)) {
        const [year, month, day] = value.split("-").map(Number);
        return new Date(year, month - 1, day);
    }
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? null : date;
}

/** Formatea una fecha ISO a un formato corto y legible. */
export function dateTime(value) {
    const date = parseDate(value);
    if (!date) return "—";
    return date.toLocaleString(CONFIG.LOCALE, {
        year: "numeric", month: "2-digit", day: "2-digit",
        hour: "2-digit", minute: "2-digit"
    });
}

export function dateOnly(value) {
    const date = parseDate(value);
    if (!date) return "—";
    return date.toLocaleDateString(CONFIG.LOCALE, {
        year: "numeric", month: "2-digit", day: "2-digit"
    });
}

/** Edad cumplida a partir de la fecha de nacimiento. */
export function edad(value) {
    const date = parseDate(value);
    if (!date) return "—";
    const hoy = new Date();
    let años = hoy.getFullYear() - date.getFullYear();
    const mes = hoy.getMonth() - date.getMonth();
    if (mes < 0 || (mes === 0 && hoy.getDate() < date.getDate())) {
        años -= 1;
    }
    if (años < 0) return "—";
    return años === 1 ? "1 año" : `${años} años`;
}

/** Etiqueta legible para un estado del backend (NO_ASISTIO -> No Asistio). */
export function humanize(value) {
    if (!value) return "—";
    return String(value)
        .toLowerCase()
        .split("_")
        .map(word => word.charAt(0).toUpperCase() + word.slice(1))
        .join(" ");
}

/** Clase de badge segun el estado clinico, para semaforos consistentes. */
export function estadoBadgeClass(estado) {
    const value = String(estado || "").toUpperCase();
    if (["ATENDIDA", "CONFIRMADA", "PAGADA", "EMITIDA", "ACTIVO", "ENVIADA"].includes(value)) {
        return "badge badge-success";
    }
    if (["PROGRAMADA", "REPROGRAMADA", "PAGO_PARCIAL", "PENDIENTE"].includes(value)) {
        return "badge badge-warning";
    }
    if (["CANCELADA", "NO_ASISTIO", "ANULADA", "VENCIDA", "FALLIDA"].includes(value)) {
        return "badge badge-danger";
    }
    return "badge badge-primary";
}

/** Badge de estado listo para insertar en una tabla o ficha. */export function estadoBadge(estado) {
    return el("span", { class: estadoBadgeClass(estado), text: humanize(estado) });
}

/** Chip compacto para permisos o etiquetas. */
export function chip(text) {
    return el("span", { class: "chip", text });
}

/* ----------------------------------------------------------------------------
   Controles de formulario
   ---------------------------------------------------------------------------- */

export function inputControl({ class: className = "form-control", ...attrs } = {}) {
    const hasClass = className !== null;
    return el("input", hasClass ? { class: className, ...attrs } : attrs);
}

export function textareaControl({ class: className = "form-control", ...attrs } = {}) {
    return el("textarea", { class: className, ...attrs });
}

/**
 * Select con opciones { value, label }.
 * @param {Array<{value:string,label:string}>} options
 */
export function selectControl(options, { class: className = "form-control", ...attrs } = {}) {
    return el("select", { class: className, ...attrs },
        options.map(option => el("option", { value: option.value, text: option.label })));
}

/** Grupo de formulario: etiqueta + control + pista/error opcional. */
export function formField(labelText, control, { hint = null, required = false } = {}) {
    return el("div", { class: "form-group" }, [
        el("label", { for: control.id || null, text: labelText, class: required ? "is-required" : null }),
        control,
        hint ? el("p", { class: "field-hint", text: hint }) : null
    ]);
}

/* ----------------------------------------------------------------------------
   Estructuras de presentacion
   ---------------------------------------------------------------------------- */

/** Construye una tabla simple con encabezados y filas ya resueltas. */
export function table(headers, rows, { emptyMessage = "Sin registros para mostrar." } = {}) {
    return el("div", { class: "table-scroll" }, [
        el("table", { class: "data-table" }, [
            el("thead", {}, [
                el("tr", {}, headers.map(header => el("th", { text: header })))
            ]),
            el("tbody", {}, rows.length
                ? rows.map(cells => el("tr", {}, cells.map(cell =>
                    cell instanceof Node ? el("td", {}, [cell]) : el("td", { text: cell }))))
                : [el("tr", {}, [el("td", {
                    colspan: headers.length,
                    class: "text-muted text-center",
                    text: emptyMessage
                })])])
        ])
    ]);
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

/** Cabecera de seccion dentro de una tarjeta, con acciones opcionales. */
export function sectionHeader(title, badgeText, actions = []) {
    return el("div", { class: "card-header" }, [
        el("h2", { text: title }),
        el("div", { class: "card-header-actions" }, [
            badgeText ? el("span", { class: "badge badge-primary", text: badgeText }) : null,
            ...actions
        ])
    ]);
}

/** Cabecera de pagina: titulo (h2), bajada y acciones a la derecha. */
export function pageHeader(title, subtitle = null, actions = []) {
    return el("div", { class: "page-header" }, [
        el("div", { class: "page-header-text" }, [
            el("h2", { text: title }),
            subtitle ? el("p", { class: "text-muted text-sm", text: subtitle }) : null
        ]),
        actions.length ? el("div", { class: "page-header-actions" }, actions) : null
    ]);
}

/** Fila de detalle etiqueta/valor para fichas (dl). */
export function detailRow(label, value) {
    return el("div", { class: "detail-row" }, [
        el("dt", { text: label }),
        el("dd", {}, [value instanceof Node ? value : String(value ?? "—")])
    ]);
}
