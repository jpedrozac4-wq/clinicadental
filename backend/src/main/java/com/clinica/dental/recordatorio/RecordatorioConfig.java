package com.clinica.dental.recordatorio;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/**
 * Habilitacion del proceso programado de recordatorios (S5).
 *
 * <p>El {@link Clock} se expone como bean para que el despacho use una hora inyectable: la suite fija
 * el reloj y verifica la ventana de 24 horas sin depender de la hora real de la maquina.
 */
@Configuration
@EnableScheduling
public class RecordatorioConfig {

    @Bean
    public Clock relojRecordatorios() {
        return Clock.systemDefaultZone();
    }
}
