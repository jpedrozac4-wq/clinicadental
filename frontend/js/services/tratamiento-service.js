/* ============================================================================
   SERVICIO DEL CATALOGO DE TRATAMIENTOS (S7)
   ----------------------------------------------------------------------------
   Contrato del backend:
   - GET  /api/tratamientos      -> TratamientoResponse[] (solo activos)
   - POST /api/tratamientos      -> 201 TratamientoResponse (409 si el codigo existe)
   - PUT  /api/tratamientos/{id} -> TratamientoResponse (409 si el codigo existe)

   Payload camelCase: codigo, nombre, descripcion, precio, duracionMinutos.
   El backend normaliza el codigo a mayusculas y valida precio > 0.
   ============================================================================ */
import { CONFIG, apiUrl } from "../config.js";
import { fetchJson } from "./api.js";

const BASE = CONFIG.ENDPOINTS.TRATAMIENTOS;

export class TratamientoService {

    static async listar() {
        const data = await fetchJson(BASE);
        return Array.isArray(data) ? data : [];
    }

    static async crear(tratamiento) {
        return fetchJson(BASE, { method: "POST", body: toPayload(tratamiento) });
    }

    static async actualizar(id, tratamiento) {
        return fetchJson(apiUrl(BASE, id), { method: "PUT", body: toPayload(tratamiento) });
    }
}

function toPayload(tratamiento) {
    return {
        codigo: String(tratamiento.codigo ?? "").trim(),
        nombre: String(tratamiento.nombre ?? "").trim(),
        descripcion: String(tratamiento.descripcion ?? "").trim(),
        precio: Number(tratamiento.precio),
        duracionMinutos: Number(tratamiento.duracionMinutos)
    };
}
