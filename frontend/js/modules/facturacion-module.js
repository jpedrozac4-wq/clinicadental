/* ============================================================================
   VISTA: FACTURACION Y PAGOS (S9, S10)
   ----------------------------------------------------------------------------
   Cubre el ciclo completo del comprobante:
   1. Emitir una factura eligiendo un paciente y sus tratamientos realizados.
   2. Listar comprobantes con numero, estado, total y saldo.
   3. Abrir un comprobante con sus lineas de detalle y sus pagos.
   4. Registrar un pago total o parcial (monto, forma de pago y referencia).
   5. Descargar el comprobante en PDF con el helper binario del cliente HTTP.

   Reglas del backend que la vista refleja sin reinventarlas:
   - 409 si un tratamiento realizado ya fue facturado o es de otro paciente.
   - 409 si la suma de pagos supera el total de la factura.
   - Una factura ANULADA no admite pagos.
   Los mensajes se muestran tal cual llegan.

   Permisos:
   - OP_FACTURA_VER      -> ver el modulo
   - OP_FACTURA_EMITIR   -> emitir comprobantes
   - OP_PAGO_REGISTRAR   -> registrar pagos

   Endpoints consumidos:
   - GET  /api/facturas, GET /api/facturas/{id}, GET /api/facturas/{id}/pdf
   - POST /api/facturas
   - GET  /api/facturas/{id}/pagos, POST /api/facturas/{id}/pagos
   - GET  /api/pacientes, GET /api/tratamientos-realizados?idPaciente=
   ============================================================================ */
import { AuthService } from "../services/auth-service.js";
import { PacienteService } from "../services/paciente-service.js";
import { RealizadoService } from "../services/realizado-service.js";
import { FacturaService, METODOS_PAGO } from "../services/factura-service.js";
import { NotificationService } from "../services/notification-service.js";
import { CONFIG } from "../config.js";
import {
    el, pageHeader, loadingState, errorState, table, formField, money, dateTime,
    dateOnly, estadoBadge, humanize
} from "../core/dom.js";
import { llenarSelect } from "./realizado-form.js";

export class FacturacionView {

    constructor({ route, params, navigate, query } = {}) {
        this.route = route;
        this.params = params;
        this.navigate = navigate;
        this.query = query;
        this.facturas = [];
        this.filtroTexto = "";
        this.filtroEstado = "";
        this.candidatos = [];
        this.seleccion = new Set();
        this.facturaAbierta = null;
        this.pagos = [];
        this.guardando = false;
    }

    async mount(container) {
        this.container = container;
        container.replaceChildren(
            this.header(),
            this.cardEmitir(),
            this.cardLista(),
            this.cardDetalle()
        );

        await this.cargar();
        return null;
    }

    /* ------------------------------------------------------------------ *
     * Estructura
     * ------------------------------------------------------------------ */

    header() {
        return pageHeader(
            "Facturación y pagos",
            "Emití comprobantes de los tratamientos realizados y registrá pagos totales o parciales."
        );
    }

    /* ---- Emision (S9) ---- */

    cardEmitir() {
        if (!AuthService.can(CONFIG.PERMISSIONS.FACTURA_EMITIR)) {
            this.panelEmitir = el("div", { hidden: true });
            return this.panelEmitir;
        }

        this.emitirPaciente = el("select", {
            id: "factura-paciente",
            class: "form-control",
            on: { change: () => this.cargarCandidatos() }
        });
        this.emitirCandidatos = el("div", { id: "factura-candidatos" }, [
            el("p", { class: "text-muted text-sm", text: "Elegí un paciente para ver sus tratamientos facturables." })
        ]);
        this.emitirObservaciones = el("textarea", {
            id: "factura-observaciones",
            class: "form-control",
            rows: "2",
            maxlength: "500"
        });
        this.emitirTotal = el("span", { class: "badge badge-success", text: "Total: —" });
        this.errorEmitir = el("div", { class: "alert-box danger", id: "form-factura-error", hidden: true });

        this.formEmitir = el("form", {
            id: "form-factura",
            novalidate: true,
            on: { submit: event => this.emitir(event) }
        }, [
            el("div", { class: "form-grid" }, [
                formField("Paciente", this.emitirPaciente, {
                    required: true,
                    hint: "Solo se facturan tratamientos del paciente elegido."
                })
            ]),
            el("div", { class: "form-group" }, [
                el("label", { text: "Tratamientos realizados a facturar", class: "is-required" }),
                this.emitirCandidatos
            ]),
            formField("Observaciones", this.emitirObservaciones, { hint: "Opcional." }),
            this.errorEmitir,
            el("div", { class: "form-actions" }, [
                this.emitirTotal,
                el("button", {
                    type: "submit",
                    class: "btn btn-primary",
                    id: "btn-emitir-factura",
                    text: "Emitir factura"
                }),
                el("button", {
                    type: "button",
                    class: "btn btn-outline",
                    id: "btn-limpiar-factura",
                    text: "Limpiar",
                    on: { click: () => this.limpiarEmision() }
                })
            ])
        ]);

        this.panelEmitir = el("div", { class: "card form-panel", id: "panel-factura-form" }, [
            el("div", { class: "card-header" }, [
                el("h2", { text: "Emitir comprobante" }),
                el("span", { class: "badge badge-primary", text: "S9" })
            ]),
            this.formEmitir
        ]);

        return this.panelEmitir;
    }

