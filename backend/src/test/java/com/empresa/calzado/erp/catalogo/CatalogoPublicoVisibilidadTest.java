package com.empresa.calzado.erp.catalogo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.repository.Query;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Guards the public catalogue boundary: models with {@code esVisibleWeb = false} are the logical
 * removal and must never leak through the anonymous {@code GET /api/catalogo} endpoint.
 *
 * <p>Because the project has no in-memory database, the query predicate itself is asserted against
 * the shipped {@link ModeloRepository#filtrarModelos} JPQL, and the controller response is asserted
 * with the repository mocked to honour that predicate.
 */
@ExtendWith(MockitoExtension.class)
class CatalogoPublicoVisibilidadTest {

    @Mock private ModeloRepository modeloRepository;
    @Mock private CalzadoSkuRepository skuRepository;
    @Mock private TipoCalzadoRepository tipoCalzadoRepository;
    @Mock private MarcaRepository marcaRepository;
    @Mock private ColorRepository colorRepository;
    @Mock private TallaEquivalenciaRepository tallaRepository;
    @Mock private CatalogoService catalogoService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        CatalogoController controller = new CatalogoController(
            modeloRepository, skuRepository, tipoCalzadoRepository, marcaRepository,
            colorRepository, tallaRepository, catalogoService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void publicListNeverReturnsHiddenModels() throws Exception {
        Modelo visible = modelo(1, true);
        Modelo hidden = modelo(2, false);
        // Emulates the repository predicate "m.esVisibleWeb = true" without a database.
        when(modeloRepository.filtrarModelos(any(), any(), any(), any())).thenAnswer(invocation ->
            List.of(visible, hidden).stream()
                .filter(modelo -> Boolean.TRUE.equals(modelo.getEsVisibleWeb()))
                .toList());

        MvcResult result = mockMvc.perform(get("/api/catalogo"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(1))
            .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("\"id\":2");
    }

    @Test
    void publicRepositoryQueryKeepsTheVisibilityPredicate() throws Exception {
        Method method = ModeloRepository.class.getMethod(
            "filtrarModelos", Integer.class, Integer.class, String.class, Boolean.class);
        Query query = method.getAnnotation(Query.class);

        assertThat(query).isNotNull();
        assertThat(query.value()).contains("m.esVisibleWeb = true");
    }

    private static Modelo modelo(Integer id, boolean visible) {
        TipoCalzado tipo = new TipoCalzado();
        tipo.setId(1);
        tipo.setCodigoLetra("T");
        tipo.setNombre("Tenis");

        Marca marca = new Marca();
        marca.setId(1);
        marca.setCodigoLetra("M");
        marca.setNombre("MarcaX");

        Modelo modelo = new Modelo();
        modelo.setId(id);
        modelo.setTipoCalzado(tipo);
        modelo.setMarca(marca);
        modelo.setCorrelativo(id);
        modelo.setNombre("Modelo " + id);
        modelo.setGenero("UNISEX");
        modelo.setTemporada("2025");
        modelo.setPrecioListaBase(new BigDecimal("100.00"));
        modelo.setEsOutlet(false);
        modelo.setPorcentajeDescuento(BigDecimal.ZERO);
        modelo.setEsVisibleWeb(visible);
        return modelo;
    }
}
