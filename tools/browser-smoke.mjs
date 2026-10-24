/* ============================================================================
   BROWSER SMOKE HARNESS — CLINICA DENTAL SPA (dependency-free, raw CDP)
   ----------------------------------------------------------------------------
   Drives a headless Chrome against the running stack and walks the delivered
   clinic application end to end FROM A CLEAN DATABASE (S23, S24): the scenario
   creates everything it needs through the UI and never asserts seed data.

   Scenario, in order (each step names the spec it proves):
     1.  prepare-session                 S13  sesión limpia, sin token viejo
     2.  login-admin                     S13, S22  login bootstrap + sidebar completo
     3.  dashboard-vacio                 S22, S23  panel con la base limpia
     4.  usuarios-crear-odontologo       S18, S20  alta del odontólogo que alimenta la agenda
     5.  usuarios-crear-recepcionista    S18  alta del personal de recepción
     6.  usuarios-baja-logica            S18  baja lógica (DELETE 204)
     7.  roles-matriz                    S18  matriz de permisos editable y roles base protegidos
     8.  agenda-odontologo-disponible    S20  el selector de la agenda sale del padrón
     9.  pacientes-crear                 S1   alta de paciente única por corrida
     10. paciente-editar                 S2   edición de los datos del paciente (PUT 200)
     11. tratamientos-crear              S7   catálogo bootstrap + alta, edición y 409 de código
     12. agenda-crear                    S4   cita A con el odontólogo real y el tratamiento creado
     13. agenda-reprogramar              S6   cita A reprogramada (PATCH 200) -> REPROGRAMADA
     14. agenda-cancelar                 S6   cita B aparte, creada y cancelada -> CANCELADA
     15. realizado-registrar             S3, S8  procedimiento desde el historial clínico
     16. factura-emitir                  S9   emisión con numeración correlativa
     17. factura-pago-parcial            S10  pago menor al saldo -> PAGO_PARCIAL
     18. factura-pago-total              S10  pago del saldo restante -> PAGADA
     19. factura-pdf                     S9   PDF real capturado desde window.fetch
     20. auditoria-ver                   S19  bitácora, filtros, paginación y detalle
     21. reportes-ver                    S21  ingresos, producción y ocupación + rango inválido
     22. recordatorios-vacio             S5, S22  bitácora sin datos semilla + despacho
     23. cleanup-paciente                S2, S3  baja lógica del paciente, historial intacto

   S6 usa DOS citas a propósito: la cita A se reprograma y se conserva vigente
   (PROGRAMADA como cita nueva), y la cancelación se ejerce sobre una cita B
   creada para eso, para que los pasos posteriores (realizado, reportes y la
   bitácora de recordatorios) sigan teniendo una cita vigente del run.

   Every selector used here is listed in UI_CONTRACT below with the frontend file
   that defines it and the exact snippet the static preflight looks for, so a
   changed UI fails fast instead of silently passing.

   Usage:
     docker compose up -d                            # stack must be reachable
     node tools/browser-smoke.mjs                    # full scenario
     node tools/browser-smoke.mjs --check-selectors   # preflight only (no Chrome)

   Environment:
     BASE_URL             front end origin                (default http://localhost)
     SMOKE_ADMIN_USER     bootstrap administrator user    (default admin)
     SMOKE_ADMIN_PASSWORD bootstrap administrator password (default cambiar-esta-clave)
     CHROME_BIN           absolute path to chrome         (default: Windows Chrome install)
     CDP_PORT             remote debugging port           (default 9333, not the MCP's 9222)
     OUT_DIR              screenshots and report.json     (default tools/artifacts)

   The administrator credentials must match CLINICA_ADMIN_USERNAME /
   CLINICA_ADMIN_PASSWORD of the stack (see docs/DESPLIEGUE.md, section 2). They
   are never hardcoded here: the defaults above are the documented bootstrap
   values, and the environment always wins.

   The run is idempotent and self-cleaning: users, patients, treatments, citas,
   receipts and payments get names/codes/emails stamped with a per-run id so
   repeated runs never collide, the throwaway user is deleted, and the patient is
   logically deleted at the end. Leftovers that the API deliberately cannot
   delete (facturas, pagos, realizados, citas, recordatorios) are reported in
   report.cleanup with the reason.

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

/* Bootstrap administrator (S23): never hardcoded inline, always overridable. */
const ADMIN_USER = process.env.SMOKE_ADMIN_USER || "admin";
const ADMIN_PASSWORD = process.env.SMOKE_ADMIN_PASSWORD || "cambiar-esta-clave";

/* ----------------------------------------------------------------------------
   SIDEBAR esperado para el administrador (S22)
   ----------------------------------------------------------------------------
   El escenario compara esta lista, en orden, contra #sidebar-menu. Un ítem de
   más o de menos es un hallazgo: el sidebar es el contrato de navegación.
   -------------------------------------------------------------------------- */
