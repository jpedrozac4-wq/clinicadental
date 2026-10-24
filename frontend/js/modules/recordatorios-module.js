/* ============================================================================
   VISTA: BITACORA DE RECORDATORIOS (S5, S15)
   ----------------------------------------------------------------------------
   Pantalla de la bitacora de recordatorios de citas proximas (S5) y del envio
   real configurable por SMTP con resultado registrado (S15).

   Que muestra:
   - Una fila por recordatorio con canal, estado, cita y fecha de la cita,
     destino, fecha objetivo, momento del envio y error del proveedor, si hubo.
   - El estado se filtra en el cliente: la bitacora se pide completa una sola
     vez y el filtro no genera trafico (el backend tambien acepta ?estado=, pero
     filtrar en el cliente mantiene la vista instantanea y ya probada).
   - Desde la agenda se entra con #/recordatorios?idCita=<id>: la vista pide el
     log filtrado por esa cita y lo anuncia en pantalla, que es el "historial
     consultable desde la interfaz" que exige S5.

   Accion:
   - "Despachar pendientes ahora" -> POST /api/recordatorios/procesar. El
     backend devuelve solo las filas despachadas, la vista las cuenta en un
     aviso y recarga la bitacora. Sin SMTP configurado el resultado queda como
     SIMULADA (el backend nunca falla el paso por eso).

   Permisos:
   - OP_RECORDATORIO_VER    -> ver la bitacora (ruta protegida por el router)
   - OP_RECORDATORIO_ENVIAR -> despachar; sin el permiso el boton no se monta

   Endpoints consumidos:
   - GET  /api/recordatorios?estado=&idCita=
   - POST /api/recordatorios/procesar
   ============================================================================ */
import { AuthService } from "../services/auth-service.js";
import { RecordatorioService } from "../services/recordatorio-service.js";
import { NotificationService } from "../services/notification-service.js";
import { CONFIG } from "../config.js";
import {
    el, pageHeader, loadingState, errorState, table, dateTime, estadoBadge, humanize
} from "../core/dom.js";
import { llenarSelect } from "./realizado-form.js";

/** Columnas de la bitacora, en el orden en que se pintan. */
const COLUMNAS = [
    "Canal", "Estado", "Cita", "Fecha de la cita", "Destino",
    "Fecha objetivo", "Enviado en", "Error"
];

export class RecordatoriosView {

    constructor({ route, params, navigate, query } = {}) {
        this.route = route;
        this.params = params;
        this.navigate = navigate;
        this.query = query;
        this.idCita = leerIdCita(query);
        this.filtroEstado = "";
        this.recordatorios = [];
        this.despachando = false;
    }

    async mount(container) {
        this.container = container;
        container.replaceChildren(
            this.header(),
            this.cardFiltros(),
            this.cardLista()
        );

        await this.cargar();
        return null;
    }

    unmount() {
        this.recordatorios = [];
    }

    /* ------------------------------------------------------------------ *
     * Estructura
     * ------------------------------------------------------------------ */

    header() {
        const acciones = [];
        if (AuthService.can(CONFIG.PERMISSIONS.RECORDATORIO_ENVIAR)) {
            acciones.push(el("button", {
                type: "button",
                class: "btn btn-primary",
                id: "btn-despachar-recordatorios",
                text: "Despachar pendientes ahora",
                on: { click: event => this.despachar(event.currentTarget) }
            }));
        }

        return pageHeader(
            "Recordatorios",
            "Bitácora de los avisos de citas próximas: cada cita deja un recordatorio 24 h antes y el envío queda registrado con su resultado.",
            acciones
        );
    }

    cardFiltros() {
        this.filtroEstadoSelect = el("select", {
            id: "filtro-recordatorio-estado",
            class: "form-control",
            on: { change: event => this.filtrarEstado(event.target.value) }
        });
        llenarSelect(this.filtroEstadoSelect, "Todos los estados",
            CONFIG.ESTADOS.RECORDATORIO.map(estado => ({ value: estado, label: humanize(estado) })));

        this.avisoCita = el("p", { class: "text-muted text-sm mb-0" });
        this.panelCita = el("div", { class: "alert-box info", id: "recordatorio-filtro-cita", hidden: true }, [
            this.avisoCita,
            el("button", {
                type: "button",
                class: "btn btn-sm btn-outline",
                id: "btn-quitar-filtro-cita",
                text: "Ver toda la bitácora",
                on: { click: () => this.quitarFiltroCita() }
            })
        ]);

        return el("div", { class: "card" }, [
            el("div", { class: "toolbar" }, [
                el("div", { class: "form-group toolbar-grow mb-0" }, [
                    el("label", { for: "filtro-recordatorio-estado", text: "Estado" }),
                    this.filtroEstadoSelect
                ])
            ]),
            this.panelCita
        ]);
    }

