/* ============================================================================
   SERVICIO DE USUARIOS, ROLES Y PERMISOS (S18, S20)
   ----------------------------------------------------------------------------
   Unico punto de acceso al padron del personal y a la matriz de permisos. Los
   modulos nunca arman la URL a mano: los listados salen de CONFIG.ENDPOINTS y
   las rutas con id, de los helpers usuarioUrl() y rolUrl(); el catalogo de
   odontologos de la agenda, de odontologosUrl() (S20).

   Contrato real del backend (modulo `usuario`):
   - GET    /api/usuarios?rol=&estado=&q=  -> UsuarioResponse[]
             `rol` acepta DENTISTA o ROLE_DENTISTA; `estado` acepta activo o
             inactivo. Sin estado se devuelve el padron completo, salvo que se
             pida rol=DENTISTA: ahi el backend devuelve solo odontologos activos
             para que la agenda nunca ofrezca un profesional dado de baja.
   - POST   /api/usuarios                  -> 201 UsuarioResponse
   - PUT    /api/usuarios/{id}             -> UsuarioResponse (password opcional)
   - DELETE /api/usuarios/{id}             -> 204 (baja logica, idempotente)
             409 si dejaria el sistema sin administrador activo o si el actor se
             da de baja a si mismo.
   - GET    /api/permisos                  -> PermisoResponse[]
             { permissionName, modulo, action, description }
   - GET    /api/roles                     -> RolResponse[]
             { id, roleName, description, permissions: string[] }
   - POST   /api/roles                     -> 201 RolResponse
   - PUT    /api/roles/{id}                -> RolResponse (matriz por reemplazo)
   - DELETE /api/roles/{id}                -> 204 (409 si tiene usuarios o es base)

   Payload camelCase del usuario: { username, fullName, email, password, rol }.
   El rol viaja en su forma canonica (ROLE_DENTISTA), que es la que publica
   GET /api/roles y la que acepta el backend en ambos formatos.

   Importante: los nombres de permiso que publica la API son los de la columna
   `permission_name` (PACIENTE_VER, no OP_PACIENTE_VER). El prefijo OP_ lo agrega
   el backend al firmar el JWT, por eso este servicio nunca traduce contra
   CONFIG.PERMISSIONS: lo que llega del catalogo es lo que se envia de vuelta.
   ============================================================================ */
import { CONFIG, apiQuery, odontologosUrl, rolUrl, usuarioUrl } from "../config.js";
import { fetchJson } from "./api.js";

const USUARIOS = CONFIG.ENDPOINTS.USUARIOS;
const ROLES = CONFIG.ENDPOINTS.ROLES;
const PERMISOS = CONFIG.ENDPOINTS.PERMISOS;

export class UsuarioService {

    /**
     * Padron filtrable. Los tres filtros son opcionales y los resuelve el backend.
     * @param {{rol?: string, estado?: string, q?: string}} [filtros]
     */
    static async listar({ rol = null, estado = null, q = null } = {}) {
        const data = await fetchJson(apiQuery(USUARIOS, { rol, estado, q }));
        return Array.isArray(data) ? data : [];
    }

    /**
     * Odontologos activos para el selector de la agenda (S20).
     * El backend ya filtra por estado; el filtro local es una red de seguridad
     * para que una fila sin `activo` nunca active un profesional de baja.
     */
    static async odontologos() {
        const data = await fetchJson(odontologosUrl());
        return (Array.isArray(data) ? data : []).filter(usuario =>
            usuario
            && usuario.id !== null && usuario.id !== undefined
            && usuario.activo !== false);
    }

    static async crear(usuario) {
        return fetchJson(USUARIOS, { method: "POST", body: toPayload(usuario) });
    }

    static async actualizar(id, usuario) {
        return fetchJson(usuarioUrl(id), { method: "PUT", body: toPayload(usuario) });
    }

    /** Baja logica: el usuario queda inactivo y conserva su historial. */
    static async darDeBaja(id) {
        await fetchJson(usuarioUrl(id), { method: "DELETE" });
        return true;
    }
}

export class RolService {

    static async listar() {
        const data = await fetchJson(ROLES);
        return Array.isArray(data) ? data : [];
    }

    static async crear(rol) {
        return fetchJson(ROLES, { method: "POST", body: toPayloadRol(rol) });
    }

    static async actualizar(id, rol) {
        return fetchJson(rolUrl(id), { method: "PUT", body: toPayloadRol(rol) });
    }

    static async eliminar(id) {
        await fetchJson(rolUrl(id), { method: "DELETE" });
        return true;
    }
}

export class PermisoService {

    /** Catalogo completo de permisos, agrupable por `modulo` en la vista. */
    static async listar() {
        const data = await fetchJson(PERMISOS);
        return Array.isArray(data) ? data : [];
    }
}

/** Cuerpo de alta/edicion. La contrasenia vacia se omite para conservar la actual. */
function toPayload(usuario) {
    const payload = {
        username: texto(usuario.username),
        fullName: texto(usuario.fullName),
        email: texto(usuario.email),
        rol: texto(usuario.rol)
    };

    const password = usuario.password === null || usuario.password === undefined
        ? ""
        : String(usuario.password);
    if (password) {
        payload.password = password;
    }
    return payload;
}

/** La matriz de permisos viaja completa: el backend reemplaza la anterior. */
function toPayloadRol(rol) {
    return {
        roleName: texto(rol.roleName),
        description: texto(rol.description),
        permissions: Array.isArray(rol.permissions)
            ? rol.permissions.map(permiso => texto(permiso)).filter(Boolean)
            : []
    };
}

function texto(value) {
    return value === null || value === undefined ? "" : String(value).trim();
}
