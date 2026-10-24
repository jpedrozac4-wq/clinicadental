/* ============================================================================
   VISTA: REPORTES Y ESTADISTICAS (S21, S22)
   ----------------------------------------------------------------------------
   Pantalla unica de solo lectura con un rango de fechas que alimenta los tres
   reportes de S21:

   - Ingresos  -> total cobrado, por metodo de pago y por mes.
   - Produccion-> tratamientos realizados por odontologo, con cantidad e importe.
   - Ocupacion -> citas por estado y por odontologo.

   Un solo control de rango (desde / hasta, por defecto el mes en curso) y un
   boton de actualizacion disparan las tres consultas en paralelo: si un reporte
   falla, los otros dos se muestran igual (Promise.allSettled) y el bloque que
   fallo pinta su estado de error con el mensaje del backend.

   Reglas de presentacion:
   - Los montos se formatean con CONFIG.CURRENCY y CONFIG.LOCALE mediante
     money(); nunca se imprime un flotante crudo.
   - Los meses "YYYY-MM" del backend se muestran como "mes/aaaa" en espanol.
   - Cada bloque distingue tres estados: vacio (state-box), error (state-box-error)
     y con datos (tabla). El vacio dice "Sin movimientos en el periodo"; el error
     lleva el mensaje del backend.
   - Un rango con desde > hasta NO dispara la peticion: se valida en el cliente y
     se muestra el aviso localmente (el backend tambien responde 400).

   Permiso: la ruta /reportes exige OP_REPORTE_VER; el sidebar y el router ya
   filtran por permiso, esta vista no repite la guarda.
   ============================================================================ */
import { ReporteService } from "../services/reporte-service.js";
import {
    el, pageHeader, loadingState, emptyState, errorState, kpiCard, table,
    money, dateOnly, humanize, estadoBadge
} from "../core/dom.js";

/** Columnas de cada tabla, en el orden en que se pintan. */
const COLUMNAS_INGRESO_METODO = ["Método de pago", "Cantidad", "Total cobrado"];
const COLUMNAS_INGRESO_MES = ["Mes", "Total cobrado"];
const COLUMNAS_PRODUCCION = ["Odontólogo", "Procedimientos", "Importe"];
const COLUMNAS_OCUPACION_ESTADO = ["Estado de la cita", "Cantidad"];
const COLUMNAS_OCUPACION_ODONTOLOGO = ["Odontólogo", "Citas"];

/** Nombres de mes en espanol para el formato mes/aaaa. */
const MESES = [
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"
];

const MENSAJE_VACIO = "Sin movimientos en el período";

export class ReportesView {

    constructor({ route, params, navigate } = {}) {
        this.route = route;
        this.params = params;
        this.navigate = navigate;

        const mesEnCurso = rangoMesEnCurso();
        this.rango = { desde: mesEnCurso.desde, hasta: mesEnCurso.hasta };
        this.ingresos = null;
        this.produccion = null;
        this.ocupacion = null;
        this.cargando = false;
        this.solicitudId = 0;
    }

    async mount(container) {
        this.container = container;
        container.replaceChildren(
            this.header(),
            this.cardRango(),
            this.alertaValidacionBox(),
            this.kpisContainer(),
            this.bloqueIngresos(),
            this.bloqueProduccion(),
            this.bloqueOcupacion()
        );

        this.actualizarEtiquetaPeriodo();
        await this.refrescar();
        return null;
    }

    unmount() {
        this.solicitudId += 1;
        this.ingresos = null;
        this.produccion = null;
        this.ocupacion = null;
        this.cargando = false;
    }

    /* ------------------------------------------------------------------ *
     * Estructura
     * ------------------------------------------------------------------ */

    header() {
        return pageHeader(
            "Reportes",
            "Ingresos cobrados, producción por odontólogo y ocupación de la agenda en el período seleccionado. Solo lectura.",
            []
        );
    }

