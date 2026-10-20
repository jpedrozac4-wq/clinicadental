/* ============================================================================
   CLIENTE HTTP CENTRALIZADO (API SERVICE)
   ----------------------------------------------------------------------------
   Responsabilidades:
   - Inyectar el token JWT en cada request autenticada.
   - Normalizar errores del backend en ApiError con status y mensaje legible.
   - Cerrar la sesion cuando el backend responde 401.
   - Soportar JSON, FormData (subida de imagenes), blobs (PDF) y timeouts.
   ============================================================================ */
import { CONFIG } from "../config.js";

const DEFAULT_TIMEOUT_MS = 20000;

export class ApiError extends Error {
    constructor(message, status = 0, payload = null) {
        super(message);
        this.name = "ApiError";
        this.status = status;
        this.payload = payload;
    }

    get isUnauthorized() {
        return this.status === 401;
    }

    get isForbidden() {
        return this.status === 403;
    }

    get isNetworkError() {
        return this.status === 0;
    }
}

let unauthorizedHandler = null;

/** El shell registra aqui su reaccion al 401 (cerrar sesion y volver al login). */
export function setUnauthorizedHandler(handler) {
    unauthorizedHandler = handler;
}

export function getStoredToken() {
    return localStorage.getItem(CONFIG.STORAGE_KEYS.TOKEN);
}

export function storeToken(token) {
    if (token) {
        localStorage.setItem(CONFIG.STORAGE_KEYS.TOKEN, token);
    } else {
        localStorage.removeItem(CONFIG.STORAGE_KEYS.TOKEN);
    }
}

/**
 * Ejecuta una request contra la API.
 *
 * @param {string} endpoint URL absoluta o relativa (normalmente desde CONFIG.ENDPOINTS).
 * @param {object} [options]
 * @param {string} [options.method] Verbo HTTP, por defecto GET.
 * @param {object|FormData} [options.body] Cuerpo: objeto (se serializa a JSON) o FormData.
 * @param {boolean} [options.auth] Enviar el token JWT. Por defecto true.
 * @param {number} [options.timeoutMs] Timeout de la request.
 * @returns {Promise<any>} Cuerpo parseado, o null si la respuesta no trae contenido.
 */
export async function fetchJson(endpoint, options = {}) {
    const {
        method = "GET",
        body,
        headers = {},
        auth = true,
        timeoutMs = DEFAULT_TIMEOUT_MS
    } = options;

    const requestHeaders = { Accept: "application/json", ...headers };
    let requestBody = body;

    if (body instanceof FormData) {
        // El navegador debe fijar el boundary del multipart.
        requestBody = body;
    } else if (typeof body === "string") {
        // Cuerpo ya serializado por el llamador: se envia tal cual.
        requestHeaders["Content-Type"] = requestHeaders["Content-Type"] || "application/json";
        requestBody = body;
    } else if (body !== undefined && body !== null) {
        requestHeaders["Content-Type"] = "application/json";
        requestBody = JSON.stringify(body);
    }

    if (auth) {
        const token = getStoredToken();
        if (token) {
            requestHeaders.Authorization = `Bearer ${token}`;
        }
    }

    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), timeoutMs);

    let response;
    try {
        response = await fetch(endpoint, {
            method,
            headers: requestHeaders,
            body: requestBody,
            signal: controller.signal
        });
    } catch (error) {
        clearTimeout(timeoutId);
        if (error.name === "AbortError") {
            throw new ApiError("El servidor tardó demasiado en responder.", 0, null);
        }
        throw new ApiError("No hay conexión con el servidor.", 0, null);
    }
    clearTimeout(timeoutId);

    if (response.status === 401) {
        // Solo una sesion que existia se considera expirada. Un login fallido
        // (auth: false, sin token) no debe disparar el cierre de sesion.
        const hadToken = Boolean(getStoredToken());
        storeToken(null);
        const payload = await safeJson(response);
        if (auth && hadToken && typeof unauthorizedHandler === "function") {
            unauthorizedHandler();
        }
        throw new ApiError(payload?.message || "Sesión expirada. Iniciá sesión nuevamente.", 401, payload);
    }

    if (!response.ok) {
        const payload = await safeJson(response);
        const message = payload?.message || payload?.error || `Error HTTP ${response.status}`;
        throw new ApiError(message, response.status, payload);
    }

    if (response.status === 204) {
        return null;
    }

    const contentType = response.headers.get("Content-Type") || "";
    if (!contentType.includes("application/json")) {
        return response.text();
    }

    const text = await response.text();
    if (!text) {
        return null;
    }
    try {
        return JSON.parse(text);
    } catch {
        throw new ApiError("El servidor devolvió una respuesta ilegible.", response.status, text);
    }
}

/** Descarga un documento binario (tickets y facturas en PDF) con el token de sesion. */
export async function fetchBlob(endpoint, options = {}) {
    const headers = { ...options.headers };
    const token = getStoredToken();
    if (token && options.auth !== false) {
        headers.Authorization = `Bearer ${token}`;
    }

    const response = await fetch(endpoint, { ...options, headers });
    if (!response.ok) {
        throw new ApiError(`No se pudo descargar el documento (HTTP ${response.status}).`, response.status, null);
    }
    return response.blob();
}

/** Dispara la descarga de un blob en el navegador. */
export function downloadBlob(blob, filename) {
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = filename;
    document.body.append(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
}

async function safeJson(response) {
    try {
        return await response.json();
    } catch {
        return null;
    }
}
