/* ============================================================================
   VISTA: USUARIOS Y ROLES (S18, S20)
   ----------------------------------------------------------------------------
   Administra el padron del personal y la matriz de permisos en una sola ruta
   (#/usuarios, permiso OP_USUARIOS_ADMIN). La pantalla tiene dos pestanas:

   1. Usuarios
      - Tabla con usuario, nombre, correo, rol, estado y una columna que marca
        quien esta disponible en la agenda.
      - Filtros por rol, estado y texto libre: los tres los resuelve el backend.
      - Alta y edicion en un panel lateral, con baja logica confirmada.
      - Alta de odontologos (S20): crear un usuario con rol Odontologo es lo que
        alimenta el selector de la agenda, y la vista lo dice explicitamente.
      - Un 409 del backend (ultimo administrador activo, baja de la propia
        cuenta) se explica en un aviso visible, no solo en un toast.

   2. Roles y permisos
      - Tabla de roles con su cantidad de permisos y la marca de rol base.
      - Al elegir un rol se abre su matriz: TODOS los permisos del catalogo
        (GET /api/permisos) agrupados por `modulo`, con su descripcion.
      - Guardar envia el conjunto completo: el backend reemplaza la matriz.
      - Los tres roles base no se renombran ni se eliminan, pero su matriz si es
        editable; ROLE_ADMINISTRADOR conserva siempre todos los permisos.

   Permisos: la ruta entera exige OP_USUARIOS_ADMIN, asi que las acciones se
   habilitan con ese mismo permiso.

   Endpoints consumidos
   - GET    /api/usuarios?rol=&estado=&q=
   - POST   /api/usuarios, PUT /api/usuarios/{id}, DELETE /api/usuarios/{id}
   - GET    /api/roles, POST /api/roles, PUT /api/roles/{id}, DELETE /api/roles/{id}
   - GET    /api/permisos
   ============================================================================ */
import { AuthService } from "../services/auth-service.js";
import { UsuarioService, RolService, PermisoService } from "../services/usuario-service.js";
import { NotificationService } from "../services/notification-service.js";
import { CONFIG } from "../config.js";
import {
    el, pageHeader, loadingState, errorState, table, formField, humanize, chip
} from "../core/dom.js";
import { llenarSelect } from "./realizado-form.js";

const BUSQUEDA_DEBOUNCE_MS = 350;
const EMAIL = /^[^@\s]+@[^@\s]+\.[A-Za-z]{2,}$/;
const PASSWORD_MAX = 72;

/** Roles que el backend nunca renombra ni elimina, aunque su matriz sea editable. */
const ROLES_BASE = new Set(Object.values(CONFIG.ROLES));

/** Etiqueta legible de un modulo del catalogo de permisos. */
const MODULOS = {
    PACIENTE: "Pacientes",
    CITA: "Citas",
    TRATAMIENTO: "Tratamientos",
    FACTURACION: "Facturación",
    SEGURIDAD: "Seguridad",
    REPORTE: "Reportes"
};

/** Verbo legible de la accion CRUD del permiso. */
const ACCIONES = {
    READ: "Consultar",
    CREATE: "Crear",
    UPDATE: "Editar",
    DELETE: "Eliminar"
};

export class UsuariosView {

    constructor({ route, params, navigate, query } = {}) {
        this.route = route;
        this.params = params;
        this.navigate = navigate;
        this.query = query;

        this.pestana = this.query?.get("tab") === "roles" ? "roles" : "usuarios";
        this.puedeAdministrar = AuthService.can(CONFIG.PERMISSIONS.USUARIOS_ADMIN);
        this.usuarioActual = AuthService.getUser()?.username || "";

        this.usuarios = [];
        this.roles = [];
        this.permisos = [];

        this.filtros = { rol: "", estado: "", q: "" };
        this.editando = null;
        this.guardando = false;

        this.rolEditando = null;
        this.seleccionPermisos = new Set();
        this.guardandoRol = false;

        this._debounce = null;
    }

    async mount(container) {
        this.container = container;
        container.replaceChildren(
            this.header(),
            this.barraPestanas(),
            this.seccionUsuarios(),
            this.seccionRoles()
        );

        this.mostrarPestana(this.pestana);

        if (this.puedeAdministrar && this.query?.get("nuevo") === "1") {
            this.abrirFormularioUsuario(null);
        }

        await Promise.all([
            this.cargarUsuarios(),
            this.cargarRoles(),
            this.cargarPermisos()
        ]);

        if (this.pestana === "roles" && !this.rolEditando && this.roles.length) {
            this.abrirRol(this.roles[0]);
        }

        return null;
    }

    unmount() {
        if (this._debounce) {
            clearTimeout(this._debounce);
            this._debounce = null;
        }
        this.usuarios = [];
        this.roles = [];
        this.permisos = [];
    }

    /* ------------------------------------------------------------------ *
     * Estructura general
     * ------------------------------------------------------------------ */

    header() {
        return pageHeader(
            "Usuarios y roles",
            "Padrón del personal de la clínica, alta de odontólogos para la agenda y matriz de permisos por rol."
        );
    }

    barraPestanas() {
        this.tabUsuarios = el("button", {
            type: "button",
            class: "btn btn-primary",
            id: "tab-usuarios",
            text: "Usuarios",
            on: { click: () => this.mostrarPestana("usuarios") }
        });
        this.tabRoles = el("button", {
            type: "button",
            class: "btn btn-outline",
            id: "tab-roles",
            text: "Roles y permisos",
            on: { click: () => this.mostrarPestana("roles") }
        });

        return el("div", { class: "toolbar", id: "tabs-usuarios-roles" }, [this.tabUsuarios, this.tabRoles]);
    }

