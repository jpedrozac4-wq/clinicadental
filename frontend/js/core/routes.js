/* ============================================================================
   REGISTRO DE RUTAS DEL PORTAL ERP
   ----------------------------------------------------------------------------
   Cada ruta declara su etiqueta, su grupo en el sidebar, los roles o permisos
   que la habilitan y la vista que monta. Las vistas se cargan con import()
   diferido para que el portal no descargue modulos que el perfil no usa.

   Contrato de vista: la clase exportada recibe ({ route, params, navigate }) y
   expone mount(container) y, opcionalmente, unmount().
   ============================================================================ */

export const ROUTES = [
    {
        id: "dashboard",
        path: "/dashboard",
        label: "Dirección / Gerencia",
        group: "Dirección",
        icon: "◈",
        roles: ["ROLE_GERENTE_GENERAL", "ROLE_GERENTE_FINANCIERO", "ROLE_GERENTE_VENTAS"],
        permissions: ["KARDEX_AUDITAR", "CONCILIACION_PASARELA_VER"],
        loader: () => import("../modules/gerencia-module.js"),
        exportName: "GerenciaView"
    },
    {
        id: "catalogo",
        path: "/catalogo",
        label: "Catálogo maestro",
        group: "Administración",
        icon: "◆",
        permissions: ["CATALOGO_CREAR_EDITAR"],
        loader: () => import("../modules/admin-catalogo-module.js"),
        exportName: "CatalogoAdminView"
    },
    {
        id: "pos",
        path: "/pos",
        label: "Punto de venta",
        group: "Comercial",
        icon: "▤",
        roles: ["ROLE_CAJERO", "ROLE_GERENTE_VENTAS"],
        permissions: ["POS_VENTA_COBRAR", "POS_CAJA_APERTURA_CIERRE", "DEVOLUCION_PROCESAR"],
        loader: () => import("../modules/pos-module.js"),
        exportName: "PosView"
    },
    {
        id: "inventario",
        path: "/inventario",
        label: "Inventario y kardex",
        group: "Operaciones",
        icon: "▦",
        roles: ["ROLE_ENCARGADO_ALMACEN", "ROLE_AUXILIAR_ALMACEN"],
        permissions: ["STOCK_CONSULTAR", "KARDEX_AUDITAR"],
        loader: () => import("../modules/inventario-module.js"),
        exportName: "InventarioView"
    },
    {
        id: "logistica",
        path: "/logistica",
        label: "Logística y despachos",
        group: "Operaciones",
        icon: "⇄",
        roles: ["ROLE_ENCARGADO_ALMACEN", "ROLE_AUXILIAR_ALMACEN"],
        permissions: ["PICKING_PACKING_BODEGA", "DESPACHO_ASIGNAR_RUTA", "DESPACHO_CONFIRMAR_OTP"],
        loader: () => import("../modules/logistica-module.js"),
        exportName: "LogisticaView"
    },
    {
        id: "soporte",
        path: "/soporte",
        label: "Help desk y reclamaciones",
        group: "Control de gestión",
        icon: "◎",
        roles: ["ROLE_SOPORTE", "ROLE_GERENTE_GENERAL"],
        permissions: ["TICKETS_GESTIONAR_DERIVAR", "TICKETS_SUPERVISAR_ALERTAS"],
        loader: () => import("../modules/soporte-module.js"),
        exportName: "SoporteView"
    },
    {
        id: "cuenta",
        path: "/cuenta",
        label: "Mi cuenta y seguridad",
        group: "Mi cuenta",
        icon: "◉",
        loader: () => import("../modules/cuenta-module.js"),
        exportName: "CuentaView"
    }
];

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
