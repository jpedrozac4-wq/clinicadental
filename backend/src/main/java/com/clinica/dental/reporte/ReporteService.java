package com.clinica.dental.reporte;

import com.clinica.dental.factura.EstadoFactura;
import com.clinica.dental.reporte.dto.IngresoMes;
import com.clinica.dental.reporte.dto.IngresoPorMetodoPago;
import com.clinica.dental.reporte.dto.IngresosResponse;
import com.clinica.dental.reporte.dto.OcupacionPorEstado;
import com.clinica.dental.reporte.dto.OcupacionPorOdontologo;
import com.clinica.dental.reporte.dto.OcupacionResponse;
import com.clinica.dental.reporte.dto.ProduccionPorOdontologo;
import com.clinica.dental.reporte.dto.ProduccionResponse;
import com.clinica.dental.shared.ClinicaBusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * Reportes y estadisticas de solo lectura (S21).
 *
 * <p>El servicio no escribe nada: agrega en la base a traves de {@link ReporteRepository} y traduce
 * las filas al contrato JSON de los tres endpoints. El rango de fechas se resuelve una sola vez en
 * {@link Rango} y se aplica igual a los tres reportes.
 *
 * <p>Sin datos, cada reporte devuelve estructuras vacias con totales en cero, nunca {@code null}:
 * el frontend los renderiza directamente. El dinero se suma con {@link BigDecimal} y no se redondea
 * en ninguna etapa.
 */
@Service
public class ReporteService {

    /**
     * Zona horaria con la que se traducen las fechas del reporte a los instantes de la base.
     *
     * <p>Coincide con la que usa el resto del dominio ({@code OffsetDateTime.now()}), de modo que un
     * pago registrado hoy cae en el dia y en el mes que ve el usuario.
     */
    private static final ZoneId ZONA = ZoneId.systemDefault();

    private final ReporteRepository reportes;

    public ReporteService(ReporteRepository reportes) {
        this.reportes = reportes;
    }

    /**
     * Ingresos cobrados en el rango, por metodo de pago y por mes.
     *
     * <p>Solo cuentan los pagos que acreditan una factura no anulada. El total es la suma exacta de
     * los parciales: el total cobrado del reporte siempre cuadra con su desglose.
     */
    @Transactional(readOnly = true)
    public IngresosResponse ingresos(LocalDate desde, LocalDate hasta) {
        Rango rango = Rango.de(desde, hasta);

        List<ReporteRepository.PagoPorMetodo> filas =
            nulos(reportes.ingresosPorMetodoPago(rango.inicio(), rango.finExclusivo(), EstadoFactura.ANULADA));
        List<ReporteRepository.PagoPorMes> filasMes =
            nulos(reportes.ingresosPorMes(rango.inicio(), rango.finExclusivo(), EstadoFactura.ANULADA));

        List<IngresoPorMetodoPago> porMetodoPago = new ArrayList<>(filas.size());
        BigDecimal totalCobrado = BigDecimal.ZERO;
        for (ReporteRepository.PagoPorMetodo fila : filas) {
            BigDecimal total = dinero(fila.getTotal());
            totalCobrado = totalCobrado.add(total);
            porMetodoPago.add(new IngresoPorMetodoPago(fila.getMetodo(), total, cantidad(fila.getCantidad())));
        }

        List<IngresoMes> porMes = new ArrayList<>(filasMes.size());
        for (ReporteRepository.PagoPorMes fila : filasMes) {
            porMes.add(new IngresoMes(etiquetaMes(fila.getAnio(), fila.getMes()), dinero(fila.getTotal())));
        }

        return new IngresosResponse(totalCobrado, List.copyOf(porMetodoPago), List.copyOf(porMes));
    }

