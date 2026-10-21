/* ============================================================================
   BROWSER SMOKE HARNESS — CLINICA DENTAL SPA (dependency-free, raw CDP)
   ----------------------------------------------------------------------------
   Drives a headless Chrome against the running stack and walks the delivered
   clinic application end to end: login + JWT persistence, dashboard KPIs,
   patients (create / search / history / edit / logical delete), treatment
   catalog (create / edit / duplicate 409), agenda (schedule / reschedule /
   cancel), performed treatment from the patient history, and billing
   (emit / partial payment / final payment / PDF). It records console errors,
   failed requests and screenshots per step, writes report.json and exits 1 on
   a fatal error.

   Every DOM hook used here is listed in UI_CONTRACT below with the frontend
   file that defines it. The static preflight verifies those hooks still exist
   before Chrome is launched, so a changed UI fails fast instead of silently
   passing.

   Usage:
     docker compose up -d                      # stack must be reachable
     node tools/browser-smoke.mjs              # full scenario
     node tools/browser-smoke.mjs --check-selectors   # preflight only (no Chrome)

   Environment:
     BASE_URL   front end origin            (default http://localhost)
     CHROME_BIN absolute path to chrome     (default: Windows Chrome install)
     CDP_PORT   remote debugging port       (default 9333, not the MCP's 9222)
     OUT_DIR    screenshots and report.json (default tools/artifacts)

   Exit code is 0 when the report has no fatal error, 1 otherwise.
   ============================================================================ */

import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { spawn } from "node:child_process";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = dirname(HERE);
const CHROME =
    process.env.CHROME_BIN ||
    (process.platform === "win32"
        ? "C:/Program Files/Google/Chrome/Application/chrome.exe"
        : "google-chrome");
const PORT = Number(process.env.CDP_PORT || 9333);
const BASE = (process.env.BASE_URL || "http://localhost").replace(/\/+$/, "");
const OUT = process.env.OUT_DIR || join(HERE, "artifacts");
const USER_DIR = join(OUT, "chrome-profile");

/* ----------------------------------------------------------------------------
   UI CONTRACT — selector -> frontend source that defines it
   ----------------------------------------------------------------------------
   `file` is relative to the repository root and `marker` is the exact snippet
   the preflight looks for inside that file. Keep both in sync with the front
   end: the harness is only allowed to depend on hooks it can prove exist.
   -------------------------------------------------------------------------- */
const UI_CONTRACT = [
    // --- Login gate, shell and view container: frontend/index.html ---
    { selector: "#login-gate", file: "frontend/index.html", marker: 'id="login-gate"' },
    { selector: "#login-form", file: "frontend/index.html", marker: 'id="login-form"' },
    { selector: "#login-username", file: "frontend/index.html", marker: 'id="login-username"' },
    { selector: "#login-password", file: "frontend/index.html", marker: 'id="login-password"' },
    { selector: "#btn-login", file: "frontend/index.html", marker: 'id="btn-login"' },
    { selector: "#app-shell", file: "frontend/index.html", marker: 'id="app-shell"' },
    { selector: "#current-user-name", file: "frontend/index.html", marker: 'id="current-user-name"' },
    { selector: "#current-user-role", file: "frontend/index.html", marker: 'id="current-user-role"' },
    { selector: "#sidebar-menu", file: "frontend/index.html", marker: 'id="sidebar-menu"' },
    { selector: "#app-view", file: "frontend/index.html", marker: 'id="app-view"' },

    // --- Session storage key and shell chrome: js/config.js + js/core/shell.js ---
    { selector: "localStorage[clinica.auth.token]", file: "frontend/js/config.js", marker: 'TOKEN: "clinica.auth.token"' },
    { selector: ".sidebar-link[data-route-id]", file: "frontend/js/core/shell.js", marker: 'class: "sidebar-link"' },
    { selector: ".sidebar-link[data-route-id]", file: "frontend/js/core/shell.js", marker: "dataset: { routeId: route.id }" },

    // --- Reserved route (must stay out of the menu): js/core/routes.js ---
    { selector: "[data-route-id=\"recordatorios\"] (reserved, must NOT exist)", file: "frontend/js/core/routes.js", marker: 'id: "recordatorios"' },
    { selector: "RUTAS_RESERVADAS", file: "frontend/js/core/routes.js", marker: "export const RUTAS_RESERVADAS" },

    // --- Shared presentation primitives: js/core/dom.js ---
    { selector: ".kpi-stat-card", file: "frontend/js/core/dom.js", marker: 'class: "kpi-stat-card"' },
    { selector: ".spinner", file: "frontend/js/core/dom.js", marker: 'class: "spinner"' },
    { selector: ".data-table", file: "frontend/js/core/dom.js", marker: 'class: "data-table"' },
    { selector: ".state-box-error", file: "frontend/js/core/dom.js", marker: '"state-box state-box-error"' },
    { selector: ".badge.badge-success / .badge.badge-danger", file: "frontend/js/core/dom.js", marker: 'return "badge badge-success";' },
    { selector: ".detail-row", file: "frontend/js/core/dom.js", marker: 'class: "detail-row"' },
    { selector: ".detail-list", file: "frontend/js/modules/paciente-historial-module.js", marker: 'class: "detail-list detail-list-grid"' },
    { selector: "#detalle-factura .detail-list", file: "frontend/js/modules/facturacion-module.js", marker: 'el("dl", { class: "detail-list detail-list-grid" }' },
    { selector: ".page-header h2", file: "frontend/js/core/dom.js", marker: 'class: "page-header"' },

    // --- Dialog handling: toasts + modal confirmation (never window.confirm) ---
    { selector: "#toast-container .toast", file: "frontend/js/services/notification-service.js", marker: 'container.id = "toast-container"' },
    { selector: "#toast-container .toast.toast-success", file: "frontend/js/services/notification-service.js", marker: "toast.className = `toast toast-${type}`;" },
    { selector: ".modal-overlay.active .modal-message", file: "frontend/js/services/notification-service.js", marker: 'text.className = "modal-message"' },
    { selector: ".modal-overlay.active .btn-danger", file: "frontend/js/services/notification-service.js", marker: "overlay.className = \"modal-overlay active\"" },
    { selector: ".modal-overlay.active .btn-danger", file: "frontend/js/services/notification-service.js", marker: 'confirmButton.className = "btn btn-danger"' },

    // --- Dashboard: js/modules/dashboard-module.js ---
    { selector: ".kpi-container", file: "frontend/js/modules/dashboard-module.js", marker: 'class: "kpi-container"' },
    { selector: ".pending-list", file: "frontend/js/modules/dashboard-module.js", marker: 'class: "pending-list"' },

    // --- Pacientes: js/modules/pacientes-module.js ---
    { selector: "#btn-nuevo-paciente", file: "frontend/js/modules/pacientes-module.js", marker: 'id: "btn-nuevo-paciente"' },
    { selector: "#filtro-paciente", file: "frontend/js/modules/pacientes-module.js", marker: 'id: "filtro-paciente"' },
    { selector: "#btn-buscar-paciente", file: "frontend/js/modules/pacientes-module.js", marker: 'id: "btn-buscar-paciente"' },
    { selector: "#lista-pacientes", file: "frontend/js/modules/pacientes-module.js", marker: 'id: "lista-pacientes"' },
    { selector: "#panel-paciente-form", file: "frontend/js/modules/pacientes-module.js", marker: 'id: "panel-paciente-form"' },
    { selector: "#form-paciente", file: "frontend/js/modules/pacientes-module.js", marker: 'id: "form-paciente"' },
    { selector: "#form-paciente-error", file: "frontend/js/modules/pacientes-module.js", marker: 'id: "form-paciente-error"' },
    { selector: "#input-nombres", file: "frontend/js/modules/pacientes-module.js", marker: 'id: "input-nombres"' },
    { selector: "#input-apellidos", file: "frontend/js/modules/pacientes-module.js", marker: 'id: "input-apellidos"' },
    { selector: "#input-fecha-nacimiento", file: "frontend/js/modules/pacientes-module.js", marker: 'id: "input-fecha-nacimiento"' },
    { selector: "#input-telefono", file: "frontend/js/modules/pacientes-module.js", marker: 'id: "input-telefono"' },
    { selector: "#input-email", file: "frontend/js/modules/pacientes-module.js", marker: 'id: "input-email"' },
    { selector: "#input-direccion", file: "frontend/js/modules/pacientes-module.js", marker: 'id: "input-direccion"' },
    { selector: "[data-accion=\"editar\"] (pacientes)", file: "frontend/js/modules/pacientes-module.js", marker: 'dataset: { accion: "editar", id: paciente.id }' },
    { selector: "#lista-pacientes .btn-danger (dar de baja)", file: "frontend/js/modules/pacientes-module.js", marker: 'class: "btn btn-sm btn-danger"' },
    { selector: "#lista-pacientes a[href*=\"/historial\"]", file: "frontend/js/modules/pacientes-module.js", marker: "href: `#/pacientes/${paciente.id}/historial`" },

    // --- Historial clinico: js/modules/paciente-historial-module.js ---
    { selector: "#historial-contenido", file: "frontend/js/modules/paciente-historial-module.js", marker: 'id: "historial-contenido"' },
    { selector: "#btn-registrar-realizado", file: "frontend/js/modules/paciente-historial-module.js", marker: 'id: "btn-registrar-realizado"' },

    // --- Performed treatment panel (shared): js/modules/realizado-form.js ---
    { selector: "#panel-realizado-form", file: "frontend/js/modules/realizado-form.js", marker: 'id: "panel-realizado-form"' },
    { selector: "#form-realizado", file: "frontend/js/modules/realizado-form.js", marker: 'id: "form-realizado"' },
    { selector: "#form-realizado-error", file: "frontend/js/modules/realizado-form.js", marker: 'id: "form-realizado-error"' },
    { selector: "#realizado-paciente", file: "frontend/js/modules/realizado-form.js", marker: 'id: "realizado-paciente"' },
    { selector: "#realizado-tratamiento", file: "frontend/js/modules/realizado-form.js", marker: 'id: "realizado-tratamiento"' },
    { selector: "#realizado-dentista", file: "frontend/js/modules/realizado-form.js", marker: 'id: "realizado-dentista"' },
    { selector: "#realizado-fecha", file: "frontend/js/modules/realizado-form.js", marker: 'id: "realizado-fecha"' },
    { selector: "#realizado-detalle", file: "frontend/js/modules/realizado-form.js", marker: 'id: "realizado-detalle"' },
    { selector: "#realizado-observaciones", file: "frontend/js/modules/realizado-form.js", marker: 'id: "realizado-observaciones"' },

    // --- Catalogo de tratamientos: js/modules/tratamientos-module.js ---
    { selector: "#btn-nuevo-tratamiento", file: "frontend/js/modules/tratamientos-module.js", marker: 'id: "btn-nuevo-tratamiento"' },
    { selector: "#filtro-tratamiento", file: "frontend/js/modules/tratamientos-module.js", marker: 'id: "filtro-tratamiento"' },
    { selector: "#lista-tratamientos", file: "frontend/js/modules/tratamientos-module.js", marker: 'id: "lista-tratamientos"' },
    { selector: "#panel-tratamiento-form", file: "frontend/js/modules/tratamientos-module.js", marker: 'id: "panel-tratamiento-form"' },
    { selector: "#form-tratamiento", file: "frontend/js/modules/tratamientos-module.js", marker: 'id: "form-tratamiento"' },
    { selector: "#form-tratamiento-error", file: "frontend/js/modules/tratamientos-module.js", marker: 'id: "form-tratamiento-error"' },
    { selector: "#input-codigo", file: "frontend/js/modules/tratamientos-module.js", marker: 'id: "input-codigo"' },
    { selector: "#input-nombre", file: "frontend/js/modules/tratamientos-module.js", marker: 'id: "input-nombre"' },
    { selector: "#input-precio", file: "frontend/js/modules/tratamientos-module.js", marker: 'id: "input-precio"' },
    { selector: "#input-duracion", file: "frontend/js/modules/tratamientos-module.js", marker: 'id: "input-duracion"' },
    { selector: "#btn-cancelar-tratamiento", file: "frontend/js/modules/tratamientos-module.js", marker: 'id: "btn-cancelar-tratamiento"' },
    { selector: "#lista-tratamientos .btn-outline (editar)", file: "frontend/js/modules/tratamientos-module.js", marker: 'text: "Editar"' },

    // --- Agenda de citas: js/modules/agenda-module.js ---
    { selector: "#btn-nueva-cita", file: "frontend/js/modules/agenda-module.js", marker: 'id: "btn-nueva-cita"' },
    { selector: "#lista-citas", file: "frontend/js/modules/agenda-module.js", marker: 'id: "lista-citas"' },
    { selector: "#panel-cita-form", file: "frontend/js/modules/agenda-module.js", marker: 'id: "panel-cita-form"' },
    { selector: "#form-cita", file: "frontend/js/modules/agenda-module.js", marker: 'id: "form-cita"' },
    { selector: "#form-cita-error", file: "frontend/js/modules/agenda-module.js", marker: 'id: "form-cita-error"' },
    { selector: "#cita-paciente", file: "frontend/js/modules/agenda-module.js", marker: 'id: "cita-paciente"' },
    { selector: "#cita-tratamiento", file: "frontend/js/modules/agenda-module.js", marker: 'id: "cita-tratamiento"' },
    { selector: "#cita-dentista", file: "frontend/js/modules/agenda-module.js", marker: 'id: "cita-dentista"' },
    { selector: "#cita-fecha-hora", file: "frontend/js/modules/agenda-module.js", marker: 'id: "cita-fecha-hora"' },
    { selector: "#cita-motivo", file: "frontend/js/modules/agenda-module.js", marker: 'id: "cita-motivo"' },
    { selector: "[data-accion=\"reprogramar\"]", file: "frontend/js/modules/agenda-module.js", marker: 'dataset: { accion: "reprogramar", id: cita.id }' },
    { selector: "[data-accion=\"cancelar\"]", file: "frontend/js/modules/agenda-module.js", marker: 'dataset: { accion: "cancelar", id: cita.id }' },
    { selector: "#panel-reprogramar-form", file: "frontend/js/modules/agenda-module.js", marker: 'id: "panel-reprogramar-form"' },
    { selector: "#form-reprogramar", file: "frontend/js/modules/agenda-module.js", marker: 'id: "form-reprogramar"' },
    { selector: "#form-reprogramar-error", file: "frontend/js/modules/agenda-module.js", marker: 'id: "form-reprogramar-error"' },
    { selector: "#reprogramar-fecha-hora", file: "frontend/js/modules/agenda-module.js", marker: 'id: "reprogramar-fecha-hora"' },

    // --- Facturacion y pagos: js/modules/facturacion-module.js ---
    { selector: "#factura-paciente", file: "frontend/js/modules/facturacion-module.js", marker: 'id: "factura-paciente"' },
    { selector: "#factura-candidatos", file: "frontend/js/modules/facturacion-module.js", marker: 'id: "factura-candidatos"' },
    { selector: "#factura-candidatos .check-row input[data-realizado-id]", file: "frontend/js/modules/facturacion-module.js", marker: "dataset: { realizadoId: candidato.id }" },
    { selector: "#form-factura", file: "frontend/js/modules/facturacion-module.js", marker: 'id: "form-factura"' },
    { selector: "#form-factura-error", file: "frontend/js/modules/facturacion-module.js", marker: 'id: "form-factura-error"' },
    { selector: "#factura-observaciones", file: "frontend/js/modules/facturacion-module.js", marker: 'id: "factura-observaciones"' },
    { selector: "#lista-facturas", file: "frontend/js/modules/facturacion-module.js", marker: 'id: "lista-facturas"' },
    { selector: "#filtro-factura", file: "frontend/js/modules/facturacion-module.js", marker: 'id: "filtro-factura"' },
    { selector: "[data-accion=\"detalle\"]", file: "frontend/js/modules/facturacion-module.js", marker: 'dataset: { accion: "detalle", id: factura.id }' },
    { selector: "[data-accion=\"pdf\"]", file: "frontend/js/modules/facturacion-module.js", marker: 'dataset: { accion: "pdf", id: factura.id }' },
    { selector: "#panel-detalle-factura", file: "frontend/js/modules/facturacion-module.js", marker: 'class: "card", id: "panel-detalle-factura"' },
    { selector: "#detalle-factura", file: "frontend/js/modules/facturacion-module.js", marker: 'id: "detalle-factura"' },
    { selector: "#form-pago", file: "frontend/js/modules/facturacion-module.js", marker: 'id: "form-pago"' },
    { selector: "#form-pago-error", file: "frontend/js/modules/facturacion-module.js", marker: 'id: "form-pago-error"' },
    { selector: "#pago-monto", file: "frontend/js/modules/facturacion-module.js", marker: 'id: "pago-monto"' },
    { selector: "#pago-metodo", file: "frontend/js/modules/facturacion-module.js", marker: 'id: "pago-metodo"' },
    { selector: "#pago-referencia", file: "frontend/js/modules/facturacion-module.js", marker: 'id: "pago-referencia"' },
    { selector: "#detalle-factura .alert-box.info (sin saldo)", file: "frontend/js/modules/facturacion-module.js", marker: 'class: "alert-box info"' }
];

