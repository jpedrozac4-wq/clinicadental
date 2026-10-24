/* ============================================================================
   VISTA: AUDITORÍA DE OPERACIONES (S19)
   ----------------------------------------------------------------------------
   Pantalla de consulta de la bitácora de auditoría: quién hizo qué, sobre qué
   registro y cuándo. Es un módulo estrictamente de LECTURA: no crea, edita ni
   borra nada, y solo consume GET /api/auditoria.

   Qué muestra:
   - Una fila por operación auditada con fecha, acción (etiqueta en español),
     tabla afectada, registro, usuario actor y el JSONB de detalle.
   - El detalle llega serializado como texto y se muestra PLEGADO por defecto en
     un <details>; al abrirlo se imprime con sangría dentro de un <pre>. Nunca se
     interpreta como HTML (siempre textContent), así que un `data` con marcado
     no puede inyectarse en la página.
   - Filtros: rango de fechas (desde/hasta), acción del catálogo y username del
     actor. "Limpiar filtros" vuelve al listado completo.
   - Paginación servidor: anterior/siguiente + "Página X de Y", con los totales
     en el contador. Cambiar de página refresca solo la tabla, el contador y la
     barra de paginación, nunca la tarjeta de filtros.

   Permisos:
   - OP_AUDITORIA_VER -> el router ya bloquea la ruta; la vista no decide nada.

   Endpoint consumido:
   - GET /api/auditoria?desde=&hasta=&accion=&usuario=&pagina=
   ============================================================================ */
import { AuditoriaService } from "../services/auditoria-service.js";
import {
    el, pageHeader, loadingState, emptyState, errorState, table, dateTime, humanize
} from "../core/dom.js";

/** Columnas de la bitácora, en el orden en que se pintan. */
const COLUMNAS = ["Fecha", "Acción", "Tabla", "Registro", "Usuario", "Detalle"];

/** Milisegundos de espera antes de aplicar el filtro de usuario mientras se escribe. */
const FILTRO_DEBOUNCE_MS = 400;

/**
 * Acciones auditadas con su etiqueta en español (S19). El `value` del select es
 * siempre el código crudo del catálogo `cat_log_accion`; el backend lo valida y
 * responde 400 si no lo conoce.
 */
const ACCIONES = [
    { codigo: "LOGIN", etiqueta: "Inicio de sesión" },
    { codigo: "PACIENTE_CREAR", etiqueta: "Alta de paciente" },
    { codigo: "PACIENTE_ACTUALIZAR", etiqueta: "Edición de paciente" },
    { codigo: "PACIENTE_BAJA", etiqueta: "Baja de paciente" },
    { codigo: "CITA_CREAR", etiqueta: "Programación de cita" },
    { codigo: "CITA_REPROGRAMAR", etiqueta: "Reprogramación de cita" },
    { codigo: "CITA_CANCELAR", etiqueta: "Cancelación de cita" },
    { codigo: "TRATAMIENTO_CREAR", etiqueta: "Alta de tratamiento" },
    { codigo: "TRATAMIENTO_ACTUALIZAR", etiqueta: "Edición de tratamiento" },
    { codigo: "REALIZADO_REGISTRAR", etiqueta: "Registro de tratamiento realizado" },
    { codigo: "FACTURA_EMITIR", etiqueta: "Emisión de factura" },
    { codigo: "PAGO_REGISTRAR", etiqueta: "Registro de pago" },
    { codigo: "USUARIO_CREAR", etiqueta: "Alta de usuario" },
    { codigo: "USUARIO_ACTUALIZAR", etiqueta: "Edición de usuario" },
    { codigo: "USUARIO_BAJA", etiqueta: "Baja de usuario" },
    { codigo: "ROL_CREAR", etiqueta: "Alta de rol" },
    { codigo: "ROL_ACTUALIZAR", etiqueta: "Edición de rol" },
    { codigo: "ROL_ELIMINAR", etiqueta: "Eliminación de rol" }
];

/** Índice código -> etiqueta para pintar la columna Acción. */
const ETIQUETAS_ACCION = new Map(ACCIONES.map(accion => [accion.codigo, accion.etiqueta]));

/** Texto del estado vacío, tal como lo pide la spec de la pantalla. */
const TEXTO_VACIO = "No hay registros de auditoría para los filtros seleccionados";

