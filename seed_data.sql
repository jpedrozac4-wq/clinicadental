-- ============================================================================
-- DATOS SEMILLA - CLINICA DENTAL
-- Se ejecuta una sola vez sobre una base vacia (docker-entrypoint-initdb.d).
-- Orden: catalogos -> seguridad -> tratamientos -> pacientes -> citas ->
--        tratamientos realizados -> facturacion -> recordatorios.
-- ============================================================================

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
('PAGO_REGISTRAR',         'Registro de un pago total o parcial');

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
('AUDITORIA_VER',           'SEGURIDAD',   'READ',   'Consultar la bitacora de auditoria');

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

-- Usuarios del sistema. Hash BCrypt de la clave demo "Password123!".
INSERT INTO app_user (username, password_hash, full_name, email) VALUES
('admin',      '$2a$12$7MAPdVu04Z5HHVfwqg.FwuGL1jWOzkmZQ8GKuk8XoC5P/tLUoKOXS', 'Administrador de la Clínica', 'admin@clinicaldental.com'),
('recepcion1', '$2a$12$7MAPdVu04Z5HHVfwqg.FwuGL1jWOzkmZQ8GKuk8XoC5P/tLUoKOXS', 'Lucía Fernández (Recepción)', 'lucia.fernandez@clinicaldental.com'),
('dentista1',  '$2a$12$7MAPdVu04Z5HHVfwqg.FwuGL1jWOzkmZQ8GKuk8XoC5P/tLUoKOXS', 'Dra. Valeria Ríos (Odontología General)', 'valeria.rios@clinicaldental.com'),
('dentista2',  '$2a$12$7MAPdVu04Z5HHVfwqg.FwuGL1jWOzkmZQ8GKuk8XoC5P/tLUoKOXS', 'Dr. Martín Salas (Ortodoncia)', 'martin.salas@clinicaldental.com');

INSERT INTO user_role (id_user, id_role)
SELECT u.id_user, r.id_role FROM app_user u, role r WHERE u.username = 'admin' AND r.role_name = 'ROLE_ADMINISTRADOR';
INSERT INTO user_role (id_user, id_role)
SELECT u.id_user, r.id_role FROM app_user u, role r WHERE u.username = 'recepcion1' AND r.role_name = 'ROLE_RECEPCIONISTA';
INSERT INTO user_role (id_user, id_role)
SELECT u.id_user, r.id_role FROM app_user u, role r WHERE u.username = 'dentista1' AND r.role_name = 'ROLE_DENTISTA';
INSERT INTO user_role (id_user, id_role)
SELECT u.id_user, r.id_role FROM app_user u, role r WHERE u.username = 'dentista2' AND r.role_name = 'ROLE_DENTISTA';

-- ============================================================================
-- 3. CATALOGO DE TRATAMIENTOS (S7)
-- ============================================================================

INSERT INTO tratamiento (codigo, nombre, descripcion, precio, duracion_minutos) VALUES
('ODO-001', 'Consulta y diagnóstico', 'Evaluación clínica inicial, historia dental y plan de tratamiento.', 150.00, 30),
('ODO-002', 'Limpieza dental (profilaxis)', 'Destartraje con ultrasonido, pulido coronario y aplicación de flúor.', 350.00, 45),
('ODO-003', 'Obturación de resina', 'Restauración estética directa de caries en pieza dental.', 400.00, 60),
('ODO-004', 'Endodoncia unirradicular', 'Tratamiento de conducto en pieza con pulpa comprometida.', 1200.00, 90),
('ODO-005', 'Extracción simple', 'Exodoncia de pieza dental con anestesia local.', 500.00, 45),
('ODO-006', 'Corona de porcelana', 'Reconstrucción protésica fija sobre pieza tratada.', 1800.00, 90),
('ODO-007', 'Blanqueamiento dental', 'Aclaramiento dental en consultorio con gel activado.', 900.00, 60),
('ODO-008', 'Ortodoncia - control mensual', 'Ajuste de arcos, ligas y control de evolución del tratamiento.', 450.00, 30),
('ODO-009', 'Implante dental unitario', 'Colocación de implante oseointegrado con aditamento protésico.', 4500.00, 120),
('ODO-010', 'Radiografía panorámica', 'Estudio imagenológico de ambos maxilares.', 250.00, 20);

