/* ============================================================================
   VISTA: AGENDA DE CITAS (S4, S6)
   ----------------------------------------------------------------------------
   Programa, reprograma y cancela citas, y desde cada cita permite registrar el
   procedimiento realizado (S8): al vincular la cita, el backend la deja
   ATENDIDA, de modo que la agenda y el historial clinico nunca se contradicen.

   Filtros: rango de fechas (lo resuelve el backend con desde/hasta) y estado
   (el backend NO acepta ese filtro, se aplica en el cliente).

   Permisos:
   - OP_CITA_VER          -> ver la agenda
   - OP_CITA_CREAR        -> programar
   - OP_CITA_REPROGRAMAR  -> reprogramar
   - OP_CITA_CANCELAR     -> cancelar
   - OP_REALIZADO_REGISTRAR -> registrar el procedimiento (marca ATENDIDA)
   - OP_RECORDATORIO_VER  -> abrir el historial de recordatorios de una cita (S5)

   Endpoints consumidos:
   - GET   /api/citas?desde=&hasta=
   - POST  /api/citas
   - PATCH /api/citas/{id}/reprogramar
   - PATCH /api/citas/{id}/cancelar
   - GET   /api/pacientes, GET /api/tratamientos (catalogos del alta)
   - GET   /api/usuarios?rol=DENTISTA via DentistaService (odontologos activos, S20)
   - POST  /api/tratamientos-realizados (via RealizadoForm)
   El historial de recordatorios de una cita no se pide aca: la fila solo enlaza
   a #/recordatorios?idCita=<id>, que es la pantalla que lo consulta (S5).
   ============================================================================ */
import { AuthService } from "../services/auth-service.js";
import { CitaService } from "../services/cita-service.js";
import { DentistaService } from "../services/dentista-service.js";
import { PacienteService } from "../services/paciente-service.js";
import { TratamientoService } from "../services/tratamiento-service.js";
import { NotificationService } from "../services/notification-service.js";
import { CONFIG } from "../config.js";
import {
    el, pageHeader, loadingState, errorState, table, formField, dateTime,
    estadoBadge, humanize, money
} from "../core/dom.js";
import { RealizadoForm, aIso, llenarSelect, valorLocal } from "./realizado-form.js";

/** Estados que ya no admiten reprogramacion ni cancelacion (S6). */
const ESTADOS_CERRADOS = ["ATENDIDA", "CANCELADA"];
/** Dias de agenda que se muestran por defecto (igual que el backend). */
const DIAS_POR_DEFECTO = 30;

export class AgendaView {

    constructor({ route, params, navigate, query } = {}) {
        this.route = route;
        this.params = params;
        this.navigate = navigate;
        this.query = query;
        this.citas = [];
        this.filtros = { desde: hoyIso(), hasta: sumarDias(hoyIso(), DIAS_POR_DEFECTO), estado: "" };
        this.reprogramando = null;
        this.catalogos = null;
        this.guardando = false;
    }

    async mount(container) {
        this.container = container;
        this.realizadoForm = new RealizadoForm({
            onRegistered: async () => {
                await this.cargar();
            }
        });

        container.replaceChildren(
            this.header(),
            this.cardFiltros(),
            this.cardLista(),
            this.cardFormulario(),
            this.cardReprogramar(),
            this.realizadoForm.element
        );

        await this.cargar();
        return null;
    }

    unmount() {
        this.realizadoForm = null;
    }

    /* ------------------------------------------------------------------ *
     * Estructura
     * ------------------------------------------------------------------ */

    header() {
        const acciones = [];
        if (AuthService.can(CONFIG.PERMISSIONS.CITA_CREAR)) {
            acciones.push(el("button", {
                type: "button",
                class: "btn btn-primary",
                id: "btn-nueva-cita",
                text: "Programar cita",
                on: { click: () => this.abrirFormulario() }
            }));
        }

        return pageHeader(
            "Agenda de citas",
            "Programá, reprogramá y cancelá citas. Registrar el procedimiento deja la cita como atendida.",
            acciones
        );
    }

