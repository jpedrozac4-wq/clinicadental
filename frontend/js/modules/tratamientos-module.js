/* ============================================================================
   VISTA: CATALOGO DE TRATAMIENTOS (S7)
   ----------------------------------------------------------------------------
   Listado del catalogo con alta y edicion. El backend solo devuelve los
   tratamientos activos y rechaza con 409 un codigo duplicado; ese mensaje se
   muestra tal cual en el formulario.

   Permisos:
   - OP_TRATAMIENTO_VER     -> ver el catalogo (todos los perfiles lo tienen)
   - OP_TRATAMIENTO_CREAR   -> alta (administrador)
   - OP_TRATAMIENTO_EDITAR  -> edicion (administrador)
   Endpoints: GET/POST /api/tratamientos, PUT /api/tratamientos/{id}.
   ============================================================================ */
import { AuthService } from "../services/auth-service.js";
import { TratamientoService } from "../services/tratamiento-service.js";
import { NotificationService } from "../services/notification-service.js";
import { CONFIG } from "../config.js";
import {
    el, pageHeader, loadingState, errorState, table, formField, money
} from "../core/dom.js";

export class TratamientosView {

    constructor({ route, params, navigate, query } = {}) {
        this.route = route;
        this.params = params;
        this.navigate = navigate;
        this.query = query;
        this.tratamientos = [];
        this.filtro = "";
        this.editando = null;
        this.guardando = false;
    }

    async mount(container) {
        this.container = container;
        container.replaceChildren(
            this.header(),
            this.buscador(),
            this.cardLista(),
            this.cardFormulario()
        );

        if (this.query?.get("nuevo") && AuthService.can(CONFIG.PERMISSIONS.TRATAMIENTO_CREAR)) {
            this.abrirFormulario(null);
        }

        await this.cargar();
        return null;
    }

    /* ------------------------------------------------------------------ *
     * Estructura
     * ------------------------------------------------------------------ */

    header() {
        const acciones = [];
        if (AuthService.can(CONFIG.PERMISSIONS.TRATAMIENTO_CREAR)) {
            acciones.push(el("button", {
                type: "button",
                class: "btn btn-primary",
                id: "btn-nuevo-tratamiento",
                text: "Nuevo tratamiento",
                on: { click: () => this.abrirFormulario(null) }
            }));
        }

        return pageHeader(
            "Catálogo de tratamientos",
            "Procedimientos disponibles con su descripción, precio y duración estándar.",
            acciones
        );
    }

    buscador() {
        return el("div", { class: "card" }, [
            el("div", { class: "toolbar" }, [
                el("div", { class: "form-group toolbar-grow mb-0" }, [
                    el("label", { for: "filtro-tratamiento", text: "Filtrar catálogo" }),
                    el("input", {
                        type: "search",
                        id: "filtro-tratamiento",
                        class: "form-control",
                        placeholder: "Buscar por código o nombre",
                        autocomplete: "off",
                        on: { input: event => this.filtrar(event.target.value) }
                    })
                ]),
                el("button", {
                    type: "button",
                    class: "btn btn-outline",
                    id: "btn-recargar-tratamientos",
                    text: "Recargar",
                    on: { click: () => this.cargar() }
                })
            ])
        ]);
    }

    cardLista() {
        this.lista = el("div", { id: "lista-tratamientos" }, [loadingState("Cargando catálogo…")]);
        this.contador = el("span", { class: "badge badge-primary", text: "0 tratamientos" });

        return el("div", { class: "card" }, [
            el("div", { class: "card-header" }, [
                el("h2", { text: "Tratamientos disponibles" }),
                el("div", { class: "card-header-actions" }, [this.contador])
            ]),
            this.lista
        ]);
    }

    cardFormulario() {
        this.tituloFormulario = el("h2", { text: "Nuevo tratamiento" });
        this.errorFormulario = el("div", { class: "alert-box danger", id: "form-tratamiento-error", hidden: true });

        this.campos = {
            codigo: el("input", { type: "text", id: "input-codigo", class: "form-control", maxlength: "20", autocomplete: "off" }),
            nombre: el("input", { type: "text", id: "input-nombre", class: "form-control", maxlength: "120", autocomplete: "off" }),
            precio: el("input", { type: "number", id: "input-precio", class: "form-control", min: "0.01", step: "0.01" }),
            duracionMinutos: el("input", { type: "number", id: "input-duracion", class: "form-control", min: "1", step: "1" }),
            descripcion: el("textarea", { id: "input-descripcion", class: "form-control", rows: "3", maxlength: "2000" })
        };

        this.formulario = el("form", {
            id: "form-tratamiento",
            novalidate: true,
            on: { submit: event => this.guardar(event) }
        }, [
            el("div", { class: "form-grid" }, [
                formField("Código", this.campos.codigo, {
                    required: true,
                    hint: "Identificador único del procedimiento (ej: ODO-011)."
                }),
                formField("Nombre", this.campos.nombre, { required: true }),
                formField("Precio", this.campos.precio, { required: true, hint: "Debe ser mayor que cero." }),
                formField("Duración (minutos)", this.campos.duracionMinutos, { required: true })
            ]),
            formField("Descripción", this.campos.descripcion),
            this.errorFormulario,
            el("div", { class: "form-actions" }, [
                el("button", {
                    type: "submit",
                    class: "btn btn-primary",
                    id: "btn-guardar-tratamiento",
                    text: "Guardar tratamiento"
                }),
                el("button", {
                    type: "button",
                    class: "btn btn-outline",
                    id: "btn-cancelar-tratamiento",
                    text: "Cancelar",
                    on: { click: () => this.cerrarFormulario() }
                })
            ])
        ]);

        this.panelFormulario = el("div", { class: "card form-panel", id: "panel-tratamiento-form", hidden: true }, [
            el("div", { class: "card-header" }, [this.tituloFormulario]),
            this.formulario
        ]);

        return this.panelFormulario;
    }

