package com.empresa.calzado.erp.ventas;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface CuponDescuentoRepository extends JpaRepository<CuponDescuento, Integer> {
    Optional<CuponDescuento> findByCodigoCuponAndActiveTrue(String codigoCupon);

    /**
     * Case-insensitive lookup used by the storefront: shoppers type coupon codes by hand, but the
     * canonical code stored (and returned) is the one persisted in the database row.
     */
    Optional<CuponDescuento> findByCodigoCuponIgnoreCaseAndActiveTrue(String codigoCupon);
}
