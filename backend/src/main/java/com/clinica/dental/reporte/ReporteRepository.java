package com.clinica.dental.reporte;

import com.clinica.dental.pago.Pago;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Consultas de solo lectura de los reportes y estadisticas de S21.
 *
 * <p>Es un repositorio dedicado al reporte: no agrega metodos a los repositorios de {@code pago},
 * {@code factura}, {@code realizado} ni {@code cita}, de modo que las consultas agregadas viven en un
 * unico lugar y no contaminan los contratos de escritura de cada modulo. {@link Pago} es solo el
 * ancla del marcador de Spring Data; las consultas cruzan las entidades del reporte.
 *
 * <p>Toda la agregacion ocurre en la base ({@code COUNT}/{@code SUM} con {@code GROUP BY}). El dinero
 * se transporta como {@link BigDecimal} de la columna {@code numeric(10,2)}: no hay conversion a
 * punto flotante ni redondeo en ninguna capa.
 *
 * <p>Las proyecciones de retorno se declaran como interfaces anidadas cuyos getters coinciden con los
 * alias de cada consulta.
 */
public interface ReporteRepository extends Repository<Pago, Integer> {

    /**
     * Cobrado por metodo de pago dentro del rango.
     *
     * <p>Excluye los pagos de comprobantes {@code ANULADA}: una factura anulada no aporta ingresos
     * aunque conserve sus pagos historicos (S10, S21). El rango es semiabierto, {@code [desde, hasta)},
     * para que el limite superior sea el inicio del dia siguiente y no se pierdan ni dupliquen pagos.
     */
    @Query("""
        SELECT m.code AS metodo, SUM(p.monto) AS total, COUNT(p) AS cantidad
          FROM Pago p
          JOIN p.metodoPago m
          JOIN p.factura f
          JOIN f.estado e
         WHERE e.code <> :estadoAnulado
           AND p.fechaPago >= :desde
           AND p.fechaPago < :hasta
         GROUP BY m.code
         ORDER BY m.code ASC
        """)
    List<PagoPorMetodo> ingresosPorMetodoPago(@Param("desde") OffsetDateTime desde,
                                              @Param("hasta") OffsetDateTime hasta,
                                              @Param("estadoAnulado") String estadoAnulado);

    /**
     * Cobrado por mes calendario dentro del rango, con la misma exclusion de facturas anuladas.
     *
     * <p>El mes se extrae en la base con {@code YEAR}/{@code MONTH} y se etiqueta como {@code YYYY-MM}
     * en la capa de servicio: asi el formato de presentacion no depende del motor de base de datos.
     */
    @Query("""
        SELECT YEAR(p.fechaPago) AS anio, MONTH(p.fechaPago) AS mes, SUM(p.monto) AS total
          FROM Pago p
          JOIN p.factura f
          JOIN f.estado e
         WHERE e.code <> :estadoAnulado
           AND p.fechaPago >= :desde
           AND p.fechaPago < :hasta
         GROUP BY YEAR(p.fechaPago), MONTH(p.fechaPago)
         ORDER BY YEAR(p.fechaPago) ASC, MONTH(p.fechaPago) ASC
        """)
    List<PagoPorMes> ingresosPorMes(@Param("desde") OffsetDateTime desde,
                                    @Param("hasta") OffsetDateTime hasta,
                                    @Param("estadoAnulado") String estadoAnulado);

    /**
     * Produccion agrupada por el odontologo que realizo cada procedimiento (S8, S21).
     *
     * <p>El importe suma {@code tratamiento_realizado.costo}, el costo congelado al registrar: editar
     * el precio del catalogo despues no altera lo ya producido.
     */
    @Query("""
        SELECT u.id AS idDentista, u.fullName AS odontologo,
               COUNT(r) AS cantidad, SUM(r.costo) AS importe
          FROM TratamientoRealizado r
          JOIN r.dentista u
         WHERE r.fechaRealizacion >= :desde
           AND r.fechaRealizacion < :hasta
         GROUP BY u.id, u.fullName
         ORDER BY u.fullName ASC
        """)
    List<ProduccionPorOdontologo> produccionPorOdontologo(@Param("desde") OffsetDateTime desde,
                                                          @Param("hasta") OffsetDateTime hasta);

    /**
     * Citas por estado segun su fecha y hora de inicio dentro del rango (S4, S21).
     *
     * <p>Se cuentan todas las citas, incluidas las canceladas y las reprogramadas: la ocupacion mide
     * que paso con la agenda, no solo lo que sigue vigente.
     */
    @Query("""
        SELECT e.code AS estado, COUNT(c) AS cantidad
          FROM Cita c
          JOIN c.estado e
         WHERE c.fechaHoraInicio >= :desde
           AND c.fechaHoraInicio < :hasta
         GROUP BY e.code
         ORDER BY e.code ASC
        """)
    List<CitasPorEstado> citasPorEstado(@Param("desde") OffsetDateTime desde,
                                        @Param("hasta") OffsetDateTime hasta);

    /** Citas por odontologo dentro del rango, con el mismo criterio de fecha de inicio. */
    @Query("""
        SELECT u.id AS idDentista, u.fullName AS odontologo, COUNT(c) AS cantidad
          FROM Cita c
          JOIN c.dentista u
         WHERE c.fechaHoraInicio >= :desde
           AND c.fechaHoraInicio < :hasta
         GROUP BY u.id, u.fullName
         ORDER BY u.fullName ASC
        """)
    List<CitasPorOdontologo> citasPorOdontologo(@Param("desde") OffsetDateTime desde,
                                                @Param("hasta") OffsetDateTime hasta);

    /** Cobrado de un metodo de pago: {@code SUM(monto)} y cantidad de pagos. */
    interface PagoPorMetodo {

        String getMetodo();

        BigDecimal getTotal();

        Long getCantidad();
    }

    /** Cobrado de un mes: anio y mes numericos, para etiquetarlos en el servicio. */
    interface PagoPorMes {

        Integer getAnio();

        Integer getMes();

        BigDecimal getTotal();
    }

    /** Procedimientos e importe de un odontologo. */
    interface ProduccionPorOdontologo {

        Integer getIdDentista();

        String getOdontologo();

        Long getCantidad();

        BigDecimal getImporte();
    }

    /** Cantidad de citas de un estado. */
    interface CitasPorEstado {

        String getEstado();

        Long getCantidad();
    }

    /** Cantidad de citas de un odontologo. */
    interface CitasPorOdontologo {

        Integer getIdDentista();

        String getOdontologo();

        Long getCantidad();
    }
}
