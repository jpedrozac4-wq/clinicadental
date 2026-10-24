/* ============================================================================
   VISTA: PANEL GENERAL (DASHBOARD)
   ----------------------------------------------------------------------------
   Resumen operativo de la clinica construido solo con datos reales del backend:
   pacientes registrados y catalogo de tratamientos.

   S22: el panel no anuncia modulos en preparacion ni rutas reservadas. Todos los
   modulos entregados (panel, pacientes, agenda, catalogo, facturacion con pagos,
   recordatorios, reportes, usuarios y auditoria) viven en el sidebar que arma
   core/shell.js a partir de ROUTES; el panel solo ofrece accesos rapidos a los
   que el perfil puede entrar.

   Base limpia: sin pacientes ni tratamientos las tarjetas muestran 0 y los
   promedios "—", nunca un error. Un fallo de red se marca por bloque con
   Promise.allSettled, sin romper el resto del panel.
   ============================================================================ */
import { AuthService } from "../services/auth-service.js";
import { PacienteService } from "../services/paciente-service.js";
import { TratamientoService } from "../services/tratamiento-service.js";
import { CONFIG } from "../config.js";
import {
    el, pageHeader, loadingState, kpiCard, table, money, edad, estadoBadge
} from "../core/dom.js";

const PROMEDIO_MINIMO = 1;

export class DashboardView {

    async mount(container) {
        const containerEl = container;
        const session = AuthService.getSession() || {};
        const header = pageHeader(
            this.saludo(session),
            this.roleHint(session)
        );

        containerEl.replaceChildren(header, loadingState("Cargando indicadores de la clínica…"));

        const [pacientesRes, tratamientosRes] = await Promise.allSettled([
            PacienteService.listar(),
            TratamientoService.listar()
        ]);

        const pacientes = pacientesRes.status === "fulfilled" ? pacientesRes.value : [];
        const tratamientos = tratamientosRes.status === "fulfilled" ? tratamientosRes.value : [];
        const pacientesOk = pacientesRes.status === "fulfilled";
        const tratamientosOk = tratamientosRes.status === "fulfilled";

        containerEl.replaceChildren(
            header,
            this.kpis(pacientes, tratamientos, { pacientesOk, tratamientosOk }),
            this.accionesRapidas(),
            this.pacientesRecientes(pacientes, pacientesOk),
            this.contextoSesion(session)
        );

        return null;
    }

    saludo(session) {
        const nombre = (session.fullName || session.username || "").split(" (")[0];
        const primerNombre = nombre.split(" ")[0] || "equipo";
        return `Hola, ${primerNombre}`;
    }

    roleHint(session) {
        const rol = session.roles?.[0] || "";
        if (rol === CONFIG.ROLES.ADMINISTRADOR) {
            return "Perfil administrador: gestión integral de pacientes, catálogo, usuarios y auditoría.";
        }
        if (rol === CONFIG.ROLES.DENTISTA) {
            return "Perfil odontólogo: fichas de pacientes, historial clínico y registro de tratamientos.";
        }
        if (rol === CONFIG.ROLES.RECEPCIONISTA) {
            return "Perfil recepción: registro de pacientes, agenda de citas y cobros.";
        }
        return "Resumen operativo de la clínica.";
    }

    kpis(pacientes, tratamientos, { pacientesOk, tratamientosOk }) {
        const precios = tratamientos
            .map(tratamiento => Number(tratamiento.precio))
            .filter(precio => Number.isFinite(precio) && precio > 0);
        const duraciones = tratamientos
            .map(tratamiento => Number(tratamiento.duracionMinutos))
            .filter(duracion => Number.isFinite(duracion) && duracion > 0);

        const precioPromedio = precios.length
            ? precios.reduce((suma, precio) => suma + precio, 0) / precios.length
            : null;
        const duracionPromedio = duraciones.length
            ? Math.round(duraciones.reduce((suma, duracion) => suma + duracion, 0) / duraciones.length)
            : null;

        return el("div", { class: "kpi-container" }, [
            kpiCard(
                "Pacientes registrados",
                pacientesOk ? String(pacientes.length) : "—",
                pacientesOk ? "Fichas vigentes en el sistema" : "Sin datos disponibles"
            ),
            kpiCard(
                "Tratamientos en catálogo",
                tratamientosOk ? String(tratamientos.length) : "—",
                tratamientosOk ? "Procedimientos activos y facturables" : "Sin datos disponibles"
            ),
            kpiCard(
                "Precio promedio",
                precioPromedio === null ? "—" : money(precioPromedio),
                precioPromedio === null ? "Sin datos disponibles" : `Sobre ${precios.length} tratamiento(s)`
            ),
            kpiCard(
                "Duración promedio",
                duracionPromedio === null || duracionPromedio < PROMEDIO_MINIMO
                    ? "—"
                    : `${duracionPromedio} min`,
                duracionPromedio === null ? "Sin datos disponibles" : "Duración media de una consulta"
            )
        ]);
    }