    mostrarPestana(nombre) {
        this.pestana = nombre === "roles" ? "roles" : "usuarios";

        const enUsuarios = this.pestana === "usuarios";
        this.tabUsuarios.className = enUsuarios ? "btn btn-primary" : "btn btn-outline";
        this.tabRoles.className = enUsuarios ? "btn btn-outline" : "btn btn-primary";
        this.seccionUsuariosEl.hidden = !enUsuarios;
        this.seccionRolesEl.hidden = enUsuarios;

        if (!enUsuarios && !this.rolEditando && this.roles.length) {
            this.abrirRol(this.roles[0]);
        }
    }

    /* ------------------------------------------------------------------ *
     * Pestana 1: usuarios
     * ------------------------------------------------------------------ */

    seccionUsuarios() {
        this.seccionUsuariosEl = el("div", { id: "seccion-usuarios" }, [
            this.avisoOdontologos(),
            this.cardFiltrosUsuarios(),
            this.cardListaUsuarios(),
            this.cardFormularioUsuario()
        ]);
        return this.seccionUsuariosEl;
    }

    /** S20: deja explicito que el padron es lo que alimenta la agenda. */
    avisoOdontologos() {
        const acciones = [];
        if (AuthService.can(CONFIG.PERMISSIONS.CITA_VER)) {
            acciones.push(el("a", {
                class: "btn btn-sm btn-outline mt-1",
                id: "link-ir-agenda",
                href: "#/agenda",
                text: "Ir a la agenda de citas"
            }));
        }

        return el("div", { class: "alert-box info", id: "aviso-odontologos-usuarios" }, [
            el("p", {
                class: "mb-0",
                text: "Los usuarios activos con rol Odontólogo son los que aparecen en el selector de odontólogos de la agenda: crear uno acá es lo que habilita la programación de citas."
            }),
            ...acciones
        ]);
    }

    cardFiltrosUsuarios() {
        this.filtroRol = el("select", {
            id: "filtro-usuario-rol",
            class: "form-control",
            on: { change: () => this.aplicarFiltros() }
        });
        llenarSelect(this.filtroRol, "Todos los roles", []);

        this.filtroEstado = el("select", {
            id: "filtro-usuario-estado",
            class: "form-control",
            on: { change: () => this.aplicarFiltros() }
        });
        llenarSelect(this.filtroEstado, "Todos los estados", [
            { value: "activo", label: "Activos" },
            { value: "inactivo", label: "Inactivos" }
        ]);

        this.filtroTexto = el("input", {
            type: "search",
            id: "filtro-usuario-texto",
            class: "form-control",
            placeholder: "Buscar por usuario, nombre o correo",
            autocomplete: "off",
            on: {
                input: () => {
                    if (this._debounce) clearTimeout(this._debounce);
                    this._debounce = setTimeout(() => this.aplicarFiltros(), BUSQUEDA_DEBOUNCE_MS);
                },
                keydown: event => {
                    if (event.key === "Enter") {
                        event.preventDefault();
                        if (this._debounce) clearTimeout(this._debounce);
                        this.aplicarFiltros();
                    }
                }
            }
        });

        return el("div", { class: "card", id: "card-filtros-usuarios" }, [
            el("div", { class: "toolbar" }, [
                el("div", { class: "form-group mb-0" }, [
                    el("label", { for: "filtro-usuario-rol", text: "Rol" }),
                    this.filtroRol
                ]),
                el("div", { class: "form-group mb-0" }, [
                    el("label", { for: "filtro-usuario-estado", text: "Estado" }),
                    this.filtroEstado
                ]),
                el("div", { class: "form-group toolbar-grow mb-0" }, [
                    el("label", { for: "filtro-usuario-texto", text: "Buscar" }),
                    this.filtroTexto
                ]),
                el("button", {
                    type: "button",
                    class: "btn btn-primary",
                    id: "btn-filtrar-usuarios",
                    text: "Filtrar",
                    on: { click: () => this.aplicarFiltros() }
                }),
                el("button", {
                    type: "button",
                    class: "btn btn-outline",
                    id: "btn-odontologos-activos",
                    text: "Ver odontólogos activos",
                    on: { click: () => this.filtrarOdontologosActivos() }
                }),
                el("button", {
                    type: "button",
                    class: "btn btn-ghost",
                    id: "btn-limpiar-filtros-usuarios",
                    text: "Limpiar",
                    on: { click: () => this.limpiarFiltros() }
                })
            ])
        ]);
    }

    cardListaUsuarios() {
        this.listaUsuarios = el("div", { id: "lista-usuarios" }, [
            loadingState("Cargando usuarios…")
        ]);
        this.contadorUsuarios = el("span", {
            class: "badge badge-primary",
            id: "contador-usuarios",
            text: "0 usuarios"
        });
        this.avisoUsuarios = el("div", {
            class: "alert-box danger",
            id: "aviso-usuarios",
            hidden: true
        });

        const acciones = [this.contadorUsuarios];
        if (this.puedeAdministrar) {
            acciones.push(el("button", {
                type: "button",
                class: "btn btn-sm btn-primary",
                id: "btn-nuevo-usuario",
                text: "Nuevo usuario",
                on: { click: () => this.abrirFormularioUsuario(null) }
            }));
        }

        return el("div", { class: "card", id: "card-usuarios" }, [
            el("div", { class: "card-header" }, [
                el("h2", { text: "Usuarios del sistema" }),
                el("div", { class: "card-header-actions" }, acciones)
            ]),
            this.avisoUsuarios,
            this.listaUsuarios
        ]);
    }

