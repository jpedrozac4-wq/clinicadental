/* ============================================================================
   SERVICIO DE AUTENTICACION Y CONTROL DE ACCESO (RBAC)
   ----------------------------------------------------------------------------
   Sesion stateless basada en JWT. El token y el perfil se persisten en
   localStorage; los permisos se evaluan siempre contra el perfil emitido por el
   backend, nunca contra datos inventados en el cliente.
   ============================================================================ */
import { CONFIG } from "../config.js";
import { fetchJson, getStoredToken, storeToken } from "./api.js";

const listeners = new Set();

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

        const session = {
            username: response.username,
            fullName: response.fullName,
            email: response.email,
            roles: response.roles || [],
            permissions: response.permissions || [],
            authorities: response.authorities || [],
            issuedAt: Date.now()
        };

        storeToken(response.token);
        localStorage.setItem(CONFIG.STORAGE_KEYS.USER, JSON.stringify(session));
        this.notify();
        return session;
    }

    /** Valida el token contra el backend y refresca el perfil. */
    static async refresh() {
        if (!getStoredToken()) return null;
        const response = await fetchJson(CONFIG.ENDPOINTS.AUTH_ME);
        const session = {
            ...(this.getSession() || {}),
            username: response.username,
            fullName: response.fullName,
            email: response.email,
            roles: response.roles || [],
            permissions: response.permissions || [],
            authorities: response.authorities || []
        };
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

    static hasRole(roleName) {
        const session = this.getSession();
        return Boolean(session && session.roles.includes(roleName));
    }

    static hasPermission(permissionName) {
        const session = this.getSession();
        return Boolean(session && session.permissions.includes(permissionName));
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
     * permisos declarados en la ruta. ROLE_ADMIN es superusuario y ve todo,
     * igual que en los matchers del backend.
     */
    static canAccess({ roles = [], permissions = [] } = {}) {
        if (!this.isAuthenticated()) return false;
        if (this.hasRole("ROLE_ADMIN")) return true;
        if (roles.length && this.hasAnyRole(roles)) return true;
        if (permissions.length && this.hasAnyPermission(permissions)) return true;
        return !roles.length && !permissions.length;
    }

    static onChange(listener) {
        listeners.add(listener);
        return () => listeners.delete(listener);
    }

    static notify() {
        listeners.forEach(listener => listener(this.getSession()));
    }
}
