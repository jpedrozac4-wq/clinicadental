package com.clinica.dental.auditoria;

/**
 * Operaciones criticas que la auditoria registra (S19).
 *
 * <p>Cada constante empareja el codigo real de {@code cat_log_accion} con la tabla sobre la que
 * actua la operacion. La tabla vive en el enum y no en cada punto de llamada porque la accion ya la
 * determina: asi no existe la forma de escribir {@code PACIENTE_BAJA} contra otra tabla.
 *
 * <p>{@code LOGIN} existe en el catalogo pero no aparece aca: S19 enumera las operaciones auditables
 * y el inicio de sesion no es una de ellas (no modifica datos de negocio).
 */
public enum AccionAuditoria {

    PACIENTE_CREAR("PACIENTE_CREAR", "paciente"),
    PACIENTE_ACTUALIZAR("PACIENTE_ACTUALIZAR", "paciente"),
    PACIENTE_BAJA("PACIENTE_BAJA", "paciente"),

    CITA_CREAR("CITA_CREAR", "cita"),
    CITA_REPROGRAMAR("CITA_REPROGRAMAR", "cita"),
    CITA_CANCELAR("CITA_CANCELAR", "cita"),

    TRATAMIENTO_CREAR("TRATAMIENTO_CREAR", "tratamiento"),
    TRATAMIENTO_ACTUALIZAR("TRATAMIENTO_ACTUALIZAR", "tratamiento"),

    REALIZADO_REGISTRAR("REALIZADO_REGISTRAR", "tratamiento_realizado"),

    FACTURA_EMITIR("FACTURA_EMITIR", "factura"),

    PAGO_REGISTRAR("PAGO_REGISTRAR", "pago"),

    USUARIO_CREAR("USUARIO_CREAR", "app_user"),
    USUARIO_ACTUALIZAR("USUARIO_ACTUALIZAR", "app_user"),
    USUARIO_BAJA("USUARIO_BAJA", "app_user"),

    ROL_CREAR("ROL_CREAR", "role"),
    ROL_ACTUALIZAR("ROL_ACTUALIZAR", "role"),
    ROL_ELIMINAR("ROL_ELIMINAR", "role");

    private final String codigo;
    private final String tabla;

    AccionAuditoria(String codigo, String tabla) {
        this.codigo = codigo;
        this.tabla = tabla;
    }

    /** Codigo canonical que debe existir en {@code cat_log_accion.code}. */
    public String getCodigo() {
        return codigo;
    }

    /** Tabla de negocio afectada por la operacion auditada. */
    public String getTabla() {
        return tabla;
    }
}