    cardFormularioUsuario() {
        if (!this.puedeAdministrar) {
            this.panelUsuarioForm = el("div", { hidden: true });
            return this.panelUsuarioForm;
        }

        this.tituloUsuarioForm = el("h2", { text: "Nuevo usuario" });
        this.errorUsuarioForm = el("div", {
            class: "alert-box danger",
            id: "form-usuario-error",
            hidden: true
        });

        this.camposUsuario = {
            username: el("input", {
                type: "text",
                id: "input-usuario-username",
                class: "form-control",
                maxlength: "80",
                autocomplete: "off"
            }),
            fullName: el("input", {
                type: "text",
                id: "input-usuario-nombre",
                class: "form-control",
                maxlength: "120",
                autocomplete: "off"
            }),
            email: el("input", {
                type: "email",
                id: "input-usuario-email",
                class: "form-control",
                maxlength: "100",
                autocomplete: "off"
            }),
            password: el("input", {
                type: "password",
                id: "input-usuario-password",
                class: "form-control",
                maxlength: String(PASSWORD_MAX),
                autocomplete: "new-password"
            }),
            rol: el("select", { id: "select-usuario-rol", class: "form-control" })
        };
        llenarSelect(this.camposUsuario.rol, "Seleccioná un rol", this.opcionesRol());

        this.formUsuario = el("form", {
            id: "form-usuario",
            novalidate: true,
            on: { submit: event => this.guardarUsuario(event) }
        }, [
            el("div", { class: "form-grid" }, [
                formField("Usuario", this.camposUsuario.username, {
                    required: true,
                    hint: "Nombre de acceso, sin espacios (ejemplo: jperez)."
                }),
                formField("Nombre completo", this.camposUsuario.fullName, { required: true }),
                formField("Correo electrónico", this.camposUsuario.email, { required: true }),
                formField("Contraseña", this.camposUsuario.password, {
                    hint: "Obligatoria al crear. Al editar, dejala vacía para conservar la actual."
                }),
                formField("Rol", this.camposUsuario.rol, {
                    required: true,
                    hint: "Si elegís Odontólogo, el usuario activo se habilita en el selector de la agenda de citas."
                })
            ]),
            this.errorUsuarioForm,
            el("div", { class: "form-actions" }, [
                el("button", {
                    type: "submit",
                    class: "btn btn-primary",
                    id: "btn-guardar-usuario",
                    text: "Guardar usuario"
                }),
                el("button", {
                    type: "button",
                    class: "btn btn-outline",
                    id: "btn-cancelar-usuario",
                    text: "Cancelar",
                    on: { click: () => this.cerrarFormularioUsuario() }
                })
            ])
        ]);

        this.panelUsuarioForm = el("div", {
            class: "card form-panel",
            id: "panel-usuario-form",
            hidden: true
        }, [
            el("div", { class: "card-header" }, [this.tituloUsuarioForm]),
            this.formUsuario
        ]);

        return this.panelUsuarioForm;
    }

    /* ------------------------------------------------------------------ *
     * Pestana 2: roles y matriz de permisos
     * ------------------------------------------------------------------ */

    seccionRoles() {
        this.seccionRolesEl = el("div", { id: "seccion-roles", hidden: true }, [
            this.cardListaRoles(),
            this.cardMatrizRol()
        ]);
        return this.seccionRolesEl;
    }

    cardListaRoles() {
        this.listaRoles = el("div", { id: "lista-roles" }, [loadingState("Cargando roles…")]);
        this.contadorRoles = el("span", { class: "badge badge-primary", id: "contador-roles", text: "0 roles" });

        const acciones = [this.contadorRoles];
        if (this.puedeAdministrar) {
            acciones.push(el("button", {
                type: "button",
                class: "btn btn-sm btn-primary",
                id: "btn-nuevo-rol",
                text: "Nuevo rol",
                on: { click: () => this.abrirRol(null) }
            }));
        }

        return el("div", { class: "card", id: "card-roles" }, [
            el("div", { class: "card-header" }, [
                el("h2", { text: "Roles del sistema" }),
                el("div", { class: "card-header-actions" }, acciones)
            ]),
            el("p", {
                class: "text-muted text-sm",
                text: "Elegí un rol para editar su matriz de permisos. Los roles base no se renombran ni se eliminan."
            }),
            this.listaRoles
        ]);
    }