-- ============================================================================
-- 4. PACIENTES (S1, S2)
-- ============================================================================

INSERT INTO paciente (nombres, apellidos, fecha_nacimiento, telefono, direccion, email, active) VALUES
('Ana Lucía',     'Mendoza Rojas',    DATE '1990-03-14', '777-12345', 'Av. Busch 1450, Zona Central',        'ana.mendoza@example.com',     TRUE),
('Carlos Alberto','Quispe Flores',    DATE '1985-07-22', '712-45678', 'Calle Murillo 245, 2do anillo',       'carlos.quispe@example.com',   TRUE),
('María Fernanda','Suárez Ortiz',     DATE '1998-11-05', '765-98765', 'Av. Alemana km 4, Equipetrol',        'maria.suarez@example.com',    TRUE),
('Jorge Andrés',  'Villarroel Paz',   DATE '1976-01-30', '703-11223', 'Calle Arenales 234, Centro',          'jorge.villarroel@example.com', TRUE),
('Rocío Belén',   'Aguilar Sánchez',  DATE '2001-05-18', '789-33445', 'Av. Santos Dumont 7mo anillo',        'rocio.aguilar@example.com',   TRUE),
('Pedro Antonio', 'Navarro Linares',  DATE '1969-09-09', '721-55667', 'Calle Sucre 78, Casco Viejo',         'pedro.navarro@example.com',   FALSE);

-- ============================================================================
-- 5. CITAS (S4, S6)
-- Las citas se expresan en tiempo relativo a CURRENT_DATE para que la agenda
-- siempre tenga actividad futura y pasada al momento de inicializar.
-- ============================================================================

-- Cita atendida de ortodoncia (historial clinico del paciente)
INSERT INTO cita (id_paciente, id_tratamiento, id_dentista, id_estado_cita, fecha_hora_inicio, duracion_minutos, motivo, notas)
SELECT
    (SELECT id_paciente FROM paciente WHERE email = 'ana.mendoza@example.com'),
    (SELECT id_tratamiento FROM tratamiento WHERE codigo = 'ODO-008'),
    (SELECT id_user FROM app_user WHERE username = 'dentista2'),
    (SELECT id_estado_cita FROM cat_estado_cita WHERE code = 'ATENDIDA'),
    ((CURRENT_DATE - INTERVAL '7 days') + TIME '10:00')::timestamptz, 30,
    'Control de ortodoncia', 'Se ajustó arco superior y se programó control mensual.';

-- Cita atendida de profilaxis
INSERT INTO cita (id_paciente, id_tratamiento, id_dentista, id_estado_cita, fecha_hora_inicio, duracion_minutos, motivo, notas)
SELECT
    (SELECT id_paciente FROM paciente WHERE email = 'carlos.quispe@example.com'),
    (SELECT id_tratamiento FROM tratamiento WHERE codigo = 'ODO-002'),
    (SELECT id_user FROM app_user WHERE username = 'dentista1'),
    (SELECT id_estado_cita FROM cat_estado_cita WHERE code = 'ATENDIDA'),
    ((CURRENT_DATE - INTERVAL '14 days') + TIME '09:00')::timestamptz, 45,
    'Limpieza semestral', 'Sarro moderado en sector inferior.';

-- Cita cancelada
INSERT INTO cita (id_paciente, id_tratamiento, id_dentista, id_estado_cita, fecha_hora_inicio, duracion_minutos, motivo)
SELECT
    (SELECT id_paciente FROM paciente WHERE email = 'maria.suarez@example.com'),
    (SELECT id_tratamiento FROM tratamiento WHERE codigo = 'ODO-010'),
    (SELECT id_user FROM app_user WHERE username = 'dentista1'),
    (SELECT id_estado_cita FROM cat_estado_cita WHERE code = 'CANCELADA'),
    ((CURRENT_DATE - INTERVAL '3 days') + TIME '16:00')::timestamptz, 20,
    'Radiografía de control';