    /* ---- Listado (S9) ---- */

    cardLista() {
        this.filtroTextoInput = el("input", {
            type: "search",
            id: "filtro-factura",
            class: "form-control",
            placeholder: "Buscar por número o paciente",
            autocomplete: "off",
            on: { input: event => this.filtrar(event.target.value) }
        });
        this.filtroEstadoSelect = el("select", {
            id: "filtro-factura-estado",
            class: "form-control",
            on: { change: event => this.filtrarEstado(event.target.value) }
        });
        llenarSelect(this.filtroEstadoSelect, "Todos los estados",
            CONFIG.ESTADOS.FACTURA.map(estado => ({ value: estado, label: humanize(estado) })));

        this.lista = el("div", { id: "lista-facturas" }, [loadingState("Cargando comprobantes…")]);
        this.contador = el("span", { class: "badge badge-primary", text: "0 comprobantes" });

        return el("div", { class: "card" }, [
            el("div", { class: "card-header" }, [
                el("h2", { text: "Comprobantes emitidos" }),
                el("div", { class: "card-header-actions" }, [this.contador])
            ]),
            el("div", { class: "toolbar" }, [
                el("div", { class: "form-group toolbar-grow mb-0" }, [
                    el("label", { for: "filtro-factura", text: "Buscar" }),
                    this.filtroTextoInput
                ]),
                el("div", { class: "form-group toolbar-grow mb-0" }, [
                    el("label", { for: "filtro-factura-estado", text: "Estado" }),
                    this.filtroEstadoSelect
                ])
            ]),
            this.lista
        ]);
    }

    /* ---- Detalle y pagos (S9, S10) ---- */

    cardDetalle() {
        this.detalleTitulo = el("h2", { text: "Detalle del comprobante" });
        this.detalleBadge = el("span", { class: "badge badge-primary", text: "—" });
        this.detalleContenido = el("div", { id: "detalle-factura" }, [
            el("p", { class: "text-muted text-sm", text: "Elegí un comprobante de la lista para ver su detalle y sus pagos." })
        ]);

        return el("div", { class: "card", id: "panel-detalle-factura", hidden: true }, [
            el("div", { class: "card-header" }, [
                this.detalleTitulo,
                el("div", { class: "card-header-actions" }, [this.detalleBadge])
            ]),
            this.detalleContenido
        ]);
    }

    /* ------------------------------------------------------------------ *
     * Datos
     * ------------------------------------------------------------------ */

    async cargar() {
        this.lista.replaceChildren(loadingState("Cargando comprobantes…"));
        try {
            this.facturas = await FacturaService.listar();
            this.renderTabla();
            await this.cargarPacientes();
            if (this.facturaAbierta) {
                const vigente = this.facturas.find(factura => factura.id === this.facturaAbierta.id);
                if (vigente) {
                    await this.abrirDetalle(vigente);
                } else {
                    this.cerrarDetalle();
                }
            }
        } catch (error) {
            this.contador.textContent = "Sin datos";
            this.lista.replaceChildren(errorState(error.message));
        }
    }

    /** Ids de tratamientos realizados que ya tienen comprobante (derivado del detalle). */
    idsFacturados() {
        const ids = new Set();
        this.facturas.forEach(factura => {
            (factura.detalle || []).forEach(linea => {
                if (linea.idRealizado) ids.add(Number(linea.idRealizado));
            });
        });
        return ids;
    }