export class AuditoriaView {

    constructor({ route, params, navigate, query } = {}) {
        this.route = route;
        this.params = params;
        this.navigate = navigate;
        this.query = query;

        this.pagina = 0;
        this.totalPaginas = 0;
        this.totalElementos = 0;
        this.filas = [];
        this.cargando = false;
        this._debounce = null;
        // Token de la última solicitud: descarta respuestas que llegan tarde.
        this._solicitud = 0;
    }

    async mount(container) {
        this.container = container;
        container.replaceChildren(
            this.header(),
            this.cardFiltros(),
            this.cardLista()
        );

        await this.cargar({ pagina: 0 });
        return null;
    }

    unmount() {
        if (this._debounce) {
            clearTimeout(this._debounce);
            this._debounce = null;
        }
        // Invalida cualquier respuesta en vuelo: la vista ya no está en el DOM.
        this._solicitud += 1;
        this.cargando = false;
        this.filas = [];
    }

    /* ------------------------------------------------------------------ *
     * Estructura estática
     * ------------------------------------------------------------------ */

    header() {
        return pageHeader(
            "Auditoría",
            "Bitácora de las operaciones críticas del sistema: quién hizo qué, sobre qué registro y cuándo. Solo lectura."
        );
    }

    cardFiltros() {
        this.inputDesde = el("input", {
            type: "date",
            id: "filtro-auditoria-desde",
            class: "form-control",
            on: { change: () => this.aplicarFiltros() }
        });
        this.inputHasta = el("input", {
            type: "date",
            id: "filtro-auditoria-hasta",
            class: "form-control",
            on: { change: () => this.aplicarFiltros() }
        });
        this.selectAccion = el("select", {
            id: "filtro-auditoria-accion",
            class: "form-control",
            on: { change: () => this.aplicarFiltros() }
        }, [
            el("option", { value: "", text: "Todas las acciones" }),
            ...ACCIONES.map(accion => el("option", { value: accion.codigo, text: accion.etiqueta }))
        ]);
        this.inputUsuario = el("input", {
            type: "search",
            id: "filtro-auditoria-usuario",
            class: "form-control",
            placeholder: "username del usuario",
            autocomplete: "off",
            on: {
                input: () => this.programarFiltro(),
                keydown: event => {
                    if (event.key !== "Enter") return;
                    event.preventDefault();
                    this.aplicarFiltros();
                }
            }
        });

        return el("div", { class: "card" }, [
            el("div", { class: "toolbar" }, [
                grupoFiltro("Desde", this.inputDesde),
                grupoFiltro("Hasta", this.inputHasta),
                grupoFiltro("Acción", this.selectAccion),
                grupoFiltro("Usuario", this.inputUsuario, { grow: true }),
                el("div", { class: "inline-actions" }, [
                    el("button", {
                        type: "button",
                        class: "btn btn-outline",
                        id: "btn-limpiar-filtros-auditoria",
                        text: "Limpiar filtros",
                        on: { click: () => this.limpiarFiltros() }
                    })
                ])
            ])
        ]);
    }

    cardLista() {
        this.lista = el("div", { id: "auditoria-lista" }, [
            loadingState("Cargando registros de auditoría…")
        ]);
        this.contador = el("span", {
            class: "badge badge-primary",
            id: "auditoria-contador",
            "aria-live": "polite",
            text: "—"
        });

        this.btnAnterior = el("button", {
            type: "button",
            class: "btn btn-sm btn-outline",
            id: "btn-auditoria-anterior",
            text: "« Anterior",
            disabled: true,
            on: { click: () => this.irPagina(this.pagina - 1) }
        });
        this.btnSiguiente = el("button", {
            type: "button",
            class: "btn btn-sm btn-outline",
            id: "btn-auditoria-siguiente",
            text: "Siguiente »",
            disabled: true,
            on: { click: () => this.irPagina(this.pagina + 1) }
        });
        this.etiquetaPagina = el("span", {
            class: "text-muted text-sm",
            id: "auditoria-pagina",
            text: "Página 0 de 0"
        });
        this.paginacion = el("div", { class: "toolbar", id: "auditoria-paginacion" }, [
            this.btnAnterior,
            this.etiquetaPagina,
            this.btnSiguiente
        ]);

        return el("div", { class: "card" }, [
            el("div", { class: "card-header" }, [
                el("h2", { text: "Bitácora de operaciones" }),
                el("div", { class: "card-header-actions" }, [this.contador])
            ]),
            this.lista,
            this.paginacion
        ]);
    }

