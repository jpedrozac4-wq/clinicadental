/* ============================================================================
   CONFIGURACION GLOBAL DEL FRONTEND (ENDPOINTS, STORAGE Y CONTRATO API)
   ----------------------------------------------------------------------------
   Punto unico de verdad para hablar con el backend Spring Boot. Los modulos
   nunca escriben rutas literales: siempre pasan por CONFIG.ENDPOINTS o por los
   helpers con parametros, para que un cambio de API se resuelva en un solo
   archivo.
   ============================================================================ */

const API = "/api";

export const CONFIG = {
    API_BASE_URL: API,
    APP_NAME: "Calzado ERP",
    STOREFRONT_URL: "index.html",
    CURRENCY: "USD",

    STORAGE_KEYS: {
        TOKEN: "erp.auth.token",
        USER: "erp.auth.user",
        CART: "erp.cart.items",
        LAST_ROUTE: "erp.shell.lastRoute",
        PRINT_PREFERENCE: "erp.pos.printPreference"
    },

    /** Sesion: el token expira a las 8 horas (28800 s) en el backend. */
    SESSION: {
        EXPIRES_IN_SECONDS: 28800,
        WARN_BEFORE_SECONDS: 600
    },

    ENDPOINTS: {
        // ---- Autenticacion ----
        AUTH_LOGIN: `${API}/auth/login`,
        AUTH_LOGOUT: `${API}/auth/logout`,
        AUTH_ME: `${API}/auth/me`,

        // ---- Catalogo (publico) ----
        CATALOGO: `${API}/catalogo`,
        CATALOGO_TIPOS: `${API}/catalogo/tipos`,
        CATALOGO_MARCAS: `${API}/catalogo/marcas`,
        CATALOGO_COLORES: `${API}/catalogo/colores`,
        CATALOGO_TALLAS: `${API}/catalogo/tallas`,

        // ---- Catalogo (administracion) ----
        CATALOGO_ADMIN: `${API}/catalogo/admin`,
        CATALOGO_ADMIN_MODELOS: `${API}/catalogo/admin/modelos`,
        CATALOGO_ADMIN_SKUS: `${API}/catalogo/admin/skus`,
        CATALOGO_ADMIN_TALLAS: `${API}/catalogo/admin/tallas`,

        // ---- E-commerce B2C ----
        CHECKOUT_INICIAR: `${API}/checkout/iniciar`,
        CHECKOUT_PAGAR: `${API}/checkout/pagar`,
        CHECKOUT_COTIZAR: `${API}/checkout/cotizar`,
        CHECKOUT_CUPON: `${API}/checkout/cupon/validar`,
        CHECKOUT_SUCURSALES: `${API}/checkout/sucursales`,
        CHECKOUT_ALERTA_DEMANDA: `${API}/checkout/alerta-demanda`,
        CHECKOUT_ORDENES: `${API}/checkout/ordenes`,

        // ---- Tracking (publico) ----
        TRACKING: `${API}/tracking`,
        TRACKING_ENTREGAR: `${API}/tracking/entregar`,

        // ---- POS y caja ----
        POS: `${API}/pos`,
        POS_SESIONES: `${API}/pos/sesiones`,
        POS_COBRAR: `${API}/pos/cobrar`,
        POS_DEVOLUCION: `${API}/pos/devolucion`,
        POS_CAJAS: `${API}/pos/cajas`,

        // ---- Inventario ----
        INVENTARIO: `${API}/inventario`,
        INVENTARIO_STOCK: `${API}/inventario/stock`,
        INVENTARIO_KARDEX: `${API}/inventario/kardex`,
        INVENTARIO_BODEGAS: `${API}/inventario/bodegas`,
        INVENTARIO_ABASTECIMIENTO: `${API}/inventario/abastecimiento`,
        INVENTARIO_BAJAS: `${API}/inventario/bajas`,

        // ---- Logistica ----
        LOGISTICA: `${API}/logistica`,
        LOGISTICA_DESPACHOS: `${API}/logistica/despachos`,
        LOGISTICA_SLA: `${API}/logistica/sla`,

        // ---- Compras ----
        COMPRAS: `${API}/compras`,
        COMPRAS_PROVEEDORES: `${API}/compras/proveedores`,
        COMPRAS_ORDENES: `${API}/compras/ordenes`,
        COMPRAS_RECEPCIONES: `${API}/compras/recepciones`,
        COMPRAS_ACONDICIONAMIENTO: `${API}/compras/acondicionamiento`,
        COMPRAS_ROP: `${API}/compras/rop`,

        // ---- Facturacion y finanzas ----
        FACTURACION: `${API}/facturacion`,
        FACTURACION_FACTURAS: `${API}/facturacion/facturas`,
        FACTURACION_NOTAS_CREDITO: `${API}/facturacion/notas-credito`,
        FACTURACION_PAGOS: `${API}/facturacion/pagos`,
        FACTURACION_CONCILIACION: `${API}/facturacion/conciliacion`,
        FACTURACION_COMISIONES: `${API}/facturacion/comisiones`,

        // ---- Marketing ----
        MARKETING: `${API}/marketing`,
        MARKETING_CUPONES: `${API}/marketing/cupones`,
        MARKETING_ALERTAS: `${API}/marketing/alertas`,

        // ---- Direccion / Gerencia ----
        GERENCIA_KPIS: `${API}/gerencia/kpis`,
        GERENCIA_REPORTES: `${API}/gerencia/reportes`,
        GERENCIA_RENTABILIDAD: `${API}/gerencia/rentabilidad`,
        DIRECCION_PRECIOS: `${API}/direccion/precios`,

        // ---- Soporte y control de gestion ----
        SOPORTE_TICKETS: `${API}/soporte/tickets`,
        SOPORTE_ESCALAMIENTOS: `${API}/soporte/escalamientos`,

        // ---- RRHH ----
        RRHH_COMISIONES: `${API}/rrhh/comisiones`,

        // ---- Administracion y auditoria ----
        ADMIN_USUARIOS: `${API}/administracion/usuarios`,
        ADMIN_ROLES: `${API}/administracion/roles`,
        ADMIN_SUCURSALES: `${API}/administracion/sucursales`,
        ADMIN_BODEGAS: `${API}/administracion/bodegas`,
        AUDITORIA: `${API}/auditoria`
    },

    /** Endpoints que se consumen sin token (storefront). */
    PUBLIC_ENDPOINTS: [
        `${API}/auth/login`,
        `${API}/checkout/iniciar`,
        `${API}/checkout/pagar`,
        `${API}/checkout/cotizar`,
        `${API}/checkout/cupon/validar`,
        `${API}/checkout/sucursales`,
        `${API}/checkout/alerta-demanda`
    ],

    /** Estados canonicos reutilizados por varias vistas. */
    ESTADOS: {
        ORDEN: ["PENDIENTE_PAGO", "PAGADA", "PREPARADA", "EN_RUTA", "ENTREGADA", "EXPIRADA", "CANCELADA"],
        DESPACHO: ["PREPARADO", "CARGADO", "EN_RUTA", "ENTREGADO"],
        TICKET: ["NUEVO", "EN_ANALISIS", "EN_BODEGA_TRANSPORTE", "RESUELTO", "CERRADO"]
    }
};

/** Helper para construir rutas con parametros sin concatenar strings sueltos. */
export function apiUrl(base, ...segments) {
    const suffix = segments
        .filter(segment => segment !== undefined && segment !== null && segment !== "")
        .map(segment => encodeURIComponent(String(segment)))
        .join("/");
    return suffix ? `${base}/${suffix}` : base;
}

/** Helper para construir query strings ignorando valores vacios. */
export function apiQuery(base, params = {}) {
    const search = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
        if (value === undefined || value === null || value === "") return;
        search.append(key, String(value));
    });
    const query = search.toString();
    return query ? `${base}?${query}` : base;
}
