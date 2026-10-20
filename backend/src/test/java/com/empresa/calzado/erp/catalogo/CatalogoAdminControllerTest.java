package com.empresa.calzado.erp.catalogo;

import com.empresa.calzado.erp.catalogo.dto.ColorVarianteCreateRequest;
import com.empresa.calzado.erp.catalogo.dto.ModeloAdminDetalle;
import com.empresa.calzado.erp.catalogo.dto.ModeloAdminResumen;
import com.empresa.calzado.erp.catalogo.dto.ModeloColorAdmin;
import com.empresa.calzado.erp.catalogo.dto.ModeloCreateRequest;
import com.empresa.calzado.erp.catalogo.dto.ModeloUpdateRequest;
import com.empresa.calzado.erp.catalogo.dto.OutletUpdateRequest;
import com.empresa.calzado.erp.catalogo.dto.SkuActiveUpdateRequest;
import com.empresa.calzado.erp.catalogo.dto.SkuAdmin;
import com.empresa.calzado.erp.catalogo.dto.SkuCreateRequest;
import com.empresa.calzado.erp.catalogo.dto.SkuUpdateRequest;
import com.empresa.calzado.erp.catalogo.dto.TallaActiveUpdateRequest;
import com.empresa.calzado.erp.catalogo.dto.TallaAdmin;
import com.empresa.calzado.erp.catalogo.dto.TallaCreateRequest;
import com.empresa.calzado.erp.catalogo.dto.TallaUpdateRequest;
import com.empresa.calzado.erp.catalogo.dto.VisibilidadWebUpdateRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract tests for the admin catalogue controller using MockMvc standalone.
 *
 * <p>The service is mocked, so these tests pin the HTTP surface: routes, query parameters, JSON
 * field names and the business-error envelope.
 */
@ExtendWith(MockitoExtension.class)
class CatalogoAdminControllerTest {

    private static final String BASE = "/api/catalogo/admin";

