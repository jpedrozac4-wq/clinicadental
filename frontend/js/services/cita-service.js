/* ============================================================================
   SERVICIO DE AGENDA (S4, S6)
   ----------------------------------------------------------------------------
   Unico punto de acceso al recurso /api/citas. Los modulos nunca arman la URL
   a mano y siempre reciben el error normalizado del cliente HTTP, para que los
   mensajes de negocio del backend (409 de solapamiento, 422 de fecha pasada)
   lleguen intactos a la interfaz.

   Contrato real del backend (CitaController + cita/dto):
   - GET   /api/citas?desde=ISO&hasta=ISO -> CitaResponse[]
             Rango por fecha_hora_inicio. Sin parametros el backend usa desde
             hoy hasta hoy + 30 dias. NO acepta filtro por estado ni por
             paciente: ese filtro se resuelve en el cliente.
   - GET   /api/citas/{id}                -> CitaResponse (404 si no existe)
   - POST  /api/citas                     -> 201 CitaResponse (409 solapamiento,
                                             422 fecha pasada, 404 paciente o
                                             tratamiento inexistente/baja)
   - PATCH /api/citas/{id}/reprogramar    -> CitaResponse (409 cita ATENDIDA o
                                             solapamiento, 422 fecha pasada)
   - PATCH /api/citas/{id}/cancelar       -> 204 sin cuerpo (409 si ya esta
                                             ATENDIDA, idempotente si ya esta
                                             CANCELADA)

   Payload camelCase: idPaciente, idTratamiento, idDentista, fechaHoraInicio
   (OffsetDateTime ISO), motivo opcional. La reprogramacion recibe
   fechaHoraInicio e idDentista opcional.
   ============================================================================ */
import { CONFIG, apiQuery, apiUrl, citaAccionUrl } from "../config.js";
import { fetchJson } from "./api.js";

const BASE = CONFIG.ENDPOINTS.CITAS;

export class CitaService {

    /**
     * Citas en un rango de fechas.
     * @param {{desde?: string, hasta?: string}} [rango] Instantes ISO con offset.
     */
    static async listar({ desde = null, hasta = null } = {}) {
        const data = await fetchJson(apiQuery(BASE, { desde, hasta }));
        return Array.isArray(data) ? data : [];
    }

    static async obtener(id) {
        return fetchJson(apiUrl(BASE, id));
    }

    /** Programa una cita nueva en estado PROGRAMADA. */
    static async crear(cita) {
        return fetchJson(BASE, { method: "POST", body: toPayload(cita) });
    }

    /**
     * Reprograma una cita: el backend deja la original en REPROGRAMADA y crea
     * una nueva PROGRAMADA vinculada a ella (S6).
     */
    static async reprogramar(id, { fechaHoraInicio, idDentista = null }) {
        const body = { fechaHoraInicio: text(fechaHoraInicio) };
        if (idDentista) {
            body.idDentista = Number(idDentista);
        }
        return fetchJson(citaAccionUrl(id, "reprogramar"), { method: "PATCH", body });
    }

    /** Cancela una cita vigente; es idempotente del lado del backend. */
    static async cancelar(id) {
        await fetchJson(citaAccionUrl(id, "cancelar"), { method: "PATCH" });
        return true;
    }
}

function toPayload(cita) {
    return {
        idPaciente: Number(cita.idPaciente),
        idTratamiento: Number(cita.idTratamiento),
        idDentista: Number(cita.idDentista),
        fechaHoraInicio: text(cita.fechaHoraInicio),
        motivo: text(cita.motivo) || null
    };
}

function text(value) {
    return String(value ?? "").trim();
}