    cardLista() {
        this.lista = el("div", { id: "lista-recordatorios" }, [
            loadingState("Cargando bitácora de recordatorios…")
        ]);
        this.contador = el("span", { class: "badge badge-primary", id: "contador-recordatorios", text: "0 recordatorios" });

        return el("div", { class: "card" }, [
            el("div", { class: "card-header" }, [
                el("h2", { text: "Bitácora de envíos" }),
                el("div", { class: "card-header-actions" }, [this.contador])
            ]),
            this.lista
        ]);
    }

    /* ------------------------------------------------------------------ *
     * Datos
     * ------------------------------------------------------------------ */

    async cargar() {
        this.lista.replaceChildren(loadingState("Cargando bitácora de recordatorios…"));
        this.actualizarAvisoCita();
        try {
            const filas = await RecordatorioService.listar({ idCita: this.idCita });
            // Red de seguridad: si el backend ignorara idCita, la vista no debe
            // mezclar recordatorios de otras citas en el historial de una cita.
            this.recordatorios = this.idCita
                ? filas.filter(fila => String(fila.idCita) === String(this.idCita))
                : filas;
            this.renderTabla();
        } catch (error) {
            this.contador.textContent = "Sin datos";
            this.lista.replaceChildren(errorState(error.message));
        }
    }

    actualizarAvisoCita() {
        const filtrada = Boolean(this.idCita);
        this.panelCita.hidden = !filtrada;
        if (!filtrada) return;

        const fecha = this.recordatorios.find(fila => String(fila.idCita) === String(this.idCita))?.fechaHoraCita;
        this.avisoCita.textContent = fecha
            ? `Mostrando solo los recordatorios de la cita #${this.idCita} del ${dateTime(fecha)}.`
            : `Mostrando solo los recordatorios de la cita #${this.idCita}.`;
    }

    visibles() {
        if (!this.filtroEstado) return this.recordatorios;
        return this.recordatorios.filter(fila => fila.estado === this.filtroEstado);
    }

    renderTabla() {
        const lista = this.visibles();
        this.contador.textContent = this.filtroEstado
            ? `${lista.length} de ${this.recordatorios.length} recordatorio(s)`
            : `${this.recordatorios.length} recordatorio(s)`;

        const rows = lista.map(fila => [
            el("span", { class: "cell-strong", text: humanize(fila.canal) }),
            estadoBadge(fila.estado),
            `#${fila.idCita ?? "—"}`,
            dateTime(fila.fechaHoraCita),
            fila.destino || "—",
            dateTime(fila.fechaObjetivo),
            fila.enviadoEn ? dateTime(fila.enviadoEn) : "—",
            fila.error || "—"
        ]);

        this.lista.replaceChildren(table(COLUMNAS, rows, {
            emptyMessage: this.filtroEstado
                ? `No hay recordatorios en estado ${humanize(this.filtroEstado)}.`
                : this.idCita
                    ? `La cita #${this.idCita} todavía no tiene recordatorios registrados.`
                    : "Todavía no se registraron recordatorios."
        }));
    }

    filtrarEstado(estado) {
        this.filtroEstado = estado || "";
        this.renderTabla();
    }

    quitarFiltroCita() {
        this.idCita = null;
        this.cargar();
    }

    /* ------------------------------------------------------------------ *
     * Despacho manual (S5, S15)
     * ------------------------------------------------------------------ */

    async despachar(boton) {
        if (this.despachando) return;

        this.despachando = true;
        const textoOriginal = boton ? boton.textContent : "Despachar pendientes ahora";
        if (boton) {
            boton.disabled = true;
            boton.textContent = "Despachando…";
        }

        try {
            const procesados = await RecordatorioService.procesar();
            const total = procesados.length;
            if (total === 0) {
                NotificationService.info("No había recordatorios vencidos para despachar.");
            } else {
                NotificationService.success(`${total} recordatorio(s) procesado(s).`);
            }
            await this.cargar();
        } catch (error) {
            // 403 si el perfil no tiene OP_RECORDATORIO_ENVIAR: se muestra tal cual.
            NotificationService.error(error.message);
        } finally {
            this.despachando = false;
            if (boton) {
                boton.disabled = false;
                boton.textContent = textoOriginal;
            }
        }
    }
}

/** Lee el filtro opcional de cita desde la query del hash (#/recordatorios?idCita=7). */
function leerIdCita(query) {
    if (!query || typeof query.get !== "function") return null;
    const raw = String(query.get("idCita") || "").trim();
    return /^\d+$/.test(raw) ? raw : null;
}
