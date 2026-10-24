/* ============================================================================
   SERVICIO DE RECORDATORIOS DE CITAS (S5, S15)
   ----------------------------------------------------------------------------
   Unico punto de acceso a la bitacora de recordatorios. Los modulos nunca arman
   la URL a mano: el listado sale de CONFIG.ENDPOINTS.RECORDATORIOS y el despacho
   del helper recordatoriosProcesarUrl(), igual que el resto de los servicios.

   Contrato real del backend (RecordatorioController + dto/RecordatorioResponse):
   - GET  /api/recordatorios?estado=&idCita= -> RecordatorioResponse[]
             Log completo, del mas reciente al mas antiguo. Ambos filtros son
             opcionales; el backend los aplica sobre la fila.
   - POST /api/recordatorios/procesar       -> RecordatorioResponse[]
             Despacha los pendientes ya vencidos y devuelve SOLO las filas de
             ese paso, ya con su estado final (ENVIADA, SIMULADA o FALLIDA).
             Sin SMTP configurado el backend registra SIMULADA con el motivo en
             `error` en vez de fallar (S15).

   Payload de cada fila (camelCase):
     { id, idCita, fechaHoraCita, canal, estado, destino, fechaObjetivo,
       mensaje, enviadoEn, error }

   El historial por cita que pide S5 se cubre con el mismo listado: la agenda
   enlaza a #/recordatorios?idCita=<id> y el modulo pasa ese id como filtro.
   ============================================================================ */
import { CONFIG, apiQuery, recordatoriosProcesarUrl } from "../config.js";
import { fetchJson } from "./api.js";

const BASE = CONFIG.ENDPOINTS.RECORDATORIOS;

export class RecordatorioService {

    /**
     * Bitacora de recordatorios, del mas reciente al mas antiguo.
     * @param {{estado?: string, idCita?: number|string}} [filtros] Opcionales.
     */
    static async listar({ estado = null, idCita = null } = {}) {
        const data = await fetchJson(apiQuery(BASE, { estado, idCita }));
        return Array.isArray(data) ? data : [];
    }

    /** Fuerza el despacho de los vencidos y devuelve las filas procesadas. */
    static async procesar() {
        const data = await fetchJson(recordatoriosProcesarUrl(), { method: "POST" });
        return Array.isArray(data) ? data : [];
    }
}
