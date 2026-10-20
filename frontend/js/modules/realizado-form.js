/* ============================================================================
   COMPONENTE COMPARTIDO: REGISTRO DE TRATAMIENTO REALIZADO (S8)
   ----------------------------------------------------------------------------
   No es una ruta: es el panel que usan la agenda y el historial clinico para
   registrar un procedimiento. El backend congela el costo con el precio vigente
   del catalogo y, si se indica una cita, la deja ATENDIDA (S8).

   Uso:
     const form = new RealizadoForm({ onRegistered: () => recargar() });
     container.append(form.element);
     await form.abrir({ paciente, cita });   // ambos opcionales

   Endpoints consumidos:
   - GET  /api/pacientes                (solo si no viene un paciente fijo)
   - GET  /api/tratamientos
   - GET  /api/citas?desde&hasta        (odontologos y citas del paciente)
   - POST /api/tratamientos-realizados  (detalleProcedimiento obligatorio)
   ============================================================================ */
import { CitaService } from "../services/cita-service.js";
import { DentistaService } from "../services/dentista-service.js";
import { PacienteService } from "../services/paciente-service.js";
import { RealizadoService } from "../services/realizado-service.js";
import { TratamientoService } from "../services/tratamiento-service.js";
import { NotificationService } from "../services/notification-service.js";
import {
    el, formField, money, dateTime, humanize
} from "../core/dom.js";

/** Estados de cita que todavia se pueden atender. */
const ESTADOS_VINCULABLES = ["PROGRAMADA", "CONFIRMADA", "REPROGRAMADA"];

export class RealizadoForm {

    constructor({ onRegistered = null, onCancel = null } = {}) {
        this.onRegistered = onRegistered;
        this.onCancel = onCancel;
        this.catalogos = null;
        this.guardando = false;
        this.root = this.build();
    }

    get element() {
        return this.root;
    }

    /* ------------------------------------------------------------------ *
     * Estructura
     * ------------------------------------------------------------------ */

    build() {
        this.contexto = el("p", { class: "text-muted text-sm" });
        this.errorBox = el("div", { class: "alert-box danger", id: "form-realizado-error", hidden: true });

        this.pacienteSelect = el("select", { id: "realizado-paciente", class: "form-control" });
        this.tratamientoSelect = el("select", { id: "realizado-tratamiento", class: "form-control" });
        this.dentistaSelect = el("select", { id: "realizado-dentista", class: "form-control" });
        this.citaSelect = el("select", { id: "realizado-cita", class: "form-control" });
        this.fechaInput = el("input", {
            type: "datetime-local",
            id: "realizado-fecha",
            class: "form-control",
            max: ahoraLocal()
        });
        this.detalleInput = el("textarea", {
            id: "realizado-detalle",
            class: "form-control",
            rows: "3",
            maxlength: "2000",
            placeholder: "Qué se realizó, piezas tratadas, hallazgos…"
        });
        this.observacionesInput = el("textarea", {
            id: "realizado-observaciones",
            class: "form-control",
            rows: "2",
            maxlength: "2000"
        });

        this.avisoDentistas = el("p", { class: "field-hint", hidden: true });

        this.submitButton = el("button", {
            type: "submit",
            class: "btn btn-primary",
            id: "btn-guardar-realizado",
            text: "Registrar tratamiento"
        });

        this.form = el("form", {
            id: "form-realizado",
            novalidate: true,
            on: { submit: event => this.guardar(event) }
        }, [
            this.contexto,
            el("div", { class: "form-grid" }, [
                formField("Paciente", this.pacienteSelect, { required: true }),
                formField("Tratamiento realizado", this.tratamientoSelect, { required: true }),
                formField("Odontólogo", this.dentistaSelect, { required: true }),
                formField("Fecha de realización", this.fechaInput, {
                    required: true,
                    hint: "No puede ser futura; el costo se congela desde el catálogo."
                }),
                formField("Cita vinculada", this.citaSelect, {
                    hint: "Si vinculás una cita, queda ATENDIDA automáticamente."
                })
            ]),
            this.avisoDentistas,
            formField("Detalle del procedimiento", this.detalleInput, { required: true }),
            formField("Observaciones", this.observacionesInput, { hint: "Opcional." }),
            this.errorBox,
            el("div", { class: "form-actions" }, [
                this.submitButton,
                el("button", {
                    type: "button",
                    class: "btn btn-outline",
                    id: "btn-cancelar-realizado",
                    text: "Cancelar",
                    on: { click: () => this.cerrar() }
                })
            ])
        ]);

        return el("div", { class: "card form-panel", id: "panel-realizado-form", hidden: true }, [
            el("div", { class: "card-header" }, [
                el("h2", { text: "Registrar tratamiento realizado" }),
                el("span", { class: "badge badge-primary", text: "S8" })
            ]),
            this.form
        ]);
    }