    cardRango() {
        this.inputDesde = el("input", {
            type: "date",
            id: "reporte-desde",
            class: "form-control",
            value: this.rango.desde,
            on: { change: () => this.actualizarEtiquetaPeriodo() }
        });
        this.inputHasta = el("input", {
            type: "date",
            id: "reporte-hasta",
            class: "form-control",
            value: this.rango.hasta,
            on: { change: () => this.actualizarEtiquetaPeriodo() }
        });
        this.botonActualizar = el("button", {
            type: "button",
            class: "btn btn-primary",
            id: "btn-actualizar-reportes",
            text: "Actualizar reportes",
            on: { click: () => this.refrescar() }
        });
        this.etiquetaPeriodo = el("p", {
            class: "text-muted text-sm mb-0",
            id: "reporte-periodo"
        });

        return el("div", { class: "card", id: "reportes-rango" }, [
            el("div", { class: "toolbar" }, [
                el("div", { class: "form-group toolbar-grow mb-0" }, [
                    el("label", { for: "reporte-desde", text: "Desde" }),
                    this.inputDesde
                ]),
                el("div", { class: "form-group toolbar-grow mb-0" }, [
                    el("label", { for: "reporte-hasta", text: "Hasta" }),
                    this.inputHasta
                ]),
                this.botonActualizar,
                el("button", {
                    type: "button",
                    class: "btn btn-outline",
                    id: "btn-reporte-mes-actual",
                    text: "Mes actual",
                    on: { click: () => this.aplicarMesEnCurso() }
                })
            ]),
            this.etiquetaPeriodo
        ]);
    }

    alertaValidacionBox() {
        this.alertaValidacion = el("div", {
            class: "alert-box danger",
            id: "reportes-validacion",
            hidden: true
        });
        return this.alertaValidacion;
    }

    kpisContainer() {
        this.kpis = el("div", { class: "kpi-container", id: "reportes-kpis" }, this.cardsKpi());
        return this.kpis;
    }

    cardsKpi() {
        return [
            this.kpi("kpi-total-cobrado", "Total cobrado", "—", "Pagos registrados en el período"),
            this.kpi("kpi-total-procedimientos", "Procedimientos realizados", "—", "Tratamientos hechos en el período"),
            this.kpi("kpi-total-importe", "Importe producido", "—", "Costo de los procedimientos del período"),
            this.kpi("kpi-total-citas", "Citas en el período", "—", "Citas agendadas en el rango")
        ];
    }

    /** KPI card con id propio para que el smoke pueda apuntarle. */
    kpi(id, label, value, sub) {
        const card = kpiCard(label, value, sub);
        card.id = id;
        return card;
    }

    bloqueIngresos() {
        this.contadorIngresos = el("span", {
            class: "badge badge-primary",
            id: "contador-ingresos",
            text: "Consultando…"
        });
        this.tablaIngresosMetodo = el("div", { id: "tabla-ingresos-metodo" }, [
            loadingState("Consultando ingresos…")
        ]);
        this.tablaIngresosMes = el("div", { id: "tabla-ingresos-mes" }, [
            loadingState("Consultando ingresos…")
        ]);

        return el("div", { class: "card", id: "bloque-ingresos" }, [
            el("div", { class: "card-header" }, [
                el("h2", { text: "Ingresos cobrados" }),
                el("div", { class: "card-header-actions" }, [this.contadorIngresos])
            ]),
            el("div", { class: "card-title-row" }, [
                el("h3", { text: "Por método de pago" })
            ]),
            this.tablaIngresosMetodo,
            el("div", { class: "card-title-row mt-2" }, [
                el("h3", { text: "Evolución mensual" })
            ]),
            this.tablaIngresosMes
        ]);
    }

    bloqueProduccion() {
        this.contadorProduccion = el("span", {
            class: "badge badge-primary",
            id: "contador-produccion",
            text: "Consultando…"
        });
        this.tablaProduccion = el("div", { id: "tabla-produccion-odontologo" }, [
            loadingState("Consultando producción…")
        ]);

        return el("div", { class: "card", id: "bloque-produccion" }, [
            el("div", { class: "card-header" }, [
                el("h2", { text: "Producción por odontólogo" }),
                el("div", { class: "card-header-actions" }, [this.contadorProduccion])
            ]),
            this.tablaProduccion
        ]);
    }

