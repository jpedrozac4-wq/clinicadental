-- ============================================================================
-- SISTEMA INTEGRAL ERP Y E-COMMERCE B2C FUNCIONAL - INDUSTRIA DEL CALZADO
-- SCRIPT DE DATOS SEMILLA (seed_data.sql) - PostgreSQL 16
-- Basado en: database-standard-design (OpenCode / Gentleman Programming)
-- Version: 2.1.0
-- Credencial de prueba para todos los usuarios: Password123!
-- Hash BCrypt: $2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewdBPj4D4g3Y2m8G
-- ============================================================================

SET client_encoding = 'UTF8';

-- ============================================================================
-- 1. POBLADO DE CATALOGOS MAESTROS (cat_*)
-- ============================================================================

-- Tipos de Calzado
INSERT INTO cat_tipo_calzado (codigo_letra, nombre, descripcion) VALUES
('T', 'Tenis', 'Calzado deportivo y urbano casual con suela de goma'),
('B', 'Botas', 'Calzado de caña media y alta para trabajo, montaña y uso rudo'),
('Z', 'Zapatos', 'Calzado formal y de vestir en cuero legítimo'),
('S', 'Sandalias', 'Calzado abierto para temporada de verano y playa');

-- Marcas Comerciales
INSERT INTO cat_marca (codigo_letra, nombre, pais_origen) VALUES
('V', 'Vans', 'Estados Unidos'),
('G', 'Grangler', 'México'),
('N', 'Nike', 'Estados Unidos'),
('A', 'Adidas', 'Alemania');

-- Colores
INSERT INTO cat_color (nombre, codigo_hex) VALUES
('Verde', '#2E7D32'),
('Café', '#6D4C41'),
('Negro', '#212121'),
('Blanco', '#FAFAFA'),
('Azul Marino', '#0D47A1'),
('Miel / Camel', '#C19A6B');

-- Tipos de Bodega
INSERT INTO cat_bodega_tipo (code, description) VALUES
('RECEPCION', 'Bodega de nuevo producto recibido en revisión y acondicionamiento (bloqueada para venta)'),
('ACTUAL', 'Bodega central operativa para despacho de ventas físicas y e-commerce'),
('LIQUIDACION', 'Bodega de calzado en liquidación y saldos outlet con precios rebajados');

-- Estados de la Orden
INSERT INTO cat_orden_estado (code, description) VALUES
('PENDIENTE_PAGO', 'Orden creada en checkout en espera de acreditación de fondos (TTL 2h activo)'),
('PAGADA', 'Pago acreditado exitosamente y orden enviada a cola de picking'),
('EN_PREPARACION', 'Personal de bodega realizando recolección e inspección de empaque'),
('LISTO_DESPACHO', 'Paquete embalado listo para entrega a chofer o courier'),
('EXPIRADA_CANCELADA', 'Orden cancelada automáticamente por expiración de TTL de 2 horas o petición del cliente');

-- Métodos de Pago
INSERT INTO cat_pago_metodo (code, description, comision_porcentaje, comision_fija) VALUES
('TARJETA_SIMULADA', 'Tarjeta de crédito o débito (Pasarela simulada con traslado de recargo)', 3.50, 0.30),
('TRANSFERENCIA', 'Transferencia bancaria local con verificación de comprobante', 0.00, 0.00),
('EFECTIVO_CONTRA_ENTREGA', 'Cobro en efectivo al momento de la entrega física en domicilio', 0.00, 0.00),
('BILLETERA_SALDO_FAVOR', 'Pago debitado del saldo a favor / monedero digital del cliente', 0.00, 0.00),
('EFECTIVO', 'Cobro en efectivo en caja del punto de venta (POS)', 0.00, 0.00);

-- Tipos de Factura / Comprobante
INSERT INTO cat_factura_tipo (code, description) VALUES
('FACTURA', 'Factura comercial formal con desglose impositivo de IVA'),
('BOLETA', 'Boleta simplificada de venta a consumidor final'),
('NOTA_CREDITO', 'Comprobante de crédito fiscal por devoluciones o cancelaciones');

-- Estados de Factura
INSERT INTO cat_factura_estado (code, description) VALUES
('EMITIDA', 'Comprobante emitido válidamente en el sistema'),
('ANULADA', 'Comprobante anulado formalmente por nota de crédito'),
('CONCILIADA', 'Comprobante cuadrado contra el depósito bancario de la pasarela');

-- Estados de Despacho y Tracking
INSERT INTO cat_despacho_estado (code, description) VALUES
('PREPARADO', 'Pedido empacado e inspeccionado en bodega central'),
('CARGADO', 'Paquete asignado y cargado en el vehículo de reparto'),
('EN_RUTA', 'Transportista en trayecto hacia el domicilio de entrega'),
('ENTREGADO', 'Entrega física completada y validada mediante código secreto OTP');

-- Motivos de Reclamación / Help Desk
INSERT INTO cat_motivo_queja (code, description) VALUES
('RETRASO_ENTREGA', 'El pedido superó el tiempo comprometido de entrega'),
('TALLA_MODELO_INCORRECTO', 'El calzado recibido no corresponde a la talla o modelo solicitado'),
('DEFECTO_FABRICACION', 'El calzado presenta costuras rotas, manchas o falla de suela'),
('PRODUCTO_NO_DISPONIBLE', 'Venta realizada con stock inexistente o quiebre de inventario'),
('PROBLEMA_COBRO', 'Cobro duplicado o error de procesamiento en pasarela'),
('DISCREPANCIA_VISUAL_WEB', 'El color o acabado real del calzado no coincide con la fotografía web');