    /* ------------------------------------------------------------------ *
     * Apertura
     * ------------------------------------------------------------------ */

    /**
     * Abre el panel con valores iniciales.
     * @param {object} [opciones]
     * @param {object|null} [opciones.paciente] Paciente fijo ({id, nombres, apellidos}).
     * @param {object|null} [opciones.cita] Cita que se atiende ({id, idTratamiento, idDentista, ...}).
     */
    async abrir({ paciente = null, cita = null } = {}) {
        this.errorBox.hidden = true;
        this.avisoDentistas.hidden = true;

        this.root.hidden = false;
        this.root.scrollIntoView({ behavior: "smooth", block: "nearest" });
        this.contexto.textContent = "Cargando catálogos…";

        try {
            await this.cargarCatalogos();
        } catch (error) {
            this.contexto.textContent = "";
            this.mostrarError(error.message);
            return;
        }

        this.poblarPacientes(paciente);
        this.poblarTratamientos(cita);
        this.poblarDentistas(cita);
        await this.poblarCitas(paciente, cita);

        this.fechaInput.value = cita?.fechaHoraInicio && !esFuturo(cita.fechaHoraInicio)
            ? valorLocal(cita.fechaHoraInicio)
            : valorLocal(new Date());
        this.detalleInput.value = "";
        this.observacionesInput.value = "";

        this.contexto.textContent = paciente
            ? `Procedimiento de ${nombrePaciente(paciente)}.`
            : "Elegí el paciente al que se le practicó el procedimiento.";

        this.pacienteSelect.focus();
    }

    cerrar() {
        this.root.hidden = true;
        this.form.reset();
        this.errorBox.hidden = true;
        if (typeof this.onCancel === "function") this.onCancel();
    }

    /* ------------------------------------------------------------------ *
     * Catalogos
     * ------------------------------------------------------------------ */

    async cargarCatalogos() {
        if (this.catalogos) return this.catalogos;

        // Pacientes y tratamientos son imprescindibles; el catalogo de
        // odontologos se deriva de la agenda y puede llegar vacio o fallar sin
        // que eso impida mostrar el formulario.
        const [pacientes, tratamientos, dentistas] = await Promise.all([
            PacienteService.listar(),
            TratamientoService.listar(),
            DentistaService.listar().catch(() => [])
        ]);
        this.catalogos = { pacientes, tratamientos, dentistas };
        return this.catalogos;
    }

    poblarPacientes(paciente) {
        const opciones = this.catalogos.pacientes.map(item => ({
            value: String(item.id),
            label: `${item.nombres} ${item.apellidos}`
        }));
        llenarSelect(this.pacienteSelect, "Seleccioná un paciente", opciones);
        if (paciente) {
            this.pacienteSelect.value = String(paciente.id);
            this.pacienteSelect.disabled = true;
        } else {
            this.pacienteSelect.disabled = false;
        }
    }

    poblarTratamientos(cita) {
        const opciones = this.catalogos.tratamientos.map(item => ({
            value: String(item.id),
            label: `${item.nombre} · ${money(item.precio)}`
        }));
        llenarSelect(this.tratamientoSelect, "Seleccioná un tratamiento", opciones);
        if (cita?.idTratamiento) {
            this.tratamientoSelect.value = String(cita.idTratamiento);
        }
    }

    poblarDentistas(cita) {
        const dentistas = this.catalogos.dentistas;
        const opciones = dentistas.map(item => ({ value: String(item.id), label: item.nombre }));

        if (!dentistas.length) {
            llenarSelect(this.dentistaSelect, "Sin odontólogos disponibles", []);
            this.avisoDentistas.textContent =
                "El backend todavía no expone un listado de usuarios: el catálogo de odontólogos se deduce de las citas existentes. Programá una cita para habilitarlo.";
            this.avisoDentistas.hidden = false;
        } else {
            llenarSelect(this.dentistaSelect, "Seleccioná un odontólogo", opciones);
            this.avisoDentistas.hidden = true;
            if (cita?.idDentista) {
                this.dentistaSelect.value = String(cita.idDentista);
            }
        }
    }

    /** Citas del paciente que todavía admiten atención (vínculo opcional). */
    async poblarCitas(paciente, cita) {
        llenarSelect(this.citaSelect, "Sin cita vinculada", []);

        if (cita) {
            llenarSelect(this.citaSelect, null, [{
                value: String(cita.id),
                label: `${dateTime(cita.fechaHoraInicio)} · ${cita.tratamiento || "Cita"}`
            }]);
            this.citaSelect.value = String(cita.id);
            this.citaSelect.disabled = true;
            return;
        }

        this.citaSelect.disabled = false;
        const idPaciente = paciente?.id ? Number(paciente.id) : Number(this.pacienteSelect.value);
        if (!idPaciente) return;

        try {
            const citas = await CitaService.listar({ desde: inicioBarrido(), hasta: finBarrido() });
            const candidatas = citas.filter(item =>
                Number(item.idPaciente) === idPaciente
                && ESTADOS_VINCULABLES.includes(item.estado));

            llenarSelect(this.citaSelect, "Sin cita vinculada", candidatas.map(item => ({
                value: String(item.id),
                label: `${dateTime(item.fechaHoraInicio)} · ${item.tratamiento || "Cita"} (${humanize(item.estado)})`
            })));
        } catch {
            // El vínculo con la cita es opcional: si falla, se registra sin cita.
            llenarSelect(this.citaSelect, "Sin cita vinculada", []);
        }
    }

