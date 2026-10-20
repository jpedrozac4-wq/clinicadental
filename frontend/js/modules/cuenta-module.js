/* ============================================================================
   VISTA: MI CUENTA Y SEGURIDAD
   ----------------------------------------------------------------------------
   Muestra el perfil autenticado, sus roles y la matriz de permisos efectiva,
   para que cada usuario entienda que puede y que no puede hacer en el sistema.
   ============================================================================ */
import { CONFIG } from "../config.js";
import { AuthService } from "../services/auth-service.js";
import { NotificationService } from "../services/notification-service.js";
import { el, sectionHeader, humanize } from "../core/dom.js";

export class CuentaView {

    async mount(container) {
        this.container = container;
        const session = AuthService.getSession();

        container.replaceChildren(
            sectionHeader("Mi cuenta", "Sesión activa"),
            el("div", { class: "grid-2" }, [
                this.profileCard(session),
                this.permissionsCard(session)
            ]),
            this.sessionCard()
        );
        return null;
    }

    profileCard(session) {
        return el("div", { class: "card mb-0" }, [
            el("h3", { text: "Perfil" }),
            el("dl", { class: "detail-list mt-2" }, [
                this.detail("Usuario", session.username),
                this.detail("Nombre completo", session.fullName),
                this.detail("Email", session.email),
                this.detail("Roles", session.roles.map(role => humanize(role.replace("ROLE_", ""))).join(", "))
            ])
        ]);
    }

    permissionsCard(session) {
        return el("div", { class: "card mb-0" }, [
            el("h3", { text: "Permisos efectivos" }),
            el("p", {
                class: "text-muted text-sm",
                text: "Los permisos provienen del token emitido por el backend. El menú y los endpoints se habilitan a partir de ellos."
            }),
            el("div", { class: "chip-list mt-2" }, session.permissions.length
                ? session.permissions.map(permission => el("span", { class: "chip", text: humanize(permission) }))
                : [el("span", { class: "text-muted", text: "Sin permisos granulares asignados." })])
        ]);
    }

    sessionCard() {
        return el("div", { class: "card mt-2" }, [
            el("h3", { text: "Sesión" }),
            el("p", {
                class: "text-muted text-sm",
                text: `El token expira a las ${Math.round(CONFIG.SESSION.EXPIRES_IN_SECONDS / 3600)} horas. Al cerrar sesión se descarta el token en este equipo.`
            }),
            el("button", {
                class: "btn btn-danger mt-1",
                text: "Cerrar sesión",
                on: {
                    click: async () => {
                        const confirmed = await NotificationService.confirm("¿Cerrar la sesión ahora?");
                        if (!confirmed) return;
                        await AuthService.logout();
                        window.location.reload();
                    }
                }
            })
        ]);
    }

    detail(label, value) {
        return el("div", { class: "detail-row" }, [
            el("dt", { text: label }),
            el("dd", { text: value || "—" })
        ]);
    }

    unmount() {
        this.container = null;
    }
}