    /** Procedimientos realizados en el rango, agrupados por el odontologo que los ejecuto. */
    @Transactional(readOnly = true)
    public ProduccionResponse produccion(LocalDate desde, LocalDate hasta) {
        Rango rango = Rango.de(desde, hasta);

        List<ReporteRepository.ProduccionPorOdontologo> filas =
            nulos(reportes.produccionPorOdontologo(rango.inicio(), rango.finExclusivo()));

        List<ProduccionPorOdontologo> porOdontologo = new ArrayList<>(filas.size());
        long totalProcedimientos = 0L;
        BigDecimal totalImporte = BigDecimal.ZERO;
        for (ReporteRepository.ProduccionPorOdontologo fila : filas) {
            BigDecimal importe = dinero(fila.getImporte());
            long parcial = cantidad(fila.getCantidad());
            totalProcedimientos += parcial;
            totalImporte = totalImporte.add(importe);
            porOdontologo.add(new ProduccionPorOdontologo(fila.getIdDentista(), fila.getOdontologo(),
                parcial, importe));
        }

        return new ProduccionResponse(List.copyOf(porOdontologo), totalProcedimientos, totalImporte);
    }

    /** Citas cuya fecha y hora de inicio cae en el rango, por estado y por odontologo (S21). */
    @Transactional(readOnly = true)
    public OcupacionResponse ocupacion(LocalDate desde, LocalDate hasta) {
        Rango rango = Rango.de(desde, hasta);

        List<ReporteRepository.CitasPorEstado> filasEstado =
            nulos(reportes.citasPorEstado(rango.inicio(), rango.finExclusivo()));
        List<ReporteRepository.CitasPorOdontologo> filasOdontologo =
            nulos(reportes.citasPorOdontologo(rango.inicio(), rango.finExclusivo()));

        List<OcupacionPorEstado> porEstado = new ArrayList<>(filasEstado.size());
        long totalCitas = 0L;
        for (ReporteRepository.CitasPorEstado fila : filasEstado) {
            long parcial = cantidad(fila.getCantidad());
            totalCitas += parcial;
            porEstado.add(new OcupacionPorEstado(fila.getEstado(), parcial));
        }

        List<OcupacionPorOdontologo> porOdontologo = new ArrayList<>(filasOdontologo.size());
        for (ReporteRepository.CitasPorOdontologo fila : filasOdontologo) {
            porOdontologo.add(new OcupacionPorOdontologo(fila.getIdDentista(), fila.getOdontologo(),
                cantidad(fila.getCantidad())));
        }

        return new OcupacionResponse(List.copyOf(porEstado), List.copyOf(porOdontologo), totalCitas);
    }

    private static BigDecimal dinero(BigDecimal monto) {
        return monto == null ? BigDecimal.ZERO : monto;
    }

    private static long cantidad(Long valor) {
        return valor == null ? 0L : valor;
    }

    private static String etiquetaMes(Integer anio, Integer mes) {
        return String.format("%04d-%02d", anio == null ? 0 : anio, mes == null ? 0 : mes);
    }

    private static <T> List<T> nulos(List<T> filas) {
        return filas == null ? List.of() : filas;
    }

    /**
     * Rango de fechas ya validado y traducido a instantes de la base.
     *
     * @param inicio       primer instante incluido, o sea el inicio del dia {@code desde}
     * @param finExclusivo inicio del dia siguiente a {@code hasta}, de modo que el rango sea
     *                     inclusivo en ambos extremos sin depender de la hora
     */
    private record Rango(OffsetDateTime inicio, OffsetDateTime finExclusivo) {

        /**
         * Resuelve el rango del reporte.
         *
         * <p>Cada extremo ausente se completa por separado con el mes en curso: sin {@code desde}
         * arranca el primer dia del mes actual, y sin {@code hasta} termina el ultimo dia del mes
         * actual. Un rango invertido se rechaza con {@code 400} antes de tocar la base.
         */
        static Rango de(LocalDate desde, LocalDate hasta) {
            LocalDate hoy = LocalDate.now();
            LocalDate primerDia = desde != null ? desde : hoy.with(TemporalAdjusters.firstDayOfMonth());
            LocalDate ultimoDia = hasta != null ? hasta : hoy.with(TemporalAdjusters.lastDayOfMonth());
            if (primerDia.isAfter(ultimoDia)) {
                throw new ClinicaBusinessException(HttpStatus.BAD_REQUEST,
                    "El rango de fechas es invalido: desde no puede ser posterior a hasta");
            }
            return new Rango(inicioDelDia(primerDia), inicioDelDia(ultimoDia.plusDays(1)));
        }

        private static OffsetDateTime inicioDelDia(LocalDate fecha) {
            return fecha.atStartOfDay(ZONA).toOffsetDateTime();
        }
    }
}