    cardMatrizRol() {
        this.tituloRolForm = el("h2", { text: "Matriz de permisos" });
        this.errorRol = el("div", { class: "alert-box danger", id: "form-rol-error", hidden: true });
        this.avisoRolBase = el("div", { class: "alert-box info", id: "rol-base-aviso", hidden: true });
        this.contadorPermisos = el("span", {
            class: "badge badge-primary",
            id: "contador-permisos-rol",
            text: "0 permisos"
        });

        this.camposRol = {
            roleName: el("input", {
                type: "text",
                id: "input-rol-nombre",
                class: "form-control",
                maxlength: "60",
                placeholder: "ENFERMERIA",
                autocomplete: "off"
            }),
            description: el("input", {
                type: "text",
                id: "input-rol-descripcion",
                class: "form-control",
                maxlength: "200",
                autocomplete: "off"
            })
        };

        this.matrizPermisos = el("div", { class: "check-list", id: "matriz-permisos" }, [
            loadingState("Cargando catálogo de permisos…")
        ]);

        this.btnPermisosTodos = el("button", {
            type: "button",
            class: "btn btn-sm btn-outline",
            id: "btn-permisos-todos",
            text: "Seleccionar todos",
            on: { click: () => this.marcarTodosLosPermisos(true) }
        });
        this.btnPermisosNinguno = el("button", {
            type: "button",
            class: "btn btn-sm btn-outline",
            id: "btn-permisos-ninguno",
            text: "Quitar todos",
            on: { click: () => this.marcarTodosLosPermisos(false) }
        });

        this.btnEliminarRol = el("button", {
            type: "button",
            class: "btn btn-danger",
            id: "btn-eliminar-rol",
            text: "Eliminar rol",
            hidden: true,
            on: { click: () => this.eliminarRol() }
        });

        this.formRol = el("form", {
            id: "form-rol",
            novalidate: true,
            on: { submit: event => this.guardarRol(event) }
        }, [
            el("div", { class: "form-grid" }, [
                formField("Nombre del rol", this.camposRol.roleName, {
                    required: true,
                    hint: "Se guarda con el prefijo del sistema: ROLE_NOMBRE."
                }),
                formField("Descripción", this.camposRol.description, {
                    hint: "Para qué sirve este rol en la clínica."
                })
            ]),
            this.avisoRolBase,
            el("div", { class: "form-group" }, [
                el("label", { text: "Permisos del rol", class: "is-required" }),
                el("p", {
                    class: "field-hint",
                    text: "La matriz se guarda completa: lo que quede sin marcar se quita del rol."
                }),
                el("div", { class: "toolbar" }, [
                    this.contadorPermisos,
                    this.btnPermisosTodos,
                    this.btnPermisosNinguno
                ]),
                this.matrizPermisos
            ]),
            el("p", {
                class: "field-hint",
                text: "Los cambios de permisos rigen en el próximo inicio de sesión de cada usuario: el token vigente conserva los permisos con los que fue emitido."
            }),
            this.errorRol,
            el("div", { class: "form-actions" }, [
                this.btnEliminarRol,
                el("button", {
                    type: "button",
                    class: "btn btn-outline",
                    id: "btn-cancelar-rol",
                    text: "Cerrar",
                    on: { click: () => this.cerrarPanelRol() }
                }),
                el("button", {
                    type: "submit",
                    class: "btn btn-primary",
                    id: "btn-guardar-rol",
                    text: "Guardar matriz"
                })
            ])
        ]);

        this.panelRolForm = el("div", {
            class: "card form-panel",
            id: "panel-rol-permisos",
            hidden: true
        }, [
            el("div", { class: "card-header" }, [this.tituloRolForm]),
            this.formRol
        ]);

        return this.panelRolForm;
    }

    /* ------------------------------------------------------------------ *
     * Datos
     * ------------------------------------------------------------------ */

    async cargarUsuarios() {
        this.listaUsuarios.replaceChildren(loadingState("Cargando usuarios…"));
        try {
            this.usuarios = await UsuarioService.listar(this.filtros);
            this.renderUsuarios();
        } catch (error) {
            this.contadorUsuarios.textContent = "Sin datos";
            this.listaUsuarios.replaceChildren(errorState(error.message));
        }
    }

    async cargarRoles() {
        this.listaRoles.replaceChildren(loadingState("Cargando roles…"));
        try {
            this.roles = await RolService.listar();
            this.renderRoles();
        } catch (error) {
            this.roles = [];
            this.contadorRoles.textContent = "Sin datos";
            this.listaRoles.replaceChildren(errorState(error.message));
        }
        this.rellenarSelectsDeRol();
    }

    async cargarPermisos() {
        if (this.permisos.length) return;
        try {
            this.permisos = await PermisoService.listar();
        } catch (error) {
            this.permisos = [];
            this.permitirMatriz(false, error.message);
            return;
        }
        if (this.rolEditando) {
            this.renderMatrizPermisos();
        }
    }

    /** Filtros aplicados en el backend: rol, estado y texto libre. */
    aplicarFiltros() {
        this.filtros = {
            rol: this.filtroRol.value,
            estado: this.filtroEstado.value,
            q: this.filtroTexto.value.trim()
        };
        this.cargarUsuarios();
    }

    filtrarOdontologosActivos() {
        this.filtroRol.value = CONFIG.ROLES.DENTISTA;
        this.filtroEstado.value = "activo";
        this.filtroTexto.value = "";
        this.aplicarFiltros();
    }

    limpiarFiltros() {
        this.filtroRol.value = "";
        this.filtroEstado.value = "";
        this.filtroTexto.value = "";
        this.aplicarFiltros();
    }

    /* ------------------------------------------------------------------ *
     * Tabla de usuarios
     * ------------------------------------------------------------------ */

