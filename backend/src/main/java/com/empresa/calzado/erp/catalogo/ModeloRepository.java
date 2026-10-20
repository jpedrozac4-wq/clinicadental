package com.empresa.calzado.erp.catalogo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ModeloRepository extends JpaRepository<Modelo, Integer> {
    List<Modelo> findByEsVisibleWebTrue();
    List<Modelo> findByEsOutletTrueAndEsVisibleWebTrue();

    @Query("SELECT m FROM Modelo m WHERE m.esVisibleWeb = true " +
           "AND (:tipoId IS NULL OR m.tipoCalzado.id = :tipoId) " +
           "AND (:marcaId IS NULL OR m.marca.id = :marcaId) " +
           "AND (:genero IS NULL OR m.genero = :genero) " +
           "AND (:esOutlet IS NULL OR m.esOutlet = :esOutlet)")
    List<Modelo> filtrarModelos(
        @Param("tipoId") Integer tipoId,
        @Param("marcaId") Integer marcaId,
        @Param("genero") String genero,
        @Param("esOutlet") Boolean esOutlet
    );

    /**
     * Back-office listing. Unlike {@link #filtrarModelos}, it does not constrain web visibility, so
     * models logically removed from the storefront (esVisibleWeb = false) remain manageable.
     *
     * @param q          lowercase name pattern including {@code %}, or {@code null} for no filter
     * @param visibleWeb optional exact web-visibility filter
     */
    @Query("SELECT m FROM Modelo m WHERE " +
           "(:q IS NULL OR LOWER(m.nombre) LIKE :q) " +
           "AND (:marcaId IS NULL OR m.marca.id = :marcaId) " +
           "AND (:tipoId IS NULL OR m.tipoCalzado.id = :tipoId) " +
           "AND (:outlet IS NULL OR m.esOutlet = :outlet) " +
           "AND (:visibleWeb IS NULL OR m.esVisibleWeb = :visibleWeb) " +
           "ORDER BY m.id ASC")
    List<Modelo> buscarAdmin(
        @Param("q") String q,
        @Param("marcaId") Integer marcaId,
        @Param("tipoId") Integer tipoId,
        @Param("outlet") Boolean outlet,
        @Param("visibleWeb") Boolean visibleWeb
    );

    List<Modelo> findByTipoCalzadoIdAndMarcaId(Integer tipoCalzadoId, Integer marcaId);

    boolean existsByTipoCalzadoIdAndMarcaIdAndCorrelativo(Integer tipoCalzadoId, Integer marcaId, Integer correlativo);

    boolean existsByTipoCalzadoIdAndMarcaIdAndCorrelativoAndIdNot(
        Integer tipoCalzadoId, Integer marcaId, Integer correlativo, Integer id);
}
