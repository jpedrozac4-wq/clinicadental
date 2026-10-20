-- ============================================================================
-- SCHEMA DE GESTION PARA CONSULTORIO DENTAL
-- PostgreSQL 16 - Modelo relacional normalizado (3FN) con integridad referencial
-- Requisitos: pacientes, citas con recordatorios, tratamientos y facturacion
-- con pagos parciales. Ver odd/tasks/clinica-dental.md (S1-S10).
-- ============================================================================

\encoding UTF8

-- ============================================================================
-- 1. FUNCIONES UTILITARIAS DE INTEGRIDAD
-- ============================================================================

-- Sella updated_at en cada UPDATE para no depender de la capa de aplicacion.
CREATE OR REPLACE FUNCTION fn_update_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Protege los registros financieros y de auditoria: son inmutables por diseño.
CREATE OR REPLACE FUNCTION fn_prevent_delete_financial()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'El registro % de la tabla % es financiero y no puede eliminarse', OLD, TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

-- ============================================================================
-- 2. CATALOGOS DE ESTADOS Y TIPOS (dominio controlado)
-- ============================================================================

-- Ciclo de vida de una cita (S4, S6)
CREATE TABLE cat_estado_cita (
    id_estado_cita SERIAL PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    description VARCHAR(150) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Canal por el que se envia un recordatorio (S5)
CREATE TABLE cat_canal_notificacion (
    id_canal SERIAL PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    description VARCHAR(150) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Resultado del intento de recordatorio (S5, S15)
CREATE TABLE cat_estado_notificacion (
    id_estado_notificacion SERIAL PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    description VARCHAR(150) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Formas de pago aceptadas (S10)
CREATE TABLE cat_metodo_pago (
    id_metodo_pago SERIAL PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    description VARCHAR(150) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Estados fiscales del comprobante (S9, S10)
CREATE TABLE cat_estado_factura (
    id_estado_factura SERIAL PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    description VARCHAR(150) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Tipos de operacion registrables en la bitacora
CREATE TABLE cat_log_accion (
    id_log_accion SERIAL PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    description VARCHAR(150) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
-- 3. SEGURIDAD, AUTENTICACION Y RBAC
-- ============================================================================

-- Perfiles de la clinica: administrador, dentista y recepcionista
CREATE TABLE role (
    id_role SERIAL PRIMARY KEY,
    role_name VARCHAR(60) NOT NULL UNIQUE,
    description VARCHAR(200),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Permisos granulares por modulo y accion
CREATE TABLE permission (
    id_permission SERIAL PRIMARY KEY,
    permission_name VARCHAR(100) NOT NULL UNIQUE,
    modulo VARCHAR(50) NOT NULL,
    action VARCHAR(30) NOT NULL,
    description VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Matriz de permisos por rol (N:M)
CREATE TABLE role_permission (
    id_role INT NOT NULL REFERENCES role(id_role) ON DELETE CASCADE ON UPDATE CASCADE,
    id_permission INT NOT NULL REFERENCES permission(id_permission) ON DELETE CASCADE ON UPDATE CASCADE,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id_role, id_permission)
);

-- Usuarios del sistema con credenciales hasheadas (BCrypt)
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
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Asignacion de roles a usuarios (N:M)
CREATE TABLE user_role (
    id_user INT NOT NULL REFERENCES app_user(id_user) ON DELETE CASCADE ON UPDATE CASCADE,
    id_role INT NOT NULL REFERENCES role(id_role) ON DELETE CASCADE ON UPDATE CASCADE,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id_user, id_role)
);

-- ============================================================================
-- 4. GESTION DE PACIENTES (S1, S2, S3)
-- ============================================================================

CREATE TABLE paciente (
    id_paciente SERIAL PRIMARY KEY,
    nombres VARCHAR(80) NOT NULL,
    apellidos VARCHAR(80) NOT NULL,
    fecha_nacimiento DATE NOT NULL,
    telefono VARCHAR(25) NOT NULL,
    direccion VARCHAR(200) NOT NULL,
    email VARCHAR(120) NOT NULL,
    -- Baja logica (S2): el registro sale de listados pero conserva su historial.
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by INT REFERENCES app_user(id_user) ON DELETE SET NULL ON UPDATE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_paciente_nacimiento CHECK (fecha_nacimiento <= CURRENT_DATE)
);

-- ============================================================================
-- 5. GESTION DE TRATAMIENTOS (S7, S8)
-- ============================================================================

-- Catalogo de tratamientos dentales disponibles con descripcion y precio
CREATE TABLE tratamiento (
    id_tratamiento SERIAL PRIMARY KEY,
    codigo VARCHAR(20) NOT NULL UNIQUE,
    nombre VARCHAR(120) NOT NULL,
    descripcion TEXT,
    precio NUMERIC(10,2) NOT NULL CHECK (precio > 0),
    duracion_minutos INT NOT NULL DEFAULT 30 CHECK (duracion_minutos > 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by INT REFERENCES app_user(id_user) ON DELETE SET NULL ON UPDATE CASCADE,
    updated_by INT REFERENCES app_user(id_user) ON DELETE SET NULL ON UPDATE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
-- 6. GESTION DE CITAS (S4, S6)
-- ============================================================================

CREATE TABLE cita (
    id_cita SERIAL PRIMARY KEY,
    id_paciente INT NOT NULL REFERENCES paciente(id_paciente) ON DELETE RESTRICT ON UPDATE CASCADE,
    id_tratamiento INT NOT NULL REFERENCES tratamiento(id_tratamiento) ON DELETE RESTRICT ON UPDATE CASCADE,
    id_dentista INT NOT NULL REFERENCES app_user(id_user) ON DELETE RESTRICT ON UPDATE CASCADE,
    id_estado_cita INT NOT NULL REFERENCES cat_estado_cita(id_estado_cita) ON DELETE RESTRICT ON UPDATE CASCADE,
    fecha_hora_inicio TIMESTAMPTZ NOT NULL,
    duracion_minutos INT NOT NULL CHECK (duracion_minutos > 0),
    motivo VARCHAR(200),
    notas TEXT,
    -- Reprogramacion (S6): la nueva cita apunta a la que reemplaza.
    id_cita_origen INT REFERENCES cita(id_cita) ON DELETE SET NULL ON UPDATE CASCADE,
    created_by INT REFERENCES app_user(id_user) ON DELETE SET NULL ON UPDATE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_cita_no_autoorigen CHECK (id_cita_origen IS NULL OR id_cita_origen <> id_cita)
);

-- ============================================================================
-- 7. TRATAMIENTOS REALIZADOS (S8, S3)
-- ============================================================================

-- El monto se congela al momento del registro: cambiar el precio del catalogo
-- no reescribe el historial clinico ni lo ya facturado.
CREATE TABLE tratamiento_realizado (
    id_realizado SERIAL PRIMARY KEY,
    id_paciente INT NOT NULL REFERENCES paciente(id_paciente) ON DELETE RESTRICT ON UPDATE CASCADE,
    id_tratamiento INT NOT NULL REFERENCES tratamiento(id_tratamiento) ON DELETE RESTRICT ON UPDATE CASCADE,
    id_cita INT REFERENCES cita(id_cita) ON DELETE SET NULL ON UPDATE CASCADE,
    id_dentista INT NOT NULL REFERENCES app_user(id_user) ON DELETE RESTRICT ON UPDATE CASCADE,
    fecha_realizacion TIMESTAMPTZ NOT NULL,
    detalle_procedimiento TEXT NOT NULL,
    observaciones TEXT,
    costo NUMERIC(10,2) NOT NULL CHECK (costo > 0),
    created_by INT REFERENCES app_user(id_user) ON DELETE SET NULL ON UPDATE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
-- 8. FACTURACION Y PAGOS (S9, S10)
-- ============================================================================

-- Comprobante con numeracion correlativa y saldo vivo
CREATE TABLE factura (
    id_factura SERIAL PRIMARY KEY,
    numero VARCHAR(20) NOT NULL UNIQUE,
    id_paciente INT NOT NULL REFERENCES paciente(id_paciente) ON DELETE RESTRICT ON UPDATE CASCADE,
    fecha_emision TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    id_estado_factura INT NOT NULL REFERENCES cat_estado_factura(id_estado_factura) ON DELETE RESTRICT ON UPDATE CASCADE,
    total NUMERIC(10,2) NOT NULL CHECK (total > 0),
    saldo NUMERIC(10,2) NOT NULL CHECK (saldo >= 0),
    observaciones TEXT,
    created_by INT REFERENCES app_user(id_user) ON DELETE SET NULL ON UPDATE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_factura_saldo_total CHECK (saldo <= total)
);

-- Detalle de la factura: un tratamiento realizado se factura una sola vez
CREATE TABLE factura_detalle (
    id_detalle SERIAL PRIMARY KEY,
    id_factura INT NOT NULL REFERENCES factura(id_factura) ON DELETE CASCADE ON UPDATE CASCADE,
    id_realizado INT NOT NULL UNIQUE REFERENCES tratamiento_realizado(id_realizado) ON DELETE RESTRICT ON UPDATE CASCADE,
    descripcion VARCHAR(200) NOT NULL,
    monto NUMERIC(10,2) NOT NULL CHECK (monto > 0)
);

-- Pagos totales o parciales imputados a una factura
CREATE TABLE pago (
    id_pago SERIAL PRIMARY KEY,
    id_factura INT NOT NULL REFERENCES factura(id_factura) ON DELETE RESTRICT ON UPDATE CASCADE,
    fecha_pago TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    monto NUMERIC(10,2) NOT NULL CHECK (monto > 0),
    id_metodo_pago INT NOT NULL REFERENCES cat_metodo_pago(id_metodo_pago) ON DELETE RESTRICT ON UPDATE CASCADE,
    referencia VARCHAR(80),
    created_by INT REFERENCES app_user(id_user) ON DELETE SET NULL ON UPDATE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
-- 9. RECORDATORIOS DE CITAS (S5, S15)
-- ============================================================================

-- Bitacora de recordatorios: cada cita programada genera uno pendiente y el
-- proceso programado deja aqui el resultado real del envio.
CREATE TABLE notificacion_cita (
    id_notificacion SERIAL PRIMARY KEY,
    id_cita INT NOT NULL REFERENCES cita(id_cita) ON DELETE CASCADE ON UPDATE CASCADE,
    id_canal INT NOT NULL REFERENCES cat_canal_notificacion(id_canal) ON DELETE RESTRICT ON UPDATE CASCADE,
    id_estado_notificacion INT NOT NULL REFERENCES cat_estado_notificacion(id_estado_notificacion) ON DELETE RESTRICT ON UPDATE CASCADE,
    destino VARCHAR(120) NOT NULL,
    fecha_objetivo TIMESTAMPTZ NOT NULL,
    mensaje TEXT NOT NULL,
    enviado_en TIMESTAMPTZ,
    error VARCHAR(300),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
-- 10. AUDITORIA
-- ============================================================================

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

-- ============================================================================
-- 11. TRIGGERS: SELLADO DE TIMESTAMPS Y PROTECCION DE REGISTROS
-- ============================================================================

CREATE TRIGGER tr_upd_paciente BEFORE UPDATE ON paciente
    FOR EACH ROW EXECUTE FUNCTION fn_update_timestamp();
CREATE TRIGGER tr_upd_tratamiento BEFORE UPDATE ON tratamiento
    FOR EACH ROW EXECUTE FUNCTION fn_update_timestamp();
CREATE TRIGGER tr_upd_cita BEFORE UPDATE ON cita
    FOR EACH ROW EXECUTE FUNCTION fn_update_timestamp();
CREATE TRIGGER tr_upd_tratamiento_realizado BEFORE UPDATE ON tratamiento_realizado
    FOR EACH ROW EXECUTE FUNCTION fn_update_timestamp();
CREATE TRIGGER tr_upd_factura BEFORE UPDATE ON factura
    FOR EACH ROW EXECUTE FUNCTION fn_update_timestamp();
CREATE TRIGGER tr_upd_app_user BEFORE UPDATE ON app_user
    FOR EACH ROW EXECUTE FUNCTION fn_update_timestamp();

-- Los comprobantes, sus cobros y la bitacora no se borran nunca.
CREATE TRIGGER tr_no_del_factura BEFORE DELETE ON factura
    FOR EACH ROW EXECUTE FUNCTION fn_prevent_delete_financial();
CREATE TRIGGER tr_no_del_factura_detalle BEFORE DELETE ON factura_detalle
    FOR EACH ROW EXECUTE FUNCTION fn_prevent_delete_financial();
CREATE TRIGGER tr_no_del_pago BEFORE DELETE ON pago
    FOR EACH ROW EXECUTE FUNCTION fn_prevent_delete_financial();
CREATE TRIGGER tr_no_del_audit_log BEFORE DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION fn_prevent_delete_financial();

-- ============================================================================
-- 12. INDICES DE CONSULTA
-- ============================================================================

-- Busqueda de pacientes por nombre, apellido o email (pantalla de recepcion)
CREATE INDEX idx_paciente_busqueda ON paciente (apellidos, nombres) WHERE active;
CREATE INDEX idx_paciente_email ON paciente (lower(email));
CREATE INDEX idx_paciente_activo ON paciente (active);

-- Agenda diaria por dentista y control de solapamiento (S4)
CREATE INDEX idx_cita_dentista_fecha ON cita (id_dentista, fecha_hora_inicio);
CREATE INDEX idx_cita_paciente_fecha ON cita (id_paciente, fecha_hora_inicio DESC);
CREATE INDEX idx_cita_estado ON cita (id_estado_cita);
CREATE INDEX idx_cita_origen ON cita (id_cita_origen);

-- Historial clinico y facturacion por paciente (S3, S9)
CREATE INDEX idx_realizado_paciente ON tratamiento_realizado (id_paciente, fecha_realizacion DESC);
CREATE INDEX idx_realizado_tratamiento ON tratamiento_realizado (id_tratamiento);
CREATE INDEX idx_factura_paciente ON factura (id_paciente, fecha_emision DESC);
CREATE INDEX idx_factura_estado ON factura (id_estado_factura);
CREATE INDEX idx_detalle_factura ON factura_detalle (id_factura);
CREATE INDEX idx_pago_factura ON pago (id_factura, fecha_pago);
CREATE INDEX idx_pago_metodo ON pago (id_metodo_pago);

-- Cola de recordatorios a despachar por el proceso programado (S5)
CREATE INDEX idx_notificacion_cola ON notificacion_cita (id_estado_notificacion, fecha_objetivo);
CREATE INDEX idx_notificacion_cita ON notificacion_cita (id_cita);

CREATE INDEX idx_audit_log_fecha ON audit_log (created_at DESC);
CREATE INDEX idx_audit_log_user ON audit_log (id_user);

-- ============================================================================
-- FIN DEL SCHEMA
-- ============================================================================
