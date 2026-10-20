package com.clinica.dental.paciente;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PacienteRepository extends JpaRepository<Paciente, Integer> {

    /**
     * Busca pacientes vigentes por nombres, apellidos o email. Con {@code texto} nulo devuelve
     * el listado completo ordenado por apellido, que es como los muestra recepcion.
     */
    @Query("""
        SELECT p FROM Paciente p
        WHERE p.active = true
          AND (:texto IS NULL
               OR LOWER(p.nombres) LIKE :texto
               OR LOWER(p.apellidos) LIKE :texto
               OR LOWER(p.email) LIKE :texto)
        ORDER BY p.apellidos ASC, p.nombres ASC
        """)
    List<Paciente> buscar(@Param("texto") String texto);

    Optional<Paciente> findByIdAndActiveTrue(Integer id);
}
