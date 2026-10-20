package com.empresa.calzado.erp.inventario;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface InventarioStockRepository extends JpaRepository<InventarioStock, Integer> {
    Optional<InventarioStock> findByBodegaIdAndCalzadoSkuId(Integer bodegaId, Integer skuId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM InventarioStock s WHERE s.bodega.id = :bodegaId AND s.calzadoSku.id = :skuId")
    Optional<InventarioStock> findStockConBloqueoPesimista(@Param("bodegaId") Integer bodegaId, @Param("skuId") Integer skuId);

    @Query("SELECT s FROM InventarioStock s WHERE s.cantidadDisponible <= s.stockSeguridadMinimo")
    List<InventarioStock> findTallasCriticasAgotadas();

    /**
     * Stock rows that a web order can be fulfilled from, for a given SKU and quantity. Only active
     * warehouses flagged for web dispatch are considered, and the rows are ordered by ascending
     * bodega id so the chosen warehouse is deterministic and never depends on query plan luck.
     */
    @Query("""
        SELECT s FROM InventarioStock s
        WHERE s.calzadoSku.id = :skuId
          AND s.cantidadDisponible >= :cantidad
          AND s.bodega.esDespachableWeb = true
          AND s.bodega.active = true
        ORDER BY s.bodega.id ASC
        """)
    List<InventarioStock> findStockDespachableWebPorSku(
        @Param("skuId") Integer skuId, @Param("cantidad") Integer cantidad);
}