    bloqueOcupacion() {
        this.contadorOcupacion = el("span", {
            class: "badge badge-primary",
            id: "contador-ocupacion",
            text: "Consultando…"
        });
        this.tablaOcupacionEstado = el("div", { id: "tabla-ocupacion-estado" }, [
            loadingState("Consultando ocupación…")
        ]);
        this.tablaOcupacionOdontologo = el("div", { id: "tabla-ocupacion-odontologo" }, [
            loadingState("Consultando ocupación…")
        ]);

        return el("div", { class: "card", id: "bloque-ocupacion" }, [
            el("div", { class: "card-header" }, [
                el("h2", { text: "Ocupación de la agenda" }),
                el("div", { class: "card-header-actions" }, [this.contadorOcupacion])
            ]),
            el("div", { class: "card-title-row" }, [
                el("h3", { text: "Citas por estado" })
            ]),
            this.tablaOcupacionEstado,
            el("div", { class: "card-title-row mt-2" }, [
                el("h3", { text: "Citas por odontólogo" })
            ]),
            this.tablaOcupacionOdontologo
        ]);
    }

    /* ------------------------------------------------------------------ *
     * Rango de fechas
     * ------------------------------------------------------------------ */

    /** Lee los inputs y aplica la unica regla local de S21: desde <= hasta. */
    leerRango() {
        const desde = (this.inputDesde.value || "").trim();
        const hasta = (this.inputHasta.value || "").trim();
        if (desde && hasta && desde > hasta) {
            return {
                error: "La fecha «desde» no puede ser posterior a la fecha «hasta». Corregí el rango para consultar los reportes."
            };
        }
        return { desde: desde || null, hasta: hasta || null };
    }

    actualizarEtiquetaPeriodo() {
        if (!this.etiquetaPeriodo) return;
        const { desde, hasta, error } = this.leerRango();
        const inicio = desde ? dateOnly(desde) : "inicio del mes en curso";
        const fin = hasta ? dateOnly(hasta) : "fin del mes en curso";
        this.etiquetaPeriodo.textContent = error
            ? "Período no aplicado: revisá las fechas."
            : `Período consultado: ${inicio} – ${fin}.`;
    }

    aplicarMesEnCurso() {
        const mesEnCurso = rangoMesEnCurso();
        this.inputDesde.value = mesEnCurso.desde;
        this.inputHasta.value = mesEnCurso.hasta;
        this.actualizarEtiquetaPeriodo();
        this.refrescar();
    }

    mostrarValidacion(mensaje) {
        this.alertaValidacion.textContent = mensaje;
        this.alertaValidacion.hidden = false;
        this.actualizarEtiquetaPeriodo();
    }

    ocultarValidacion() {
        this.alertaValidacion.hidden = true;
        this.alertaValidacion.textContent = "";
    }

    /* ------------------------------------------------------------------ *
     * Carga
     * ------------------------------------------------------------------ */

    /**
     * Consulta los tres reportes del rango vigente. Un rango invalido corta acá:
     * no se dispara ninguna peticion y el aviso queda en pantalla.
     */
    async refrescar() {
        if (this.cargando) return;

        const lectura = this.leerRango();
        if (lectura.error) {
            this.mostrarValidacion(lectura.error);
            return;
        }

        this.ocultarValidacion();
        this.rango = { desde: lectura.desde, hasta: lectura.hasta };
        this.actualizarEtiquetaPeriodo();

        const solicitud = ++this.solicitudId;
        this.cargando = true;
        this.marcarCargando();
        if (this.botonActualizar) {
            this.botonActualizar.disabled = true;
            this.botonActualizar.textContent = "Consultando…";
        }

        try {
            const [ingresos, produccion, ocupacion] = await Promise.allSettled([
                ReporteService.ingresos(this.rango),
                ReporteService.produccion(this.rango),
                ReporteService.ocupacion(this.rango)
            ]);

            // Una respuesta vieja no pisa el rango que el usuario eligio despues.
            if (solicitud !== this.solicitudId) return;

            this.ingresos = ingresos;
            this.produccion = produccion;
            this.ocupacion = ocupacion;

            this.renderKpis();
            this.renderIngresos();
            this.renderProduccion();
            this.renderOcupacion();
        } finally {
            if (solicitud === this.solicitudId) {
                this.cargando = false;
                if (this.botonActualizar) {
                    this.botonActualizar.disabled = false;
                    this.botonActualizar.textContent = "Actualizar reportes";
                }
            }
        }
    }

