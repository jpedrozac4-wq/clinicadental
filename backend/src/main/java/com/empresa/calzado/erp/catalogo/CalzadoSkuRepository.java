package com.empresa.calzado.erp.catalogo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface CalzadoSkuRepository extends JpaRepository<CalzadoSku, Integer> {
    Optional<CalzadoSku> findBySku(String sku);
    boolean existsBySku(String sku);
    Optional<CalzadoSku> findByCodigoBarrasEan(String ean);
    List<CalzadoSku> findByModeloColorModeloId(Integer modeloId);
    boolean existsByModeloColorIdAndTallaId(Integer modeloColorId, Integer tallaId);

    /**
     * Guards the barcode as a unique scan key. The column only has a non-unique index, but
     * {@link #findByCodigoBarrasEan} is a single-result finder: a duplicated barcode would make the
     * POS lookup ambiguous or fail outright.
     */
    boolean existsByCodigoBarrasEan(String codigoBarrasEan);

    boolean existsByCodigoBarrasEanAndIdNot(String codigoBarrasEan, Integer id);
}
