/* ============================================================================
   VISTA: PACIENTES (S1, S2)
   ----------------------------------------------------------------------------
   Listado con busqueda, alta, edicion y baja logica contra la API real.
   Las acciones se habilitan con los permisos del perfil:
   - OP_PACIENTE_VER        -> ver listado y ficha
   - OP_PACIENTE_CREAR      -> registrar
   - OP_PACIENTE_EDITAR     -> editar
   - OP_PACIENTE_ELIMINAR   -> dar de baja
   - OP_PACIENTE_HISTORIAL_VER -> abrir el historial clinico
   Endpoint: /api/pacientes (GET con ?texto=, POST, PUT /{id}, DELETE /{id}).
   ============================================================================ */
import { AuthService } from "../services/auth-service.js";
import { PacienteService } from "../services/paciente-service.js";
import { NotificationService } from "../services/notification-service.js";
import { CONFIG } from "../config.js";
import {
    el, pageHeader, loadingState, errorState, table, formField, dateOnly, edad
} from "../core/dom.js";

const BUSQUEDA_DEBOUNCE_MS = 350;
const EMAIL = /^[^@\s]+@[^@\s]+\.[A-Za-z]{2,}$/;

/** Fecha de hoy en formato ISO corto (yyyy-MM-dd), para el maximo del datepicker. */
function hoyIso() {
    const hoy = new Date();
    const mes = String(hoy.getMonth() + 1).padStart(2, "0");
    const dia = String(hoy.getDate()).padStart(2, "0");
    return `${hoy.getFullYear()}-${mes}-${dia}`;
}

export class PacientesView {

    constructor({ route, params, navigate, query } = {}) {
        this.route = route;
        this.params = params;
        this.navigate = navigate;
        this.query = query;
        this.pacientes = [];
        this.texto = "";
        this.editando = null;
        this.guardando = false;
        this._debounce = null;
    }

    async mount(container) {
        this.container = container;
        container.replaceChildren(
            this.header(),
            this.buscador(),
            this.cardLista(),
            this.cardFormulario()
        );

        if (this.query?.get("nuevo") && AuthService.can(CONFIG.PERMISSIONS.PACIENTE_CREAR)) {
            this.abrirFormulario(null);
        }

        await this.cargar();
        return null;
    }

    unmount() {
        if (this._debounce) {
            clearTimeout(this._debounce);
            this._debounce = null;
        }
    }

    /* ------------------------------------------------------------------ *
     * Estructura estatica
     * ------------------------------------------------------------------ */

    header() {
        const acciones = [];
        if (AuthService.can(CONFIG.PERMISSIONS.PACIENTE_CREAR)) {
            acciones.push(el("button", {
                type: "button",
                class: "btn btn-primary",
                id: "btn-nuevo-paciente",
                text: "Nuevo paciente",
                on: { click: () => this.abrirFormulario(null) }
            }));
        }

        return pageHeader(
            "Pacientes",
            "Registro, edición y baja lógica de las fichas de la clínica.",
            acciones
        );
    }

    buscador() {
        const input = el("input", {
            type: "search",
            id: "filtro-paciente",
            class: "form-control",
            placeholder: "Buscar por nombre, apellido, correo o teléfono",
            autocomplete: "off",
            on: {
                input: () => {
                    if (this._debounce) clearTimeout(this._debounce);
                    this._debounce = setTimeout(() => this.buscar(), BUSQUEDA_DEBOUNCE_MS);
                },
                keydown: event => {
                    if (event.key === "Enter") {
                        event.preventDefault();
                        this.buscar();
                    }
                }
            }
        });

        return el("div", { class: "card" }, [
            el("div", { class: "toolbar" }, [
                el("div", { class: "form-group toolbar-grow mb-0" }, [
                    el("label", { for: "filtro-paciente", text: "Buscar paciente" }),
                    input
                ]),
                el("button", {
                    type: "button",
                    class: "btn btn-primary",
                    id: "btn-buscar-paciente",
                    text: "Buscar",
                    on: { click: () => this.buscar() }
                }),
                el("button", {
                    type: "button",
                    class: "btn btn-outline",
                    id: "btn-limpiar-busqueda",
                    text: "Limpiar",
                    on: { click: () => this.limpiarBusqueda() }
                })
            ])
        ]);
    }

    cardLista() {
        this.lista = el("div", { id: "lista-pacientes" }, [loadingState("Cargando pacientes…")]);
        this.contador = el("span", { class: "badge badge-primary", text: "0 registros" });

        return el("div", { class: "card" }, [
            el("div", { class: "card-header" }, [
                el("h2", { text: "Pacientes registrados" }),
                el("div", { class: "card-header-actions" }, [this.contador])
            ]),
            this.lista
        ]);
    }