    marcarCargando() {
        this.kpis.replaceChildren(...this.cardsKpi());
        this.contadorIngresos.textContent = "Consultando…";
        this.contadorProduccion.textContent = "Consultando…";
        this.contadorOcupacion.textContent = "Consultando…";
        this.tablaIngresosMetodo.replaceChildren(loadingState("Consultando ingresos…"));
        this.tablaIngresosMes.replaceChildren(loadingState("Consultando ingresos…"));
        this.tablaProduccion.replaceChildren(loadingState("Consultando producción…"));
        this.tablaOcupacionEstado.replaceChildren(loadingState("Consultando ocupación…"));
        this.tablaOcupacionOdontologo.replaceChildren(loadingState("Consultando ocupación…"));
    }

    /** Normaliza el resultado de Promise.allSettled a { ok, data } o { ok, error }. */
    resultado(settled) {
        if (settled && settled.status === "fulfilled") {
            return { ok: true, data: settled.value };
        }
        return {
            ok: false,
            error: settled?.reason?.message || "No se pudo consultar el reporte."
        };
    }

    /* ------------------------------------------------------------------ *
     * Render
     * ------------------------------------------------------------------ */

    renderKpis() {
        const ingresos = this.resultado(this.ingresos);
        const produccion = this.resultado(this.produccion);
        const ocupacion = this.resultado(this.ocupacion);

        this.kpis.replaceChildren(
            this.kpi(
                "kpi-total-cobrado",
                "Total cobrado",
                ingresos.ok ? money(ingresos.data.totalCobrado) : "—",
                ingresos.ok
                    ? `${ingresos.data.porMetodoPago.length} método(s) de pago con movimientos`
                    : "Sin datos disponibles"
            ),
            this.kpi(
                "kpi-total-procedimientos",
                "Procedimientos realizados",
                produccion.ok ? String(produccion.data.totalProcedimientos) : "—",
                produccion.ok ? "Tratamientos realizados en el período" : "Sin datos disponibles"
            ),
            this.kpi(
                "kpi-total-importe",
                "Importe producido",
                produccion.ok ? money(produccion.data.totalImporte) : "—",
                produccion.ok
                    ? `Sobre ${produccion.data.porOdontologo.length} odontólogo(s)`
                    : "Sin datos disponibles"
            ),
            this.kpi(
                "kpi-total-citas",
                "Citas en el período",
                ocupacion.ok ? String(ocupacion.data.totalCitas) : "—",
                ocupacion.ok
                    ? `${ocupacion.data.porEstado.length} estado(s) de cita`
                    : "Sin datos disponibles"
            )
        );
    }

    renderIngresos() {
        const ingresos = this.resultado(this.ingresos);

        if (!ingresos.ok) {
            this.contadorIngresos.textContent = "Sin datos";
            this.tablaIngresosMetodo.replaceChildren(errorState(ingresos.error));
            this.tablaIngresosMes.replaceChildren(errorState(ingresos.error));
            return;
        }

        const { totalCobrado, porMetodoPago, porMes } = ingresos.data;
        this.contadorIngresos.textContent = porMetodoPago.length
            ? `${porMetodoPago.length} método(s) · ${money(totalCobrado)}`
            : MENSAJE_VACIO;

        this.tablaIngresosMetodo.replaceChildren(porMetodoPago.length
            ? table(COLUMNAS_INGRESO_METODO, porMetodoPago.map(fila => [
                el("span", { class: "cell-strong", text: humanize(fila.metodo) }),
                String(fila.cantidad),
                money(fila.total)
            ]))
            : emptyState(
                MENSAJE_VACIO,
                "No hay pagos registrados en el rango de fechas elegido."
            ));

        this.tablaIngresosMes.replaceChildren(porMes.length
            ? table(COLUMNAS_INGRESO_MES, porMes.map(fila => [
                el("span", { class: "cell-strong", text: mesLegible(fila.mes) }),
                money(fila.total)
            ]))
            : emptyState(
                MENSAJE_VACIO,
                "Todavía no hay cobros que se puedan agrupar por mes."
            ));
    }

