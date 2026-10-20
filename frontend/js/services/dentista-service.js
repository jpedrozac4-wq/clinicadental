/* ============================================================================
   SERVICIO DE ODONTOLOGOS (DERIVADO DE LA AGENDA)
   ----------------------------------------------------------------------------
   El backend NO expone un endpoint que liste los usuarios con rol DENTISTA
   (las rutas /api/usuarios/** estan declaradas en SecurityConfig pero todavia
   no hay controlador). CitaResponse si publica, por cada cita, el id y el
   nombre completo del dentista asignado.

   Mientras falte ese endpoint, este servicio deriva el catalogo de odontologos
   de los datos reales de la agenda: pide un rango amplio de citas y descarta
   duplicados. Si la clinica todavia no tiene citas, la lista queda vacia y el
   formulario lo informa en vez de inventar ids.
   ============================================================================ */
import { CitaService } from "./cita-service.js";

/** Rango de barrido: dos anios atras y dos adelante cubre toda agenda real. */
const DIAS_BARRIDO = 730;

export class DentistaService {

    /** Lista de odontologos { id, nombre } deducida de las citas existentes. */
    static async listar() {
        const citas = await CitaService.listar({
            desde: instanteDesplazado(-DIAS_BARRIDO),
            hasta: instanteDesplazado(DIAS_BARRIDO)
        });

        const porId = new Map();
        citas.forEach(cita => {
            if (!cita.idDentista || porId.has(cita.idDentista)) return;
            porId.set(cita.idDentista, {
                id: cita.idDentista,
                nombre: cita.dentista || `Odontólogo ${cita.idDentista}`
            });
        });

        return [...porId.values()].sort((a, b) => a.nombre.localeCompare(b.nombre, "es"));
    }
}

function instanteDesplazado(dias) {
    const fecha = new Date();
    fecha.setDate(fecha.getDate() + dias);
    fecha.setMilliseconds(0);
    return fecha.toISOString();
}
