package com.clinica.dental.realizado;

import com.clinica.dental.cita.Cita;
import com.clinica.dental.cita.CitaRepository;
import com.clinica.dental.cita.CitaService;
import com.clinica.dental.cita.EstadoCita;
import com.clinica.dental.paciente.Paciente;
import com.clinica.dental.paciente.PacienteRepository;
import com.clinica.dental.realizado.dto.RealizadoRequest;
import com.clinica.dental.realizado.dto.RealizadoResponse;
import com.clinica.dental.seguridad.SystemUser;
import com.clinica.dental.seguridad.SystemUserRepository;
import com.clinica.dental.shared.ClinicaBusinessException;
import com.clinica.dental.tratamiento.Tratamiento;
import com.clinica.dental.tratamiento.TratamientoRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Registro de tratamientos realizados (S8). */
@ExtendWith(MockitoExtension.class)
class TratamientoRealizadoServiceTest {

    @Mock private TratamientoRealizadoRepository realizados;
    @Mock private PacienteRepository pacientes;
    @Mock private TratamientoRepository tratamientos;
    @Mock private SystemUserRepository usuarios;
    @Mock private CitaRepository citas;
    @Mock private CitaService citaService;

    @InjectMocks private TratamientoRealizadoService service;

    private static Paciente paciente(int id) {
        Paciente p = new Paciente();
        p.setId(id);
        p.setNombres("Carlos Alberto");
        p.setApellidos("Quispe Flores");
        p.setActive(true);
        return p;
    }

    private static Tratamiento tratamiento(int id, String precio) {
        Tratamiento t = new Tratamiento();
        t.setId(id);
        t.setNombre("Obturación de resina");
        t.setPrecio(new BigDecimal(precio));
        t.setDuracionMinutos(60);
        t.setActive(true);
        return t;
    }

    private static SystemUser dentista(int id) {
        SystemUser u = new SystemUser();
        u.setId(id);
        u.setFullName("Dr. Martín Salas");
        u.setActive(true);
        return u;
    }

    private static Cita cita(int id, int idPaciente) {
        Cita c = new Cita();
        c.setId(id);
        c.setPaciente(paciente(idPaciente));
        c.setTratamiento(tratamiento(2, "400.00"));
        c.setDentista(dentista(3));
        EstadoCita estado = new EstadoCita();
        estado.setCode(EstadoCita.PROGRAMADA);
        c.setEstado(estado);
        c.setFechaHoraInicio(OffsetDateTime.now().minusDays(1));
        c.setDuracionMinutos(60);
        return c;
    }

    private static RealizadoRequest pedido(Integer idCita) {
        return new RealizadoRequest(1, 2, 3, OffsetDateTime.now().minusHours(2),
            "Obturación de caries en pieza 26", null, idCita);
    }

    @Test
    void registrarCongelaElCostoVigenteDelCatalogo() {
        when(pacientes.findByIdAndActiveTrue(1)).thenReturn(Optional.of(paciente(1)));
        when(tratamientos.findByIdAndActiveTrue(2)).thenReturn(Optional.of(tratamiento(2, "400.00")));
        when(usuarios.findById(3)).thenReturn(Optional.of(dentista(3)));
        when(realizados.save(any(TratamientoRealizado.class))).thenAnswer(inv -> {
            TratamientoRealizado r = inv.getArgument(0);
            r.setId(70);
            return r;
        });

        RealizadoResponse registrado = service.registrar(pedido(null));

        assertThat(registrado.id()).isEqualTo(70);
        assertThat(registrado.costo()).isEqualByComparingTo("400.00");
        assertThat(registrado.detalleProcedimiento()).isEqualTo("Obturación de caries en pieza 26");
        verifyNoInteractions(citaService);
    }

    @Test
    void registrarConUnaCitaDeOtroPacienteDevuelve409YNoGuarda() {
        when(pacientes.findByIdAndActiveTrue(1)).thenReturn(Optional.of(paciente(1)));
        when(tratamientos.findByIdAndActiveTrue(2)).thenReturn(Optional.of(tratamiento(2, "400.00")));
        when(usuarios.findById(3)).thenReturn(Optional.of(dentista(3)));
        when(citas.findById(20)).thenReturn(Optional.of(cita(20, 99)));

        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.registrar(pedido(20)), ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getMessage()).contains("no pertenece al paciente");
        verify(realizados, never()).save(any(TratamientoRealizado.class));
        verifyNoInteractions(citaService);
    }

    @Test
    void registrarConSuPropiaCitaDejaLaCitaAtendida() {
        when(pacientes.findByIdAndActiveTrue(1)).thenReturn(Optional.of(paciente(1)));
        when(tratamientos.findByIdAndActiveTrue(2)).thenReturn(Optional.of(tratamiento(2, "400.00")));
        when(usuarios.findById(3)).thenReturn(Optional.of(dentista(3)));
        when(citas.findById(20)).thenReturn(Optional.of(cita(20, 1)));
        when(realizados.save(any(TratamientoRealizado.class))).thenAnswer(inv -> inv.getArgument(0));

        RealizadoResponse registrado = service.registrar(pedido(20));

        assertThat(registrado.idCita()).isEqualTo(20);
        verify(citaService).marcarAtendida(20, 1);
    }

    @Test
    void registrarConFechaFuturaDevuelve422YNoGuarda() {
        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.registrar(new RealizadoRequest(1, 2, 3, OffsetDateTime.now().plusHours(3),
                "Procedimiento", null, null)),
            ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        verify(realizados, never()).save(any(TratamientoRealizado.class));
    }

    @Test
    void registrarSinDetalleDelProcedimientoDevuelve400YNoGuarda() {
        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.registrar(new RealizadoRequest(1, 2, 3, OffsetDateTime.now().minusHours(1),
                "   ", null, null)),
            ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(realizados, never()).save(any(TratamientoRealizado.class));
    }

    @Test
    void registrarParaUnPacienteDadoDeBajaDevuelve404() {
        when(pacientes.findByIdAndActiveTrue(1)).thenReturn(Optional.empty());

        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.registrar(pedido(null)), ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(realizados, never()).save(any(TratamientoRealizado.class));
    }

    @Test
    void listarPorPacienteConsultaSoloEsePaciente() {
        TratamientoRealizado r = new TratamientoRealizado();
        r.setId(5);
        r.setPaciente(paciente(1));
        r.setTratamiento(tratamiento(2, "400.00"));
        r.setDentista(dentista(3));
        r.setFechaRealizacion(OffsetDateTime.now().minusDays(2));
        r.setDetalleProcedimiento("Control");
        r.setCosto(new BigDecimal("400.00"));
        when(realizados.findByPacienteIdOrderByFechaRealizacionDesc(1)).thenReturn(List.of(r));

        assertThat(service.listar(1)).hasSize(1);
        verify(realizados, never()).findAllByOrderByFechaRealizacionDesc();
    }

    @Test
    void listarSinFiltroDevuelveTodosLosRegistros() {
        when(realizados.findAllByOrderByFechaRealizacionDesc()).thenReturn(List.of());

        assertThat(service.listar(null)).isEmpty();
    }
}
