/* ============================================================================
   SERVICIO DE PACIENTES (S1, S2, S3)
   ----------------------------------------------------------------------------
   Unico punto de acceso al recurso /api/pacientes. Los modulos nunca arman la
   URL a mano y siempre reciben el error normalizado del cliente HTTP.

   Contrato del backend:
   - GET    /api/pacientes?texto=...  -> PacienteResponse[]
   - GET    /api/pacientes/{id}       -> PacienteResponse (404 si no existe o esta de baja)
   - POST   /api/pacientes            -> 201 PacienteResponse
   - PUT    /api/pacientes/{id}       -> PacienteResponse
   - DELETE /api/pacientes/{id}       -> 204 sin cuerpo (baja logica)
   - GET    /api/pacientes/{id}/historial -> HistorialResponse (404 si no existe)

   El payload usa camelCase: nombres, apellidos, fechaNacimiento (ISO yyyy-MM-dd),
   telefono, direccion, email.
   ============================================================================ */
import { CONFIG, apiQuery, apiUrl, pacienteHistorialUrl } from "../config.js";
import { fetchJson } from "./api.js";

const BASE = CONFIG.ENDPOINTS.PACIENTES;

export class PacienteService {

    /** Lista pacientes vigentes; `texto` filtra por nombre, apellido, email o telefono. */
    static async listar(texto = "") {
        const data = await fetchJson(apiQuery(BASE, { texto }));
        return normalizeList(data);
    }

    static async obtener(id) {
        return fetchJson(apiUrl(BASE, id));
    }

    /** Alta de paciente. Devuelve el paciente creado con su id. */
    static async crear(paciente) {
        return fetchJson(BASE, { method: "POST", body: toPayload(paciente) });
    }

    static async actualizar(id, paciente) {
        return fetchJson(apiUrl(BASE, id), { method: "PUT", body: toPayload(paciente) });
    }

    /** Baja logica: deja de aparecer en listados y conserva el historial clinico. */
    static async darDeBaja(id) {
        await fetchJson(apiUrl(BASE, id), { method: "DELETE" });
        return true;
    }

    /** Historial clinico: citas y tratamientos realizados del paciente (S3). */
    static async historial(id) {
        return fetchJson(pacienteHistorialUrl(id));
    }
}

function toPayload(paciente) {
    return {
        nombres: text(paciente.nombres),
        apellidos: text(paciente.apellidos),
        fechaNacimiento: text(paciente.fechaNacimiento),
        telefono: text(paciente.telefono),
        direccion: text(paciente.direccion),
        email: text(paciente.email)
    };
}

function text(value) {
    return String(value ?? "").trim();
}

function normalizeList(data) {
    return Array.isArray(data) ? data : [];
}