    renderUsuarios() {
        const conFiltros = Boolean(this.filtros.rol || this.filtros.estado || this.filtros.q);
        this.contadorUsuarios.textContent = conFiltros
            ? `${this.usuarios.length} resultado(s)`
            : `${this.usuarios.length} usuario(s)`;

        const rows = this.usuarios.map(usuario => {
            const activo = usuario.activo !== false;
            const odontologoActivo = activo && usuario.rol === CONFIG.ROLES.DENTISTA;

            const acciones = [];
            if (this.puedeAdministrar) {
                acciones.push(el("button", {
                    type: "button",
                    class: "btn btn-sm btn-outline",
                    text: "Editar",
                    dataset: { accion: "editar-usuario", id: usuario.id },
                    on: { click: () => this.abrirFormularioUsuario(usuario) }
                }));
                if (activo) {
                    acciones.push(el("button", {
                        type: "button",
                        class: "btn btn-sm btn-danger",
                        text: "Dar de baja",
                        dataset: { accion: "baja-usuario", id: usuario.id },
                        on: { click: () => this.darDeBajaUsuario(usuario) }
                    }));
                }
            }

            return [
                el("div", { class: "inline-actions" }, [
                    el("span", { class: "cell-strong", text: usuario.username }),
                    usuario.username === this.usuarioActual ? chip("Tu usuario") : null
                ].filter(Boolean)),
                usuario.fullName || usuario.nombre || "—",
                usuario.email || "—",
                el("span", { class: "badge badge-primary", text: etiquetaRol(usuario.rol) }),
                el("span", {
                    class: activo ? "badge badge-success" : "badge badge-neutral",
                    text: activo ? "Activo" : "Inactivo"
                }),
                odontologoActivo
                    ? el("span", { class: "badge badge-success", text: "Disponible en agenda" })
                    : el("span", {
                        class: "text-muted text-sm",
                        text: activo ? "—" : "Dado de baja"
                    }),
                el("div", { class: "inline-actions" }, acciones.length
                    ? acciones
                    : [el("span", { class: "text-muted text-sm", text: "Solo lectura" })])
            ];
        });

        this.listaUsuarios.replaceChildren(table(
            ["Usuario", "Nombre", "Correo", "Rol", "Estado", "Odontólogo", "Acciones"],
            rows,
            {
                emptyMessage: conFiltros
                    ? "Ningún usuario coincide con los filtros aplicados."
                    : "Todavía no hay usuarios registrados."
            }
        ));
    }

    /* ------------------------------------------------------------------ *
     * Alta y edicion de usuarios (S18)
     * ------------------------------------------------------------------ */

    abrirFormularioUsuario(usuario) {
        if (!this.puedeAdministrar) return;

        this.editando = usuario || null;
        this.tituloUsuarioForm.textContent = usuario ? "Editar usuario" : "Nuevo usuario";
        this.ocultarErrorUsuario();

        this.rellenarSelectRolFormulario(usuario?.rol || "");
        this.camposUsuario.username.value = usuario?.username || "";
        this.camposUsuario.fullName.value = usuario?.fullName || usuario?.nombre || "";
        this.camposUsuario.email.value = usuario?.email || "";
        this.camposUsuario.password.value = "";

        this.panelUsuarioForm.hidden = false;
        this.panelUsuarioForm.scrollIntoView({ behavior: "smooth", block: "nearest" });
        this.camposUsuario.username.focus();
    }

    cerrarFormularioUsuario() {
        this.editando = null;
        if (!this.formUsuario) return;
        this.panelUsuarioForm.hidden = true;
        this.formUsuario.reset();
        this.ocultarErrorUsuario();
    }

    leerFormularioUsuario() {
        return {
            username: this.camposUsuario.username.value.trim(),
            fullName: this.camposUsuario.fullName.value.trim(),
            email: this.camposUsuario.email.value.trim(),
            password: this.camposUsuario.password.value,
            rol: this.camposUsuario.rol.value
        };
    }

    /** Validacion de conveniencia: la autoridad sigue siendo el backend. */
    validarUsuario(datos) {
        if (!datos.username) return "El usuario es obligatorio.";
        if (!datos.fullName) return "El nombre completo es obligatorio.";
        if (!datos.email) return "El correo electrónico es obligatorio.";
        if (!EMAIL.test(datos.email)) {
            return "El correo electrónico no tiene un formato válido (ejemplo: nombre@dominio.com).";
        }
        if (!this.editando && !datos.password) {
            return "La contraseña es obligatoria para crear un usuario.";
        }
        if (datos.password && datos.password.length > PASSWORD_MAX) {
            return `La contraseña no puede superar los ${PASSWORD_MAX} caracteres.`;
        }
        if (!datos.rol) return "Seleccioná el rol del usuario.";
        return null;
    }

    async guardarUsuario(event) {
        event.preventDefault();
        if (this.guardando || !this.puedeAdministrar) return;

        const datos = this.leerFormularioUsuario();
        const error = this.validarUsuario(datos);
        if (error) {
            this.mostrarErrorUsuario(error);
            return;
        }

        this.guardando = true;
        this.bloquearBotonGuardarUsuario(true);
        this.ocultarErrorUsuario();

        try {
            if (this.editando) {
                await UsuarioService.actualizar(this.editando.id, datos);
                NotificationService.success("Usuario actualizado correctamente.");
            } else {
                const creado = await UsuarioService.crear(datos);
                NotificationService.success(esOdontologo(creado)
                    ? `Odontólogo ${creado.fullName} creado. Ya está disponible en el selector de la agenda.`
                    : "Usuario creado correctamente.");
            }
            this.ocultarAvisoUsuarios();
            this.cerrarFormularioUsuario();
            await this.cargarUsuarios();
        } catch (respuesta) {
            // 409 de username o email duplicado: el formulario queda intacto.
            this.mostrarErrorUsuario(respuesta.message);
        } finally {
            this.guardando = false;
            this.bloquearBotonGuardarUsuario(false);
        }
    }