-- Estados de Ticket de Soporte
INSERT INTO cat_ticket_estado (code, description) VALUES
('NUEVO', 'Ticket radicado por el cliente pendiente de asignación'),
('EN_ANALISIS', 'Operador de soporte evaluando la incidencia'),
('EN_BODEGA_TRANSPORTE', 'Ticket derivado al área de almacén o logística para aclaración'),
('RESUELTO', 'Incidencia solucionada (reposición, nota de crédito o entrega)'),
('CERRADO', 'Ticket concluido con conformidad del cliente');

-- Tipos de Movimiento en Kardex
INSERT INTO cat_movimiento_stock_tipo (code, description, es_incremento) VALUES
('INGRESO_PROVEEDOR', 'Entrada inicial de mercancía acondicionada desde Bodega 1', TRUE),
('SALIDA_VENTA_POS', 'Descarga de inventario por venta presencial en tienda física', FALSE),
('SALIDA_VENTA_WEB', 'Descarga de inventario por venta completada en e-commerce', FALSE),
('BLOQUEO_CHECKOUT', 'Reserva temporal de inventario durante el proceso de pago', FALSE),
('LIBERACION_TTL', 'Reingreso de inventario por expiración de reserva de 2 horas', TRUE),
('TRASPASO_SALIDA', 'Salida de stock por solicitud de abastecimiento a tienda', FALSE),
('TRASPASO_ENTRADA', 'Ingreso de stock confirmado en tienda receptora', TRUE),
('BAJA_MERMA', 'Baja definitiva de inventario por rotura aprobada por gerencia', FALSE),
('REINGRESO_DEVOLUCION', 'Reingreso de producto apto devuelto por el cliente', TRUE);

-- Tipos de Acción en Bitácora (audit_log)
INSERT INTO cat_log_accion (code, description) VALUES
('LOG', 'Evento general de registro en sistema'),
('ACCESS', 'Inicio de sesión exitoso'),
('FAILED_ATTEMPT', 'Intento de inicio de sesión fallido'),
('LOCKED', 'Cuenta de usuario bloqueada por intentos fallidos'),
('UNLOCKED', 'Cuenta de usuario desbloqueada por administrador'),
('EXPORT', 'Exportación de datos confidenciales a PDF o Excel'),
('CONSULT', 'Consulta de reportes gerenciales o auditoría');

-- Tipos de Acción en Auditoría Avanzada (audit_trail)
INSERT INTO cat_auditoria_accion (code, description) VALUES
('INSERT', 'Creación de nuevo registro operativo'),
('UPDATE', 'Modificación de estado o datos de registro'),
('DELETE', 'Eliminación lógica de registro'),
('VOID', 'Anulación formal de comprobante o documento');

-- ============================================================================
-- 2. ROLES, PERMISOS Y MATRIZ RBAC (RBAC Pattern)
-- ============================================================================

-- Inserción de los 9 Roles Corporativos
INSERT INTO role (role_name, description) VALUES
('ROLE_ADMIN', 'Administrador total de sistemas, usuarios y configuraciones técnicas'),
('ROLE_GERENTE_GENERAL', 'Máxima autoridad ejecutiva; aprueba precios y bajas de stock'),
('ROLE_GERENTE_FINANCIERO', 'Control de finanzas, conciliación bancaria y balances contables'),
('ROLE_GERENTE_VENTAS', 'Estrategia comercial, promociones, outlets y formulación de precios'),
('ROLE_ENCARGADO_ALMACEN', 'Control de bodegas, transferencias y solicitudes de baja por merma'),
('ROLE_AUXILIAR_ALMACEN', 'Operaciones de picking, empaque y acondicionamiento en Bodega 1'),
('ROLE_CAJERO', 'Atención en terminal POS físico, cobro a clientes y devoluciones'),
('ROLE_SOPORTE', 'Atención de tickets de reclamación y seguimiento de clientes'),
('ROLE_CONTADOR', 'Libro diario, emisión de facturación y notas de crédito');

