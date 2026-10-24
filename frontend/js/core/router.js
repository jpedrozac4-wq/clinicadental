/* ============================================================================
   ROUTER HASH DEL SISTEMA CLINICO
   ----------------------------------------------------------------------------
   Rutas declarativas en core/routes.js. El router resuelve el hash, valida la
   sesion y los permisos, monta la vista en el contenedor y avisa al shell para
   resaltar el item activo. Las vistas se cargan con import() diferido, de modo
   que un perfil no descarga modulos que no puede usar.
   ============================================================================ */
import { el, emptyState } from "./dom.js";
import { AuthService } from "../services/auth-service.js";
import { CONFIG } from "../config.js";

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
        if (this._started) {
            this.resolve();
            return;
        }
        this._started = true;
        window.addEventListener("hashchange", this._onHashChange);

        if (!this.rawHash() || this.currentPath() === "/") {
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
     * modulo accesible. Evita que un perfil caiga en "Acceso restringido" apenas
     * inicia sesion.
     */
    preferredStartPath() {
        const accessible = this.navRoutes();
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

    /**
     * Rutas que ademas se muestran en el sidebar. Una ruta `hidden` existe y se
     * puede navegar (por ejemplo el historial de un paciente), pero no ocupa un
     * item del menu.
     */
    navRoutes() {
        return this.accessibleRoutes().filter(route => !route.hidden);
    }

    firstAccessiblePath() {
        const first = this.navRoutes()[0];
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
        return this.rawHash().split("?")[0] || "/";
    }

    /** Query string del hash (por ejemplo #/pacientes?nuevo=1). */
    currentQuery() {
        const raw = this.rawHash();
        const index = raw.indexOf("?");
        return new URLSearchParams(index >= 0 ? raw.slice(index + 1) : "");
    }

    rawHash() {
        return window.location.hash.replace(/^#/, "");
    }

    /** Resuelve el hash actual y monta la vista correspondiente. */
    async resolve() {
        const path = this.currentPath();
        const route = this.matchRoute(path);

        if (!route) {
            this.mountFallback(
                "Ruta no encontrada",
                `La sección "${path}" no existe en el sistema.`
            );
            return;
        }

        if (!AuthService.isAuthenticated()) {
            this.mountFallback("Sesión requerida", "Iniciá sesión para acceder al sistema.");
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

    /** Busca la ruta declarada que corresponde al path actual (admite :params). */
    matchRoute(path) {
        const exact = this.routes.find(candidate => candidate.path === path);
        if (exact) return exact;

        const pathParts = path.split("/").filter(Boolean);
        return this.routes.find(candidate => {
            const patternParts = candidate.path.split("/").filter(Boolean);
            if (patternParts.length !== pathParts.length) return false;
            return patternParts.every((part, index) =>
                part.startsWith(":") ? pathParts[index].length > 0 : part === pathParts[index]);
        }) || null;
    }

    async mountRoute(route, path) {
        this.unmountActive();
        this.activeRoute = route;
        this.container.replaceChildren();
        this.updateTitle(route);

        try {
            const module = await route.loader();
            const ViewClass = module[route.exportName];
            if (typeof ViewClass !== "function") {
                throw new Error(`La ruta "${route.id}" no exporta la vista "${route.exportName}".`);
            }

            const params = extractParams(route.path, path);
            const query = this.currentQuery();
            const view = new ViewClass({
                route,
                params,
                query,
                navigate: (target) => this.navigate(target)
            });
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

    updateTitle(route) {
        const label = route?.label || "Acceso";
        document.title = `${label} · ${CONFIG.APP_NAME}`;
    }

    mountFallback(title, message) {
        this.unmountActive();
        this.activeRoute = null;
        document.title = `${title} · ${CONFIG.APP_NAME}`;
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
export function extractParams(pattern, path) {
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