    /* ------------------------------------------------------------------ *
     * Alta
     * ------------------------------------------------------------------ */

    async guardar(event) {
        event.preventDefault();
        if (this.guardando) return;

        const realizado = this.leerFormulario();
        const error = this.validar(realizado);
        if (error) {
            this.mostrarError(error);
            return;
        }

        this.guardando = true;
        this.submitButton.disabled = true;
        this.submitButton.textContent = "Registrando…";
        this.errorBox.hidden = true;

        try {
            const creado = await RealizadoService.registrar(realizado);
            const detalleCita = creado?.idCita ? " La cita quedó marcada como atendida." : "";
            NotificationService.success(
                `Tratamiento registrado por ${money(creado?.costo)}.${detalleCita}`
            );
            this.cerrar();
            if (typeof this.onRegistered === "function") {
                await this.onRegistered(creado);
            }
        } catch (respuesta) {
            this.mostrarError(respuesta.message);
        } finally {
            this.guardando = false;
            this.submitButton.disabled = false;
            this.submitButton.textContent = "Registrar tratamiento";
        }
    }

    leerFormulario() {
        return {
            idPaciente: this.pacienteSelect.value,
            idTratamiento: this.tratamientoSelect.value,
            idDentista: this.dentistaSelect.value,
            idCita: this.citaSelect.value || null,
            fechaRealizacion: aIso(this.fechaInput.value),
            detalleProcedimiento: this.detalleInput.value.trim(),
            observaciones: this.observacionesInput.value.trim()
        };
    }

    /** Validación de conveniencia: el backend sigue siendo la autoridad. */
    validar(realizado) {
        if (!realizado.idPaciente) return "Seleccioná el paciente.";
        if (!realizado.idTratamiento) return "Seleccioná el tratamiento realizado.";
        if (!realizado.idDentista) return "Seleccioná el odontólogo que realizó el procedimiento.";
        if (!realizado.fechaRealizacion) return "Indicá la fecha y hora de realización.";
        if (esFuturo(realizado.fechaRealizacion)) return "La fecha de realización no puede ser futura.";
        if (!realizado.detalleProcedimiento) {
            return "El detalle del procedimiento es obligatorio: describí qué se realizó.";
        }
        return null;
    }

    mostrarError(mensaje) {
        this.errorBox.textContent = mensaje;
        this.errorBox.hidden = false;
    }
}

/* ----------------------------------------------------------------------------
   Helpers de fecha y select
   ---------------------------------------------------------------------------- */

/** Valor para <input type="datetime-local"> en hora local (YYYY-MM-DDTHH:mm). */
export function valorLocal(fecha) {
    const date = fecha instanceof Date ? fecha : new Date(fecha);
    if (Number.isNaN(date.getTime())) return "";
    const pad = numero => String(numero).padStart(2, "0");
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
        + `T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/** Ahora en formato datetime-local, para el atributo max. */
function ahoraLocal() {
    return valorLocal(new Date());
}

/** Convierte el valor de un datetime-local al instante ISO con offset. */
export function aIso(valor) {
    const texto = String(valor ?? "").trim();
    if (!texto) return null;
    const date = new Date(texto);
    return Number.isNaN(date.getTime()) ? null : date.toISOString();
}

function esFuturo(valor) {
    const date = valor instanceof Date ? valor : new Date(valor);
    return !Number.isNaN(date.getTime()) && date.getTime() > Date.now();
}

function inicioBarrido() {
    const fecha = new Date();
    fecha.setFullYear(fecha.getFullYear() - 1);
    return fecha.toISOString();
}

function finBarrido() {
    const fecha = new Date();
    fecha.setFullYear(fecha.getFullYear() + 1);
    return fecha.toISOString();
}

function nombrePaciente(paciente) {
    return `${paciente.nombres || ""} ${paciente.apellidos || ""}`.trim() || `Paciente ${paciente.id}`;
}

/** Reemplaza las opciones de un select, con placeholder opcional. */
export function llenarSelect(select, placeholder, opciones) {
    const nodos = [];
    if (placeholder !== null && placeholder !== undefined) {
        nodos.push(el("option", { value: "", text: placeholder }));
    }
    opciones.forEach(opcion => {
        nodos.push(el("option", { value: opcion.value, text: opcion.label }));
    });
    select.replaceChildren(...nodos);
    return select;
}
