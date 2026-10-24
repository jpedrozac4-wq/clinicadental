/* ============================================================================
   SERVICIO DE TRATAMIENTOS REALIZADOS (S3, S8)
   ----------------------------------------------------------------------------
   Contrato real del backend (TratamientoRealizadoController + realizado/dto):
   - GET  /api/tratamientos-realizados?idPaciente= -> RealizadoResponse[]
             Sin idPaciente devuelve todos, del mas reciente al mas antiguo.
   - POST /api/tratamientos-realizados              -> 201 RealizadoResponse
             (404 paciente/tratamiento/dentista inexistente, 409 si la cita
             indicada no pertenece al paciente, 422 fecha de realizacion futura)

   Payload camelCase: idPaciente, idTratamiento, idDentista, fechaRealizacion
   (OffsetDateTime ISO), detalleProcedimiento (obligatorio), observaciones e
   idCita opcionales.

   El costo NUNCA se envia: el backend lo toma del precio vigente del catalogo
   y lo congela. Si llega idCita, esa cita pasa a ATENDIDA (S8).
   ============================================================================ */
import { CONFIG, apiQuery } from "../config.js";
import { fetchJson } from "./api.js";

const BASE = CONFIG.ENDPOINTS.TRATAMIENTOS_REALIZADOS;

export class RealizadoService {

    static async listar({ idPaciente = null } = {}) {
        const data = await fetchJson(apiQuery(BASE, { idPaciente }));
        return Array.isArray(data) ? data : [];
    }

    /** Registra un procedimiento; el backend congela el costo y atiende la cita. */
    static async registrar(realizado) {
        return fetchJson(BASE, { method: "POST", body: toPayload(realizado) });
    }
}

function toPayload(realizado) {
    return {
        idPaciente: Number(realizado.idPaciente),
        idTratamiento: Number(realizado.idTratamiento),
        idDentista: Number(realizado.idDentista),
        fechaRealizacion: text(realizado.fechaRealizacion),
        detalleProcedimiento: text(realizado.detalleProcedimiento),
        observaciones: text(realizado.observaciones) || null,
        idCita: realizado.idCita ? Number(realizado.idCita) : null
    };
}

function text(value) {
    return String(value ?? "").trim();
}
