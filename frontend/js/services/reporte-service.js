/* ============================================================================
   SERVICIO DE REPORTES Y ESTADISTICAS (S21)
   ----------------------------------------------------------------------------
   Unico punto de acceso a los tres reportes de solo lectura. Los modulos nunca
   arman la URL a mano: sale del helper reporteUrl(nombre, params), que ya
   conoce CONFIG.ENDPOINTS.REPORTES y descarta los parametros vacios.

   Contrato real del backend (ReporteController + dto/*):
   - GET /api/reportes/ingresos?desde=&hasta=
             { totalCobrado, porMetodoPago: [{ metodo, total, cantidad }],
               porMes: [{ mes: "YYYY-MM", total }] }
   - GET /api/reportes/produccion?desde=&hasta=
             { porOdontologo: [{ idDentista, odontologo, cantidad, importe }],
               totalProcedimientos, totalImporte }
   - GET /api/reportes/ocupacion?desde=&hasta=
             { porEstado: [{ estado, cantidad }],
               porOdontologo: [{ idDentista, odontologo, cantidad }], totalCitas }

   Reglas de rango (iguales en los tres endpoints):
   - `desde` y `hasta` son opcionales y viajan como YYYY-MM-DD; el rango es
     inclusivo en ambos extremos.
   - Si falta un extremo, el backend usa el mes en curso.
   - Un rango con desde > hasta responde 400: la vista lo valida antes de
     consultar y por eso ese caso no llega al servicio.
   - Sin datos responde ceros y listas vacias, nunca null. Igual se normaliza
     aqui: un reporte sin filas se consume sin comprobar null en cada celda.
   ============================================================================ */
import { reporteUrl } from "../config.js";
import { fetchJson } from "./api.js";

/** Lista segura: cualquier cosa que no sea arreglo se trata como vacia. */
function lista(value) {
    return Array.isArray(value) ? value : [];
}

/** Numero seguro para montos y cantidades: evita NaN en pantalla. */
function numero(value) {
    const amount = Number(value);
    return Number.isFinite(amount) ? amount : 0;
}

/** Texto seguro para etiquetas que llegan del backend. */
function texto(value) {
    return value === null || value === undefined ? "" : String(value);
}

export class ReporteService {

    /** Cobrado del rango, desglosado por metodo de pago y por mes (S21). */
    static async ingresos({ desde = null, hasta = null } = {}) {
        const data = await fetchJson(reporteUrl("ingresos", { desde, hasta }));
        return {
            totalCobrado: numero(data?.totalCobrado),
            porMetodoPago: lista(data?.porMetodoPago).map(fila => ({
                metodo: texto(fila?.metodo),
                total: numero(fila?.total),
                cantidad: numero(fila?.cantidad)
            })),
            porMes: lista(data?.porMes).map(fila => ({
                mes: texto(fila?.mes),
                total: numero(fila?.total)
            }))
        };
    }

    /** Tratamientos realizados por odontologo, con cantidad e importe (S21). */
    static async produccion({ desde = null, hasta = null } = {}) {
        const data = await fetchJson(reporteUrl("produccion", { desde, hasta }));
        return {
            porOdontologo: lista(data?.porOdontologo).map(fila => ({
                idDentista: fila?.idDentista ?? null,
                odontologo: texto(fila?.odontologo),
                cantidad: numero(fila?.cantidad),
                importe: numero(fila?.importe)
            })),
            totalProcedimientos: numero(data?.totalProcedimientos),
            totalImporte: numero(data?.totalImporte)
        };
    }

    /** Citas del rango por estado y por odontologo (S21). */
    static async ocupacion({ desde = null, hasta = null } = {}) {
        const data = await fetchJson(reporteUrl("ocupacion", { desde, hasta }));
        return {
            porEstado: lista(data?.porEstado).map(fila => ({
                estado: texto(fila?.estado),
                cantidad: numero(fila?.cantidad)
            })),
            porOdontologo: lista(data?.porOdontologo).map(fila => ({
                idDentista: fila?.idDentista ?? null,
                odontologo: texto(fila?.odontologo),
                cantidad: numero(fila?.cantidad)
            })),
            totalCitas: numero(data?.totalCitas)
        };
    }
}
