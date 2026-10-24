package com.clinica.dental.reporte;

import com.clinica.dental.reporte.dto.IngresoMes;
import com.clinica.dental.reporte.dto.IngresoPorMetodoPago;
import com.clinica.dental.reporte.dto.IngresosResponse;
import com.clinica.dental.reporte.dto.OcupacionPorEstado;
import com.clinica.dental.reporte.dto.OcupacionPorOdontologo;
import com.clinica.dental.reporte.dto.OcupacionResponse;
import com.clinica.dental.reporte.dto.ProduccionPorOdontologo;
import com.clinica.dental.reporte.dto.ProduccionResponse;
import com.clinica.dental.shared.ClinicaBusinessException;
import com.clinica.dental.shared.ManejadorErroresClinica;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrato HTTP de los reportes de solo lectura (S21). */
@ExtendWith(MockitoExtension.class)
class ReporteControllerTest {

    @Mock private ReporteService service;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ReporteController(service))
            .setControllerAdvice(new ManejadorErroresClinica())
            .build();
    }

    @Test
    void losIngresosDevuelvenTotalPorMetodoYPorMes() throws Exception {
        when(service.ingresos(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)))
            .thenReturn(new IngresosResponse(
                new BigDecimal("450.50"),
                List.of(new IngresoPorMetodoPago("EFECTIVO", new BigDecimal("450.50"), 2L)),
                List.of(new IngresoMes("2026-03", new BigDecimal("450.50")))));

        mvc.perform(get("/api/reportes/ingresos")
                .param("desde", "2026-03-01")
                .param("hasta", "2026-03-31"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalCobrado").value(450.50))
            .andExpect(jsonPath("$.porMetodoPago[0].metodo").value("EFECTIVO"))
            .andExpect(jsonPath("$.porMetodoPago[0].total").value(450.50))
            .andExpect(jsonPath("$.porMetodoPago[0].cantidad").value(2))
            .andExpect(jsonPath("$.porMes[0].mes").value("2026-03"))
            .andExpect(jsonPath("$.porMes[0].total").value(450.50));

        verify(service).ingresos(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));
    }

    @Test
    void losIngresosSinParametrosDeleganElRangoEnElServicio() throws Exception {
        when(service.ingresos(null, null)).thenReturn(new IngresosResponse(
            BigDecimal.ZERO, List.of(), List.of()));

        mvc.perform(get("/api/reportes/ingresos"))
            .andExpect(status().isOk());

        verify(service).ingresos(null, null);
    }

    @Test
    void unRangoInvertidoDevuelve400ConMensaje() throws Exception {
        when(service.ingresos(LocalDate.of(2026, 3, 31), LocalDate.of(2026, 3, 1)))
            .thenThrow(new ClinicaBusinessException(HttpStatus.BAD_REQUEST,
                "El rango de fechas es invalido: desde no puede ser posterior a hasta"));

        mvc.perform(get("/api/reportes/ingresos")
                .param("desde", "2026-03-31")
                .param("hasta", "2026-03-01"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("ERROR"))
            .andExpect(jsonPath("$.message").value(
                org.hamcrest.Matchers.containsString("desde no puede ser posterior a hasta")));
    }

    @Test
    void unaFechaIlegibleDevuelve400SinLlamarAlServicio() throws Exception {
        mvc.perform(get("/api/reportes/ingresos")
                .param("desde", "no-es-una-fecha")
                .param("hasta", "2026-03-31"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void losIngresosSinDatosDevuelvenCerosYListasVacias() throws Exception {
        when(service.ingresos(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)))
            .thenReturn(new IngresosResponse(BigDecimal.ZERO, List.of(), List.of()));

        mvc.perform(get("/api/reportes/ingresos")
                .param("desde", "2026-03-01")
                .param("hasta", "2026-03-31"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalCobrado").value(0))
            .andExpect(jsonPath("$.porMetodoPago").isArray())
            .andExpect(jsonPath("$.porMetodoPago").isEmpty())
            .andExpect(jsonPath("$.porMes").isArray())
            .andExpect(jsonPath("$.porMes").isEmpty());
    }

    @Test
    void laProduccionDevuelvePorOdontologoYLosTotales() throws Exception {
        when(service.produccion(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)))
            .thenReturn(new ProduccionResponse(
                List.of(new ProduccionPorOdontologo(3, "Dra. Valeria Ríos", 3L,
                    new BigDecimal("1050.50"))),
                3L, new BigDecimal("1050.50")));

        mvc.perform(get("/api/reportes/produccion")
                .param("desde", "2026-03-01")
                .param("hasta", "2026-03-31"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.porOdontologo[0].idDentista").value(3))
            .andExpect(jsonPath("$.porOdontologo[0].odontologo").value("Dra. Valeria Ríos"))
            .andExpect(jsonPath("$.porOdontologo[0].cantidad").value(3))
            .andExpect(jsonPath("$.porOdontologo[0].importe").value(1050.50))
            .andExpect(jsonPath("$.totalProcedimientos").value(3))
            .andExpect(jsonPath("$.totalImporte").value(1050.50));
    }

    @Test
    void laOcupacionDevuelvePorEstadoYPorOdontologoConTotal() throws Exception {
        when(service.ocupacion(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)))
            .thenReturn(new OcupacionResponse(
                List.of(new OcupacionPorEstado("ATENDIDA", 3L)),
                List.of(new OcupacionPorOdontologo(3, "Dra. Valeria Ríos", 3L)),
                3L));

        mvc.perform(get("/api/reportes/ocupacion")
                .param("desde", "2026-03-01")
                .param("hasta", "2026-03-31"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.porEstado[0].estado").value("ATENDIDA"))
            .andExpect(jsonPath("$.porEstado[0].cantidad").value(3))
            .andExpect(jsonPath("$.porOdontologo[0].idDentista").value(3))
            .andExpect(jsonPath("$.porOdontologo[0].odontologo").value("Dra. Valeria Ríos"))
            .andExpect(jsonPath("$.porOdontologo[0].cantidad").value(3))
            .andExpect(jsonPath("$.totalCitas").value(3));
    }

    @Test
    void laOcupacionSinDatosDevuelveListasVaciasYTotalCero() throws Exception {
        when(service.ocupacion(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)))
            .thenReturn(new OcupacionResponse(List.of(), List.of(), 0L));

        mvc.perform(get("/api/reportes/ocupacion")
                .param("desde", "2026-03-01")
                .param("hasta", "2026-03-31"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.porEstado").isArray())
            .andExpect(jsonPath("$.porEstado").isEmpty())
            .andExpect(jsonPath("$.porOdontologo").isEmpty())
            .andExpect(jsonPath("$.totalCitas").value(0));
    }

    @Test
    void laProduccionSinDatosDevuelveListaVaciaYTotalesEnCero() throws Exception {
        when(service.produccion(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)))
            .thenReturn(new ProduccionResponse(List.of(), 0L, BigDecimal.ZERO));

        mvc.perform(get("/api/reportes/produccion")
                .param("desde", "2026-03-01")
                .param("hasta", "2026-03-31"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.porOdontologo").isArray())
            .andExpect(jsonPath("$.porOdontologo").isEmpty())
            .andExpect(jsonPath("$.totalProcedimientos").value(0))
            .andExpect(jsonPath("$.totalImporte").value(0));
    }
}