    renderProduccion() {
        const produccion = this.resultado(this.produccion);

        if (!produccion.ok) {
            this.contadorProduccion.textContent = "Sin datos";
            this.tablaProduccion.replaceChildren(errorState(produccion.error));
            return;
        }

        const { porOdontologo, totalProcedimientos, totalImporte } = produccion.data;
        this.contadorProduccion.textContent = porOdontologo.length
            ? `${totalProcedimientos} procedimiento(s) · ${money(totalImporte)}`
            : MENSAJE_VACIO;

        this.tablaProduccion.replaceChildren(porOdontologo.length
            ? table(COLUMNAS_PRODUCCION, porOdontologo.map(fila => [
                el("span", { class: "cell-strong", text: fila.odontologo || "—" }),
                String(fila.cantidad),
                money(fila.importe)
            ]))
            : emptyState(
                "Sin procedimientos en el período",
                "No hay tratamientos realizados por odontólogo en el rango elegido."
            ));
    }

    renderOcupacion() {
        const ocupacion = this.resultado(this.ocupacion);

        if (!ocupacion.ok) {
            this.contadorOcupacion.textContent = "Sin datos";
            this.tablaOcupacionEstado.replaceChildren(errorState(ocupacion.error));
            this.tablaOcupacionOdontologo.replaceChildren(errorState(ocupacion.error));
            return;
        }

        const { porEstado, porOdontologo, totalCitas } = ocupacion.data;
        this.contadorOcupacion.textContent = totalCitas
            ? `${totalCitas} cita(s)`
            : "Sin citas en el período";

        this.tablaOcupacionEstado.replaceChildren(porEstado.length
            ? table(COLUMNAS_OCUPACION_ESTADO, porEstado.map(fila => [
                estadoBadge(fila.estado),
                String(fila.cantidad)
            ]))
            : emptyState(
                "Sin citas en el período",
                "No hay citas agendadas en el rango de fechas elegido."
            ));

        this.tablaOcupacionOdontologo.replaceChildren(porOdontologo.length
            ? table(COLUMNAS_OCUPACION_ODONTOLOGO, porOdontologo.map(fila => [
                el("span", { class: "cell-strong", text: fila.odontologo || "—" }),
                String(fila.cantidad)
            ]))
            : emptyState(
                "Sin citas en el período",
                "Ningún odontólogo tiene citas agendadas en el rango elegido."
            ));
    }
}

/* ----------------------------------------------------------------------------
   Helpers de fecha del reporte
   ---------------------------------------------------------------------------- */

/** Primer y último día del mes en curso, en ISO corto (YYYY-MM-DD). */
function rangoMesEnCurso() {
    const hoy = new Date();
    const primero = new Date(hoy.getFullYear(), hoy.getMonth(), 1);
    const ultimo = new Date(hoy.getFullYear(), hoy.getMonth() + 1, 0);
    return { desde: isoLocal(primero), hasta: isoLocal(ultimo) };
}

/** Fecha a YYYY-MM-DD en horario local (toISOString correria el día). */
function isoLocal(date) {
    const año = date.getFullYear();
    const mes = String(date.getMonth() + 1).padStart(2, "0");
    const dia = String(date.getDate()).padStart(2, "0");
    return `${año}-${mes}-${dia}`;
}

/**
 * "YYYY-MM" -> "mes/aaaa" en español ("2025-10" -> "octubre/2025").
 * Un valor que no cumpla el patrón se muestra tal cual para no ocultar el dato.
 */
export function mesLegible(mes) {
    const match = /^(\d{4})-(\d{2})$/.exec(String(mes || "").trim());
    if (!match) return mes ? String(mes) : "—";
    const indice = Number(match[2]) - 1;
    const nombre = MESES[indice];
    return nombre ? `${nombre}/${match[1]}` : String(mes);
}
