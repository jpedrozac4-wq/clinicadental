/* ============================================================================
   VISTA: HISTORIAL CLINICO DE UN PACIENTE (S3)
   ----------------------------------------------------------------------------
   Consume GET /api/pacientes/{id}/historial, que devuelve las citas y los
   tratamientos realizados del paciente ordenados del mas reciente al mas
   antiguo. La ficha (GET /api/pacientes/{id}) se pide en paralelo para mostrar
   los datos personales; si el paciente fue dado de baja la ficha responde 404
   pero el historial sigue disponible, y la vista lo informa sin romperse.
   ============================================================================ */
import { PacienteService } from "../services/paciente-service.js";
import { ApiError } from "../services/api.js";
import {
    el, pageHeader, loadingState, errorState, table, money, dateTime, dateOnly,
    edad, estadoBadge, detailRow
} from "../core/dom.js";

export class PacienteHistorialView {

    constructor({ route, params, navigate } = {}) {
        this.route = route;
        this.params = params;
        this.navigate = navigate;
        this.idPaciente = params?.id || null;
    }

    async mount(container) {
        this.container = container;
        const idPaciente = this.idPaciente;
        const destino = el("div", { id: "historial-contenido" }, [
            loadingState("Cargando historial clínico…")
        ]);

        container.replaceChildren(
            this.header(null),
            destino
        );

        if (!idPaciente) {
            destino.replaceChildren(errorState("La ruta no incluye un paciente válido."));
            return null;
        }

        const [fichaRes, historialRes] = await Promise.allSettled([
            PacienteService.obtener(idPaciente),
            PacienteService.historial(idPaciente)
        ]);

        if (historialRes.status === "rejected") {
            const error = historialRes.reason;
            destino.replaceChildren(errorState(
                error instanceof ApiError && error.status === 404
                    ? "El paciente no existe o fue eliminado del sistema."
                    : error.message
            ));
            return null;
        }

        const historial = historialRes.value || {};
        const ficha = fichaRes.status === "fulfilled" ? fichaRes.value : null;
        const baja = !ficha && fichaRes.status === "rejected"
            && fichaRes.reason instanceof ApiError && fichaRes.reason.status === 404;

        this.container.replaceChildren(
            this.header(historial.paciente || nombreDesdeFicha(ficha), baja),
            destino
        );

        destino.replaceChildren(
            this.fichaCard(ficha, historial, baja),
            this.tratamientosCard(historial.tratamientos || []),
            this.citasCard(historial.citas || [])
        );

        return null;
    }

    header(nombrePaciente, baja = false) {
        const titulo = nombrePaciente ? `Historial de ${nombrePaciente}` : "Historial clínico";
        return pageHeader(
            titulo,
            "Citas y tratamientos realizados, del más reciente al más antiguo.",
            [
                baja ? el("span", { class: "badge badge-danger", text: "Paciente dado de baja" }) : null,
                el("a", { class: "btn btn-outline", href: "#/pacientes", text: "← Volver a pacientes" })
            ].filter(Boolean)
        );
    }

    fichaCard(ficha, historial, baja) {
        if (!ficha) {
            return el("div", { class: "card" }, [
                el("div", { class: "card-header" }, [el("h2", { text: "Ficha del paciente" })]),
                el("p", {
                    class: "alert-box warning",
                    text: baja
                        ? `El paciente ${historial.paciente || ""} fue dado de baja: no admite citas nuevas, pero su historial clínico se conserva completo.`
                        : "No se pudieron recuperar los datos personales del paciente."
                })
            ]);
        }

        return el("div", { class: "card" }, [
            el("div", { class: "card-header" }, [
                el("h2", { text: "Ficha del paciente" }),
                el("span", { class: "badge badge-primary", text: `Id ${ficha.id}` })
            ]),
            el("dl", { class: "detail-list detail-list-grid" }, [
                detailRow("Nombres", ficha.nombres),
                detailRow("Apellidos", ficha.apellidos),
                detailRow("Fecha de nacimiento", `${dateOnly(ficha.fechaNacimiento)} (${edad(ficha.fechaNacimiento)})`),
                detailRow("Teléfono", ficha.telefono),
                detailRow("Correo electrónico", ficha.email),
                detailRow("Dirección", ficha.direccion)
            ])
        ]);
    }

    tratamientosCard(tratamientos) {
        const total = tratamientos.reduce((suma, item) => suma + (Number(item.costo) || 0), 0);
        const rows = tratamientos.map(tratamiento => [
            dateTime(tratamiento.fechaRealizacion),
            el("span", { class: "cell-strong", text: tratamiento.tratamiento || "—" }),
            tratamiento.detalleProcedimiento || "—",
            money(tratamiento.costo)
        ]);

        return el("div", { class: "card" }, [
            el("div", { class: "card-header" }, [
                el("h2", { text: "Tratamientos realizados" }),
                el("div", { class: "card-header-actions" }, [
                    el("span", { class: "badge badge-primary", text: `${tratamientos.length} registro(s)` }),
                    tratamientos.length
                        ? el("span", { class: "badge badge-success", text: `Costo acumulado ${money(total)}` })
                        : null
                ].filter(Boolean))
            ]),
            table(
                ["Fecha de realización", "Tratamiento", "Detalle del procedimiento", "Costo"],
                rows,
                { emptyMessage: "El paciente todavía no tiene tratamientos registrados." }
            )
        ]);
    }

    citasCard(citas) {
        const rows = citas.map(cita => [
            dateTime(cita.fechaHoraInicio),
            el("span", { class: "cell-strong", text: cita.tratamiento || "—" }),
            estadoBadge(cita.estado),
            cita.motivo || "—"
        ]);

        return el("div", { class: "card" }, [
            el("div", { class: "card-header" }, [
                el("h2", { text: "Citas del paciente" }),
                el("span", { class: "badge badge-primary", text: `${citas.length} registro(s)` })
            ]),
            table(
                ["Fecha y hora", "Tratamiento", "Estado", "Motivo"],
                rows,
                { emptyMessage: "El paciente todavía no tiene citas registradas." }
            )
        ]);
    }
}

function nombreDesdeFicha(ficha) {
    if (!ficha) return "";
    return `${ficha.nombres || ""} ${ficha.apellidos || ""}`.trim();
}
