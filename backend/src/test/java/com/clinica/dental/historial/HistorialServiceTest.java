package com.clinica.dental.historial;

import com.clinica.dental.cita.Cita;
import com.clinica.dental.cita.CitaRepository;
import com.clinica.dental.cita.EstadoCita;
import com.clinica.dental.historial.dto.HistorialResponse;
import com.clinica.dental.paciente.Paciente;
import com.clinica.dental.paciente.PacienteRepository;
import com.clinica.dental.realizado.TratamientoRealizado;
import com.clinica.dental.realizado.TratamientoRealizadoRepository;
import com.clinica.dental.seguridad.SystemUser;
import com.clinica.dental.shared.ClinicaBusinessException;
import com.clinica.dental.tratamiento.Tratamiento;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.when;

/** Historial clinico por paciente (S3). */
@ExtendWith(MockitoExtension.class)
class HistorialServiceTest {

    @Mock private PacienteRepository pacientes;
    @Mock private CitaRepository citas;
    @Mock private TratamientoRealizadoRepository realizados;

    @InjectMocks private HistorialService service;

    private static Paciente paciente(int id, boolean activo) {
        Paciente p = new Paciente();
        p.setId(id);
        p.setNombres("Rocío Belén");
        p.setApellidos("Aguilar Sánchez");
        p.setActive(activo);
        return p;
    }

    private static Tratamiento tratamiento(int id, String nombre) {
        Tratamiento t = new Tratamiento();
        t.setId(id);
        t.setNombre(nombre);
        t.setPrecio(new BigDecimal("450.00"));
        return t;
    }

    private static Cita cita(String code) {
        Cita c = new Cita();
        c.setId(9);
        c.setPaciente(paciente(1, true));
        c.setTratamiento(tratamiento(2, "Consulta y diagnóstico"));
        SystemUser d = new SystemUser();
        d.setId(3);
        d.setFullName("Dra. Valeria Ríos");
        c.setDentista(d);
        EstadoCita estado = new EstadoCita();
        estado.setCode(code);
        c.setEstado(estado);
        c.setFechaHoraInicio(OffsetDateTime.now().minusDays(4));
        c.setDuracionMinutos(30);
        c.setMotivo("Primera consulta");
        return c;
    }

    private static TratamientoRealizado realizado() {
        TratamientoRealizado r = new TratamientoRealizado();
        r.setId(4);
        r.setPaciente(paciente(1, true));
        r.setTratamiento(tratamiento(2, "Ortodoncia - control mensual"));
        SystemUser d = new SystemUser();
        d.setId(3);
        d.setFullName("Dr. Martín Salas");
        r.setDentista(d);
        r.setFechaRealizacion(OffsetDateTime.now().minusDays(7));
        r.setDetalleProcedimiento("Cambio de arcos y ligas");
        r.setCosto(new BigDecimal("450.00"));
        return r;
    }

    @Test
    void elHistorialDevuelveLasCitasYLosTratamientosDelPaciente() {
        when(pacientes.findById(1)).thenReturn(Optional.of(paciente(1, true)));
        when(citas.historialDePaciente(1)).thenReturn(List.of(cita(EstadoCita.ATENDIDA)));
        when(realizados.findByPacienteIdOrderByFechaRealizacionDesc(1)).thenReturn(List.of(realizado()));

        HistorialResponse historial = service.dePaciente(1);

        assertThat(historial.paciente()).isEqualTo("Rocío Belén Aguilar Sánchez");
        assertThat(historial.citas()).hasSize(1);
        assertThat(historial.citas().get(0).estado()).isEqualTo("ATENDIDA");
        assertThat(historial.citas().get(0).tratamiento()).isEqualTo("Consulta y diagnóstico");
        assertThat(historial.tratamientos()).hasSize(1);
        assertThat(historial.tratamientos().get(0).detalleProcedimiento())
            .isEqualTo("Cambio de arcos y ligas");
        assertThat(historial.tratamientos().get(0).costo()).isEqualByComparingTo("450.00");
    }

    @Test
    void elHistorialDeUnPacienteDadoDeBajaSigueDisponible() {
        when(pacientes.findById(6)).thenReturn(Optional.of(paciente(6, false)));
        when(citas.historialDePaciente(6)).thenReturn(List.of());
        when(realizados.findByPacienteIdOrderByFechaRealizacionDesc(6)).thenReturn(List.of());

        HistorialResponse historial = service.dePaciente(6);

        assertThat(historial.idPaciente()).isEqualTo(6);
        assertThat(historial.citas()).isEmpty();
        assertThat(historial.tratamientos()).isEmpty();
    }

    @Test
    void elHistorialDeUnPacienteInexistenteDevuelve404() {
        when(pacientes.findById(99)).thenReturn(Optional.empty());

        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.dePaciente(99), ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
