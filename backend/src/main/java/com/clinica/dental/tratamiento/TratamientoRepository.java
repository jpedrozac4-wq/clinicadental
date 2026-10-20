package com.clinica.dental.tratamiento;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TratamientoRepository extends JpaRepository<Tratamiento, Integer> {

    List<Tratamiento> findByActiveTrueOrderByNombreAsc();

    Optional<Tratamiento> findByIdAndActiveTrue(Integer id);

    boolean existsByCodigoIgnoreCase(String codigo);

    boolean existsByCodigoIgnoreCaseAndIdNot(String codigo, Integer id);
}
