package com.clinica.dental.realizado;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TratamientoRealizadoRepository extends JpaRepository<TratamientoRealizado, Integer> {

    List<TratamientoRealizado> findByPacienteIdOrderByFechaRealizacionDesc(Integer idPaciente);

    List<TratamientoRealizado> findAllByOrderByFechaRealizacionDesc();
}