-- Cita con inasistencia
INSERT INTO cita (id_paciente, id_tratamiento, id_dentista, id_estado_cita, fecha_hora_inicio, duracion_minutos, motivo)
SELECT
    (SELECT id_paciente FROM paciente WHERE email = 'rocio.aguilar@example.com'),
    (SELECT id_tratamiento FROM tratamiento WHERE codigo = 'ODO-001'),
    (SELECT id_user FROM app_user WHERE username = 'dentista1'),
    (SELECT id_estado_cita FROM cat_estado_cita WHERE code = 'NO_ASISTIO'),
    ((CURRENT_DATE - INTERVAL '5 days') + TIME '09:30')::timestamptz, 30,
    'Primera consulta';

-- Cita original que fue reprogramada
INSERT INTO cita (id_paciente, id_tratamiento, id_dentista, id_estado_cita, fecha_hora_inicio, duracion_minutos, motivo)
SELECT
    (SELECT id_paciente FROM paciente WHERE email = 'rocio.aguilar@example.com'),
    (SELECT id_tratamiento FROM tratamiento WHERE codigo = 'ODO-001'),
    (SELECT id_user FROM app_user WHERE username = 'dentista1'),
    (SELECT id_estado_cita FROM cat_estado_cita WHERE code = 'REPROGRAMADA'),
    ((CURRENT_DATE + INTERVAL '3 days') + TIME '09:00')::timestamptz, 30,
    'Primera consulta';

-- Cita vigente que reemplaza a la anterior (S6)
INSERT INTO cita (id_paciente, id_tratamiento, id_dentista, id_estado_cita, fecha_hora_inicio, duracion_minutos, motivo, id_cita_origen)
SELECT
    (SELECT id_paciente FROM paciente WHERE email = 'rocio.aguilar@example.com'),
    (SELECT id_tratamiento FROM tratamiento WHERE codigo = 'ODO-001'),
    (SELECT id_user FROM app_user WHERE username = 'dentista1'),
    (SELECT id_estado_cita FROM cat_estado_cita WHERE code = 'PROGRAMADA'),
    ((CURRENT_DATE + INTERVAL '4 days') + TIME '09:00')::timestamptz, 30,
    'Primera consulta',
    (SELECT id_cita FROM cita
      WHERE id_paciente = (SELECT id_paciente FROM paciente WHERE email = 'rocio.aguilar@example.com')
        AND fecha_hora_inicio = ((CURRENT_DATE + INTERVAL '3 days') + TIME '09:00')::timestamptz);

-- Agenda futura del dentista 1: dos citas consecutivas, sin solapamiento
INSERT INTO cita (id_paciente, id_tratamiento, id_dentista, id_estado_cita, fecha_hora_inicio, duracion_minutos, motivo)
SELECT
    (SELECT id_paciente FROM paciente WHERE email = 'ana.mendoza@example.com'),
    (SELECT id_tratamiento FROM tratamiento WHERE codigo = 'ODO-001'),
    (SELECT id_user FROM app_user WHERE username = 'dentista1'),
    (SELECT id_estado_cita FROM cat_estado_cita WHERE code = 'PROGRAMADA'),
    ((CURRENT_DATE + INTERVAL '1 day') + TIME '09:00')::timestamptz, 30,
    'Dolor en molar superior';

INSERT INTO cita (id_paciente, id_tratamiento, id_dentista, id_estado_cita, fecha_hora_inicio, duracion_minutos, motivo)
SELECT
    (SELECT id_paciente FROM paciente WHERE email = 'carlos.quispe@example.com'),
    (SELECT id_tratamiento FROM tratamiento WHERE codigo = 'ODO-002'),
    (SELECT id_user FROM app_user WHERE username = 'dentista1'),
    (SELECT id_estado_cita FROM cat_estado_cita WHERE code = 'CONFIRMADA'),
    ((CURRENT_DATE + INTERVAL '1 day') + TIME '10:00')::timestamptz, 45,
    'Limpieza semestral';