    mostrarErrorUsuario(mensaje) {
        this.errorUsuarioForm.textContent = mensaje;
        this.errorUsuarioForm.hidden = false;
    }

    ocultarErrorUsuario() {
        this.errorUsuarioForm.hidden = true;
        this.errorUsuarioForm.textContent = "";
    }

    bloquearBotonGuardarUsuario(bloquear) {
        const boton = this.formUsuario?.querySelector("#btn-guardar-usuario");
        if (!boton) return;
        boton.disabled = bloquear;
        boton.textContent = bloquear ? "Guardando…" : "Guardar usuario";
    }

    /* ------------------------------------------------------------------ *
     * Baja logica (S18)
     * ------------------------------------------------------------------ */

    async darDeBajaUsuario(usuario) {
        if (!this.puedeAdministrar) return;

        const propias = usuario.username === this.usuarioActual;
        const confirmado = await NotificationService.confirm(
            propias
                ? `Estás por dar de baja tu propia cuenta (${usuario.username}). El sistema rechaza esta operación para no dejarte sin acceso.`
                : `¿Dar de baja a ${usuario.fullName || usuario.username}? Queda inactivo y deja de aparecer en los selectores, pero conserva su historial.`,
            { confirmLabel: "Dar de baja", cancelLabel: "Cancelar" }
        );
        if (!confirmado) return;

        try {
            await UsuarioService.darDeBaja(usuario.id);
            if (this.editando?.id === usuario.id) {
                this.cerrarFormularioUsuario();
            }
            this.ocultarAvisoUsuarios();
            NotificationService.success("Usuario dado de baja. Su historial quedó intacto.");
            await this.cargarUsuarios();
        } catch (error) {
            NotificationService.error(error.message);
            this.mostrarAvisoUsuarios(error);
        }
    }

    /**
     * Explica en pantalla los 409 del backend, que son reglas de negocio y no
     * fallas: sin administrador activo o baja de la propia cuenta.
     */
    mostrarAvisoUsuarios(error) {
        const detalle = error?.status === 409
            ? "La baja no se aplicó: el sistema conserva siempre al menos un administrador activo y ninguna cuenta puede darse de baja a sí misma."
            : "La operación no se pudo completar.";

        this.avisoUsuarios.replaceChildren(
            el("p", { class: "mb-0 font-bold", text: error?.message || detalle }),
            el("p", { class: "text-sm mb-0 mt-1", text: detalle })
        );
        this.avisoUsuarios.hidden = false;
    }

    ocultarAvisoUsuarios() {
        this.avisoUsuarios.hidden = true;
        this.avisoUsuarios.replaceChildren();
    }

    /* ------------------------------------------------------------------ *
     * Tabla de roles
     * ------------------------------------------------------------------ */

    renderRoles() {
        this.contadorRoles.textContent = `${this.roles.length} rol(es)`;

        const rows = this.roles.map(rol => {
            const base = esRolBase(rol.roleName);
            const permisos = Array.isArray(rol.permissions) ? rol.permissions.length : 0;

            const acciones = [
                el("button", {
                    type: "button",
                    class: "btn btn-sm btn-outline",
                    text: "Editar matriz",
                    dataset: { accion: "editar-rol", id: rol.id },
                    on: { click: () => this.abrirRol(rol) }
                })
            ];
            if (this.puedeAdministrar && !base) {
                acciones.push(el("button", {
                    type: "button",
                    class: "btn btn-sm btn-danger",
                    text: "Eliminar",
                    dataset: { accion: "eliminar-rol", id: rol.id },
                    on: { click: () => this.eliminarRol(rol) }
                }));
            }

            return [
                el("div", {}, [
                    el("div", { class: "cell-strong", text: etiquetaRol(rol.roleName) }),
                    el("div", { class: "text-muted text-sm", text: rol.roleName })
                ]),
                rol.description || "—",
                el("span", { class: "badge badge-primary", text: `${permisos} permiso(s)` }),
                base
                    ? el("span", { class: "badge badge-warning", text: "Rol base" })
                    : el("span", { class: "badge badge-neutral", text: "Personalizado" }),
                el("div", { class: "inline-actions" }, acciones)
            ];
        });

        this.listaRoles.replaceChildren(table(
            ["Rol", "Descripción", "Permisos", "Tipo", "Acciones"],
            rows,
            { emptyMessage: "Todavía no hay roles definidos." }
        ));
    }

    /* ------------------------------------------------------------------ *
     * Matriz de permisos (S18)
     * ------------------------------------------------------------------ */

