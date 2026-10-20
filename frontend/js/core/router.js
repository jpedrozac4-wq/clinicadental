/* ============================================================================
   ROUTER HASH DEL PORTAL ERP
   ----------------------------------------------------------------------------
   Rutas declarativas en core/routes.js. El router resuelve el hash, valida la
   sesion y los permisos, monta la vista en el contenedor y avisa al shell para
   resaltar el item activo.
   ============================================================================ */
import { el, emptyState } from "./dom.js";
import { AuthService } from "../services/auth-service.js";

export class Router {

    /**
     * @param {object} options
     * @param {HTMLElement} options.container Contenedor donde se montan las vistas.
     * @param {Array<object>} options.routes Rutas declaradas.
     * @param {string} [options.defaultPath] Ruta a la que ir cuando el hash esta vacio.
     * @param {Function} [options.onNavigate] Callback (route, params) tras montar.
     */
    constructor({ container, routes, defaultPath = null, onNavigate = null }) {
        this.container = container;
        this.routes = routes;
        this.defaultPath = defaultPath;
        this.onNavigate = onNavigate;
        this.activeView = null;
        this.activeRoute = null;
        this._onHashChange = () => this.resolve();
    }

    start() {
        if (this._started) return;
        this._started = true;
        window.addEventListener("hashchange", this._onHashChange);

        if (!window.location.hash) {
            this.navigate(this.preferredStartPath(), { replace: true });
            return;
        }
        this.resolve();
    }

    stop() {
        this._started = false;
        window.removeEventListener("hashchange", this._onHashChange);
        this.unmountActive();
    }

    /**
     * Ruta de aterrizaje: la preferida si el perfil puede verla; si no, el primer
     * modulo accesible. Evita que un cajero caiga en "Acceso restringido" apenas
     * inicia sesion.
     */
    preferredStartPath() {
        const accessible = this.accessibleRoutes();
        if (!accessible.length) {
            return this.defaultPath || "/sin-acceso";
        }
        const preferred = accessible.find(route => route.path === this.defaultPath);
        return (preferred || accessible[0]).path;
    }

    /** Rutas visibles para la sesion actual, en el orden declarado. */
    accessibleRoutes() {
        return this.routes.filter(route => AuthService.canAccess(route));
    }

    firstAccessiblePath() {
        const first = this.accessibleRoutes()[0];
        return first ? first.path : "/sin-acceso";
    }

    navigate(path, { replace = false } = {}) {
        const target = `#${path}`;
        if (window.location.hash === target) {
            this.resolve();
            return;
        }
        if (replace) {
            window.history.replaceState(null, "", target);
            this.resolve();
        } else {
            window.location.hash = target;
        }
    }

    currentPath() {
        return window.location.hash.replace(/^#/, "") || "/";
    }

    /** Resuelve el hash actual y monta la vista correspondiente. */
    async resolve() {
        const path = this.currentPath();
        const route = this.routes.find(candidate => candidate.path === path);

        if (!route) {
            this.mountFallback(
                "Ruta no encontrada",
                `La sección "${path}" no existe en este portal.`
            );
            return;
        }

        if (!AuthService.isAuthenticated()) {
            this.mountFallback("Sesión requerida", "Iniciá sesión para acceder al portal.");
            this.notify(route, null);
            return;
        }

        if (!AuthService.canAccess(route)) {
            this.mountFallback(
                "Acceso restringido",
                `Tu perfil no tiene permisos para el módulo "${route.label}".`
            );
            this.notify(route, null);
            return;
        }

        await this.mountRoute(route, path);
    }

    async mountRoute(route, path) {
        this.unmountActive();
        this.activeRoute = route;
        this.container.replaceChildren();

        try {
            const module = await route.loader();
            const ViewClass = module[route.exportName];
            if (typeof ViewClass !== "function") {
                throw new Error(`La ruta "${route.id}" no exporta la vista "${route.exportName}".`);
            }

            const params = extractParams(route.path, path);
            const view = new ViewClass({ route, params, navigate: (target) => this.navigate(target) });
            this.activeView = view;

            const content = await view.mount(this.container);
            if (content instanceof Node && !this.container.contains(content)) {
                this.container.append(content);
            }
            this.notify(route, params);
        } catch (error) {
            console.error(`[Router] Fallo al montar la ruta ${route.id}:`, error);
            this.container.replaceChildren(
                emptyState("No se pudo cargar el módulo", error.message)
            );
            this.notify(route, null);
        }
    }

    mountFallback(title, message) {
        this.unmountActive();
        this.activeRoute = null;
        this.container.replaceChildren(
            el("div", { class: "card" }, [
                el("h2", { text: title }),
                el("p", { class: "text-muted", text: message })
            ])
        );
    }

    unmountActive() {
        if (this.activeView && typeof this.activeView.unmount === "function") {
            try {
                this.activeView.unmount();
            } catch (error) {
                console.error("[Router] Error al desmontar la vista:", error);
            }
        }
        this.activeView = null;
    }

    notify(route, params) {
        if (typeof this.onNavigate === "function") {
            this.onNavigate(route, params);
        }
    }
}

/** Extrae parametros de rutas con el patron "/ruta/:id". */
function extractParams(pattern, path) {
    const patternParts = pattern.split("/").filter(Boolean);
    const pathParts = path.split("/").filter(Boolean);
    if (patternParts.length !== pathParts.length) return {};

    return patternParts.reduce((params, part, index) => {
        if (part.startsWith(":")) {
            params[part.slice(1)] = decodeURIComponent(pathParts[index]);
        }
        return params;
    }, {});
}
