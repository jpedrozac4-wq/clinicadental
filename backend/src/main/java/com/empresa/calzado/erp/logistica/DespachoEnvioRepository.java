package com.empresa.calzado.erp.logistica;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface DespachoEnvioRepository extends JpaRepository<DespachoEnvio, Integer> {
    Optional<DespachoEnvio> findByNumeroGuiaTracking(String numeroGuiaTracking);
    Optional<DespachoEnvio> findByNumeroGuiaTrackingAndCodigoSecretoOtp(String numeroGuia, String otp);
}