-- Agenda futura del dentista 2
INSERT INTO cita (id_paciente, id_tratamiento, id_dentista, id_estado_cita, fecha_hora_inicio, duracion_minutos, motivo)
SELECT
    (SELECT id_paciente FROM paciente WHERE email = 'maria.suarez@example.com'),
    (SELECT id_tratamiento FROM tratamiento WHERE codigo = 'ODO-003'),
    (SELECT id_user FROM app_user WHERE username = 'dentista2'),
    (SELECT id_estado_cita FROM cat_estado_cita WHERE code = 'PROGRAMADA'),
    ((CURRENT_DATE + INTERVAL '2 days') + TIME '11:00')::timestamptz, 60,
    'Caries en pieza 26';

INSERT INTO cita (id_paciente, id_tratamiento, id_dentista, id_estado_cita, fecha_hora_inicio, duracion_minutos, motivo)
SELECT
    (SELECT id_paciente FROM paciente WHERE email = 'jorge.villarroel@example.com'),
    (SELECT id_tratamiento FROM tratamiento WHERE codigo = 'ODO-004'),
    (SELECT id_user FROM app_user WHERE username = 'dentista2'),
    (SELECT id_estado_cita FROM cat_estado_cita WHERE code = 'CONFIRMADA'),
    ((CURRENT_DATE + INTERVAL '2 days') + TIME '15:00')::timestamptz, 90,
    'Endodoncia pieza 36';

-- ============================================================================
-- 6. TRATAMIENTOS REALIZADOS (S8)
-- El costo se congela con el precio vigente del catalogo.
-- ============================================================================

-- Vinculados a citas atendidas
INSERT INTO tratamiento_realizado (id_paciente, id_tratamiento, id_cita, id_dentista, fecha_realizacion, detalle_procedimiento, observaciones, costo)
SELECT
    (SELECT id_paciente FROM paciente WHERE email = 'ana.mendoza@example.com'),
    (SELECT id_tratamiento FROM tratamiento WHERE codigo = 'ODO-008'),
    (SELECT id_cita FROM cita c JOIN paciente p ON p.id_paciente = c.id_paciente
      WHERE p.email = 'ana.mendoza@example.com'
        AND c.fecha_hora_inicio = ((CURRENT_DATE - INTERVAL '7 days') + TIME '10:00')::timestamptz),
    (SELECT id_user FROM app_user WHERE username = 'dentista2'),
    ((CURRENT_DATE - INTERVAL '7 days') + TIME '10:00')::timestamptz,
    'Cambio de arcos superior e inferior, recambio de ligas y control de torque.',
    'Buena evolución, sin molestias reportadas.',
    450.00;

INSERT INTO tratamiento_realizado (id_paciente, id_tratamiento, id_cita, id_dentista, fecha_realizacion, detalle_procedimiento, observaciones, costo)
SELECT
    (SELECT id_paciente FROM paciente WHERE email = 'carlos.quispe@example.com'),
    (SELECT id_tratamiento FROM tratamiento WHERE codigo = 'ODO-002'),
    (SELECT id_cita FROM cita c JOIN paciente p ON p.id_paciente = c.id_paciente
      WHERE p.email = 'carlos.quispe@example.com'
        AND c.fecha_hora_inicio = ((CURRENT_DATE - INTERVAL '14 days') + TIME '09:00')::timestamptz),
    (SELECT id_user FROM app_user WHERE username = 'dentista1'),
    ((CURRENT_DATE - INTERVAL '14 days') + TIME '09:00')::timestamptz,
    'Destartraje con ultrasonido en cuatro cuadrantes, pulido y flúor.',
    'Se recomienda control en seis meses.',
    350.00;

