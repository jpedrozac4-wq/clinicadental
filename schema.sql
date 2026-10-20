-- ============================================================================
-- SISTEMA INTEGRAL ERP Y E-COMMERCE B2C FUNCIONAL - INDUSTRIA DEL CALZADO
-- SCRIPT DDL OFICIAL DE BASE DE DATOS (PostgreSQL 16)
-- Basado en: database-standard-design (OpenCode / Gentleman Programming)
-- Version: 2.1.0
-- ============================================================================

-- Configuración de entorno y codificación
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SET check_function_bodies = false;
SET client_min_messages = warning;
SET row_security = off;

-- Limpieza preventiva si se ejecuta en ambiente de desarrollo
DROP SCHEMA IF EXISTS public CASCADE;
CREATE SCHEMA public;
GRANT ALL ON SCHEMA public TO postgres;
GRANT ALL ON SCHEMA public TO public;

-- Extensiones recomendadas para UUIDs y funciones criptográficas
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ============================================================================
-- 1. FUNCIONES AUXILIARES GLOBALES (TRIGGERS DE AUDITORIA Y SEGURIDAD)
-- ============================================================================

-- Función para actualizar automáticamente la columna updated_at
CREATE OR REPLACE FUNCTION fn_update_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Función de protección inmutable: impide el DELETE físico en registros financieros y de auditoría
CREATE OR REPLACE FUNCTION fn_prevent_delete_financial()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Operación rechazada (Hard Rule): Los registros financieros, contables y de auditoría no pueden eliminarse físicamente.';
END;
$$ LANGUAGE plpgsql;

-- ============================================================================
-- 2. CATALOGOS MAESTROS (cat_*) — Regla: Cero ENUMs en tablas transaccionales
-- ============================================================================