-- Inserción del Catálogo Granular de Permisos
INSERT INTO permission (permission_name, modulo, action, description) VALUES
('CATALOGO_VER', 'CATALOGO', 'READ', 'Visualizar catálogo maestro de modelos, variantes y SKUs'),
('CATALOGO_CREAR_EDITAR', 'CATALOGO', 'CREATE', 'Crear y modificar fichas técnicas de calzado'),
('PRECIOS_SOLICITAR_CAMBIO', 'DIRECCION', 'CREATE', 'Formular solicitudes de cambio de precios y liquidación outlet'),
('PRECIOS_APROBAR_CAMBIO', 'DIRECCION', 'APPROVE', 'Aprobar o rechazar modificaciones de precios en lote'),
('STOCK_CONSULTAR', 'BODEGA', 'READ', 'Consultar saldos de stock físico y reservado en tiempo real'),
('ABASTECIMIENTO_SOLICITAR', 'BODEGA', 'CREATE', 'Emitir solicitudes de reposición interna entre tiendas y almacén'),
('ABASTECIMIENTO_DESPACHAR', 'BODEGA', 'UPDATE', 'Despachar y confirmar guías de traspaso interno'),
('STOCK_BAJA_SOLICITAR', 'BODEGA', 'CREATE', 'Emitir solicitudes de baja de calzado dañado o mermas'),
('STOCK_BAJA_APROBAR', 'BODEGA', 'APPROVE', 'Aprobar bajas de stock y ajustes de inventario mensual'),
('KARDEX_AUDITAR', 'BODEGA', 'READ', 'Consultar el libro mayor de movimientos de inventario'),
('POS_VENTA_COBRAR', 'VENTAS', 'CREATE', 'Efectuar cobros y emitir tickets en punto de venta presencial'),
('POS_CAJA_APERTURA_CIERRE', 'VENTAS', 'UPDATE', 'Abrir y cerrar turnos de caja con arqueo de dinero'),
('POS_ARQUEO_AUDITAR', 'FINANZAS', 'READ', 'Auditar diferencias de caja y cortes de turno'),
('DEVOLUCION_PROCESAR', 'VENTAS', 'CREATE', 'Procesar cambios y devoluciones físicas con inspección de calzado'),
('PICKING_PACKING_BODEGA', 'LOGISTICA', 'UPDATE', 'Marcar órdenes en recolección y listas para despacho'),
('DESPACHO_ASIGNAR_RUTA', 'LOGISTICA', 'UPDATE', 'Asignar choferes propios o couriers a los pedidos'),
('DESPACHO_CONFIRMAR_OTP', 'LOGISTICA', 'UPDATE', 'Ingresar código OTP del cliente y cerrar entrega física'),
('COMPRAS_SOLICITAR', 'COMPRAS', 'CREATE', 'Emitir requerimientos de compra por rotación mensual'),
('COMPRAS_ORDEN_EMITIR', 'COMPRAS', 'APPROVE', 'Aprobar y emitir órdenes de compra formales a fabricantes'),
('RECEPCION_BODEGA1_REGISTRAR', 'COMPRAS', 'CREATE', 'Registrar recepción de calzado en cuarentena en Bodega 1'),
('ACONDICIONAMIENTO_APROBAR', 'PRODUCCION', 'APPROVE', 'Aprobar lote de calzado y transferirlo a Bodega 2 (Actual)'),
('FACTURAS_NOTAS_CREDITO_EMITIR', 'FINANZAS', 'CREATE', 'Emitir comprobantes fiscales y notas de crédito de reembolso'),
('CONCILIACION_PASARELA_VER', 'FINANZAS', 'READ', 'Auditar ingresos brutos, retenciones y neto bancario'),
('TICKETS_GESTIONAR_DERIVAR', 'SOPORTE', 'UPDATE', 'Atender tickets de queja y derivar a bodega o transporte'),
('TICKETS_SUPERVISAR_ALERTAS', 'SOPORTE', 'READ', 'Monitorear alarmas de tickets estancados cada 6 horas'),
('USUARIOS_ROLES_ADMIN', 'ADMIN', 'UPDATE', 'Gestionar cuentas de usuario y asignación de roles'),
('AUDITORIA_LOGS_VER', 'ADMIN', 'READ', 'Consultar bitácora general y pistas de auditoría avanzada');

-- Poblar la Matriz de Permisos (role_permission)
-- 1. ROLE_ADMIN (Permisos totales de sistema y auditoría)
INSERT INTO role_permission (id_role, id_permission)
SELECT r.id_role, p.id_permission FROM role r, permission p 
WHERE r.role_name = 'ROLE_ADMIN' AND p.permission_name IN (
    'CATALOGO_VER', 'CATALOGO_CREAR_EDITAR', 'STOCK_CONSULTAR', 'KARDEX_AUDITAR', 
    'POS_ARQUEO_AUDITAR', 'USUARIOS_ROLES_ADMIN', 'AUDITORIA_LOGS_VER', 'TICKETS_SUPERVISAR_ALERTAS'
);

-- 2. ROLE_GERENTE_GENERAL (Aprobaciones máximas y visión directiva)
INSERT INTO role_permission (id_role, id_permission)
SELECT r.id_role, p.id_permission FROM role r, permission p 
WHERE r.role_name = 'ROLE_GERENTE_GENERAL' AND p.permission_name IN (
    'CATALOGO_VER', 'CATALOGO_CREAR_EDITAR', 'PRECIOS_APROBAR_CAMBIO', 'STOCK_CONSULTAR',
    'STOCK_BAJA_APROBAR', 'KARDEX_AUDITAR', 'POS_ARQUEO_AUDITAR', 'COMPRAS_ORDEN_EMITIR',
    'CONCILIACION_PASARELA_VER', 'TICKETS_SUPERVISAR_ALERTAS', 'AUDITORIA_LOGS_VER'
);

-- 3. ROLE_GERENTE_FINANCIERO (Control financiero, conciliación y auditoría de cajas)
INSERT INTO role_permission (id_role, id_permission)
SELECT r.id_role, p.id_permission FROM role r, permission p 
WHERE r.role_name = 'ROLE_GERENTE_FINANCIERO' AND p.permission_name IN (
    'CATALOGO_VER', 'STOCK_CONSULTAR', 'KARDEX_AUDITAR', 'POS_ARQUEO_AUDITAR',
    'COMPRAS_ORDEN_EMITIR', 'FACTURAS_NOTAS_CREDITO_EMITIR', 'CONCILIACION_PASARELA_VER'
);

-- 4. ROLE_GERENTE_VENTAS (Formulación de precios, outlets, ventas y promociones)
INSERT INTO role_permission (id_role, id_permission)
SELECT r.id_role, p.id_permission FROM role r, permission p 
WHERE r.role_name = 'ROLE_GERENTE_VENTAS' AND p.permission_name IN (
    'CATALOGO_VER', 'CATALOGO_CREAR_EDITAR', 'PRECIOS_SOLICITAR_CAMBIO', 'STOCK_CONSULTAR',
    'POS_VENTA_COBRAR', 'POS_ARQUEO_AUDITAR', 'DEVOLUCION_PROCESAR', 'COMPRAS_SOLICITAR',
    'TICKETS_SUPERVISAR_ALERTAS'
);