    cardFiltros() {
        this.filtroDesde = el("input", {
            type: "date",
            id: "filtro-cita-desde",
            class: "form-control",
            value: this.filtros.desde
        });
        this.filtroHasta = el("input", {
            type: "date",
            id: "filtro-cita-hasta",
            class: "form-control",
            value: this.filtros.hasta
        });
        this.filtroEstado = el("select", { id: "filtro-cita-estado", class: "form-control" });
        llenarSelect(this.filtroEstado, "Todos los estados",
            CONFIG.ESTADOS.CITA.map(estado => ({ value: estado, label: humanize(estado) })));

        return el("div", { class: "card" }, [
            el("div", { class: "toolbar" }, [
                el("div", { class: "form-group toolbar-grow mb-0" }, [
                    el("label", { for: "filtro-cita-desde", text: "Desde" }),
                    this.filtroDesde
                ]),
                el("div", { class: "form-group toolbar-grow mb-0" }, [
                    el("label", { for: "filtro-cita-hasta", text: "Hasta" }),
                    this.filtroHasta
                ]),
                el("div", { class: "form-group toolbar-grow mb-0" }, [
                    el("label", { for: "filtro-cita-estado", text: "Estado" }),
                    this.filtroEstado
                ]),
                el("button", {
                    type: "button",
                    class: "btn btn-primary",
                    id: "btn-filtrar-citas",
                    text: "Aplicar filtros",
                    on: { click: () => this.aplicarFiltros() }
                }),
                el("button", {
                    type: "button",
                    class: "btn btn-outline",
                    id: "btn-limpiar-filtros-cita",
                    text: "Limpiar",
                    on: { click: () => this.limpiarFiltros() }
                })
            ])
        ]);
    }

    cardLista() {
        this.lista = el("div", { id: "lista-citas" }, [loadingState("Cargando agenda…")]);
        this.contador = el("span", { class: "badge badge-primary", text: "0 citas" });

        return el("div", { class: "card" }, [
            el("div", { class: "card-header" }, [
                el("h2", { text: "Citas del período" }),
                el("div", { class: "card-header-actions" }, [this.contador])
            ]),
            this.lista
        ]);
    }

    cardFormulario() {
        this.errorFormulario = el("div", { class: "alert-box danger", id: "form-cita-error", hidden: true });

        this.campos = {
            idPaciente: el("select", { id: "cita-paciente", class: "form-control" }),
            idTratamiento: el("select", { id: "cita-tratamiento", class: "form-control" }),
            idDentista: el("select", { id: "cita-dentista", class: "form-control" }),
            fechaHoraInicio: el("input", {
                type: "datetime-local",
                id: "cita-fecha-hora",
                class: "form-control",
                min: ahoraLocal()
            }),
            motivo: el("input", {
                type: "text",
                id: "cita-motivo",
                class: "form-control",
                maxlength: "200",
                placeholder: "Motivo de la consulta (opcional)"
            })
        };
        this.avisoOdontologosTexto = el("p", { class: "mb-0" });
        this.avisoOdontologos = el("div", { class: "alert-box info", id: "aviso-odontologos-agenda", hidden: true }, [
            this.avisoOdontologosTexto,
            AuthService.can(CONFIG.PERMISSIONS.USUARIOS_ADMIN)
                ? el("a", {
                    class: "btn btn-sm btn-outline mt-1",
                    id: "link-cargar-odontologos",
                    href: "#/usuarios",
                    text: "Cargar odontólogos en Usuarios y roles"
                })
                : null
        ]);

        this.formulario = el("form", {
            id: "form-cita",
            novalidate: true,
            on: { submit: event => this.guardar(event) }
        }, [
            el("div", { class: "form-grid" }, [
                formField("Paciente", this.campos.idPaciente, { required: true }),
                formField("Tratamiento", this.campos.idTratamiento, {
                    required: true,
                    hint: "La duración de la cita sale del catálogo."
                }),
                formField("Odontólogo", this.campos.idDentista, { required: true }),
                formField("Fecha y hora de inicio", this.campos.fechaHoraInicio, { required: true })
            ]),
            formField("Motivo", this.campos.motivo, { hint: "Opcional, máximo 200 caracteres." }),
            this.avisoOdontologos,
            this.errorFormulario,
            el("div", { class: "form-actions" }, [
                el("button", {
                    type: "submit",
                    class: "btn btn-primary",
                    id: "btn-guardar-cita",
                    text: "Programar cita"
                }),
                el("button", {
                    type: "button",
                    class: "btn btn-outline",
                    id: "btn-cancelar-cita",
                    text: "Cancelar",
                    on: { click: () => this.cerrarFormulario() }
                })
            ])
        ]);

        this.panelFormulario = el("div", { class: "card form-panel", id: "panel-cita-form", hidden: true }, [
            el("div", { class: "card-header" }, [el("h2", { text: "Nueva cita" })]),
            this.formulario
        ]);

        return this.panelFormulario;
    }