    cardFormulario() {
        this.tituloFormulario = el("h2", { text: "Nuevo paciente" });
        this.errorFormulario = el("div", { class: "alert-box danger", id: "form-paciente-error", hidden: true });

        this.campos = {
            nombres: el("input", { type: "text", id: "input-nombres", class: "form-control", maxlength: "80", autocomplete: "off" }),
            apellidos: el("input", { type: "text", id: "input-apellidos", class: "form-control", maxlength: "80", autocomplete: "off" }),
            fechaNacimiento: el("input", { type: "date", id: "input-fecha-nacimiento", class: "form-control", max: hoyIso() }),
            telefono: el("input", { type: "tel", id: "input-telefono", class: "form-control", maxlength: "25", autocomplete: "off" }),
            direccion: el("input", { type: "text", id: "input-direccion", class: "form-control", maxlength: "200", autocomplete: "off" }),
            email: el("input", { type: "email", id: "input-email", class: "form-control", maxlength: "120", autocomplete: "off" })
        };

        this.formulario = el("form", {
            id: "form-paciente",
            novalidate: true,
            on: { submit: event => this.guardar(event) }
        }, [
            el("div", { class: "form-grid" }, [
                formField("Nombres", this.campos.nombres, { required: true }),
                formField("Apellidos", this.campos.apellidos, { required: true }),
                formField("Fecha de nacimiento", this.campos.fechaNacimiento, {
                    required: true,
                    hint: "Debe ser anterior a hoy."
                }),
                formField("Teléfono", this.campos.telefono, { required: true }),
                formField("Correo electrónico", this.campos.email, { required: true }),
                formField("Dirección", this.campos.direccion, { required: true })
            ]),
            this.errorFormulario,
            el("div", { class: "form-actions" }, [
                el("button", {
                    type: "submit",
                    class: "btn btn-primary",
                    id: "btn-guardar-paciente",
                    text: "Guardar paciente"
                }),
                el("button", {
                    type: "button",
                    class: "btn btn-outline",
                    id: "btn-cancelar-paciente",
                    text: "Cancelar",
                    on: { click: () => this.cerrarFormulario() }
                })
            ])
        ]);

        this.panelFormulario = el("div", { class: "card form-panel", id: "panel-paciente-form", hidden: true }, [
            el("div", { class: "card-header" }, [this.tituloFormulario]),
            this.formulario
        ]);

        return this.panelFormulario;
    }

    /* ------------------------------------------------------------------ *
     * Datos
     * ------------------------------------------------------------------ */

    async cargar() {
        this.lista.replaceChildren(loadingState("Buscando pacientes…"));
        try {
            this.pacientes = await PacienteService.listar(this.texto);
            this.renderTabla();
        } catch (error) {
            this.contador.textContent = "Sin datos";
            this.lista.replaceChildren(errorState(error.message));
        }
    }

    renderTabla() {
        const puedeEditar = AuthService.can(CONFIG.PERMISSIONS.PACIENTE_EDITAR);
        const puedeEliminar = AuthService.can(CONFIG.PERMISSIONS.PACIENTE_ELIMINAR);
        const puedeHistorial = AuthService.can(CONFIG.PERMISSIONS.PACIENTE_HISTORIAL_VER);

        this.contador.textContent = this.texto
            ? `${this.pacientes.length} resultado(s)`
            : `${this.pacientes.length} registro(s)`;

        const rows = this.pacientes.map(paciente => {
            const acciones = [];
            if (puedeHistorial) {
                acciones.push(el("a", {
                    class: "btn btn-sm btn-outline",
                    href: `#/pacientes/${paciente.id}/historial`,
                    text: "Historial"
                }));
            }
            if (puedeEditar) {
                acciones.push(el("button", {
                    type: "button",
                    class: "btn btn-sm btn-outline",
                    text: "Editar",
                    dataset: { accion: "editar", id: paciente.id },
                    on: { click: () => this.abrirFormulario(paciente) }
                }));
            }
            if (puedeEliminar) {
                acciones.push(el("button", {
                    type: "button",
                    class: "btn btn-sm btn-danger",
                    text: "Dar de baja",
                    on: { click: () => this.darDeBaja(paciente) }
                }));
            }

            return [
                el("span", { class: "cell-strong", text: `${paciente.nombres} ${paciente.apellidos}` }),
                edad(paciente.fechaNacimiento),
                dateOnly(paciente.fechaNacimiento),
                paciente.telefono || "—",
                paciente.email || "—",
                paciente.direccion || "—",
                el("div", { class: "inline-actions" }, acciones.length
                    ? acciones
                    : [el("span", { class: "text-muted text-sm", text: "Solo lectura" })])
            ];
        });

        this.lista.replaceChildren(table(
            ["Paciente", "Edad", "Nacimiento", "Teléfono", "Correo", "Dirección", "Acciones"],
            rows,
            {
                emptyMessage: this.texto
                    ? `No se encontraron pacientes para "${this.texto}".`
                    : "Todavía no hay pacientes registrados."
            }
        ));
    }