    @Mock private CatalogoAdminService service;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new CatalogoAdminController(service)).build();
    }

    @Test
    void listsEveryModelIncludingHiddenOnes() throws Exception {
        ModeloAdminResumen conStock = new ModeloAdminResumen(
            1, 1, "Tenis", 1, "MarcaX", 1, "Zapato Urbano", "UNISEX", "2025",
            new BigDecimal("100.00"), new BigDecimal("100.00"), false, BigDecimal.ZERO, true);
        ModeloAdminResumen oculto = new ModeloAdminResumen(
            2, 1, "Tenis", 1, "MarcaX", 2, "Zapato Retirado", "UNISEX", "2025",
            new BigDecimal("100.00"), new BigDecimal("100.00"), false, BigDecimal.ZERO, false);
        when(service.listarModelos(null, null, null, null, null)).thenReturn(List.of(conStock, oculto));

        mockMvc.perform(get(BASE + "/modelos"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[1].nombre").value("Zapato Retirado"))
            .andExpect(jsonPath("$[1].esVisibleWeb").value(false));
    }

    @Test
    void forwardsAdminFiltersAsQueryParameters() throws Exception {
        when(service.listarModelos("zap", 2, 3, true, false)).thenReturn(List.of());

        mockMvc.perform(get(BASE + "/modelos")
                .param("q", "zap")
                .param("marcaId", "2")
                .param("tipoId", "3")
                .param("outlet", "true")
                .param("visibleWeb", "false"))
            .andExpect(status().isOk());

        verify(service).listarModelos("zap", 2, 3, true, false);
    }

    @Test
    void returnsModelDetailWithColoursAndSkus() throws Exception {
        ModeloAdminDetalle detalle = new ModeloAdminDetalle(
            1, 1, "Tenis", 1, "MarcaX", 1, "Zapato Urbano", "Cuero", "Textil", "Goma", "Cordones",
            "UNISEX", "2025", new BigDecimal("100.00"), new BigDecimal("100.00"), false, BigDecimal.ZERO, true,
            List.of(new ModeloColorAdmin(10, 3, "Negro", "#000000", "/storage/negro.jpg", "[]")),
            List.of(new SkuAdmin(20, "T01001-40", new BigDecimal("40.0"), new BigDecimal("25.0"),
                new BigDecimal("100.00"), true, 10, 800, "7501234567890")));
        when(service.obtenerModelo(1)).thenReturn(detalle);

        mockMvc.perform(get(BASE + "/modelos/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.correlativo").value(1))
            .andExpect(jsonPath("$.esVisibleWeb").value(true))
            .andExpect(jsonPath("$.colores[0].fotoPrincipalUrl").value("/storage/negro.jpg"))
            .andExpect(jsonPath("$.skus[0].sku").value("T01001-40"))
            .andExpect(jsonPath("$.skus[0].tallaEur").value(40.0))
            .andExpect(jsonPath("$.skus[0].idModeloColor").value(10))
            .andExpect(jsonPath("$.skus[0].pesoGramos").value(800))
            .andExpect(jsonPath("$.skus[0].codigoBarrasEan").value("7501234567890"));
    }

    @Test
    void listsSkusOfOneModel() throws Exception {
        when(service.listarSkus(1)).thenReturn(List.of(
            new SkuAdmin(20, "T01001-40", new BigDecimal("40.0"), new BigDecimal("25.0"),
                new BigDecimal("100.00"), true, 10, 800, null)));

        mockMvc.perform(get(BASE + "/skus").param("modeloId", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].sku").value("T01001-40"))
            .andExpect(jsonPath("$[0].idModeloColor").value(10))
            .andExpect(jsonPath("$[0].pesoGramos").value(800))
            .andExpect(jsonPath("$[0].active").value(true));

        verify(service).listarSkus(1);
    }

    @Test
    void createsAModel() throws Exception {
        ModeloCreateRequest request = new ModeloCreateRequest(
            1, 1, null, "Zapato Urbano", "Cuero", "Textil", "Goma", "Cordones",
            "UNISEX", "2025", new BigDecimal("100.00"), false, BigDecimal.ZERO, true);
        when(service.crearModelo(any())).thenReturn(detalleMinimo());

        mockMvc.perform(post(BASE + "/modelos")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1));

        verify(service).crearModelo(any(ModeloCreateRequest.class));
    }

    @Test
    void updatesAModel() throws Exception {
        ModeloUpdateRequest request = new ModeloUpdateRequest(
            null, null, null, "Zapato Editado", null, null, null, null, null, null,
            new BigDecimal("150.00"), null, null, null);
        when(service.actualizarModelo(eq(1), any())).thenReturn(detalleMinimo());

        mockMvc.perform(put(BASE + "/modelos/1")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());

        verify(service).actualizarModelo(eq(1), any(ModeloUpdateRequest.class));
    }

    @Test
    void updatesOutletFlag() throws Exception {
        when(service.actualizarOutlet(eq(1), any())).thenReturn(detalleMinimo());

        mockMvc.perform(patch(BASE + "/modelos/1/outlet")
                .contentType("application/json")
                .content("{\"esOutlet\":true,\"porcentajeDescuento\":15}"))
            .andExpect(status().isOk());

        verify(service).actualizarOutlet(eq(1), any(OutletUpdateRequest.class));
    }

    @Test
    void updatesWebVisibility() throws Exception {
        when(service.actualizarVisibilidad(eq(1), any())).thenReturn(detalleMinimo());

        mockMvc.perform(patch(BASE + "/modelos/1/visibilidad-web")
                .contentType("application/json")
                .content("{\"esVisibleWeb\":false}"))
            .andExpect(status().isOk());

        verify(service).actualizarVisibilidad(eq(1), any(VisibilidadWebUpdateRequest.class));
    }

    @Test
    void mapsBusinessFailuresToAReadableErrorEnvelope() throws Exception {
        when(service.obtenerModelo(99))
            .thenThrow(new CatalogoBusinessException(HttpStatus.NOT_FOUND, "Modelo no encontrado: 99"));

        mockMvc.perform(get(BASE + "/modelos/99"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value("ERROR"))
            .andExpect(jsonPath("$.message").value("Modelo no encontrado: 99"));
    }

    @Test
    void createsAnSku() throws Exception {
        SkuCreateRequest request = new SkuCreateRequest(10, 2, "7501000001391", null);
        when(service.crearSku(any())).thenReturn(skuAdmin());

        mockMvc.perform(post(BASE + "/skus")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(20))
            .andExpect(jsonPath("$.sku").value("TV001-39"))
            .andExpect(jsonPath("$.idModeloColor").value(10))
            .andExpect(jsonPath("$.pesoGramos").value(900))
            .andExpect(jsonPath("$.active").value(true))
            .andExpect(jsonPath("$.codigoBarrasEan").value("7501000001391"));

        verify(service).crearSku(any(SkuCreateRequest.class));
    }

    @Test
    void updatesAnSku() throws Exception {
        when(service.actualizarSku(eq(20), any())).thenReturn(skuAdmin());

        mockMvc.perform(put(BASE + "/skus/20")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(new SkuUpdateRequest("7501000001391", 900))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sku").value("TV001-39"));

        verify(service).actualizarSku(eq(20), any(SkuUpdateRequest.class));
    }

    @Test
    void togglesSkuActive() throws Exception {
        when(service.actualizarSkuActivo(eq(20), any())).thenReturn(skuAdmin());

        mockMvc.perform(patch(BASE + "/skus/20/active")
                .contentType("application/json")
                .content("{\"active\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(true));

        verify(service).actualizarSkuActivo(eq(20), any(SkuActiveUpdateRequest.class));
    }

    @Test
    void mapsSkuBusinessFailuresToTheErrorEnvelope() throws Exception {
        when(service.crearSku(any()))
            .thenThrow(new CatalogoBusinessException("Ya existe un SKU para esa variante de color y talla"));

        mockMvc.perform(post(BASE + "/skus")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(new SkuCreateRequest(10, 2, null, null))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("ERROR"))
            .andExpect(jsonPath("$.message").value("Ya existe un SKU para esa variante de color y talla"));
    }

    @Test
    void mapsMissingActiveFlagToTheErrorEnvelope() throws Exception {
        when(service.actualizarSkuActivo(eq(20), any()))
            .thenThrow(new CatalogoBusinessException("El campo active es obligatorio"));

        mockMvc.perform(patch(BASE + "/skus/20/active")
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("ERROR"))
            .andExpect(jsonPath("$.message").value("El campo active es obligatorio"));
    }

    @Test
    void mapsMissingRequestBodyToTheErrorEnvelope() throws Exception {
        when(service.actualizarSkuActivo(eq(20), isNull()))
            .thenThrow(new CatalogoBusinessException("El cuerpo de la solicitud es obligatorio"));

        mockMvc.perform(patch(BASE + "/skus/20/active"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("ERROR"))
            .andExpect(jsonPath("$.message").value("El cuerpo de la solicitud es obligatorio"));

        verify(service).actualizarSkuActivo(eq(20), isNull());
    }

    @Test
    void addsAColourVariant() throws Exception {
        when(service.agregarColor(eq(1), any()))
            .thenReturn(new ModeloColorAdmin(10, 3, "Negro", "#000000", null, null));

        mockMvc.perform(post(BASE + "/modelos/1/colores")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(new ColorVarianteCreateRequest(3))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.colorId").value(3))
            .andExpect(jsonPath("$.color").value("Negro"));

        verify(service).agregarColor(eq(1), any(ColorVarianteCreateRequest.class));
    }

    @Test
    void uploadsAColourVariantImageAsMultipart() throws Exception {
        when(service.subirImagenColor(eq(1), eq(3), any()))
            .thenReturn(new ModeloColorAdmin(10, 3, "Negro", "#000000", "/storage/calzado/foto.jpg", null));
        MockMultipartFile imagen = new MockMultipartFile("imagen", "foto.jpg", "image/jpeg", "x".getBytes());

        mockMvc.perform(multipart(BASE + "/modelos/1/colores/3/imagen").file(imagen))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fotoPrincipalUrl").value("/storage/calzado/foto.jpg"));

        verify(service).subirImagenColor(eq(1), eq(3), any(MultipartFile.class));
    }

    @Test
    void deletesAColourVariantImage() throws Exception {
        when(service.eliminarImagenColor(1, 3))
            .thenReturn(new ModeloColorAdmin(10, 3, "Negro", "#000000", null, null));

        mockMvc.perform(delete(BASE + "/modelos/1/colores/3/imagen"))
            .andExpect(status().isOk());

        verify(service).eliminarImagenColor(1, 3);
    }

    @Test
    void mapsEmptyColourImageUploadToTheErrorEnvelope() throws Exception {
        when(service.subirImagenColor(eq(1), eq(3), any()))
            .thenThrow(new CatalogoBusinessException("El archivo de imagen es obligatorio"));

        mockMvc.perform(multipart(BASE + "/modelos/1/colores/3/imagen"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("ERROR"))
            .andExpect(jsonPath("$.message").value("El archivo de imagen es obligatorio"));
    }

    @Test
    void mapsDuplicateColourVariantToTheErrorEnvelope() throws Exception {
        when(service.agregarColor(eq(1), any()))
            .thenThrow(new CatalogoBusinessException("La variante de color ya existe para ese modelo"));

        mockMvc.perform(post(BASE + "/modelos/1/colores")
                .contentType("application/json")
                .content("{\"idColor\":3}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("ERROR"))
            .andExpect(jsonPath("$.message").value("La variante de color ya existe para ese modelo"));
    }

    @Test
    void listsEverySizeIncludingInactiveOnes() throws Exception {
        when(service.listarTallas()).thenReturn(List.of(
            new TallaAdmin(1, new BigDecimal("39.0"), new BigDecimal("6.5"), new BigDecimal("8.5"),
                new BigDecimal("6.0"), new BigDecimal("24.5"), true),
            new TallaAdmin(2, new BigDecimal("40.0"), null, null, null,
                new BigDecimal("25.0"), false)));

        mockMvc.perform(get(BASE + "/tallas"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[1].tallaEur").value(40.0))
            .andExpect(jsonPath("$[1].active").value(false));
    }

    @Test
    void createsATalla() throws Exception {
        TallaCreateRequest request = new TallaCreateRequest(
            new BigDecimal("39.0"), new BigDecimal("6.5"), new BigDecimal("8.5"),
            new BigDecimal("6.0"), new BigDecimal("24.5"));
        when(service.crearTalla(any())).thenReturn(tallaAdmin());

        mockMvc.perform(post(BASE + "/tallas")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(5))
            .andExpect(jsonPath("$.tallaEur").value(39.0))
            .andExpect(jsonPath("$.active").value(true));

        verify(service).crearTalla(any(TallaCreateRequest.class));
    }

    @Test
    void updatesATalla() throws Exception {
        when(service.actualizarTalla(eq(5), any())).thenReturn(tallaAdmin());

        mockMvc.perform(put(BASE + "/tallas/5")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(
                    new TallaUpdateRequest(null, null, null, new BigDecimal("25.5")))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(5));

        verify(service).actualizarTalla(eq(5), any(TallaUpdateRequest.class));
    }

    @Test
    void togglesTallaActive() throws Exception {
        when(service.actualizarTallaActivo(eq(5), any())).thenReturn(tallaAdmin());

        mockMvc.perform(patch(BASE + "/tallas/5/active")
                .contentType("application/json")
                .content("{\"active\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(true));

        verify(service).actualizarTallaActivo(eq(5), any(TallaActiveUpdateRequest.class));
    }

    @Test
    void mapsDuplicateTallaToTheErrorEnvelope() throws Exception {
        when(service.crearTalla(any()))
            .thenThrow(new CatalogoBusinessException("Ya existe una talla con tallaEur 39.0"));

        mockMvc.perform(post(BASE + "/tallas")
                .contentType("application/json")
                .content("{\"tallaEur\":39.0,\"tallaCmMx\":24.5}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("ERROR"))
            .andExpect(jsonPath("$.message").value("Ya existe una talla con tallaEur 39.0"));
    }

    @Test
    void mapsUnknownTallaToNotFound() throws Exception {
        when(service.actualizarTalla(eq(99), any()))
            .thenThrow(new CatalogoBusinessException(HttpStatus.NOT_FOUND, "Talla no encontrada: 99"));

        mockMvc.perform(put(BASE + "/tallas/99")
                .contentType("application/json")
                .content("{\"tallaCmMx\":25.0}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value("ERROR"))
            .andExpect(jsonPath("$.message").value("Talla no encontrada: 99"));
    }

    @Test
    void mapsMissingTallaEurToTheErrorEnvelope() throws Exception {
        when(service.crearTalla(any()))
            .thenThrow(new CatalogoBusinessException("El parametro tallaEur es obligatorio"));

        mockMvc.perform(post(BASE + "/tallas")
                .contentType("application/json")
                .content("{\"tallaCmMx\":24.5}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("El parametro tallaEur es obligatorio"));
    }

    @Test
    void mapsMissingActiveFlagForTallaToTheErrorEnvelope() throws Exception {
        when(service.actualizarTallaActivo(eq(5), any()))
            .thenThrow(new CatalogoBusinessException("El parametro active es obligatorio"));

        mockMvc.perform(patch(BASE + "/tallas/5/active")
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("El parametro active es obligatorio"));
    }

    private static TallaAdmin tallaAdmin() {
        return new TallaAdmin(5, new BigDecimal("39.0"), new BigDecimal("6.5"), new BigDecimal("8.5"),
            new BigDecimal("6.0"), new BigDecimal("24.5"), true);
    }

    private static ModeloAdminDetalle detalleMinimo() {
        return new ModeloAdminDetalle(
            1, 1, "Tenis", 1, "MarcaX", 1, "Zapato Urbano", "Cuero", "Textil", "Goma", "Cordones",
            "UNISEX", "2025", new BigDecimal("100.00"), new BigDecimal("100.00"), false, BigDecimal.ZERO, true,
            List.of(), List.of());
    }

    private static SkuAdmin skuAdmin() {
        return new SkuAdmin(20, "TV001-39", new BigDecimal("39.0"), new BigDecimal("24.5"),
            new BigDecimal("65.00"), true, 10, 900, "7501000001391");
    }
}
