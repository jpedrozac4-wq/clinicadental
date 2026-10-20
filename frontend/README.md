# Frontend del sistema de gestión — Clínica Dental

Aplicación de una sola página en JavaScript vainilla con módulos ES nativos.
**No tiene build step**: Nginx sirve estos archivos tal cual y hace de proxy
inverso hacia el backend Spring Boot bajo `/api/`.

```
frontend/
├── index.html                  SPA: compuerta de login + shell + área de trabajo
├── css/
│   ├── variables.css           Paleta y tokens de diseño de la clínica
│   ├── base.css                Reset, tipografía y utilidades base
│   ├── layout.css              Cabecera, sidebar, layout y responsive
│   ├── components.css          Componentes genéricos (botones, badges, tablas, toasts, modales)
│   ├── auth.css                Compuerta de login
│   └── clinic.css              Estilos del dominio clínico (KPIs, fichas, formularios)
└── js/
    ├── config.js               Endpoints, roles, permisos y claves de storage
    ├── clinic-app.js           Arranque: sesión guardada, shell y router
    ├── core/
    │   ├── dom.js              Helpers de DOM y formato (fechas, edad, moneda, tablas)
    │   ├── router.js           Router hash con guardas de sesión y permisos
    │   ├── routes.js           Registro de rutas + rutas reservadas
    │   └── shell.js            Sidebar por permisos, cabecera y login/logout
    ├── services/
    │   ├── api.js              Cliente HTTP central (JWT, errores, blobs)
    │   ├── auth-service.js     Sesión JWT y evaluación de RBAC
    │   ├── notification-service.js  Toasts y confirmaciones
    │   ├── paciente-service.js      /api/pacientes
    │   └── tratamiento-service.js   /api/tratamientos
    └── modules/                Una vista por ruta (ver tabla de pantallas)
```

## Arranque

```bash
docker compose up -d          # PostgreSQL + backend + Nginx
# abrir http://localhost
```

Usuarios de la semilla (`Password123!`): `admin`, `recepcion1`, `dentista1`,
`dentista2`.

## Pantallas y endpoints consumidos

| Pantalla | Ruta | Endpoints |
|----------|------|-----------|
| Login | compuerta de `index.html` | `POST /api/auth/login`, `GET /api/auth/me`, `POST /api/auth/logout` |
| Panel general | `#/dashboard` | `GET /api/pacientes`, `GET /api/tratamientos` |
| Pacientes | `#/pacientes` | `GET /api/pacientes?texto=`, `POST /api/pacientes`, `PUT /api/pacientes/{id}`, `DELETE /api/pacientes/{id}` |
| Historial clínico | `#/pacientes/{id}/historial` | `GET /api/pacientes/{id}`, `GET /api/pacientes/{id}/historial` |
| Catálogo de tratamientos | `#/tratamientos` | `GET /api/tratamientos`, `POST /api/tratamientos`, `PUT /api/tratamientos/{id}` |
| Mi cuenta | `#/cuenta` | ninguno (perfil de la sesión) |

Todas las llamadas llevan `Authorization: Bearer <jwt>` y asumen el contrato de
error del backend: `{"status":"ERROR","message":"..."}`.

## Roles y permisos

El backend entrega un JWT con las authorities `ROLE_*` y `OP_*`.
`auth-service.js` normaliza el payload (`roles` y `permissions` llegan sin
prefijo, `authorities` llega completo) a la forma con prefijo, que es la que
usan los matchers de `SecurityConfig`.

| Acción en la UI | Permiso requerido |
|-----------------|-------------------|
| Ver pacientes / historial | `OP_PACIENTE_VER` / `OP_PACIENTE_HISTORIAL_VER` |
| Registrar paciente | `OP_PACIENTE_CREAR` |
| Editar paciente | `OP_PACIENTE_EDITAR` |
| Dar de baja paciente | `OP_PACIENTE_ELIMINAR` |
| Ver catálogo de tratamientos | `OP_TRATAMIENTO_VER` |
| Crear / editar tratamiento | `OP_TRATAMIENTO_CREAR` / `OP_TRATAMIENTO_EDITAR` |

`ROLE_ADMINISTRADOR` es superusuario y ve todo, igual que en el backend. Las
acciones se ocultan (no se deshabilitan) cuando el perfil no tiene el permiso;
el backend sigue siendo la autoridad final y responde `403` si se fuerza.

## Cómo agregar la agenda, la facturación y los recordatorios

`core/routes.js` ya declara `RUTAS_RESERVADAS` con la ruta, la etiqueta, el
grupo de sidebar, el icono y los permisos de cada área pendiente. Para
habilitarlas:

1. Crear `js/modules/agenda-module.js`, `facturacion-module.js` y
   `recordatorios-module.js` con el contrato `mount(container)` / `unmount()`.
2. Mover la entrada correspondiente de `RUTAS_RESERVADAS` a `ROUTES`.

No hay que tocar el shell ni el router: el sidebar agrupa y filtra solo.

## Contrato de DOM y storage (para las pruebas de humo)

- Compuerta: `#login-gate`, `#login-form`, `#login-username`,
  `#login-password`, `#login-error`, `#btn-login`, `#login-demo-profiles`
  (`[data-demo-user]`).
- Shell: `#app-shell`, `#app-sidebar`, `#sidebar-menu`,
  `.sidebar-link[data-route-id]`, `#sidebar-toggle`, `#app-view`,
  `#current-user-name`, `#current-user-role`, `#btn-logout`.
- Vistas: `#app-view h2` es el título de la pantalla, `.card`, `.data-table`,
  `.spinner` (cargando), `.state-box-error` (error), `.toast-success` /
  `.toast-error`, `.modal-overlay.active .btn-danger` (confirmación).
- `localStorage`: `clinica.auth.token` y `clinica.auth.user`.