const STORAGE_TOKEN_KEY = "clinica.auth.token";

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

function assert(condition, message) {
    if (!condition) throw new Error(message);
}

/** Quotes a string so it can be embedded inside an evaluate() expression. */
const sel = (value) => JSON.stringify(value);

/* ----------------------------------------------------------------------------
   Static preflight: fail fast when a hook the scenario depends on is gone
   -------------------------------------------------------------------------- */

function preflight() {
    const cache = new Map();
    const missing = [];

    for (const hook of UI_CONTRACT) {
        if (!cache.has(hook.file)) {
            cache.set(hook.file, readFileSync(join(ROOT, hook.file), "utf8"));
        }
        if (!cache.get(hook.file).includes(hook.marker)) missing.push(hook);
    }

    if (missing.length) {
        const detail = missing
            .map((hook) => `  - ${hook.selector}\n      esperado en ${hook.file}: ${hook.marker}`)
            .join("\n");
        const error = new Error(
            `Preflight de selectores falló: ${missing.length} hook(s) de la UI ya no existen en el frontend.\n`
            + `El escenario no puede correr contra una UI distinta; actualizá tools/browser-smoke.mjs.\n${detail}`
        );
        error.missing = missing;
        throw error;
    }

    return { checked: UI_CONTRACT.length, files: [...cache.keys()] };
}

/* ----------------------------------------------------------------------------
   Minimal CDP client
   -------------------------------------------------------------------------- */

class Cdp {
    constructor(ws) {
        this.ws = ws;
        this.seq = 0;
        this.pending = new Map();
        this.eventHandlers = new Map();
        this.console = [];
        this.exceptions = [];
        this.logs = [];
        this.failed = [];
        this.badStatus = [];
        this.network = [];
        this.requests = new Map();
    }

    static async connect(wsUrl) {
        const ws = new WebSocket(wsUrl);
        await new Promise((resolve, reject) => {
            ws.onopen = resolve;
            ws.onerror = () => reject(new Error("No se pudo abrir el socket de DevTools"));
        });
        const cdp = new Cdp(ws);
        ws.onmessage = (event) => {
            let msg;
            try {
                msg = JSON.parse(event.data);
            } catch {
                return; // Ignore malformed CDP frames instead of breaking the socket.
            }
            if (msg.id !== undefined && cdp.pending.has(msg.id)) {
                const { resolve, reject } = cdp.pending.get(msg.id);
                cdp.pending.delete(msg.id);
                if (msg.error) reject(new Error(`${msg.error.message} (${msg.method ?? ""})`));
                else resolve(msg.result);
                return;
            }
            cdp.record(msg);
        };
        return cdp;
    }

    /** Subscribes to one CDP event; returns the unsubscribe function. */
    on(method, handler) {
        const current = this.eventHandlers.get(method) || [];
        current.push(handler);
        this.eventHandlers.set(method, current);
        return () => {
            const list = this.eventHandlers.get(method) || [];
            this.eventHandlers.set(method, list.filter((item) => item !== handler));
        };
    }

    record(msg) {
        const p = msg.params || {};
        const handlers = this.eventHandlers.get(msg.method);
        if (handlers) {
            handlers.forEach((handler) => {
                try {
                    handler(p);
                } catch (error) {
                    this.exceptions.push({ text: `handler ${msg.method}: ${error.message}` });
                }
            });
        }

        if (msg.method === "Runtime.consoleAPICalled") {
            const text = (p.args || []).map((a) => a.value ?? a.description ?? a.type).join(" ");
            if (p.type === "error" || p.type === "warning") {
                this.console.push({ level: p.type, text });
            }
        } else if (msg.method === "Runtime.exceptionThrown") {
            const d = p.exceptionDetails || {};
            this.exceptions.push({
                text: d.exception?.description || d.text || "unknown exception",
                url: d.url,
                line: d.lineNumber
            });
        } else if (msg.method === "Log.entryAdded") {
            const e = p.entry || {};
            if (e.level === "error" || e.level === "warning") {
                this.logs.push({ level: e.level, text: e.text, source: e.source, url: e.url || "" });
            }
        } else if (msg.method === "Network.requestWillBeSent") {
            this.requests.set(p.requestId, {
                url: p.request?.url || "",
                method: p.request?.method || ""
            });
        } else if (msg.method === "Network.loadingFailed") {
            const known = this.requests.get(p.requestId);
            this.failed.push({
                url: known?.url || p.requestId,
                error: p.errorText,
                type: p.type
            });
        } else if (msg.method === "Network.responseReceived") {
            const r = p.response || {};
            const known = this.requests.get(p.requestId);
            this.network.push({
                status: r.status,
                url: r.url,
                mimeType: r.mimeType,
                method: known?.method || ""
            });
            if (r.status >= 400) {
                this.badStatus.push({ status: r.status, url: r.url });
            }
        }
    }

