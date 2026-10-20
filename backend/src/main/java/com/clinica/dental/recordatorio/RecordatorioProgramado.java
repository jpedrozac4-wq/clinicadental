package com.clinica.dental.recordatorio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Proceso programado que despacha los recordatorios vencidos (S5).
 *
 * <p>La cadencia es configurable y arranca con un valor conservador: cada 15 minutos, con un minuto
 * de gracia para que la aplicacion termine de levantar. Se puede ajustar con
 * {@code CLINICA_RECORDATORIO_INTERVALO_MS} y {@code CLINICA_RECORDATORIO_RETRASO_INICIAL_MS}.
 */
@Component
public class RecordatorioProgramado {

    private static final Logger log = LoggerFactory.getLogger(RecordatorioProgramado.class);

    private final RecordatorioService recordatorios;

    public RecordatorioProgramado(RecordatorioService recordatorios) {
        this.recordatorios = recordatorios;
    }

    @Scheduled(
        fixedDelayString = "${clinica.recordatorios.intervalo-ms:900000}",
        initialDelayString = "${clinica.recordatorios.retraso-inicial-ms:60000}")
    public void despacharVencidos() {
        int procesados = recordatorios.procesar().size();
        if (procesados > 0) {
            log.info("Proceso programado de recordatorios: {} recordatorio(s) despachado(s)", procesados);
        }
    }
}