    abrirRol(rol) {
        const edicion = Boolean(rol);
        const base = edicion && esRolBase(rol.roleName);
        const administrador = edicion && rol.roleName === CONFIG.ROLES.ADMINISTRADOR;

        this.rolEditando = rol || null;
        this.seleccionPermisos = administrador
            ? new Set(this.permisos.map(permiso => permiso.permissionName))
            : new Set(Array.isArray(rol?.permissions) ? rol.permissions : []);

        this.tituloRolForm.textContent = edicion
            ? `Matriz de permisos · ${etiquetaRol(rol.roleName)}`
            : "Nuevo rol";
        this.camposRol.roleName.value = rol?.roleName || "";
        this.camposRol.roleName.disabled = base;
        this.camposRol.description.value = rol?.description || "";

        this.avisoRolBase.hidden = !base;
        if (base) {
            this.avisoRolBase.textContent = administrador
                ? "Rol base del sistema: no se puede renombrar ni eliminar, y conserva siempre todos los permisos del catálogo."
                : "Rol base del sistema: no se puede renombrar ni eliminar, pero su matriz de permisos sí es editable.";
        }

        this.btnEliminarRol.hidden = !edicion || base;
        this.btnPermisosTodos.disabled = administrador || !this.permisos.length;
        this.btnPermisosNinguno.disabled = administrador || !this.permisos.length;

        this.errorRol.hidden = true;
        this.renderMatrizPermisos();

        this.panelRolForm.hidden = false;
        this.panelRolForm.scrollIntoView({ behavior: "smooth", block: "nearest" });
    }

    cerrarPanelRol() {
        this.rolEditando = null;
        this.seleccionPermisos = new Set();
        this.panelRolForm.hidden = true;
        this.formRol.reset();
        this.errorRol.hidden = true;
        this.avisoRolBase.hidden = true;
    }

    /** Checklist de TODOS los permisos del catalogo, agrupados por modulo. */
    renderMatrizPermisos() {
        if (!this.permisos.length) {
            this.matrizPermisos.replaceChildren(el("p", {
                class: "text-muted text-sm mb-0",
                text: "No se pudo cargar el catálogo de permisos. Volvé a entrar a la pantalla para reintentar."
            }));
            this.actualizarContadorPermisos();
            return;
        }

        const administrador = this.rolEditando?.roleName === CONFIG.ROLES.ADMINISTRADOR;
        if (administrador) {
            // El backend fuerza la matriz completa: el contador y el guardado lo reflejan.
            this.seleccionPermisos = new Set(this.permisos.map(permiso => permiso.permissionName));
        }
        const bloques = [];

        for (const grupo of agruparPermisos(this.permisos)) {
            bloques.push(el("div", {
                class: "text-muted text-sm font-bold",
                dataset: { modulo: grupo.modulo },
                text: etiquetaModulo(grupo.modulo)
            }));

            for (const permiso of grupo.permisos) {
                const id = `permiso-${permiso.permissionName}`;
                const checkbox = el("input", {
                    type: "checkbox",
                    id,
                    dataset: { permissionName: permiso.permissionName, modulo: grupo.modulo },
                    on: {
                        change: event => this.alternarPermiso(permiso.permissionName, event.target.checked)
                    }
                });
                checkbox.checked = administrador || this.seleccionPermisos.has(permiso.permissionName);
                checkbox.disabled = administrador;

                bloques.push(el("label", { class: "check-row", for: id }, [
                    checkbox,
                    el("span", { class: "check-row-main" }, [
                        el("span", { class: "check-row-title", text: permiso.permissionName }),
                        el("span", {
                            class: "text-muted text-sm",
                            text: permiso.description || "Sin descripción."
                        })
                    ]),
                    el("span", { class: "check-row-amount" }, [
                        chip(etiquetaAccion(permiso.action))
                    ])
                ]));
            }
        }

        this.matrizPermisos.replaceChildren(...bloques);
        this.actualizarContadorPermisos();
    }

    /** Muestra un error persistente en la matriz cuando no hay catalogo. */
    permitirMatriz(habilitada, mensaje) {
        this.btnPermisosTodos.disabled = !habilitada;
        this.btnPermisosNinguno.disabled = !habilitada;
        if (!habilitada) {
            this.matrizPermisos.replaceChildren(el("p", {
                class: "text-muted text-sm mb-0",
                text: `No se pudo cargar el catálogo de permisos: ${mensaje}`
            }));
        }
    }

    alternarPermiso(nombre, marcado) {
        if (marcado) {
            this.seleccionPermisos.add(nombre);
        } else {
            this.seleccionPermisos.delete(nombre);
        }
        this.actualizarContadorPermisos();
    }

    marcarTodosLosPermisos(marcado) {
        if (this.rolEditando?.roleName === CONFIG.ROLES.ADMINISTRADOR) return;

        this.seleccionPermisos = marcado
            ? new Set(this.permisos.map(permiso => permiso.permissionName))
            : new Set();
        this.renderMatrizPermisos();
    }

    actualizarContadorPermisos() {
        this.contadorPermisos.textContent = this.permisos.length
            ? `${this.seleccionPermisos.size} de ${this.permisos.length} permiso(s)`
            : "0 permisos";
    }

    async guardarRol(event) {
        event.preventDefault();
        if (this.guardandoRol || !this.puedeAdministrar) return;

        const roleName = this.camposRol.roleName.value.trim();
        const description = this.camposRol.description.value.trim();
        if (!roleName) {
            this.mostrarErrorRol("El nombre del rol es obligatorio.");
            return;
        }

        const permissions = [...this.seleccionPermisos].sort();
        const idPrevio = this.rolEditando?.id || null;

        this.guardandoRol = true;
        this.bloquearBotonGuardarRol(true);
        this.errorRol.hidden = true;

        try {
            if (idPrevio) {
                await RolService.actualizar(idPrevio, { roleName, description, permissions });
                NotificationService.success("Matriz de permisos actualizada. Rige en el próximo inicio de sesión.");
            } else {
                await RolService.crear({ roleName, description, permissions });
                NotificationService.success("Rol creado con su matriz de permisos.");
            }

            await this.cargarRoles();
            const recargado = idPrevio
                ? this.roles.find(rol => rol.id === idPrevio)
                : this.roles.find(rol => rol.roleName === normalizarNombreRol(roleName));
            if (recargado) {
                this.abrirRol(recargado);
            } else {
                this.cerrarPanelRol();
            }
        } catch (respuesta) {
            // 400 por un permiso desconocido, 409 por nombre duplicado: se muestra
            // tal cual y la matriz queda como estaba para corregirla.
            this.mostrarErrorRol(respuesta.message);
        } finally {
            this.guardandoRol = false;
            this.bloquearBotonGuardarRol(false);
        }
    }