const SIDEBAR_ADMIN = [
    { grupo: "General", label: "Panel general", routeId: "dashboard" },
    { grupo: "Atención clínica", label: "Pacientes", routeId: "pacientes" },
    { grupo: "Atención clínica", label: "Agenda de citas", routeId: "agenda" },
    { grupo: "Atención clínica", label: "Catálogo de tratamientos", routeId: "tratamientos" },
    { grupo: "Administración", label: "Facturación y pagos", routeId: "facturacion" },
    { grupo: "Administración", label: "Recordatorios", routeId: "recordatorios" },
    { grupo: "Administración", label: "Reportes", routeId: "reportes" },
    { grupo: "Administración", label: "Usuarios y roles", routeId: "usuarios" },
    { grupo: "Administración", label: "Auditoría", routeId: "auditoria" },
    { grupo: "Mi cuenta", label: "Mi cuenta", routeId: "cuenta" }
];

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

    // --- Session storage, endpoints, roles, moneda: js/config.js ---
    { selector: "localStorage[clinica.auth.token]", file: "frontend/js/config.js", marker: 'TOKEN: "clinica.auth.token"' },
    { selector: "localStorage[clinica.auth.user]", file: "frontend/js/config.js", marker: 'USER: "clinica.auth.user"' },
    { selector: "CONFIG.CURRENCY (moneda con la que se formatean los montos)", file: "frontend/js/config.js", marker: 'CURRENCY: "USD"' },
    { selector: "CONFIG.LOCALE (formato regional de los montos)", file: "frontend/js/config.js", marker: 'LOCALE: "es-ES"' },
    { selector: "CONFIG.ESTADOS.RECORDATORIO", file: "frontend/js/config.js", marker: "RECORDATORIO: [" },
    { selector: "GET /api/usuarios (padrón del personal)", file: "frontend/js/config.js", marker: "USUARIOS: `${API}/usuarios`" },
    { selector: "GET /api/roles + /api/permisos", file: "frontend/js/config.js", marker: "ROLES: `${API}/roles`" },
    { selector: "GET /api/auditoria", file: "frontend/js/config.js", marker: "AUDITORIA: `${API}/auditoria`" },
    { selector: "GET /api/reportes/{ingresos|produccion|ocupacion}", file: "frontend/js/config.js", marker: "REPORTES: `${API}/reportes`" },

    // --- Shell + sidebar agrupado: js/core/shell.js ---
    { selector: ".sidebar-link[data-route-id]", file: "frontend/js/core/shell.js", marker: 'class: "sidebar-link"' },
    { selector: ".sidebar-link[data-route-id]", file: "frontend/js/core/shell.js", marker: "dataset: { routeId: route.id }" },
    { selector: ".sidebar-group-label (grupos del sidebar, S22)", file: "frontend/js/core/shell.js", marker: 'class: "sidebar-group-label"' },
    { selector: ".sidebar-label (etiqueta del ítem)", file: "frontend/js/core/shell.js", marker: 'class: "sidebar-label"' },

    // --- Rutas entregadas: js/core/routes.js (S22: RUTAS_RESERVADAS vacío) ---
    { selector: "[data-route-id=\"dashboard\"]", file: "frontend/js/core/routes.js", marker: 'id: "dashboard"' },
    { selector: "[data-route-id=\"pacientes\"]", file: "frontend/js/core/routes.js", marker: 'id: "pacientes"' },
    { selector: "[data-route-id=\"agenda\"]", file: "frontend/js/core/routes.js", marker: 'id: "agenda"' },
    { selector: "[data-route-id=\"tratamientos\"]", file: "frontend/js/core/routes.js", marker: 'id: "tratamientos"' },
    { selector: "[data-route-id=\"facturacion\"]", file: "frontend/js/core/routes.js", marker: 'id: "facturacion"' },
    { selector: "[data-route-id=\"recordatorios\"]", file: "frontend/js/core/routes.js", marker: 'id: "recordatorios"' },
    { selector: "[data-route-id=\"reportes\"]", file: "frontend/js/core/routes.js", marker: 'id: "reportes"' },
    { selector: "[data-route-id=\"usuarios\"]", file: "frontend/js/core/routes.js", marker: 'id: "usuarios"' },
    { selector: "[data-route-id=\"auditoria\"]", file: "frontend/js/core/routes.js", marker: 'id: "auditoria"' },
    { selector: "[data-route-id=\"cuenta\"]", file: "frontend/js/core/routes.js", marker: 'id: "cuenta"' },

    // --- Primitivas de presentación compartidas: js/core/dom.js ---
    { selector: ".kpi-stat-card", file: "frontend/js/core/dom.js", marker: 'class: "kpi-stat-card"' },
    { selector: ".kpi-stat-label", file: "frontend/js/core/dom.js", marker: 'class: "kpi-stat-label"' },
    { selector: ".kpi-stat-value", file: "frontend/js/core/dom.js", marker: 'class: "kpi-stat-value"' },
    { selector: ".spinner", file: "frontend/js/core/dom.js", marker: 'class: "spinner"' },
    { selector: ".data-table", file: "frontend/js/core/dom.js", marker: 'class: "data-table"' },
    { selector: ".table-scroll", file: "frontend/js/core/dom.js", marker: 'class: "table-scroll"' },
    { selector: "tbody tr td[colspan] (mensaje de tabla vacía)", file: "frontend/js/core/dom.js", marker: "colspan: headers.length" },
    { selector: ".state-box (estado vacío)", file: "frontend/js/core/dom.js", marker: 'class: "state-box"' },
    { selector: ".state-box-error", file: "frontend/js/core/dom.js", marker: '"state-box state-box-error"' },
    { selector: ".badge.badge-success / .badge.badge-danger", file: "frontend/js/core/dom.js", marker: 'return "badge badge-success";' },
    { selector: ".detail-row", file: "frontend/js/core/dom.js", marker: 'class: "detail-row"' },
    { selector: ".detail-list", file: "frontend/js/modules/paciente-historial-module.js", marker: 'class: "detail-list detail-list-grid"' },
    { selector: "#detalle-factura .detail-list", file: "frontend/js/modules/facturacion-module.js", marker: 'el("dl", { class: "detail-list detail-list-grid" }' },
    { selector: ".page-header h2", file: "frontend/js/core/dom.js", marker: 'class: "page-header"' },

    // --- Avisos y confirmación (nunca window.confirm / window.alert) ---
    { selector: "#toast-container .toast", file: "frontend/js/services/notification-service.js", marker: 'container.id = "toast-container"' },
    { selector: "#toast-container .toast.toast-success", file: "frontend/js/services/notification-service.js", marker: "toast.className = `toast toast-${type}`;" },
    { selector: ".modal-overlay.active .modal-message", file: "frontend/js/services/notification-service.js", marker: 'text.className = "modal-message"' },
    { selector: ".modal-overlay.active .btn-danger", file: "frontend/js/services/notification-service.js", marker: "overlay.className = \"modal-overlay active\"" },
    { selector: ".modal-overlay.active .btn-danger", file: "frontend/js/services/notification-service.js", marker: 'confirmButton.className = "btn btn-danger"' },

    // --- Dashboard (S22: sin módulos en preparación): js/modules/dashboard-module.js ---
    { selector: "#app-view .kpi-container", file: "frontend/js/modules/dashboard-module.js", marker: 'el("div", { class: "kpi-container" }' },
    { selector: "emptyMessage \"Todavía no hay pacientes registrados.\"", file: "frontend/js/modules/dashboard-module.js", marker: "Todavía no hay pacientes registrados." },

    // --- Usuarios y roles (S18, S20): js/modules/usuarios-module.js ---
    { selector: "#tabs-usuarios-roles", file: "frontend/js/modules/usuarios-module.js", marker: 'id: "tabs-usuarios-roles"' },
    { selector: "#tab-usuarios", file: "frontend/js/modules/usuarios-module.js", marker: 'id: "tab-usuarios"' },
    { selector: "#tab-roles", file: "frontend/js/modules/usuarios-module.js", marker: 'id: "tab-roles"' },
    { selector: "#seccion-usuarios", file: "frontend/js/modules/usuarios-module.js", marker: 'id: "seccion-usuarios"' },
    { selector: "#seccion-roles", file: "frontend/js/modules/usuarios-module.js", marker: 'id: "seccion-roles"' },
    { selector: "#filtro-usuario-rol", file: "frontend/js/modules/usuarios-module.js", marker: 'id: "filtro-usuario-rol"' },
    { selector: "#filtro-usuario-estado", file: "frontend/js/modules/usuarios-module.js", marker: 'id: "filtro-usuario-estado"' },
    { selector: "#filtro-usuario-texto", file: "frontend/js/modules/usuarios-module.js", marker: 'id: "filtro-usuario-texto"' },
    { selector: "#btn-filtrar-usuarios", file: "frontend/js/modules/usuarios-module.js", marker: 'id: "btn-filtrar-usuarios"' },
    { selector: "#btn-odontologos-activos", file: "frontend/js/modules/usuarios-module.js", marker: 'id: "btn-odontologos-activos"' },
    { selector: "#btn-limpiar-filtros-usuarios", file: "frontend/js/modules/usuarios-module.js", marker: 'id: "btn-limpiar-filtros-usuarios"' },
    { selector: "#contador-usuarios", file: "frontend/js/modules/usuarios-module.js", marker: 'id: "contador-usuarios"' },
    { selector: "#btn-nuevo-usuario", file: "frontend/js/modules/usuarios-module.js", marker: 'id: "btn-nuevo-usuario"' },
    { selector: "#aviso-usuarios", file: "frontend/js/modules/usuarios-module.js", marker: 'id: "aviso-usuarios"' },
    { selector: "#lista-usuarios", file: "frontend/js/modules/usuarios-module.js", marker: 'id: "lista-usuarios"' },
    { selector: "#panel-usuario-form", file: "frontend/js/modules/usuarios-module.js", marker: 'id: "panel-usuario-form"' },
    { selector: "#form-usuario", file: "frontend/js/modules/usuarios-module.js", marker: 'id: "form-usuario"' },
    { selector: "#form-usuario-error", file: "frontend/js/modules/usuarios-module.js", marker: 'id: "form-usuario-error"' },
    { selector: "#input-usuario-username", file: "frontend/js/modules/usuarios-module.js", marker: 'id: "input-usuario-username"' },
    { selector: "#input-usuario-nombre", file: "frontend/js/modules/usuarios-module.js", marker: 'id: "input-usuario-nombre"' },
    { selector: "#input-usuario-email", file: "frontend/js/modules/usuarios-module.js", marker: 'id: "input-usuario-email"' },
    { selector: "#input-usuario-password", file: "frontend/js/modules/usuarios-module.js", marker: 'id: "input-usuario-password"' },
    { selector: "#select-usuario-rol", file: "frontend/js/modules/usuarios-module.js", marker: 'id: "select-usuario-rol"' },
    { selector: "#btn-guardar-usuario", file: "frontend/js/modules/usuarios-module.js", marker: 'id: "btn-guardar-usuario"' },
    { selector: "#btn-cancelar-usuario", file: "frontend/js/modules/usuarios-module.js", marker: 'id: "btn-cancelar-usuario"' },
    { selector: "[data-accion=\"editar-usuario\"]", file: "frontend/js/modules/usuarios-module.js", marker: 'dataset: { accion: "editar-usuario", id: usuario.id }' },
    { selector: "[data-accion=\"baja-usuario\"]", file: "frontend/js/modules/usuarios-module.js", marker: 'dataset: { accion: "baja-usuario", id: usuario.id }' },
    { selector: ".badge \"Disponible en agenda\" (odontólogo activo)", file: "frontend/js/modules/usuarios-module.js", marker: 'text: "Disponible en agenda"' },
    { selector: "#contador-roles", file: "frontend/js/modules/usuarios-module.js", marker: 'id: "contador-roles"' },
    { selector: "#btn-nuevo-rol", file: "frontend/js/modules/usuarios-module.js", marker: 'id: "btn-nuevo-rol"' },
    { selector: "#lista-roles", file: "frontend/js/modules/usuarios-module.js", marker: 'id: "lista-roles"' },
    { selector: "[data-accion=\"editar-rol\"]", file: "frontend/js/modules/usuarios-module.js", marker: 'dataset: { accion: "editar-rol", id: rol.id }' },
    { selector: "[data-accion=\"eliminar-rol\"]", file: "frontend/js/modules/usuarios-module.js", marker: 'dataset: { accion: "eliminar-rol", id: rol.id }' },
    { selector: "#panel-rol-permisos", file: "frontend/js/modules/usuarios-module.js", marker: 'id: "panel-rol-permisos"' },
    { selector: "#form-rol", file: "frontend/js/modules/usuarios-module.js", marker: 'id: "form-rol"' },
    { selector: "#form-rol-error", file: "frontend/js/modules/usuarios-module.js", marker: 'id: "form-rol-error"' },
    { selector: "#rol-base-aviso", file: "frontend/js/modules/usuarios-module.js", marker: 'id: "rol-base-aviso"' },
    { selector: "#input-rol-nombre", file: "frontend/js/modules/usuarios-module.js", marker: 'id: "input-rol-nombre"' },
    { selector: "#input-rol-descripcion", file: "frontend/js/modules/usuarios-module.js", marker: 'id: "input-rol-descripcion"' },
    { selector: "#contador-permisos-rol", file: "frontend/js/modules/usuarios-module.js", marker: 'id: "contador-permisos-rol"' },
    { selector: "#btn-permisos-todos", file: "frontend/js/modules/usuarios-module.js", marker: 'id: "btn-permisos-todos"' },
    { selector: "#btn-permisos-ninguno", file: "frontend/js/modules/usuarios-module.js", marker: 'id: "btn-permisos-ninguno"' },
    { selector: "#matriz-permisos", file: "frontend/js/modules/usuarios-module.js", marker: 'id: "matriz-permisos"' },
    { selector: "#matriz-permisos [data-modulo] (grupo por módulo)", file: "frontend/js/modules/usuarios-module.js", marker: "dataset: { modulo: grupo.modulo }" },
    { selector: "#permiso-<PERMISSION_NAME>", file: "frontend/js/modules/usuarios-module.js", marker: "const id = `permiso-${permiso.permissionName}`;" },
    { selector: "input[data-permission-name][data-modulo]", file: "frontend/js/modules/usuarios-module.js", marker: "dataset: { permissionName: permiso.permissionName, modulo: grupo.modulo }" },
    { selector: "#btn-guardar-rol", file: "frontend/js/modules/usuarios-module.js", marker: 'id: "btn-guardar-rol"' },
    { selector: "#btn-cancelar-rol", file: "frontend/js/modules/usuarios-module.js", marker: 'id: "btn-cancelar-rol"' },
    { selector: "#btn-eliminar-rol", file: "frontend/js/modules/usuarios-module.js", marker: 'id: "btn-eliminar-rol"' },
    { selector: "rol base: #input-rol-nombre deshabilitado", file: "frontend/js/modules/usuarios-module.js", marker: "this.camposRol.roleName.disabled = base;" },
    { selector: "rol base: #btn-eliminar-rol oculto", file: "frontend/js/modules/usuarios-module.js", marker: "this.btnEliminarRol.hidden = !edicion || base;" },

    // --- Auditoría (S19): js/modules/auditoria-module.js ---
    { selector: "#filtro-auditoria-desde", file: "frontend/js/modules/auditoria-module.js", marker: 'id: "filtro-auditoria-desde"' },
    { selector: "#filtro-auditoria-hasta", file: "frontend/js/modules/auditoria-module.js", marker: 'id: "filtro-auditoria-hasta"' },
    { selector: "#filtro-auditoria-accion", file: "frontend/js/modules/auditoria-module.js", marker: 'id: "filtro-auditoria-accion"' },
    { selector: "#filtro-auditoria-usuario", file: "frontend/js/modules/auditoria-module.js", marker: 'id: "filtro-auditoria-usuario"' },
    { selector: "#btn-limpiar-filtros-auditoria", file: "frontend/js/modules/auditoria-module.js", marker: 'id: "btn-limpiar-filtros-auditoria"' },
    { selector: "#auditoria-lista", file: "frontend/js/modules/auditoria-module.js", marker: 'id: "auditoria-lista"' },
    { selector: "#auditoria-contador", file: "frontend/js/modules/auditoria-module.js", marker: 'id: "auditoria-contador"' },
    { selector: "details.auditoria-detalle[data-id-log]", file: "frontend/js/modules/auditoria-module.js", marker: 'class: "auditoria-detalle",' },
    { selector: "details.auditoria-detalle[data-id-log]", file: "frontend/js/modules/auditoria-module.js", marker: 'dataset: { idLog: String(fila.idLog ?? "") }' },
    { selector: ".auditoria-detalle-json (detalle desplegado)", file: "frontend/js/modules/auditoria-module.js", marker: 'class: "auditoria-detalle-json"' },
    { selector: "#auditoria-paginacion", file: "frontend/js/modules/auditoria-module.js", marker: 'id: "auditoria-paginacion"' },
    { selector: "#btn-auditoria-anterior", file: "frontend/js/modules/auditoria-module.js", marker: 'id: "btn-auditoria-anterior"' },
    { selector: "#auditoria-pagina", file: "frontend/js/modules/auditoria-module.js", marker: 'id: "auditoria-pagina"' },
    { selector: "#btn-auditoria-siguiente", file: "frontend/js/modules/auditoria-module.js", marker: 'id: "btn-auditoria-siguiente"' },
    { selector: "paginación deshabilitada con una sola página", file: "frontend/js/modules/auditoria-module.js", marker: "this.btnSiguiente.disabled = this.totalPaginas === 0" },

    // --- Reportes (S21): js/modules/reportes-module.js ---
    { selector: "#reporte-desde", file: "frontend/js/modules/reportes-module.js", marker: 'id: "reporte-desde"' },
    { selector: "#reporte-hasta", file: "frontend/js/modules/reportes-module.js", marker: 'id: "reporte-hasta"' },
    { selector: "#btn-actualizar-reportes", file: "frontend/js/modules/reportes-module.js", marker: 'id: "btn-actualizar-reportes"' },
    { selector: "#btn-reporte-mes-actual", file: "frontend/js/modules/reportes-module.js", marker: 'id: "btn-reporte-mes-actual"' },
    { selector: "#reporte-periodo", file: "frontend/js/modules/reportes-module.js", marker: 'id: "reporte-periodo"' },
    { selector: "#reportes-validacion", file: "frontend/js/modules/reportes-module.js", marker: 'id: "reportes-validacion"' },
    { selector: "#reportes-kpis", file: "frontend/js/modules/reportes-module.js", marker: 'id: "reportes-kpis"' },
    { selector: "#kpi-total-cobrado", file: "frontend/js/modules/reportes-module.js", marker: 'this.kpi("kpi-total-cobrado"' },
    { selector: "#kpi-total-procedimientos", file: "frontend/js/modules/reportes-module.js", marker: 'this.kpi("kpi-total-procedimientos"' },
    { selector: "#kpi-total-importe", file: "frontend/js/modules/reportes-module.js", marker: 'this.kpi("kpi-total-importe"' },
    { selector: "#kpi-total-citas", file: "frontend/js/modules/reportes-module.js", marker: 'this.kpi("kpi-total-citas"' },
    { selector: "#bloque-ingresos", file: "frontend/js/modules/reportes-module.js", marker: 'id: "bloque-ingresos"' },
    { selector: "#bloque-produccion", file: "frontend/js/modules/reportes-module.js", marker: 'id: "bloque-produccion"' },
    { selector: "#bloque-ocupacion", file: "frontend/js/modules/reportes-module.js", marker: 'id: "bloque-ocupacion"' },
    { selector: "#contador-ingresos", file: "frontend/js/modules/reportes-module.js", marker: 'id: "contador-ingresos"' },
    { selector: "#contador-produccion", file: "frontend/js/modules/reportes-module.js", marker: 'id: "contador-produccion"' },
    { selector: "#contador-ocupacion", file: "frontend/js/modules/reportes-module.js", marker: 'id: "contador-ocupacion"' },
    { selector: "#tabla-ingresos-metodo", file: "frontend/js/modules/reportes-module.js", marker: 'id: "tabla-ingresos-metodo"' },
    { selector: "#tabla-ingresos-mes", file: "frontend/js/modules/reportes-module.js", marker: 'id: "tabla-ingresos-mes"' },
    { selector: "#tabla-produccion-odontologo", file: "frontend/js/modules/reportes-module.js", marker: 'id: "tabla-produccion-odontologo"' },
    { selector: "#tabla-ocupacion-estado", file: "frontend/js/modules/reportes-module.js", marker: 'id: "tabla-ocupacion-estado"' },
    { selector: "#tabla-ocupacion-odontologo", file: "frontend/js/modules/reportes-module.js", marker: 'id: "tabla-ocupacion-odontologo"' },
    { selector: "estado vacío \"Sin movimientos en el período\"", file: "frontend/js/modules/reportes-module.js", marker: 'const MENSAJE_VACIO = "Sin movimientos en el período";' },
    { selector: "rango inválido validado en el cliente (sin request)", file: "frontend/js/modules/reportes-module.js", marker: "if (desde && hasta && desde > hasta) {" },

    // --- Pacientes (S1, S2): js/modules/pacientes-module.js ---
    { selector: "#btn-nuevo-paciente", file: "frontend/js/modules/pacientes-module.js", marker: 'id: "btn-nuevo-paciente"' },
    { selector: "#filtro-paciente", file: "frontend/js/modules/pacientes-module.js", marker: 'id: "filtro-paciente"' },
    { selector: "#btn-buscar-paciente", file: "frontend/js/modules/pacientes-module.js", marker: 'id: "btn-buscar-paciente"' },
    { selector: "#lista-pacientes", file: "frontend/js/modules/pacientes-module.js", marker: 'id: "lista-pacientes"' },
    { selector: "#panel-paciente-form", file: "frontend/js/modules/pacientes-module.js", marker: 'id: "panel-paciente-form"' },
    { selector: "#form-paciente", file: "frontend/js/modules/pacientes-module.js", marker: 'id: "form-paciente"' },
    { selector: "#form-paciente-error", file: "frontend/js/modules/pacientes-module.js", marker: 'id: "form-paciente-error"' },
    { selector: "#btn-guardar-paciente", file: "frontend/js/modules/pacientes-module.js", marker: 'id: "btn-guardar-paciente"' },
    { selector: "#input-nombres", file: "frontend/js/modules/pacientes-module.js", marker: 'id: "input-nombres"' },
    { selector: "#input-apellidos", file: "frontend/js/modules/pacientes-module.js", marker: 'id: "input-apellidos"' },
    { selector: "#input-fecha-nacimiento", file: "frontend/js/modules/pacientes-module.js", marker: 'id: "input-fecha-nacimiento"' },
    { selector: "#input-telefono", file: "frontend/js/modules/pacientes-module.js", marker: 'id: "input-telefono"' },
    { selector: "#input-email", file: "frontend/js/modules/pacientes-module.js", marker: 'id: "input-email"' },
    { selector: "#input-direccion", file: "frontend/js/modules/pacientes-module.js", marker: 'id: "input-direccion"' },
    { selector: "[data-accion=\"editar\"] (pacientes)", file: "frontend/js/modules/pacientes-module.js", marker: 'dataset: { accion: "editar", id: paciente.id }' },
    { selector: "#lista-pacientes .btn-danger (dar de baja)", file: "frontend/js/modules/pacientes-module.js", marker: 'class: "btn btn-sm btn-danger"' },
    { selector: "#lista-pacientes a[href*=\"/historial\"]", file: "frontend/js/modules/pacientes-module.js", marker: "href: `#/pacientes/${paciente.id}/historial`" },

    // --- Historial clínico (S3): js/modules/paciente-historial-module.js ---
    { selector: "#historial-contenido", file: "frontend/js/modules/paciente-historial-module.js", marker: 'id: "historial-contenido"' },
    { selector: "#btn-registrar-realizado", file: "frontend/js/modules/paciente-historial-module.js", marker: 'id: "btn-registrar-realizado"' },

    // --- Tratamiento realizado (S8): js/modules/realizado-form.js ---
    { selector: "#panel-realizado-form", file: "frontend/js/modules/realizado-form.js", marker: 'id: "panel-realizado-form"' },
    { selector: "#form-realizado", file: "frontend/js/modules/realizado-form.js", marker: 'id: "form-realizado"' },
    { selector: "#form-realizado-error", file: "frontend/js/modules/realizado-form.js", marker: 'id: "form-realizado-error"' },
    { selector: "#realizado-paciente", file: "frontend/js/modules/realizado-form.js", marker: 'id: "realizado-paciente"' },
    { selector: "#realizado-tratamiento", file: "frontend/js/modules/realizado-form.js", marker: 'id: "realizado-tratamiento"' },
    { selector: "#realizado-dentista", file: "frontend/js/modules/realizado-form.js", marker: 'id: "realizado-dentista"' },
    { selector: "#realizado-fecha", file: "frontend/js/modules/realizado-form.js", marker: 'id: "realizado-fecha"' },
    { selector: "#realizado-detalle", file: "frontend/js/modules/realizado-form.js", marker: 'id: "realizado-detalle"' },
    { selector: "#realizado-observaciones", file: "frontend/js/modules/realizado-form.js", marker: 'id: "realizado-observaciones"' },
    { selector: "#btn-guardar-realizado", file: "frontend/js/modules/realizado-form.js", marker: 'id: "btn-guardar-realizado"' },

    // --- Catálogo de tratamientos (S7): js/modules/tratamientos-module.js ---
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
    { selector: "#input-descripcion", file: "frontend/js/modules/tratamientos-module.js", marker: 'id: "input-descripcion"' },
    { selector: "#btn-guardar-tratamiento", file: "frontend/js/modules/tratamientos-module.js", marker: 'id: "btn-guardar-tratamiento"' },
    { selector: "#btn-cancelar-tratamiento", file: "frontend/js/modules/tratamientos-module.js", marker: 'id: "btn-cancelar-tratamiento"' },
    { selector: "#lista-tratamientos .btn-outline (editar)", file: "frontend/js/modules/tratamientos-module.js", marker: 'text: "Editar"' },

    // --- Agenda de citas (S4, S6, S20): js/modules/agenda-module.js ---
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
    { selector: "#btn-guardar-cita", file: "frontend/js/modules/agenda-module.js", marker: 'id: "btn-guardar-cita"' },
    { selector: "#aviso-odontologos-agenda", file: "frontend/js/modules/agenda-module.js", marker: 'id: "aviso-odontologos-agenda"' },
    { selector: "#link-cargar-odontologos", file: "frontend/js/modules/agenda-module.js", marker: 'id: "link-cargar-odontologos"' },
    { selector: "aviso: la agenda se alimenta del padrón (S20)", file: "frontend/js/modules/agenda-module.js", marker: 'this.avisoOdontologos.hidden = false;' },
    { selector: "[data-accion=\"reprogramar\"]", file: "frontend/js/modules/agenda-module.js", marker: 'dataset: { accion: "reprogramar", id: cita.id }' },
    { selector: "#panel-reprogramar-form", file: "frontend/js/modules/agenda-module.js", marker: 'id: "panel-reprogramar-form"' },
    { selector: "#form-reprogramar", file: "frontend/js/modules/agenda-module.js", marker: 'id: "form-reprogramar"' },
    { selector: "#form-reprogramar-error", file: "frontend/js/modules/agenda-module.js", marker: 'id: "form-reprogramar-error"' },
    { selector: "#reprogramar-fecha-hora", file: "frontend/js/modules/agenda-module.js", marker: 'id: "reprogramar-fecha-hora"' },
    { selector: "#reprogramar-dentista", file: "frontend/js/modules/agenda-module.js", marker: 'id: "reprogramar-dentista"' },
    { selector: "#btn-guardar-reprogramacion", file: "frontend/js/modules/agenda-module.js", marker: 'id: "btn-guardar-reprogramacion"' },
    { selector: "[data-accion=\"cancelar\"]", file: "frontend/js/modules/agenda-module.js", marker: 'dataset: { accion: "cancelar", id: cita.id }' },
    { selector: "[data-accion=\"realizado\"] (registrar procedimiento desde la agenda)", file: "frontend/js/modules/agenda-module.js", marker: 'dataset: { accion: "realizado", id: cita.id }' },
    { selector: "estados cerrados (ATENDIDA/CANCELADA): sin acciones de S6", file: "frontend/js/modules/agenda-module.js", marker: 'const ESTADOS_CERRADOS = ["ATENDIDA", "CANCELADA"];' },
    { selector: "modal de cancelación: anuncia la cancelación de recordatorios", file: "frontend/js/modules/agenda-module.js", marker: "Se cancelarán también sus recordatorios pendientes" },

    // --- Facturación y pagos (S9, S10): js/modules/facturacion-module.js ---
    { selector: "#factura-paciente", file: "frontend/js/modules/facturacion-module.js", marker: 'id: "factura-paciente"' },
    { selector: "#factura-candidatos", file: "frontend/js/modules/facturacion-module.js", marker: 'id: "factura-candidatos"' },
    { selector: "#factura-candidatos .check-row input[data-realizado-id]", file: "frontend/js/modules/facturacion-module.js", marker: "dataset: { realizadoId: candidato.id }" },
    { selector: "#form-factura", file: "frontend/js/modules/facturacion-module.js", marker: 'id: "form-factura"' },
    { selector: "#form-factura-error", file: "frontend/js/modules/facturacion-module.js", marker: 'id: "form-factura-error"' },
    { selector: "#btn-emitir-factura", file: "frontend/js/modules/facturacion-module.js", marker: 'id: "btn-emitir-factura"' },
    { selector: "#form-factura .form-actions .badge (total de la emisión)", file: "frontend/js/modules/facturacion-module.js", marker: "`Total: ${money(total)} · ${this.seleccion.size} tratamiento(s)`" },
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
    { selector: "#btn-registrar-pago", file: "frontend/js/modules/facturacion-module.js", marker: 'id: "btn-registrar-pago"' },
    { selector: "#detalle-factura .alert-box.info (sin saldo)", file: "frontend/js/modules/facturacion-module.js", marker: 'class: "alert-box info"' },

    // --- Recordatorios (S5, S15, S22): js/modules/recordatorios-module.js ---
    { selector: "#lista-recordatorios", file: "frontend/js/modules/recordatorios-module.js", marker: 'id: "lista-recordatorios"' },
    { selector: "#contador-recordatorios", file: "frontend/js/modules/recordatorios-module.js", marker: 'id: "contador-recordatorios"' },
    { selector: "#filtro-recordatorio-estado", file: "frontend/js/modules/recordatorios-module.js", marker: 'id: "filtro-recordatorio-estado"' },
    { selector: "#btn-despachar-recordatorios", file: "frontend/js/modules/recordatorios-module.js", marker: 'id: "btn-despachar-recordatorios"' },
    { selector: "emptyMessage \"Todavía no se registraron recordatorios.\"", file: "frontend/js/modules/recordatorios-module.js", marker: "Todavía no se registraron recordatorios." }
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
            for (const handler of handlers) {
                try {
                    handler(p);
                } catch (error) {
                    this.exceptions.push({ text: `handler ${msg.method}: ${error.message}` });
                }
            }
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
            } catch (error) {
                // Un error de sintaxis o de referencia no se arregla esperando: la
                // expresión misma está mal. Sin este corte, un typo en la expresión se
                // reporta como timeout de la condición y manda a depurar el producto
                // en lugar del harness.
                if (esErrorPermanenteDeExpresion(error)) {
                    throw new Error(
                        `La expresión de espera no evalúa (${label}): ${error.message}\n`
                        + `  expresión: ${expression}`
                    );
                }
                /* la página todavía está navegando */
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

    for (const c of drained.console) {
        items.push(`${label}: console.${c.level} -> ${c.text}`);
    }
    for (const e of drained.exceptions) {
        items.push(`${label}: excepción no capturada -> ${e.text}`);
    }
    for (const l of drained.logs) {
        if (isNoise(l.url)) continue; // e.g. the favicon 404 the browser logs on its own
        items.push(`${label}: log.${l.level} (${l.source}) -> ${l.text}`);
    }

    for (const b of drained.badStatus) {
        if (isNoise(b.url)) continue;
        const expected = expectedHttp.some((entry) => b.url.includes(entry.url) && b.status === entry.status);
        items.push(`${label}: HTTP ${b.status} -> ${b.url}${expected ? " (esperado por el escenario)" : ""}`);
    }

    for (const f of drained.failed) {
        if (isNoise(f.url)) continue;
        items.push(`${label}: request fallida (${f.error}) -> ${f.url}`);
    }

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
    // Se usa checkVisibility() porque las propiedades del propio nodo no alcanzan:
    // un <a> dentro de un contenedor con display:none o [hidden] conserva su
    // display computado y su .hidden en false, asi que la comprobacion ingenua
    // lo daba por visible aunque no se renderice (falso positivo que escondia
    // estados vacios dentro de contenedores ocultos).
    const visible = await cdp.evaluate(`
        const node = document.querySelector(${sel(selector)});
        if (!node) return false;
        if (typeof node.checkVisibility === 'function') {
            return node.checkVisibility({ checkOpacity: false, checkVisibilityCSS: true });
        }
        if (node.hidden) return false;
        const style = getComputedStyle(node);
        if (style.display === 'none' || style.visibility === 'hidden') return false;
        const rect = node.getBoundingClientRect();
        return rect.width > 0 && rect.height > 0;
    `);
    return visible === true;
}