-- Procedimientos sin cita previa (atención directa)
INSERT INTO tratamiento_realizado (id_paciente, id_tratamiento, id_dentista, fecha_realizacion, detalle_procedimiento, observaciones, costo)
SELECT
    (SELECT id_paciente FROM paciente WHERE email = 'maria.suarez@example.com'),
    (SELECT id_tratamiento FROM tratamiento WHERE codigo = 'ODO-006'),
    (SELECT id_user FROM app_user WHERE username = 'dentista1'),
    ((CURRENT_DATE - INTERVAL '30 days') + TIME '11:00')::timestamptz,
    'Toma de impresión, prueba de estructura y cementado de corona de porcelana en pieza 16.',
    'Control de oclusión satisfactorio.',
    1800.00;

INSERT INTO tratamiento_realizado (id_paciente, id_tratamiento, id_dentista, fecha_realizacion, detalle_procedimiento, observaciones, costo)
SELECT
    (SELECT id_paciente FROM paciente WHERE email = 'jorge.villarroel@example.com'),
    (SELECT id_tratamiento FROM tratamiento WHERE codigo = 'ODO-004'),
    (SELECT id_user FROM app_user WHERE username = 'dentista2'),
    ((CURRENT_DATE - INTERVAL '21 days') + TIME '15:30')::timestamptz,
    'Apertura cameral, instrumentación de conducto único y obturación con gutapercha.',
    'Pieza 36 sin síntomas posteriores.',
    1200.00;

INSERT INTO tratamiento_realizado (id_paciente, id_tratamiento, id_dentista, fecha_realizacion, detalle_procedimiento, observaciones, costo)
SELECT
    (SELECT id_paciente FROM paciente WHERE email = 'rocio.aguilar@example.com'),
    (SELECT id_tratamiento FROM tratamiento WHERE codigo = 'ODO-005'),
    (SELECT id_user FROM app_user WHERE username = 'dentista1'),
    ((CURRENT_DATE - INTERVAL '10 days') + TIME '17:00')::timestamptz,
    'Exodoncia de tercer molar superior derecho con anestesia local.',
    'Se indicó analgesia y control a los siete días.',
    500.00;

INSERT INTO tratamiento_realizado (id_paciente, id_tratamiento, id_dentista, fecha_realizacion, detalle_procedimiento, observaciones, costo)
SELECT
    (SELECT id_paciente FROM paciente WHERE email = 'carlos.quispe@example.com'),
    (SELECT id_tratamiento FROM tratamiento WHERE codigo = 'ODO-001'),
    (SELECT id_user FROM app_user WHERE username = 'dentista1'),
    ((CURRENT_DATE - INTERVAL '40 days') + TIME '09:00')::timestamptz,
    'Anamnesis, examen clínico y radiográfico, plan de tratamiento integral.',
    'Pendiente profilaxis y restauración.',
    150.00;

-- ============================================================================
-- 7. FACTURACION Y PAGOS (S9, S10)
-- Invariantes: total = suma del detalle; saldo = total - suma de pagos.
-- ============================================================================

-- F-000001: pagada en efectivo, saldo cero
INSERT INTO factura (numero, id_paciente, fecha_emision, id_estado_factura, total, saldo, observaciones)
VALUES ('F-000001',
    (SELECT id_paciente FROM paciente WHERE email = 'ana.mendoza@example.com'),
    ((CURRENT_DATE - INTERVAL '7 days') + TIME '11:00')::timestamptz,
    (SELECT id_estado_factura FROM cat_estado_factura WHERE code = 'PAGADA'),
    450.00, 0.00, 'Control de ortodoncia cancelado en recepción.');

INSERT INTO factura_detalle (id_factura, id_realizado, descripcion, monto)
SELECT f.id_factura,
    (SELECT r.id_realizado FROM tratamiento_realizado r JOIN paciente p ON p.id_paciente = r.id_paciente
      WHERE p.email = 'ana.mendoza@example.com' AND r.costo = 450.00),
    'Ortodoncia - control mensual (Dra. Ríos / Dr. Salas)', 450.00
FROM factura f WHERE f.numero = 'F-000001';