-- Tipos de Calzado (T=Tenis, B=Botas, Z=Zapatos, S=Sandalias)
CREATE TABLE cat_tipo_calzado (
    id_tipo_calzado SERIAL PRIMARY KEY,
    codigo_letra CHAR(1) NOT NULL UNIQUE,
    nombre VARCHAR(50) NOT NULL,
    descripcion TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Marcas Comerciales y Fabricantes (V=Vans, G=Grangler, N=Nike, A=Adidas)
CREATE TABLE cat_marca (
    id_marca SERIAL PRIMARY KEY,
    codigo_letra CHAR(1) NOT NULL UNIQUE,
    nombre VARCHAR(50) NOT NULL,
    pais_origen VARCHAR(50),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Paleta de Colores Normalizada
CREATE TABLE cat_color (
    id_color SERIAL PRIMARY KEY,
    nombre VARCHAR(50) NOT NULL UNIQUE,
    codigo_hex VARCHAR(7) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Tipos de Bodega (RECEPCION, ACTUAL, LIQUIDACION)
CREATE TABLE cat_bodega_tipo (
    id_bodega_tipo SERIAL PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    description VARCHAR(150) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Estados de la Orden (PENDIENTE_PAGO, PAGADA, PREPARADA, EN_RUTA, ENTREGADA, EXPIRADA)
CREATE TABLE cat_orden_estado (
    id_orden_estado SERIAL PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    description VARCHAR(150) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Métodos de Pago y Pasarelas
CREATE TABLE cat_pago_metodo (
    id_pago_metodo SERIAL PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    description VARCHAR(150) NOT NULL,
    comision_porcentaje NUMERIC(5,2) NOT NULL DEFAULT 0.00,
    comision_fija NUMERIC(10,2) NOT NULL DEFAULT 0.00,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Tipos de Comprobante Fiscal (FACTURA, BOLETA, NOTA_CREDITO, NOTA_DEBITO)
CREATE TABLE cat_factura_tipo (
    id_factura_tipo SERIAL PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    description VARCHAR(150) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Estados Fiscales del Comprobante
CREATE TABLE cat_factura_estado (
    id_factura_estado SERIAL PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    description VARCHAR(150) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Estados del Despacho y Tracking
CREATE TABLE cat_despacho_estado (
    id_despacho_estado SERIAL PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    description VARCHAR(150) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Motivos Tipificados de Reclamación / Help Desk
CREATE TABLE cat_motivo_queja (
    id_motivo_queja SERIAL PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE,
    description VARCHAR(150) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Estados del Ticket de Soporte
CREATE TABLE cat_ticket_estado (
    id_ticket_estado SERIAL PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    description VARCHAR(150) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Tipos de Movimiento en Kardex de Inventario
CREATE TABLE cat_movimiento_stock_tipo (
    id_movimiento_tipo SERIAL PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE,
    description VARCHAR(150) NOT NULL,
    es_incremento BOOLEAN NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Tipos de Acción en Bitácora de Operaciones (audit_log)
CREATE TABLE cat_log_accion (
    id_log_accion SERIAL PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    description VARCHAR(150) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Tipos de Operación en Auditoría Avanzada (audit_trail)
CREATE TABLE cat_auditoria_accion (
    id_auditoria_accion SERIAL PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    description VARCHAR(150) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
-- 3. SEGURIDAD, AUTENTICACION Y PATRON RBAC CANONICO
-- ============================================================================

-- Roles Formales de la Organización (9 perfiles corporativos)
CREATE TABLE role (
    id_role SERIAL PRIMARY KEY,
    role_name VARCHAR(60) NOT NULL UNIQUE,
    description VARCHAR(200),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Permisos Granulares por Módulo y Acción
CREATE TABLE permission (
    id_permission SERIAL PRIMARY KEY,
    permission_name VARCHAR(100) NOT NULL UNIQUE,
    modulo VARCHAR(50) NOT NULL,
    action VARCHAR(30) NOT NULL,
    description VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Matriz de Permisos por Rol (Relación N:M canónica)
CREATE TABLE role_permission (
    id_role INT NOT NULL REFERENCES role(id_role) ON DELETE CASCADE ON UPDATE CASCADE,
    id_permission INT NOT NULL REFERENCES permission(id_permission) ON DELETE CASCADE ON UPDATE CASCADE,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id_role, id_permission)
);

-- Usuarios del Sistema con Credenciales Hash Seguras (BCrypt / Argon2)
CREATE TABLE app_user (
    id_user SERIAL PRIMARY KEY,
    username VARCHAR(80) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    hash_method VARCHAR(20) NOT NULL DEFAULT 'BCRYPT',
    salt VARCHAR(64),
    full_name VARCHAR(120) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    failed_attempts SMALLINT NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ,
    last_login TIMESTAMPTZ,
    created_by INT,
    updated_by INT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Asignación de Roles a Usuarios (Relación N:M)
CREATE TABLE user_role (
    id_user INT NOT NULL REFERENCES app_user(id_user) ON DELETE CASCADE ON UPDATE CASCADE,
    id_role INT NOT NULL REFERENCES role(id_role) ON DELETE CASCADE ON UPDATE CASCADE,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id_user, id_role)
);

-- Liquidación Mensual de Comisiones e Incentivos de Personal
CREATE TABLE comision_empleado (
    id_comision SERIAL PRIMARY KEY,
    id_user INT NOT NULL REFERENCES app_user(id_user) ON DELETE RESTRICT ON UPDATE CASCADE,
    mes_anio CHAR(7) NOT NULL, -- 'YYYY-MM'
    monto_comision_ventas NUMERIC(12,2) NOT NULL DEFAULT 0.00 CHECK (monto_comision_ventas >= 0),
    bono_sla_bodega NUMERIC(12,2) NOT NULL DEFAULT 0.00 CHECK (bono_sla_bodega >= 0),
    bono_volumen_calidad NUMERIC(12,2) NOT NULL DEFAULT 0.00 CHECK (bono_volumen_calidad >= 0),
    total_liquidado NUMERIC(12,2) NOT NULL DEFAULT 0.00 CHECK (total_liquidado >= 0),
    calculated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_user_mes_comision UNIQUE (id_user, mes_anio)
);

-- ============================================================================
-- 4. DOMINIO DE PRODUCTO Y CATALOGO MAESTRO DE CALZADO
-- ============================================================================

-- Modelo Base de Calzado (Cabecera de Producto)
CREATE TABLE modelo (
    id_modelo SERIAL PRIMARY KEY,
    id_tipo_calzado INT NOT NULL REFERENCES cat_tipo_calzado(id_tipo_calzado) ON DELETE RESTRICT ON UPDATE CASCADE,
    id_marca INT NOT NULL REFERENCES cat_marca(id_marca) ON DELETE RESTRICT ON UPDATE CASCADE,
    correlativo INT NOT NULL CHECK (correlativo > 0 AND correlativo <= 999),
    nombre VARCHAR(100) NOT NULL,
    material_exterior VARCHAR(60) NOT NULL,
    material_forro VARCHAR(60),
    tipo_suela VARCHAR(60) NOT NULL,
    tipo_cierre VARCHAR(60) NOT NULL,
    genero VARCHAR(20) NOT NULL CHECK (genero IN ('HOMBRE', 'MUJER', 'UNISEX', 'NINO')),
    temporada VARCHAR(40) NOT NULL,
    precio_lista_base NUMERIC(10,2) NOT NULL CHECK (precio_lista_base > 0),
    es_outlet BOOLEAN NOT NULL DEFAULT FALSE,
    porcentaje_descuento NUMERIC(5,2) NOT NULL DEFAULT 0.00 CHECK (porcentaje_descuento >= 0 AND porcentaje_descuento <= 100),
    es_visible_web BOOLEAN NOT NULL DEFAULT TRUE,
    created_by INT REFERENCES app_user(id_user) ON DELETE RESTRICT ON UPDATE CASCADE,
    updated_by INT REFERENCES app_user(id_user) ON DELETE RESTRICT ON UPDATE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_modelo_correlativo_marca UNIQUE (id_tipo_calzado, id_marca, correlativo)
);

-- Variante de Color del Modelo
CREATE TABLE modelo_color (
    id_modelo_color SERIAL PRIMARY KEY,
    id_modelo INT NOT NULL REFERENCES modelo(id_modelo) ON DELETE CASCADE ON UPDATE CASCADE,
    id_color INT NOT NULL REFERENCES cat_color(id_color) ON DELETE RESTRICT ON UPDATE CASCADE,
    foto_principal_url VARCHAR(255),
    galeria_fotos_json JSONB,
    created_by INT REFERENCES app_user(id_user) ON DELETE RESTRICT ON UPDATE CASCADE,
    updated_by INT REFERENCES app_user(id_user) ON DELETE RESTRICT ON UPDATE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_modelo_color UNIQUE (id_modelo, id_color)
);

-- Tabla de Equivalencias Multidimensionales de Talla (EUR, US, UK, CM/MX)
CREATE TABLE talla_equivalencia (
    id_talla SERIAL PRIMARY KEY,
    talla_eur NUMERIC(3,1) NOT NULL UNIQUE CHECK (talla_eur > 0),
    talla_us_hombre NUMERIC(3,1) CHECK (talla_us_hombre > 0),
    talla_us_mujer NUMERIC(3,1) CHECK (talla_us_mujer > 0),
    talla_uk NUMERIC(3,1) CHECK (talla_uk > 0),
    talla_cm_mx NUMERIC(3,1) NOT NULL CHECK (talla_cm_mx > 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Ítem Físico Transaccional con SKU Algorítmico Único ([Tipo][Marca][Correlativo]-[Talla])
CREATE TABLE calzado_sku (
    id_calzado_sku SERIAL PRIMARY KEY,
    id_modelo_color INT NOT NULL REFERENCES modelo_color(id_modelo_color) ON DELETE CASCADE ON UPDATE CASCADE,
    id_talla INT NOT NULL REFERENCES talla_equivalencia(id_talla) ON DELETE RESTRICT ON UPDATE CASCADE,
    sku VARCHAR(30) NOT NULL UNIQUE,
    codigo_barras_ean VARCHAR(14),
    precio_venta_vigente NUMERIC(10,2) NOT NULL CHECK (precio_venta_vigente > 0),
    peso_gramos INT NOT NULL DEFAULT 800 CHECK (peso_gramos > 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by INT REFERENCES app_user(id_user) ON DELETE RESTRICT ON UPDATE CASCADE,
    updated_by INT REFERENCES app_user(id_user) ON DELETE RESTRICT ON UPDATE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_sku_variante_talla UNIQUE (id_modelo_color, id_talla)
);

-- ============================================================================
-- 5. MULTIBODEGA, INVENTARIOS Y KARDEX INMUTABLE
-- ============================================================================

-- Las Tres Bodegas Segregadas (Recepción/Revisión, Actual/Despacho, Liquidación)
CREATE TABLE bodega (
    id_bodega SERIAL PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    id_bodega_tipo INT NOT NULL REFERENCES cat_bodega_tipo(id_bodega_tipo) ON DELETE RESTRICT ON UPDATE CASCADE,
    ubicacion VARCHAR(150),
    es_despachable_web BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by INT REFERENCES app_user(id_user) ON DELETE RESTRICT ON UPDATE CASCADE,
    updated_by INT REFERENCES app_user(id_user) ON DELETE RESTRICT ON UPDATE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Stock Disponible y Reservado por Bodega y SKU (Restricción dura: CHECK >= 0)
CREATE TABLE inventario_stock (
    id_stock SERIAL PRIMARY KEY,
    id_bodega INT NOT NULL REFERENCES bodega(id_bodega) ON DELETE RESTRICT ON UPDATE CASCADE,
    id_calzado_sku INT NOT NULL REFERENCES calzado_sku(id_calzado_sku) ON DELETE RESTRICT ON UPDATE CASCADE,
    cantidad_disponible INT NOT NULL DEFAULT 0 CHECK (cantidad_disponible >= 0),
    cantidad_reservada INT NOT NULL DEFAULT 0 CHECK (cantidad_reservada >= 0),
    stock_seguridad_minimo INT NOT NULL DEFAULT 2 CHECK (stock_seguridad_minimo >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_bodega_sku UNIQUE (id_bodega, id_calzado_sku)
);

-- Kardex Inmutable: Libro Mayor de Movimientos de Inventario
CREATE TABLE kardex_movimiento (
    id_kardex BIGSERIAL PRIMARY KEY,
    id_bodega INT NOT NULL REFERENCES bodega(id_bodega) ON DELETE RESTRICT ON UPDATE CASCADE,
    id_calzado_sku INT NOT NULL REFERENCES calzado_sku(id_calzado_sku) ON DELETE RESTRICT ON UPDATE CASCADE,
    id_movimiento_tipo INT NOT NULL REFERENCES cat_movimiento_stock_tipo(id_movimiento_tipo) ON DELETE RESTRICT ON UPDATE CASCADE,
    cantidad_movimiento INT NOT NULL CHECK (cantidad_movimiento <> 0),
    stock_anterior INT NOT NULL CHECK (stock_anterior >= 0),
    stock_posterior INT NOT NULL CHECK (stock_posterior >= 0),
    documento_tipo VARCHAR(40) NOT NULL, -- 'ORDEN', 'TRASPASO', 'RECEPCION', 'BAJA', 'DEVOLUCION'
    documento_id BIGINT NOT NULL,
    id_user INT NOT NULL REFERENCES app_user(id_user) ON DELETE RESTRICT ON UPDATE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Solicitudes de Abastecimiento Interno entre Tiendas y Almacén
CREATE TABLE solicitud_abastecimiento (
    id_solicitud_abastecimiento SERIAL PRIMARY KEY,
    id_bodega_origen INT NOT NULL REFERENCES bodega(id_bodega) ON DELETE RESTRICT ON UPDATE CASCADE,
    id_bodega_destino INT NOT NULL REFERENCES bodega(id_bodega) ON DELETE RESTRICT ON UPDATE CASCADE,
    id_user_solicitante INT NOT NULL REFERENCES app_user(id_user) ON DELETE RESTRICT ON UPDATE CASCADE,
    estado VARCHAR(30) NOT NULL DEFAULT 'SOLICITADA' CHECK (estado IN ('SOLICITADA', 'EN_TRANSITO', 'RECIBIDA_COMPLETA', 'RECIBIDA_DISCREPANCIA', 'CANCELADA')),
    created_by INT REFERENCES app_user(id_user) ON DELETE RESTRICT ON UPDATE CASCADE,
    updated_by INT REFERENCES app_user(id_user) ON DELETE RESTRICT ON UPDATE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Líneas de Solicitud de Abastecimiento
CREATE TABLE solicitud_abastecimiento_linea (
    id_solicitud_linea SERIAL PRIMARY KEY,
    id_solicitud_abastecimiento INT NOT NULL REFERENCES solicitud_abastecimiento(id_solicitud_abastecimiento) ON DELETE CASCADE ON UPDATE CASCADE,
    id_calzado_sku INT NOT NULL REFERENCES calzado_sku(id_calzado_sku) ON DELETE RESTRICT ON UPDATE CASCADE,
    cantidad_solicitada INT NOT NULL CHECK (cantidad_solicitada > 0),
    cantidad_enviada INT NOT NULL DEFAULT 0 CHECK (cantidad_enviada >= 0),
    cantidad_recibida INT NOT NULL DEFAULT 0 CHECK (cantidad_recibida >= 0)
);

-- Solicitudes de Baja de Inventario por Merma o Defecto (Aprobación de Gerente General)
CREATE TABLE solicitud_baja_stock (
    id_solicitud_baja SERIAL PRIMARY KEY,
    id_bodega INT NOT NULL REFERENCES bodega(id_bodega) ON DELETE RESTRICT ON UPDATE CASCADE,
    id_user_almacen INT NOT NULL REFERENCES app_user(id_user) ON DELETE RESTRICT ON UPDATE CASCADE,
    id_user_gerente INT REFERENCES app_user(id_user) ON DELETE RESTRICT ON UPDATE CASCADE,
    motivo VARCHAR(40) NOT NULL CHECK (motivo IN ('MERMA_ROTURA', 'DEFECTO_FABRICA', 'FALTANTE_CONTEO')),
    estado VARCHAR(30) NOT NULL DEFAULT 'PENDIENTE_GERENCIA' CHECK (estado IN ('PENDIENTE_GERENCIA', 'APROBADA', 'RECHAZADA')),
    justificacion TEXT NOT NULL,
    created_by INT REFERENCES app_user(id_user) ON DELETE RESTRICT ON UPDATE CASCADE,
    updated_by INT REFERENCES app_user(id_user) ON DELETE RESTRICT ON UPDATE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Líneas de Solicitud de Baja de Inventario
CREATE TABLE solicitud_baja_stock_linea (
    id_baja_linea SERIAL PRIMARY KEY,
    id_solicitud_baja INT NOT NULL REFERENCES solicitud_baja_stock(id_solicitud_baja) ON DELETE CASCADE ON UPDATE CASCADE,
    id_calzado_sku INT NOT NULL REFERENCES calzado_sku(id_calzado_sku) ON DELETE RESTRICT ON UPDATE CASCADE,
    cantidad_baja INT NOT NULL CHECK (cantidad_baja > 0)
);

-- ============================================================================
-- 6. TIENDAS FISICAS, PUNTOS DE VENTA (POS) Y ARQUEOS DE CAJA
-- ============================================================================

-- Sucursales Físicas de la Cadena
CREATE TABLE sucursal (
    id_sucursal SERIAL PRIMARY KEY,
    codigo_sucursal VARCHAR(30) NOT NULL UNIQUE,
    nombre VARCHAR(100) NOT NULL,
    direccion TEXT NOT NULL,
    telefono VARCHAR(25),
    id_bodega INT NOT NULL REFERENCES bodega(id_bodega) ON DELETE RESTRICT ON UPDATE CASCADE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by INT REFERENCES app_user(id_user) ON DELETE RESTRICT ON UPDATE CASCADE,
    updated_by INT REFERENCES app_user(id_user) ON DELETE RESTRICT ON UPDATE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Cajas Registradoras / Terminales POS en Tiendas
CREATE TABLE caja_pos (
    id_caja_pos SERIAL PRIMARY KEY,
    id_sucursal INT NOT NULL REFERENCES sucursal(id_sucursal) ON DELETE RESTRICT ON UPDATE CASCADE,
    numero_caja VARCHAR(20) NOT NULL,
    nombre_terminal VARCHAR(60) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_sucursal_numero_caja UNIQUE (id_sucursal, numero_caja)
);

-- Sesiones y Turnos de Caja (Aperturas, Cierres y Arqueos)
CREATE TABLE sesion_caja (
    id_sesion_caja SERIAL PRIMARY KEY,
    id_caja_pos INT NOT NULL REFERENCES caja_pos(id_caja_pos) ON DELETE RESTRICT ON UPDATE CASCADE,
    id_user_cajero INT NOT NULL REFERENCES app_user(id_user) ON DELETE RESTRICT ON UPDATE CASCADE,
    monto_apertura NUMERIC(10,2) NOT NULL CHECK (monto_apertura >= 0),
    monto_cierre_efectivo NUMERIC(10,2) CHECK (monto_cierre_efectivo >= 0),
    total_ventas_sistema NUMERIC(10,2) NOT NULL DEFAULT 0.00 CHECK (total_ventas_sistema >= 0),
    diferencia_arqueo NUMERIC(10,2) DEFAULT 0.00,
    estado VARCHAR(20) NOT NULL DEFAULT 'ABIERTA' CHECK (estado IN ('ABIERTA', 'CERRADA', 'AUDITADA')),
    fecha_apertura TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_cierre TIMESTAMPTZ
);

-- ============================================================================
-- 7. CLIENTES, MARKETING, CUPONES Y ORDENES DE VENTA
-- ============================================================================

-- Clientes Omnicanal con Wallet de Saldo a Favor
CREATE TABLE customer (
    id_customer SERIAL PRIMARY KEY,
    tipo_documento VARCHAR(20) NOT NULL DEFAULT 'CF' CHECK (tipo_documento IN ('CF', 'NIT', 'DNI', 'PASAPORTE')),
    numero_documento VARCHAR(30),
    nombre_completo VARCHAR(120) NOT NULL,
    email VARCHAR(100) NOT NULL,
    telefono VARCHAR(25),
    direccion TEXT,
    es_invitado BOOLEAN NOT NULL DEFAULT TRUE,
    saldo_favor_wallet NUMERIC(10,2) NOT NULL DEFAULT 0.00 CHECK (saldo_favor_wallet >= 0),
    created_by INT REFERENCES app_user(id_user) ON DELETE RESTRICT ON UPDATE CASCADE,
    updated_by INT REFERENCES app_user(id_user) ON DELETE RESTRICT ON UPDATE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Cupones de Descuento Promocionales (Marketing)
CREATE TABLE cupon_descuento (
    id_cupon SERIAL PRIMARY KEY,
    codigo_cupon VARCHAR(40) NOT NULL UNIQUE,
    tipo_descuento VARCHAR(20) NOT NULL CHECK (tipo_descuento IN ('PORCENTAJE', 'MONTO_FIJO')),
    valor_descuento NUMERIC(10,2) NOT NULL CHECK (valor_descuento > 0),
    monto_minimo_compra NUMERIC(10,2) NOT NULL DEFAULT 0.00 CHECK (monto_minimo_compra >= 0),
    fecha_inicio TIMESTAMPTZ NOT NULL,
    fecha_fin TIMESTAMPTZ NOT NULL,
    limite_usos INT NOT NULL DEFAULT 100 CHECK (limite_usos > 0),
    usos_actuales INT NOT NULL DEFAULT 0 CHECK (usos_actuales >= 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Tarifario Dinámico de Envíos (Flete por Distancia y Umbral de Compra Gratis)
CREATE TABLE tarifario_envio (
    id_tarifa SERIAL PRIMARY KEY,
    zona_nombre VARCHAR(80) NOT NULL,
    codigo_postal_prefijo VARCHAR(20),
    distancia_km_max NUMERIC(8,2) NOT NULL CHECK (distancia_km_max > 0),
    costo_flete_base NUMERIC(10,2) NOT NULL CHECK (costo_flete_base >= 0),
    umbral_compra_envio_gratis NUMERIC(10,2) NOT NULL DEFAULT 70.00 CHECK (umbral_compra_envio_gratis >= 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Órdenes de Venta (E-commerce y POS)
CREATE TABLE orden (
    id_orden SERIAL PRIMARY KEY,
    numero_orden VARCHAR(35) NOT NULL UNIQUE,
    id_customer INT NOT NULL REFERENCES customer(id_customer) ON DELETE RESTRICT ON UPDATE CASCADE,
    canal_venta VARCHAR(30) NOT NULL CHECK (canal_venta IN ('ONLINE_ECOMMERCE', 'FISICO_TIENDA_POS')),
    tipo_entrega VARCHAR(30) NOT NULL CHECK (tipo_entrega IN ('ENVIO_DOMICILIO', 'RETIRO_TIENDA_CLICK_COLLECT')),
    id_sucursal_pos INT REFERENCES sucursal(id_sucursal) ON DELETE RESTRICT ON UPDATE CASCADE,
    id_user_cajero INT REFERENCES app_user(id_user) ON DELETE RESTRICT ON UPDATE CASCADE,
    id_sucursal_retiro INT REFERENCES sucursal(id_sucursal) ON DELETE RESTRICT ON UPDATE CASCADE,
    id_bodega_despacho INT NOT NULL REFERENCES bodega(id_bodega) ON DELETE RESTRICT ON UPDATE CASCADE,
    id_cupon INT REFERENCES cupon_descuento(id_cupon) ON DELETE RESTRICT ON UPDATE CASCADE,
    id_orden_estado INT NOT NULL REFERENCES cat_orden_estado(id_orden_estado) ON DELETE RESTRICT ON UPDATE CASCADE,
    subtotal_productos NUMERIC(10,2) NOT NULL CHECK (subtotal_productos >= 0),
    costo_envio NUMERIC(10,2) NOT NULL DEFAULT 0.00 CHECK (costo_envio >= 0),
    comision_pasarela NUMERIC(10,2) NOT NULL DEFAULT 0.00 CHECK (comision_pasarela >= 0),
    descuento_aplicado NUMERIC(10,2) NOT NULL DEFAULT 0.00 CHECK (descuento_aplicado >= 0),
    total_a_pagar NUMERIC(10,2) NOT NULL CHECK (total_a_pagar >= 0),
    fecha_expiracion_ttl_2h TIMESTAMPTZ NOT NULL,
    created_by INT REFERENCES app_user(id_user) ON DELETE RESTRICT ON UPDATE CASCADE,
    updated_by INT REFERENCES app_user(id_user) ON DELETE RESTRICT ON UPDATE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Detalle de Líneas de Orden
CREATE TABLE orden_linea (
    id_orden_linea SERIAL PRIMARY KEY,
    id_orden INT NOT NULL REFERENCES orden(id_orden) ON DELETE CASCADE ON UPDATE CASCADE,
    id_calzado_sku INT NOT NULL REFERENCES calzado_sku(id_calzado_sku) ON DELETE RESTRICT ON UPDATE CASCADE,
    cantidad INT NOT NULL CHECK (cantidad > 0),
    unit_price NUMERIC(10,2) NOT NULL CHECK (unit_price > 0),
    descuento_unitario NUMERIC(10,2) NOT NULL DEFAULT 0.00 CHECK (descuento_unitario >= 0),
    line_total NUMERIC(10,2) NOT NULL CHECK (line_total >= 0)
);

-- Reservas Temporales en Checkout con TTL de 2 Horas
CREATE TABLE reserva_checkout (
    id_reserva SERIAL PRIMARY KEY,
    id_orden INT NOT NULL REFERENCES orden(id_orden) ON DELETE CASCADE ON UPDATE CASCADE,
    id_calzado_sku INT NOT NULL REFERENCES calzado_sku(id_calzado_sku) ON DELETE RESTRICT ON UPDATE CASCADE,
    id_bodega INT NOT NULL REFERENCES bodega(id_bodega) ON DELETE RESTRICT ON UPDATE CASCADE,
    cantidad INT NOT NULL CHECK (cantidad > 0),
    fecha_expiracion TIMESTAMPTZ NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVA' CHECK (estado IN ('ACTIVA', 'CONCRETADA', 'EXPIRADA_LIBERADA')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Captura de Demanda en Tallas Agotadas (Leads de Marketing y Compras)
CREATE TABLE alerta_demanda (
    id_alerta SERIAL PRIMARY KEY,
    id_calzado_sku INT NOT NULL REFERENCES calzado_sku(id_calzado_sku) ON DELETE CASCADE ON UPDATE CASCADE,
    email_cliente VARCHAR(100) NOT NULL,
    notificado BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
-- 8. PATRON DE FACTURACION (INVOICING) Y TRANSACCIONES FINANCIERAS
-- ============================================================================

-- Facturas / Comprobantes Fiscales Emitidos
CREATE TABLE factura (
    id_invoice SERIAL PRIMARY KEY,
    id_orden INT NOT NULL REFERENCES orden(id_orden) ON DELETE RESTRICT ON UPDATE CASCADE,
    id_customer INT NOT NULL REFERENCES customer(id_customer) ON DELETE RESTRICT ON UPDATE CASCADE,
    id_factura_tipo INT NOT NULL REFERENCES cat_factura_tipo(id_factura_tipo) ON DELETE RESTRICT ON UPDATE CASCADE,
    id_factura_estado INT NOT NULL REFERENCES cat_factura_estado(id_factura_estado) ON DELETE RESTRICT ON UPDATE CASCADE,
    numero_correlativo VARCHAR(35) NOT NULL UNIQUE,
    nit_cliente VARCHAR(30) NOT NULL DEFAULT 'CF',
    razon_social VARCHAR(120) NOT NULL,
    monto_subtotal NUMERIC(10,2) NOT NULL CHECK (monto_subtotal >= 0),
    monto_iva NUMERIC(10,2) NOT NULL CHECK (monto_iva >= 0),
    monto_recargo_pasarela NUMERIC(10,2) NOT NULL DEFAULT 0.00 CHECK (monto_recargo_pasarela >= 0),
    invoice_total NUMERIC(10,2) NOT NULL CHECK (invoice_total >= 0),
    es_simulado BOOLEAN NOT NULL DEFAULT TRUE,
    pdf_url VARCHAR(255),
    created_by INT REFERENCES app_user(id_user) ON DELETE RESTRICT ON UPDATE CASCADE,
    updated_by INT REFERENCES app_user(id_user) ON DELETE RESTRICT ON UPDATE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Líneas de Factura (Detalle de Facturación)
CREATE TABLE factura_linea (
    id_invoice_line SERIAL PRIMARY KEY,
    id_invoice INT NOT NULL REFERENCES factura(id_invoice) ON DELETE RESTRICT ON UPDATE CASCADE,
    id_calzado_sku INT NOT NULL REFERENCES calzado_sku(id_calzado_sku) ON DELETE RESTRICT ON UPDATE CASCADE,
    cantidad INT NOT NULL CHECK (cantidad > 0),
    unit_price NUMERIC(10,2) NOT NULL CHECK (unit_price > 0),
    line_total NUMERIC(10,2) NOT NULL CHECK (line_total >= 0)
);

-- Transacciones de Cobro y Pagos (Conciliación Bancaria y de Pasarela)
CREATE TABLE pago_transaccion (
    id_pago SERIAL PRIMARY KEY,
    id_invoice INT NOT NULL REFERENCES factura(id_invoice) ON DELETE RESTRICT ON UPDATE CASCADE,
    id_pago_metodo INT NOT NULL REFERENCES cat_pago_metodo(id_pago_metodo) ON DELETE RESTRICT ON UPDATE CASCADE,
    estado_pago VARCHAR(25) NOT NULL CHECK (estado_pago IN ('APROBADO', 'RECHAZADO', 'PENDIENTE')),
    monto_bruto NUMERIC(10,2) NOT NULL CHECK (monto_bruto >= 0),
    comision_retenida NUMERIC(10,2) NOT NULL DEFAULT 0.00 CHECK (comision_retenida >= 0),
    monto_neto_bancario NUMERIC(10,2) NOT NULL CHECK (monto_neto_bancario >= 0),
    referencia_transaccion VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Devoluciones Omnicanal (Criterio del Cajero hacia Bodega 2 o Bodega 3 Outlet)
CREATE TABLE devolucion (
    id_devolucion SERIAL PRIMARY KEY,
    numero_devolucion VARCHAR(35) NOT NULL UNIQUE,
    id_invoice_origen INT NOT NULL REFERENCES factura(id_invoice) ON DELETE RESTRICT ON UPDATE CASCADE,
    id_sucursal INT NOT NULL REFERENCES sucursal(id_sucursal) ON DELETE RESTRICT ON UPDATE CASCADE,
    id_user_cajero INT NOT NULL REFERENCES app_user(id_user) ON DELETE RESTRICT ON UPDATE CASCADE,
    id_bodega_destino INT NOT NULL REFERENCES bodega(id_bodega) ON DELETE RESTRICT ON UPDATE CASCADE,
    motivo_devolucion VARCHAR(60) NOT NULL,
    dias_transcurridos INT NOT NULL CHECK (dias_transcurridos >= 0),
    suela_intacta BOOLEAN NOT NULL,
    empaque_original BOOLEAN NOT NULL,
    monto_total_reembolso NUMERIC(10,2) NOT NULL CHECK (monto_total_reembolso >= 0),
    id_invoice_nota_credito INT REFERENCES factura(id_invoice) ON DELETE RESTRICT ON UPDATE CASCADE,
    created_by INT REFERENCES app_user(id_user) ON DELETE RESTRICT ON UPDATE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Líneas de Devolución
CREATE TABLE devolucion_linea (
    id_devolucion_linea SERIAL PRIMARY KEY,
    id_devolucion INT NOT NULL REFERENCES devolucion(id_devolucion) ON DELETE CASCADE ON UPDATE CASCADE,
    id_calzado_sku INT NOT NULL REFERENCES calzado_sku(id_calzado_sku) ON DELETE RESTRICT ON UPDATE CASCADE,
    cantidad INT NOT NULL CHECK (cantidad > 0),
    unit_price NUMERIC(10,2) NOT NULL CHECK (unit_price > 0),
    estado_calzado VARCHAR(30) NOT NULL CHECK (estado_calzado IN ('APTO_PRODUCTO_ACTUAL', 'DANADO_OUTLET'))
);

-- ============================================================================
-- 9. LOGISTICA, DESPACHO Y VALIDACION CON CODIGO OTP
-- ============================================================================

-- Guías de Despacho y Envío
CREATE TABLE despacho_envio (
    id_despacho SERIAL PRIMARY KEY,
    id_orden INT NOT NULL UNIQUE REFERENCES orden(id_orden) ON DELETE RESTRICT ON UPDATE CASCADE,
    tipo_transporte VARCHAR(30) NOT NULL CHECK (tipo_transporte IN ('FLOTA_PROPIA_LOCAL', 'COURIER_EXTERNO')),
    courier_nombre VARCHAR(80),
    id_user_chofer INT REFERENCES app_user(id_user) ON DELETE RESTRICT ON UPDATE CASCADE,
    numero_guia_tracking VARCHAR(50) NOT NULL UNIQUE,
    codigo_secreto_otp CHAR(6) NOT NULL,
    id_despacho_estado INT NOT NULL REFERENCES cat_despacho_estado(id_despacho_estado) ON DELETE RESTRICT ON UPDATE CASCADE,
    intentos_entrega SMALLINT NOT NULL DEFAULT 0 CHECK (intentos_entrega >= 0),
    otp_verificado BOOLEAN NOT NULL DEFAULT FALSE,
    fecha_preparacion_bodega TIMESTAMPTZ,
    fecha_salida_ruta TIMESTAMPTZ,
    fecha_entrega_real TIMESTAMPTZ,
    sla_limite_preparacion_24h TIMESTAMPTZ NOT NULL,
    sla_limite_entrega_5d TIMESTAMPTZ NOT NULL,
    created_by INT REFERENCES app_user(id_user) ON DELETE RESTRICT ON UPDATE CASCADE,
    updated_by INT REFERENCES app_user(id_user) ON DELETE RESTRICT ON UPDATE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
-- 10. COMPRAS, PROVEEDORES Y ACONDICIONAMIENTO EN BODEGA 1
-- ============================================================================

-- Proveedores y Fabricantes de Calzado
CREATE TABLE proveedor (
    id_proveedor SERIAL PRIMARY KEY,
    razon_social VARCHAR(120) NOT NULL,
    contacto_nombre VARCHAR(80),
    telefono VARCHAR(25),
    email VARCHAR(100),
    direccion TEXT,
    lead_time_dias_promedio INT NOT NULL DEFAULT 7 CHECK (lead_time_dias_promedio > 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by INT REFERENCES app_user(id_user) ON DELETE RESTRICT ON UPDATE CASCADE,
    updated_by INT REFERENCES app_user(id_user) ON DELETE RESTRICT ON UPDATE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Órdenes de Compra por Tallas Libres
CREATE TABLE orden_compra (
    id_orden_compra SERIAL PRIMARY KEY,
    numero_oc VARCHAR(35) NOT NULL UNIQUE,
    id_proveedor INT NOT NULL REFERENCES proveedor(id_proveedor) ON DELETE RESTRICT ON UPDATE CASCADE,
    id_user_compras INT NOT NULL REFERENCES app_user(id_user) ON DELETE RESTRICT ON UPDATE CASCADE,
    estado VARCHAR(30) NOT NULL DEFAULT 'BORRADOR' CHECK (estado IN ('BORRADOR', 'EMITIDA', 'RECIBIDA_PARCIAL', 'RECIBIDA_COMPLETA', 'CANCELADA')),
    total_estimado NUMERIC(12,2) NOT NULL CHECK (total_estimado >= 0),
    created_by INT REFERENCES app_user(id_user) ON DELETE RESTRICT ON UPDATE CASCADE,
    updated_by INT REFERENCES app_user(id_user) ON DELETE RESTRICT ON UPDATE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Líneas de Orden de Compra
CREATE TABLE orden_compra_linea (
    id_oc_linea SERIAL PRIMARY KEY,
    id_orden_compra INT NOT NULL REFERENCES orden_compra(id_orden_compra) ON DELETE CASCADE ON UPDATE CASCADE,
    id_calzado_sku INT NOT NULL REFERENCES calzado_sku(id_calzado_sku) ON DELETE RESTRICT ON UPDATE CASCADE,
    cantidad_solicitada_libre INT NOT NULL CHECK (cantidad_solicitada_libre > 0),
    unit_cost NUMERIC(10,2) NOT NULL CHECK (unit_cost > 0)
);

-- Recepciones Físicas en Bodega 1 y Cotejo Trilateral
CREATE TABLE recepcion_mercancia (
    id_recepcion SERIAL PRIMARY KEY,
    id_orden_compra INT NOT NULL REFERENCES orden_compra(id_orden_compra) ON DELETE RESTRICT ON UPDATE CASCADE,
    id_bodega INT NOT NULL REFERENCES bodega(id_bodega) ON DELETE RESTRICT ON UPDATE CASCADE,
    id_user_recepcion INT NOT NULL REFERENCES app_user(id_user) ON DELETE RESTRICT ON UPDATE CASCADE,
    numero_factura_proveedor VARCHAR(60) NOT NULL,
    tiene_discrepancias BOOLEAN NOT NULL DEFAULT FALSE,
    acta_discrepancia_texto TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Lotes de Acondicionamiento e Inspección de Calidad en Bodega 1
CREATE TABLE lote_acondicionamiento (
    id_lote SERIAL PRIMARY KEY,
    id_recepcion INT NOT NULL REFERENCES recepcion_mercancia(id_recepcion) ON DELETE RESTRICT ON UPDATE CASCADE,
    id_user_revisor INT NOT NULL REFERENCES app_user(id_user) ON DELETE RESTRICT ON UPDATE CASCADE,
    id_calzado_sku INT NOT NULL REFERENCES calzado_sku(id_calzado_sku) ON DELETE RESTRICT ON UPDATE CASCADE,
    cantidad_inspeccionada INT NOT NULL CHECK (cantidad_inspeccionada > 0),
    cantidad_aprobada INT NOT NULL DEFAULT 0 CHECK (cantidad_aprobada >= 0),
    cantidad_rechazada_merma INT NOT NULL DEFAULT 0 CHECK (cantidad_rechazada_merma >= 0),
    promovido_a_bodega2 BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
-- 11. GOBERNANZA, CONTROL DE GESTION Y RECLAMACIONES
-- ============================================================================

-- Solicitudes de Cambio de Precio en Lote (Ventas solicita -> Gerente General aprueba)
CREATE TABLE solicitud_cambio_precio (
    id_solicitud_precio SERIAL PRIMARY KEY,
    id_user_ventas INT NOT NULL REFERENCES app_user(id_user) ON DELETE RESTRICT ON UPDATE CASCADE,
    id_user_gerente INT REFERENCES app_user(id_user) ON DELETE RESTRICT ON UPDATE CASCADE,
    tipo_solicitud VARCHAR(30) NOT NULL CHECK (tipo_solicitud IN ('REBAJA_OUTLET', 'ACTUALIZACION_LISTA', 'NUEVO_PRODUCTO')),
    estado VARCHAR(30) NOT NULL DEFAULT 'PENDIENTE_APROBACION' CHECK (estado IN ('PENDIENTE_APROBACION', 'APROBADA', 'RECHAZADA')),
    motivo_rechazo TEXT,
    created_by INT REFERENCES app_user(id_user) ON DELETE RESTRICT ON UPDATE CASCADE,
    updated_by INT REFERENCES app_user(id_user) ON DELETE RESTRICT ON UPDATE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Líneas de Productos en la Solicitud de Cambio de Precio
CREATE TABLE solicitud_cambio_precio_linea (
    id_solicitud_precio_linea SERIAL PRIMARY KEY,
    id_solicitud_precio INT NOT NULL REFERENCES solicitud_cambio_precio(id_solicitud_precio) ON DELETE CASCADE ON UPDATE CASCADE,
    id_modelo INT NOT NULL REFERENCES modelo(id_modelo) ON DELETE RESTRICT ON UPDATE CASCADE,
    precio_anterior NUMERIC(10,2) NOT NULL CHECK (precio_anterior > 0),
    precio_nuevo_propuesto NUMERIC(10,2) NOT NULL CHECK (precio_nuevo_propuesto > 0),
    porcentaje_descuento_outlet NUMERIC(5,2) NOT NULL DEFAULT 0.00 CHECK (porcentaje_descuento_outlet >= 0 AND porcentaje_descuento_outlet <= 100)
);

-- Help Desk y Reclamaciones (Control del 40% de quejas y monitor cada 6h)
CREATE TABLE ticket_soporte (
    id_ticket SERIAL PRIMARY KEY,
    numero_ticket VARCHAR(35) NOT NULL UNIQUE,
    id_customer INT NOT NULL REFERENCES customer(id_customer) ON DELETE RESTRICT ON UPDATE CASCADE,
    id_orden INT REFERENCES orden(id_orden) ON DELETE RESTRICT ON UPDATE CASCADE,
    id_motivo_queja INT NOT NULL REFERENCES cat_motivo_queja(id_motivo_queja) ON DELETE RESTRICT ON UPDATE CASCADE,
    id_ticket_estado INT NOT NULL REFERENCES cat_ticket_estado(id_ticket_estado) ON DELETE RESTRICT ON UPDATE CASCADE,
    area_derivada VARCHAR(30) NOT NULL DEFAULT 'NINGUNA' CHECK (area_derivada IN ('NINGUNA', 'BODEGA', 'LOGISTICA_TRANSPORTE', 'FINANZAS')),
    descripcion TEXT NOT NULL,
    resolucion_texto TEXT,
    fecha_ultima_interaccion TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    alerta_estancado_6h_disparada BOOLEAN NOT NULL DEFAULT FALSE,
    created_by INT REFERENCES app_user(id_user) ON DELETE RESTRICT ON UPDATE CASCADE,
    updated_by INT REFERENCES app_user(id_user) ON DELETE RESTRICT ON UPDATE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
-- 12. PATRON DE AUDITORIA AVANZADA Y BITACORA (audit_log y audit_trail)
-- ============================================================================

-- Bitácora de Accesos y Operaciones Generales (INSERT-ONLY)
CREATE TABLE audit_log (
    id_log BIGSERIAL PRIMARY KEY,
    table_name VARCHAR(150),
    record_pk VARCHAR(200),
    id_log_accion INT NOT NULL REFERENCES cat_log_accion(id_log_accion) ON DELETE RESTRICT ON UPDATE CASCADE,
    id_user INT REFERENCES app_user(id_user) ON DELETE RESTRICT ON UPDATE CASCADE,
    data JSONB,
    ip_address VARCHAR(80),
    user_agent VARCHAR(255),
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Auditoría Avanzada de Estados de Datos (data_before y data_after JSON)
CREATE TABLE audit_trail (
    id_audit BIGSERIAL PRIMARY KEY,
    table_name VARCHAR(150) NOT NULL,
    record_pk VARCHAR(200) NOT NULL,
    id_auditoria_accion INT NOT NULL REFERENCES cat_auditoria_accion(id_auditoria_accion) ON DELETE RESTRICT ON UPDATE CASCADE,
    id_user INT REFERENCES app_user(id_user) ON DELETE RESTRICT ON UPDATE CASCADE,
    data_before JSONB,
    data_after JSONB,
    ip_address VARCHAR(80),
    user_agent VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
-- 13. TRIGGERS DE AUDITORIA Y BLOQUEO DE BORRADO FISICO (HARD RULES)
-- ============================================================================

-- Triggers automáticos para actualizar updated_at
CREATE TRIGGER tr_upd_modelo BEFORE UPDATE ON modelo FOR EACH ROW EXECUTE FUNCTION fn_update_timestamp();
CREATE TRIGGER tr_upd_modelo_color BEFORE UPDATE ON modelo_color FOR EACH ROW EXECUTE FUNCTION fn_update_timestamp();
CREATE TRIGGER tr_upd_calzado_sku BEFORE UPDATE ON calzado_sku FOR EACH ROW EXECUTE FUNCTION fn_update_timestamp();
CREATE TRIGGER tr_upd_bodega BEFORE UPDATE ON bodega FOR EACH ROW EXECUTE FUNCTION fn_update_timestamp();
CREATE TRIGGER tr_upd_sucursal BEFORE UPDATE ON sucursal FOR EACH ROW EXECUTE FUNCTION fn_update_timestamp();
CREATE TRIGGER tr_upd_customer BEFORE UPDATE ON customer FOR EACH ROW EXECUTE FUNCTION fn_update_timestamp();
CREATE TRIGGER tr_upd_orden BEFORE UPDATE ON orden FOR EACH ROW EXECUTE FUNCTION fn_update_timestamp();
CREATE TRIGGER tr_upd_factura BEFORE UPDATE ON factura FOR EACH ROW EXECUTE FUNCTION fn_update_timestamp();
CREATE TRIGGER tr_upd_despacho_envio BEFORE UPDATE ON despacho_envio FOR EACH ROW EXECUTE FUNCTION fn_update_timestamp();
CREATE TRIGGER tr_upd_proveedor BEFORE UPDATE ON proveedor FOR EACH ROW EXECUTE FUNCTION fn_update_timestamp();
CREATE TRIGGER tr_upd_orden_compra BEFORE UPDATE ON orden_compra FOR EACH ROW EXECUTE FUNCTION fn_update_timestamp();
CREATE TRIGGER tr_upd_ticket_soporte BEFORE UPDATE ON ticket_soporte FOR EACH ROW EXECUTE FUNCTION fn_update_timestamp();
CREATE TRIGGER tr_upd_solicitud_precio BEFORE UPDATE ON solicitud_cambio_precio FOR EACH ROW EXECUTE FUNCTION fn_update_timestamp();
CREATE TRIGGER tr_upd_solicitud_abastecimiento BEFORE UPDATE ON solicitud_abastecimiento FOR EACH ROW EXECUTE FUNCTION fn_update_timestamp();
CREATE TRIGGER tr_upd_solicitud_baja BEFORE UPDATE ON solicitud_baja_stock FOR EACH ROW EXECUTE FUNCTION fn_update_timestamp();
CREATE TRIGGER tr_upd_app_user BEFORE UPDATE ON app_user FOR EACH ROW EXECUTE FUNCTION fn_update_timestamp();

-- Triggers de protección inmutable contra DELETE físico
CREATE TRIGGER tr_no_del_factura BEFORE DELETE ON factura FOR EACH ROW EXECUTE FUNCTION fn_prevent_delete_financial();
CREATE TRIGGER tr_no_del_factura_linea BEFORE DELETE ON factura_linea FOR EACH ROW EXECUTE FUNCTION fn_prevent_delete_financial();
CREATE TRIGGER tr_no_del_pago BEFORE DELETE ON pago_transaccion FOR EACH ROW EXECUTE FUNCTION fn_prevent_delete_financial();
CREATE TRIGGER tr_no_del_kardex BEFORE DELETE ON kardex_movimiento FOR EACH ROW EXECUTE FUNCTION fn_prevent_delete_financial();
CREATE TRIGGER tr_no_del_audit_log BEFORE DELETE ON audit_log FOR EACH ROW EXECUTE FUNCTION fn_prevent_delete_financial();
CREATE TRIGGER tr_no_del_audit_trail BEFORE DELETE ON audit_trail FOR EACH ROW EXECUTE FUNCTION fn_prevent_delete_financial();

-- ============================================================================
-- 14. INDICES DE ALTO RENDIMIENTO (OPTIMIZACION DE BUSQUEDA Y CHECKOUT)
-- ============================================================================

-- Búsqueda rápida de SKU
CREATE INDEX idx_calzado_sku_code ON calzado_sku (sku);
CREATE INDEX idx_calzado_sku_ean ON calzado_sku (codigo_barras_ean);

-- Filtros facetados de catálogo web
CREATE INDEX idx_modelo_filtros ON modelo (id_tipo_calzado, id_marca, genero, es_visible_web, es_outlet);
CREATE INDEX idx_modelo_color_busqueda ON modelo_color (id_modelo, id_color);

-- Control de stock y reservas transaccionales en checkout
CREATE INDEX idx_stock_bodega_sku ON inventario_stock (id_bodega, id_calzado_sku);
CREATE INDEX idx_reserva_activa_expiracion ON reserva_checkout (estado, fecha_expiracion);

-- Búsqueda de órdenes y comprobantes
CREATE INDEX idx_orden_customer ON orden (id_customer, id_orden_estado);
CREATE INDEX idx_orden_fecha ON orden (created_at);
CREATE INDEX idx_factura_orden ON factura (id_orden);
CREATE INDEX idx_factura_correlativo ON factura (numero_correlativo);

-- Logística y validación de OTP
CREATE INDEX idx_despacho_otp_tracking ON despacho_envio (numero_guia_tracking, codigo_secreto_otp);

-- Auditoría y Bitácora
CREATE INDEX idx_kardex_bodega_fecha ON kardex_movimiento (id_bodega, created_at);
CREATE INDEX idx_audit_log_user_fecha ON audit_log (id_user, created_at);
CREATE INDEX idx_audit_trail_table_pk ON audit_trail (table_name, record_pk);

-- Help Desk
CREATE INDEX idx_ticket_estado_fecha ON ticket_soporte (id_ticket_estado, fecha_ultima_interaccion);

-- Comisiones
CREATE INDEX idx_comision_user_mes ON comision_empleado (id_user, mes_anio);