    async cargarPacientes() {
        if (!this.emitirPaciente) return;
        try {
            const pacientes = await PacienteService.listar();
            const seleccionado = this.emitirPaciente.value;
            llenarSelect(this.emitirPaciente, "Seleccioná un paciente", pacientes.map(paciente => ({
                value: String(paciente.id),
                label: `${paciente.nombres} ${paciente.apellidos}`
            })));
            this.emitirPaciente.value = seleccionado;
        } catch {
            llenarSelect(this.emitirPaciente, "Sin pacientes disponibles", []);
        }
    }

    visibles() {
        const texto = this.filtroTexto.toLowerCase();
        return this.facturas.filter(factura => {
            const coincideTexto = !texto
                || String(factura.numero || "").toLowerCase().includes(texto)
                || String(factura.paciente || "").toLowerCase().includes(texto);
            const coincideEstado = !this.filtroEstado || factura.estado === this.filtroEstado;
            return coincideTexto && coincideEstado;
        });
    }

    renderTabla() {
        const lista = this.visibles();
        this.contador.textContent = `${lista.length} de ${this.facturas.length} comprobante(s)`;

        const rows = lista.map(factura => {
            const saldo = Number(factura.saldo) || 0;
            const acciones = [
                el("button", {
                    type: "button",
                    class: "btn btn-sm btn-outline",
                    text: "Ver detalle",
                    dataset: { accion: "detalle", id: factura.id },
                    on: { click: () => this.abrirDetalle(factura) }
                }),
                el("button", {
                    type: "button",
                    class: "btn btn-sm btn-outline",
                    text: "PDF",
                    dataset: { accion: "pdf", id: factura.id },
                    on: { click: event => this.descargarPdf(factura, event.currentTarget) }
                })
            ];

            return [
                el("span", { class: "cell-strong", text: factura.numero || "—" }),
                factura.paciente || "—",
                dateOnly(factura.fechaEmision),
                estadoBadge(factura.estado),
                money(factura.total),
                money(factura.totalPagado),
                saldo > 0
                    ? el("span", { class: "cell-strong", text: money(saldo) })
                    : el("span", { class: "badge badge-success", text: "Sin saldo" }),
                el("div", { class: "inline-actions" }, acciones)
            ];
        });

        this.lista.replaceChildren(table(
            ["Número", "Paciente", "Emisión", "Estado", "Total", "Pagado", "Saldo", "Acciones"],
            rows,
            {
                emptyMessage: this.filtroTexto || this.filtroEstado
                    ? "No hay comprobantes que coincidan con el filtro."
                    : "Todavía no se emitieron comprobantes."
            }
        ));
    }

    filtrar(texto) {
        this.filtroTexto = String(texto || "").trim();
        this.renderTabla();
    }

    filtrarEstado(estado) {
        this.filtroEstado = estado;
        this.renderTabla();
    }

    /* ------------------------------------------------------------------ *
     * Emision
     * ------------------------------------------------------------------ */

    async cargarCandidatos() {
        const idPaciente = this.emitirPaciente.value;
        this.seleccion.clear();
        this.actualizarTotal();

        if (!idPaciente) {
            this.emitirCandidatos.replaceChildren(
                el("p", { class: "text-muted text-sm", text: "Elegí un paciente para ver sus tratamientos facturables." })
            );
            return;
        }

        this.emitirCandidatos.replaceChildren(loadingState("Buscando tratamientos realizados…"));

        try {
            const realizados = await RealizadoService.listar({ idPaciente });
            const facturados = this.idsFacturados();
            this.candidatos = realizados.map(realizado => ({
                ...realizado,
                facturado: facturados.has(Number(realizado.id))
            }));
            this.renderCandidatos();
        } catch (error) {
            this.emitirCandidatos.replaceChildren(errorState(error.message));
        }
    }