INSERT INTO pago (id_factura, fecha_pago, monto, id_metodo_pago, referencia)
SELECT f.id_factura, ((CURRENT_DATE - INTERVAL '7 days') + TIME '11:15')::timestamptz, 450.00,
    (SELECT id_metodo_pago FROM cat_metodo_pago WHERE code = 'EFECTIVO'), 'Recibo interno 1042'
FROM factura f WHERE f.numero = 'F-000001';

-- F-000002: pago parcial (dos pagos, saldo pendiente)
INSERT INTO factura (numero, id_paciente, fecha_emision, id_estado_factura, total, saldo, observaciones)
VALUES ('F-000002',
    (SELECT id_paciente FROM paciente WHERE email = 'carlos.quispe@example.com'),
    ((CURRENT_DATE - INTERVAL '14 days') + TIME '10:00')::timestamptz,
    (SELECT id_estado_factura FROM cat_estado_factura WHERE code = 'PAGO_PARCIAL'),
    500.00, 150.00, 'Profilaxis y consulta inicial. Saldo a completar en el próximo control.');

INSERT INTO factura_detalle (id_factura, id_realizado, descripcion, monto)
SELECT f.id_factura,
    (SELECT r.id_realizado FROM tratamiento_realizado r JOIN paciente p ON p.id_paciente = r.id_paciente
      WHERE p.email = 'carlos.quispe@example.com' AND r.costo = 350.00),
    'Limpieza dental (profilaxis)', 350.00
FROM factura f WHERE f.numero = 'F-000002';

INSERT INTO factura_detalle (id_factura, id_realizado, descripcion, monto)
SELECT f.id_factura,
    (SELECT r.id_realizado FROM tratamiento_realizado r JOIN paciente p ON p.id_paciente = r.id_paciente
      WHERE p.email = 'carlos.quispe@example.com' AND r.costo = 150.00),
    'Consulta y diagnóstico', 150.00
FROM factura f WHERE f.numero = 'F-000002';

INSERT INTO pago (id_factura, fecha_pago, monto, id_metodo_pago, referencia)
SELECT f.id_factura, ((CURRENT_DATE - INTERVAL '14 days') + TIME '10:10')::timestamptz, 200.00,
    (SELECT id_metodo_pago FROM cat_metodo_pago WHERE code = 'TARJETA'), 'POS 3312 - autorización 88120'
FROM factura f WHERE f.numero = 'F-000002';

INSERT INTO pago (id_factura, fecha_pago, monto, id_metodo_pago, referencia)
SELECT f.id_factura, ((CURRENT_DATE - INTERVAL '9 days') + TIME '16:40')::timestamptz, 150.00,
    (SELECT id_metodo_pago FROM cat_metodo_pago WHERE code = 'EFECTIVO'), 'Recibo interno 1051'
FROM factura f WHERE f.numero = 'F-000002';

-- F-000003: emitida, sin pagos, saldo completo
INSERT INTO factura (numero, id_paciente, fecha_emision, id_estado_factura, total, saldo, observaciones)
VALUES ('F-000003',
    (SELECT id_paciente FROM paciente WHERE email = 'maria.suarez@example.com'),
    ((CURRENT_DATE - INTERVAL '30 days') + TIME '12:00')::timestamptz,
    (SELECT id_estado_factura FROM cat_estado_factura WHERE code = 'EMITIDA'),
    1800.00, 1800.00, 'Corona de porcelana pieza 16. Pendiente de cobro.');

INSERT INTO factura_detalle (id_factura, id_realizado, descripcion, monto)
SELECT f.id_factura,
    (SELECT r.id_realizado FROM tratamiento_realizado r JOIN paciente p ON p.id_paciente = r.id_paciente
      WHERE p.email = 'maria.suarez@example.com' AND r.costo = 1800.00),
    'Corona de porcelana', 1800.00
FROM factura f WHERE f.numero = 'F-000003';

