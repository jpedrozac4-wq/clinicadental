-- ============================================================================
-- BOOTSTRAP DE PRODUCCION - CLINICA DENTAL
-- ----------------------------------------------------------------------------
-- Se ejecuta UNA sola vez sobre una base vacia (docker-entrypoint-initdb.d),
-- despues de schema.sql. Deja el sistema operable sin ningun dato de negocio:
-- no crea pacientes, citas, facturas, pagos ni recordatorios.
--
-- Contenido:
--   1. Catalogos de estados, canales, metodos de pago y acciones auditables.
--   2. Los tres roles base con su matriz de permisos y el catalogo completo.
--   3. Ocho tratamientos de odontologia general con precio de referencia,
--      editables desde el catalogo de la aplicacion.
--
-- El usuario administrador NO se crea aqui: lo crea el backend al arrancar, a
-- partir de CLINICA_ADMIN_USERNAME / CLINICA_ADMIN_PASSWORD, y solo si no
-- existe ningun administrador activo (S23).
--
-- Los datos de demostracion viven en seed_data.sql, que NO se monta en el init
-- automatico; se aplica a mano cuando se quiere una base de prueba poblada.
-- ============================================================================

-- 1. CATALOGOS DE ESTADOS Y TIPOS
-- ============================================================================

INSERT INTO cat_estado_cita (code, description) VALUES
('PROGRAMADA',  'Cita agendada y pendiente de confirmacion'),
('CONFIRMADA',  'El paciente confirmo su asistencia'),
('REPROGRAMADA', 'Cita reemplazada por otra fecha u horario'),
('ATENDIDA',    'El paciente asistio y el tratamiento se realizo'),
('CANCELADA',   'Cita anulada antes de la atencion'),
('NO_ASISTIO',  'El paciente no se presento a la cita');

INSERT INTO cat_canal_notificacion (code, description) VALUES
('EMAIL', 'Correo electronico del paciente'),
('SMS',   'Mensaje de texto al telefono del paciente');

INSERT INTO cat_estado_notificacion (code, description) VALUES
('PENDIENTE', 'Recordatorio en cola, aun no despachado'),
('ENVIADA',   'Recordatorio entregado por el canal configurado'),
('SIMULADA',  'Recordatorio registrado sin SMTP configurado'),
('FALLIDA',   'El envio fallo y quedo registrado el error'),
('CANCELADA', 'Recordatorio anulado porque la cita ya no aplica');

INSERT INTO cat_metodo_pago (code, description) VALUES
('EFECTIVO',    'Pago en efectivo en recepcion'),
('TARJETA',     'Pago con tarjeta de debito o credito'),
('TRANSFERENCIA', 'Transferencia o deposito bancario');

INSERT INTO cat_estado_factura (code, description) VALUES
('EMITIDA',      'Comprobante emitido sin pagos registrados'),
('PAGO_PARCIAL', 'Comprobante con pagos parciales y saldo pendiente'),
('PAGADA',       'Comprobante cancelado en su totalidad'),
('ANULADA',      'Comprobante anulado sin efecto de cobro');

INSERT INTO cat_log_accion (code, description) VALUES
('LOGIN',                  'Inicio de sesion en el sistema'),
('PACIENTE_CREAR',         'Alta de un paciente'),
('PACIENTE_ACTUALIZAR',    'Modificacion de datos de un paciente'),
('PACIENTE_BAJA',          'Baja logica de un paciente'),
('CITA_CREAR',             'Programacion de una cita'),
('CITA_REPROGRAMAR',       'Reprogramacion de una cita'),
('CITA_CANCELAR',          'Cancelacion de una cita'),
('TRATAMIENTO_CREAR',      'Alta de un tratamiento en el catalogo'),
('TRATAMIENTO_ACTUALIZAR', 'Modificacion de un tratamiento del catalogo'),
('REALIZADO_REGISTRAR',    'Registro de un tratamiento realizado'),
('FACTURA_EMITIR',         'Emision de un comprobante'),
('PAGO_REGISTRAR',         'Registro de un pago total o parcial'),
('USUARIO_CREAR',          'Alta de un usuario del sistema'),
('USUARIO_ACTUALIZAR',     'Modificacion de un usuario o de su rol'),
('USUARIO_BAJA',           'Baja logica de un usuario'),
('ROL_CREAR',              'Alta de un rol con su matriz de permisos'),
('ROL_ACTUALIZAR',         'Modificacion de la matriz de permisos de un rol'),
('ROL_ELIMINAR',           'Eliminacion de un rol');

-- ============================================================================
-- 2. SEGURIDAD: ROLES, PERMISOS Y USUARIOS
-- ============================================================================

INSERT INTO role (role_name, description) VALUES
('ROLE_ADMINISTRADOR', 'Administrador de la clinica: usuarios, catalogos y auditoria'),
('ROLE_DENTISTA',      'Odontologo: atiende citas y registra tratamientos realizados'),
('ROLE_RECEPCIONISTA', 'Recepcion: pacientes, agenda, facturacion y recordatorios');