    accionesRapidas() {
        const acciones = [];

        if (AuthService.can(CONFIG.PERMISSIONS.PACIENTE_VER)) {
            acciones.push(el("a", {
                class: "btn btn-primary",
                href: "#/pacientes",
                text: "Ver pacientes"
            }));
        }
        if (AuthService.can(CONFIG.PERMISSIONS.PACIENTE_CREAR)) {
            acciones.push(el("a", {
                class: "btn btn-outline",
                href: "#/pacientes?nuevo=1",
                text: "Registrar paciente"
            }));
        }
        if (AuthService.can(CONFIG.PERMISSIONS.TRATAMIENTO_VER)) {
            acciones.push(el("a", {
                class: "btn btn-outline",
                href: "#/tratamientos",
                text: "Catálogo de tratamientos"
            }));
        }
        if (AuthService.can(CONFIG.PERMISSIONS.CITA_VER)) {
            acciones.push(el("a", {
                class: "btn btn-outline",
                href: "#/agenda",
                text: "Ver agenda"
            }));
        }
        if (AuthService.can(CONFIG.PERMISSIONS.FACTURA_VER)) {
            acciones.push(el("a", {
                class: "btn btn-outline",
                href: "#/facturacion",
                text: "Facturación y pagos"
            }));
        }
        if (AuthService.can(CONFIG.PERMISSIONS.REPORTE_VER)) {
            acciones.push(el("a", {
                class: "btn btn-outline",
                href: "#/reportes",
                text: "Reportes"
            }));
        }

        return el("div", { class: "card" }, [
            el("div", { class: "card-header" }, [el("h2", { text: "Accesos rápidos" })]),
            el("div", { class: "quick-actions" }, acciones.length
                ? acciones
                : [el("p", { class: "text-muted text-sm", text: "Tu perfil no tiene acciones habilitadas." })])
        ]);
    }

    pacientesRecientes(pacientes, ok) {
        const recientes = [...pacientes]
            .sort((a, b) => Number(b.id) - Number(a.id))
            .slice(0, 5);

        const rows = recientes.map(paciente => [
            el("span", { class: "cell-strong", text: `${paciente.nombres} ${paciente.apellidos}` }),
            edad(paciente.fechaNacimiento),
            paciente.telefono || "—",
            paciente.email || "—",
            el("a", {
                class: "btn btn-sm btn-outline",
                href: `#/pacientes/${paciente.id}/historial`,
                text: "Historial"
            })
        ]);

        const contenido = ok
            ? table(
                ["Paciente", "Edad", "Teléfono", "Correo", "Historial"],
                rows,
                { emptyMessage: "Todavía no hay pacientes registrados." }
            )
            : el("p", {
                class: "alert-box danger",
                text: "No se pudo consultar el listado de pacientes con el perfil actual."
            });

        return el("div", { class: "card" }, [
            el("div", { class: "card-header" }, [
                el("h2", { text: "Pacientes registrados recientemente" }),
                el("a", { class: "btn btn-sm btn-outline", href: "#/pacientes", text: "Ver todos" })
            ]),
            contenido
        ]);
    }

    contextoSesion(session) {
        const roles = AuthService.roleLabel(session.roles);
        return el("div", { class: "card" }, [
            el("div", { class: "card-header" }, [el("h2", { text: "Sesión activa" }), estadoBadge("ACTIVO")]),
            el("div", { class: "session-summary" }, [
                el("p", { class: "text-sm", text: `Usuario: ${session.username || "—"}` }),
                el("p", { class: "text-sm", text: `Nombre: ${session.fullName || "—"}` }),
                el("p", { class: "text-sm", text: `Rol: ${roles}` }),
                el("p", { class: "text-sm", text: `Permisos efectivos: ${(session.permissions || []).length}` })
            ])
        ]);
    }
}
