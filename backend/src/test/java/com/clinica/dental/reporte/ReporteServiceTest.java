package com.clinica.dental.reporte;

import com.clinica.dental.factura.EstadoFactura;
import com.clinica.dental.reporte.dto.IngresosResponse;
import com.clinica.dental.reporte.dto.OcupacionResponse;
import com.clinica.dental.reporte.dto.ProduccionResponse;
import com.clinica.dental.shared.ClinicaBusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Estadisticas de solo lectura: ingresos, produccion y ocupacion (S21). */
@ExtendWith(MockitoExtension.class)
class ReporteServiceTest {

    private static final ZoneId ZONA = ZoneId.systemDefault();

    @Mock private ReporteRepository reportes;

    @InjectMocks private ReporteService service;

    private static OffsetDateTime inicioDe(LocalDate fecha) {
        return fecha.atStartOfDay(ZONA).toOffsetDateTime();
    }

    private static ReporteRepository.PagoPorMetodo pagoPorMetodo(String metodo, String total, long cantidad) {
        ReporteRepository.PagoPorMetodo fila = mock(ReporteRepository.PagoPorMetodo.class);
        when(fila.getMetodo()).thenReturn(metodo);
        when(fila.getTotal()).thenReturn(new BigDecimal(total));
        when(fila.getCantidad()).thenReturn(cantidad);
        return fila;
    }

    private static ReporteRepository.PagoPorMes pagoPorMes(int anio, int mes, String total) {
        ReporteRepository.PagoPorMes fila = mock(ReporteRepository.PagoPorMes.class);
        when(fila.getAnio()).thenReturn(anio);
        when(fila.getMes()).thenReturn(mes);
        when(fila.getTotal()).thenReturn(new BigDecimal(total));
        return fila;
    }

    private static ReporteRepository.ProduccionPorOdontologo produccion(
            int id, String nombre, long cantidad, String importe) {
        ReporteRepository.ProduccionPorOdontologo fila =
            mock(ReporteRepository.ProduccionPorOdontologo.class);
        when(fila.getIdDentista()).thenReturn(id);
        when(fila.getOdontologo()).thenReturn(nombre);
        when(fila.getCantidad()).thenReturn(cantidad);
        when(fila.getImporte()).thenReturn(new BigDecimal(importe));
        return fila;
    }

    private static ReporteRepository.CitasPorEstado citaPorEstado(String estado, long cantidad) {
        ReporteRepository.CitasPorEstado fila = mock(ReporteRepository.CitasPorEstado.class);
        when(fila.getEstado()).thenReturn(estado);
        when(fila.getCantidad()).thenReturn(cantidad);
        return fila;
    }

    private static ReporteRepository.CitasPorOdontologo citaPorOdontologo(
            int id, String nombre, long cantidad) {
        ReporteRepository.CitasPorOdontologo fila = mock(ReporteRepository.CitasPorOdontologo.class);
        when(fila.getIdDentista()).thenReturn(id);
        when(fila.getOdontologo()).thenReturn(nombre);
        when(fila.getCantidad()).thenReturn(cantidad);
        return fila;
    }

    // ---------------------------------------------------------------- ingresos

    @Test
    void losIngresosDesglosanPorMetodoYPorMesYsumanElTotalCobrado() {
        LocalDate desde = LocalDate.of(2026, 3, 1);
        LocalDate hasta = LocalDate.of(2026, 3, 31);
        List<ReporteRepository.PagoPorMetodo> cobros = List.of(
            pagoPorMetodo("EFECTIVO", "100.10", 2),
            pagoPorMetodo("TARJETA", "0.20", 1));
        List<ReporteRepository.PagoPorMes> meses = List.of(
            pagoPorMes(2026, 2, "0.20"),
            pagoPorMes(2026, 3, "100.10"));
        when(reportes.ingresosPorMetodoPago(any(), any(), any())).thenReturn(cobros);
        when(reportes.ingresosPorMes(any(), any(), any())).thenReturn(meses);

        IngresosResponse respuesta = service.ingresos(desde, hasta);

        assertThat(respuesta.totalCobrado()).isEqualTo(new BigDecimal("100.30"));
        assertThat(respuesta.porMetodoPago())
            .extracting(f -> f.metodo())
            .containsExactly("EFECTIVO", "TARJETA");
        assertThat(respuesta.porMetodoPago().get(0).total()).isEqualTo(new BigDecimal("100.10"));
        assertThat(respuesta.porMetodoPago().get(0).cantidad()).isEqualTo(2L);
        assertThat(respuesta.porMes())
            .extracting(f -> f.mes())
            .containsExactly("2026-02", "2026-03");
        assertThat(respuesta.porMes().get(1).total()).isEqualTo(new BigDecimal("100.10"));
    }