INSERT INTO permission (permission_name, modulo, action, description) VALUES
('PACIENTE_VER',            'PACIENTE',    'READ',   'Consultar el listado y la ficha de pacientes'),
('PACIENTE_CREAR',          'PACIENTE',    'CREATE', 'Registrar nuevos pacientes'),
('PACIENTE_EDITAR',         'PACIENTE',    'UPDATE', 'Modificar los datos de un paciente'),
('PACIENTE_ELIMINAR',       'PACIENTE',    'DELETE', 'Dar de baja logica a un paciente'),
('PACIENTE_HISTORIAL_VER',  'PACIENTE',    'READ',   'Consultar el historial clinico de un paciente'),
('CITA_VER',                'CITA',        'READ',   'Consultar la agenda de citas'),
('CITA_CREAR',              'CITA',        'CREATE', 'Programar citas para los pacientes'),
('CITA_REPROGRAMAR',        'CITA',        'UPDATE', 'Reprogramar una cita a otra fecha u horario'),
('CITA_CANCELAR',           'CITA',        'DELETE', 'Cancelar una cita'),
('CITA_ATENDER',            'CITA',        'UPDATE', 'Marcar una cita como atendida'),
('TRATAMIENTO_VER',         'TRATAMIENTO', 'READ',   'Consultar el catalogo de tratamientos'),
('TRATAMIENTO_CREAR',       'TRATAMIENTO', 'CREATE', 'Crear tratamientos en el catalogo'),
('TRATAMIENTO_EDITAR',      'TRATAMIENTO', 'UPDATE', 'Editar tratamientos del catalogo'),
('REALIZADO_VER',           'TRATAMIENTO', 'READ',   'Consultar los tratamientos realizados'),
('REALIZADO_REGISTRAR',     'TRATAMIENTO', 'CREATE', 'Registrar un tratamiento realizado'),
('FACTURA_VER',             'FACTURACION', 'READ',   'Consultar comprobantes y saldos'),
('FACTURA_EMITIR',          'FACTURACION', 'CREATE', 'Emitir comprobantes de tratamientos realizados'),
('PAGO_REGISTRAR',          'FACTURACION', 'CREATE', 'Registrar pagos totales o parciales'),
('RECORDATORIO_VER',        'CITA',        'READ',   'Consultar los recordatorios de citas'),
('RECORDATORIO_ENVIAR',     'CITA',        'UPDATE', 'Despachar recordatorios pendientes'),
('USUARIOS_ADMIN',          'SEGURIDAD',   'UPDATE', 'Administrar usuarios, roles y permisos'),
('AUDITORIA_VER',           'SEGURIDAD',   'READ',   'Consultar la bitacora de auditoria'),
('REPORTE_VER',             'REPORTE',     'READ',   'Consultar los reportes de ingresos, produccion y ocupacion');

-- ROLE_ADMINISTRADOR: acceso total
INSERT INTO role_permission (id_role, id_permission)
SELECT r.id_role, p.id_permission FROM role r, permission p
WHERE r.role_name = 'ROLE_ADMINISTRADOR';

-- ROLE_DENTISTA: foco clinico (pacientes, atencion y registro de procedimientos)
INSERT INTO role_permission (id_role, id_permission)
SELECT r.id_role, p.id_permission FROM role r, permission p
WHERE r.role_name = 'ROLE_DENTISTA' AND p.permission_name IN (
    'PACIENTE_VER', 'PACIENTE_CREAR', 'PACIENTE_EDITAR', 'PACIENTE_HISTORIAL_VER',
    'CITA_VER', 'CITA_CREAR', 'CITA_ATENDER',
    'TRATAMIENTO_VER', 'REALIZADO_VER', 'REALIZADO_REGISTRAR', 'RECORDATORIO_VER'
);

-- ROLE_RECEPCIONISTA: agenda, cobros y recordatorios
INSERT INTO role_permission (id_role, id_permission)
SELECT r.id_role, p.id_permission FROM role r, permission p
WHERE r.role_name = 'ROLE_RECEPCIONISTA' AND p.permission_name IN (
    'PACIENTE_VER', 'PACIENTE_CREAR', 'PACIENTE_EDITAR', 'PACIENTE_ELIMINAR', 'PACIENTE_HISTORIAL_VER',
    'CITA_VER', 'CITA_CREAR', 'CITA_REPROGRAMAR', 'CITA_CANCELAR',
    'TRATAMIENTO_VER', 'REALIZADO_VER',
    'FACTURA_VER', 'FACTURA_EMITIR', 'PAGO_REGISTRAR',
    'RECORDATORIO_VER', 'RECORDATORIO_ENVIAR'
);

-- ============================================================================
-- 3. CATALOGO BASE DE TRATAMIENTOS
-- ============================================================================

INSERT INTO tratamiento (codigo, nombre, descripcion, precio, duracion_minutos) VALUES
('ODO-001', 'Consulta y diagnóstico', 'Evaluación clínica inicial, historia dental y plan de tratamiento.', 150.00, 30),
('ODO-002', 'Limpieza dental (profilaxis)', 'Destartraje con ultrasonido, pulido coronario y aplicación de flúor.', 350.00, 45),
('ODO-003', 'Obturación de resina', 'Restauración estética directa de caries en pieza dental.', 400.00, 60),
('ODO-004', 'Endodoncia unirradicular', 'Tratamiento de conducto en pieza con pulpa comprometida.', 1200.00, 90),
('ODO-005', 'Extracción simple', 'Exodoncia de pieza dental con anestesia local.', 500.00, 45),
('ODO-006', 'Corona de porcelana', 'Reconstrucción protésica fija sobre pieza tratada.', 1800.00, 90),
('ODO-007', 'Blanqueamiento dental', 'Aclaramiento dental en consultorio con gel activado.', 900.00, 60),
('ODO-008', 'Ortodoncia - control mensual', 'Ajuste de arcos, ligas y control de evolución del tratamiento.', 450.00, 30);

-- ============================================================================
-- FIN DEL BOOTSTRAP DE PRODUCCION
-- ============================================================================