-- 5. ROLE_ENCARGADO_ALMACEN (Control de bodegas, traspasos, bajas y acondicionamiento)
INSERT INTO role_permission (id_role, id_permission)
SELECT r.id_role, p.id_permission FROM role r, permission p 
WHERE r.role_name = 'ROLE_ENCARGADO_ALMACEN' AND p.permission_name IN (
    'CATALOGO_VER', 'STOCK_CONSULTAR', 'ABASTECIMIENTO_SOLICITAR', 'ABASTECIMIENTO_DESPACHAR',
    'STOCK_BAJA_SOLICITAR', 'KARDEX_AUDITAR', 'PICKING_PACKING_BODEGA', 'DESPACHO_ASIGNAR_RUTA',
    'COMPRAS_SOLICITAR', 'RECEPCION_BODEGA1_REGISTRAR', 'ACONDICIONAMIENTO_APROBAR',
    'TICKETS_SUPERVISAR_ALERTAS'
);

-- 6. ROLE_AUXILIAR_ALMACEN (Picking, empaque y acondicionamiento físico)
INSERT INTO role_permission (id_role, id_permission)
SELECT r.id_role, p.id_permission FROM role r, permission p 
WHERE r.role_name = 'ROLE_AUXILIAR_ALMACEN' AND p.permission_name IN (
    'CATALOGO_VER', 'STOCK_CONSULTAR', 'ABASTECIMIENTO_DESPACHAR', 'PICKING_PACKING_BODEGA',
    'DESPACHO_CONFIRMAR_OTP', 'RECEPCION_BODEGA1_REGISTRAR'
);

-- 7. ROLE_CAJERO (Atención de mostrador, cobro POS, arqueos y devoluciones)
INSERT INTO role_permission (id_role, id_permission)
SELECT r.id_role, p.id_permission FROM role r, permission p 
WHERE r.role_name = 'ROLE_CAJERO' AND p.permission_name IN (
    'CATALOGO_VER', 'STOCK_CONSULTAR', 'ABASTECIMIENTO_SOLICITAR', 'POS_VENTA_COBRAR',
    'POS_CAJA_APERTURA_CIERRE', 'DEVOLUCION_PROCESAR', 'FACTURAS_NOTAS_CREDITO_EMITIR'
);

-- 8. ROLE_SOPORTE (Help desk, atención de reclamos y derivaciones)
INSERT INTO role_permission (id_role, id_permission)
SELECT r.id_role, p.id_permission FROM role r, permission p 
WHERE r.role_name = 'ROLE_SOPORTE' AND p.permission_name IN (
    'CATALOGO_VER', 'STOCK_CONSULTAR', 'TICKETS_GESTIONAR_DERIVAR'
);

-- 9. ROLE_CONTADOR (Facturación formal, notas de crédito y libro mayor)
INSERT INTO role_permission (id_role, id_permission)
SELECT r.id_role, p.id_permission FROM role r, permission p 
WHERE r.role_name = 'ROLE_CONTADOR' AND p.permission_name IN (
    'CATALOGO_VER', 'STOCK_CONSULTAR', 'KARDEX_AUDITAR', 'POS_ARQUEO_AUDITAR',
    'FACTURAS_NOTAS_CREDITO_EMITIR', 'CONCILIACION_PASARELA_VER', 'AUDITORIA_LOGS_VER'
);

-- Inserción de Usuarios de Prueba con BCrypt ($2a$12$... Password123!)
INSERT INTO app_user (username, password_hash, full_name, email) VALUES
('admin', '$2a$12$7MAPdVu04Z5HHVfwqg.FwuGL1jWOzkmZQ8GKuk8XoC5P/tLUoKOXS', 'Administrador de Sistemas', 'admin@calzadoempresa.com'),
('gerentegeneral', '$2a$12$7MAPdVu04Z5HHVfwqg.FwuGL1jWOzkmZQ8GKuk8XoC5P/tLUoKOXS', 'Lic. Roberto Alarcón (Gerente General)', 'roberto.alarcon@calzadoempresa.com'),
('gerentefinanciero', '$2a$12$7MAPdVu04Z5HHVfwqg.FwuGL1jWOzkmZQ8GKuk8XoC5P/tLUoKOXS', 'Lic. Mariana Gómez (Gerente Financiero)', 'mariana.gomez@calzadoempresa.com'),
('gerenteventas', '$2a$12$7MAPdVu04Z5HHVfwqg.FwuGL1jWOzkmZQ8GKuk8XoC5P/tLUoKOXS', 'Carlos Fuentes (Gerente de Ventas)', 'carlos.fuentes@calzadoempresa.com'),
('jefealmacen', '$2a$12$7MAPdVu04Z5HHVfwqg.FwuGL1jWOzkmZQ8GKuk8XoC5P/tLUoKOXS', 'Hernán Pérez (Jefe de Almacén)', 'hernan.perez@calzadoempresa.com'),
('auxiliaralmacen', '$2a$12$7MAPdVu04Z5HHVfwqg.FwuGL1jWOzkmZQ8GKuk8XoC5P/tLUoKOXS', 'Lucas Benítez (Auxiliar de Bodega y Despacho)', 'lucas.benitez@calzadoempresa.com'),
('cajero1', '$2a$12$7MAPdVu04Z5HHVfwqg.FwuGL1jWOzkmZQ8GKuk8XoC5P/tLUoKOXS', 'Andrea Martínez (Cajera Tienda Centro)', 'andrea.martinez@calzadoempresa.com'),
('soporte1', '$2a$12$7MAPdVu04Z5HHVfwqg.FwuGL1jWOzkmZQ8GKuk8XoC5P/tLUoKOXS', 'Sofía Valenzuela (Atención al Cliente)', 'soporte@calzadoempresa.com'),
('contador1', '$2a$12$7MAPdVu04Z5HHVfwqg.FwuGL1jWOzkmZQ8GKuk8XoC5P/tLUoKOXS', 'C.P. Gustavo Morales (Contador General)', 'gustavo.morales@calzadoempresa.com');