    cardReprogramar() {
        this.contextoReprogramar = el("p", { class: "text-muted text-sm" });
        this.errorReprogramar = el("div", {
            class: "alert-box danger", id: "form-reprogramar-error", hidden: true
        });
        this.reprogramarFecha = el("input", {
            type: "datetime-local",
            id: "reprogramar-fecha-hora",
            class: "form-control",
            min: ahoraLocal()
        });
        this.reprogramarDentista = el("select", { id: "reprogramar-dentista", class: "form-control" });

        this.formReprogramar = el("form", {
            id: "form-reprogramar",
            novalidate: true,
            on: { submit: event => this.confirmarReprogramacion(event) }
        }, [
            this.contextoReprogramar,
            el("div", { class: "form-grid" }, [
                formField("Nueva fecha y hora", this.reprogramarFecha, { required: true }),
                formField("Odontólogo", this.reprogramarDentista, {
                    hint: "Dejá el actual o asigná otro."
                })
            ]),
            this.errorReprogramar,
            el("div", { class: "form-actions" }, [
                el("button", {
                    type: "submit",
                    class: "btn btn-primary",
                    id: "btn-guardar-reprogramacion",
                    text: "Reprogramar cita"
                }),
                el("button", {
                    type: "button",
                    class: "btn btn-outline",
                    id: "btn-cancelar-reprogramacion",
                    text: "Cerrar",
                    on: { click: () => this.cerrarReprogramar() }
                })
            ])
        ]);

        this.panelReprogramar = el("div", {
            class: "card form-panel", id: "panel-reprogramar-form", hidden: true
        }, [
            el("div", { class: "card-header" }, [
                el("h2", { text: "Reprogramar cita" }),
                el("span", { class: "badge badge-warning", text: "S6" })
            ]),
            this.formReprogramar
        ]);

        return this.panelReprogramar;
    }

    /* ------------------------------------------------------------------ *
     * Datos
     * ------------------------------------------------------------------ */

    async cargar() {
        this.lista.replaceChildren(loadingState("Cargando agenda…"));
        try {
            this.citas = await CitaService.listar({
                desde: inicioDelDia(this.filtros.desde),
                hasta: finExclusivoDelDia(this.filtros.hasta)
            });
            this.renderTabla();
        } catch (error) {
            this.contador.textContent = "Sin datos";
            this.lista.replaceChildren(errorState(error.message));
        }
    }

    visibles() {
        if (!this.filtros.estado) return this.citas;
        return this.citas.filter(cita => cita.estado === this.filtros.estado);
    }

