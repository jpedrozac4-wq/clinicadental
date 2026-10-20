/* ============================================================================
   CONFIGURACION GLOBAL DEL FRONTEND CLINICO (ENDPOINTS, ROLES Y CONTRATO API)
   ----------------------------------------------------------------------------
   Punto unico de verdad para hablar con el backend Spring Boot de la clinica.
   Los modulos nunca escriben rutas literales: siempre pasan por CONFIG.ENDPOINTS
   o por los helpers con parametros, para que un cambio de API se resuelva en un
   solo archivo.
   ============================================================================ */

const API = "/api";

export const CONFIG = {
    API_BASE_URL: API,
    APP_NAME: "Clínica Dental",
    APP_SUBTITLE: "Sistema de gestión odontológica",
    CURRENCY: "USD",
    LOCALE: "es-ES",

    STORAGE_KEYS: {
        TOKEN: "clinica.auth.token",
        USER: "clinica.auth.user"
    },

    /** Sesion: el token expira a las 8 horas (28800 s) en el backend. */
    SESSION: {
        EXPIRES_IN_SECONDS: 28800,
        WARN_BEFORE_SECONDS: 600
    },

    ENDPOINTS: {
        // ---- Autenticacion ----
        AUTH_LOGIN: `${API}/auth/login`,
        AUTH_LOGOUT: `${API}/auth/logout`,
        AUTH_ME: `${API}/auth/me`,

        // ---- Gestion de pacientes (S1, S2) ----
        PACIENTES: `${API}/pacientes`,

        // ---- Catalogo de tratamientos (S7) ----
        TRATAMIENTOS: `${API}/tratamientos`,

        // ---- Agenda de citas (S4, S6) ----
        CITAS: `${API}/citas`,

        // ---- Tratamientos realizados (S3, S8) ----
        TRATAMIENTOS_REALIZADOS: `${API}/tratamientos-realizados`,

        // ---- Facturacion y pagos (S9, S10) ----
        FACTURAS: `${API}/facturas`,

        // ---- Reservado: recordatorios (S5, S15), otro modulo lo entrega ----
        RECORDATORIOS: `${API}/recordatorios`
    },

    /**
     * Roles canonicos emitidos por el backend. El JWT los transporta con prefijo
     * ROLE_, pero el payload de /api/auth/login los publica sin prefijo; el
     * servicio de autenticacion normaliza ambos formatos a esta forma.
     */
    ROLES: {
        ADMINISTRADOR: "ROLE_ADMINISTRADOR",
        DENTISTA: "ROLE_DENTISTA",
        RECEPCIONISTA: "ROLE_RECEPCIONISTA"
    },

    /** Etiquetas legibles por rol, para el shell y las vistas. */
    ROLE_LABELS: {
        ROLE_ADMINISTRADOR: "Administrador",
        ROLE_DENTISTA: "Odontólogo",
        ROLE_RECEPCIONISTA: "Recepción"
    },

    /**
     * Permisos granulares con el nombre completo de la authority del backend
     * (OP_*). Los matchers de SecurityConfig usan exactamente estos nombres.
     */
    PERMISSIONS: {
        PACIENTE_VER: "OP_PACIENTE_VER",
        PACIENTE_CREAR: "OP_PACIENTE_CREAR",
        PACIENTE_EDITAR: "OP_PACIENTE_EDITAR",
        PACIENTE_ELIMINAR: "OP_PACIENTE_ELIMINAR",
        PACIENTE_HISTORIAL_VER: "OP_PACIENTE_HISTORIAL_VER",
        CITA_VER: "OP_CITA_VER",
        CITA_CREAR: "OP_CITA_CREAR",
        CITA_REPROGRAMAR: "OP_CITA_REPROGRAMAR",
        CITA_CANCELAR: "OP_CITA_CANCELAR",
        CITA_ATENDER: "OP_CITA_ATENDER",
        TRATAMIENTO_VER: "OP_TRATAMIENTO_VER",
        TRATAMIENTO_CREAR: "OP_TRATAMIENTO_CREAR",
        TRATAMIENTO_EDITAR: "OP_TRATAMIENTO_EDITAR",
        REALIZADO_VER: "OP_REALIZADO_VER",
        REALIZADO_REGISTRAR: "OP_REALIZADO_REGISTRAR",
        FACTURA_VER: "OP_FACTURA_VER",
        FACTURA_EMITIR: "OP_FACTURA_EMITIR",
        PAGO_REGISTRAR: "OP_PAGO_REGISTRAR",
        RECORDATORIO_VER: "OP_RECORDATORIO_VER"
    },

    /** Estados canonicos del schema clinico (cat_estado_cita / factura). */
    ESTADOS: {
        CITA: ["PROGRAMADA", "CONFIRMADA", "REPROGRAMADA", "ATENDIDA", "CANCELADA", "NO_ASISTIO"],
        FACTURA: ["EMITIDA", "PAGO_PARCIAL", "PAGADA", "ANULADA"]
    }
};

/** Helper para construir rutas con parametros sin concatenar strings sueltos. */
export function apiUrl(base, ...segments) {
    const suffix = segments
        .filter(segment => segment !== undefined && segment !== null && segment !== "")
        .map(segment => encodeURIComponent(String(segment)))
        .join("/");
    return suffix ? `${base}/${suffix}` : base;
}

/** Helper para construir query strings ignorando valores vacios. */
export function apiQuery(base, params = {}) {
    const search = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
        if (value === undefined || value === null || value === "") return;
        search.append(key, String(value));
    });
    const query = search.toString();
    return query ? `${base}?${query}` : base;
}

/** Endpoint del historial clinico de un paciente (S3). */
export function pacienteHistorialUrl(id) {
    return `${apiUrl(CONFIG.ENDPOINTS.PACIENTES, id)}/historial`;
}

/** Accion sobre una cita: /api/citas/{id}/reprogramar | cancelar (S6). */
export function citaAccionUrl(id, accion) {
    return `${apiUrl(CONFIG.ENDPOINTS.CITAS, id)}/${accion}`;
}

/** Comprobante PDF de una factura (S9). */
export function facturaPdfUrl(id) {
    return `${apiUrl(CONFIG.ENDPOINTS.FACTURAS, id)}/pdf`;
}

/** Pagos registrados sobre una factura (S10). */
export function facturaPagosUrl(id) {
    return `${apiUrl(CONFIG.ENDPOINTS.FACTURAS, id)}/pagos`;
}