-- Asignación de Roles a Usuarios
INSERT INTO user_role (id_user, id_role)
SELECT u.id_user, r.id_role FROM app_user u, role r WHERE u.username = 'admin' AND r.role_name = 'ROLE_ADMIN';
INSERT INTO user_role (id_user, id_role)
SELECT u.id_user, r.id_role FROM app_user u, role r WHERE u.username = 'gerentegeneral' AND r.role_name = 'ROLE_GERENTE_GENERAL';
INSERT INTO user_role (id_user, id_role)
SELECT u.id_user, r.id_role FROM app_user u, role r WHERE u.username = 'gerentefinanciero' AND r.role_name = 'ROLE_GERENTE_FINANCIERO';
INSERT INTO user_role (id_user, id_role)
SELECT u.id_user, r.id_role FROM app_user u, role r WHERE u.username = 'gerenteventas' AND r.role_name = 'ROLE_GERENTE_VENTAS';
INSERT INTO user_role (id_user, id_role)
SELECT u.id_user, r.id_role FROM app_user u, role r WHERE u.username = 'jefealmacen' AND r.role_name = 'ROLE_ENCARGADO_ALMACEN';
INSERT INTO user_role (id_user, id_role)
SELECT u.id_user, r.id_role FROM app_user u, role r WHERE u.username = 'auxiliaralmacen' AND r.role_name = 'ROLE_AUXILIAR_ALMACEN';
INSERT INTO user_role (id_user, id_role)
SELECT u.id_user, r.id_role FROM app_user u, role r WHERE u.username = 'cajero1' AND r.role_name = 'ROLE_CAJERO';
INSERT INTO user_role (id_user, id_role)
SELECT u.id_user, r.id_role FROM app_user u, role r WHERE u.username = 'soporte1' AND r.role_name = 'ROLE_SOPORTE';
INSERT INTO user_role (id_user, id_role)
SELECT u.id_user, r.id_role FROM app_user u, role r WHERE u.username = 'contador1' AND r.role_name = 'ROLE_CONTADOR';

-- ============================================================================
-- 3. BODEGAS, SUCURSALES Y CAJAS POS
-- ============================================================================

-- Las Tres Bodegas Segregadas
INSERT INTO bodega (nombre, id_bodega_tipo, ubicacion, es_despachable_web) VALUES
('Bodega 1 - Recepción y Cuarentena', (SELECT id_bodega_tipo FROM cat_bodega_tipo WHERE code = 'RECEPCION'), 'Planta Logística Norte, Muelle A', FALSE),
('Bodega 2 - Almacén Central de Despacho', (SELECT id_bodega_tipo FROM cat_bodega_tipo WHERE code = 'ACTUAL'), 'Planta Logística Norte, Muelle B', TRUE),
('Bodega 3 - Almacén de Liquidación Outlet', (SELECT id_bodega_tipo FROM cat_bodega_tipo WHERE code = 'LIQUIDACION'), 'Sucursal Outlet Sur, Depósito 2', TRUE);

-- Tiendas Físicas
INSERT INTO sucursal (codigo_sucursal, nombre, direccion, telefono, id_bodega) VALUES
('SUC-CENTRO', 'Tienda Peatonal Centro Histórico', 'Av. 9 de Octubre #450, Centro', '+593 4 250 1122', (SELECT id_bodega FROM bodega WHERE nombre LIKE '%Bodega 2%')),
('SUC-MALL', 'Tienda Mall Las Américas', 'Centro Comercial Las Américas, Local 120', '+593 4 290 8877', (SELECT id_bodega FROM bodega WHERE nombre LIKE '%Bodega 2%'));

-- Cajas Registradoras POS
INSERT INTO caja_pos (id_sucursal, numero_caja, nombre_terminal) VALUES
((SELECT id_sucursal FROM sucursal WHERE codigo_sucursal = 'SUC-CENTRO'), 'CAJA-01', 'Terminal Táctil Mostrador Principal'),
((SELECT id_sucursal FROM sucursal WHERE codigo_sucursal = 'SUC-CENTRO'), 'CAJA-02', 'Terminal Auxiliar Rápido'),
((SELECT id_sucursal FROM sucursal WHERE codigo_sucursal = 'SUC-MALL'), 'CAJA-01', 'Terminal Mostrador Mall');

-- ============================================================================
-- 4. TALLAS DE EQUIVALENCIA MULTIDIMENSIONAL (EUR, US, UK, CM/MX)
-- ============================================================================

INSERT INTO talla_equivalencia (talla_eur, talla_us_hombre, talla_us_mujer, talla_uk, talla_cm_mx) VALUES
(38.0, 6.0, 7.5, 5.0, 24.0),
(39.0, 6.5, 8.0, 5.5, 24.5),
(40.0, 7.5, 9.0, 6.5, 25.5),
(41.0, 8.5, 10.0, 7.5, 26.5),
(42.0, 9.0, 10.5, 8.0, 27.0),
(43.0, 10.0, 11.5, 9.0, 28.0),
(44.0, 11.0, 12.5, 10.0, 29.0);

-- ============================================================================
-- 5. MODELOS, VARIANTES Y GENERACION DE SKUs DEL CASO
-- ============================================================================

-- Modelo 1: Tenis Vans Dancer Verde (Ejemplo exacto del caso: TV001-XX)
INSERT INTO modelo (id_tipo_calzado, id_marca, correlativo, nombre, material_exterior, material_forro, tipo_suela, tipo_cierre, genero, temporada, precio_lista_base, es_outlet, porcentaje_descuento, es_visible_web) VALUES
(
    (SELECT id_tipo_calzado FROM cat_tipo_calzado WHERE codigo_letra = 'T'),
    (SELECT id_marca FROM cat_marca WHERE codigo_letra = 'V'),
    1,
    'Dancer Urbano',
    'Lona Canvas Reforzada',
    'Textil Respirable',
    'Goma Vulcanizada Waffle',
    'Cordones de Algodón',
    'UNISEX',
    'Verano 2025',
    65.00,
    FALSE,
    0.00,
    TRUE
);