    renderCandidatos() {
        if (!this.candidatos.length) {
            this.emitirCandidatos.replaceChildren(el("p", {
                class: "text-muted text-sm",
                text: "Este paciente no tiene tratamientos realizados registrados. Registrá el procedimiento en la agenda o en su historial clínico."
            }));
            return;
        }

        const facturables = this.candidatos.filter(candidato => !candidato.facturado);

        if (!facturables.length) {
            this.emitirCandidatos.replaceChildren(el("p", {
                class: "alert-box warning",
                text: "Todos los tratamientos realizados de este paciente ya tienen comprobante emitido."
            }));
            return;
        }

        const items = this.candidatos.map(candidato => {
            const checkbox = el("input", {
                type: "checkbox",
                value: String(candidato.id),
                disabled: candidato.facturado,
                dataset: { realizadoId: candidato.id },
                on: { change: event => this.alternarSeleccion(candidato, event.target) }
            });

            return el("label", { class: `check-row${candidato.facturado ? " is-disabled" : ""}` }, [
                checkbox,
                el("span", { class: "check-row-main" }, [
                    el("span", { class: "check-row-title", text: candidato.tratamiento || "Tratamiento" }),
                    el("span", {
                        class: "text-muted text-sm",
                        text: `${dateTime(candidato.fechaRealizacion)} · ${candidato.dentista || "Sin odontólogo"}`
                    }),
                    el("span", { class: "text-muted text-sm", text: candidato.detalleProcedimiento || "—" })
                ]),
                el("span", { class: "check-row-amount" }, [
                    money(candidato.costo),
                    candidato.facturado ? el("span", { class: "badge badge-warning", text: "Ya facturado" }) : null
                ].filter(Boolean))
            ]);
        });

        this.emitirCandidatos.replaceChildren(el("div", { class: "check-list" }, items));
    }

    alternarSeleccion(candidato, checkbox) {
        if (checkbox.checked) {
            this.seleccion.add(Number(candidato.id));
        } else {
            this.seleccion.delete(Number(candidato.id));
        }
        this.actualizarTotal();
    }

    actualizarTotal() {
        const total = this.candidatos
            .filter(candidato => this.seleccion.has(Number(candidato.id)))
            .reduce((suma, candidato) => suma + (Number(candidato.costo) || 0), 0);

        this.emitirTotal.textContent = this.seleccion.size
            ? `Total: ${money(total)} · ${this.seleccion.size} tratamiento(s)`
            : "Total: —";
    }

    limpiarEmision() {
        this.seleccion.clear();
        this.candidatos = [];
        this.emitirPaciente.value = "";
        this.emitirObservaciones.value = "";
        this.errorEmitir.hidden = true;
        this.actualizarTotal();
        this.emitirCandidatos.replaceChildren(
            el("p", { class: "text-muted text-sm", text: "Elegí un paciente para ver sus tratamientos facturables." })
        );
    }

    async emitir(event) {
        event.preventDefault();
        if (this.guardando) return;

        if (!this.emitirPaciente.value) {
            this.mostrarErrorEmitir("Seleccioná el paciente.");
            return;
        }
        if (!this.seleccion.size) {
            this.mostrarErrorEmitir("Elegí al menos un tratamiento realizado para facturar.");
            return;
        }

        this.guardando = true;
        const boton = this.formEmitir.querySelector("#btn-emitir-factura");
        boton.disabled = true;
        boton.textContent = "Emitiendo…";
        this.errorEmitir.hidden = true;

        try {
            const factura = await FacturaService.emitir({
                idPaciente: this.emitirPaciente.value,
                idsRealizado: [...this.seleccion],
                observaciones: this.emitirObservaciones.value
            });
            NotificationService.success(`Comprobante ${factura.numero} emitido por ${money(factura.total)}.`);
            this.limpiarEmision();
            this.facturaAbierta = factura;
            await this.cargar();
        } catch (error) {
            // 409: tratamiento ya facturado o de otro paciente.
            this.mostrarErrorEmitir(error.message);
        } finally {
            this.guardando = false;
            boton.disabled = false;
            boton.textContent = "Emitir factura";
        }
    }

    mostrarErrorEmitir(mensaje) {
        this.errorEmitir.textContent = mensaje;
        this.errorEmitir.hidden = false;
    }

    /* ------------------------------------------------------------------ *
     * Detalle del comprobante
     * ------------------------------------------------------------------ */

