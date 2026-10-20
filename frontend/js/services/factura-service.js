/* ============================================================================
   SERVICIO DE FACTURACION Y PAGOS (S9, S10)
   ----------------------------------------------------------------------------
   Contrato real del backend (FacturaController + PagoController y sus dto):
   - GET  /api/facturas                          -> FacturaResponse[]
             Ya viene con el detalle y los totales derivados (total, saldo,
             totalPagado). No acepta filtros: se filtran en el cliente.
   - GET  /api/facturas/{id}                     -> FacturaResponse
   - POST /api/facturas                          -> 201 FacturaResponse
             (409 id_realizado ya facturado / de otro paciente / repetido,
              404 paciente dado de baja)
   - GET  /api/facturas/{id}/pdf                 -> application/pdf
   - GET  /api/facturas/{id}/pagos               -> PagoResponse[]
   - POST /api/facturas/{id}/pagos               -> 201 PagoResponse
             (409 si la suma de pagos supera el total o la factura esta ANULADA,
              404 metodo de pago inexistente/inactivo)

   Payload camelCase: la emision recibe idPaciente, idsRealizado y observaciones
   opcionales; el pago recibe monto, idMetodoPago y referencia opcional.
   Los montos de la factura NO se envian: salen del costo congelado de cada
   tratamiento realizado.
   ============================================================================ */
import { CONFIG, apiUrl, facturaPagosUrl, facturaPdfUrl } from "../config.js";
import { downloadBlob, fetchBlob, fetchJson } from "./api.js";

const BASE = CONFIG.ENDPOINTS.FACTURAS;

export class FacturaService {

    static async listar() {
        const data = await fetchJson(BASE);
        return Array.isArray(data) ? data : [];
    }

    static async obtener(id) {
        return fetchJson(apiUrl(BASE, id));
    }

    /** Emite un comprobante con los tratamientos realizados indicados (S9). */
    static async emitir({ idPaciente, idsRealizado = [], observaciones = "" }) {
        return fetchJson(BASE, {
            method: "POST",
            body: {
                idPaciente: Number(idPaciente),
                idsRealizado: idsRealizado.map(id => Number(id)),
                observaciones: text(observaciones) || null
            }
        });
    }

    /** Descarga el comprobante PDF usando el helper binario del cliente HTTP. */
    static async descargarPdf(factura) {
        const blob = await fetchBlob(facturaPdfUrl(factura.id));
        downloadBlob(blob, `${factura.numero || `factura-${factura.id}`}.pdf`);
        return true;
    }

    /* ---- Pagos de una factura (S10) ---- */

    static async pagos(idFactura) {
        const data = await fetchJson(facturaPagosUrl(idFactura));
        return Array.isArray(data) ? data : [];
    }

    static async registrarPago(idFactura, { monto, idMetodoPago, referencia = "" }) {
        return fetchJson(facturaPagosUrl(idFactura), {
            method: "POST",
            body: {
                monto: Number(monto),
                idMetodoPago: Number(idMetodoPago),
                referencia: text(referencia) || null
            }
        });
    }
}

/**
 * Catalogo de formas de pago (cat_metodo_pago de la semilla).
 *
 * El backend NO expone un endpoint de metodos de pago: PagoRequest exige
 * `idMetodoPago` y solo lo valida contra la tabla. Mientras ese endpoint no
 * exista, la interfaz ofrece el catalogo de la semilla con sus ids conocidos
 * (1 EFECTIVO, 2 TARJETA, 3 TRANSFERENCIA). Si el backend responde 404 de
 * metodo inexistente, el mensaje se muestra tal cual para que el desajuste sea
 * visible en vez de silencioso.
 */
export const METODOS_PAGO = [
    { id: 1, codigo: "EFECTIVO", label: "Efectivo" },
    { id: 2, codigo: "TARJETA", label: "Tarjeta" },
    { id: 3, codigo: "TRANSFERENCIA", label: "Transferencia" }
];

function text(value) {
    return String(value ?? "").trim();
}