    /* ------------------------------------------------------------------ *
     * Datos
     * ------------------------------------------------------------------ */

    /**
     * Pide una página y refresca tabla, contador y paginación en sitio.
     * @param {{pagina?: number}} [opciones]
     */
    async cargar({ pagina = 0 } = {}) {
        const token = ++this._solicitud;
        this.cargando = true;
        this.lista.replaceChildren(loadingState("Cargando registros de auditoría…"));
        this.deshabilitarPaginacion();

        try {
            const respuesta = await AuditoriaService.listar({ ...this.filtros(), pagina });
            if (token !== this._solicitud) return; // respuesta obsoleta

            this.pagina = respuesta.pagina;
            this.totalPaginas = respuesta.totalPaginas;
            this.totalElementos = respuesta.totalElementos;
            this.filas = respuesta.contenido;

            this.renderTabla(respuesta.contenido);
            this.actualizarPaginacion();
        } catch (error) {
            if (token !== this._solicitud) return;

            // El mensaje del backend se muestra tal cual: una acción desconocida
            // llega como 400 con su texto y una sesión sin permiso como 403.
            this.pagina = 0;
            this.totalPaginas = 0;
            this.totalElementos = 0;
            this.filas = [];
            this.contador.textContent = "Sin datos";
            this.lista.replaceChildren(errorState(error.message));
            this.ocultarPaginacion();
        } finally {
            if (token === this._solicitud) {
                this.cargando = false;
            }
        }
    }

    /** Filtros activos, con los valores vacíos convertidos a null. */
    filtros() {
        return {
            desde: valorDe(this.inputDesde),
            hasta: valorDe(this.inputHasta),
            accion: valorDe(this.selectAccion),
            usuario: valorDe(this.inputUsuario)
        };
    }

    hayFiltros() {
        return Object.values(this.filtros()).some(valor => Boolean(valor));
    }

    /* ------------------------------------------------------------------ *
     * Filtros
     * ------------------------------------------------------------------ */

    aplicarFiltros() {
        if (this._debounce) {
            clearTimeout(this._debounce);
            this._debounce = null;
        }
        // Cualquier cambio de filtro vuelve a la primera página.
        this.cargar({ pagina: 0 });
    }

    programarFiltro() {
        if (this._debounce) clearTimeout(this._debounce);
        this._debounce = setTimeout(() => this.aplicarFiltros(), FILTRO_DEBOUNCE_MS);
    }

    limpiarFiltros() {
        this.inputDesde.value = "";
        this.inputHasta.value = "";
        this.selectAccion.value = "";
        this.inputUsuario.value = "";
        this.aplicarFiltros();
    }

    /* ------------------------------------------------------------------ *
     * Render
     * ------------------------------------------------------------------ */

    renderTabla(filas) {
        this.contador.textContent = this.totalElementos > 0
            ? `${filas.length} de ${this.totalElementos} registro(s)`
            : "0 registro(s)";

        if (!filas.length) {
            this.lista.replaceChildren(emptyState(
                TEXTO_VACIO,
                this.hayFiltros()
                    ? "Probá ampliar el rango de fechas o quitar algún filtro."
                    : "Todavía no se registró ninguna operación auditada."
            ));
            return;
        }

        const rows = filas.map(fila => [
            fechaHora(fila.fecha),
            etiquetaAccion(fila),
            fila.tabla || "—",
            fila.registro === null || fila.registro === undefined || fila.registro === ""
                ? "—"
                : `#${fila.registro}`,
            fila.usuario
                ? el("span", { class: "cell-strong", text: fila.usuario })
                : el("span", {
                    class: "text-muted",
                    text: "Sistema",
                    title: "Operación sin usuario autenticado (proceso programado)"
                }),
            celdaDetalle(fila)
        ]);

        this.lista.replaceChildren(table(COLUMNAS, rows));
    }

