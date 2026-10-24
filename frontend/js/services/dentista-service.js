/* ============================================================================
   SERVICIO DE ODONTOLOGOS (S20)
   ----------------------------------------------------------------------------
   El catalogo de odontologos es el padron real de usuarios: GET
   /api/usuarios?rol=DENTISTA devuelve los odontologos ACTIVOS con `id` y
   `nombre`. Antes de S20 este servicio derivaba la lista de las citas ya
   registradas, de modo que una clinica sin agenda no podia programar su primera
   cita; ahora la fuente es el padron y una base limpia devuelve [].

   Interfaz publica (la consumen agenda-module.js y realizado-form.js):
     DentistaService.listar() -> [{ id, nombre }] ordenado por nombre.

   El tipo de `id` se mantiene numerico, igual que `idDentista` en las citas, y
   los consumidores lo comparan con String(...) como antes.

   La vista que administra el padron es #/usuarios: crear un usuario con rol
   Odontologo es lo que habilita el selector de la agenda.
   ============================================================================ */
import { UsuarioService } from "./usuario-service.js";

export class DentistaService {

    /** Odontologos activos { id, nombre }, ordenados alfabeticamente. */
    static async listar() {
        const usuarios = await UsuarioService.odontologos();

        const porId = new Map();
        for (const usuario of usuarios) {
            const id = Number(usuario.id);
            if (!Number.isInteger(id) || porId.has(id)) continue;
            porId.set(id, {
                id,
                nombre: usuario.nombre || usuario.fullName || `Odontólogo ${id}`
            });
        }

        return [...porId.values()].sort((a, b) => a.nombre.localeCompare(b.nombre, "es"));
    }
}
