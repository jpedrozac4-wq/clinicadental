/* ============================================================================
   SERVICIO DE AUTENTICACION Y CONTROL DE ACCESO (RBAC CLINICO)
   ----------------------------------------------------------------------------
   Sesion stateless basada en JWT. El token y el perfil se persisten en
   localStorage; los permisos se evaluan siempre contra el perfil emitido por el
   backend, nunca contra datos inventados en el cliente.

   Nota de contrato: /api/auth/login devuelve `roles` y `permissions` sin prefijo
   ("ADMINISTRADOR", "PACIENTE_VER") pero tambien la lista completa `authorities`
   ("ROLE_ADMINISTRADOR", "OP_PACIENTE_VER"). Este servicio normaliza todo a la
   forma con prefijo, que es la que usan los matchers del backend.
   ============================================================================ */
import { CONFIG } from "../config.js";
import { fetchJson, getStoredToken, storeToken } from "./api.js";

const ROLE_PREFIX = "ROLE_";
const PERMISSION_PREFIX = "OP_";

const listeners = new Set();

/**
 * Normaliza el payload de autenticacion a { authorities, roles, permissions }
 * con los prefijos canonicos del backend.
 */
function normalizeAuthorities(payload) {
    const source = Array.isArray(payload?.authorities) && payload.authorities.length
        ? payload.authorities
        : [
            ...(payload?.roles || []).map(role => withPrefix(role, ROLE_PREFIX)),
            ...(payload?.permissions || []).map(permission => withPrefix(permission, PERMISSION_PREFIX))
        ];

    const authorities = [...new Set(source)].sort();
    return {
        authorities,
        roles: authorities.filter(authority => authority.startsWith(ROLE_PREFIX)),
        permissions: authorities.filter(authority => authority.startsWith(PERMISSION_PREFIX))
    };
}

function withPrefix(value, prefix) {
    const text = String(value || "").trim();
    if (!text) return text;
    return text.startsWith(prefix) ? text : `${prefix}${text}`;
}

export class AuthService {

    static getSession() {
        const rawUser = localStorage.getItem(CONFIG.STORAGE_KEYS.USER);
        if (!rawUser || !getStoredToken()) return null;
        try {
            return JSON.parse(rawUser);
        } catch {
            this.clearSession();
            return null;
        }
    }

    static getUser() {
        return this.getSession();
    }

    static isAuthenticated() {
        return this.getSession() !== null;
    }

    /** Inicia sesion y persiste token + perfil. Lanza ApiError si las credenciales fallan. */
    static async login(username, password) {
        const response = await fetchJson(CONFIG.ENDPOINTS.AUTH_LOGIN, {
            method: "POST",
            auth: false,
            body: { username, password }
        });

        if (!response?.token) {
            throw new Error("El servidor no devolvió un token de sesión.");
        }

        const session = this.buildSession(response);
        storeToken(response.token);
        localStorage.setItem(CONFIG.STORAGE_KEYS.USER, JSON.stringify(session));
        this.notify();
        return session;
    }

    /** Valida el token contra el backend y refresca el perfil. */
    static async refresh() {
        if (!getStoredToken()) return null;
        const response = await fetchJson(CONFIG.ENDPOINTS.AUTH_ME);
        const session = this.buildSession(response, this.getSession());
        localStorage.setItem(CONFIG.STORAGE_KEYS.USER, JSON.stringify(session));
        this.notify();
        return session;
    }

    static async logout() {
        try {
            await fetchJson(CONFIG.ENDPOINTS.AUTH_LOGOUT, { method: "POST" });
        } catch {
            // El cierre de sesion es local: si el backend no responde, igual se limpia.
        }
        this.clearSession();
    }

    /** Limpieza local sin llamada al backend (usada ante un 401). */
    static clearSession() {
        storeToken(null);
        localStorage.removeItem(CONFIG.STORAGE_KEYS.USER);
        this.notify();
    }

    static buildSession(payload, previous = null) {
        const { authorities, roles, permissions } = normalizeAuthorities(payload);
        return {
            username: payload.username || previous?.username || "",
            fullName: payload.fullName || previous?.fullName || payload.username || "",
            email: payload.email || previous?.email || "",
            roles,
            permissions,
            authorities,
            issuedAt: previous?.issuedAt || Date.now()
        };
    }

    static hasRole(roleName) {
        const session = this.getSession();
        return Boolean(session && session.roles.includes(roleName));
    }

    static hasPermission(permissionName) {
        const session = this.getSession();
        return Boolean(session && session.permissions.includes(permissionName));
    }

    /** Alias corto usado por las vistas para habilitar acciones. */
    static can(permissionName) {
        return this.hasPermission(permissionName);
    }

    /** true si el usuario tiene al menos uno de los permisos indicados. */
    static hasAnyPermission(permissions = []) {
        if (!permissions.length) return true;
        return permissions.some(permission => this.hasPermission(permission));
    }

    /** true si el usuario tiene al menos uno de los roles indicados. */
    static hasAnyRole(roles = []) {
        if (!roles.length) return true;
        return roles.some(role => this.hasRole(role));
    }

    /**
     * Regla de visibilidad de un modulo: alcanza con cumplir los roles O los
     * permisos declarados en la ruta. ROLE_ADMINISTRADOR es superusuario y ve
     * todo, igual que en los matchers del backend. Una ruta sin restricciones es
     * visible para cualquier sesion autenticada.
     */
    static canAccess({ roles = [], permissions = [] } = {}) {
        if (!this.isAuthenticated()) return false;
        if (this.hasRole(CONFIG.ROLES.ADMINISTRADOR)) return true;
        if (roles.length && this.hasAnyRole(roles)) return true;
        if (permissions.length && this.hasAnyPermission(permissions)) return true;
        return !roles.length && !permissions.length;
    }

    /** Etiqueta legible del rol activo (el primero del perfil). */
    static roleLabel(roles = null) {
        const session = this.getSession();
        const list = roles || session?.roles || [];
        if (!list.length) return "Sin rol";
        return list.map(role => CONFIG.ROLE_LABELS[role] || role.replace(ROLE_PREFIX, "")).join(" · ");
    }

    static onChange(listener) {
        listeners.add(listener);
        return () => listeners.delete(listener);
    }

    static notify() {
        listeners.forEach(listener => listener(this.getSession()));
    }
}