    async abrirDetalle(factura) {
        this.facturaAbierta = factura;
        const panel = this.container.querySelector("#panel-detalle-factura");
        if (panel) panel.hidden = false;

        this.detalleTitulo.textContent = `Comprobante ${factura.numero || factura.id}`;
        this.detalleBadge.replaceChildren(estadoBadge(factura.estado));
        this.detalleContenido.replaceChildren(loadingState("Cargando detalle y pagos…"));

        try {
            const [comprobante, pagos] = await Promise.all([
                FacturaService.obtener(factura.id),
                FacturaService.pagos(factura.id)
            ]);
            this.pagos = pagos;
            this.renderDetalle(comprobante);
            panel?.scrollIntoView({ behavior: "smooth", block: "nearest" });
        } catch (error) {
            this.detalleContenido.replaceChildren(errorState(error.message));
        }
    }

    cerrarDetalle() {
        this.facturaAbierta = null;
        this.pagos = [];
        const panel = this.container?.querySelector("#panel-detalle-factura");
        if (panel) panel.hidden = true;
    }

    renderDetalle(factura) {
        const total = Number(factura.total) || 0;
        const pagado = Number(factura.totalPagado) || 0;
        const saldo = Number(factura.saldo) || 0;
        const pagable = saldo > 0 && factura.estado !== "ANULADA";

        this.detalleTitulo.textContent = `Comprobante ${factura.numero || factura.id}`;
        this.detalleBadge.replaceChildren(estadoBadge(factura.estado));

        const lineas = (factura.detalle || []).map(linea => [
            el("span", { class: "cell-strong", text: linea.descripcion || "—" }),
            linea.idRealizado ? `#${linea.idRealizado}` : "—",
            money(linea.monto)
        ]);

        const accionesCabecera = [
            el("button", {
                type: "button",
                class: "btn btn-sm btn-outline",
                text: "Descargar PDF",
                on: { click: event => this.descargarPdf(factura, event.currentTarget) }
            }),
            el("button", {
                type: "button",
                class: "btn btn-sm btn-ghost",
                text: "Cerrar detalle",
                on: { click: () => this.cerrarDetalle() }
            })
        ];

        this.detalleContenido.replaceChildren(
            el("dl", { class: "detail-list detail-list-grid" }, [
                detail("Paciente", factura.paciente || "—"),
                detail("Fecha de emisión", dateTime(factura.fechaEmision)),
                detail("Estado", estadoBadge(factura.estado)),
                detail("Total", money(total)),
                detail("Pagado", money(pagado)),
                detail("Saldo", money(saldo)),
                factura.observaciones ? detail("Observaciones", factura.observaciones) : null
            ].filter(Boolean)),
            el("div", { class: "inline-actions mt-2" }, accionesCabecera),
            sectionTitle("Líneas del comprobante"),
            table(["Descripción", "Realizado", "Monto"], lineas, {
                emptyMessage: "El comprobante no tiene líneas registradas."
            }),
            sectionTitle("Pagos registrados"),
            table(
                ["Fecha", "Monto", "Forma de pago", "Referencia"],
                this.pagos.map(pago => [
                    dateTime(pago.fechaPago),
                    el("span", { class: "cell-strong", text: money(pago.monto) }),
                    pago.metodoPago || "—",
                    pago.referencia || "—"
                ]),
                { emptyMessage: "Todavía no se registraron pagos sobre este comprobante." }
            ),
            pagable
                ? this.formPago(factura, saldo)
                : el("p", { class: "alert-box info", text: this.mensajeSinCobro(factura) })
        );
    }

    /** Aviso cuando el comprobante ya no admite pagos (anulado o cancelado). */
    mensajeSinCobro(factura) {
        if (factura.estado === "ANULADA") {
            return "El comprobante está anulado y no admite pagos.";
        }
        return "El comprobante está cancelado en su totalidad: no queda saldo por cobrar.";
    }