async function isDisabled(cdp, selector) {
    const disabled = await cdp.evaluate(`
        const node = document.querySelector(${sel(selector)});
        return node ? Boolean(node.disabled) : null;
    `);
    return disabled === true;
}

/** Opciones de un <select> como { value, label }, en el orden del DOM. */
async function optionsOf(cdp, selector) {
    const options = await cdp.evaluate(`
        const node = document.querySelector(${sel(selector)});
        if (!node) return [];
        return Array.from(node.options).map(o => ({ value: o.value, label: o.textContent.trim() }));
    `);
    return Array.isArray(options) ? options : [];
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

/**
 * Filas de datos de una tabla dentro de un módulo.
 *
 * Las tablas del frontend pintan el mensaje de "sin registros" como UNA fila con
 * un único <td colspan>: contarlas como datos haría pasar por "hay filas" una
 * tabla vacía, así que se excluye ese placeholder.
 */
async function rowsOf(cdp, containerSelector) {
    const rows = await cdp.evaluate(`
        const root = document.querySelector(${sel(containerSelector)});
        if (!root) return [];
        return Array.from(root.querySelectorAll('tbody tr'))
            .filter(tr => !(tr.children.length === 1 && tr.children[0].hasAttribute('colspan')))
            .map(tr => ({
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

/** Mensaje de tabla vacía (la fila con colspan), o "" si la tabla tiene datos. */
async function emptyTableMessage(cdp, containerSelector) {
    const message = await cdp.evaluate(`
        const root = document.querySelector(${sel(containerSelector)});
        if (!root) return "";
        const cell = Array.from(root.querySelectorAll('tbody td[colspan]'))
            .find(td => td.textContent.trim().length > 0);
        return cell ? cell.textContent.trim() : "";
    `);
    return typeof message === "string" ? message : "";
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

/**
 * Sidebar en orden, agrupado por los rótulos que arma core/shell.js (S22):
 * [{ grupo, label, routeId }]. Permite comparar grupos, ítems y su orden.
 */
async function readSidebar(cdp) {
    const items = await cdp.evaluate(`
        const root = document.querySelector('#sidebar-menu');
        if (!root) return [];
        const salida = [];
        let grupo = '';
        Array.from(root.children).forEach(node => {
            if (node.classList.contains('sidebar-group-label')) {
                grupo = node.textContent.trim();
                return;
            }
            if (node.classList.contains('sidebar-link')) {
                salida.push({
                    grupo,
                    routeId: node.dataset.routeId || '',
                    label: (node.querySelector('.sidebar-label')?.textContent || '').trim()
                });
            }
        });
        return salida;
    `);
    return Array.isArray(items) ? items : [];
}

/** Abre una opción de un <select> por el texto visible y devuelve su value. */
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

/** Espera a que el log de red registre una llamada con ese método/URL/estado. */
async function waitCall(cdp, { method = null, url = "", status = null }, timeoutMs = 30000) {
    const deadline = Date.now() + timeoutMs;
    while (Date.now() < deadline) {
        const hits = findCalls(cdp, { method, url, status });
        if (hits.length) return hits;
        await sleep(120);
    }
    throw new Error(`No se registró ${method || "la"} ${url}${status ? ` -> ${status}` : ""}.`);
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
    // Diagnóstico del timeout: si el submit nativo quedó bloqueado por validación
    // del navegador no hay aviso ni error visible, y el timeout solo no lo distingue
    // de una petición que nunca respondió.
    const diagnostico = await cdp.evaluate(`
        const invalidos = Array.from(document.querySelectorAll('input, select, textarea'))
            .filter(control => control.willValidate && !control.checkValidity())
            .map(control => (control.id || control.name || control.type)
                + ': ' + (control.validationMessage || 'sin mensaje'));
        return {
            toasts: Array.from(document.querySelectorAll('#toast-container .toast'))
                .map(t => t.textContent.trim()),
            invalidos
        };
    `).catch(() => null);
    const peticiones = cdp.network
        .slice(-6)
        .map((entry) => `${entry.method || "?"} ${entry.url} -> ${entry.status}`);
    throw new Error(
        `Sin respuesta del formulario (aviso "${successNeedle}") en el tiempo esperado.\n`
        + `  toasts visibles: ${JSON.stringify(diagnostico?.toasts || [])}\n`
        + `  controles inválidos: ${JSON.stringify(diagnostico?.invalidos || [])}\n`
        + `  últimas peticiones: ${peticiones.join(" | ")}`
    );
}

/**
 * Guarda la matriz de un rol y confirma la operación por cualquiera de sus dos
 * pruebas observables: el aviso de éxito de la UI o el PUT 200 que dejó la matriz
 * persistida. En una corrida real el aviso no se vio aunque el PUT había entrado;
 * el contrato es el PUT, el aviso es la señal, y exigir sólo la señal vuelve
 * flaky al escenario sin decir nada del producto.
 */
async function guardarMatrizRol(cdp, rolId, { successNeedle = "Matriz de permisos actualizada", timeoutMs = 20000 } = {}) {
    const urlRol = `/api/roles/${rolId}`;
    const putAntes = findCalls(cdp, { method: "PUT", url: urlRol, status: 200 }).length;
    await clearToasts(cdp);
    await submitForm(cdp, "#form-rol");
    const deadline = Date.now() + timeoutMs;
    while (Date.now() < deadline) {
        const estado = await cdp.evaluate(`
            const box = document.querySelector('#form-rol-error');
            return {
                error: box && !box.hidden ? box.textContent.trim() : "",
                toasts: Array.from(document.querySelectorAll('#toast-container .toast'))
                    .map(t => t.textContent.trim())
            };
        `);
        const aviso = estado.toasts.find((text) => text.includes(successNeedle));
        if (findCalls(cdp, { method: "PUT", url: urlRol, status: 200 }).length > putAntes) {
            return { registrado: true, porAviso: Boolean(aviso), toast: aviso || null };
        }
        if (estado.error) return { registrado: false, error: estado.error };
        await sleep(150);
    }
    return { registrado: false, error: `sin aviso de éxito ni PUT 200 en ${timeoutMs} ms` };
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

/**
 * Errores que no se resuelven reintentando: la expresión evaluada en la página
 * está mal escrita (typo, `;` de más, identificador inexistente). Se distinguen de
 * los errores transitorios de navegación, que sí se reintentan hasta el timeout.
 */
function esErrorPermanenteDeExpresion(error) {
    const mensaje = String(error && error.message ? error.message : "");
    return /SyntaxError|Unexpected token|Unexpected identifier|ReferenceError|is not defined/i.test(mensaje);
}

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

/**
 * Total de un contador de la UI ("3 de 12 registro(s)", "8 de 11 recordatorio(s)",
 * "4 resultado(s)"). Devuelve el total cuando el formato lo declara y la cantidad
 * visible en el resto de los casos.
 */
function parseCounterTotal(text) {
    const raw = String(text ?? "");
    const conTotal = /de\s+(\d+)/i.exec(raw);
    if (conTotal) return Number(conTotal[1]);
    const simple = /(\d+)/.exec(raw);
    return simple ? Number(simple[1]) : null;
}

function pad(number) {
    return String(number).padStart(2, "0");
}

/** Formats a Date as the value expected by <input type="datetime-local">. */
function localDateTimeInput(date) {
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
        + `T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/** YYYY-MM-DD en horario local, que es lo que espera un <input type="date">. */
function isoDate(offsetDays = 0) {
    const date = new Date();
    date.setDate(date.getDate() + offsetDays);
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
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
        for (const file of result.files) {
            console.log(`  · ${file}`);
        }
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
   left behind by a previous execution (username, email, phone, treatment code,
   motivo, reference). */
const STAMP = `${Date.now()}${Math.floor(Math.random() * 1000)}`;
const SHORT = STAMP.toString(36).toUpperCase();
const STAFF_PASSWORD = `ClinicaQA-${STAMP}!`;
const PATIENT = {
    nombres: "QA Smoke",
    apellidos: `Paciente ${STAMP}`,
    fechaNacimiento: "1988-06-15",
    telefono: `7${STAMP.slice(-8)}`,
    /* Edición de S2: mismo largo que el teléfono original para que ninguno de
       los dos sea subcadena del otro y la aserción de la tabla no dé falsos
       positivos. */
    telefonoEditado: `9${STAMP.slice(-8)}`,
    direccion: "Av. Smoke 123, Zona QA",
    direccionEditada: "Av. Smoke 456, Zona QA (editada)",
    email: `smoke-${STAMP}@example.com`
};
const TREATMENT = {
    codigo: `QA-${SHORT}`,
    nombre: `Smoke tratamiento ${STAMP}`,
    descripcion: "Procedimiento creado por el smoke de navegador.",
    precio: "350",
    precioEditado: "500",
    duracionMinutos: "30"
};
const USUARIOS = {
    odontologo: {
        username: `smoke-od-${STAMP}`,
        nombre: `Dra. Smoke Odontologa ${STAMP}`,
        email: `smoke-od-${STAMP}@example.com`,
        rolLabel: "Odontólogo",
        rolBadge: "Odontólogo"
    },
    recepcionista: {
        username: `smoke-re-${STAMP}`,
        nombre: `Smoke Recepcion ${STAMP}`,
        email: `smoke-re-${STAMP}@example.com`,
        rolLabel: "Recepción",
        rolBadge: "Recepción"
    },
    temporal: {
        username: `smoke-baja-${STAMP}`,
        nombre: `Smoke Baja ${STAMP}`,
        email: `smoke-baja-${STAMP}@example.com`,
        rolLabel: "Recepción"
    }
};
/* Rol base que se usa para probar la matriz editable: no es el administrador,
   así que su matriz se puede cambiar y restaurar sin tocar el acceso total. */
const ROL_BASE_MATRIZ = "ROLE_RECEPCIONISTA";
const CITA_MOTIVO = `SMOKE-${STAMP}`;
/* Motivo de la cita que sólo existe para ejercer la cancelación de S6 (cita B). */
const CITA_B_MOTIVO = `SMOKE-B-${STAMP}`;
const PAGO_REFERENCIA = `REF-${STAMP}`;
// S10 exige pagos parciales: el primer pago deja el comprobante en PAGO_PARCIAL y el
// segundo cubre el saldo restante. El importe es menor que el total del comprobante
// (500) a proposito, para forzar el estado intermedio.
const PAGO_PARCIAL = 200;
const PAGO_METODO_LABEL = "Efectivo";
const DETALLE_REALIZADO = `Detalle de procedimiento ${STAMP}`;
/* Acciones auditables que la propia corrida produce, con la etiqueta que pinta
   la pantalla de auditoría (S19). */
const AUDITADAS_ESPERADAS = [
    { codigo: "PACIENTE_CREAR", etiqueta: "Alta de paciente" },
    { codigo: "USUARIO_CREAR", etiqueta: "Alta de usuario" },
    { codigo: "CITA_CREAR", etiqueta: "Programación de cita" },
    { codigo: "CITA_REPROGRAMAR", etiqueta: "Reprogramación de cita" },
    { codigo: "CITA_CANCELAR", etiqueta: "Cancelación de cita" },
    { codigo: "FACTURA_EMITIR", etiqueta: "Emisión de factura" },
    { codigo: "PAGO_REGISTRAR", etiqueta: "Registro de pago" }
];

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

/** Fila del padrón de usuarios cuyo primer campo es `username`. */
async function findUserRow(cdp, username) {
    const rows = await rowsOf(cdp, "#lista-usuarios");
    return rows.find((row) => row.cells[0].replace("Tu usuario", "").trim() === username) || null;
}

/** Alta de un usuario por la UI (S18). Devuelve la fila creada y su id. */
async function crearUsuarioUI(cdp, datos) {
    await click(cdp, "#btn-nuevo-usuario");
    await cdp.waitFor(
        "const panel = document.querySelector('#panel-usuario-form'); return Boolean(panel) && !panel.hidden;",
        10000,
        "formulario de usuario"
    );

    await setValue(cdp, "#input-usuario-username", datos.username);
    await setValue(cdp, "#input-usuario-nombre", datos.nombre);
    await setValue(cdp, "#input-usuario-email", datos.email);
    await setValue(cdp, "#input-usuario-password", STAFF_PASSWORD);
    const rolValue = await selectOptionByText(cdp, "#select-usuario-rol", datos.rolLabel);

    await clearToasts(cdp);
    await submitForm(cdp, "#form-usuario");
    const outcome = await waitFormOutcome(cdp, {
        errorSelector: "#form-usuario-error",
        busySelector: "#btn-guardar-usuario",
        successNeedle: "creado"
    });
    assert(outcome.ok, `No se pudo crear el usuario ${datos.username}: ${outcome.error}`);

    await waitCall(cdp, { method: "POST", url: "/api/usuarios", status: 201 });
    await cdp.waitFor(
        `return Array.from(document.querySelectorAll('#lista-usuarios tbody tr'))
            .some(tr => tr.textContent.includes(${sel(datos.username)}));`,
        20000,
        `usuario ${datos.username} en el padrón`
    );

    const row = await findUserRow(cdp, datos.username);
    assert(row, `El usuario ${datos.username} no aparece en el padrón tras crearlo.`);
    const id = row.actions.find((action) => action.accion === "editar-usuario")?.id || null;
    assert(id, `No se pudo leer el id de ${datos.username} desde la acción de edición de su fila.`);
    assert(
        !(await isVisible(cdp, "#panel-usuario-form")),
        "El panel de alta de usuario siguió abierto después de guardar."
    );

    return { id, rolValue, row, toast: outcome.toast };
}

/* ----------------------------------------------------------------------------
   Agenda de una cita: reintenta con otro horario solo ante un 409 de solapamiento
   -------------------------------------------------------------------------- */

async function programarCita(cdp, { pacienteId, tratamientoNombre, dentistaId, motivo, slots }) {
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
    await selectOptionByText(cdp, "#cita-tratamiento", tratamientoNombre);
    await setValue(cdp, "#cita-dentista", dentistaId);
    await setValue(cdp, "#cita-motivo", motivo);

    let created = null;
    let lastError = "";
    for (const slot of slots) {
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
    return created;
}

// La sesión de DevTools se conserva fuera del try para poder capturar el estado de la
// página (screenshot + últimas peticiones) cuando la corrida muere.
let sesion = null;

try {
    const version = await fetchDevtoolsJson(`http://127.0.0.1:${PORT}/json/version`);
    report.browser = version.Browser;

    const targets = await fetchDevtoolsJson(`http://127.0.0.1:${PORT}/json/list`);
    const page = targets.find((target) => target.type === "page");
    const cdp = await Cdp.connect(page.webSocketDebuggerUrl);
    sesion = cdp;

    await cdp.send("Page.enable");
    await cdp.send("Runtime.enable");
    await cdp.send("Log.enable");
    await cdp.send("Network.enable");
    // La caché HTTP se desactiva para toda la corrida. Sin esto, el perfil de
    // Chrome reutiliza las versiones anteriores de routes.js/config.js y el
    // sidebar observado es el de la corrida previa: un falso negativo que no
    // dice nada sobre el código servido. Lo que prueba el harness es lo que
    // nginx entrega ahora, no lo que el navegador recuerda.
    await cdp.send("Network.setCacheDisabled", { cacheDisabled: true });
    await cdp.send("Emulation.setDeviceMetricsOverride", {
        width: 1440, height: 900, deviceScaleFactor: 1, mobile: false
    });

    /* ---------------- 1. Sesión limpia: sin token no se valida ninguna sesión ---------------- */
    await step(cdp, "prepare-session", async () => {
        // El storage se borra ANTES de la primera navegación: si se limpiara con la
        // app ya cargada, ese primer arranque podría validar un token viejo con
        // GET /api/auth/me y el paso dejaría de probar la compuerta de login.
        let clearedWith = "Storage.clearDataForOrigin";
        try {
            await cdp.send("Storage.clearDataForOrigin", {
                origin: BASE,
                storageTypes: "local_storage,session_storage"
            });
        } catch (error) {
            clearedWith = `localStorage.clear + recarga (fallback: ${error.message})`;
        }

        await cdp.send("Page.navigate", { url: `${BASE}/index.html` });
        await cdp.waitFor("return document.readyState === 'complete';", 25000, "carga del documento");

        if (clearedWith.startsWith("localStorage")) {
            await cdp.evaluate("localStorage.clear(); sessionStorage.clear(); return true;");
            // El primer arranque pudo validar el token que había antes de limpiar:
            // se descarta esa actividad de red para que la aserción de abajo siga
            // midiendo sólo lo que pasa con el storage ya vacío.
            cdp.network = [];
            await cdp.send("Page.navigate", { url: `${BASE}/index.html` });
            await cdp.waitFor("return document.readyState === 'complete';", 25000, "recarga del documento");
        }

        await cdp.waitFor(
            "const gate = document.querySelector('#login-gate'); return Boolean(gate) && !gate.hidden;",
            25000,
            "compuerta de login"
        );

        const stored = await cdp.evaluate(`return localStorage.getItem(${sel(STORAGE_TOKEN_KEY)});`);
        assert(stored === null, `Quedó un token guardado en "${STORAGE_TOKEN_KEY}" al iniciar la corrida.`);
        assert(
            findCalls(cdp, { method: "GET", url: "/api/auth/me" }).length === 0,
            "La aplicación llamó a GET /api/auth/me sin token guardado: la compuerta de login debía resolverse en el cliente."
        );

        const gateVisible = await isVisible(cdp, "#login-gate");
        const shellHidden = !(await isVisible(cdp, "#app-shell"));
        assert(gateVisible, "La compuerta de login no se mostró con la sesión limpia.");
        assert(shellHidden, "El shell autenticado se mostró sin sesión guardada.");

        return {
            clearedWith,
            title: await cdp.evaluate("return document.title;"),
            loginGateVisible: gateVisible,
            shellHidden,
            tokenStored: false,
            authMeCalls: 0
        };
    });

    /* ---------------- 2. Login del administrador bootstrap + sidebar completo (S13, S22) ---------------- */
    const login = await step(cdp, "login-admin", async () => {
        await setValue(cdp, "#login-username", ADMIN_USER);
        await setValue(cdp, "#login-password", ADMIN_PASSWORD);
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
            `El login no llegó a POST /api/auth/login con respuesta 200 (usuario "${ADMIN_USER}").`
        );

        const sidebar = await readSidebar(cdp);
        const observado = sidebar.map((item) => `${item.grupo} → ${item.label}`);
        const esperado = SIDEBAR_ADMIN.map((item) => `${item.grupo} → ${item.label}`);
        const faltantes = esperado.filter((item) => !observado.includes(item));
        const sobrantes = observado.filter((item) => !esperado.includes(item));
        assert(
            faltantes.length === 0 && sobrantes.length === 0 && observado.join("|") === esperado.join("|"),
            `El sidebar del administrador no coincide con S22. Faltan: [${faltantes.join(", ")}]; `
            + `sobran: [${sobrantes.join(", ")}]; observado: ${observado.join(" | ")}.`
        );
        const routeIds = sidebar.map((item) => item.routeId);
        assert(
            JSON.stringify(routeIds) === JSON.stringify(SIDEBAR_ADMIN.map((item) => item.routeId)),
            `El orden de los módulos del sidebar cambió: ${routeIds.join(" → ")}.`
        );
        assert(
            (await countOf(cdp, '[data-route-id="recordatorios"]')) === 1
            && (await countOf(cdp, '[data-route-id="usuarios"]')) === 1
            && (await countOf(cdp, '[data-route-id="auditoria"]')) === 1
            && (await countOf(cdp, '[data-route-id="reportes"]')) === 1,
            "El sidebar no muestra los módulos de administración entregados por S18-S22."
        );

        return {
            tokenSegments: segments,
            tokenKey: STORAGE_TOKEN_KEY,
            user: await textOf(cdp, "#current-user-name"),
            role: await textOf(cdp, "#current-user-role"),
            hash: await cdp.evaluate("return window.location.hash;"),
            sidebar
        };
    });
    assert(login.role === "Administrador", `El rol mostrado no es Administrador: "${login.role}".`);
    assert(login.user.length > 0, "La cabecera no muestra el nombre del usuario autenticado.");

    /* ---------------- 3. Panel general con la base limpia (S22, S23) ---------------- */
    await step(cdp, "dashboard-vacio", async () => {
        // El login ya dejo montado el panel, y el router no remonta una ruta que ya
        // esta activa: clickear el link del sidebar no dispara peticiones nuevas. Como
        // step() vacia el log de red al cerrar cada paso, hay que salir a otra ruta y
        // volver para que las consultas del panel ocurran DENTRO de este paso.
        await cdp.evaluate("window.location.hash = '#/pacientes'; return true;");
        await cdp.waitFor(
            "return Boolean(document.querySelector('#lista-pacientes'));",
            20000,
            "listado de pacientes antes de volver al panel"
        );
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
        for (const kpi of kpis) {
            assert(kpi.label.length > 0, "Un KPI quedó sin etiqueta.");
            assert(kpi.value.length > 0, `El KPI "${kpi.label}" quedó sin valor.`);
        }

        const cuerpo = await textOf(cdp, "#app-view");
        assert(
            !/preparaci[oó]n|pr[oó]ximamente|en construcci[oó]n|pr[oó]xima entrega/i.test(cuerpo),
            "El panel general sigue anunciando módulos en preparación (S22): el sidebar ya tiene todos los módulos."
        );
        assert(
            (await countOf(cdp, "#app-view .state-box-error")) === 0,
            "El panel general muestra un estado de error con la base limpia."
        );

        const pacientes = kpis.find((kpi) => kpi.label === "Pacientes registrados");
        assert(
            pacientes && pacientes.value === "0",
            `Con la base limpia el KPI de pacientes debe ser 0 (quedó "${pacientes?.value}").`
        );
        const tratamientos = kpis.find((kpi) => kpi.label === "Tratamientos en catálogo");
        assert(
            tratamientos && Number(tratamientos.value) >= 1,
            `El catálogo bootstrap de tratamientos (S23) no aparece en el panel: "${tratamientos?.value}".`
        );
        const pacientesVacio = await emptyTableMessage(cdp, "#app-view");
        assert(
            pacientesVacio.includes("Todavía no hay pacientes registrados."),
            `El panel no muestra el estado vacío de pacientes: "${pacientesVacio}".`
        );
        assert(
            findCalls(cdp, { method: "GET", url: "/api/pacientes", status: 200 }).length > 0
            && findCalls(cdp, { method: "GET", url: "/api/tratamientos", status: 200 }).length > 0,
            "El panel no consultó GET /api/pacientes y GET /api/tratamientos."
        );

        return {
            heading: await textOf(cdp, "#app-view .page-header h2"),
            kpis,
            pacientesEnCero: true,
            mensajePacientesVacio: pacientesVacio,
            sinModulosEnPreparacion: true,
            sinEstadoDeError: true
        };
    }, { strictConsole: true });

    /* ---------------- 4. Alta del odontólogo (S18, S20) ---------------- */
    const odontologo = await step(cdp, "usuarios-crear-odontologo", async () => {
        await openModule(cdp, "usuarios", "Usuarios y roles");
        await cdp.waitFor(
            "return document.querySelectorAll('#lista-usuarios tbody tr').length > 0;",
            20000,
            "padrón de usuarios"
        );
        assert(
            (await countOf(cdp, "#lista-usuarios .state-box-error")) === 0,
            "El padrón de usuarios muestra un estado de error."
        );
        assert(
            (await countOf(cdp, "#btn-nuevo-usuario")) === 1,
            "El administrador no ve la acción 'Nuevo usuario'."
        );

        const creado = await crearUsuarioUI(cdp, USUARIOS.odontologo);
        assert(
            creado.row.cells[3] === USUARIOS.odontologo.rolBadge,
            `El rol del usuario creado no es "${USUARIOS.odontologo.rolBadge}": "${creado.row.cells[3]}".`
        );
        assert(creado.row.cells[4] === "Activo", `El usuario creado no nació activo: "${creado.row.cells[4]}".`);
        assert(
            creado.row.cells[5] === "Disponible en agenda",
            `El odontólogo no quedó marcado como disponible para la agenda: "${creado.row.cells[5]}".`
        );
        assert(
            creado.id && Number.isInteger(Number(creado.id)),
            `El id del odontólogo creado no es utilizable por la agenda: "${creado.id}".`
        );
        assert(
            findCalls(cdp, { method: "GET", url: "/api/usuarios", status: 200 }).length > 0,
            "La pantalla de usuarios no consultó GET /api/usuarios."
        );

        return {
            id: creado.id,
            username: USUARIOS.odontologo.username,
            rol: creado.rolValue,
            rolBadge: creado.row.cells[3],
            disponibleEnAgenda: creado.row.cells[5],
            toast: creado.toast,
            postOk: true
        };
    });

    /* ---------------- 5. Alta de la recepcionista (S18) ---------------- */
    const recepcionista = await step(cdp, "usuarios-crear-recepcionista", async () => {
        const creado = await crearUsuarioUI(cdp, USUARIOS.recepcionista);
        assert(
            creado.row.cells[3] === USUARIOS.recepcionista.rolBadge,
            `El rol del usuario creado no es "${USUARIOS.recepcionista.rolBadge}": "${creado.row.cells[3]}".`
        );
        assert(
            creado.row.cells[5] !== "Disponible en agenda",
            "Un usuario de recepción no debe aparecer como odontólogo disponible en la agenda."
        );
        assert(
            /^\d+\s+usuario\(s\)/.test(await textOf(cdp, "#contador-usuarios")),
            `El contador del padrón no informa la cantidad de usuarios: "${await textOf(cdp, "#contador-usuarios")}".`
        );

        return {
            id: creado.id,
            username: USUARIOS.recepcionista.username,
            rol: creado.rolValue,
            rolBadge: creado.row.cells[3],
            toast: creado.toast,
            contador: await textOf(cdp, "#contador-usuarios")
        };
    });

    /* ---------------- 6. Baja lógica de un usuario descartable (S18) ---------------- */
    await step(cdp, "usuarios-baja-logica", async () => {
        // El usuario descartable se crea dentro del paso para no ensuciar el
        // padrón con una cuenta extra que sobreviva a la corrida.
        const temporal = await crearUsuarioUI(cdp, USUARIOS.temporal);

        await clickInRow(cdp, "#lista-usuarios", USUARIOS.temporal.username, '[data-accion="baja-usuario"]');
        const message = await waitModal(cdp);
        assert(/dar de baja/i.test(message), `El diálogo de baja de usuario no es el esperado: "${message}".`);

        await clearToasts(cdp);
        await confirmModal(cdp);
        const toast = await waitToast(cdp, "Usuario dado de baja");
        await waitCall(cdp, { method: "DELETE", url: `/api/usuarios/${temporal.id}`, status: 204 });

        // La baja es lógica: la fila permanece en el padrón como inactiva y pierde
        // la acción de baja. El listado completo incluye inactivos a propósito.
        await cdp.waitFor(
            `return Array.from(document.querySelectorAll('#lista-usuarios tbody tr'))
                .some(tr => tr.textContent.includes(${sel(USUARIOS.temporal.username)}) && tr.textContent.includes('Inactivo'));`,
            20000,
            "usuario dado de baja como inactivo"
        );
        const filaInactiva = await findUserRow(cdp, USUARIOS.temporal.username);
        assert(filaInactiva, "El usuario dado de baja desapareció del padrón: la baja debe ser lógica, no física.");
        assert(
            filaInactiva.cells[4] === "Inactivo",
            `El usuario dado de baja no quedó marcado como Inactivo: "${filaInactiva.cells[4]}".`
        );
        assert(
            !filaInactiva.actions.some((action) => action.accion === "baja-usuario"),
            "La fila del usuario inactivo sigue ofreciendo la acción de baja."
        );

        // Y deja de aparecer entre los activos: el filtro se resuelve en el backend.
        const getsActivos = findCalls(cdp, { method: "GET", url: "/api/usuarios?estado=activo" }).length;
        await setValue(cdp, "#filtro-usuario-estado", "activo");
        await waitCall(cdp, { method: "GET", url: "/api/usuarios?estado=activo", status: 200 });
        assert(
            findCalls(cdp, { method: "GET", url: "/api/usuarios?estado=activo" }).length > getsActivos,
            "El filtro de estado no disparó una consulta nueva al backend."
        );
        await cdp.waitFor(
            `return !Array.from(document.querySelectorAll('#lista-usuarios tbody tr'))
                .some(tr => tr.textContent.includes(${sel(USUARIOS.temporal.username)}));`,
            20000,
            "usuario inactivo fuera del listado de activos"
        );

        await click(cdp, "#btn-limpiar-filtros-usuarios");
        await cdp.waitFor(
            `return Array.from(document.querySelectorAll('#lista-usuarios tbody tr'))
                .some(tr => tr.textContent.includes(${sel(USUARIOS.temporal.username)}));`,
            20000,
            "padrón completo tras limpiar los filtros"
        );

        return {
            usuario: USUARIOS.temporal.username,
            id: temporal.id,
            modalMensaje: message,
            toast,
            deleteOk: true,
            estadoFinal: filaInactiva.cells[4],
            fueraDeActivos: true
        };
    });

    /* ---------------- 7. Matriz de permisos de un rol base (S18) ---------------- */
    await step(cdp, "roles-matriz", async () => {
        await click(cdp, "#tab-roles");
        await cdp.waitFor(
            "const seccion = document.querySelector('#seccion-roles'); return Boolean(seccion) && !seccion.hidden;",
            15000,
            "pestaña de roles"
        );
        assert(
            await isVisible(cdp, "#seccion-usuarios") === false,
            "La sección de usuarios siguió visible al abrir la pestaña de roles."
        );
        await cdp.waitFor(
            "return document.querySelectorAll('#lista-roles tbody tr').length >= 3;",
            20000,
            "los tres roles base del bootstrap"
        );

        const rolesBase = (await rowsOf(cdp, "#lista-roles")).filter((row) => row.badges.includes("Rol base"));
        assert(
            rolesBase.length >= 3,
            `El bootstrap debe dejar los tres roles base (S18/S23) y hay ${rolesBase.length}.`
        );
        for (const row of rolesBase) {
            assert(
                !row.actions.some((action) => action.accion === "eliminar-rol"),
                `Un rol base ofrece la acción de eliminar: ${row.text}.`
            );
        }

        await clickInRow(cdp, "#lista-roles", ROL_BASE_MATRIZ, '[data-accion="editar-rol"]');
        await cdp.waitFor(
            "const panel = document.querySelector('#panel-rol-permisos'); return Boolean(panel) && !panel.hidden;",
            15000,
            "panel de la matriz de permisos"
        );
        await cdp.waitFor(
            "return document.querySelectorAll('#matriz-permisos input[data-permission-name]').length > 0;",
            20000,
            "catálogo de permisos en la matriz"
        );

        const rolId = (await rowsOf(cdp, "#lista-roles"))
            .find((row) => row.text.includes(ROL_BASE_MATRIZ))
            ?.actions.find((action) => action.accion === "editar-rol")?.id || null;
        assert(rolId, `No se pudo leer el id del rol ${ROL_BASE_MATRIZ}.`);

        // La matriz llega completa y agrupada por módulo, con un checkbox por permiso.
        const matriz = await cdp.evaluate(`
            const boxes = Array.from(document.querySelectorAll('#matriz-permisos input[data-permission-name]'));
            const grupos = Array.from(document.querySelectorAll('#matriz-permisos [data-modulo]'))
                .map(nodo => nodo.dataset.modulo);
            return {
                total: boxes.length,
                marcados: boxes.filter(b => b.checked).length,
                sinModulo: boxes.filter(b => !b.dataset.modulo).length,
                sinNombre: boxes.filter(b => !b.dataset.permissionName).length,
                idFormato: boxes.every(b => b.id === 'permiso-' + b.dataset.permissionName),
                modulos: [...new Set(grupos)],
                marcadosNombres: boxes.filter(b => b.checked).map(b => b.dataset.permissionName)
            };
        `);
        assert(matriz.total > 0, "La matriz de permisos quedó vacía.");
        assert(matriz.sinModulo === 0 && matriz.sinNombre === 0,
            "Hay permisos sin data-modulo o sin data-permission-name en la matriz.");
        assert(matriz.idFormato, "Los checkboxes de la matriz no usan el id #permiso-<PERMISSION_NAME>.");
        assert(
            matriz.modulos.length >= 3,
            `La matriz no está agrupada por módulo: ${matriz.modulos.join(", ")}.`
        );
        assert(matriz.marcados > 0, `El rol base ${ROL_BASE_MATRIZ} quedó sin permisos en la matriz.`);

        // Rol base: no se renombra ni se elimina desde la UI.
        assert(
            await isDisabled(cdp, "#input-rol-nombre"),
            "El nombre de un rol base es editable: el backend lo rechaza con 409 y la UI debe bloquearlo antes."
        );
        assert(
            await isVisible(cdp, "#rol-base-aviso"),
            "La matriz de un rol base no explica que no se puede renombrar ni eliminar."
        );
        assert(
            !(await isVisible(cdp, "#btn-eliminar-rol")),
            "El botón de eliminar rol está visible para un rol base."
        );
        const eliminarOculto = await cdp.evaluate(
            "const b = document.querySelector('#btn-eliminar-rol'); return Boolean(b) && b.hidden === true;"
        );
        assert(eliminarOculto, "El botón #btn-eliminar-rol no está oculto (hidden) para un rol base.");

        const contadorOriginal = await textOf(cdp, "#contador-permisos-rol");
        assert(
            contadorOriginal.startsWith(`${matriz.marcados} de ${matriz.total}`),
            `El contador de permisos no coincide con los checkboxes marcados: "${contadorOriginal}".`
        );

        // Cambio mínimo y reversible: se quita el primer permiso marcado y se guarda.
        const quitado = await cdp.evaluate(`
            const box = Array.from(document.querySelectorAll('#matriz-permisos input[data-permission-name]'))
                .find(b => b.checked && !b.disabled);
            if (!box) return null;
            box.click();
            return { nombre: box.dataset.permissionName, quedoMarcado: box.checked };
        `);
        assert(quitado, "No se encontró ningún permiso marcado y editable en la matriz.");
        assert(quitado.quedoMarcado === false, `No se pudo desmarcar el permiso ${quitado.nombre}.`);

        const guardado = await guardarMatrizRol(cdp, rolId);
        assert(guardado.registrado, `No se pudo guardar la matriz del rol: ${guardado.error}`);
        await waitCall(cdp, { method: "PUT", url: `/api/roles/${rolId}`, status: 200 });

        const contadorTrasCambio = await textOf(cdp, "#contador-permisos-rol");
        assert(
            contadorTrasCambio.startsWith(`${matriz.marcados - 1} de ${matriz.total}`),
            `El contador no refleja el permiso quitado: "${contadorTrasCambio}".`
        );
        await cdp.waitFor(
            `const box = document.querySelector('#permiso-' + ${sel(quitado.nombre)}); return Boolean(box) && box.checked === false;`,
            20000,
            `permiso ${quitado.nombre} persistido como quitado`
        );

        // Restauración: el escenario tiene que dejar el rol como lo encontró.
        await cdp.evaluate(`
            const box = document.querySelector('#permiso-' + ${sel(quitado.nombre)});
            if (box && !box.checked) box.click();
            return true;
        `);
        const restaurado = await guardarMatrizRol(cdp, rolId);
        assert(restaurado.registrado, `No se pudo restaurar la matriz del rol: ${restaurado.error}`);
        await waitCall(cdp, { method: "PUT", url: `/api/roles/${rolId}`, status: 200 });
        assert(
            findCalls(cdp, { method: "PUT", url: `/api/roles/${rolId}`, status: 200 }).length >= 2,
            "No se registraron los dos PUT de la matriz (cambio + restauración)."
        );
        await cdp.waitFor(
            `const box = document.querySelector('#permiso-' + ${sel(quitado.nombre)}); return Boolean(box) && box.checked === true;`,
            20000,
            `permiso ${quitado.nombre} restaurado`
        );
        const contadorFinal = await textOf(cdp, "#contador-permisos-rol");
        assert(
            contadorFinal === contadorOriginal,
            `La matriz no volvió a su estado original: "${contadorFinal}" vs "${contadorOriginal}".`
        );

        return {
            rol: ROL_BASE_MATRIZ,
            rolId,
            permisos: matriz.total,
            modulos: matriz.modulos,
            permisosMarcados: matriz.marcados,
            permisoQuitado: quitado.nombre,
            nombreRolBloqueado: true,
            eliminarRolOculto: true,
            contadorOriginal,
            contadorFinal,
            putOk: true
        };
    });

    /* ---------------- 8. El selector de la agenda sale del padrón (S20) ---------------- */
    await step(cdp, "agenda-odontologo-disponible", async () => {
        await openModule(cdp, "agenda", "Agenda de citas");
        await click(cdp, "#btn-nueva-cita");
        await cdp.waitFor(
            "const panel = document.querySelector('#panel-cita-form'); return Boolean(panel) && !panel.hidden;",
            10000,
            "formulario de cita"
        );
        await cdp.waitFor(
            "return document.querySelectorAll('#cita-dentista option').length > 1;",
            20000,
            "odontólogos activos en el selector de la agenda"
        );

        const dentistas = await optionsOf(cdp, "#cita-dentista");
        const opcion = dentistas.find((option) => option.value === String(odontologo.id));
        assert(
            opcion,
            `El odontólogo creado (id ${odontologo.id}) no aparece en #cita-dentista: `
            + `${dentistas.filter((item) => item.value).map((item) => `${item.value}:${item.label}`).join(", ")}.`
        );
        assert(
            opcion.label.includes(USUARIOS.odontologo.nombre),
            `La opción del odontólogo no muestra su nombre: "${opcion.label}".`
        );
        assert(
            !(await isVisible(cdp, "#aviso-odontologos-agenda")),
            "La agenda sigue avisando que no hay odontólogos cargados con un odontólogo activo en el padrón."
        );
        assert(
            findCalls(cdp, { method: "GET", url: "/api/usuarios?rol=DENTISTA", status: 200 }).length > 0,
            "El selector de odontólogos no se alimentó de GET /api/usuarios?rol=DENTISTA."
        );
        assert(
            !(await isVisible(cdp, "#link-cargar-odontologos")),
            "El enlace para cargar odontólogos sigue visible con el padrón ya cargado."
        );

        return {
            odontologoId: odontologo.id,
            opcion: opcion.label,
            opciones: dentistas.filter((item) => item.value).map((item) => item.label),
            avisoOculto: true
        };
    });

    /* ---------------- 9. Alta de paciente (S1) ---------------- */
    const paciente = await step(cdp, "pacientes-crear", async () => {
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

        const fila = (await rowsOf(cdp, "#lista-pacientes")).find((row) => row.text.includes(PATIENT.email));
        assert(fila, "El paciente recién creado no aparece en el listado.");
        const linkHistorial = fila.links.find((href) => /\/historial$/.test(href || ""));
        assert(linkHistorial, `La fila del paciente no ofrece el historial clínico: ${fila.links.join(", ")}.`);
        const pacienteId = linkHistorial.match(/(\d+)/)?.[1] || null;
        assert(pacienteId, `No se pudo leer el id del paciente desde "${linkHistorial}".`);

        return {
            pacienteId,
            email: PATIENT.email,
            telefono: PATIENT.telefono,
            toast: outcome.toast,
            postOk: findCalls(cdp, { method: "POST", url: "/api/pacientes", status: 201 }).length > 0
        };
    });
    const pacienteId = paciente.pacienteId;

    /* ---------------- 10. Edición de los datos del paciente (S2) ---------------- */
    await step(cdp, "paciente-editar", async () => {
        await openModule(cdp, "pacientes", "Pacientes");
        await setValue(cdp, "#filtro-paciente", PATIENT.email);
        await click(cdp, "#btn-buscar-paciente");
        await cdp.waitFor(
            "return document.querySelectorAll('#lista-pacientes tbody tr').length === 1;",
            20000,
            "resultado único antes de editar"
        );
        assert(
            findCalls(cdp, { method: "GET", url: "/api/pacientes?texto=", status: 200 }).length > 0,
            "La búsqueda previa a la edición no llegó a GET /api/pacientes?texto=."
        );

        await clickInRow(cdp, "#lista-pacientes", PATIENT.email, '[data-accion="editar"]');
        await cdp.waitFor(
            "const panel = document.querySelector('#panel-paciente-form'); return Boolean(panel) && !panel.hidden;",
            10000,
            "formulario de edición de paciente"
        );
        const telefonoCargado = await cdp.evaluate("return document.querySelector('#input-telefono').value;");
        assert(
            telefonoCargado === PATIENT.telefono,
            `El formulario no cargó el teléfono original del paciente (${telefonoCargado}).`
        );
        const direccionCargada = await cdp.evaluate("return document.querySelector('#input-direccion').value;");
        assert(
            direccionCargada === PATIENT.direccion,
            `El formulario no cargó la dirección original del paciente (${direccionCargada}).`
        );

        await setValue(cdp, "#input-telefono", PATIENT.telefonoEditado);
        await setValue(cdp, "#input-direccion", PATIENT.direccionEditada);
        await clearToasts(cdp);
        await submitForm(cdp, "#form-paciente");
        const outcome = await waitFormOutcome(cdp, {
            errorSelector: "#form-paciente-error",
            busySelector: "#btn-guardar-paciente",
            successNeedle: "Paciente actualizado correctamente"
        });
        assert(outcome.ok, `No se pudo editar el paciente: ${outcome.error}`);
        await waitCall(cdp, { method: "PUT", url: `/api/pacientes/${pacienteId}`, status: 200 });

        // La tabla tiene que mostrar el dato nuevo: no alcanza con que el formulario
        // lo haya aceptado.
        await cdp.waitFor(
            `return (document.querySelector('#lista-pacientes')?.textContent || '').includes(${sel(PATIENT.telefonoEditado)});`,
            20000,
            "teléfono editado en el listado"
        );
        const fila = (await rowsOf(cdp, "#lista-pacientes")).find((row) => row.text.includes(PATIENT.email));
        assert(fila, "El paciente editado desapareció del listado.");
        assert(
            fila.cells[3] === PATIENT.telefonoEditado,
            `El listado no muestra el teléfono editado: "${fila.cells[3]}".`
        );
        assert(
            fila.cells[5] === PATIENT.direccionEditada,
            `El listado no muestra la dirección editada: "${fila.cells[5]}".`
        );
        assert(
            !fila.cells[3].includes(PATIENT.telefono),
            `El listado sigue mostrando el teléfono anterior del paciente: "${fila.cells[3]}".`
        );

        return {
            pacienteId,
            telefonoAnterior: telefonoCargado,
            telefonoNuevo: fila.cells[3],
            direccionNueva: fila.cells[5],
            toast: outcome.toast,
            putOk: true
        };
    });

    /* ---------------- 11. Catálogo: bootstrap + alta, edición y 409 (S7) ---------------- */
    const tratamiento = await step(cdp, "tratamientos-crear", async () => {
        await openModule(cdp, "tratamientos", "Catálogo de tratamientos");
        await cdp.waitFor(
            "return document.querySelectorAll('#lista-tratamientos tbody tr').length > 0;",
            20000,
            "catálogo de tratamientos"
        );
        assert(
            (await countOf(cdp, "#lista-tratamientos .state-box-error")) === 0,
            "El catálogo de tratamientos muestra un estado de error."
        );

        // El bootstrap de producción deja el catálogo base (S23): el escenario no lo
        // crea, solo comprueba que existe y que agrega uno propio.
        const bootstrap = await rowsOf(cdp, "#lista-tratamientos");
        const base = bootstrap.filter((row) => /ODO-\d{3}/.test(row.text));
        assert(
            base.length > 0,
            `El catálogo bootstrap (códigos ODO-###) no está cargado: ${bootstrap.map((row) => row.cells[0]).join(", ")}.`
        );

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
        const creado = await waitFormOutcome(cdp, {
            errorSelector: "#form-tratamiento-error",
            busySelector: "#btn-guardar-tratamiento",
            successNeedle: `Tratamiento ${TREATMENT.codigo} creado correctamente`
        });
        assert(creado.ok, `No se pudo crear el tratamiento: ${creado.error}`);
        assert(
            findCalls(cdp, { method: "POST", url: "/api/tratamientos", status: 201 }).length > 0,
            "El alta del tratamiento no llegó a POST /api/tratamientos (201)."
        );

        await setValue(cdp, "#filtro-tratamiento", TREATMENT.codigo);
        await cdp.waitFor(
            "return document.querySelectorAll('#lista-tratamientos tbody tr').length === 1;",
            20000,
            "tratamiento filtrado en el catálogo"
        );
        const fila = (await rowsOf(cdp, "#lista-tratamientos"))[0];
        assert(fila.text.includes(TREATMENT.nombre), "El catálogo no muestra el tratamiento recién creado.");

        // Edición del precio recién creado.
        await clickInRow(cdp, "#lista-tratamientos", TREATMENT.codigo, ".btn-outline");
        await cdp.waitFor(
            "const panel = document.querySelector('#panel-tratamiento-form'); return Boolean(panel) && !panel.hidden;",
            10000,
            "formulario de edición de tratamiento"
        );
        const codigoCargado = await cdp.evaluate("return document.querySelector('#input-codigo').value;");
        assert(
            codigoCargado === TREATMENT.codigo,
            `El formulario no cargó el código ${TREATMENT.codigo} (${codigoCargado}).`
        );
        await setValue(cdp, "#input-precio", TREATMENT.precioEditado);
        await clearToasts(cdp);
        await submitForm(cdp, "#form-tratamiento");
        const editado = await waitFormOutcome(cdp, {
            errorSelector: "#form-tratamiento-error",
            busySelector: "#btn-guardar-tratamiento",
            successNeedle: "Tratamiento actualizado correctamente"
        });
        assert(editado.ok, `No se pudo editar el precio del tratamiento: ${editado.error}`);
        assert(
            findCalls(cdp, { method: "PUT", url: "/api/tratamientos/", status: 200 }).length > 0,
            "La edición del tratamiento no llegó a PUT /api/tratamientos/{id} (200)."
        );

        // Reabrir el formulario comprueba que el precio persistió (sin depender del locale).
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

        // El código duplicado del tratamiento nuevo sigue devolviendo 409 (S7).
        await click(cdp, "#btn-nuevo-tratamiento");
        await cdp.waitFor(
            "const panel = document.querySelector('#panel-tratamiento-form'); return Boolean(panel) && !panel.hidden;",
            10000,
            "formulario de alta de tratamiento duplicado"
        );
        await setValue(cdp, "#input-codigo", TREATMENT.codigo);
        await setValue(cdp, "#input-nombre", `${TREATMENT.nombre} duplicado`);
        await setValue(cdp, "#input-precio", "99");
        await setValue(cdp, "#input-duracion", "15");
        await clearToasts(cdp);
        await submitForm(cdp, "#form-tratamiento");
        const duplicado = await waitFormOutcome(cdp, {
            errorSelector: "#form-tratamiento-error",
            busySelector: "#btn-guardar-tratamiento",
            successNeedle: "creado correctamente"
        });
        assert(!duplicado.ok, "El alta con código duplicado fue aceptada: el backend debía responder 409.");
        assert(
            /ya existe un tratamiento con el codigo/i.test(duplicado.error)
            && duplicado.error.includes(TREATMENT.codigo),
            `El formulario no mostró el mensaje de código duplicado: "${duplicado.error}".`
        );
        assert(
            findCalls(cdp, { method: "POST", url: "/api/tratamientos", status: 409 }).length > 0,
            "El backend no respondió 409 al código duplicado."
        );
        assert(
            await isVisible(cdp, "#panel-tratamiento-form"),
            "El formulario se cerró pese al error de código duplicado."
        );
        await click(cdp, "#btn-cancelar-tratamiento");

        return {
            codigo: TREATMENT.codigo,
            nombre: TREATMENT.nombre,
            precio: Number(TREATMENT.precioEditado),
            precioPersistido,
            tratamientosBootstrap: base.length,
            toastCreacion: creado.toast,
            toastEdicion: editado.toast,
            postOk: true,
            putOk: true,
            conflictoDuplicado: duplicado.error
        };
    }, { expectedHttp: [{ url: "/api/tratamientos", status: 409 }] });

    /* ---------------- 12. Programación de la cita con el odontólogo real (S4) ---------------- */
    const agenda = await step(cdp, "agenda-crear", async () => {
        await openModule(cdp, "agenda", "Agenda de citas");
        const baseMinute = (Math.floor(Date.now() / 60000) % 50) + 5;
        const created = await programarCita(cdp, {
            pacienteId,
            tratamientoNombre: TREATMENT.nombre,
            dentistaId: odontologo.id,
            motivo: CITA_MOTIVO,
            slots: [
                futureSlot(9, baseMinute),
                futureSlot(10, (baseMinute + 7) % 60),
                futureSlot(12, (baseMinute + 17) % 60),
                futureSlot(15, (baseMinute + 23) % 60)
            ]
        });

        await cdp.waitFor(
            `return (document.querySelector('#lista-citas')?.textContent || '').includes(${sel(CITA_MOTIVO)});`,
            20000,
            "cita en la agenda"
        );
        assert(
            findCalls(cdp, { method: "POST", url: "/api/citas", status: 201 }).length > 0,
            "La programación de la cita no llegó a POST /api/citas (201)."
        );

        const row = (await rowsOf(cdp, "#lista-citas")).find((item) => item.text.includes(CITA_MOTIVO));
        assert(row, "La cita creada no aparece en la agenda del período.");
        assert(row.badges.includes("Programada"), `La cita no nació en estado Programada: ${row.badges.join(", ")}.`);
        const citaId = row.actions.find((action) => action.accion === "reprogramar")?.id || null;
        assert(citaId, "No se pudo leer el id de la cita creada desde la fila de la agenda.");

        return {
            citaId,
            fechaHora: created.slot,
            dentistaId: odontologo.id,
            motivo: CITA_MOTIVO,
            estado: row.badges[0] || "",
            toast: created.toast,
            postOk: true
        };
    });

    /* ---------------- 13. Reprogramación de la cita (S6) ---------------- */
    const reprogramada = await step(cdp, "agenda-reprogramar", async () => {
        await click(cdp, `#lista-citas [data-accion="reprogramar"][data-id="${agenda.citaId}"]`);
        await cdp.waitFor(
            "const panel = document.querySelector('#panel-reprogramar-form'); return Boolean(panel) && !panel.hidden;",
            10000,
            "formulario de reprogramación"
        );
        // El odontólogo no se toca: el PATCH acepta idDentista nulo y conserva el de
        // la cita original, que es lo que verifica este paso.
        const dentistaPreseleccionado = await cdp.evaluate(
            "return document.querySelector('#reprogramar-dentista')?.value || '';"
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
        await waitCall(cdp, { method: "PATCH", url: `/api/citas/${agenda.citaId}/reprogramar`, status: 200 });

        await cdp.waitFor(
            `return Array.from(document.querySelectorAll('#lista-citas tbody tr'))
                .filter(tr => tr.textContent.includes(${sel(CITA_MOTIVO)})).length === 2;`,
            25000,
            "citas original y reprogramada en la agenda"
        );
        const rows = (await rowsOf(cdp, "#lista-citas")).filter((row) => row.text.includes(CITA_MOTIVO));
        assert(rows.length === 2, `Se esperaban 2 filas (original + nueva) con el motivo, y hay ${rows.length}.`);
        const original = rows.find((row) => row.badges.includes("Reprogramada")
            && row.actions.some((action) => action.id === agenda.citaId));
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
            fechaHoraOriginal: agenda.fechaHora,
            dentistaPreseleccionado: dentistaPreseleccionado || null,
            toast: done.toast,
            patchOk: true
        };
    });

    /* ---------------- 14. Cancelación de una cita aparte (S6) ---------------- */
    const cancelada = await step(cdp, "agenda-cancelar", async () => {
        // La cita reprogramada se conserva a propósito: los pasos posteriores
        // (realizado, reportes y bitácora de recordatorios) se apoyan en ella.
        // La cancelación se ejerce sobre una cita B creada dentro del paso.
        const baseMinute = (Math.floor(Date.now() / 60000) % 40) + 13;
        const creada = await programarCita(cdp, {
            pacienteId,
            tratamientoNombre: TREATMENT.nombre,
            dentistaId: odontologo.id,
            motivo: CITA_B_MOTIVO,
            slots: [
                futureSlot(20, baseMinute),
                futureSlot(22, (baseMinute + 11) % 60),
                futureSlot(25, (baseMinute + 23) % 60)
            ]
        });
        await waitCall(cdp, { method: "POST", url: "/api/citas", status: 201 });

        await cdp.waitFor(
            `return (document.querySelector('#lista-citas')?.textContent || '').includes(${sel(CITA_B_MOTIVO)});`,
            25000,
            "cita B en la agenda"
        );
        const filaB = (await rowsOf(cdp, "#lista-citas")).find((row) => row.text.includes(CITA_B_MOTIVO));
        assert(filaB, "La cita B no aparece en la agenda del período.");
        assert(filaB.badges.includes("Programada"), `La cita B no nació Programada: ${filaB.badges.join(", ")}.`);
        const citaBId = filaB.actions.find((action) => action.accion === "cancelar")?.id || null;
        assert(citaBId, "No se pudo leer el id de la cita B desde la fila de la agenda.");

        await click(cdp, `#lista-citas [data-accion="cancelar"][data-id="${citaBId}"]`);
        const message = await waitModal(cdp);
        assert(/Cancelar la cita/i.test(message), `El diálogo de confirmación no es el de cancelación: "${message}".`);
        assert(
            /recordatorios pendientes/i.test(message),
            `El diálogo de cancelación no anuncia que anula los recordatorios pendientes (S5/S6): "${message}".`
        );

        await clearToasts(cdp);
        await confirmModal(cdp);
        const toast = await waitToast(cdp, "Cita cancelada correctamente");
        // El contrato real de S6 responde 204 (noContent) al cancelar.
        await waitCall(cdp, { method: "PATCH", url: `/api/citas/${citaBId}/cancelar`, status: 204 });

        await cdp.waitFor(
            `return Array.from(document.querySelectorAll('#lista-citas tbody tr'))
                .some(tr => tr.textContent.includes(${sel(CITA_B_MOTIVO)}) && tr.textContent.includes('Cancelada'));`,
            25000,
            "cita B en estado Cancelada"
        );
        const filaCancelada = (await rowsOf(cdp, "#lista-citas")).find((row) => row.text.includes(CITA_B_MOTIVO));
        assert(
            filaCancelada.badges.includes("Cancelada"),
            `La cita B no quedó Cancelada: ${filaCancelada.badges.join(", ")}.`
        );

        // Cancelar la cita B no puede tocar la cita reprogramada de la corrida.
        const reprogramadaVigente = (await rowsOf(cdp, "#lista-citas"))
            .some((row) => row.text.includes(CITA_MOTIVO) && row.badges.includes("Programada"));
        assert(
            reprogramadaVigente,
            "La cancelación de la cita B alteró la cita reprogramada que usan los pasos posteriores."
        );

        return {
            citaBId,
            motivo: CITA_B_MOTIVO,
            fechaHora: creada.slot,
            estado: "Cancelada",
            citaReprogramadaIntacta: reprogramada.nuevaId,
            modalMensaje: message,
            toast,
            patchStatus: 204,
            postOk: true,
            patchOk: true
        };
    });

    /* ---------------- 15. Tratamiento realizado desde el historial (S3, S8) ---------------- */
    await step(cdp, "realizado-registrar", async () => {
        await openModule(cdp, "pacientes", "Pacientes");
        await setValue(cdp, "#filtro-paciente", PATIENT.email);
        await click(cdp, "#btn-buscar-paciente");
        await cdp.waitFor(
            "return document.querySelectorAll('#lista-pacientes tbody tr').length === 1;",
            20000,
            "un único resultado de búsqueda"
        );
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
        const cuerpoHistorial = await textOf(cdp, "#historial-contenido");
        assert(
            cuerpoHistorial.includes(PATIENT.email),
            "La ficha del historial no muestra el correo del paciente creado."
        );
        assert(
            cuerpoHistorial.includes(CITA_MOTIVO),
            "El historial del paciente no incluye la cita programada en el paso anterior."
        );
        assert(
            cuerpoHistorial.includes("todavía no tiene tratamientos registrados"),
            "El historial de un paciente recién creado ya muestra tratamientos realizados."
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

        await selectOptionByText(cdp, "#realizado-tratamiento", TREATMENT.nombre);
        await setValue(cdp, "#realizado-dentista", odontologo.id);
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
        assert(
            findCalls(cdp, { method: "POST", url: "/api/tratamientos-realizados", status: 201 }).length > 0,
            "El registro del procedimiento no llegó a POST /api/tratamientos-realizados (201)."
        );

        await cdp.waitFor(
            `return (document.querySelector('#historial-contenido')?.textContent || '').includes(${sel(DETALLE_REALIZADO)});`,
            25000,
            "tratamiento realizado en el historial"
        );
        const row = (await rowsOf(cdp, "#historial-contenido .data-table"))
            .find((item) => item.text.includes(DETALLE_REALIZADO));
        assert(row, "El tratamiento realizado no aparece en la tabla del historial.");
        const costo = parseMoney(row.cells.at(-1));
        assert(
            costo === tratamiento.precio,
            `El costo congelado del realizado no coincide: se esperaba ${tratamiento.precio} y llegó ${costo}.`
        );

        return {
            detalle: DETALLE_REALIZADO,
            costo,
            pacienteId,
            toast: outcome.toast,
            postOk: true
        };
    });

    /* ---------------- 16. Emisión del comprobante (S9) ---------------- */
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
        assert(
            findCalls(cdp, { method: "POST", url: "/api/facturas", status: 201 }).length > 0,
            "La emisión no llegó a POST /api/facturas (201)."
        );

        // El listado se recarga después del toast de éxito, así que contar filas no
        // alcanza: mientras recarga, la única fila es el mensaje de "sin
        // coincidencias" y el paso leería ese texto como número de comprobante.
        await setValue(cdp, "#filtro-factura", STAMP);
        await cdp.waitFor(
            "const first = document.querySelector('#lista-facturas tbody tr td'); return !!first && first.textContent.trim().startsWith('F-');",
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
        assert(total === tratamiento.precio, `El total del comprobante no coincide: ${total}.`);
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
            postOk: true
        };
    });

    /** Reads the invoice detail panel (labels + amounts) and its payment table. */
    async function readDetalle(cdp) {
        // El panel se monta cuando termina el GET del comprobante: esperar el contenido
        // evita leer un detalle a medio pintar y reportar un falso "no esta montado".
        await cdp.waitFor(
            "return Boolean(document.querySelector('#detalle-factura .detail-list'));",
            20000,
            "detalle del comprobante con contenido"
        );
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

    /* ---------------- 17. Pago parcial -> PAGO_PARCIAL (S10) ---------------- */
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
        assert(
            parseMoney(inicial.fields.Saldo) === factura.saldo,
            `El saldo del detalle no coincide con el listado: "${inicial.fields.Saldo}".`
        );
        assert(
            inicial.lineas.some((linea) => linea.includes(`#${factura.realizadoId}`)),
            "La línea del comprobante no referencia el tratamiento realizado facturado."
        );
        assert(
            inicial.pagos.length === 1 && inicial.pagos[0].includes("Todavía no se registraron pagos"),
            "El comprobante recién emitido ya muestra pagos registrados."
        );

        // Un pago menor al saldo: S10 deja el comprobante en PAGO_PARCIAL, no en PAGADA.
        assert(
            PAGO_PARCIAL > 0 && PAGO_PARCIAL < factura.total,
            `El pago parcial (${PAGO_PARCIAL}) tiene que ser mayor que 0 y menor que el total (${factura.total}).`
        );
        const referenciaParcial = `${PAGO_REFERENCIA}-1`;
        await setValue(cdp, "#pago-monto", String(PAGO_PARCIAL));
        await selectOptionByText(cdp, "#pago-metodo", PAGO_METODO_LABEL);
        await setValue(cdp, "#pago-referencia", referenciaParcial);
        await clearToasts(cdp);
        await submitForm(cdp, "#form-pago");
        const outcome = await waitFormOutcome(cdp, {
            errorSelector: "#form-pago-error",
            busySelector: "#btn-registrar-pago",
            successNeedle: "Pago de"
        });
        assert(outcome.ok, `No se pudo registrar el pago parcial: ${outcome.error}`);
        assert(
            findCalls(cdp, { method: "POST", url: `/api/facturas/${factura.facturaId}/pagos`, status: 201 }).length > 0,
            "El pago parcial no llegó a POST /api/facturas/{id}/pagos (201)."
        );

        await cdp.waitFor(
            "return (document.querySelector('#panel-detalle-factura .card-header-actions .badge')?.textContent || '').trim() === 'Pago Parcial';",
            25000,
            "estado Pago Parcial"
        );
        const parcial = await readDetalle(cdp);
        const saldo = parseMoney(parcial.fields.Saldo);
        const pagado = parseMoney(parcial.fields.Pagado);
        assert(
            saldo === factura.total - PAGO_PARCIAL,
            `El saldo tras el pago parcial es ${saldo} y se esperaba ${factura.total - PAGO_PARCIAL}.`
        );
        assert(pagado === PAGO_PARCIAL, `El total pagado tras el pago parcial es ${pagado}.`);
        assert(parcial.pagos.length === 1, `Se esperaba 1 pago registrado y hay ${parcial.pagos.length}.`);
        assert(
            parcial.pagos.some((linea) => linea.includes(referenciaParcial)),
            "El pago parcial no aparece en la tabla de pagos del comprobante."
        );
        assert(
            !/no queda saldo por cobrar/i.test(parcial.aviso),
            `El comprobante con saldo pendiente ya anuncia que no queda nada por cobrar: "${parcial.aviso}".`
        );

        return {
            monto: PAGO_PARCIAL,
            metodo: PAGO_METODO_LABEL,
            referencia: referenciaParcial,
            estado: "Pago Parcial",
            saldo,
            pagado,
            pagosRegistrados: parcial.pagos.length,
            toast: outcome.toast,
            postOk: true
        };
    });

    /* ---------------- 18. Pago del saldo restante -> PAGADA (S10) ---------------- */
    await step(cdp, "factura-pago-total", async () => {
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
        assert(
            inicial.estadoBadge === "Pago Parcial",
            `El detalle debía seguir en Pago Parcial tras el pago parcial: "${inicial.estadoBadge}".`
        );
        assert(
            inicial.lineas.some((linea) => linea.includes(`#${factura.realizadoId}`)),
            "La línea del comprobante no referencia el tratamiento realizado facturado."
        );
        assert(
            inicial.pagos.length === 1,
            `El comprobante con un pago parcial debe listar exactamente 1 pago y lista ${inicial.pagos.length}.`
        );

        // Cobrar el saldo restante: S10 deja la factura en PAGADA.
        const saldoPendiente = parseMoney(inicial.fields.Saldo);
        assert(
            saldoPendiente === factura.total - PAGO_PARCIAL,
            `El saldo a cobrar no es el que dejó el pago parcial: ${saldoPendiente}.`
        );
        await setValue(cdp, "#pago-monto", String(saldoPendiente));
        await selectOptionByText(cdp, "#pago-metodo", PAGO_METODO_LABEL);
        await setValue(cdp, "#pago-referencia", PAGO_REFERENCIA);
        await clearToasts(cdp);
        await submitForm(cdp, "#form-pago");
        const outcome = await waitFormOutcome(cdp, {
            errorSelector: "#form-pago-error",
            busySelector: "#btn-registrar-pago",
            successNeedle: "Pago de"
        });
        assert(outcome.ok, `No se pudo registrar el pago total: ${outcome.error}`);
        assert(
            findCalls(cdp, { method: "POST", url: `/api/facturas/${factura.facturaId}/pagos`, status: 201 }).length > 0,
            "El pago no llegó a POST /api/facturas/{id}/pagos (201)."
        );

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
        assert(
            /no queda saldo por cobrar/i.test(final.aviso),
            `El detalle no anuncia que no queda saldo por cobrar: "${final.aviso}".`
        );
        assert(final.pagos.length === 2, `Se esperaban 2 pagos registrados (parcial + saldo) y hay ${final.pagos.length}.`);
        assert(
            final.pagos.some((linea) => linea.includes(PAGO_REFERENCIA)),
            "El pago registrado no aparece en la tabla de pagos del comprobante."
        );

        // La fila del listado tiene que coincidir con el panel de detalle.
        await setValue(cdp, "#filtro-factura", STAMP);
        await cdp.waitFor(
            "const first = document.querySelector('#lista-facturas tbody tr td'); return !!first && first.textContent.trim().startsWith('F-');",
            20000,
            "comprobante pagado en el listado"
        );
        const row = (await rowsOf(cdp, "#lista-facturas"))[0];
        assert(row.badges[0] === "Pagada", `El listado no muestra el comprobante como Pagada: "${row.badges[0]}".`);

        return {
            monto: saldoPendiente,
            metodo: PAGO_METODO_LABEL,
            referencia: PAGO_REFERENCIA,
            estado: "Pagada",
            saldo,
            pagado,
            pagosRegistrados: final.pagos.length,
            aviso: final.aviso,
            listadoEstado: row.badges[0],
            listadoSaldo: row.cells[6],
            toast: outcome.toast,
            postOk: true
        };
    });

    /* ---------------- 19. Comprobante PDF (S9) ---------------- */
    await step(cdp, "factura-pdf", async () => {
        if (!(await isVisible(cdp, "#detalle-factura"))) {
            await click(cdp, `#lista-facturas [data-accion="detalle"][data-id="${factura.facturaId}"]`);
            await cdp.waitFor(
                "return Boolean(document.querySelector('#detalle-factura .detail-list'));",
                20000,
                "detalle del comprobante para el PDF"
            );
        }

        // The module fetches the PDF with the session token and then hands the
        // blob to the browser: inspect the real HTTP response instead of
        // trusting the download prompt (which headless Chrome cannot show).
        // Chrome no expone el cuerpo de una respuesta que la pagina consume como blob:
        // Network.getResponseBody devuelve una firma vacia. El hook se instala en la propia
        // pagina, envuelve window.fetch y clona la respuesta del PDF para guardar los bytes
        // reales que recibio la aplicacion.
        await cdp.evaluate(`
            if (!window.__smokePdfHook) {
                window.__smokePdfHook = true;
                window.__smokePdf = null;
                const originalFetch = window.fetch;
                window.fetch = async (...args) => {
                    const response = await originalFetch(...args);
                    const url = typeof args[0] === "string" ? args[0] : (args[0] && args[0].url) || "";
                    if (url.includes("/api/facturas/") && url.includes("/pdf")) {
                        try {
                            const bytes = new Uint8Array(await response.clone().arrayBuffer());
                            window.__smokePdf = {
                                status: response.status,
                                contentType: response.headers.get("content-type") || "",
                                firma: Array.from(bytes.slice(0, 8)),
                                total: bytes.length,
                                data: Array.from(bytes)
                            };
                        } catch (error) {
                            window.__smokePdf = { error: String(error) };
                        }
                    }
                    return response;
                };
            }
            window.__smokePdf = null;
            return true;
        `);

        const clicked = await cdp.evaluate(`
            const root = document.querySelector('#detalle-factura');
            if (!root) return false;
            const button = Array.from(root.querySelectorAll('button'))
                .find(node => node.textContent.includes('Descargar PDF'));
            if (!button) return false;
            button.click();
            return true;
        `);
        assert(clicked, "No se encontro el boton 'Descargar PDF' del detalle del comprobante.");

        const captured = await withTimeout((async () => {
            for (let intento = 0; intento < 300; intento += 1) {
                const value = await cdp.evaluate("return window.__smokePdf;");
                if (value) return value;
                await sleep(100);
            }
            return null;
        })(), 40000, "el comprobante PDF");

        assert(captured, "La aplicacion no descargo el comprobante: no hubo peticion al PDF.");
        assert(!captured.error, `La descarga del comprobante fallo: ${captured.error}`);
        const pdf = {
            status: captured.status,
            contentType: captured.contentType,
            bytes: captured.total,
            magic: Buffer.from(captured.firma).subarray(0, 5).toString("latin1"),
            buffer: Buffer.from(captured.data)
        };
        assert(pdf.status === 200, `El comprobante respondio HTTP ${pdf.status} en lugar de 200.`);
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
    });

    /* ---------------- 20. Auditoría de las operaciones de la corrida (S19) ---------------- */
    await step(cdp, "auditoria-ver", async () => {
        await openModule(cdp, "auditoria", "Auditoría");
        await cdp.waitFor(
            "return document.querySelectorAll('#auditoria-lista tbody tr').length > 0;",
            25000,
            "bitácora de auditoría con registros"
        );
        assert(
            (await countOf(cdp, "#auditoria-lista .state-box-error")) === 0,
            "La bitácora de auditoría muestra un estado de error."
        );
        assert(
            findCalls(cdp, { method: "GET", url: "/api/auditoria", status: 200 }).length > 0,
            "La pantalla de auditoría no consultó GET /api/auditoria."
        );

        const contadorInicial = await textOf(cdp, "#auditoria-contador");
        const totalInicial = parseCounterTotal(contadorInicial);
        assert(
            totalInicial !== null && totalInicial > 0,
            `El contador de la bitácora no informa registros: "${contadorInicial}".`
        );

        const filas = await rowsOf(cdp, "#auditoria-lista");
        assert(filas.length > 0, "La tabla de auditoría no tiene filas.");
        for (const [index, row] of filas.entries()) {
            assert(row.cells[0] && row.cells[0].length > 0, `La fila ${index + 1} no muestra la fecha.`);
            assert(row.cells[1] && row.cells[1].length > 0, `La fila ${index + 1} no muestra la acción.`);
            assert(row.cells[2] && row.cells[2].length > 0, `La fila ${index + 1} no muestra la tabla afectada.`);
        }

        // El detalle JSONB llega plegado: al abrirlo se formatea dentro de un <pre>.
        const detalle = await cdp.evaluate(`
            const details = document.querySelector('#auditoria-lista details.auditoria-detalle');
            if (!details) return null;
            details.open = true;
            return { idLog: details.dataset.idLog || '' };
        `);
        assert(detalle && detalle.idLog, "La bitácora no expone el detalle de cada registro con data-id-log.");
        await cdp.waitFor(
            "const pre = document.querySelector('#auditoria-lista details.auditoria-detalle .auditoria-detalle-json');"
            + " return Boolean(pre) && !pre.hidden && pre.textContent.trim().length > 1;",
            10000,
            "detalle de auditoría desplegado"
        );
        const detalleTexto = await textOf(cdp, "#auditoria-lista details.auditoria-detalle .auditoria-detalle-json");
        assert(detalleTexto.length > 1, "El detalle desplegado de la auditoría quedó vacío.");

        // Las operaciones de esta corrida tienen que estar auditadas (S19) y el filtro
        // por acción tiene que resolverlas en el backend.
        const acciones = [];
        for (const esperada of AUDITADAS_ESPERADAS) {
            const getAntes = findCalls(cdp, { method: "GET", url: "/api/auditoria" }).length;
            await setValue(cdp, "#filtro-auditoria-accion", esperada.codigo);
            await waitCall(cdp, { method: "GET", url: `accion=${esperada.codigo}`, status: 200 });
            await cdp.waitFor(
                `return Array.from(document.querySelectorAll('#auditoria-lista tbody tr'))
                    .some(tr => tr.textContent.includes(${sel(esperada.etiqueta)}));`,
                25000,
                `registros auditados de ${esperada.codigo}`
            );
            const filtradas = await rowsOf(cdp, "#auditoria-lista");
            assert(filtradas.length > 0, `El filtro ${esperada.codigo} no devolvió ninguna fila.`);
            assert(
                filtradas.every((row) => row.cells[1] === esperada.etiqueta),
                `El filtro ${esperada.codigo} mezcló otras acciones: ${filtradas.map((row) => row.cells[1]).join(", ")}.`
            );
            assert(
                findCalls(cdp, { method: "GET", url: "/api/auditoria" }).length > getAntes,
                `El filtro de acción no disparó una consulta nueva al backend para ${esperada.codigo}.`
            );
            const conAccion = findCalls(cdp, { method: "GET", url: `accion=${esperada.codigo}` }).length;
            assert(
                conAccion > 0,
                `La consulta filtrada no envió accion=${esperada.codigo} al backend.`
            );
            acciones.push({
                codigo: esperada.codigo,
                etiqueta: esperada.etiqueta,
                filas: filtradas.length,
                total: parseCounterTotal(await textOf(cdp, "#auditoria-contador"))
            });
        }

        // Limpiar filtros vuelve a la bitácora completa.
        const getAntesLimpiar = findCalls(cdp, { method: "GET", url: "/api/auditoria" }).length;
        await click(cdp, "#btn-limpiar-filtros-auditoria");
        await cdp.waitFor(
            `return (document.querySelector('#filtro-auditoria-accion')?.value || '') === '';`,
            10000,
            "filtros de auditoría limpios"
        );
        await cdp.waitFor(
            "return document.querySelectorAll('#auditoria-lista tbody tr').length > 0"
            + " && !document.querySelector('#auditoria-lista .state-box');",
            25000,
            "bitácora completa tras limpiar los filtros"
        );
        assert(
            findCalls(cdp, { method: "GET", url: "/api/auditoria" }).length > getAntesLimpiar,
            "Limpiar los filtros no volvió a consultar la bitácora."
        );

        // Paginación: con una sola página "Siguiente" queda deshabilitado; con varias,
        // el botón navega y "Anterior" se habilita en la segunda.
        assert(
            await isVisible(cdp, "#auditoria-paginacion"),
            "La barra de paginación de la auditoría no se muestra con registros."
        );
        const etiquetaPagina = await textOf(cdp, "#auditoria-pagina");
        const partes = /Página\s+(\d+)\s+de\s+(\d+)/i.exec(etiquetaPagina);
        assert(partes, `La etiqueta de página no tiene el formato esperado: "${etiquetaPagina}".`);
        const paginaActual = Number(partes[1]);
        const totalPaginas = Number(partes[2]);
        assert(paginaActual === 1, `La bitácora no arranca en la página 1: "${etiquetaPagina}".`);
        assert(
            await isDisabled(cdp, "#btn-auditoria-anterior"),
            "El botón 'Anterior' está habilitado en la primera página."
        );

        let paginacion = { totalPaginas, navegada: false };
        if (totalPaginas === 1) {
            assert(
                await isDisabled(cdp, "#btn-auditoria-siguiente"),
                "Con una sola página el botón 'Siguiente' debe quedar deshabilitado."
            );
        } else {
            await click(cdp, "#btn-auditoria-siguiente");
            await cdp.waitFor(
                "return /Página\\s+2\\s+de/i.test(document.querySelector('#auditoria-pagina')?.textContent || '');",
                25000,
                "segunda página de la bitácora"
            );
            assert(
                !(await isDisabled(cdp, "#btn-auditoria-anterior")),
                "El botón 'Anterior' no se habilitó en la segunda página."
            );
            assert(
                findCalls(cdp, { method: "GET", url: "pagina=1" }).length > 0,
                "El botón 'Siguiente' no pidió la página 1 (base 0) al backend."
            );
            await click(cdp, "#btn-auditoria-anterior");
            await cdp.waitFor(
                "return /Página\\s+1\\s+de/i.test(document.querySelector('#auditoria-pagina')?.textContent || '');",
                25000,
                "vuelta a la primera página"
            );
            paginacion = { totalPaginas, navegada: true };
        }

        return {
            contador: contadorInicial,
            totalRegistros: totalInicial,
            filasPrimeraPagina: filas.length,
            detalle: { idLog: detalle.idLog, longitud: detalleTexto.length },
            accionesFiltradas: acciones,
            paginacion,
            etiquetaPagina
        };
    });

    /* ---------------- 21. Reportes y rango inválido (S21) ---------------- */
    await step(cdp, "reportes-ver", async () => {
        await openModule(cdp, "reportes", "Reportes");
        await cdp.waitFor(
            "return ['#bloque-ingresos', '#bloque-produccion', '#bloque-ocupacion']"
            + ".every(s => Boolean(document.querySelector(s)));",
            20000,
            "los tres bloques de reportes"
        );
        await cdp.waitFor(
            "return document.querySelectorAll('#tabla-ingresos-metodo tbody tr').length > 0"
            + " && document.querySelectorAll('#tabla-produccion-odontologo tbody tr').length > 0;",
            25000,
            "ingresos y producción con datos"
        );
        await settle(cdp);

        assert(
            (await countOf(cdp, "#app-view .state-box-error")) === 0,
            "Los reportes muestran un estado de error con datos en el período."
        );
        assert(
            findCalls(cdp, { method: "GET", url: "/api/reportes/ingresos", status: 200 }).length > 0
            && findCalls(cdp, { method: "GET", url: "/api/reportes/produccion", status: 200 }).length > 0
            && findCalls(cdp, { method: "GET", url: "/api/reportes/ocupacion", status: 200 }).length > 0,
            "La pantalla de reportes no consultó los tres endpoints de S21."
        );

        const kpiCobradoTexto = await textOf(cdp, "#kpi-total-cobrado .kpi-stat-value");
        assert(
            /^\d{1,3}(\.\d{3})*,\d{2}\s*US\$$/.test(kpiCobradoTexto.trim()),
            `El KPI de ingresos no usa el formato monetario de la app (es-ES/USD): "${kpiCobradoTexto}".`
        );
        const kpiCobrado = parseMoney(kpiCobradoTexto);
        assert(
            kpiCobrado !== null && kpiCobrado >= factura.total,
            `El total cobrado (${kpiCobradoTexto}) no incluye el pago del paso anterior (${factura.total}).`
        );

        // El KPI tiene que coincidir con el desglose por método de pago que muestra
        // la propia pantalla; con la base limpia el único pago es el de esta corrida.
        const ingresos = await rowsOf(cdp, "#tabla-ingresos-metodo");
        assert(ingresos.length > 0, "La tabla de ingresos por método de pago quedó vacía.");
        const sumaMetodos = ingresos.reduce((total, row) => total + (parseMoney(row.cells[2]) || 0), 0);
        assert(
            Math.abs(sumaMetodos - kpiCobrado) < 0.01,
            `El KPI (${kpiCobrado}) no coincide con el desglose por método (${sumaMetodos}).`
        );
        const metodoEsperado = ingresos.find((row) => row.cells[0] === PAGO_METODO_LABEL);
        assert(
            metodoEsperado,
            `El método "${PAGO_METODO_LABEL}" del pago registrado no aparece en los ingresos: `
            + `${ingresos.map((row) => row.cells[0]).join(", ")}.`
        );
        if (ingresos.length === 1 && ingresos[0].cells[1] === "1") {
            assert(
                kpiCobrado === factura.total,
                `Con un único cobro el KPI debe igualar al comprobante: ${kpiCobrado} vs ${factura.total}.`
            );
        }
        assert(
            (await countOf(cdp, "#tabla-ingresos-mes tbody tr")) > 0,
            "La tabla de evolución mensual de ingresos quedó vacía."
        );

        const produccion = await rowsOf(cdp, "#tabla-produccion-odontologo");
        assert(produccion.length > 0, "La tabla de producción por odontólogo quedó vacía.");
        assert(
            produccion.some((row) => row.cells[0].includes(USUARIOS.odontologo.nombre)),
            `La producción no atribuye el procedimiento al odontólogo de la corrida: `
            + `${produccion.map((row) => row.cells[0]).join(", ")}.`
        );

        // La cita se agenda a futuro: se amplía el rango para que la ocupación la
        // incluya aunque la corrida caiga cerca del cierre del mes.
        await setValue(cdp, "#reporte-desde", isoDate(-30));
        await setValue(cdp, "#reporte-hasta", isoDate(60));
        const getsAntes = findCalls(cdp, { method: "GET", url: "/api/reportes" }).length;
        await click(cdp, "#btn-actualizar-reportes");
        await cdp.waitFor(
            "return document.querySelectorAll('#tabla-ocupacion-estado tbody tr').length > 0;",
            25000,
            "ocupación de la agenda con datos"
        );
        await settle(cdp);
        assert(
            findCalls(cdp, { method: "GET", url: "/api/reportes" }).length > getsAntes,
            "El botón de actualizar no volvió a consultar los reportes."
        );
        const ocupacion = await rowsOf(cdp, "#tabla-ocupacion-estado");
        // La corrida produce los tres estados de S4/S6: A reprogramada, A' vigente y B
        // cancelada. El reporte tiene que reflejar los tres.
        const estadosObservados = ocupacion.map((row) => row.cells[0]);
        for (const estado of ["Programada", "Reprogramada", "Cancelada"]) {
            assert(
                estadosObservados.includes(estado),
                `La ocupación no incluye el estado "${estado}" que produjo la corrida (S4/S6): `
                + `${estadosObservados.join(", ")}.`
            );
        }
        assert(
            (await rowsOf(cdp, "#tabla-ocupacion-odontologo"))
                .some((row) => row.cells[0].includes(USUARIOS.odontologo.nombre)),
            "La ocupación por odontólogo no incluye al odontólogo de la corrida."
        );

        // Rango inválido: la validación es local, no se dispara ninguna petición.
        const getsAntesInvalido = findCalls(cdp, { method: "GET", url: "/api/reportes" }).length;
        await setValue(cdp, "#reporte-desde", isoDate(10));
        await setValue(cdp, "#reporte-hasta", isoDate(-10));
        await click(cdp, "#btn-actualizar-reportes");
        await cdp.waitFor(
            "const box = document.querySelector('#reportes-validacion'); return Boolean(box) && !box.hidden;",
            10000,
            "aviso de rango inválido"
        );
        const aviso = await textOf(cdp, "#reportes-validacion");
        assert(
            /posterior/i.test(aviso),
            `El aviso del rango inválido no explica el problema: "${aviso}".`
        );
        await sleep(1200);
        assert(
            findCalls(cdp, { method: "GET", url: "/api/reportes" }).length === getsAntesInvalido,
            "Un rango inválido disparó peticiones a /api/reportes: la validación debía cortar en el cliente."
        );

        // Vuelta al mes en curso: deja la pantalla en un estado válido y repetible.
        await click(cdp, "#btn-reporte-mes-actual");
        await cdp.waitFor(
            "const box = document.querySelector('#reportes-validacion'); return Boolean(box) && box.hidden;",
            15000,
            "aviso de validación oculto"
        );
        await cdp.waitFor(
            "return document.querySelectorAll('#tabla-ingresos-metodo tbody tr').length > 0;",
            25000,
            "reportes recargados con el mes en curso"
        );
        await settle(cdp);
        assert(
            findCalls(cdp, { method: "GET", url: "/api/reportes" }).length > getsAntesInvalido,
            "El botón 'Mes actual' no volvió a consultar los reportes."
        );

        return {
            kpiCobrado: kpiCobradoTexto,
            ingresosPorMetodo: ingresos.map((row) => row.cells.join(" · ")),
            produccionOdontologos: produccion.map((row) => row.cells.join(" · ")),
            ocupacionEstados: ocupacion.map((row) => row.cells.join(" · ")),
            periodo: await textOf(cdp, "#reporte-periodo"),
            avisoRango: aviso,
            sinRequestEnRangoInvalido: true
        };
    });

    /* ---------------- 22. Recordatorios sobre una base sin semilla (S5, S22) ---------------- */
    await step(cdp, "recordatorios-vacio", async () => {
        await openModule(cdp, "recordatorios", "Recordatorios");
        await cdp.waitFor(
            "return Boolean(document.querySelector('#lista-recordatorios'));",
            20000,
            "pantalla de recordatorios montada"
        );
        await settle(cdp);

        assert(
            (await countOf(cdp, "#lista-recordatorios .state-box-error")) === 0,
            "La bitácora de recordatorios muestra un estado de error."
        );
        assert(
            (await countOf(cdp, "#btn-despachar-recordatorios")) === 1,
            "El administrador no ve la acción de despacho de recordatorios (OP_RECORDATORIO_ENVIAR)."
        );
        assert(
            findCalls(cdp, { method: "GET", url: "/api/recordatorios", status: 200 }).length > 0,
            "La pantalla de recordatorios no consultó GET /api/recordatorios."
        );

        const contador = await textOf(cdp, "#contador-recordatorios");
        const total = parseCounterTotal(contador);
        const filas = await rowsOf(cdp, "#lista-recordatorios");

        // El nombre del paso quedó del escenario anterior: ya no hay bitácora vacía,
        // porque la base no trae semilla y las citas del run sí generan recordatorios.
        // Lo que se sigue probando es que TODA fila nace de una cita de esta corrida.
        const citasDeLaCorrida = [agenda.citaId, reprogramada.nuevaId, cancelada.citaBId].map((id) => `#${id}`);
        assert(
            filas.every((row) => citasDeLaCorrida.includes(row.cells[2])),
            `La bitácora mezcla recordatorios ajenos a la corrida (base sin semilla): `
            + `${filas.map((row) => row.cells[2]).join(", ")}.`
        );

        // Lo que el sistema HACE con los recordatorios ante S6 (CitaService):
        //  - reprogramar cancela el pendiente de la cita reemplazada y programa el de la nueva;
        //  - cancelar anula los pendientes de la cita cancelada.
        // Por eso se esperan exactamente 3 filas: A cancelada, A' pendiente y B cancelada.
        const esperado = new Map([
            [`#${agenda.citaId}`, "Cancelada"],
            [`#${reprogramada.nuevaId}`, "Pendiente"],
            [`#${cancelada.citaBId}`, "Cancelada"]
        ]);
        const estadoPorCita = new Map();
        for (const row of filas) {
            estadoPorCita.set(row.cells[2], row.badges[0]);
        }
        assert(
            estadoPorCita.size === esperado.size,
            `Se esperaban ${esperado.size} recordatorio(s) (uno por cita del run) y hay ${estadoPorCita.size}: `
            + `${[...estadoPorCita.entries()].map(([cita, estado]) => `${cita}=${estado}`).join(", ")}.`
        );
        for (const [cita, estado] of esperado) {
            assert(
                estadoPorCita.has(cita),
                `La bitácora no tiene el recordatorio de la cita ${cita}: ${[...estadoPorCita.keys()].join(", ")}.`
            );
            assert(
                estadoPorCita.get(cita) === estado,
                `El recordatorio de la cita ${cita} quedó en estado "${estadoPorCita.get(cita)}" y se esperaba "${estado}".`
            );
        }
        for (const row of filas) {
            assert(row.cells[0] && row.cells[0].length > 0, "Una fila de la bitácora no muestra el canal.");
            assert(row.badges.length === 1, "Una fila de la bitácora no muestra un badge de estado único.");
            assert(
                row.cells[3] && row.cells[3] !== "—",
                "Una fila de la bitácora no muestra la fecha de la cita."
            );
        }
        // Si el backend dejara de programar el recordatorio de una cita (S5), el mapa de
        // arriba ya habría fallado; el contador tiene que moverse con las filas.
        assert(
            total === filas.length,
            `El contador (${contador}) no coincide con las ${filas.length} fila(s) visibles.`
        );

        // Filtro CANCELADA: exactamente los dos recordatorios que S6 anuló en esta
        // corrida (el de la cita reprogramada y el de la cita cancelada), ni uno más.
        await setValue(cdp, "#filtro-recordatorio-estado", "CANCELADA");
        await cdp.waitFor(
            "return document.querySelectorAll('#lista-recordatorios tbody tr').length === 2"
            + " && !document.querySelector('#lista-recordatorios tbody td[colspan]');",
            15000,
            "recordatorios cancelados de la corrida"
        );
        const canceladas = await rowsOf(cdp, "#lista-recordatorios");
        assert(
            canceladas.length === 2,
            `El filtro CANCELADA muestra ${canceladas.length} fila(s) y se esperaban 2 (citas A y B).`
        );
        assert(
            canceladas.every((row) => row.badges[0] === "Cancelada"),
            `El filtro CANCELADA dejó filas de otro estado: ${canceladas.map((row) => row.badges[0]).join(", ")}.`
        );
        const citasCanceladas = canceladas.map((row) => row.cells[2]).sort();
        const citasEsperadas = [...esperado.entries()]
            .filter(([, estado]) => estado === "Cancelada")
            .map(([cita]) => cita)
            .sort();
        assert(
            JSON.stringify(citasCanceladas) === JSON.stringify(citasEsperadas),
            `El filtro CANCELADA no muestra las citas de S6: ${citasCanceladas.join(", ")} vs ${citasEsperadas.join(", ")}.`
        );
        const contadorCanceladas = await textOf(cdp, "#contador-recordatorios");
        assert(
            contadorCanceladas === `2 de ${filas.length} recordatorio(s)`,
            `El contador del filtro CANCELADA no refleja 2 de ${filas.length}: "${contadorCanceladas}".`
        );

        // Filtro PENDIENTE: sólo la cita nueva que nació al reprogramar.
        await setValue(cdp, "#filtro-recordatorio-estado", "PENDIENTE");
        await cdp.waitFor(
            "return document.querySelectorAll('#lista-recordatorios tbody tr').length === 1"
            + " && !document.querySelector('#lista-recordatorios tbody td[colspan]');",
            15000,
            "recordatorio pendiente de la cita reprogramada"
        );
        const pendientes = await rowsOf(cdp, "#lista-recordatorios");
        assert(
            pendientes.length === 1 && pendientes[0].badges[0] === "Pendiente",
            `El filtro PENDIENTE no muestra un único recordatorio pendiente: `
            + `${pendientes.map((row) => `${row.cells[2]}=${row.badges[0]}`).join(", ")}.`
        );
        assert(
            pendientes[0].cells[2] === `#${reprogramada.nuevaId}`,
            `El único pendiente debería ser la cita reprogramada #${reprogramada.nuevaId} y es ${pendientes[0].cells[2]}.`
        );

        await setValue(cdp, "#filtro-recordatorio-estado", "");
        await cdp.waitFor(
            `return document.querySelectorAll('#lista-recordatorios tbody tr').length === ${filas.length > 0 ? filas.length : 1};`,
            15000,
            "bitácora completa tras limpiar el filtro"
        );

        return {
            contador,
            filas: filas.length,
            estadoPorCita: [...estadoPorCita.entries()].map(([cita, estado]) => `${cita}=${estado}`),
            citasDeLaCorrida,
            filtroCancelada: { filas: canceladas.length, citas: citasCanceladas, contador: contadorCanceladas },
            filtroPendiente: { filas: pendientes.length, cita: pendientes[0].cells[2] },
            sinDatosSemilla: true,
            despachoVisible: true
        };
    });

    /* ---------------- 23. Baja lógica del paciente y limpieza (S2, S3) ---------------- */
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
            "return (document.querySelector('#lista-pacientes')?.textContent || '').includes('No se encontraron pacientes');",
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
        assert(
            historial.includes(DETALLE_REALIZADO),
            "El historial clínico perdió los tratamientos realizados tras la baja lógica."
        );
        assert(
            historial.includes(CITA_MOTIVO),
            "El historial clínico perdió las citas del paciente tras la baja lógica."
        );
        assert(
            historial.includes(CITA_B_MOTIVO),
            "El historial clínico perdió la cita cancelada (cita B) tras la baja lógica."
        );

        report.cleanup = {
            paciente: {
                id: pacienteId,
                email: PATIENT.email,
                accion: "baja lógica vía UI (DELETE /api/pacientes/{id} -> 204)",
                historialConservado: true
            },
            usuarios: {
                odontologo: { id: odontologo.id, username: USUARIOS.odontologo.username, nota: "queda activo: alimenta el selector de la agenda (S20)" },
                recepcionista: { id: recepcionista.id, username: USUARIOS.recepcionista.username, nota: "queda activo: personal de recepción de la clínica" },
                descartable: { id: USUARIOS.temporal.username, accion: "baja lógica vía UI (DELETE /api/usuarios/{id} -> 204)" }
            },
            tratamiento: {
                codigo: TREATMENT.codigo,
                nota: "queda activo en el catálogo: la API de S7 solo expone alta y edición, no borrado"
            },
            citasNota: "S6 no se ejerce sobre la cita que después se factura: se reprograma la cita A y la cancelación usa una cita B aparte",
            citas: [
                {
                    id: Number(agenda.citaId),
                    estado: "REPROGRAMADA",
                    nota: "cita original de S4, reemplazada al reprogramar (S6)"
                },
                {
                    id: Number(reprogramada.nuevaId),
                    estado: "PROGRAMADA",
                    nota: "cita vigente que creó la reprogramación (S6); la usan el realizado y los reportes"
                },
                {
                    id: Number(cancelada.citaBId),
                    estado: "CANCELADA",
                    nota: "cita B creada sólo para ejercer la cancelación de S6"
                }
            ],
            realizado: {
                nota: "se conserva: el historial clínico y el comprobante dependen de él (S2, S3)"
            },
            factura: {
                numero: factura.numero,
                estado: "PAGADA",
                nota: "comprobante inmutable; el frontend no expone anulación"
            },
            recordatorios: {
                nota: "1 PENDIENTE (cita reprogramada) y 2 CANCELADA: reprogramar anula el pendiente de la cita reemplazada y cancelar anula el de la cita cancelada (S5/S6). El frontend no expone borrado de recordatorios"
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
        "S1 alta de pacientes": ["pacientes-crear"],
        "S2 edición y baja de paciente": ["paciente-editar", "cleanup-paciente"],
        "S3 historial clínico": ["realizado-registrar", "cleanup-paciente"],
        "S4 programación de citas": ["agenda-crear"],
        "S5/S15 recordatorios": ["recordatorios-vacio"],
        "S6 reprogramación y cancelación": ["agenda-reprogramar", "agenda-cancelar"],
        "S7 catálogo de tratamientos": ["tratamientos-crear"],
        "S8 tratamientos realizados": ["realizado-registrar"],
        "S9 facturación y PDF": ["factura-emitir", "factura-pdf"],
        "S10 pagos": ["factura-pago-total"],
        "S13 sesión y panel": ["prepare-session", "login-admin", "dashboard-vacio"],
        "S18 usuarios, roles y permisos": [
            "usuarios-crear-odontologo",
            "usuarios-crear-recepcionista",
            "usuarios-baja-logica",
            "roles-matriz"
        ],
        "S19 auditoría": ["auditoria-ver"],
        "S20 odontólogos consultables": ["agenda-odontologo-disponible"],
        "S21 reportes": ["reportes-ver"],
        "S22 sidebar completo": ["login-admin", "dashboard-vacio", "recordatorios-vacio"],
        "S23/S24 base limpia": [
            "prepare-session",
            "dashboard-vacio",
            "recordatorios-vacio",
            "cleanup-paciente"
        ]
    };
    report.notes.push(
        "Cada selector del escenario está fichado en UI_CONTRACT (selector -> archivo + marcador) y validado por el preflight estático antes de lanzar Chrome.",
        "El escenario no depende de ningún dato semilla: los usuarios, el paciente, el tratamiento, las dos citas, el realizado, el comprobante y el pago los crea la propia corrida.",
        "S6 se ejerce con dos citas: la cita A se reprograma (queda REPROGRAMADA y nace una PROGRAMADA) y la cita B se crea y se cancela, para no dejar sin cita vigente al realizado ni a los reportes.",
        "Recordatorios (S5/S6): reprogramar cancela el pendiente de la cita reemplazada y programa el de la nueva; cancelar anula los pendientes. Con las tres citas del run quedan 1 PENDIENTE (cita reprogramada) y 2 CANCELADA (cita original y cita B), y eso es lo que verifica la bitácora.",
        "PATCH /api/citas/{id}/cancelar responde 204 (noContent) y reprogramar 200 con la cita nueva: el escenario afirma esos estados reales del contrato de S6.",
        "Se filtran del informe el 404 de favicon.ico y las descargas blob: del navegador: son ruido del navegador, no fallas de la aplicación.",
        "El paso de agenda reintenta con otro horario sólo ante un 409 de solapamiento del odontólogo; cualquier otro error corta la corrida.",
        "Las credenciales del administrador salen de SMOKE_ADMIN_USER / SMOKE_ADMIN_PASSWORD (defaults documentados) y deben coincidir con CLINICA_ADMIN_USERNAME / CLINICA_ADMIN_PASSWORD del stack."
    );
    report.ok = true;
} catch (error) {
    report.ok = false;
    report.fatal = error.message;
    // Una corrida que muere no deja captura del paso que falló: sin esto, el
    // reporte dice "timeout" y hay que adivinar en qué estado quedó la pantalla.
    if (sesion) {
        try {
            await sesion.screenshot("fatal.png");
            report.fatalScreenshot = "fatal.png";
        } catch {
            /* la sesión de DevTools ya puede estar cerrada */
        }
        report.networkTail = sesion.network
            .slice(-15)
            .map((entry) => `${entry.method || "?"} ${entry.url} -> ${entry.status}`);
    }
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
