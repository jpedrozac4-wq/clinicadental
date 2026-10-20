/* ============================================================================
   SHELL DEL PORTAL ERP
   ----------------------------------------------------------------------------
   Construye el marco de navegacion: sidebar filtrado por permisos, cabecera con
   perfil activo, cierre de sesion y la compuerta de login. El shell no conoce
   ningun modulo de negocio: solo el registro de rutas y la sesion.
   ============================================================================ */
import { AuthService } from "../services/auth-service.js";
import { NotificationService } from "../services/notification-service.js";
import { groupedRoutes } from "./routes.js";
import { el, qs } from "./dom.js";

export class Shell {

    constructor({ router }) {
        this.router = router;
        this.sidebarMenu = qs("#sidebar-menu");
        this.sidebar = qs("#erp-sidebar");
        this.viewContainer = qs("#erp-view");
        this.userName = qs("#current-user-name");
        this.userRole = qs("#current-user-role");
        this.loginGate = qs("#login-gate");
        this.shell = qs("#erp-shell");
        this.menuToggle = qs("#sidebar-toggle");
    }

    /** Muestra el portal autenticado y arranca el router. */
    showPortal() {
        if (this.loginGate) this.loginGate.hidden = true;
        if (this.shell) this.shell.hidden = false;
        this.renderSidebar();
        this.renderUser();
        this.bindChrome();
        this.router.start();
    }

    /** Muestra la compuerta de login y detiene la navegacion. */
    showLoginGate() {
        this.router.stop();
        if (this.shell) this.shell.hidden = true;
        if (this.loginGate) this.loginGate.hidden = false;
        const username = qs("#login-username");
        if (username) username.focus();
    }

    renderSidebar() {
        if (!this.sidebarMenu) return;
        const accessible = this.router.accessibleRoutes();
        this.sidebarMenu.replaceChildren();

        groupedRoutes(accessible).forEach(({ name, items }) => {
            this.sidebarMenu.append(el("div", { class: "sidebar-group-label", text: name }));
            items.forEach(route => {
                const link = el("a", {
                    href: `#${route.path}`,
                    class: "sidebar-link",
                    dataset: { routeId: route.id }
                }, [
                    el("span", { class: "sidebar-icon", text: route.icon || "•" }),
                    el("span", { class: "sidebar-label", text: route.label })
                ]);
                link.addEventListener("click", () => this.closeMobileSidebar());
                this.sidebarMenu.append(link);
            });
        });

        if (!accessible.length) {
            this.sidebarMenu.append(el("p", {
                class: "sidebar-empty",
                text: "Tu perfil no tiene módulos habilitados."
            }));
        }
    }

    renderUser() {
        const session = AuthService.getSession();
        if (!session) return;
        const roleLabel = (session.roles[0] || "SIN ROL").replace("ROLE_", "");
        if (this.userName) this.userName.textContent = session.fullName || session.username;
        if (this.userRole) this.userRole.textContent = roleLabel;
    }

    /** Resalta el item activo del sidebar. */
    markActive(route) {
        if (!this.sidebarMenu) return;
        this.sidebarMenu.querySelectorAll(".sidebar-link").forEach(link => {
            link.classList.toggle("active", Boolean(route) && link.dataset.routeId === route.id);
        });
    }

    bindChrome() {
        const logoutButton = qs("#btn-logout");
        if (logoutButton && !logoutButton.dataset.bound) {
            logoutButton.dataset.bound = "true";
            logoutButton.addEventListener("click", async () => {
                const confirmed = await NotificationService.confirm(
                    "¿Querés cerrar la sesión en este equipo?",
                    { confirmLabel: "Cerrar sesión", cancelLabel: "Seguir trabajando" }
                );
                if (!confirmed) return;
                await AuthService.logout();
                window.location.hash = "";
                this.showLoginGate();
                NotificationService.info("Sesión cerrada correctamente.");
            });
        }

        if (this.menuToggle && !this.menuToggle.dataset.bound) {
            this.menuToggle.dataset.bound = "true";
            this.menuToggle.addEventListener("click", () => {
                if (this.sidebar) this.sidebar.classList.toggle("open");
            });
        }
    }

    closeMobileSidebar() {
        if (this.sidebar) this.sidebar.classList.remove("open");
    }

    /** Engancha el formulario de login de la compuerta. */
    bindLoginForm(onAuthenticated) {
        const form = qs("#login-form");
        if (!form) return;

        const submitButton = qs("#btn-erp-login");
        const errorBox = qs("#login-error");

        form.addEventListener("submit", async event => {
            event.preventDefault();
            const username = qs("#login-username").value.trim();
            const password = qs("#login-password").value;

            if (!username || !password) {
                this.showLoginError("Ingresá usuario y contraseña.");
                return;
            }

            if (errorBox) errorBox.hidden = true;
            if (submitButton) {
                submitButton.disabled = true;
                submitButton.textContent = "Verificando credenciales…";
            }

            try {
                const session = await AuthService.login(username, password);
                NotificationService.success(`Bienvenido, ${session.fullName}.`);
                onAuthenticated();
            } catch (error) {
                this.showLoginError(
                    error.status === 401
                        ? "Usuario o contraseña incorrectos."
                        : error.message
                );
            } finally {
                if (submitButton) {
                    submitButton.disabled = false;
                    submitButton.textContent = "Ingresar al ERP";
                }
            }
        });

        qs("#login-demo-profiles")?.addEventListener("click", event => {
            const button = event.target.closest("[data-demo-user]");
            if (!button) return;
            qs("#login-username").value = button.dataset.demoUser;
            qs("#login-password").value = button.dataset.demoPassword || "Password123!";
            qs("#login-password").focus();
        });
    }

    showLoginError(message) {
        const errorBox = qs("#login-error");
        if (!errorBox) return;
        errorBox.textContent = message;
        errorBox.hidden = false;
    }
}