-- F-000004: anulada, sin efecto de cobro
INSERT INTO factura (numero, id_paciente, fecha_emision, id_estado_factura, total, saldo, observaciones)
VALUES ('F-000004',
    (SELECT id_paciente FROM paciente WHERE email = 'jorge.villarroel@example.com'),
    ((CURRENT_DATE - INTERVAL '21 days') + TIME '17:00')::timestamptz,
    (SELECT id_estado_factura FROM cat_estado_factura WHERE code = 'ANULADA'),
    1200.00, 1200.00, 'Anulada: el tratamiento se cubrió por convenio con el seguro.');

INSERT INTO factura_detalle (id_factura, id_realizado, descripcion, monto)
SELECT f.id_factura,
    (SELECT r.id_realizado FROM tratamiento_realizado r JOIN paciente p ON p.id_paciente = r.id_paciente
      WHERE p.email = 'jorge.villarroel@example.com' AND r.costo = 1200.00),
    'Endodoncia unirradicular', 1200.00
FROM factura f WHERE f.numero = 'F-000004';

-- ============================================================================
-- 8. RECORDATORIOS DE CITAS (S5, S15)
-- fecha_objetivo = inicio de la cita menos 24 horas.
-- ============================================================================

-- Pendientes: ya vencidos o por vencer, listos para el proceso programado
INSERT INTO notificacion_cita (id_cita, id_canal, id_estado_notificacion, destino, fecha_objetivo, mensaje)
SELECT c.id_cita,
    (SELECT id_canal FROM cat_canal_notificacion WHERE code = 'EMAIL'),
    (SELECT id_estado_notificacion FROM cat_estado_notificacion WHERE code = 'PENDIENTE'),
    p.email,
    c.fecha_hora_inicio - INTERVAL '24 hours',
    'Recordatorio: su cita en Clínica Dental es el ' || to_char(c.fecha_hora_inicio, 'DD/MM/YYYY HH24:MI') || '.'
FROM cita c JOIN paciente p ON p.id_paciente = c.id_paciente
WHERE c.fecha_hora_inicio = ((CURRENT_DATE + INTERVAL '1 day') + TIME '09:00')::timestamptz;

-- Enviada con éxito
INSERT INTO notificacion_cita (id_cita, id_canal, id_estado_notificacion, destino, fecha_objetivo, mensaje, enviado_en)
SELECT c.id_cita,
    (SELECT id_canal FROM cat_canal_notificacion WHERE code = 'EMAIL'),
    (SELECT id_estado_notificacion FROM cat_estado_notificacion WHERE code = 'ENVIADA'),
    p.email,
    c.fecha_hora_inicio - INTERVAL '24 hours',
    'Recordatorio: su cita en Clínica Dental es el ' || to_char(c.fecha_hora_inicio, 'DD/MM/YYYY HH24:MI') || '.',
    c.fecha_hora_inicio - INTERVAL '23 hours'
FROM cita c JOIN paciente p ON p.id_paciente = c.id_paciente
WHERE c.fecha_hora_inicio = ((CURRENT_DATE + INTERVAL '2 days') + TIME '11:00')::timestamptz;

-- Simulada: no hay SMTP configurado, queda registrada la intención de envío
INSERT INTO notificacion_cita (id_cita, id_canal, id_estado_notificacion, destino, fecha_objetivo, mensaje, enviado_en, error)
SELECT c.id_cita,
    (SELECT id_canal FROM cat_canal_notificacion WHERE code = 'EMAIL'),
    (SELECT id_estado_notificacion FROM cat_estado_notificacion WHERE code = 'SIMULADA'),
    p.email,
    c.fecha_hora_inicio - INTERVAL '24 hours',
    'Recordatorio: su cita en Clínica Dental es el ' || to_char(c.fecha_hora_inicio, 'DD/MM/YYYY HH24:MI') || '.',
    CURRENT_TIMESTAMP,
    'SMTP no configurado: el envío quedó registrado como simulado.'
FROM cita c JOIN paciente p ON p.id_paciente = c.id_paciente
WHERE c.fecha_hora_inicio = ((CURRENT_DATE + INTERVAL '2 days') + TIME '15:00')::timestamptz;

-- ============================================================================
-- FIN DE LOS DATOS SEMILLA
-- ============================================================================