    renderTabla() {
        const lista = this.visibles();
        this.contador.textContent = this.filtros.estado
            ? `${lista.length} de ${this.citas.length} cita(s)`
            : `${this.citas.length} cita(s)`;

        const puedeReprogramar = AuthService.can(CONFIG.PERMISSIONS.CITA_REPROGRAMAR);
        const puedeCancelar = AuthService.can(CONFIG.PERMISSIONS.CITA_CANCELAR);
        const puedeRegistrar = AuthService.can(CONFIG.PERMISSIONS.REALIZADO_REGISTRAR);

        const rows = lista.map(cita => {
            const abierta = !ESTADOS_CERRADOS.includes(cita.estado);
            const acciones = [];

            if (puedeRegistrar && cita.estado !== "CANCELADA") {
                acciones.push(el("button", {
                    type: "button",
                    class: "btn btn-sm btn-success",
                    text: "Registrar procedimiento",
                    dataset: { accion: "realizado", id: cita.id },
                    on: { click: () => this.registrarProcedimiento(cita) }
                }));
            }
            if (puedeReprogramar && abierta) {
                acciones.push(el("button", {
                    type: "button",
                    class: "btn btn-sm btn-outline",
                    text: "Reprogramar",
                    dataset: { accion: "reprogramar", id: cita.id },
                    on: { click: () => this.abrirReprogramar(cita) }
                }));
            }
            if (puedeCancelar && abierta) {
                acciones.push(el("button", {
                    type: "button",
                    class: "btn btn-sm btn-danger",
                    text: "Cancelar",
                    dataset: { accion: "cancelar", id: cita.id },
                    on: { click: () => this.cancelar(cita) }
                }));
            }
            if (AuthService.can(CONFIG.PERMISSIONS.PACIENTE_HISTORIAL_VER)) {
                acciones.push(el("a", {
                    class: "btn btn-sm btn-outline",
                    href: `#/pacientes/${cita.idPaciente}/historial`,
                    text: "Historial"
                }));
            }
            // S5: el historial de recordatorios de la cita queda a un clic desde la
            // propia fila, con el filtro por cita ya aplicado en la pantalla.
            if (AuthService.can(CONFIG.PERMISSIONS.RECORDATORIO_VER)) {
                acciones.push(el("a", {
                    class: "btn btn-sm btn-outline",
                    href: `#/recordatorios?idCita=${cita.id}`,
                    text: "Recordatorios"
                }));
            }

            return [
                el("span", { class: "cell-strong", text: dateTime(cita.fechaHoraInicio) }),
                `${cita.duracionMinutos ?? "—"} min`,
                cita.paciente || "—",
                cita.tratamiento || "—",
                cita.dentista || "—",
                estadoBadge(cita.estado),
                cita.motivo || "—",
                el("div", { class: "inline-actions" }, acciones.length
                    ? acciones
                    : [el("span", { class: "text-muted text-sm", text: "Solo lectura" })])
            ];
        });

        this.lista.replaceChildren(table(
            ["Fecha y hora", "Duración", "Paciente", "Tratamiento", "Odontólogo", "Estado", "Motivo", "Acciones"],
            rows,
            {
                emptyMessage: this.filtros.estado
                    ? `No hay citas en estado ${humanize(this.filtros.estado)} en el período seleccionado.`
                    : "No hay citas en el período seleccionado."
            }
        ));
    }

    aplicarFiltros() {
        const desde = this.filtroDesde.value;
        const hasta = this.filtroHasta.value;

        if (desde && hasta && desde > hasta) {
            NotificationService.warning("La fecha inicial no puede ser posterior a la final.");
            return;
        }

        this.filtros = {
            desde: desde || hoyIso(),
            hasta: hasta || sumarDias(hoyIso(), DIAS_POR_DEFECTO),
            estado: this.filtroEstado.value
        };
        this.cargar();
    }

    limpiarFiltros() {
        this.filtros = { desde: hoyIso(), hasta: sumarDias(hoyIso(), DIAS_POR_DEFECTO), estado: "" };
        this.filtroDesde.value = this.filtros.desde;
        this.filtroHasta.value = this.filtros.hasta;
        this.filtroEstado.value = "";
        this.cargar();
    }

    /* ------------------------------------------------------------------ *
     * Alta de cita
     * ------------------------------------------------------------------ */

    async abrirFormulario() {
        this.errorFormulario.hidden = true;
        this.avisoOdontologos.hidden = true;
        this.panelFormulario.hidden = false;
        this.panelFormulario.scrollIntoView({ behavior: "smooth", block: "nearest" });
        this.campos.idPaciente.focus();

        try {
            await this.cargarCatalogos();
        } catch (error) {
            this.mostrarErrorFormulario(error.message);
        }
    }

    cerrarFormulario() {
        this.panelFormulario.hidden = true;
        this.formulario.reset();
        this.errorFormulario.hidden = true;
    }

    async cargarCatalogos() {
        if (this.catalogos) {
            this.poblarCatalogos(this.catalogos);
            return this.catalogos;
        }

        const [pacientes, tratamientos, dentistas] = await Promise.all([
            PacienteService.listar(),
            TratamientoService.listar(),
            DentistaService.listar()
        ]);

        this.catalogos = { pacientes, tratamientos, dentistas };
        this.poblarCatalogos(this.catalogos);
        return this.catalogos;
    }

