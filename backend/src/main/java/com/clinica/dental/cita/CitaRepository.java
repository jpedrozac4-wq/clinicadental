package com.clinica.dental.cita;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface CitaRepository extends JpaRepository<Cita, Integer> {

    /** Agenda en un rango de fechas, ordenada cronologicamente. */
    @Query("""
        SELECT c FROM Cita c
        WHERE c.fechaHoraInicio >= :desde AND c.fechaHoraInicio < :hasta
        ORDER BY c.fechaHoraInicio ASC
        """)
    List<Cita> agenda(@Param("desde") OffsetDateTime desde, @Param("hasta") OffsetDateTime hasta);

    /** Historial de citas de un paciente, del mas reciente al mas antiguo (S3). */
    @Query("""
        SELECT c FROM Cita c
        WHERE c.paciente.id = :idPaciente
        ORDER BY c.fechaHoraInicio DESC
        """)
    List<Cita> historialDePaciente(@Param("idPaciente") Integer idPaciente);

    /**
     * Cantidad de citas vigentes del dentista que se solapan con el bloque propuesto.
     * Solo {@code PROGRAMADA} y {@code CONFIRMADA} ocupan la agenda.
     *
     * <p>Es SQL nativo porque el fin del bloque es {@code inicio + duracion} y la aritmetica de
     * intervalos sobre {@code timestamptz} no se puede expresar en JPQL. Esa misma expresion no es
     * inmutable, asi que tampoco puede vivir en una columna generada.
     */
    @Query(value = """
        SELECT COUNT(*)
          FROM cita c
          JOIN cat_estado_cita e ON e.id_estado_cita = c.id_estado_cita
         WHERE c.id_dentista = :idDentista
           AND e.code IN ('PROGRAMADA', 'CONFIRMADA')
           AND c.fecha_hora_inicio < :fin
           AND :inicio < c.fecha_hora_inicio + (c.duracion_minutos * INTERVAL '1 minute')
        """, nativeQuery = true)
    long contarSolapadas(@Param("idDentista") Integer idDentista,
                         @Param("inicio") OffsetDateTime inicio,
                         @Param("fin") OffsetDateTime fin);

    Optional<Cita> findByIdAndEstadoCode(Integer id, String code);
}