    formPago(factura, saldo) {
        const monto = el("input", {
            type: "number",
            id: "pago-monto",
            class: "form-control",
            min: "0.01",
            step: "0.01",
            max: String(saldo),
            value: String(saldo)
        });
        const metodo = el("select", { id: "pago-metodo", class: "form-control" });
        llenarSelect(metodo, "Seleccioná la forma de pago", this.opcionesMetodoPago());
        const referencia = el("input", {
            type: "text",
            id: "pago-referencia",
            class: "form-control",
            maxlength: "80",
            placeholder: "Nº de recibo, autorización o transferencia (opcional)"
        });
        const error = el("div", { class: "alert-box danger", id: "form-pago-error", hidden: true });
        const boton = el("button", {
            type: "submit",
            class: "btn btn-primary",
            id: "btn-registrar-pago",
            text: "Registrar pago"
        });

        const form = el("form", {
            id: "form-pago",
            novalidate: true,
            on: {
                submit: event => this.registrarPago(event, factura, {
                    monto, metodo, referencia, error, boton, saldo
                })
            }
        }, [
            sectionTitle("Registrar pago"),
            el("p", {
                class: "text-muted text-sm",
                text: `Saldo pendiente ${money(saldo)}. Podés cobrar el total o un pago parcial.`
            }),
            el("div", { class: "form-grid" }, [
                formField("Monto", monto, { required: true, hint: "No puede superar el saldo pendiente." }),
                formField("Forma de pago", metodo, { required: true }),
                formField("Referencia", referencia)
            ]),
            error,
            el("div", { class: "form-actions" }, [boton])
        ]);

        return form;
    }

    /**
     * Opciones de forma de pago. El catalogo de la semilla es el punto de
     * partida; los ids realmente usados en los pagos ya registrados del
     * comprobante tienen prioridad, para que un desajuste de numeracion en la
     * tabla cat_metodo_pago no rompa el cobro.
     */
    opcionesMetodoPago() {
        const porCodigo = new Map(METODOS_PAGO.map(item => [item.codigo, { id: item.id, label: item.label }]));
        this.pagos.forEach(pago => {
            if (pago.metodoPago && pago.idMetodoPago) {
                porCodigo.set(pago.metodoPago, { id: pago.idMetodoPago, label: humanize(pago.metodoPago) });
            }
        });
        return [...porCodigo.values()].map(item => ({ value: String(item.id), label: item.label }));
    }

    async registrarPago(event, factura, controles) {
        event.preventDefault();
        if (this.guardando) return;

        const monto = Number(controles.monto.value);
        const idMetodoPago = controles.metodo.value;

        if (!Number.isFinite(monto) || monto <= 0) {
            this.mostrarErrorPago(controles.error, "El monto debe ser mayor que cero.");
            return;
        }
        if (monto > controles.saldo) {
            this.mostrarErrorPago(controles.error,
                `El monto no puede superar el saldo pendiente (${money(controles.saldo)}).`);
            return;
        }
        if (!idMetodoPago) {
            this.mostrarErrorPago(controles.error, "Seleccioná la forma de pago.");
            return;
        }

        this.guardando = true;
        controles.boton.disabled = true;
        controles.boton.textContent = "Registrando…";
        controles.error.hidden = true;

        try {
            const pago = await FacturaService.registrarPago(factura.id, {
                monto,
                idMetodoPago,
                referencia: controles.referencia.value
            });
            NotificationService.success(`Pago de ${money(pago.monto)} registrado correctamente.`);
            const actualizada = await FacturaService.obtener(factura.id);
            this.facturaAbierta = actualizada;
            await this.cargar();
        } catch (error) {
            // 409: la suma de pagos supera el total o la factura está anulada.
            this.mostrarErrorPago(controles.error, error.message);
        } finally {
            this.guardando = false;
            controles.boton.disabled = false;
            controles.boton.textContent = "Registrar pago";
        }
    }

    mostrarErrorPago(errorBox, mensaje) {
        if (!errorBox) return;
        errorBox.textContent = mensaje;
        errorBox.hidden = false;
    }

    /* ------------------------------------------------------------------ *
     * Comprobante PDF
     * ------------------------------------------------------------------ */

    async descargarPdf(factura, boton) {
        const textoOriginal = boton ? boton.textContent : null;
        if (boton) {
            boton.disabled = true;
            boton.textContent = "Descargando…";
        }
        try {
            await FacturaService.descargarPdf(factura);
            NotificationService.success(`Comprobante ${factura.numero || factura.id} descargado.`);
        } catch (error) {
            NotificationService.error(error.message);
        } finally {
            if (boton) {
                boton.disabled = false;
                boton.textContent = textoOriginal;
            }
        }
    }
}

function detail(label, value) {
    return el("div", { class: "detail-row" }, [
        el("dt", { text: label }),
        el("dd", {}, [value instanceof Node ? value : String(value)])
    ]);
}

function sectionTitle(texto) {
    return el("h2", { class: "card-title-row", text: texto });
}
