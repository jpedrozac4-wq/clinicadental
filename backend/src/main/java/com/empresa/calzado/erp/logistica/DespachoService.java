package com.empresa.calzado.erp.logistica;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;

@Service
public class DespachoService {

    private final DespachoEnvioRepository despachoRepository;
    private final DespachoEstadoRepository estadoRepository;

    public DespachoService(
        DespachoEnvioRepository despachoRepository,
        DespachoEstadoRepository estadoRepository
    ) {
        this.despachoRepository = despachoRepository;
        this.estadoRepository = estadoRepository;
    }

    public DespachoEnvio consultarTracking(String numeroGuia) {
        return despachoRepository.findByNumeroGuiaTracking(numeroGuia)
            .orElseThrow(() -> new IllegalArgumentException("Guía de tracking " + numeroGuia + " no encontrada"));
    }

    @Transactional
    public DespachoEnvio validarEntregaConOtp(String numeroGuia, String otpIngresado) {
        DespachoEnvio despacho = despachoRepository.findByNumeroGuiaTracking(numeroGuia)
            .orElseThrow(() -> new IllegalArgumentException("Guía no encontrada"));

        if (!despacho.getCodigoSecretoOtp().equals(otpIngresado)) {
            despacho.setIntentosEntrega((short) (despacho.getIntentosEntrega() + 1));
            despachoRepository.save(despacho);
            throw new SecurityException("Código OTP incorrecto. Entrega rechazada.");
        }

        DespachoEstado estadoEntregado = estadoRepository.findByCode("ENTREGADO")
            .orElseThrow(() -> new IllegalStateException("Estado ENTREGADO no configurado"));

        despacho.setDespachoEstado(estadoEntregado);
        despacho.setOtpVerificado(true);
        despacho.setFechaEntregaReal(OffsetDateTime.now());
        return despachoRepository.save(despacho);
    }
}