    @Test
    void losIngresosSoloCuentanPagosDeFacturasNoAnuladas() {
        LocalDate desde = LocalDate.of(2026, 3, 1);
        LocalDate hasta = LocalDate.of(2026, 3, 31);
        when(reportes.ingresosPorMetodoPago(any(), any(), any())).thenReturn(List.of());
        when(reportes.ingresosPorMes(any(), any(), any())).thenReturn(List.of());

        service.ingresos(desde, hasta);

        // La exclusion de ANULADA vive en el JPQL; aqui se fija el contrato del repositorio.
        verify(reportes).ingresosPorMetodoPago(
            inicioDe(desde), inicioDe(hasta.plusDays(1)), EstadoFactura.ANULADA);
        verify(reportes).ingresosPorMes(
            inicioDe(desde), inicioDe(hasta.plusDays(1)), EstadoFactura.ANULADA);
    }

    @Test
    void losIngresosSinDatosDevuelvenEstructurasVaciasYTotalCero() {
        when(reportes.ingresosPorMetodoPago(any(), any(), any())).thenReturn(List.of());
        when(reportes.ingresosPorMes(any(), any(), any())).thenReturn(List.of());

        IngresosResponse respuesta = service.ingresos(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

        assertThat(respuesta.totalCobrado()).isEqualByComparingTo("0");
        assertThat(respuesta.porMetodoPago()).isNotNull().isEmpty();
        assertThat(respuesta.porMes()).isNotNull().isEmpty();
    }

    @Test
    void losIngresosSinRangoUsanElMesEnCurso() {
        when(reportes.ingresosPorMetodoPago(any(), any(), any())).thenReturn(List.of());
        when(reportes.ingresosPorMes(any(), any(), any())).thenReturn(List.of());

        service.ingresos(null, null);

        LocalDate hoy = LocalDate.now();
        verify(reportes).ingresosPorMetodoPago(
            inicioDe(hoy.with(TemporalAdjusters.firstDayOfMonth())),
            inicioDe(hoy.with(TemporalAdjusters.lastDayOfMonth()).plusDays(1)),
            EstadoFactura.ANULADA);
    }

    @Test
    void losIngresosConRangoInvertidoFallanCon400YNoConsultanLaBase() {
        ClinicaBusinessException error = catchThrowableOfType(
            () -> service.ingresos(LocalDate.of(2026, 3, 31), LocalDate.of(2026, 3, 1)),
            ClinicaBusinessException.class);

        assertThat(error).isNotNull();
        assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(error.getMessage()).contains("desde").contains("posterior");
        verifyNoInteractions(reportes);
    }

    // -------------------------------------------------------------- produccion

    @Test
    void laProduccionAgrupaPorOdontologoYSumaCantidadEImporte() {
        LocalDate desde = LocalDate.of(2026, 3, 1);
        LocalDate hasta = LocalDate.of(2026, 3, 31);
        List<ReporteRepository.ProduccionPorOdontologo> filas = List.of(
            produccion(3, "Dra. Valeria Ríos", 3, "1050.50"),
            produccion(7, "Dr. Mario Salas", 1, "350.00"));
        when(reportes.produccionPorOdontologo(any(), any())).thenReturn(filas);

        ProduccionResponse respuesta = service.produccion(desde, hasta);

        assertThat(respuesta.porOdontologo()).hasSize(2);
        assertThat(respuesta.porOdontologo().get(0).idDentista()).isEqualTo(3);
        assertThat(respuesta.porOdontologo().get(0).odontologo()).isEqualTo("Dra. Valeria Ríos");
        assertThat(respuesta.porOdontologo().get(0).cantidad()).isEqualTo(3L);
        assertThat(respuesta.porOdontologo().get(0).importe()).isEqualTo(new BigDecimal("1050.50"));
        assertThat(respuesta.totalProcedimientos()).isEqualTo(4L);
        assertThat(respuesta.totalImporte()).isEqualTo(new BigDecimal("1400.50"));
        verify(reportes).produccionPorOdontologo(inicioDe(desde), inicioDe(hasta.plusDays(1)));
    }

    @Test
    void laProduccionSinDatosDevuelveEstructurasVaciasYTotalesEnCero() {
        when(reportes.produccionPorOdontologo(any(), any())).thenReturn(List.of());

        ProduccionResponse respuesta = service.produccion(
            LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

        assertThat(respuesta.porOdontologo()).isNotNull().isEmpty();
        assertThat(respuesta.totalProcedimientos()).isZero();
        assertThat(respuesta.totalImporte()).isEqualByComparingTo("0");
    }

    @Test
    void laProduccionConRangoInvertidoFallaCon400YNoConsultaLaBase() {
        ClinicaBusinessException error = catchThrowableOfType(
            () -> service.produccion(LocalDate.of(2026, 3, 31), LocalDate.of(2026, 3, 1)),
            ClinicaBusinessException.class);

        assertThat(error).isNotNull();
        assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(reportes, never()).produccionPorOdontologo(any(), any());
    }

    // --------------------------------------------------------------- ocupacion

    @Test
    void laOcupacionDesglosaPorEstadoYPorOdontologoConTotalDeCitas() {
        LocalDate desde = LocalDate.of(2026, 3, 1);
        LocalDate hasta = LocalDate.of(2026, 3, 31);
        List<ReporteRepository.CitasPorEstado> porEstado = List.of(
            citaPorEstado("ATENDIDA", 2),
            citaPorEstado("PROGRAMADA", 3));
        List<ReporteRepository.CitasPorOdontologo> porOdontologo = List.of(
            citaPorOdontologo(7, "Dr. Mario Salas", 1),
            citaPorOdontologo(3, "Dra. Valeria Ríos", 4));
        when(reportes.citasPorEstado(any(), any())).thenReturn(porEstado);
        when(reportes.citasPorOdontologo(any(), any())).thenReturn(porOdontologo);

        OcupacionResponse respuesta = service.ocupacion(desde, hasta);

        assertThat(respuesta.porEstado()).extracting(f -> f.estado())
            .containsExactly("ATENDIDA", "PROGRAMADA");
        assertThat(respuesta.porEstado().get(1).cantidad()).isEqualTo(3L);
        assertThat(respuesta.porOdontologo()).extracting(f -> f.odontologo())
            .containsExactly("Dr. Mario Salas", "Dra. Valeria Ríos");
        assertThat(respuesta.porOdontologo().get(1).idDentista()).isEqualTo(3);
        assertThat(respuesta.totalCitas()).isEqualTo(5L);
        verify(reportes).citasPorEstado(inicioDe(desde), inicioDe(hasta.plusDays(1)));
        verify(reportes).citasPorOdontologo(inicioDe(desde), inicioDe(hasta.plusDays(1)));
    }

    @Test
    void laOcupacionSinDatosDevuelveEstructurasVaciasYTotalCero() {
        when(reportes.citasPorEstado(any(), any())).thenReturn(List.of());
        when(reportes.citasPorOdontologo(any(), any())).thenReturn(List.of());

        OcupacionResponse respuesta = service.ocupacion(
            LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

        assertThat(respuesta.porEstado()).isNotNull().isEmpty();
        assertThat(respuesta.porOdontologo()).isNotNull().isEmpty();
        assertThat(respuesta.totalCitas()).isZero();
    }

    @Test
    void laOcupacionConRangoInvertidoFallaCon400YNoConsultaLaBase() {
        ClinicaBusinessException error = catchThrowableOfType(
            () -> service.ocupacion(LocalDate.of(2026, 3, 31), LocalDate.of(2026, 3, 1)),
            ClinicaBusinessException.class);

        assertThat(error).isNotNull();
        assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(reportes, never()).citasPorEstado(any(), any());
        verify(reportes, never()).citasPorOdontologo(any(), any());
    }

    @Test
    void unSoloExtremoDelRangoSeCompletaConElMesEnCurso() {
        when(reportes.citasPorEstado(any(), any())).thenReturn(List.of());
        when(reportes.citasPorOdontologo(any(), any())).thenReturn(List.of());

        service.ocupacion(LocalDate.of(2026, 1, 15), null);

        ArgumentCaptor<OffsetDateTime> fin = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(reportes).citasPorEstado(eq(inicioDe(LocalDate.of(2026, 1, 15))), fin.capture());
        LocalDate hoy = LocalDate.now();
        assertThat(fin.getValue())
            .isEqualTo(inicioDe(hoy.with(TemporalAdjusters.lastDayOfMonth()).plusDays(1)));
    }
}
