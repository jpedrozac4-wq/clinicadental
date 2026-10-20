/* ============================================================================
   SHELL DEL SISTEMA CLINICO
   ----------------------------------------------------------------------------
   Construye el marco de navegacion: sidebar filtrado por rol y permisos,
   cabecera con el perfil activo, cierre de sesion y la compuerta de login. El
   shell no conoce ningun modulo de negocio: solo el registro de rutas y la
   sesion vigente.
   ============================================================================ */
import { AuthService } from "../services/auth-service.js";
import { NotificationService } from "../services/notification-service.js";
import { groupedRoutes } from "./routes.js";
import { el, qs } from "./dom.js";
import { CONFIG } from "../config.js";

export class Shell {

    constructor({ router }) {
        this.router = router;
        this.sidebarMenu = qs("#sidebar-menu");
        this.sidebar = qs("#app-sidebar");
        this.viewContainer = qs("#app-view");
        this.userName = qs("#current-user-name");
        this.userRole = qs("#current-user-role");
        this.loginGate = qs("#login-gate");
        this.shell = qs("#app-shell");
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
        const password = qs("#login-password");
        if (password) password.value = "";
        const username = qs("#login-username");
        if (username) username.focus();
    }

    renderSidebar() {
        if (!this.sidebarMenu) return;
        const accessible = this.router.navRoutes();
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

        if (this.userName) this.userName.textContent = session.fullName || session.username;
        if (this.userRole) {
            this.userRole.textContent = AuthService.roleLabel(session.roles);
        }

        // Los botones de cabecera se etiquetan por rol para que el usuario sepa
        // con que perfil esta trabajando (S13: sesion por roles).
        if (this.shell) {
            this.shell.dataset.role = (session.roles[0] || "").replace("ROLE_", "").toLowerCase();
        }
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

        const submitButton = qs("#btn-login");
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
                const nombre = (session.fullName || session.username).split(" (")[0];
                NotificationService.success(`Bienvenido/a, ${nombre}.`);
                onAuthenticated();
            } catch (error) {
                this.showLoginError(this.loginErrorMessage(error));
            } finally {
                if (submitButton) {
                    submitButton.disabled = false;
                    submitButton.textContent = "Ingresar al sistema";
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

    /**
     * Mensaje de fallo de login. Spring Security responde 401 con
     * {"status":"ERROR","message":"No autenticado"} cuando las credenciales no
     * son validas, y el backend no expone el motivo exacto a proposito.
     */
    loginErrorMessage(error) {
        if (error?.status === 401 || error?.status === 403) {
            return "Usuario o contraseña incorrectos.";
        }
        if (error?.isNetworkError) {
            return "No hay conexión con el servidor. Verificá que el backend esté disponible.";
        }
        return `No se pudo iniciar sesión (HTTP ${error?.status || "?"}). Verificá tu usuario y contraseña.`;
    }

    showLoginError(message) {
        const errorBox = qs("#login-error");
        if (!errorBox) return;
        errorBox.textContent = message;
        errorBox.hidden = false;
        if (this.shell) this.shell.hidden = true;
        if (this.loginGate) this.loginGate.hidden = false;
    }

    /** Nombre del sistema para las vistas que lo necesiten. */
    static appName() {
        return CONFIG.APP_NAME;
    }
}