-- NOTA: este repositorio no incluye fotos de producto. Estas columnas apuntaban
-- a archivos que no existen bajo frontend/assets/, y el fallback de la SPA de
-- Nginx enmascaraba el archivo faltante como una respuesta 200 que llevaba
-- index.html. La tienda renderiza su propio placeholder cuando no hay foto, por
-- eso ambas columnas se dejan vacías a propósito.
-- Variante de Color Verde
INSERT INTO modelo_color (id_modelo, id_color, foto_principal_url, galeria_fotos_json) VALUES
(
    (SELECT id_modelo FROM modelo WHERE correlativo = 1 AND id_marca = (SELECT id_marca FROM cat_marca WHERE codigo_letra = 'V')),
    (SELECT id_color FROM cat_color WHERE nombre = 'Verde'),
    NULL,
    '[]'::jsonb
);

-- Generación de SKUs para TV001 (Tallas 39, 40, 41, 42)
INSERT INTO calzado_sku (id_modelo_color, id_talla, sku, codigo_barras_ean, precio_venta_vigente) VALUES
((SELECT id_modelo_color FROM modelo_color WHERE id_modelo = 1), (SELECT id_talla FROM talla_equivalencia WHERE talla_eur = 39.0), 'TV001-39', '7501000001391', 65.00),
((SELECT id_modelo_color FROM modelo_color WHERE id_modelo = 1), (SELECT id_talla FROM talla_equivalencia WHERE talla_eur = 40.0), 'TV001-40', '7501000001407', 65.00),
((SELECT id_modelo_color FROM modelo_color WHERE id_modelo = 1), (SELECT id_talla FROM talla_equivalencia WHERE talla_eur = 41.0), 'TV001-41', '7501000001414', 65.00),
((SELECT id_modelo_color FROM modelo_color WHERE id_modelo = 1), (SELECT id_talla FROM talla_equivalencia WHERE talla_eur = 42.0), 'TV001-42', '7501000001421', 65.00);

-- Modelo 2: Botas Grangler Montana Café (Ejemplo exacto del caso: BG012-XX)
INSERT INTO modelo (id_tipo_calzado, id_marca, correlativo, nombre, material_exterior, material_forro, tipo_suela, tipo_cierre, genero, temporada, precio_lista_base, es_outlet, porcentaje_descuento, es_visible_web) VALUES
(
    (SELECT id_tipo_calzado FROM cat_tipo_calzado WHERE codigo_letra = 'B'),
    (SELECT id_marca FROM cat_marca WHERE codigo_letra = 'G'),
    12,
    'Montana Rudo',
    'Cuero Vacuno Encerado',
    'Microfibra Térmica',
    'Caucho Antideslizante',
    'Cordones y Pasadores Metálicos',
    'HOMBRE',
    'Invierno 2024',
    95.00,
    FALSE,
    0.00,
    TRUE
);

-- Variante de Color Café
INSERT INTO modelo_color (id_modelo, id_color, foto_principal_url, galeria_fotos_json) VALUES
(
    (SELECT id_modelo FROM modelo WHERE correlativo = 12 AND id_marca = (SELECT id_marca FROM cat_marca WHERE codigo_letra = 'G')),
    (SELECT id_color FROM cat_color WHERE nombre = 'Café'),
    NULL,
    '[]'::jsonb
);

-- Generación de SKUs para BG012 (Tallas 40, 41, 42, 43)
INSERT INTO calzado_sku (id_modelo_color, id_talla, sku, codigo_barras_ean, precio_venta_vigente) VALUES
((SELECT id_modelo_color FROM modelo_color WHERE id_modelo = 2), (SELECT id_talla FROM talla_equivalencia WHERE talla_eur = 40.0), 'BG012-40', '7501000012403', 95.00),
((SELECT id_modelo_color FROM modelo_color WHERE id_modelo = 2), (SELECT id_talla FROM talla_equivalencia WHERE talla_eur = 41.0), 'BG012-41', '7501000012410', 95.00),
((SELECT id_modelo_color FROM modelo_color WHERE id_modelo = 2), (SELECT id_talla FROM talla_equivalencia WHERE talla_eur = 42.0), 'BG012-42', '7501000012427', 95.00),
((SELECT id_modelo_color FROM modelo_color WHERE id_modelo = 2), (SELECT id_talla FROM talla_equivalencia WHERE talla_eur = 43.0), 'BG012-43', '7501000012434', 95.00);

-- Modelo 3: Tenis Vans Retro Blanco (OUTLET con 30% de Descuento: TV002-XX)
INSERT INTO modelo (id_tipo_calzado, id_marca, correlativo, nombre, material_exterior, material_forro, tipo_suela, tipo_cierre, genero, temporada, precio_lista_base, es_outlet, porcentaje_descuento, es_visible_web) VALUES
(
    (SELECT id_tipo_calzado FROM cat_tipo_calzado WHERE codigo_letra = 'T'),
    (SELECT id_marca FROM cat_marca WHERE codigo_letra = 'V'),
    2,
    'Retro Skate 90s',
    'Lona de Algodón',
    'Textil Suave',
    'Goma Flexible',
    'Cordones Clásicos',
    'UNISEX',
    'Otoño 2024',
    60.00,
    TRUE,
    30.00, -- 30% de descuento -> Precio: $42.00 USD
    TRUE
);