    poblarCatalogos({ pacientes, tratamientos, dentistas }) {
        llenarSelect(this.campos.idPaciente, "Seleccioná un paciente", pacientes.map(paciente => ({
            value: String(paciente.id),
            label: `${paciente.nombres} ${paciente.apellidos}`
        })));

        llenarSelect(this.campos.idTratamiento, "Seleccioná un tratamiento", tratamientos.map(tratamiento => ({
            value: String(tratamiento.id),
            label: `${tratamiento.nombre} · ${tratamiento.duracionMinutos} min · ${money(tratamiento.precio)}`
        })));

        if (!dentistas.length) {
            llenarSelect(this.campos.idDentista, "Sin odontólogos activos", []);
            // S20: el catalogo sale del padron de usuarios, no de las citas ya cargadas.
            this.avisoOdontologosTexto.textContent = AuthService.can(CONFIG.PERMISSIONS.USUARIOS_ADMIN)
                ? "No hay odontólogos activos cargados: el selector se alimenta de los usuarios con rol Odontólogo. Creá uno en Usuarios y roles para poder programar citas."
                : "No hay odontólogos activos cargados: el selector se alimenta de los usuarios con rol Odontólogo. Pedile a un administrador que cree uno para poder programar citas.";
            this.avisoOdontologos.hidden = false;
        } else {
            llenarSelect(this.campos.idDentista, "Seleccioná un odontólogo", dentistas.map(dentista => ({
                value: String(dentista.id),
                label: dentista.nombre
            })));
            this.avisoOdontologos.hidden = true;
        }
    }

    async guardar(event) {
        event.preventDefault();
        if (this.guardando) return;

        const cita = {
            idPaciente: this.campos.idPaciente.value,
            idTratamiento: this.campos.idTratamiento.value,
            idDentista: this.campos.idDentista.value,
            fechaHoraInicio: aIso(this.campos.fechaHoraInicio.value),
            motivo: this.campos.motivo.value.trim()
        };

        const error = this.validar(cita);
        if (error) {
            this.mostrarErrorFormulario(error);
            return;
        }

        this.guardando = true;
        const boton = this.formulario.querySelector("#btn-guardar-cita");
        boton.disabled = true;
        boton.textContent = "Programando…";
        this.errorFormulario.hidden = true;

        try {
            const creada = await CitaService.crear(cita);
            NotificationService.success(`Cita programada para el ${dateTime(creada.fechaHoraInicio)}.`);
            this.cerrarFormulario();
            await this.cargar();
        } catch (respuesta) {
            // 409 solapamiento y 422 fecha pasada llegan con el texto del backend.
            this.mostrarErrorFormulario(respuesta.message);
        } finally {
            this.guardando = false;
            boton.disabled = false;
            boton.textContent = "Programar cita";
        }
    }

    validar(cita) {
        if (!cita.idPaciente) return "Seleccioná el paciente.";
        if (!cita.idTratamiento) return "Seleccioná el tratamiento.";
        if (!cita.idDentista) return "Seleccioná el odontólogo.";
        if (!cita.fechaHoraInicio) return "Indicá la fecha y hora de inicio.";
        if (new Date(cita.fechaHoraInicio).getTime() <= Date.now()) {
            return "La fecha y hora de la cita debe ser futura.";
        }
        if (cita.motivo.length > 200) return "El motivo no puede superar los 200 caracteres.";
        return null;
    }

    mostrarErrorFormulario(mensaje) {
        this.errorFormulario.textContent = mensaje;
        this.errorFormulario.hidden = false;
    }

    /* ------------------------------------------------------------------ *
     * Reprogramacion (S6)
     * ------------------------------------------------------------------ */

    async abrirReprogramar(cita) {
        this.reprogramando = cita;
        this.errorReprogramar.hidden = true;
        this.contextoReprogramar.textContent =
            `${cita.paciente || "Paciente"} · ${cita.tratamiento || "Tratamiento"} · actualmente ${dateTime(cita.fechaHoraInicio)}`;

        this.reprogramarFecha.value = valorLocal(new Date(Date.now() + 60 * 60 * 1000));

        try {
            const { dentistas } = await this.cargarCatalogos();
            llenarSelect(this.reprogramarDentista, "Mantener el odontólogo actual",
                dentistas.map(dentista => ({ value: String(dentista.id), label: dentista.nombre })));
            this.reprogramarDentista.value = "";
        } catch {
            llenarSelect(this.reprogramarDentista, "Mantener el odontólogo actual", []);
        }

        this.panelReprogramar.hidden = false;
        this.panelReprogramar.scrollIntoView({ behavior: "smooth", block: "nearest" });
        this.reprogramarFecha.focus();
    }

