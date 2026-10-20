package com.empresa.calzado.erp.facturacion;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface FacturaTipoRepository extends JpaRepository<FacturaTipo, Integer> {
    Optional<FacturaTipo> findByCode(String code);

    /**
     * The document type row, locked with a PESSIMISTIC WRITE lock.
     *
     * <p>Invoicing takes this lock on the {@code cat_factura_tipo} row of the document being issued
     * before reading the highest correlative of that type, so two concurrent sales of the same type
     * cannot compute the same next number (project Decision 34). The lock is held until the
     * invoicing transaction commits, which serializes same-type invoices; that is the declared
     * trade-off and the eventual fix is a counter table, which would need DDL.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM FacturaTipo t WHERE t.code = :code")
    Optional<FacturaTipo> findByCodeConBloqueoPesimista(@Param("code") String code);
}