-- Variante de Color Blanco
INSERT INTO modelo_color (id_modelo, id_color, foto_principal_url, galeria_fotos_json) VALUES
(
    (SELECT id_modelo FROM modelo WHERE correlativo = 2 AND id_marca = (SELECT id_marca FROM cat_marca WHERE codigo_letra = 'V')),
    (SELECT id_color FROM cat_color WHERE nombre = 'Blanco'),
    NULL,
    '[]'::jsonb
);

-- Generación de SKUs para TV002 (Tallas 39, 40, 41 con precio rebajado a $42.00 USD)
INSERT INTO calzado_sku (id_modelo_color, id_talla, sku, codigo_barras_ean, precio_venta_vigente) VALUES
((SELECT id_modelo_color FROM modelo_color WHERE id_modelo = 3), (SELECT id_talla FROM talla_equivalencia WHERE talla_eur = 39.0), 'TV002-39', '7501000002398', 42.00),
((SELECT id_modelo_color FROM modelo_color WHERE id_modelo = 3), (SELECT id_talla FROM talla_equivalencia WHERE talla_eur = 40.0), 'TV002-40', '7501000002404', 42.00),
((SELECT id_modelo_color FROM modelo_color WHERE id_modelo = 3), (SELECT id_talla FROM talla_equivalencia WHERE talla_eur = 41.0), 'TV002-41', '7501000002411', 42.00);

-- ============================================================================
-- 6. STOCK INICIAL Y REGISTRO EN KARDEX INMUTABLE
-- ============================================================================

-- Stock de Tenis Vans TV001 en Bodega 2 (Actual / Despacho)
INSERT INTO inventario_stock (id_bodega, id_calzado_sku, cantidad_disponible, cantidad_reservada, stock_seguridad_minimo)
SELECT (SELECT id_bodega FROM bodega WHERE nombre LIKE '%Bodega 2%'), id_calzado_sku, 25, 0, 3
FROM calzado_sku WHERE sku LIKE 'TV001-%';

-- Stock de Botas Grangler BG012 en Bodega 2 (Actual / Despacho)
INSERT INTO inventario_stock (id_bodega, id_calzado_sku, cantidad_disponible, cantidad_reservada, stock_seguridad_minimo)
SELECT (SELECT id_bodega FROM bodega WHERE nombre LIKE '%Bodega 2%'), id_calzado_sku, 15, 0, 2
FROM calzado_sku WHERE sku LIKE 'BG012-%';

-- Stock de Tenis Outlet TV002 en Bodega 3 (Liquidación / Outlet)
INSERT INTO inventario_stock (id_bodega, id_calzado_sku, cantidad_disponible, cantidad_reservada, stock_seguridad_minimo)
SELECT (SELECT id_bodega FROM bodega WHERE nombre LIKE '%Bodega 3%'), id_calzado_sku, 10, 0, 1
FROM calzado_sku WHERE sku LIKE 'TV002-%';

-- Registro de Movimientos Iniciales en el Kardex
INSERT INTO kardex_movimiento (id_bodega, id_calzado_sku, id_movimiento_tipo, cantidad_movimiento, stock_anterior, stock_posterior, documento_tipo, documento_id, id_user)
SELECT s.id_bodega, s.id_calzado_sku, (SELECT id_movimiento_tipo FROM cat_movimiento_stock_tipo WHERE code = 'INGRESO_PROVEEDOR'), s.cantidad_disponible, 0, s.cantidad_disponible, 'RECEPCION_INICIAL', 1, (SELECT id_user FROM app_user WHERE username = 'jefealmacen')
FROM inventario_stock s;

-- ============================================================================
-- 7. TARIFARIO DE ENVIOS Y CUPONES DE MARKETING
-- ============================================================================

INSERT INTO tarifario_envio (zona_nombre, codigo_postal_prefijo, distancia_km_max, costo_flete_base, umbral_compra_envio_gratis) VALUES
('Zona Metropolitana (Urbana)', '0901', 25.00, 4.50, 70.00),
('Zona Departamental / Interurbana', '0902', 100.00, 8.50, 90.00),
('Zona Nacional Larga Distancia', '0903', 500.00, 14.00, 120.00);

INSERT INTO cupon_descuento (codigo_cupon, tipo_descuento, valor_descuento, monto_minimo_compra, fecha_inicio, fecha_fin, limite_usos) VALUES
('BIENVENIDA10', 'PORCENTAJE', 10.00, 40.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '90 DAYS', 500),
('OUTLET50', 'MONTO_FIJO', 15.00, 80.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '30 DAYS', 100);

-- ============================================================================
-- 8. CLIENTES, ORDENES Y FACTURAS DE DEMOSTRACION
-- ============================================================================

-- Clientes
INSERT INTO customer (tipo_documento, numero_documento, nombre_completo, email, telefono, direccion, es_invitado, saldo_favor_wallet) VALUES
('CF', 'CONSUMIDOR-FINAL', 'Consumidor Final Mostrador', 'cf@calzadoempresa.com', '+593 9 000 0000', 'Venta en Tienda Física', TRUE, 0.00),
('DNI', '0928174621', 'Carlos Alberto Mendoza', 'carlos.mendoza@email.com', '+593 9 847 1122', 'Cdla. Kennedy Norte Mz. 102 V. 4, Guayaquil', FALSE, 25.00),
('NIT', '1792019284001', 'Distribuidora Andina de Calzado S.A.', 'compras@andina.com', '+593 2 399 4400', 'Parque Industrial Sur, Nave 14, Quito', FALSE, 0.00);

