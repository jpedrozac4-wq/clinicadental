/* ============================================================================
   REGISTRO DE RUTAS DEL SISTEMA CLINICO
   ----------------------------------------------------------------------------
   Cada ruta declara su etiqueta, su grupo en el sidebar, los roles o permisos
   que la habilitan y la vista que monta. Las vistas se cargan con import()
   diferido para que el portal no descargue modulos que el perfil no usa.

   Contrato de vista: la clase exportada recibe ({ route, params, navigate }) y
   expone mount(container) y, opcionalmente, unmount().

   Convenciones:
   - `roles` / `permissions`: basta cumplir una de las dos listas para ver la
     ruta. Sin restricciones, la ruta queda disponible para toda sesion.
   - `hidden: true`: la ruta se navega pero no ocupa un item del sidebar.
   ============================================================================ */
import { CONFIG } from "../config.js";

const { PERMISSIONS } = CONFIG;

export const ROUTES = [
    {
        id: "dashboard",
        path: "/dashboard",
        label: "Panel general",
        group: "General",
        icon: "◈",
        roles: [],
        permissions: [],
        loader: () => import("../modules/dashboard-module.js"),
        exportName: "DashboardView"
    },
    {
        id: "pacientes",
        path: "/pacientes",
        label: "Pacientes",
        group: "Atención clínica",
        icon: "◎",
        permissions: [PERMISSIONS.PACIENTE_VER],
        loader: () => import("../modules/pacientes-module.js"),
        exportName: "PacientesView"
    },
    {
        id: "paciente-historial",
        path: "/pacientes/:id/historial",
        label: "Historial clínico",
        group: "Atención clínica",
        icon: "▤",
        hidden: true,
        permissions: [PERMISSIONS.PACIENTE_HISTORIAL_VER],
        loader: () => import("../modules/paciente-historial-module.js"),
        exportName: "PacienteHistorialView"
    },
    {
        id: "agenda",
        path: "/agenda",
        label: "Agenda de citas",
        group: "Atención clínica",
        icon: "▦",
        permissions: [PERMISSIONS.CITA_VER],
        loader: () => import("../modules/agenda-module.js"),
        exportName: "AgendaView"
    },
    {
        id: "tratamientos",
        path: "/tratamientos",
        label: "Catálogo de tratamientos",
        group: "Atención clínica",
        icon: "◇",
        permissions: [PERMISSIONS.TRATAMIENTO_VER],
        loader: () => import("../modules/tratamientos-module.js"),
        exportName: "TratamientosView"
    },
    {
        id: "facturacion",
        path: "/facturacion",
        label: "Facturación y pagos",
        group: "Administración",
        icon: "▤",
        permissions: [PERMISSIONS.FACTURA_VER],
        loader: () => import("../modules/facturacion-module.js"),
        exportName: "FacturacionView"
    },
    {
        id: "recordatorios",
        path: "/recordatorios",
        label: "Recordatorios",
        group: "Administración",
        icon: "◎",
        permissions: [PERMISSIONS.RECORDATORIO_VER],
        loader: () => import("../modules/recordatorios-module.js"),
        exportName: "RecordatoriosView"
    },
    {
        id: "reportes",
        path: "/reportes",
        label: "Reportes",
        group: "Administración",
        icon: "▥",
        permissions: [PERMISSIONS.REPORTE_VER],
        loader: () => import("../modules/reportes-module.js"),
        exportName: "ReportesView"
    },
    {
        id: "usuarios",
        path: "/usuarios",
        label: "Usuarios y roles",
        group: "Administración",
        icon: "◍",
        permissions: [PERMISSIONS.USUARIOS_ADMIN],
        loader: () => import("../modules/usuarios-module.js"),
        exportName: "UsuariosView"
    },
    {
        id: "auditoria",
        path: "/auditoria",
        label: "Auditoría",
        group: "Administración",
        icon: "▣",
        permissions: [PERMISSIONS.AUDITORIA_VER],
        loader: () => import("../modules/auditoria-module.js"),
        exportName: "AuditoriaView"
    },
    {
        id: "cuenta",
        path: "/cuenta",
        label: "Mi cuenta",
        group: "Mi cuenta",
        icon: "◉",
        roles: [],
        permissions: [],
        loader: () => import("../modules/cuenta-module.js"),
        exportName: "CuentaView"
    }
];

/**
 * Rutas reservadas para entregas futuras: el sidebar y el router las agrupan y
 * filtran igual que las de ROUTES, pero no se navegan hasta que se muevan.
 *
 * S22 dejo la lista vacia: los nueve modulos estan entregados y vinculados
 * (panel, pacientes, agenda, tratamientos, facturacion, recordatorios,
 * reportes, usuarios y auditoria). El mecanismo se conserva porque lo consume
 * el dashboard y es el punto documentado para la proxima pantalla.
 */
export const RUTAS_RESERVADAS = [];

/** Agrupa las rutas accesibles por seccion del sidebar, preservando el orden. */
export function groupedRoutes(routes) {
    const groups = new Map();
    routes.forEach(route => {
        const group = route.group || "General";
        if (!groups.has(group)) {
            groups.set(group, []);
        }
        groups.get(group).push(route);
    });
    return Array.from(groups, ([name, items]) => ({ name, items }));
}
