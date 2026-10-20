/* ============================================================================
   ARRANQUE DEL SISTEMA CLINICO (SPA)
   ----------------------------------------------------------------------------
   Orden de arranque:
   1. Restaurar la sesion guardada y validarla contra /api/auth/me.
   2. Si hay sesion valida -> montar shell + router.
   3. Si no -> mostrar la compuerta de login.
   Un 401 en cualquier request vuelve a la compuerta automaticamente.
   ============================================================================ */
import { AuthService } from "./services/auth-service.js";
import { setUnauthorizedHandler } from "./services/api.js";
import { NotificationService } from "./services/notification-service.js";
import { Router } from "./core/router.js";
import { Shell } from "./core/shell.js";
import { ROUTES } from "./core/routes.js";
import { CONFIG } from "./config.js";

const viewContainer = document.querySelector("#app-view");

const router = new Router({
    container: viewContainer,
    routes: ROUTES,
    defaultPath: "/dashboard",
    onNavigate: (route) => shell.markActive(route)
});

const shell = new Shell({ router });

function startPortal() {
    shell.showPortal();
}

async function bootstrap() {
    document.title = CONFIG.APP_NAME;

    setUnauthorizedHandler(() => {
        shell.showLoginGate();
        NotificationService.warning("Tu sesión expiró. Volvé a ingresar.");
    });

    shell.bindLoginForm(startPortal);

    if (!AuthService.isAuthenticated()) {
        shell.showLoginGate();
        return;
    }

    try {
        await AuthService.refresh();
        startPortal();
    } catch (error) {
        AuthService.clearSession();
        shell.showLoginGate();
        if (!error.isUnauthorized) {
            NotificationService.error("No se pudo validar la sesión contra el servidor.");
        }
    }
}

document.addEventListener("DOMContentLoaded", bootstrap);