-- Orden 1: Venta E-commerce B2C Pagada (Carlos Mendoza compra 1 par TV001-40 con envío gratis y tarjeta simulada)
INSERT INTO orden (numero_orden, id_customer, canal_venta, tipo_entrega, id_bodega_despacho, id_orden_estado, subtotal_productos, costo_envio, comision_pasarela, total_a_pagar, fecha_expiracion_ttl_2h) VALUES
(
    'ORD-2025-0001',
    (SELECT id_customer FROM customer WHERE email = 'carlos.mendoza@email.com'),
    'ONLINE_ECOMMERCE',
    'ENVIO_DOMICILIO',
    (SELECT id_bodega FROM bodega WHERE nombre LIKE '%Bodega 2%'),
    (SELECT id_orden_estado FROM cat_orden_estado WHERE code = 'PAGADA'),
    65.00,
    4.50,
    2.43, -- Recargo 3.5% + $0.30 trasladado al cliente
    71.93,
    CURRENT_TIMESTAMP + INTERVAL '2 HOURS'
);

-- Detalle de Orden 1
INSERT INTO orden_linea (id_orden, id_calzado_sku, cantidad, unit_price, descuento_unitario, line_total) VALUES
(
    (SELECT id_orden FROM orden WHERE numero_orden = 'ORD-2025-0001'),
    (SELECT id_calzado_sku FROM calzado_sku WHERE sku = 'TV001-40'),
    1,
    65.00,
    0.00,
    65.00
);

-- Factura de Orden 1
INSERT INTO factura (id_orden, id_customer, id_factura_tipo, id_factura_estado, numero_correlativo, nit_cliente, razon_social, monto_subtotal, monto_iva, monto_recargo_pasarela, invoice_total, pdf_url) VALUES
(
    (SELECT id_orden FROM orden WHERE numero_orden = 'ORD-2025-0001'),
    (SELECT id_customer FROM customer WHERE email = 'carlos.mendoza@email.com'),
    (SELECT id_factura_tipo FROM cat_factura_tipo WHERE code = 'FACTURA'),
    (SELECT id_factura_estado FROM cat_factura_estado WHERE code = 'EMITIDA'),
    'FAC-000101',
    '0928174621',
    'Carlos Alberto Mendoza',
    58.04,
    6.96, -- 12% IVA
    2.43,
    71.93,
    '/storage/facturas/fac-000101.pdf'
);

-- Pago Transaccional de Orden 1
INSERT INTO pago_transaccion (id_invoice, id_pago_metodo, estado_pago, monto_bruto, comision_retenida, monto_neto_bancario, referencia_transaccion) VALUES
(
    (SELECT id_invoice FROM factura WHERE numero_correlativo = 'FAC-000101'),
    (SELECT id_pago_metodo FROM cat_pago_metodo WHERE code = 'TARJETA_SIMULADA'),
    'APROBADO',
    71.93,
    2.43,
    69.50, -- Neto ingresado a cuenta bancaria
    'SIM-TX-98472910'
);

-- Despacho con Código Secreto OTP para Orden 1
INSERT INTO despacho_envio (id_orden, tipo_transporte, courier_nombre, id_user_chofer, numero_guia_tracking, codigo_secreto_otp, id_despacho_estado, sla_limite_preparacion_24h, sla_limite_entrega_5d) VALUES
(
    (SELECT id_orden FROM orden WHERE numero_orden = 'ORD-2025-0001'),
    'FLOTA_PROPIA_LOCAL',
    'Furgoneta Reparto Local 01',
    (SELECT id_user FROM app_user WHERE username = 'auxiliaralmacen'),
    'TRK-2025-0001',
    '839201', -- Código secreto OTP que el cliente le dictará al chofer
    (SELECT id_despacho_estado FROM cat_despacho_estado WHERE code = 'PREPARADO'),
    CURRENT_TIMESTAMP + INTERVAL '24 HOURS',
    CURRENT_TIMESTAMP + INTERVAL '5 DAYS'
);

-- Descarga de Stock en Bodega 2 por la Venta Web
UPDATE inventario_stock
SET cantidad_disponible = cantidad_disponible - 1
WHERE id_bodega = (SELECT id_bodega FROM bodega WHERE nombre LIKE '%Bodega 2%')
  AND id_calzado_sku = (SELECT id_calzado_sku FROM calzado_sku WHERE sku = 'TV001-40');

-- Registro en Kardex de la Venta Web
INSERT INTO kardex_movimiento (id_bodega, id_calzado_sku, id_movimiento_tipo, cantidad_movimiento, stock_anterior, stock_posterior, documento_tipo, documento_id, id_user) VALUES
(
    (SELECT id_bodega FROM bodega WHERE nombre LIKE '%Bodega 2%'),
    (SELECT id_calzado_sku FROM calzado_sku WHERE sku = 'TV001-40'),
    (SELECT id_movimiento_tipo FROM cat_movimiento_stock_tipo WHERE code = 'SALIDA_VENTA_WEB'),
    -1,
    25,
    24,
    'ORDEN_VENTA',
    1,
    (SELECT id_user FROM app_user WHERE username = 'admin')
);

-- ============================================================================
-- 9. TICKET DE SOPORTE DE EJEMPLO (Control del 40% de Quejas)
-- ============================================================================

INSERT INTO ticket_soporte (numero_ticket, id_customer, id_orden, id_motivo_queja, id_ticket_estado, area_derivada, descripcion) VALUES
(
    'TCK-2025-0001',
    (SELECT id_customer FROM customer WHERE email = 'carlos.mendoza@email.com'),
    (SELECT id_orden FROM orden WHERE numero_orden = 'ORD-2025-0001'),
    (SELECT id_motivo_queja FROM cat_motivo_queja WHERE code = 'DISCREPANCIA_VISUAL_WEB'),
    (SELECT id_ticket_estado FROM cat_ticket_estado WHERE code = 'EN_ANALISIS'),
    'BODEGA',
    'El cliente solicita confirmar si el tono verde del modelo TV001 corresponde exactamente al verde bosque de la fotografía web.'
);