    async eliminarRol(rol = null) {
        const objetivo = rol || this.rolEditando;
        if (!objetivo || !this.puedeAdministrar) return;

        if (esRolBase(objetivo.roleName)) {
            this.mostrarErrorRol("Los roles base del sistema no pueden eliminarse.");
            return;
        }

        const confirmado = await NotificationService.confirm(
            `¿Eliminar el rol ${etiquetaRol(objetivo.roleName)}? Solo se puede si no tiene usuarios asignados.`,
            { confirmLabel: "Eliminar rol", cancelLabel: "Cancelar" }
        );
        if (!confirmado) return;

        try {
            await RolService.eliminar(objetivo.id);
            NotificationService.success("Rol eliminado.");
            if (this.rolEditando?.id === objetivo.id) {
                this.cerrarPanelRol();
            }
            await this.cargarRoles();
        } catch (respuesta) {
            // 409 si el rol tiene usuarios o es base.
            NotificationService.error(respuesta.message);
            this.mostrarErrorRol(respuesta.message);
        }
    }

    mostrarErrorRol(mensaje) {
        this.errorRol.textContent = mensaje;
        this.errorRol.hidden = false;
    }

    bloquearBotonGuardarRol(bloquear) {
        const boton = this.formRol?.querySelector("#btn-guardar-rol");
        if (!boton) return;
        boton.disabled = bloquear;
        boton.textContent = bloquear ? "Guardando…" : "Guardar matriz";
    }

    /* ------------------------------------------------------------------ *
     * Catalogos de roles en los selects
     * ------------------------------------------------------------------ */

    /** Roles reales del backend; si la carga falla, los tres roles base del contrato. */
    opcionesRol() {
        if (this.roles.length) {
            return this.roles.map(rol => ({
                value: rol.roleName,
                label: etiquetaRol(rol.roleName)
            }));
        }
        return Object.values(CONFIG.ROLES).map(roleName => ({
            value: roleName,
            label: etiquetaRol(roleName)
        }));
    }

    rellenarSelectsDeRol() {
        if (!this.filtroRol) return;

        const rolFiltrado = this.filtros.rol;
        llenarSelect(this.filtroRol, "Todos los roles", this.opcionesRol());
        this.filtroRol.value = this.opcionesRol().some(opcion => opcion.value === rolFiltrado)
            ? rolFiltrado
            : "";

        if (!this.panelUsuarioForm || this.panelUsuarioForm.hidden) return;
        this.rellenarSelectRolFormulario(this.camposUsuario.rol.value);
    }

    /** Opciones del select del formulario, conservando la seleccion vigente. */
    rellenarSelectRolFormulario(valor) {
        const opciones = this.opcionesRol();
        llenarSelect(this.camposUsuario.rol, "Seleccioná un rol", opciones);
        this.camposUsuario.rol.value = opciones.some(opcion => opcion.value === valor) ? valor : "";
    }
}

/* ----------------------------------------------------------------------------
   Helpers de presentacion
   ---------------------------------------------------------------------------- */

/** Etiqueta legible de un rol: la del contrato o el nombre sin el prefijo ROLE_. */
function etiquetaRol(roleName) {
    if (!roleName) return "—";
    return CONFIG.ROLE_LABELS[roleName] || humanize(String(roleName).replace(/^ROLE_/, ""));
}

function esRolBase(roleName) {
    return ROLES_BASE.has(String(roleName || "").toUpperCase());
}

function esOdontologo(usuario) {
    return usuario?.rol === CONFIG.ROLES.DENTISTA;
}

function normalizarNombreRol(roleName) {
    const limpio = String(roleName || "").trim().toUpperCase();
    return limpio.startsWith("ROLE_") ? limpio : `ROLE_${limpio}`;
}

function etiquetaModulo(modulo) {
    if (!modulo) return "Otros";
    return MODULOS[String(modulo).toUpperCase()] || humanize(modulo);
}

function etiquetaAccion(accion) {
    if (!accion) return "—";
    return ACCIONES[String(accion).toUpperCase()] || humanize(accion);
}

/** Agrupa el catalogo plano del backend por `modulo`, con orden estable. */
function agruparPermisos(permisos) {
    const grupos = new Map();
    for (const permiso of permisos) {
        const modulo = permiso.modulo || "OTROS";
        if (!grupos.has(modulo)) grupos.set(modulo, []);
        grupos.get(modulo).push(permiso);
    }

    return [...grupos.entries()]
        .map(([modulo, items]) => ({
            modulo,
            permisos: items.sort((a, b) =>
                String(a.permissionName).localeCompare(String(b.permissionName), "es"))
        }))
        .sort((a, b) => a.modulo.localeCompare(b.modulo, "es"));
}