    send(method, params = {}) {
        const id = ++this.seq;
        this.ws.send(JSON.stringify({ id, method, params }));
        return new Promise((resolve, reject) => {
            this.pending.set(id, { resolve, reject });
            setTimeout(() => {
                if (this.pending.has(id)) {
                    this.pending.delete(id);
                    reject(new Error(`timeout: ${method}`));
                }
            }, 20000);
        });
    }

    async evaluate(expression) {
        const result = await this.send("Runtime.evaluate", {
            expression: `(() => { ${expression} })()`,
            returnByValue: true,
            awaitPromise: true
        });
        if (result.exceptionDetails) {
            throw new Error(result.exceptionDetails.exception?.description || "La evaluación en la página falló");
        }
        return result.result.value;
    }

    async waitFor(expression, timeoutMs = 12000, label = expression) {
        const deadline = Date.now() + timeoutMs;
        while (Date.now() < deadline) {
            try {
                if (await this.evaluate(expression)) return true;
            } catch {
                /* page still navigating */
            }
            await sleep(150);
        }
        throw new Error(`Timeout esperando: ${label}`);
    }

    async screenshot(file) {
        const shot = await this.send("Page.captureScreenshot", { format: "png", captureBeyondViewport: true });
        writeFileSync(join(OUT, file), Buffer.from(shot.data, "base64"));
    }

    drain() {
        return {
            console: this.console.splice(0),
            exceptions: this.exceptions.splice(0),
            logs: this.logs.splice(0),
            failed: this.failed.splice(0),
            badStatus: this.badStatus.splice(0),
            network: this.network.splice(0)
        };
    }
}

/* ----------------------------------------------------------------------------
   Findings (console / network noise) — reported, not swallowed
   -------------------------------------------------------------------------- */

/** Requests that are browser noise, not application failures. */
function isNoise(url) {
    if (!url) return true;
    return url.startsWith("blob:")
        || url.startsWith("data:")
        || url.includes("favicon.ico")
        || url.startsWith("devtools:");
}

function findingsFor(label, drained, expectedHttp = []) {
    const items = [];

    drained.console.forEach((c) => items.push(`${label}: console.${c.level} -> ${c.text}`));
    drained.exceptions.forEach((e) => items.push(`${label}: excepción no capturada -> ${e.text}`));
    drained.logs.forEach((l) => {
        if (isNoise(l.url)) return; // e.g. the favicon 404 the browser logs on its own
        items.push(`${label}: log.${l.level} (${l.source}) -> ${l.text}`);
    });

    drained.badStatus.forEach((b) => {
        if (isNoise(b.url)) return;
        const expected = expectedHttp.some((entry) => b.url.includes(entry.url) && b.status === entry.status);
        items.push(`${label}: HTTP ${b.status} -> ${b.url}${expected ? " (esperado por el escenario)" : ""}`);
    });

    drained.failed.forEach((f) => {
        if (isNoise(f.url)) return;
        items.push(`${label}: request fallida (${f.error}) -> ${f.url}`);
    });

    return items;
}

/* ----------------------------------------------------------------------------
   DOM helpers — every selector comes from UI_CONTRACT above
   -------------------------------------------------------------------------- */

async function click(cdp, selector) {
    const clicked = await cdp.evaluate(`
        const node = document.querySelector(${sel(selector)});
        if (!node) return false;
        node.click();
        return true;
    `);
    assert(clicked, `No se encontró el elemento ${selector} para interactuar.`);
}

async function setValue(cdp, selector, value) {
    const applied = await cdp.evaluate(`
        const node = document.querySelector(${sel(selector)});
        if (!node) return null;
        node.focus();
        node.value = ${JSON.stringify(String(value))};
        node.dispatchEvent(new Event('input', { bubbles: true }));
        node.dispatchEvent(new Event('change', { bubbles: true }));
        return node.value;
    `);
    assert(
        applied === String(value),
        `No se pudo fijar ${selector} en ${JSON.stringify(String(value))} (quedó ${JSON.stringify(applied)}).`
    );
}

async function submitForm(cdp, selector) {
    const submitted = await cdp.evaluate(`
        const form = document.querySelector(${sel(selector)});
        if (!form) return false;
        form.requestSubmit();
        return true;
    `);
    assert(submitted, `No se encontró el formulario ${selector} para enviar.`);
}

async function textOf(cdp, selector) {
    const value = await cdp.evaluate(`
        const node = document.querySelector(${sel(selector)});
        return node ? node.textContent.trim() : null;
    `);
    return value === null ? "" : value;
}

async function countOf(cdp, selector) {
    const total = await cdp.evaluate(`return document.querySelectorAll(${sel(selector)}).length;`);
    return Number(total) || 0;
}

/** true when the node exists, is not hidden and is not display/visibility-suppressed. */
async function isVisible(cdp, selector) {
    const visible = await cdp.evaluate(`
        const node = document.querySelector(${sel(selector)});
        if (!node) return false;
        const style = getComputedStyle(node);
        return !node.hidden && style.display !== 'none' && style.visibility !== 'hidden';
    `);
    return visible === true;
}

/** Waits until the mounted view has no spinner left, i.e. it settled. */
async function settle(cdp, timeoutMs = 15000) {
    const deadline = Date.now() + timeoutMs;
    while (Date.now() < deadline) {
        const pending = await cdp.evaluate("return document.querySelectorAll('#app-view .spinner').length;");
        if (pending === 0) {
            await sleep(250);
            return true;
        }
        await sleep(150);
    }
    return false;
}

/** Row snapshot of a table inside a module container. */
async function rowsOf(cdp, containerSelector) {
    const rows = await cdp.evaluate(`
        const root = document.querySelector(${sel(containerSelector)});
        if (!root) return [];
        return Array.from(root.querySelectorAll('tbody tr')).map(tr => ({
            text: tr.textContent.trim(),
            cells: Array.from(tr.children).map(td => td.textContent.trim()),
            badges: Array.from(tr.querySelectorAll('.badge')).map(b => b.textContent.trim()),
            actions: Array.from(tr.querySelectorAll('[data-accion]')).map(b => ({
                accion: b.dataset.accion,
                id: b.dataset.id
            })),
            links: Array.from(tr.querySelectorAll('a[href]')).map(a => a.getAttribute('href'))
        }));
    `);
    return Array.isArray(rows) ? rows : [];
}

/** Clicks a control inside the table row whose text contains `needle`. */
async function clickInRow(cdp, containerSelector, needle, childSelector) {
    const clicked = await cdp.evaluate(`
        const needle = ${JSON.stringify(needle)};
        const root = document.querySelector(${sel(containerSelector)});
        if (!root) return false;
        const row = Array.from(root.querySelectorAll('tbody tr')).find(tr => tr.textContent.includes(needle));
        if (!row) return false;
        const target = row.querySelector(${sel(childSelector)});
        if (!target) return false;
        target.click();
        return true;
    `);
    assert(clicked, `No se encontró la fila con "${needle}" o el control ${childSelector} dentro de ella.`);
}

/* ----------------------------------------------------------------------------
   Interaction helpers (toasts + modals, never window.confirm / window.alert)
   -------------------------------------------------------------------------- */

async function clearToasts(cdp) {
    await cdp.evaluate("document.querySelectorAll('#toast-container .toast').forEach(t => t.remove()); return true;");
}

async function waitToast(cdp, needle, timeoutMs = 15000) {
    const deadline = Date.now() + timeoutMs;
    while (Date.now() < deadline) {
        const toasts = await cdp.evaluate(
            "return Array.from(document.querySelectorAll('#toast-container .toast')).map(t => t.textContent);"
        );
        const hit = (toasts || []).find((text) => text.includes(needle));
        if (hit) return hit;
        await sleep(120);
    }
    throw new Error(`No apareció el aviso esperado en pantalla: "${needle}".`);
}

/**
 * Waits for the outcome of a form submit: the success toast or the inline
 * error box the module renders. Used to retry on agenda conflicts (409).
 */
async function waitFormOutcome(cdp, { errorSelector, busySelector, successNeedle, timeoutMs = 20000 }) {
    const deadline = Date.now() + timeoutMs;
    while (Date.now() < deadline) {
        const state = await cdp.evaluate(`
            const box = document.querySelector(${sel(errorSelector)});
            const error = box && !box.hidden ? box.textContent.trim() : "";
            const busyNode = document.querySelector(${sel(busySelector)});
            const busy = Boolean(busyNode && busyNode.disabled);
            const toasts = Array.from(document.querySelectorAll('#toast-container .toast')).map(t => t.textContent);
            return { error, busy, toasts };
        `);
        const success = (state.toasts || []).find((text) => text.includes(successNeedle));
        if (success) return { ok: true, toast: success };
        if (state.error && !state.busy) return { ok: false, error: state.error };
        await sleep(150);
    }
    throw new Error(`Sin respuesta del formulario (aviso "${successNeedle}") en el tiempo esperado.`);
}

async function waitModal(cdp, timeoutMs = 10000) {
    await cdp.waitFor(
        "return Boolean(document.querySelector('.modal-overlay.active .modal-message'));",
        timeoutMs,
        "diálogo de confirmación"
    );
    return textOf(cdp, ".modal-overlay.active .modal-message");
}

async function confirmModal(cdp) {
    await click(cdp, ".modal-overlay.active .btn-danger");
    await cdp.waitFor("return !document.querySelector('.modal-overlay.active');", 10000, "cierre del diálogo");
}

/* ----------------------------------------------------------------------------
   Value helpers
   -------------------------------------------------------------------------- */

/** Parses an es-ES money string ("500,00 US$") into a number. */
function parseMoney(text) {
    const raw = String(text ?? "");
    if (/sin saldo/i.test(raw)) return 0;
    const clean = raw.replace(/[^\d.,-]/g, "");
    if (!clean) return null;
    const normalized = clean.replace(/\./g, "").replace(",", ".");
    const value = Number(normalized);
    return Number.isFinite(value) ? value : null;
}

function pad(number) {
    return String(number).padStart(2, "0");
}