    actualizarPaginacion() {
        this.paginacion.hidden = false;
        this.btnAnterior.disabled = this.pagina <= 0;
        this.btnSiguiente.disabled = this.totalPaginas === 0
            || this.pagina >= this.totalPaginas - 1;
        this.etiquetaPagina.textContent = this.totalPaginas === 0
            ? "Página 0 de 0"
            : `Página ${this.pagina + 1} de ${this.totalPaginas}`;
    }

    deshabilitarPaginacion() {
        this.btnAnterior.disabled = true;
        this.btnSiguiente.disabled = true;
    }

    ocultarPaginacion() {
        this.deshabilitarPaginacion();
        this.paginacion.hidden = true;
    }

    /* ------------------------------------------------------------------ *
     * Paginación
     * ------------------------------------------------------------------ */

    async irPagina(destino) {
        if (this.cargando) return;
        if (!Number.isInteger(destino) || destino < 0) return;
        if (this.totalPaginas > 0 && destino >= this.totalPaginas) return;
        if (destino === this.pagina) return;

        await this.cargar({ pagina: destino });
    }
}

/* ----------------------------------------------------------------------------
   Piezas de presentación
   ---------------------------------------------------------------------------- */

/** Grupo etiqueta + control de la barra de filtros. */
function grupoFiltro(labelText, control, { grow = false } = {}) {
    return el("div", { class: grow ? "form-group toolbar-grow mb-0" : "form-group mb-0" }, [
        el("label", { for: control.id, text: labelText }),
        control
    ]);
}

/**
 * Fecha y hora de la operación en formato dd/mm/yyyy HH:mm.
 * Reusa el formateador compartido (dateTime, locale de la app) y normaliza el
 * separador ", " que agrega el locale entre fecha y hora: la bitácora se lee en
 * columnas y la spec de la pantalla pide exactamente dd/mm/yyyy HH:mm.
 */
function fechaHora(value) {
    return dateTime(value).replace(/,\s*/g, " ");
}

/** Valor actual de un control, recortado y con vacío -> null. */
function valorDe(control) {
    const raw = String(control?.value || "").trim();
    return raw === "" ? null : raw;
}

/** Etiqueta en español de la acción; si el código es nuevo, cae al backend. */
function etiquetaAccion(fila) {
    const codigo = String(fila?.accion || "").trim();
    const etiqueta = ETIQUETAS_ACCION.get(codigo)
        || (fila?.descripcionAccion ? String(fila.descripcionAccion) : humanize(codigo));

    return el("span", {
        class: "cell-strong",
        text: etiqueta,
        title: codigo || null
    });
}

/**
 * Celda de detalle: plegada por defecto y formateada recién al abrirse.
 * El JSONB llega como texto; se imprime dentro de un <pre> con textContent,
 * de modo que un `data` con marcado nunca se interpreta como HTML.
 */
function celdaDetalle(fila) {
    if (fila?.detalle === null || fila?.detalle === undefined || fila?.detalle === "") {
        return el("span", { class: "text-muted", text: "—" });
    }

    const pre = el("pre", {
        class: "auditoria-detalle-json",
        hidden: true,
        style: "white-space: pre-wrap; word-break: break-word; max-width: 30rem; margin: 0.4rem 0 0; font-size: 0.75rem;"
    });
    const details = el("details", {
        class: "auditoria-detalle",
        dataset: { idLog: String(fila.idLog ?? "") }
    }, [
        el("summary", { class: "auditoria-detalle-resumen", text: "Ver detalle" }),
        pre
    ]);

    details.addEventListener("toggle", () => {
        if (!details.open || pre.dataset.formateado === "true") return;
        pre.textContent = formatearDetalle(fila.detalle);
        pre.dataset.formateado = "true";
        pre.hidden = false;
    });

    return details;
}

/** Imprime el detalle con sangría; si no es JSON válido, se muestra tal cual. */
function formatearDetalle(detalle) {
    if (detalle === null || detalle === undefined) return "—";

    if (typeof detalle === "object") {
        try {
            return JSON.stringify(detalle, null, 2);
        } catch {
            return String(detalle);
        }
    }

    const texto = String(detalle);
    try {
        return JSON.stringify(JSON.parse(texto), null, 2);
    } catch {
        // JSONB serializado con formato inesperado: se muestra el texto crudo.
        return texto;
    }
}
