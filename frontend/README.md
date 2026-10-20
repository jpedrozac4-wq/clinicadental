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
│   └── clinic.css              Estilos del dominio clínico (KPIs, fichas, formularios, check-list)
└── js/
    ├── config.js               Endpoints, roles, permisos, claves de storage y helpers de URL
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
    │   ├── tratamiento-service.js   /api/tratamientos
    │   ├── cita-service.js          /api/citas
    │   ├── realizado-service.js     /api/tratamientos-realizados
    │   ├── factura-service.js       /api/facturas y sus pagos
    │   └── dentista-service.js      catálogo de odontólogos derivado de la agenda
    └── modules/                Una vista por ruta (ver tabla de pantallas)
        └── realizado-form.js   Panel compartido: registrar tratamiento realizado (S8)
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
| Historial clínico | `#/pacientes/{id}/historial` | `GET /api/pacientes/{id}`, `GET /api/pacientes/{id}/historial`, `POST /api/tratamientos-realizados` |
| Agenda de citas | `#/agenda` | `GET /api/citas?desde=&hasta=`, `POST /api/citas`, `PATCH /api/citas/{id}/reprogramar`, `PATCH /api/citas/{id}/cancelar`, `GET /api/pacientes`, `GET /api/tratamientos`, `POST /api/tratamientos-realizados` |
| Catálogo de tratamientos | `#/tratamientos` | `GET /api/tratamientos`, `POST /api/tratamientos`, `PUT /api/tratamientos/{id}` |
| Facturación y pagos | `#/facturacion` | `GET /api/facturas`, `GET /api/facturas/{id}`, `GET /api/facturas/{id}/pdf`, `POST /api/facturas`, `GET /api/facturas/{id}/pagos`, `POST /api/facturas/{id}/pagos`, `GET /api/pacientes`, `GET /api/tratamientos-realizados?idPaciente=` |
| Mi cuenta | `#/cuenta` | ninguno (perfil de la sesión) |

Todas las llamadas llevan `Authorization: Bearer <jwt>` y asumen el contrato de
error del backend: `{"status":"ERROR","message":"..."}`. Los mensajes de
negocio se muestran **tal cual** llegan, sin reinventarlos en el cliente:

- `409` de solapamiento al programar/reprogramar y `422` de fecha pasada (S4, S6).
- `409` de tratamiento ya facturado o de otro paciente al emitir (S9).
- `409` cuando la suma de pagos supera el total o la factura está anulada (S10).

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
| Ver agenda | `OP_CITA_VER` |
| Programar cita | `OP_CITA_CREAR` |
| Reprogramar cita | `OP_CITA_REPROGRAMAR` |
| Cancelar cita | `OP_CITA_CANCELAR` |
| Registrar tratamiento realizado | `OP_REALIZADO_REGISTRAR` |
| Ver facturación y pagos | `OP_FACTURA_VER` |
| Emitir comprobante | `OP_FACTURA_EMITIR` |
| Registrar pago | `OP_PAGO_REGISTRAR` |

`ROLE_ADMINISTRADOR` es superusuario y ve todo, igual que en el backend. Las
acciones se ocultan (no se deshabilitan) cuando el perfil no tiene el permiso;
el backend sigue siendo la autoridad final y responde `403` si se fuerza.

## Vacíos del backend que la UI resuelve (o informa) en el cliente

Ninguno de estos puntos requiere tocar el backend; se documentan para que la
próxima entrega los cierre del lado del servidor:

1. **No existe un listado de usuarios/odontólogos** (`/api/usuarios/**` está
declarado en `SecurityConfig` pero no hay controlador). `dentista-service.js`
deriva el catálogo de odontólogos de `GET /api/citas` (id + nombre por cita).
Si la clínica no tiene citas, el formulario lo avisa en vez de inventar ids.
2. **No existe catálogo de formas de pago** (`cat_metodo_pago`). `PagoRequest`
exige `idMetodoPago`; `factura-service.js` parte del catálogo de la semilla
(1 Efectivo, 2 Tarjeta, 3 Transferencia) y lo corrige con los ids realmente
usados en los pagos ya registrados del comprobante, que son los de la base.
Si un id no existe, el mensaje `404` del backend se muestra tal cual.
3. **`GET /api/citas` no filtra por estado, paciente ni odontólogo.** La agenda
envía solo `desde`/`hasta` y filtra el estado en el cliente.
4. **`GET /api/facturas` no acepta filtros.** La búsqueda por número o paciente y
el filtro por estado se aplican en el cliente.
5. **`RealizadoResponse` no publica si el tratamiento ya fue facturado.** La
facturación deduce los `idRealizado` ya facturados del detalle de
`GET /api/facturas`; el backend sigue rechazando con `409` cualquier intento.

## Cómo agregar los recordatorios (única área reservada)

`core/routes.js` mantiene `RUTAS_RESERVADAS` con la ruta, la etiqueta, el
grupo de sidebar, el icono y los permisos de recordatorios. Para habilitarla:

1. Crear `js/modules/recordatorios-module.js` con el contrato
   `mount(container)` / `unmount()`.
2. Mover la entrada de `RUTAS_RESERVADAS` a `ROUTES`.

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
- Agenda: `#lista-citas`, `#filtro-cita-desde`, `#filtro-cita-hasta`,
  `#filtro-cita-estado`, `#btn-filtrar-citas`, `#btn-limpiar-filtros-cita`,
  `#btn-nueva-cita`, `#form-cita`, `#form-cita-error`, `[data-accion="reprogramar"|
  "cancelar"|"realizado"]`, `#form-reprogramar`, `#form-reprogramar-error`.
- Tratamiento realizado (agenda e historial): `#panel-realizado-form`,
  `#form-realizado`, `#realizado-paciente`, `#realizado-tratamiento`,
  `#realizado-dentista`, `#realizado-cita`, `#realizado-fecha`,
  `#realizado-detalle`, `#form-realizado-error`, `#btn-registrar-realizado`.
- Facturación: `#lista-facturas`, `#filtro-factura`, `#filtro-factura-estado`,
  `#form-factura`, `#factura-paciente`, `#factura-candidatos`,
  `#form-factura-error`, `[data-accion="detalle"|"pdf"]`,
  `#panel-detalle-factura`, `#detalle-factura`, `#form-pago`,
  `#pago-monto`, `#pago-metodo`, `#pago-referencia`, `#form-pago-error`.
- `localStorage`: `clinica.auth.token` y `clinica.auth.user`.
