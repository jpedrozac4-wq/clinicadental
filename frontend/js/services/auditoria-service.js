/* ============================================================================
   SERVICIO DE AUDITORÍA (S19)
   ----------------------------------------------------------------------------
   Único punto de acceso a la bitácora de auditoría. Igual que el resto de los
   servicios, el módulo nunca arma la URL a mano: sale de CONFIG.ENDPOINTS.
   AUDITORIA y se compone con apiQuery().

   Contrato real del backend (AuditoriaController + página de Spring Data):
   - GET /api/auditoria?desde=&hasta=&accion=&usuario=&pagina=
             -> { contenido: [...], pagina, tamanio, totalElementos, totalPaginas }
             Bitácora paginada del más reciente al más antiguo. Todos los
             filtros son opcionales. Tamaño de página por defecto 25 (máximo
             100). Una `accion` desconocida devuelve 400 con un mensaje; una
             `pagina` fuera de rango devuelve `contenido` vacío pero con los
             totales reales (así la vista puede volver atrás).

   Payload de cada fila (camelCase):
     { idLog, fecha, accion, descripcionAccion, tabla, registro, usuario, detalle }
     - `accion` es el código del catálogo (PACIENTE_CREAR, PAGO_REGISTRAR, …).
     - `usuario` es el username del actor; puede ser null si la operación la
       ejecutó un proceso programado sin sesión (S19).
     - `detalle` es el JSONB de `audit_log.data` serializado como texto.

   La normalización de abajo es defensiva: si el backend devolviera el cuerpo
   incompleto (o null), la vista recibe siempre la misma forma y no rompe.
   ============================================================================ */
import { CONFIG, apiQuery } from "../config.js";
import { fetchJson } from "./api.js";

const BASE = CONFIG.ENDPOINTS.AUDITORIA;

/** Tamaño de página que aplica el backend cuando no se envía nada. */
const TAMANIO_POR_DEFECTO = 25;

export class AuditoriaService {

    /**
     * Página de la bitácora de auditoría, del más reciente al más antiguo.
     * @param {object} [filtros]
     * @param {string|null} [filtros.desde] Fecha inicial YYYY-MM-DD (inclusive).
     * @param {string|null} [filtros.hasta] Fecha final YYYY-MM-DD (inclusive).
     * @param {string|null} [filtros.accion] Código del catálogo de acciones.
     * @param {string|null} [filtros.usuario] Username del actor.
     * @param {number} [filtros.pagina] Índice de página, base 0.
     * @returns {Promise<{contenido: Array<object>, pagina: number, tamanio: number,
     *                    totalElementos: number, totalPaginas: number}>}
     */
    static async listar({ desde = null, hasta = null, accion = null, usuario = null, pagina = 0 } = {}) {
        const data = await fetchJson(apiQuery(BASE, { desde, hasta, accion, usuario, pagina }));
        return normalizarPagina(data);
    }
}

/** Convierte un valor del backend en número, o devuelve el respaldo. */
function numero(value, fallback) {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : fallback;
}

/** Forma canónica de una página de auditoría, sin importar lo que llegue. */
function normalizarPagina(data) {
    const contenido = Array.isArray(data?.contenido) ? data.contenido : [];
    const tamanio = numero(data?.tamanio, TAMANIO_POR_DEFECTO) || TAMANIO_POR_DEFECTO;
    const totalElementos = numero(data?.totalElementos, contenido.length);
    const totalPaginas = numero(
        data?.totalPaginas,
        totalElementos > 0 ? Math.ceil(totalElementos / tamanio) : 0
    );

    return {
        contenido,
        pagina: numero(data?.pagina, 0),
        tamanio,
        totalElementos,
        totalPaginas
    };
}