    /* ------------------------------------------------------------------ *
     * Datos
     * ------------------------------------------------------------------ */

    async cargar() {
        this.lista.replaceChildren(loadingState("Cargando catálogo…"));
        try {
            this.tratamientos = await TratamientoService.listar();
            this.renderTabla();
        } catch (error) {
            this.contador.textContent = "Sin datos";
            this.lista.replaceChildren(errorState(error.message));
        }
    }

    filtrar(texto) {
        this.filtro = String(texto || "").trim().toLowerCase();
        this.renderTabla();
    }

    visibles() {
        if (!this.filtro) return this.tratamientos;
        return this.tratamientos.filter(tratamiento =>
            String(tratamiento.codigo || "").toLowerCase().includes(this.filtro)
            || String(tratamiento.nombre || "").toLowerCase().includes(this.filtro));
    }

    renderTabla() {
        const puedeEditar = AuthService.can(CONFIG.PERMISSIONS.TRATAMIENTO_EDITAR);
        const lista = this.visibles();

        this.contador.textContent = this.filtro
            ? `${lista.length} de ${this.tratamientos.length}`
            : `${this.tratamientos.length} tratamiento(s)`;

        const rows = lista.map(tratamiento => [
            el("span", { class: "badge badge-primary", text: tratamiento.codigo || "—" }),
            el("span", { class: "cell-strong", text: tratamiento.nombre || "—" }),
            tratamiento.descripcion || "—",
            money(tratamiento.precio),
            `${tratamiento.duracionMinutos ?? "—"} min`,
            puedeEditar
                ? el("button", {
                    type: "button",
                    class: "btn btn-sm btn-outline",
                    text: "Editar",
                    on: { click: () => this.abrirFormulario(tratamiento) }
                })
                : el("span", { class: "text-muted text-sm", text: "Solo lectura" })
        ]);

        this.lista.replaceChildren(table(
            ["Código", "Tratamiento", "Descripción", "Precio", "Duración", "Acciones"],
            rows,
            {
                emptyMessage: this.filtro
                    ? `No hay tratamientos que coincidan con "${this.filtro}".`
                    : "El catálogo está vacío."
            }
        ));
    }

    /* ------------------------------------------------------------------ *
     * Alta y edicion
     * ------------------------------------------------------------------ */

    abrirFormulario(tratamiento) {
        this.editando = tratamiento || null;
        this.tituloFormulario.textContent = tratamiento ? "Editar tratamiento" : "Nuevo tratamiento";
        this.errorFormulario.hidden = true;
        this.errorFormulario.textContent = "";

        this.campos.codigo.value = tratamiento?.codigo || "";
        this.campos.nombre.value = tratamiento?.nombre || "";
        this.campos.precio.value = tratamiento?.precio ?? "";
        this.campos.duracionMinutos.value = tratamiento?.duracionMinutos ?? "";
        this.campos.descripcion.value = tratamiento?.descripcion || "";

        this.panelFormulario.hidden = false;
        this.panelFormulario.scrollIntoView({ behavior: "smooth", block: "nearest" });
        this.campos.codigo.focus();
    }

    cerrarFormulario() {
        this.editando = null;
        this.panelFormulario.hidden = true;
        this.formulario.reset();
        this.errorFormulario.hidden = true;
    }

    async guardar(event) {
        event.preventDefault();
        if (this.guardando) return;

        const tratamiento = this.leerFormulario();
        const error = this.validar(tratamiento);
        if (error) {
            this.mostrarError(error);
            return;
        }

        this.guardando = true;
        this.bloquearBotonGuardar(true);
        this.errorFormulario.hidden = true;

        try {
            if (this.editando) {
                await TratamientoService.actualizar(this.editando.id, tratamiento);
                NotificationService.success("Tratamiento actualizado correctamente.");
            } else {
                const creado = await TratamientoService.crear(tratamiento);
                NotificationService.success(`Tratamiento ${creado.codigo} creado correctamente.`);
            }
            this.cerrarFormulario();
            await this.cargar();
        } catch (error) {
            // El backend responde 409 con el codigo duplicado: se muestra su mensaje.
            this.mostrarError(error.message);
        } finally {
            this.guardando = false;
            this.bloquearBotonGuardar(false);
        }
    }

    leerFormulario() {
        return {
            codigo: this.campos.codigo.value.trim(),
            nombre: this.campos.nombre.value.trim(),
            precio: this.campos.precio.value,
            duracionMinutos: this.campos.duracionMinutos.value,
            descripcion: this.campos.descripcion.value.trim()
        };
    }

    validar(tratamiento) {
        if (!tratamiento.codigo) return "El código del tratamiento es obligatorio.";
        if (!tratamiento.nombre) return "El nombre del tratamiento es obligatorio.";
        const precio = Number(tratamiento.precio);
        if (!Number.isFinite(precio) || precio <= 0) return "El precio debe ser mayor que cero.";
        const duracion = Number(tratamiento.duracionMinutos);
        if (!Number.isFinite(duracion) || duracion < 1) return "La duración debe ser al menos un minuto.";
        return null;
    }

    mostrarError(mensaje) {
        this.errorFormulario.textContent = mensaje;
        this.errorFormulario.hidden = false;
    }

    bloquearBotonGuardar(bloquear) {
        const boton = this.formulario.querySelector("#btn-guardar-tratamiento");
        if (!boton) return;
        boton.disabled = bloquear;
        boton.textContent = bloquear ? "Guardando…" : "Guardar tratamiento";
    }
}
