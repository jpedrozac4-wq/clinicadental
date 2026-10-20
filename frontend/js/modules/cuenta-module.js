/* ============================================================================
   VISTA: MI CUENTA
   ----------------------------------------------------------------------------
   Muestra el perfil autenticado, sus roles y la matriz de permisos efectiva,
   para que cada usuario entienda que puede y que no puede hacer en el sistema.
   No consume endpoints: trabaja sobre la sesion emitida por /api/auth/login y
   validada contra /api/auth/me.
   ============================================================================ */
import { CONFIG } from "../config.js";
import { AuthService } from "../services/auth-service.js";
import {
    el, pageHeader, sectionHeader, detailRow, chip, humanize
} from "../core/dom.js";

export class CuentaView {

    async mount(container) {
        const session = AuthService.getSession() || {};

        container.replaceChildren(
            pageHeader(
                "Mi cuenta",
                "Perfil con el que estás trabajando y permisos que habilita el sistema."
            ),
            el("div", { class: "grid-2" }, [
                this.perfilCard(session),
                this.permisosCard(session)
            ]),
            this.sesionCard(session)
        );

        return null;
    }

    perfilCard(session) {
        const roles = session.roles || [];
        return el("div", { class: "card mb-0" }, [
            sectionHeader("Perfil", null, []),
            el("dl", { class: "detail-list" }, [
                detailRow("Usuario", session.username || "—"),
                detailRow("Nombre completo", session.fullName || "—"),
                detailRow("Correo electrónico", session.email || "—"),
                detailRow("Rol", AuthService.roleLabel(roles)),
                detailRow("Roles asignados", roles.length
                    ? el("span", { class: "chip-list" }, roles.map(role =>
                        chip(CONFIG.ROLE_LABELS[role] || role.replace("ROLE_", ""))))
                    : el("span", { class: "text-muted", text: "Sin roles asignados" }))
            ])
        ]);
    }

    permisosCard(session) {
        const permisos = session.permissions || [];
        return el("div", { class: "card mb-0" }, [
            sectionHeader("Permisos efectivos", `${permisos.length}`),
            el("p", {
                class: "text-muted text-sm",
                text: "Los permisos provienen del token emitido por el backend. El menú y los endpoints se habilitan a partir de ellos."
            }),
            el("div", { class: "chip-list mt-2" }, permisos.length
                ? permisos.map(permiso => chip(humanize(permiso.replace("OP_", ""))))
                : [el("span", { class: "text-muted", text: "Sin permisos granulares asignados." })])
        ]);
    }

    sesionCard(session) {
        const horas = Math.round(CONFIG.SESSION.EXPIRES_IN_SECONDS / 3600);
        const emitida = session.issuedAt ? new Date(session.issuedAt) : null;

        return el("div", { class: "card" }, [
            sectionHeader("Sesión", "JWT"),
            el("dl", { class: "detail-list" }, [
                detailRow("Tipo de sesión", "Stateless con JSON Web Token"),
                detailRow("Vigencia del token", `${horas} horas`),
                detailRow("Inicio de sesión", emitida ? emitida.toLocaleString(CONFIG.LOCALE) : "—")
            ]),
            el("p", {
                class: "text-muted text-sm mt-2",
                text: "Al cerrar sesión se descarta el token en este equipo; el servidor no guarda estado de la sesión."
            })
        ]);
    }
}