    buscar() {
        this.texto = (this.container.querySelector("#filtro-paciente")?.value || "").trim();
        this.cargar();
    }

    limpiarBusqueda() {
        const input = this.container.querySelector("#filtro-paciente");
        if (input) input.value = "";
        this.texto = "";
        this.cargar();
    }

    /* ------------------------------------------------------------------ *
     * Alta y edicion
     * ------------------------------------------------------------------ */

    abrirFormulario(paciente) {
        this.editando = paciente || null;
        this.tituloFormulario.textContent = paciente ? "Editar paciente" : "Nuevo paciente";
        this.errorFormulario.hidden = true;
        this.errorFormulario.textContent = "";

        Object.entries(this.campos).forEach(([campo, control]) => {
            control.value = paciente?.[campo] || "";
        });

        this.panelFormulario.hidden = false;
        this.panelFormulario.scrollIntoView({ behavior: "smooth", block: "nearest" });
        this.campos.nombres.focus();
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

        const paciente = this.leerFormulario();
        const errores = this.validar(paciente);
        if (errores) {
            this.mostrarErrorFormulario(errores);
            return;
        }

        this.guardando = true;
        this.bloquearBotonGuardar(true);
        this.errorFormulario.hidden = true;

        try {
            if (this.editando) {
                await PacienteService.actualizar(this.editando.id, paciente);
                NotificationService.success("Paciente actualizado correctamente.");
            } else {
                const creado = await PacienteService.crear(paciente);
                NotificationService.success(`Paciente registrado con el id ${creado.id}.`);
            }
            this.cerrarFormulario();
            await this.cargar();
        } catch (error) {
            this.mostrarErrorFormulario(error.message);
        } finally {
            this.guardando = false;
            this.bloquearBotonGuardar(false);
        }
    }

    leerFormulario() {
        return Object.fromEntries(
            Object.entries(this.campos).map(([campo, control]) => [campo, control.value.trim()])
        );
    }

    /** Validacion de conveniencia: el backend sigue siendo la autoridad. */
    validar(paciente) {
        const faltantes = Object.entries({
            nombres: "Los nombres",
            apellidos: "Los apellidos",
            fechaNacimiento: "La fecha de nacimiento",
            telefono: "El teléfono",
            direccion: "La dirección",
            email: "El correo electrónico"
        }).filter(([campo]) => !paciente[campo]).map(([, etiqueta]) => etiqueta);

        if (faltantes.length) {
            return `${faltantes.join(", ")} ${faltantes.length === 1 ? "es obligatorio" : "son obligatorios"}.`;
        }
        if (paciente.fechaNacimiento > hoyIso()) {
            return "La fecha de nacimiento no puede ser futura.";
        }
        if (!EMAIL.test(paciente.email)) {
            return "El correo electrónico no tiene un formato válido (ejemplo: nombre@dominio.com).";
        }
        return null;
    }

    mostrarErrorFormulario(mensaje) {
        this.errorFormulario.textContent = mensaje;
        this.errorFormulario.hidden = false;
    }

    bloquearBotonGuardar(bloquear) {
        const boton = this.formulario.querySelector("#btn-guardar-paciente");
        if (!boton) return;
        boton.disabled = bloquear;
        boton.textContent = bloquear ? "Guardando…" : "Guardar paciente";
    }

    /* ------------------------------------------------------------------ *
     * Baja logica
     * ------------------------------------------------------------------ */

    async darDeBaja(paciente) {
        const confirmado = await NotificationService.confirm(
            `¿Dar de baja a ${paciente.nombres} ${paciente.apellidos}? Dejará de aparecer en los listados, pero su historial clínico y sus tratamientos se conservan.`,
            { confirmLabel: "Dar de baja", cancelLabel: "Cancelar" }
        );
        if (!confirmado) return;

        try {
            await PacienteService.darDeBaja(paciente.id);
            if (this.editando?.id === paciente.id) {
                this.cerrarFormulario();
            }
            NotificationService.success("Paciente dado de baja. Su historial quedó intacto.");
            await this.cargar();
        } catch (error) {
            NotificationService.error(error.message);
        }
    }
}
