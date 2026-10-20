/* ============================================================================
   SERVICIO DE CARRITO B2C (PERSISTENCIA EN localStorage)
   ----------------------------------------------------------------------------
   Estado del carrito del storefront publico. La identidad de una linea es el
   SKU: no existen filas duplicadas para el mismo codigo. El estado vive en
   localStorage bajo CONFIG.STORAGE_KEYS.CART y sobrevive a un recargado.
   ============================================================================ */
import { CONFIG } from "../config.js";

const STORAGE_KEY = CONFIG.STORAGE_KEYS.CART;
const subscribers = new Set();

/** Redondeo a centavos para que el subtotal coincida con lo que imprime money(). */
function roundMoney(value) {
    const amount = Number(value);
    if (!Number.isFinite(amount)) return 0;
    return Math.round((amount + Number.EPSILON) * 100) / 100;
}

/** Las cantidades del carrito son enteras: se trunca cualquier decimal. */
function toWholeQuantity(value) {
    const amount = Number(value);
    if (!Number.isFinite(amount)) return 0;
    return Math.trunc(amount);
}

/**
 * Normaliza una linea cruda. Devuelve null cuando la fila no es un item
 * vendible (sin SKU o sin cantidad positiva), para poder descartarla.
 */
function sanitizeItem(raw) {
    if (!raw || typeof raw !== "object") return null;

    const sku = typeof raw.sku === "string" ? raw.sku.trim() : "";
    const cantidad = toWholeQuantity(raw.cantidad);
    if (!sku || cantidad <= 0) return null;

    return {
        sku,
        nombre: typeof raw.nombre === "string" && raw.nombre ? raw.nombre : sku,
        tallaEur: raw.tallaEur === undefined ? null : raw.tallaEur,
        precio: roundMoney(raw.precio),
        cantidad,
        fotoUrl: typeof raw.fotoUrl === "string" && raw.fotoUrl ? raw.fotoUrl : null
    };
}

/**
 * Lee el carrito guardado. Un valor corrupto o ilegible NUNCA lanza: se
 * reinicia a carrito vacio y se deja constancia en consola.
 */
function readItems() {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return [];

    let parsed;
    try {
        parsed = JSON.parse(raw);
    } catch {
        console.warn(`[CarritoService] ${STORAGE_KEY} tenia JSON invalido; se reinicio el carrito vacio.`);
        writeItems([]);
        return [];
    }

    if (!Array.isArray(parsed)) {
        console.warn(`[CarritoService] ${STORAGE_KEY} no era una lista; se reinicio el carrito vacio.`);
        writeItems([]);
        return [];
    }

    const items = parsed.map(sanitizeItem).filter(Boolean);
    if (items.length !== parsed.length) {
        console.warn(`[CarritoService] Se descartaron ${parsed.length - items.length} filas invalidas del carrito guardado.`);
        writeItems(items);
    }
    return items;
}

function writeItems(items) {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(items));
}

function notify() {
    const snapshot = CarritoService.getItems();
    subscribers.forEach(listener => {
        try {
            listener(snapshot);
        } catch (error) {
            console.error("[CarritoService] Un suscriptor del carrito fallo:", error);
        }
    });
}

export class CarritoService {

    /** Lineas actuales del carrito, ya normalizadas. */
    static getItems() {
        return readItems();
    }

    /** Cantidad total de pares (suma de cantidades, no de lineas). */
    static getCount() {
        return readItems().reduce((total, item) => total + item.cantidad, 0);
    }

    /** Subtotal redondeado a centavos, listo para money(). */
    static getSubtotal() {
        return roundMoney(readItems().reduce((total, item) => total + item.precio * item.cantidad, 0));
    }

    /**
     * Agrega un SKU. Si ya existe, incrementa su cantidad en lugar de crear
     * una fila duplicada. Devuelve el SKU agregado o null si el item era invalido.
     */
    static add(item) {
        const normalized = sanitizeItem({ ...(item || {}), cantidad: item && item.cantidad !== undefined ? item.cantidad : 1 });
        if (!normalized) return null;

        const items = readItems();
        const existing = items.find(row => row.sku === normalized.sku);
        if (existing) {
            existing.cantidad += normalized.cantidad;
        } else {
            items.push(normalized);
        }
        writeItems(items);
        notify();
        return normalized.sku;
    }

    /** Fija la cantidad de un SKU. Cantidad <= 0 elimina la linea. */
    static setCantidad(sku, cantidad) {
        const items = readItems();
        const index = items.findIndex(row => row.sku === sku);
        if (index === -1) return;

        const next = toWholeQuantity(cantidad);
        if (next <= 0) {
            items.splice(index, 1);
        } else {
            items[index].cantidad = next;
        }
        writeItems(items);
        notify();
    }

    static remove(sku) {
        CarritoService.setCantidad(sku, 0);
    }

    static clear() {
        writeItems([]);
        notify();
    }

    /**
     * Suscribe una vista a los cambios del carrito. Devuelve la funcion para
     * desuscribirse.
     */
    static subscribe(listener) {
        if (typeof listener !== "function") return () => {};
        subscribers.add(listener);
        return () => subscribers.delete(listener);
    }
}
