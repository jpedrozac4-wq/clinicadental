package com.clinica.dental.tratamiento;

import com.clinica.dental.shared.ClinicaBusinessException;
import com.clinica.dental.tratamiento.dto.TratamientoRequest;
import com.clinica.dental.tratamiento.dto.TratamientoResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Reglas del catalogo de tratamientos (S7). */
@ExtendWith(MockitoExtension.class)
class TratamientoServiceTest {

    @Mock
    private TratamientoRepository repository;

    @InjectMocks
    private TratamientoService service;

    private static TratamientoRequest ficha(String codigo, String precio) {
        return new TratamientoRequest(codigo, "Limpieza dental",
            "Destartraje con ultrasonido y pulido", new BigDecimal(precio), 45);
    }

    private static Tratamiento persistido(int id) {
        Tratamiento t = new Tratamiento();
        t.setId(id);
        t.setCodigo("ODO-002");
        t.setNombre("Limpieza dental");
        t.setPrecio(new BigDecimal("350.00"));
        t.setDuracionMinutos(45);
        t.setActive(true);
        return t;
    }

    @Test
    void crearGuardaElTratamientoVigenteConSuPrecioYDuracion() {
        when(repository.save(any(Tratamiento.class))).thenAnswer(inv -> {
            Tratamiento t = inv.getArgument(0);
            t.setId(11);
            return t;
        });

        TratamientoResponse creado = service.crear(ficha("odo-002", "350.00"));

        assertThat(creado.id()).isEqualTo(11);
        assertThat(creado.codigo()).isEqualTo("ODO-002");
        assertThat(creado.precio()).isEqualByComparingTo("350.00");
        assertThat(creado.duracionMinutos()).isEqualTo(45);

        ArgumentCaptor<Tratamiento> guardado = ArgumentCaptor.forClass(Tratamiento.class);
        verify(repository).save(guardado.capture());
        assertThat(guardado.getValue().getActive()).isTrue();
    }

    @Test
    void crearConCodigoDuplicadoDevuelve409YNoGuarda() {
        when(repository.existsByCodigoIgnoreCase("ODO-002")).thenReturn(true);

        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.crear(ficha("ODO-002", "350.00")), ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getMessage()).contains("ODO-002");
        verify(repository, never()).save(any(Tratamiento.class));
    }

    @Test
    void crearConPrecioNoPositivoDevuelve400YNoGuarda() {
        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.crear(ficha("ODO-003", "0.00")), ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getMessage()).contains("precio");
        verify(repository, never()).save(any(Tratamiento.class));
    }

    @Test
    void crearConDuracionInvalidaDevuelve400YNoGuarda() {
        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.crear(new TratamientoRequest("ODO-003", "Obturacion", null,
                new BigDecimal("400.00"), 0)), ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(repository, never()).save(any(Tratamiento.class));
    }

    @Test
    void actualizarUnTratamientoInexistenteDevuelve404() {
        when(repository.findByIdAndActiveTrue(99)).thenReturn(Optional.empty());

        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.actualizar(99, ficha("ODO-002", "350.00")), ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void actualizarConElCodigoDeOtroTratamientoDevuelve409() {
        when(repository.findByIdAndActiveTrue(5)).thenReturn(Optional.of(persistido(5)));
        when(repository.existsByCodigoIgnoreCaseAndIdNot("ODO-009", 5)).thenReturn(true);

        ClinicaBusinessException ex = catchThrowableOfType(
            () -> service.actualizar(5, ficha("ODO-009", "350.00")), ClinicaBusinessException.class);

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verify(repository, never()).save(any(Tratamiento.class));
    }

    @Test
    void actualizarModificaPrecioYDuracionDelCatalogo() {
        when(repository.findByIdAndActiveTrue(5)).thenReturn(Optional.of(persistido(5)));
        when(repository.save(any(Tratamiento.class))).thenAnswer(inv -> inv.getArgument(0));

        TratamientoResponse actualizado = service.actualizar(5,
            new TratamientoRequest("ODO-002", "Limpieza dental profunda",
                "Incluye curetaje", new BigDecimal("480.00"), 60));

        assertThat(actualizado.nombre()).isEqualTo("Limpieza dental profunda");
        assertThat(actualizado.precio()).isEqualByComparingTo("480.00");
        assertThat(actualizado.duracionMinutos()).isEqualTo(60);
    }

    @Test
    void listarDevuelveLosTratamientosVigentes() {
        when(repository.findByActiveTrueOrderByNombreAsc()).thenReturn(List.of(persistido(1), persistido(2)));

        assertThat(service.listar()).hasSize(2);
    }
}