    cerrarReprogramar() {
        this.reprogramando = null;
        this.panelReprogramar.hidden = true;
        this.formReprogramar.reset();
        this.errorReprogramar.hidden = true;
    }

    async confirmarReprogramacion(event) {
        event.preventDefault();
        if (this.guardando || !this.reprogramando) return;

        const fechaHoraInicio = aIso(this.reprogramarFecha.value);
        if (!fechaHoraInicio) {
            this.mostrarErrorReprogramar("Indicá la nueva fecha y hora.");
            return;
        }
        if (new Date(fechaHoraInicio).getTime() <= Date.now()) {
            this.mostrarErrorReprogramar("La nueva fecha y hora debe ser futura.");
            return;
        }

        this.guardando = true;
        const boton = this.formReprogramar.querySelector("#btn-guardar-reprogramacion");
        boton.disabled = true;
        boton.textContent = "Reprogramando…";
        this.errorReprogramar.hidden = true;

        try {
            const nueva = await CitaService.reprogramar(this.reprogramando.id, {
                fechaHoraInicio,
                idDentista: this.reprogramarDentista.value || null
            });
            NotificationService.success(
                `Cita reprogramada para el ${dateTime(nueva.fechaHoraInicio)}. La cita original quedó como reprogramada.`
            );
            this.cerrarReprogramar();
            await this.cargar();
        } catch (respuesta) {
            this.mostrarErrorReprogramar(respuesta.message);
        } finally {
            this.guardando = false;
            boton.disabled = false;
            boton.textContent = "Reprogramar cita";
        }
    }

    mostrarErrorReprogramar(mensaje) {
        this.errorReprogramar.textContent = mensaje;
        this.errorReprogramar.hidden = false;
    }

    /* ------------------------------------------------------------------ *
     * Cancelacion (S6)
     * ------------------------------------------------------------------ */

    async cancelar(cita) {
        const confirmado = await NotificationService.confirm(
            `¿Cancelar la cita de ${cita.paciente || "el paciente"} del ${dateTime(cita.fechaHoraInicio)}? Se cancelarán también sus recordatorios pendientes.`,
            { confirmLabel: "Cancelar cita", cancelLabel: "Volver" }
        );
        if (!confirmado) return;

        try {
            await CitaService.cancelar(cita.id);
            NotificationService.success("Cita cancelada correctamente.");
            await this.cargar();
        } catch (error) {
            NotificationService.error(error.message);
        }
    }

    /* ------------------------------------------------------------------ *
     * Registro del procedimiento (S8)
     * ------------------------------------------------------------------ */

    registrarProcedimiento(cita) {
        this.realizadoForm.abrir({
            paciente: { id: cita.idPaciente, nombres: cita.paciente || "" },
            cita
        });
    }
}

/* ----------------------------------------------------------------------------
   Helpers de fecha (hora local del navegador)
   ---------------------------------------------------------------------------- */

function hoyIso() {
    const hoy = new Date();
    const mes = String(hoy.getMonth() + 1).padStart(2, "0");
    const dia = String(hoy.getDate()).padStart(2, "0");
    return `${hoy.getFullYear()}-${mes}-${dia}`;
}

function sumarDias(iso, dias) {
    const [year, month, day] = iso.split("-").map(Number);
    const fecha = new Date(year, month - 1, day);
    fecha.setDate(fecha.getDate() + dias);
    return valorLocal(fecha).slice(0, 10);
}

function ahoraLocal() {
    return valorLocal(new Date());
}

/** Inicio del dia en ISO con offset, que es lo que espera el backend. */
function inicioDelDia(iso) {
    const [year, month, day] = iso.split("-").map(Number);
    return new Date(year, month - 1, day, 0, 0, 0).toISOString();
}

/** Limite superior exclusivo: el backend filtra con fecha_hora_inicio < hasta. */
function finExclusivoDelDia(iso) {
    const [year, month, day] = iso.split("-").map(Number);
    return new Date(year, month - 1, day + 1, 0, 0, 0).toISOString();
}