/** Formats a Date as the value expected by <input type="datetime-local">. */
function localDateTimeInput(date) {
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
        + `T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/** Future slot inside the default agenda window (today..+30 days). */
function futureSlot(dayOffset, minute) {
    const date = new Date();
    date.setDate(date.getDate() + dayOffset);
    date.setHours(7, minute, 0, 0);
    return localDateTimeInput(date);
}

function withTimeout(promise, timeoutMs, label) {
    let timer = null;
    const timeout = new Promise((_, reject) => {
        timer = setTimeout(() => reject(new Error(`Timeout esperando ${label}.`)), timeoutMs);
    });
    return Promise.race([promise, timeout]).finally(() => clearTimeout(timer));
}

/** Network log lookup, for asserting the exact HTTP contract of a step. */
function findCalls(cdp, { method = null, url = "", status = null }) {
    return cdp.network.filter((entry) =>
        (!method || entry.method === method)
        && entry.url.includes(url)
        && (status === null || entry.status === status));
}

/* ----------------------------------------------------------------------------
   Step wrapper: runs, screenshots, drains noise and records the report entry
   -------------------------------------------------------------------------- */

let stepIndex = 0;

async function step(cdp, label, fn, options = {}) {
    const result = (await fn()) || {};
    stepIndex += 1;
    const file = `${String(stepIndex).padStart(2, "0")}-${label}.png`;

    try {
        await cdp.screenshot(file);
    } catch (error) {
        result.screenshotError = error.message;
    }

    const drained = cdp.drain();
    const findings = findingsFor(label, drained, options.expectedHttp || []);
    report.steps.push({ step: label, screenshot: file, ...result, findings });
    report.findings.push(...findings);

    if (options.strictConsole) {
        const hard = drained.console.filter((c) => c.level === "error").length
            + drained.exceptions.length
            + drained.logs.filter((l) => l.level === "error" && !isNoise(l.url)).length;
        if (hard > 0) {
            throw new Error(`El paso "${label}" registró errores de consola: ${findings.join(" | ")}`);
        }
    }

    return result;
}

/* ----------------------------------------------------------------------------
   Main
   -------------------------------------------------------------------------- */

const report = {
    base: BASE,
    out: OUT,
    generatedAt: new Date().toISOString(),
    preflight: null,
    steps: [],
    findings: [],
    cleanup: null,
    artifacts: [],
    notes: []
};

const CHECK_ONLY = process.argv.includes("--check-selectors");

if (CHECK_ONLY) {
    try {
        const result = preflight();
        console.log(`Preflight OK: ${result.checked} hooks verificados en ${result.files.length} archivos del frontend.`);
        result.files.forEach((file) => console.log(`  · ${file}`));
        process.exit(0);
    } catch (error) {
        console.error(error.message);
        process.exit(1);
    }
}

mkdirSync(OUT, { recursive: true });

try {
    report.preflight = { ok: true, ...preflight() };
    report.selectorMap = UI_CONTRACT.map((hook) => ({ selector: hook.selector, source: hook.file }));
} catch (error) {
    report.preflight = {
        ok: false,
        missing: (error.missing || []).map((hook) => ({
            selector: hook.selector,
            file: hook.file,
            marker: hook.marker
        }))
    };
    report.ok = false;
    report.fatal = error.message;
    writeFileSync(join(OUT, "report.json"), JSON.stringify(report, null, 2));
    console.error(error.message);
    process.exit(1);
}

/* The scenario ids are unique per run so repeated runs never collide with data
   left behind by a previous execution (email, treatment code and motivo). */
const STAMP = `${Date.now()}${Math.floor(Math.random() * 1000)}`;
const PATIENT = {
    nombres: "QA Smoke",
    apellidos: `Paciente ${STAMP}`,
    fechaNacimiento: "1988-06-15",
    telefono: "700-00001",
    telefonoEditado: "700-99999",
    direccion: "Av. Smoke 123, Zona QA",
    email: `smoke-${STAMP}@example.com`
};
const TREATMENT = {
    codigo: `QA-${STAMP.toString(36).toUpperCase()}`,
    nombre: `Smoke tratamiento ${STAMP}`,
    descripcion: "Procedimiento creado por el smoke de navegador.",
    precio: "350",
    precioEditado: "500",
    duracionMinutos: "30"
};
const CITA_MOTIVO = `SMOKE-${STAMP}`;
const PAGO_REFERENCIA = `REF-${STAMP}`;
const DETALLE_REALIZADO = `Detalle de procedimiento ${STAMP}`;

const chrome = spawn(CHROME, [
    "--headless=new",
    `--remote-debugging-port=${PORT}`,
    `--user-data-dir=${USER_DIR}`,
    "--no-first-run",
    "--no-default-browser-check",
    "--disable-gpu",
    "--disable-dev-shm-usage",
    "--window-size=1440,900",
    "about:blank"
], { stdio: ["ignore", "pipe", "pipe"] });

const chromeStderr = [];
chrome.stderr.on("data", (data) => chromeStderr.push(data.toString()));
chrome.stdout.on("data", () => {});
// Without this listener a missing CHROME_BIN would crash before report.json exists.
chrome.on("error", (error) => chromeStderr.push(`spawn: ${error.message}`));

async function fetchDevtoolsJson(url, tries = 60) {
    for (let i = 0; i < tries; i++) {
        try {
            const res = await fetch(url);
            if (res.ok) return res.json();
        } catch {
            /* not up yet */
        }
        await sleep(500);
    }
    throw new Error(`No se pudo alcanzar ${url}`);
}

/** Opens a sidebar module through its real menu link. */
async function openModule(cdp, routeId, expectedHeading) {
    await click(cdp, `.sidebar-link[data-route-id="${routeId}"]`);
    await cdp.waitFor(
        `return (document.querySelector('#app-view .page-header h2')?.textContent || '').trim() === ${sel(expectedHeading)};`,
        20000,
        `vista "${expectedHeading}"`
    );
    await settle(cdp);
}

/** Selects an option whose visible text contains `needle` and returns its value. */
async function selectOptionByText(cdp, selector, needle) {
    const value = await cdp.evaluate(`
        const node = document.querySelector(${sel(selector)});
        if (!node) return null;
        const needle = ${JSON.stringify(needle)};
        const option = Array.from(node.options).find(o => o.value && o.textContent.includes(needle));
        if (!option) return null;
        node.value = option.value;
        node.dispatchEvent(new Event('change', { bubbles: true }));
        return option.value;
    `);
    assert(value !== null, `No hay ninguna opción con "${needle}" en ${selector}.`);
    return String(value);
}

/** Selects the first real option of a catalog select and returns { value, label }. */
async function selectFirstOption(cdp, selector) {
    const chosen = await cdp.evaluate(`
        const node = document.querySelector(${sel(selector)});
        if (!node) return null;
        const option = Array.from(node.options).find(o => o.value);
        if (!option) return null;
        node.value = option.value;
        node.dispatchEvent(new Event('change', { bubbles: true }));
        return { value: option.value, label: option.textContent.trim() };
    `);
    assert(chosen, `El selector ${selector} no tiene opciones cargadas.`);
    return chosen;
}

try {
    const version = await fetchDevtoolsJson(`http://127.0.0.1:${PORT}/json/version`);
    report.browser = version.Browser;

    const targets = await fetchDevtoolsJson(`http://127.0.0.1:${PORT}/json/list`);
    const page = targets.find((target) => target.type === "page");
    const cdp = await Cdp.connect(page.webSocketDebuggerUrl);

    await cdp.send("Page.enable");
    await cdp.send("Runtime.enable");
    await cdp.send("Log.enable");
    await cdp.send("Network.enable");
    await cdp.send("Emulation.setDeviceMetricsOverride", {
        width: 1440, height: 900, deviceScaleFactor: 1, mobile: false
    });

    /* ---------------- 0. Clean session (repeatable runs) ---------------- */
    await step(cdp, "prepare-session", async () => {
        await cdp.send("Page.navigate", { url: `${BASE}/index.html` });
        await cdp.waitFor("return document.readyState === 'complete';", 25000, "carga del documento");
        const hadToken = await cdp.evaluate(
            `return Boolean(localStorage.getItem(${sel(STORAGE_TOKEN_KEY)}));`
        );
        await cdp.evaluate("localStorage.clear(); sessionStorage.clear(); return true;");
        await cdp.send("Page.navigate", { url: `${BASE}/index.html` });
        await cdp.waitFor(
            "const gate = document.querySelector('#login-gate'); return Boolean(gate) && !gate.hidden;",
            25000,
            "compuerta de login"
        );
        return {
            title: await cdp.evaluate("return document.title;"),
            hadPreviousSession: hadToken === true,
            loginGateVisible: await isVisible(cdp, "#login-gate"),
            shellHidden: !(await isVisible(cdp, "#app-shell"))
        };
    }, { expectedHttp: [{ url: "/api/auth/me", status: 401 }] });

    /* ---------------- 1. Login as admin + JWT persistence ---------------- */
    const login = await step(cdp, "login-admin", async () => {
        await setValue(cdp, "#login-username", "admin");
        await setValue(cdp, "#login-password", "Password123!");
        await submitForm(cdp, "#login-form");
        await cdp.waitFor(
            "const shell = document.querySelector('#app-shell'); return Boolean(shell) && !shell.hidden;",
            25000,
            "shell autenticado"
        );
        await cdp.waitFor(
            "return (document.querySelector('#app-view .page-header h2')?.textContent || '').length > 0;",
            20000,
            "primera vista del router"
        );

        const token = await cdp.evaluate(
            `const raw = localStorage.getItem(${sel(STORAGE_TOKEN_KEY)}); return raw ? raw.length : 0;`
        );
        assert(token > 0, `El JWT no quedó guardado en la clave documentada "${STORAGE_TOKEN_KEY}".`);
        const segments = await cdp.evaluate(
            `return (localStorage.getItem(${sel(STORAGE_TOKEN_KEY)}) || '').split('.').length;`
        );
        assert(segments === 3, `El token guardado en "${STORAGE_TOKEN_KEY}" no tiene forma de JWT (${segments} segmentos).`);
        assert(
            findCalls(cdp, { method: "POST", url: "/api/auth/login", status: 200 }).length > 0,
            "El login no llegó a POST /api/auth/login con respuesta 200."
        );

        return {
            tokenSegments: segments,
            tokenKey: STORAGE_TOKEN_KEY,
            user: await textOf(cdp, "#current-user-name"),
            role: await textOf(cdp, "#current-user-role"),
            hash: await cdp.evaluate("return window.location.hash;"),
            menu: await cdp.evaluate(
                "return Array.from(document.querySelectorAll('.sidebar-link')).map(a => a.dataset.routeId);"
            )
        };
    });
    assert(login.role === "Administrador", `El rol mostrado no es Administrador: "${login.role}".`);
    assert(
        login.menu.includes("pacientes") && login.menu.includes("facturacion"),
        `El menú del administrador no incluye los módulos esperados: ${login.menu.join(", ")}.`
    );

    /* ---------------- 1b. Reload: session restored through GET /api/auth/me ---------------- */
    await step(cdp, "reload-session", async () => {
        await cdp.send("Page.navigate", { url: `${BASE}/index.html` });
        await cdp.waitFor(
            "const shell = document.querySelector('#app-shell'); return Boolean(shell) && !shell.hidden;",
            25000,
            "shell tras recargar"
        );
        await cdp.waitFor(
            "return (document.querySelector('#app-view .page-header h2')?.textContent || '').length > 0;",
            20000,
            "vista tras recargar"
        );
        assert(
            findCalls(cdp, { method: "GET", url: "/api/auth/me", status: 200 }).length > 0,
            "Tras recargar no se validó la sesión con GET /api/auth/me (200)."
        );
        assert((await isVisible(cdp, "#login-gate")) === false,
            "La compuerta de login siguió visible tras recargar con sesión válida.");
        return {
            heading: await textOf(cdp, "#app-view .page-header h2"),
            hash: await cdp.evaluate("return window.location.hash;"),
            tokenStillStored: await cdp.evaluate(
                `return Boolean(localStorage.getItem(${sel(STORAGE_TOKEN_KEY)}));`
            )
        };
    });

    /* ---------------- 2. Dashboard KPIs (strict: no console errors) ---------------- */
    await step(cdp, "dashboard", async () => {
        // Remount through the menu link so the KPI requests happen inside this
        // step (the network assertions below would otherwise race the reload).
        await click(cdp, '.sidebar-link[data-route-id="dashboard"]');
        await cdp.waitFor(
            "return document.querySelectorAll('.kpi-container .kpi-stat-card').length === 4;",
            20000,
            "KPIs del panel general"
        );
        await settle(cdp);
        const kpis = await cdp.evaluate(`
            return Array.from(document.querySelectorAll('.kpi-container .kpi-stat-card')).map(card => ({
                label: (card.querySelector('.kpi-stat-label')?.textContent || '').trim(),
                value: (card.querySelector('.kpi-stat-value')?.textContent || '').trim(),
                sub: (card.querySelector('.kpi-stat-sub')?.textContent || '').trim()
            }));
        `);
        assert(kpis.length === 4, `Se esperaban 4 KPIs y se encontraron ${kpis.length}.`);
        kpis.forEach((kpi) => {
            assert(kpi.label.length > 0, "Un KPI quedó sin etiqueta.");
            assert(kpi.value.length > 0 && kpi.value !== "—",
                `El KPI "${kpi.label}" no trajo datos del backend (valor "${kpi.value}").`);
        });
        assert(await countOf(cdp, "#app-view .state-box-error") === 0, "El panel general muestra un estado de error.");

        return {
            heading: await textOf(cdp, "#app-view .page-header h2"),
            kpis,
            pacientesApiOk: findCalls(cdp, { method: "GET", url: "/api/pacientes", status: 200 }).length > 0,
            tratamientosApiOk: findCalls(cdp, { method: "GET", url: "/api/tratamientos", status: 200 }).length > 0
        };
    }, { strictConsole: true });

    /* ---------------- 8. Recordatorios is still a reserved route ---------------- */
    await step(cdp, "recordatorios-reservado", async () => {
        assert(
            await countOf(cdp, '[data-route-id="recordatorios"]') === 0,
            "La ruta reservada de recordatorios apareció en el menú lateral."
        );
        const pending = await textOf(cdp, ".pending-list");
        assert(/recordatorios/i.test(pending), "El panel no anuncia recordatorios como módulo en preparación.");

        await cdp.evaluate("window.location.hash = '#/recordatorios'; return true;");
        await cdp.waitFor(
            "return (document.querySelector('#app-view h2')?.textContent || '').trim() === 'Ruta no encontrada';",
            15000,
            "fallback de ruta no encontrada"
        );
        const fallback = await textOf(cdp, "#app-view");

        await cdp.evaluate("window.location.hash = '#/dashboard'; return true;");
        await cdp.waitFor(
            "return (document.querySelector('#app-view .page-header h2')?.textContent || '').trim().startsWith('Hola');",
            20000,
            "regreso al panel general"
        );

        return {
            menuHasRecordatorios: false,
            pendingNotice: pending,
            fallbackTitle: "Ruta no encontrada",
            fallbackMentionsMissingRoute: fallback.includes("no existe en el sistema")
        };
    });

    /* ---------------- 3a. Pacientes: create ---------------- */
    await step(cdp, "pacientes-crear", async () => {
        await openModule(cdp, "pacientes", "Pacientes");
        await click(cdp, "#btn-nuevo-paciente");
        await cdp.waitFor(
            "const panel = document.querySelector('#panel-paciente-form'); return Boolean(panel) && !panel.hidden;",
            10000,
            "formulario de paciente"
        );

        await setValue(cdp, "#input-nombres", PATIENT.nombres);
        await setValue(cdp, "#input-apellidos", PATIENT.apellidos);
        await setValue(cdp, "#input-fecha-nacimiento", PATIENT.fechaNacimiento);
        await setValue(cdp, "#input-telefono", PATIENT.telefono);
        await setValue(cdp, "#input-email", PATIENT.email);
        await setValue(cdp, "#input-direccion", PATIENT.direccion);

        await clearToasts(cdp);
        await submitForm(cdp, "#form-paciente");
        const outcome = await waitFormOutcome(cdp, {
            errorSelector: "#form-paciente-error",
            busySelector: "#btn-guardar-paciente",
            successNeedle: "Paciente registrado con el id"
        });
        assert(outcome.ok, `No se pudo registrar el paciente: ${outcome.error}`);
        await cdp.waitFor(
            `return (document.querySelector('#lista-pacientes')?.textContent || '').includes(${sel(PATIENT.email)});`,
            20000,
            "paciente en el listado"
        );

        return {
            email: PATIENT.email,
            toast: outcome.toast,
            postOk: findCalls(cdp, { method: "POST", url: "/api/pacientes", status: 201 }).length > 0
        };
    });

    /* ---------------- 3b. Search + open clinical history ---------------- */
    const historialInicial = await step(cdp, "pacientes-buscar-historial", async () => {
        await setValue(cdp, "#filtro-paciente", PATIENT.email);
        await click(cdp, "#btn-buscar-paciente");
        await cdp.waitFor(
            "return document.querySelectorAll('#lista-pacientes tbody tr').length === 1;",
            20000,
            "un único resultado de búsqueda"
        );
        const found = await textOf(cdp, "#lista-pacientes");
        assert(found.includes(PATIENT.email), "La búsqueda por correo no devolvió al paciente creado.");
        assert(
            findCalls(cdp, { method: "GET", url: "/api/pacientes?texto=", status: 200 }).length > 0,
            "La búsqueda no llegó a GET /api/pacientes?texto=."
        );

        await clickInRow(cdp, "#lista-pacientes", PATIENT.email, 'a[href*="/historial"]');
        await cdp.waitFor(
            "return /^#\\/pacientes\\/\\d+\\/historial$/.test(window.location.hash);",
            20000,
            "ruta del historial clínico"
        );
        await cdp.waitFor(
            "return document.querySelectorAll('#historial-contenido .data-table').length === 2;",
            20000,
            "tablas del historial clínico"
        );

        const hash = await cdp.evaluate("return window.location.hash;");
        const patientId = hash.match(/(\d+)/)?.[1] || null;
        assert(patientId, `No se pudo leer el id del paciente desde el hash "${hash}".`);

        const body = await textOf(cdp, "#historial-contenido");
        assert(body.includes(PATIENT.email), "La ficha del historial no muestra el correo del paciente creado.");

        return {
            patientId,
            heading: await textOf(cdp, "#app-view .page-header h2"),
            tables: await countOf(cdp, "#historial-contenido .data-table"),
            emptyTreatments: body.includes("todavía no tiene tratamientos registrados"),
            emptyAppointments: body.includes("todavía no tiene citas registradas")
        };
    });
    const pacienteId = historialInicial.patientId;

    /* ---------------- 3c. Edit a patient field ---------------- */
    await step(cdp, "paciente-editar", async () => {
        await openModule(cdp, "pacientes", "Pacientes");
        await setValue(cdp, "#filtro-paciente", PATIENT.email);
        await click(cdp, "#btn-buscar-paciente");
        await cdp.waitFor(
            "return document.querySelectorAll('#lista-pacientes tbody tr').length === 1;",
            20000,
            "resultado único antes de editar"
        );

        await click(cdp, '#lista-pacientes [data-accion="editar"]');
        await cdp.waitFor(
            "const panel = document.querySelector('#panel-paciente-form'); return Boolean(panel) && !panel.hidden;",
            10000,
            "formulario de edición de paciente"
        );
        const cargado = await cdp.evaluate("return document.querySelector('#input-telefono').value;");
        assert(cargado === PATIENT.telefono, `El formulario no cargó el teléfono original (${cargado}).`);

        await setValue(cdp, "#input-telefono", PATIENT.telefonoEditado);
        await clearToasts(cdp);
        await submitForm(cdp, "#form-paciente");
        const outcome = await waitFormOutcome(cdp, {
            errorSelector: "#form-paciente-error",
            busySelector: "#btn-guardar-paciente",
            successNeedle: "Paciente actualizado correctamente"
        });
        assert(outcome.ok, `No se pudo editar el paciente: ${outcome.error}`);
        await cdp.waitFor(
            `return (document.querySelector('#lista-pacientes')?.textContent || '').includes(${sel(PATIENT.telefonoEditado)});`,
            20000,
            "teléfono editado en el listado"
        );

        return {
            toast: outcome.toast,
            telefonoAnterior: cargado,
            telefonoNuevo: PATIENT.telefonoEditado,
            putOk: findCalls(cdp, { method: "PUT", url: `/api/pacientes/${pacienteId}`, status: 200 }).length > 0
        };
    });

    /* ---------------- 4a. Tratamientos: create ---------------- */
    await step(cdp, "tratamientos-crear", async () => {
        await openModule(cdp, "tratamientos", "Catálogo de tratamientos");
        await click(cdp, "#btn-nuevo-tratamiento");
        await cdp.waitFor(
            "const panel = document.querySelector('#panel-tratamiento-form'); return Boolean(panel) && !panel.hidden;",
            10000,
            "formulario de tratamiento"
        );

        await setValue(cdp, "#input-codigo", TREATMENT.codigo);
        await setValue(cdp, "#input-nombre", TREATMENT.nombre);
        await setValue(cdp, "#input-precio", TREATMENT.precio);
        await setValue(cdp, "#input-duracion", TREATMENT.duracionMinutos);
        await setValue(cdp, "#input-descripcion", TREATMENT.descripcion);

        await clearToasts(cdp);
        await submitForm(cdp, "#form-tratamiento");
        const outcome = await waitFormOutcome(cdp, {
            errorSelector: "#form-tratamiento-error",
            busySelector: "#btn-guardar-tratamiento",
            successNeedle: `Tratamiento ${TREATMENT.codigo} creado correctamente`
        });
        assert(outcome.ok, `No se pudo crear el tratamiento: ${outcome.error}`);

        await setValue(cdp, "#filtro-tratamiento", TREATMENT.codigo);
        await cdp.waitFor(
            "return document.querySelectorAll('#lista-tratamientos tbody tr').length === 1;",
            20000,
            "tratamiento filtrado en el catálogo"
        );
        const row = (await rowsOf(cdp, "#lista-tratamientos"))[0];
        assert(row.text.includes(TREATMENT.nombre), "El catálogo no muestra el tratamiento recién creado.");

        return {
            codigo: TREATMENT.codigo,
            toast: outcome.toast,
            precio: TREATMENT.precio,
            postOk: findCalls(cdp, { method: "POST", url: "/api/tratamientos", status: 201 }).length > 0
        };
    });

    /* ---------------- 4b. Tratamientos: edit price ---------------- */
    await step(cdp, "tratamientos-editar", async () => {
        await setValue(cdp, "#filtro-tratamiento", TREATMENT.codigo);
        await cdp.waitFor(
            "return document.querySelectorAll('#lista-tratamientos tbody tr').length === 1;",
            15000,
            "tratamiento filtrado antes de editar"
        );
        await clickInRow(cdp, "#lista-tratamientos", TREATMENT.codigo, ".btn-outline");
        await cdp.waitFor(
            "const panel = document.querySelector('#panel-tratamiento-form'); return Boolean(panel) && !panel.hidden;",
            10000,
            "formulario de edición de tratamiento"
        );
        const codigoCargado = await cdp.evaluate("return document.querySelector('#input-codigo').value;");
        assert(codigoCargado === TREATMENT.codigo, `El formulario no cargó el código ${TREATMENT.codigo} (${codigoCargado}).`);

        await setValue(cdp, "#input-precio", TREATMENT.precioEditado);
        await clearToasts(cdp);
        await submitForm(cdp, "#form-tratamiento");
        const outcome = await waitFormOutcome(cdp, {
            errorSelector: "#form-tratamiento-error",
            busySelector: "#btn-guardar-tratamiento",
            successNeedle: "Tratamiento actualizado correctamente"
        });
        assert(outcome.ok, `No se pudo editar el precio del tratamiento: ${outcome.error}`);

        // Re-open the form to prove the new price persisted (locale independent).
        await clickInRow(cdp, "#lista-tratamientos", TREATMENT.codigo, ".btn-outline");
        await cdp.waitFor(
            "const panel = document.querySelector('#panel-tratamiento-form'); return Boolean(panel) && !panel.hidden;",
            10000,
            "formulario reabierto"
        );
        const precioPersistido = await cdp.evaluate("return document.querySelector('#input-precio').value;");
        assert(
            Number(precioPersistido) === Number(TREATMENT.precioEditado),
            `El precio editado no persistió: se esperaba ${TREATMENT.precioEditado} y quedó ${precioPersistido}.`
        );
        await click(cdp, "#btn-cancelar-tratamiento");

        return {
            toast: outcome.toast,
            precioPersistido,
            putOk: findCalls(cdp, { method: "PUT", url: "/api/tratamientos/", status: 200 }).length > 0
        };
    });

    /* ---------------- 4c. Duplicate code surfaces the 409 message ---------------- */
    await step(cdp, "tratamientos-codigo-duplicado", async () => {
        await click(cdp, "#btn-nuevo-tratamiento");
        await cdp.waitFor(
            "const panel = document.querySelector('#panel-tratamiento-form'); return Boolean(panel) && !panel.hidden;",
            10000,
            "formulario de alta de tratamiento"
        );
        await setValue(cdp, "#input-codigo", TREATMENT.codigo);
        await setValue(cdp, "#input-nombre", `${TREATMENT.nombre} duplicado`);
        await setValue(cdp, "#input-precio", "99");
        await setValue(cdp, "#input-duracion", "15");

        await clearToasts(cdp);
        await submitForm(cdp, "#form-tratamiento");
        const outcome = await waitFormOutcome(cdp, {
            errorSelector: "#form-tratamiento-error",
            busySelector: "#btn-guardar-tratamiento",
            successNeedle: "creado correctamente"
        });
        assert(!outcome.ok, "El alta con código duplicado fue aceptada: el backend debía responder 409.");
        assert(
            /ya existe un tratamiento con el codigo/i.test(outcome.error) && outcome.error.includes(TREATMENT.codigo),
            `El formulario no mostró el mensaje de código duplicado: "${outcome.error}".`
        );
        assert(
            findCalls(cdp, { method: "POST", url: "/api/tratamientos", status: 409 }).length > 0,
            "El backend no respondió 409 al código duplicado."
        );
        const formVisible = await isVisible(cdp, "#panel-tratamiento-form");
        assert(formVisible, "El formulario se cerró pese al error de código duplicado.");
        await click(cdp, "#btn-cancelar-tratamiento");

        return {
            mensajeMostrado: outcome.error,
            status: 409,
            formKeptOpen: formVisible,
            postConflict: true
        };
    }, { expectedHttp: [{ url: "/api/tratamientos", status: 409 }] });

    /* ---------------- 5a. Agenda: schedule ---------------- */
    const agenda = await step(cdp, "agenda-crear", async () => {
        await openModule(cdp, "agenda", "Agenda de citas");
        await click(cdp, "#btn-nueva-cita");
        await cdp.waitFor(
            "const panel = document.querySelector('#panel-cita-form'); return Boolean(panel) && !panel.hidden;",
            10000,
            "formulario de cita"
        );
        await cdp.waitFor(
            "return document.querySelectorAll('#cita-dentista option').length > 1"
            + " && document.querySelectorAll('#cita-tratamiento option').length > 1;",
            20000,
            "catálogos del formulario de cita"
        );

        await setValue(cdp, "#cita-paciente", pacienteId);
        const tratamientoId = await selectOptionByText(cdp, "#cita-tratamiento", STAMP);
        const dentista = await selectFirstOption(cdp, "#cita-dentista");
        await setValue(cdp, "#cita-motivo", CITA_MOTIVO);

        const baseMinute = (Math.floor(Date.now() / 60000) % 50) + 5;
        const candidateSlots = [
            futureSlot(9, baseMinute),
            futureSlot(10, (baseMinute + 7) % 60),
            futureSlot(12, (baseMinute + 17) % 60),
            futureSlot(15, (baseMinute + 23) % 60)
        ];

        let created = null;
        let lastError = "";
        for (const slot of candidateSlots) {
            await setValue(cdp, "#cita-fecha-hora", slot);
            await clearToasts(cdp);
            await submitForm(cdp, "#form-cita");
            const outcome = await waitFormOutcome(cdp, {
                errorSelector: "#form-cita-error",
                busySelector: "#btn-guardar-cita",
                successNeedle: "Cita programada para el"
            });
            if (outcome.ok) {
                created = { slot, toast: outcome.toast };
                break;
            }
            lastError = outcome.error;
            // Only a dentist overlap is retryable: any other error must surface.
            assert(/horario/i.test(outcome.error), `No se pudo programar la cita: ${outcome.error}`);
        }
        assert(created, `Ningún horario candidato quedó libre para el odontólogo. Último error: ${lastError}`);

        await cdp.waitFor(
            `return (document.querySelector('#lista-citas')?.textContent || '').includes(${sel(CITA_MOTIVO)});`,
            20000,
            "cita en la agenda"
        );
        const rows = await rowsOf(cdp, "#lista-citas");
        const row = rows.find((item) => item.text.includes(CITA_MOTIVO));
        assert(row, "La cita creada no aparece en la agenda del período.");
        assert(row.badges.includes("Programada"), `La cita no nació en estado Programada: ${row.badges.join(", ")}.`);
        const citaId = row.actions.find((action) => action.accion === "reprogramar")?.id || null;
        assert(citaId, "No se pudo leer el id de la cita creada desde la fila de la agenda.");

        return {
            citaId,
            fechaHora: created.slot,
            tratamientoId,
            dentista,
            motivo: CITA_MOTIVO,
            estado: row.badges[0] || "",
            toast: created.toast,
            postOk: findCalls(cdp, { method: "POST", url: "/api/citas", status: 201 }).length > 0
        };
    });

    /* ---------------- 5b. Agenda: reschedule ---------------- */
    const reprogramada = await step(cdp, "agenda-reprogramar", async () => {
        await click(cdp, `#lista-citas [data-accion="reprogramar"][data-id="${agenda.citaId}"]`);
        await cdp.waitFor(
            "const panel = document.querySelector('#panel-reprogramar-form'); return Boolean(panel) && !panel.hidden;",
            10000,
            "formulario de reprogramación"
        );

        const baseMinute = (Math.floor(Date.now() / 60000) % 45) + 11;
        const candidateSlots = [futureSlot(16, baseMinute), futureSlot(17, (baseMinute + 9) % 60)];
        let done = null;
        let lastError = "";
        for (const slot of candidateSlots) {
            await setValue(cdp, "#reprogramar-fecha-hora", slot);
            await clearToasts(cdp);
            await submitForm(cdp, "#form-reprogramar");
            const outcome = await waitFormOutcome(cdp, {
                errorSelector: "#form-reprogramar-error",
                busySelector: "#btn-guardar-reprogramacion",
                successNeedle: "Cita reprogramada para el"
            });
            if (outcome.ok) {
                done = { slot, toast: outcome.toast };
                break;
            }
            lastError = outcome.error;
            assert(/horario/i.test(outcome.error), `No se pudo reprogramar la cita: ${outcome.error}`);
        }
        assert(done, `Ningún horario candidato quedó libre para reprogramar. Último error: ${lastError}`);

        await cdp.waitFor(
            `return Array.from(document.querySelectorAll('#lista-citas tbody tr'))
                .filter(tr => tr.textContent.includes(${sel(CITA_MOTIVO)})).length === 2;`,
            25000,
            "citas original y reprogramada en la agenda"
        );
        const rows = (await rowsOf(cdp, "#lista-citas")).filter((row) => row.text.includes(CITA_MOTIVO));
        assert(rows.length === 2, `Se esperaban 2 filas (original + nueva) con el motivo, y hay ${rows.length}.`);
        const original = rows.find((row) => row.actions.some((action) => action.id === agenda.citaId)
            && row.badges.includes("Reprogramada"));
        const nueva = rows.find((row) => row.badges.includes("Programada"));
        assert(original, "La cita original no quedó marcada como Reprogramada.");
        assert(nueva, "No se creó la nueva cita Programada tras reprogramar.");
        const nuevaId = nueva.actions.find((action) => action.accion === "cancelar")?.id || null;
        assert(nuevaId, "No se pudo leer el id de la cita reprogramada.");

        return {
            originalId: agenda.citaId,
            originalEstado: "Reprogramada",
            nuevaId,
            nuevaFechaHora: done.slot,
            nuevaEstado: "Programada",
            toast: done.toast,
            patchOk: findCalls(cdp, { method: "PATCH", url: "/reprogramar", status: 200 }).length > 0
        };
    });

    /* ---------------- 5c. Agenda: cancel (modal confirmation) ---------------- */
    await step(cdp, "agenda-cancelar", async () => {
        await click(cdp, `#lista-citas [data-accion="cancelar"][data-id="${reprogramada.nuevaId}"]`);
        const message = await waitModal(cdp);
        assert(
            message.includes("Cancelar la cita") || message.includes("¿Cancelar la cita"),
            `El diálogo de confirmación no es el de cancelación de cita: "${message}".`
        );

        await clearToasts(cdp);
        await confirmModal(cdp);
        const toast = await waitToast(cdp, "Cita cancelada correctamente");
        await cdp.waitFor(
            `return Array.from(document.querySelectorAll('#lista-citas tbody tr'))
                .some(tr => tr.textContent.includes(${sel(CITA_MOTIVO)}) && tr.textContent.includes('Cancelada'));`,
            25000,
            "estado Cancelada en la agenda"
        );
        const rows = (await rowsOf(cdp, "#lista-citas")).filter((row) => row.text.includes(CITA_MOTIVO));
        const cancelada = rows.find((row) => row.badges.includes("Cancelada"));
        assert(cancelada, "La cita reprogramada no quedó en estado Cancelada.");

        return {
            modalMensaje: message,
            estado: "Cancelada",
            toast,
            patchOk: findCalls(cdp, { method: "PATCH", url: "/cancelar", status: 204 }).length > 0
        };
    });

    /* ---------------- 6. Realizados: register from the patient history ---------------- */
    await step(cdp, "realizado-registrar", async () => {
        await clickInRow(cdp, "#lista-citas", CITA_MOTIVO, 'a[href*="/historial"]');
        await cdp.waitFor(
            "return document.querySelectorAll('#historial-contenido .data-table').length === 2;",
            20000,
            "historial del paciente desde la agenda"
        );

        await click(cdp, "#btn-registrar-realizado");
        await cdp.waitFor(
            "const panel = document.querySelector('#panel-realizado-form'); return Boolean(panel) && !panel.hidden;",
            10000,
            "panel de tratamiento realizado"
        );
        await cdp.waitFor(
            "return document.querySelectorAll('#realizado-tratamiento option').length > 1"
            + " && document.querySelectorAll('#realizado-dentista option').length > 1;",
            20000,
            "catálogos del panel de realizado"
        );

        await setValue(cdp, "#realizado-tratamiento", agenda.tratamientoId);
        await setValue(cdp, "#realizado-dentista", agenda.dentista.value);
        await setValue(cdp, "#realizado-fecha", localDateTimeInput(new Date(Date.now() - 5 * 60000)));
        await setValue(cdp, "#realizado-detalle", DETALLE_REALIZADO);
        await setValue(cdp, "#realizado-observaciones", "Registrado por el smoke de navegador.");

        await clearToasts(cdp);
        await submitForm(cdp, "#form-realizado");
        const outcome = await waitFormOutcome(cdp, {
            errorSelector: "#form-realizado-error",
            busySelector: "#btn-guardar-realizado",
            successNeedle: "Tratamiento registrado por"
        });
        assert(outcome.ok, `No se pudo registrar el tratamiento realizado: ${outcome.error}`);

        await cdp.waitFor(
            `return (document.querySelector('#historial-contenido')?.textContent || '').includes(${sel(DETALLE_REALIZADO)});`,
            25000,
            "tratamiento realizado en el historial"
        );
        const rows = await rowsOf(cdp, "#historial-contenido .data-table");
        const row = rows.find((item) => item.text.includes(DETALLE_REALIZADO));
        assert(row, "El tratamiento realizado no aparece en la tabla del historial.");
        const costo = parseMoney(row.cells[row.cells.length - 1]);
        assert(
            costo === Number(TREATMENT.precioEditado),
            `El costo congelado del realizado no coincide: se esperaba ${TREATMENT.precioEditado} y llegó ${costo}.`
        );

        return {
            detalle: DETALLE_REALIZADO,
            costo,
            estadoTabla: await countOf(cdp, "#historial-contenido .data-table"),
            toast: outcome.toast,
            postOk: findCalls(cdp, { method: "POST", url: "/api/tratamientos-realizados", status: 201 }).length > 0
        };
    });

    /* ---------------- 7a. Facturacion: emit ---------------- */
    const factura = await step(cdp, "factura-emitir", async () => {
        await openModule(cdp, "facturacion", "Facturación y pagos");
        await cdp.waitFor(
            "return document.querySelectorAll('#factura-paciente option').length > 1;",
            20000,
            "pacientes del formulario de emisión"
        );

        await setValue(cdp, "#factura-paciente", pacienteId);
        await cdp.waitFor(
            "return document.querySelectorAll('#factura-candidatos .check-row').length > 0;",
            20000,
            "tratamientos facturables del paciente"
        );

        const candidato = await cdp.evaluate(`
            const needle = ${JSON.stringify(DETALLE_REALIZADO)};
            const rows = Array.from(document.querySelectorAll('#factura-candidatos .check-row'));
            const row = rows.find(r => r.textContent.includes(needle));
            if (!row) return null;
            const box = row.querySelector('input[type="checkbox"]');
            if (!box) return null;
            if (!box.checked) box.click();
            return { realizadoId: box.dataset.realizadoId, checked: box.checked };
        `);
        assert(candidato, "No se encontró el tratamiento realizado como candidato facturable.");
        assert(candidato.checked === true, "No se pudo seleccionar el tratamiento a facturar.");

        const totalBadge = await textOf(cdp, "#form-factura .form-actions .badge");
        assert(/1 tratamiento/.test(totalBadge), `El total de la emisión no refleja la selección: "${totalBadge}".`);

        await setValue(cdp, "#factura-observaciones", "Emitida por el smoke de navegador.");
        await clearToasts(cdp);
        await submitForm(cdp, "#form-factura");
        const outcome = await waitFormOutcome(cdp, {
            errorSelector: "#form-factura-error",
            busySelector: "#btn-emitir-factura",
            successNeedle: "emitido por"
        });
        assert(outcome.ok, `No se pudo emitir el comprobante: ${outcome.error}`);

        await setValue(cdp, "#filtro-factura", STAMP);
        await cdp.waitFor(
            "return document.querySelectorAll('#lista-facturas tbody tr').length === 1;",
            20000,
            "comprobante filtrado en el listado"
        );
        const row = (await rowsOf(cdp, "#lista-facturas"))[0];
        const numero = row.cells[0];
        const estado = row.badges[0] || "";
        const total = parseMoney(row.cells[4]);
        const pagado = parseMoney(row.cells[5]);
        const saldo = parseMoney(row.cells[6]);

        assert(/^F-\d{6}$/.test(numero), `El número de comprobante no tiene el formato correlativo esperado: "${numero}".`);
        assert(estado === "Emitida", `El comprobante no nació en estado Emitida: "${estado}".`);
        assert(total === Number(TREATMENT.precioEditado), `El total del comprobante no coincide: ${total}.`);
        assert(pagado === 0, `El comprobante recién emitido ya registra pagos: ${pagado}.`);
        assert(saldo === total, `El saldo inicial no iguala el total: ${saldo} vs ${total}.`);

        const facturaId = row.actions.find((action) => action.accion === "detalle")?.id || null;
        assert(facturaId, "No se pudo leer el id del comprobante emitido.");

        return {
            facturaId,
            numero,
            estado,
            total,
            pagado,
            saldo,
            realizadoId: String(candidato.realizadoId),
            totalBadge,
            toast: outcome.toast,
            postOk: findCalls(cdp, { method: "POST", url: "/api/facturas", status: 201 }).length > 0
        };
    });

    /** Reads the invoice detail panel (labels + amounts) and its payment table. */
    async function readDetalle(cdp) {
        const detail = await cdp.evaluate(`
            const root = document.querySelector('#detalle-factura .detail-list');
            if (!root) return null;
            const rows = Array.from(root.querySelectorAll('.detail-row'));
            const fields = {};
            rows.forEach(row => {
                const label = (row.querySelector('dt')?.textContent || '').trim();
                const value = (row.querySelector('dd')?.textContent || '').trim();
                if (label) fields[label] = value;
            });
            const tables = Array.from(document.querySelectorAll('#detalle-factura .data-table'));
            const lineas = tables[0]
                ? Array.from(tables[0].querySelectorAll('tbody tr')).map(tr => tr.textContent.trim())
                : [];
            const pagos = tables[1]
                ? Array.from(tables[1].querySelectorAll('tbody tr')).map(tr => tr.textContent.trim())
                : [];
            const info = document.querySelector('#detalle-factura .alert-box.info');
            return {
                fields,
                estadoBadge: (document.querySelector('#panel-detalle-factura .card-header-actions .badge')?.textContent || '').trim(),
                lineas,
                pagos,
                aviso: info ? info.textContent.trim() : ""
            };
        `);
        assert(detail, "El panel de detalle del comprobante no está montado.");
        return detail;
    }

    /* ---------------- 7b. Facturacion: partial payment ---------------- */
    await step(cdp, "factura-pago-parcial", async () => {
        await click(cdp, `#lista-facturas [data-accion="detalle"][data-id="${factura.facturaId}"]`);
        await cdp.waitFor(
            "const panel = document.querySelector('#panel-detalle-factura'); return Boolean(panel) && !panel.hidden;",
            15000,
            "panel de detalle del comprobante"
        );
        await cdp.waitFor(
            "return Boolean(document.querySelector('#form-pago'));",
            20000,
            "formulario de pago"
        );

        const inicial = await readDetalle(cdp);
        assert(inicial.estadoBadge === "Emitida", `El detalle no arranca en Emitida: "${inicial.estadoBadge}".`);
        assert(parseMoney(inicial.fields.Saldo) === factura.saldo, `El saldo del detalle no coincide con el listado: "${inicial.fields.Saldo}".`);
        assert(
            inicial.lineas.some((linea) => linea.includes(`#${factura.realizadoId}`)),
            "La línea del comprobante no referencia el tratamiento realizado facturado."
        );
        assert(inicial.pagos.length === 1 && inicial.pagos[0].includes("Todavía no se registraron pagos"),
            "El comprobante recién emitido ya muestra pagos registrados.");

        const pagoParcial = 200;
        await setValue(cdp, "#pago-monto", String(pagoParcial));
        await setValue(cdp, "#pago-metodo", "1");
        await setValue(cdp, "#pago-referencia", PAGO_REFERENCIA);
        await clearToasts(cdp);
        await submitForm(cdp, "#form-pago");
        const outcome = await waitFormOutcome(cdp, {
            errorSelector: "#form-pago-error",
            busySelector: "#btn-registrar-pago",
            successNeedle: "Pago de"
        });
        assert(outcome.ok, `No se pudo registrar el pago parcial: ${outcome.error}`);

        await cdp.waitFor(
            "return (document.querySelector('#panel-detalle-factura .card-header-actions .badge')?.textContent || '').trim() === 'Pago Parcial';",
            25000,
            "estado Pago Parcial"
        );
        const parcial = await readDetalle(cdp);
        const saldo = parseMoney(parcial.fields.Saldo);
        const pagado = parseMoney(parcial.fields.Pagado);
        assert(saldo === factura.total - pagoParcial, `El saldo tras el pago parcial es ${saldo}.`);
        assert(pagado === pagoParcial, `El total pagado tras el pago parcial es ${pagado}.`);
        assert(parcial.pagos.some((linea) => linea.includes(PAGO_REFERENCIA)), "El pago parcial no aparece en la tabla de pagos.");

        return {
            monto: pagoParcial,
            estado: "Pago Parcial",
            saldo,
            pagado,
            referencia: PAGO_REFERENCIA,
            toast: outcome.toast,
            postOk: findCalls(cdp, { method: "POST", url: `/api/facturas/${factura.facturaId}/pagos`, status: 201 }).length > 0
        };
    });

    /* ---------------- 7c. Facturacion: remaining payment -> PAGADA ---------------- */
    await step(cdp, "factura-pago-total", async () => {
        const antes = await readDetalle(cdp);
        const saldoPendiente = parseMoney(antes.fields.Saldo);
        assert(saldoPendiente > 0, `No queda saldo por cobrar antes del segundo pago: ${antes.fields.Saldo}.`);

        await setValue(cdp, "#pago-monto", String(saldoPendiente));
        await setValue(cdp, "#pago-metodo", "2");
        await setValue(cdp, "#pago-referencia", `${PAGO_REFERENCIA}-2`);
        await clearToasts(cdp);
        await submitForm(cdp, "#form-pago");
        const outcome = await waitFormOutcome(cdp, {
            errorSelector: "#form-pago-error",
            busySelector: "#btn-registrar-pago",
            successNeedle: "Pago de"
        });
        assert(outcome.ok, `No se pudo registrar el pago final: ${outcome.error}`);

        await cdp.waitFor(
            "return (document.querySelector('#panel-detalle-factura .card-header-actions .badge')?.textContent || '').trim() === 'Pagada';",
            25000,
            "estado Pagada"
        );
        const final = await readDetalle(cdp);
        const saldo = parseMoney(final.fields.Saldo);
        const pagado = parseMoney(final.fields.Pagado);
        assert(saldo === 0, `El comprobante quedó como Pagada con saldo ${saldo}.`);
        assert(pagado === factura.total, `El total pagado no alcanzó el total del comprobante: ${pagado} vs ${factura.total}.`);
        assert(/no queda saldo por cobrar/i.test(final.aviso),
            `El detalle no anuncia que no queda saldo por cobrar: "${final.aviso}".`);
        assert(final.pagos.length === 2, `Se esperaban 2 pagos registrados y hay ${final.pagos.length}.`);

        // The list row must agree with the detail panel.
        await setValue(cdp, "#filtro-factura", STAMP);
        await cdp.waitFor(
            "return document.querySelectorAll('#lista-facturas tbody tr').length === 1;",
            20000,
            "comprobante pagado en el listado"
        );
        const row = (await rowsOf(cdp, "#lista-facturas"))[0];

        return {
            monto: saldoPendiente,
            estado: "Pagada",
            saldo,
            pagado,
            pagosRegistrados: final.pagos.length,
            aviso: final.aviso,
            listadoEstado: row.badges[0] || "",
            listadoSaldo: row.cells[6],
            toast: outcome.toast
        };
    });

    /* ---------------- 7d. Facturacion: download the PDF ---------------- */
    await step(cdp, "factura-pdf", async () => {
        // The module fetches the PDF with the session token and then hands the
        // blob to the browser: inspect the real HTTP response instead of
        // trusting the download prompt (which headless Chrome cannot show).
        let resolvePdf;
        const pdfPromise = new Promise((resolve) => { resolvePdf = resolve; });
        let target = null;

        const offResponse = cdp.on("Network.responseReceived", (params) => {
            const url = params.response?.url || "";
            if (target) return;
            if (!url.includes("/api/facturas/") || !url.includes("/pdf")) return;
            const headers = params.response.headers || {};
            const contentType = Object.entries(headers)
                .find(([name]) => name.toLowerCase() === "content-type")?.[1]
                || params.response.mimeType
                || "";
            target = {
                requestId: params.requestId,
                status: params.response.status,
                contentType
            };
        });

        const offFinished = cdp.on("Network.loadingFinished", async (params) => {
            if (!target || params.requestId !== target.requestId) return;
            try {
                const body = await cdp.send("Network.getResponseBody", { requestId: params.requestId });
                const buffer = Buffer.from(body.body, body.base64Encoded ? "base64" : "utf8");
                resolvePdf({
                    ...target,
                    bytes: buffer.length,
                    magic: buffer.subarray(0, 5).toString("latin1"),
                    buffer
                });
            } catch (error) {
                resolvePdf({ ...target, error: error.message });
            }
        });

        const offFailed = cdp.on("Network.loadingFailed", (params) => {
            if (!target || params.requestId !== target.requestId) return;
            resolvePdf({ ...target, error: params.errorText || "request fallida" });
        });

        try {
            const clicked = await cdp.evaluate(`
                const root = document.querySelector('#detalle-factura');
                if (!root) return false;
                const button = Array.from(root.querySelectorAll('button'))
                    .find(node => node.textContent.includes('Descargar PDF'));
                if (!button) return false;
                button.click();
                return true;
            `);
            assert(clicked, "No se encontró el botón 'Descargar PDF' del detalle del comprobante.");

            const pdf = await withTimeout(pdfPromise, 30000, "el comprobante PDF");
            assert(!pdf.error, `La descarga del comprobante falló: ${pdf.error}`);
            assert(pdf.status === 200, `El comprobante respondió HTTP ${pdf.status} en lugar de 200.`);
            assert(/application\/pdf/i.test(pdf.contentType),
                `El comprobante no se sirve como application/pdf: "${pdf.contentType}".`);
            assert(pdf.magic === "%PDF-", `El cuerpo del comprobante no es un PDF (firma "${pdf.magic}").`);
            assert(pdf.bytes > 1000, `El PDF descargado es sospechosamente chico: ${pdf.bytes} bytes.`);

            const file = `factura-${factura.numero}.pdf`;
            writeFileSync(join(OUT, file), pdf.buffer);
            report.artifacts.push(file);

            const toast = await waitToast(cdp, `Comprobante ${factura.numero} descargado`);
            return {
                numero: factura.numero,
                contentType: pdf.contentType,
                status: pdf.status,
                bytes: pdf.bytes,
                magic: pdf.magic,
                artifact: file,
                toast
            };
        } finally {
            offResponse();
            offFinished();
            offFailed();
        }
    });

    /* ---------------- 3d. Cleanup: logical delete of the created patient ---------------- */
    await step(cdp, "cleanup-paciente", async () => {
        await openModule(cdp, "pacientes", "Pacientes");
        await setValue(cdp, "#filtro-paciente", PATIENT.email);
        await click(cdp, "#btn-buscar-paciente");
        await cdp.waitFor(
            "return document.querySelectorAll('#lista-pacientes tbody tr').length === 1;",
            20000,
            "paciente a dar de baja"
        );

        await clickInRow(cdp, "#lista-pacientes", PATIENT.email, ".btn-danger");
        const message = await waitModal(cdp);
        assert(/dar de baja/i.test(message), `El diálogo de baja no es el esperado: "${message}".`);

        await clearToasts(cdp);
        await confirmModal(cdp);
        const toast = await waitToast(cdp, "Paciente dado de baja");
        await cdp.waitFor(
            `return (document.querySelector('#lista-pacientes')?.textContent || '').includes('No se encontraron pacientes');`,
            20000,
            "paciente fuera del listado"
        );

        assert(
            findCalls(cdp, { method: "DELETE", url: `/api/pacientes/${pacienteId}`, status: 204 }).length > 0,
            `La baja lógica no llegó a DELETE /api/pacientes/${pacienteId} (204).`
        );

        // The clinical history must survive the logical delete (S2 / S3).
        await cdp.evaluate(`window.location.hash = '#/pacientes/${pacienteId}/historial'; return true;`);
        await cdp.waitFor(
            "return document.querySelectorAll('#historial-contenido .data-table').length === 2;",
            25000,
            "historial del paciente dado de baja"
        );
        const historial = await textOf(cdp, "#historial-contenido");
        const encabezado = await textOf(cdp, "#app-view .page-header");
        assert(/dado de baja/i.test(encabezado), `La cabecera no informa la baja del paciente: "${encabezado}".`);
        assert(historial.includes(DETALLE_REALIZADO),
            "El historial clínico perdió los tratamientos realizados tras la baja lógica.");

        report.cleanup = {
            paciente: {
                id: pacienteId,
                email: PATIENT.email,
                accion: "baja lógica vía UI (DELETE /api/pacientes/{id} -> 204)",
                historialConservado: true
            },
            tratamiento: {
                codigo: TREATMENT.codigo,
                nota: "queda activo en el catálogo: la API de S7 solo expone alta y edición, no borrado"
            },
            citas: [
                { id: Number(agenda.citaId), estado: "REPROGRAMADA (cita original)" },
                { id: Number(reprogramada.nuevaId), estado: "CANCELADA" }
            ],
            realizado: {
                nota: "se conserva: el historial clínico sobrevive a la baja del paciente (S2, S3)"
            },
            factura: {
                numero: factura.numero,
                estado: "PAGADA",
                nota: "comprobante inmutable; el frontend no expone anulación"
            }
        };

        return {
            deletedPatient: true,
            modalMensaje: message,
            toast,
            removedFromList: true,
            historyPreserved: true,
            historyHeading: encabezado
        };
    }, { expectedHttp: [{ url: `/api/pacientes/${pacienteId}`, status: 404 }] });

    report.flow = {
        "S1 paciente": ["pacientes-crear", "pacientes-buscar-historial"],
        "S2 edicion y baja": ["paciente-editar", "cleanup-paciente"],
        "S3 historial": ["pacientes-buscar-historial", "realizado-registrar"],
        "S4 agenda": ["agenda-crear"],
        "S6 reprogramar y cancelar": ["agenda-reprogramar", "agenda-cancelar"],
        "S7 catalogo": ["tratamientos-crear", "tratamientos-editar", "tratamientos-codigo-duplicado"],
        "S8 realizados": ["realizado-registrar"],
        "S9 facturacion": ["factura-emitir", "factura-pdf"],
        "S10 pagos": ["factura-pago-parcial", "factura-pago-total"],
        "S13/S16 panel y sesion": ["prepare-session", "login-admin", "reload-session", "dashboard"],
        "ruta reservada": ["recordatorios-reservado"]
    };
    report.notes.push(
        "Cada selector del escenario está fichado en UI_CONTRACT (selector -> archivo) y validado por el preflight estático antes de lanzar Chrome.",
        "Se filtran del informe el 404 de favicon.ico y las descargas blob: del navegador: son ruido del navegador, no fallas de la aplicación.",
        "Los pasos de agenda reintentan con otro horario sólo ante un 409 de solapamiento del odontólogo; cualquier otro error corta la corrida."
    );
    report.ok = true;
} catch (error) {
    report.ok = false;
    report.fatal = error.message;
} finally {
    try {
        spawn("taskkill", ["/F", "/T", "/PID", String(chrome.pid)], { stdio: "ignore" });
    } catch {
        /* already gone */
    }
    chrome.kill();
    report.chromeStderr = chromeStderr.join("").split("\n").slice(0, 10);
    report.summary = {
        ok: report.ok === true,
        steps: report.steps.length,
        findings: report.findings.length,
        fatal: report.fatal || null
    };
    writeFileSync(join(OUT, "report.json"), JSON.stringify(report, null, 2));
    console.log(JSON.stringify({
        ok: report.summary.ok,
        base: report.base,
        steps: report.steps.map((entry) => entry.step),
        findings: report.summary.findings,
        fatal: report.summary.fatal,
        report: join(OUT, "report.json")
    }, null, 2));
    if (!report.ok) process.exitCode = 1;
}
