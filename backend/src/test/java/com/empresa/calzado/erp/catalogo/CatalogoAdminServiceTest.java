package com.empresa.calzado.erp.catalogo;

import com.empresa.calzado.erp.catalogo.dto.ColorVarianteCreateRequest;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the write/read business rules of the catalogue administration service.
 *
 * <p>Repository access is mocked: the project intentionally has no H2 or Testcontainers, so these
 * tests target the service logic and the entity/DTO contract, not SQL execution.
 */
@ExtendWith(MockitoExtension.class)
class CatalogoAdminServiceTest {

    @Mock private ModeloRepository modeloRepository;
    @Mock private ModeloColorRepository modeloColorRepository;
    @Mock private CalzadoSkuRepository skuRepository;
    @Mock private TipoCalzadoRepository tipoCalzadoRepository;
    @Mock private MarcaRepository marcaRepository;
    @Mock private TallaEquivalenciaRepository tallaRepository;
    @Mock private ColorRepository colorRepository;
    @Mock private ImagenCalzadoStorage imagenStorage;

    @InjectMocks private CatalogoAdminService service;

    @Test
    void rejectsDiscountAboveOneHundred() {
        ModeloCreateRequest request = crearRequest(null, new BigDecimal("100.00"), false, new BigDecimal("101"));

        assertThatThrownBy(() -> service.crearModelo(request))
            .isInstanceOf(CatalogoBusinessException.class)
            .hasMessageContaining("porcentajeDescuento");

        verify(modeloRepository, never()).save(any());
    }

    @Test
    void rejectsNegativeDiscount() {
        ModeloCreateRequest request = crearRequest(null, new BigDecimal("100.00"), false, new BigDecimal("-1"));

        assertThatThrownBy(() -> service.crearModelo(request))
            .isInstanceOf(CatalogoBusinessException.class)
            .hasMessageContaining("porcentajeDescuento");
    }

    @Test
    void rejectsExactlyOneHundredDiscountBecauseItWouldZeroThePrice() {
        ModeloCreateRequest request = crearRequest(null, new BigDecimal("100.00"), true, new BigDecimal("100"));

        assertThatThrownBy(() -> service.crearModelo(request))
            .isInstanceOf(CatalogoBusinessException.class)
            .hasMessageContaining("porcentajeDescuento");

        verify(modeloRepository, never()).save(any());
    }

    @Test
    void rejectsIdentityChangeWhenModelAlreadyHasSkus() {
        // An SKU code embeds [tipo][marca][correlativo], so changing those would desynchronise
        // every code already printed or scanned at the POS.
        Modelo existente = modelo(5, 1, true);
        when(modeloRepository.findById(5)).thenReturn(Optional.of(existente));
        when(modeloRepository.existsByTipoCalzadoIdAndMarcaIdAndCorrelativoAndIdNot(1, 1, 2, 5))
            .thenReturn(false);
        when(skuRepository.findByModeloColorModeloId(5)).thenReturn(List.of(sku("TM001-40", "100.00")));

        ModeloUpdateRequest conNuevoCorrelativo = new ModeloUpdateRequest(
            null, null, 2, null, null, null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.actualizarModelo(5, conNuevoCorrelativo))
            .isInstanceOf(CatalogoBusinessException.class)
            .hasMessageContaining("correlativo");

        verify(modeloRepository, never()).save(any());
    }

    @Test
    void allowsIdentityChangeWhileModelIsStillADraft() {
        Modelo borrador = modelo(5, 1, true);
        when(modeloRepository.findById(5)).thenReturn(Optional.of(borrador));
        when(modeloRepository.existsByTipoCalzadoIdAndMarcaIdAndCorrelativoAndIdNot(1, 1, 2, 5))
            .thenReturn(false);
        when(modeloRepository.save(any(Modelo.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(modeloColorRepository.findByModeloIdOrderByIdAsc(5)).thenReturn(List.of());
        when(skuRepository.findByModeloColorModeloId(5)).thenReturn(List.of());

        ModeloUpdateRequest conNuevoCorrelativo = new ModeloUpdateRequest(
            null, null, 2, null, null, null, null, null, null, null, null, null, null, null);

        service.actualizarModelo(5, conNuevoCorrelativo);

        ArgumentCaptor<Modelo> captor = ArgumentCaptor.forClass(Modelo.class);
        verify(modeloRepository).save(captor.capture());
        assertThat(captor.getValue().getCorrelativo()).isEqualTo(2);
    }

    @Test
    void positiveDiscountImpliesOutlet() {
        stubNuevoCorrelativo();
        when(modeloRepository.save(any(Modelo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.crearModelo(crearRequest(null, new BigDecimal("100.00"), false, new BigDecimal("20")));

        ArgumentCaptor<Modelo> captor = ArgumentCaptor.forClass(Modelo.class);
        verify(modeloRepository).save(captor.capture());
        assertThat(captor.getValue().getEsOutlet()).isTrue();
        assertThat(captor.getValue().getPorcentajeDescuento()).isEqualByComparingTo("20");
    }

    @Test
    void changingBasePriceRecomputesEverySkuPrice() {
        Modelo existente = modelo(5, 1, true);
        when(modeloRepository.findById(5)).thenReturn(Optional.of(existente));
        when(modeloRepository.existsByTipoCalzadoIdAndMarcaIdAndCorrelativoAndIdNot(1, 1, 1, 5)).thenReturn(false);
        when(modeloRepository.save(any(Modelo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CalzadoSku sku40 = sku("T01001-40", "100.00");
        CalzadoSku sku41 = sku("T01001-41", "100.00");
        when(skuRepository.findByModeloColorModeloId(5)).thenReturn(List.of(sku40, sku41));
        when(skuRepository.save(any(CalzadoSku.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.actualizarModelo(5, updateRequest(new BigDecimal("200.00"), null, null, null));

        assertThat(sku40.getPrecioVentaVigente()).isEqualByComparingTo("200.00");
        assertThat(sku41.getPrecioVentaVigente()).isEqualByComparingTo("200.00");
        verify(skuRepository, times(2)).save(any(CalzadoSku.class));
    }

    @Test
    void changingOutletDiscountRecomputesSkuPrices() {
        Modelo existente = modelo(5, 1, true);
        when(modeloRepository.findById(5)).thenReturn(Optional.of(existente));
        when(modeloRepository.save(any(Modelo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CalzadoSku sku40 = sku("T01001-40", "100.00");
        when(skuRepository.findByModeloColorModeloId(5)).thenReturn(List.of(sku40));
        when(skuRepository.save(any(CalzadoSku.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.actualizarOutlet(5, new OutletUpdateRequest(false, new BigDecimal("25")));

        assertThat(existente.getEsOutlet()).isTrue();
        assertThat(existente.getPorcentajeDescuento()).isEqualByComparingTo("25");
        assertThat(sku40.getPrecioVentaVigente()).isEqualByComparingTo("75.00");
    }

    @Test
    void visibilityToggleFlipsTheFlagAndNeverDeletes() {
        Modelo existente = modelo(5, 1, true);
        when(modeloRepository.findById(5)).thenReturn(Optional.of(existente));
        when(modeloRepository.save(any(Modelo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.actualizarVisibilidad(5, new VisibilidadWebUpdateRequest(false));

        assertThat(existente.getEsVisibleWeb()).isFalse();
        verify(modeloRepository).save(existente);
        verify(modeloRepository, never()).delete(any(Modelo.class));
        verify(modeloRepository, never()).deleteById(any());
    }

    @Test
    void adminListKeepsModelsHiddenFromThePublicCatalogue() {
        Modelo visible = modelo(1, 1, true);
        Modelo hidden = modelo(2, 2, false);
        when(modeloRepository.buscarAdmin(null, null, null, null, null)).thenReturn(List.of(visible, hidden));

        List<ModeloAdminResumen> result = service.listarModelos(null, null, null, null, null);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(ModeloAdminResumen::id).containsExactly(1, 2);
        assertThat(result).anyMatch(resumen -> Boolean.FALSE.equals(resumen.esVisibleWeb()));
    }

    @Test
    void rejectsDuplicateCorrelativo() {
        when(tipoCalzadoRepository.findById(1)).thenReturn(Optional.of(tipo()));
        when(marcaRepository.findById(1)).thenReturn(Optional.of(marca()));
        when(modeloRepository.existsByTipoCalzadoIdAndMarcaIdAndCorrelativo(1, 1, 7)).thenReturn(true);

        ModeloCreateRequest request = crearRequest(7, new BigDecimal("100.00"), false, BigDecimal.ZERO);

        assertThatThrownBy(() -> service.crearModelo(request))
            .isInstanceOf(CatalogoBusinessException.class)
            .hasMessageContaining("correlativo");

        verify(modeloRepository, never()).save(any());
    }

    @Test
    void autoAssignedCorrelativoIsTheNextFreeValue() {
        when(tipoCalzadoRepository.findById(1)).thenReturn(Optional.of(tipo()));
        when(marcaRepository.findById(1)).thenReturn(Optional.of(marca()));
        when(modeloRepository.findByTipoCalzadoIdAndMarcaId(1, 1))
            .thenReturn(List.of(modelo(1, 1, true), modelo(2, 2, true), modelo(3, 4, true)));
        when(modeloRepository.save(any(Modelo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.crearModelo(crearRequest(null, new BigDecimal("100.00"), false, BigDecimal.ZERO));

        ArgumentCaptor<Modelo> captor = ArgumentCaptor.forClass(Modelo.class);
        verify(modeloRepository).save(captor.capture());
        assertThat(captor.getValue().getCorrelativo()).isEqualTo(3);
    }

    @Test
    void rejectsCorrelativoOutsideOneToNineNineNine() {
        assertThatThrownBy(() -> service.crearModelo(crearRequest(0, new BigDecimal("100.00"), false, BigDecimal.ZERO)))
            .isInstanceOf(CatalogoBusinessException.class)
            .hasMessageContaining("correlativo");

        assertThatThrownBy(() -> service.crearModelo(crearRequest(1000, new BigDecimal("100.00"), false, BigDecimal.ZERO)))
            .isInstanceOf(CatalogoBusinessException.class)
            .hasMessageContaining("correlativo");
    }

    @Test
    void rejectsInvalidGenero() {
        ModeloCreateRequest base = crearRequest(null, new BigDecimal("100.00"), false, BigDecimal.ZERO);
        ModeloCreateRequest invalido = new ModeloCreateRequest(
            base.idTipoCalzado(), base.idMarca(), base.correlativo(), base.nombre(), base.materialExterior(),
            base.materialForro(), base.tipoSuela(), base.tipoCierre(), "NIÑA",
            base.temporada(), base.precioListaBase(), base.esOutlet(), base.porcentajeDescuento(), true);

        assertThatThrownBy(() -> service.crearModelo(invalido))
            .isInstanceOf(CatalogoBusinessException.class)
            .hasMessageContaining("genero");
    }

    @Test
    void rejectsNonPositiveBasePrice() {
        assertThatThrownBy(() -> service.crearModelo(crearRequest(null, BigDecimal.ZERO, false, BigDecimal.ZERO)))
            .isInstanceOf(CatalogoBusinessException.class)
            .hasMessageContaining("precioListaBase");
    }

    @Test
    void rejectsBlankRequiredText() {
        ModeloCreateRequest base = crearRequest(null, new BigDecimal("100.00"), false, BigDecimal.ZERO);
        ModeloCreateRequest invalido = new ModeloCreateRequest(
            base.idTipoCalzado(), base.idMarca(), base.correlativo(), "   ", base.materialExterior(),
            base.materialForro(), base.tipoSuela(), base.tipoCierre(), base.genero(),
            base.temporada(), base.precioListaBase(), base.esOutlet(), base.porcentajeDescuento(), true);

        assertThatThrownBy(() -> service.crearModelo(invalido))
            .isInstanceOf(CatalogoBusinessException.class)
            .hasMessageContaining("nombre");
    }

    @Test
    void rejectsUnknownTipoOrMarca() {
        when(tipoCalzadoRepository.findById(1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.crearModelo(crearRequest(null, new BigDecimal("100.00"), false, BigDecimal.ZERO)))
            .isInstanceOf(CatalogoBusinessException.class)
            .hasMessageContaining("Tipo de calzado");
    }

    // ------------------------------------------------------------------
    // SKU CRUD
    // ------------------------------------------------------------------

    @Test
    void createSkuDerivesTheCodeFromTipoMarcaCorrelativoAndTalla() {
        Modelo modelo = modelo(5, 1, true);
        modelo.getMarca().setCodigoLetra("V");
        when(modeloColorRepository.findById(10)).thenReturn(Optional.of(modeloColor(10, modelo)));
        when(tallaRepository.findById(2)).thenReturn(Optional.of(talla(2, "39.0", true)));
        when(skuRepository.save(any(CalzadoSku.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SkuAdmin result = service.crearSku(new SkuCreateRequest(10, 2, null, null));

        assertThat(result.sku()).isEqualTo("TV001-39-VE");
        ArgumentCaptor<CalzadoSku> captor = ArgumentCaptor.forClass(CalzadoSku.class);
        verify(skuRepository).save(captor.capture());
        assertThat(captor.getValue().getSku()).isEqualTo("TV001-39-VE");
    }

    @Test
    void twoColourVariantsOfTheSameModelNeverShareACode() {
        Modelo modelo = modelo(5, 1, true);
        modelo.getMarca().setCodigoLetra("V");
        ModeloColor verde = modeloColor(10, modelo, "Verde");
        ModeloColor negro = modeloColor(11, modelo, "Negro");
        when(modeloColorRepository.findById(10)).thenReturn(Optional.of(verde));
        when(modeloColorRepository.findById(11)).thenReturn(Optional.of(negro));
        when(modeloColorRepository.findByModeloIdOrderByIdAsc(5)).thenReturn(List.of(verde, negro));
        when(tallaRepository.findById(2)).thenReturn(Optional.of(talla(2, "39.0", true)));
        when(skuRepository.save(any(CalzadoSku.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String codigoVerde = service.crearSku(new SkuCreateRequest(10, 2, null, null)).sku();
        String codigoNegro = service.crearSku(new SkuCreateRequest(11, 2, null, null)).sku();

        assertThat(codigoVerde).isEqualTo("TV001-39-VE");
        assertThat(codigoNegro).isEqualTo("TV001-39-NE");
        assertThat(codigoVerde).isNotEqualTo(codigoNegro);
    }

    @Test
    void createSkuRejectsADerivedCodeThatAlreadyExists() {
        Modelo modelo = modelo(5, 1, true);
        modelo.getMarca().setCodigoLetra("V");
        when(modeloColorRepository.findById(10)).thenReturn(Optional.of(modeloColor(10, modelo)));
        when(tallaRepository.findById(2)).thenReturn(Optional.of(talla(2, "39.0", true)));
        when(skuRepository.existsBySku("TV001-39-VE")).thenReturn(true);

        assertThatThrownBy(() -> service.crearSku(new SkuCreateRequest(10, 2, null, null)))
            .isInstanceOf(CatalogoBusinessException.class)
            .hasMessageContaining("TV001-39-VE");

        verify(skuRepository, never()).save(any());
    }

    @Test
    void createSkuCopiesTheEffectivePriceFromTheModel() {
        Modelo modelo = modelo(5, 1, true);
        modelo.setEsOutlet(true);
        modelo.setPorcentajeDescuento(new BigDecimal("20"));
        when(modeloColorRepository.findById(10)).thenReturn(Optional.of(modeloColor(10, modelo)));
        when(tallaRepository.findById(2)).thenReturn(Optional.of(talla(2, "39.0", true)));
        when(skuRepository.save(any(CalzadoSku.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.crearSku(new SkuCreateRequest(10, 2, null, null));

        ArgumentCaptor<CalzadoSku> captor = ArgumentCaptor.forClass(CalzadoSku.class);
        verify(skuRepository).save(captor.capture());
        assertThat(captor.getValue().getPrecioVentaVigente()).isEqualByComparingTo("80.00");
    }

    @Test
    void createSkuDefaultsWeightToEightHundredAndActiveToTrue() {
        when(modeloColorRepository.findById(10)).thenReturn(Optional.of(modeloColor(10, modelo(5, 1, true))));
        when(tallaRepository.findById(2)).thenReturn(Optional.of(talla(2, "39.0", true)));
        when(skuRepository.save(any(CalzadoSku.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.crearSku(new SkuCreateRequest(10, 2, null, null));

        ArgumentCaptor<CalzadoSku> captor = ArgumentCaptor.forClass(CalzadoSku.class);
        verify(skuRepository).save(captor.capture());
        assertThat(captor.getValue().getPesoGramos()).isEqualTo(800);
        assertThat(captor.getValue().getActive()).isTrue();
    }

    @Test
    void createSkuProjectsTheOwningColourVariantAndDefaultWeight() {
        when(modeloColorRepository.findById(10)).thenReturn(Optional.of(modeloColor(10, modelo(5, 1, true))));
        when(tallaRepository.findById(2)).thenReturn(Optional.of(talla(2, "39.0", true)));
        when(skuRepository.save(any(CalzadoSku.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SkuAdmin result = service.crearSku(new SkuCreateRequest(10, 2, null, null));

        assertThat(result.idModeloColor()).isEqualTo(10);
        assertThat(result.pesoGramos()).isEqualTo(800);
    }

    @Test
    void createSkuProjectsTheRequestedWeight() {
        when(modeloColorRepository.findById(10)).thenReturn(Optional.of(modeloColor(10, modelo(5, 1, true))));
        when(tallaRepository.findById(2)).thenReturn(Optional.of(talla(2, "39.0", true)));
        when(skuRepository.save(any(CalzadoSku.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SkuAdmin result = service.crearSku(new SkuCreateRequest(10, 2, null, 1200));

        assertThat(result.pesoGramos()).isEqualTo(1200);
    }

    @Test
    void skuAdminMapsMissingColourVariantAndWeightToNull() {
        CalzadoSku sinVariante = new CalzadoSku();
        sinVariante.setSku("TV001-39");
        sinVariante.setPrecioVentaVigente(new BigDecimal("65.00"));
        sinVariante.setActive(true);
        sinVariante.setPesoGramos(null);
        when(modeloRepository.existsById(5)).thenReturn(true);
        when(skuRepository.findByModeloColorModeloId(5)).thenReturn(List.of(sinVariante));

        List<SkuAdmin> skus = service.listarSkus(5);

        assertThat(skus).singleElement().satisfies(admin -> {
            assertThat(admin.idModeloColor()).isNull();
            assertThat(admin.pesoGramos()).isNull();
        });
    }

    @Test
    void createSkuRejectsDuplicateVariantTallaBeforeSaving() {
        when(modeloColorRepository.findById(10)).thenReturn(Optional.of(modeloColor(10, modelo(5, 1, true))));
        when(tallaRepository.findById(2)).thenReturn(Optional.of(talla(2, "39.0", true)));
        when(skuRepository.existsByModeloColorIdAndTallaId(10, 2)).thenReturn(true);

        assertThatThrownBy(() -> service.crearSku(new SkuCreateRequest(10, 2, null, null)))
            .isInstanceOf(CatalogoBusinessException.class)
            .hasMessageContaining("Ya existe un SKU para esa variante de color y talla");

        verify(skuRepository, never()).save(any());
    }

    @Test
    void createSkuRejectsUnknownColorVariant() {
        when(modeloColorRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.crearSku(new SkuCreateRequest(99, 2, null, null)))
            .isInstanceOf(CatalogoBusinessException.class)
            .hasMessageContaining("Variante de color no encontrada");
    }

    @Test
    void createSkuRejectsUnknownTalla() {
        when(modeloColorRepository.findById(10)).thenReturn(Optional.of(modeloColor(10, modelo(5, 1, true))));
        when(tallaRepository.findById(2)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.crearSku(new SkuCreateRequest(10, 2, null, null)))
            .isInstanceOf(CatalogoBusinessException.class)
            .hasMessageContaining("Talla no encontrada");
    }

    @Test
    void createSkuRejectsInactiveTalla() {
        when(modeloColorRepository.findById(10)).thenReturn(Optional.of(modeloColor(10, modelo(5, 1, true))));
        when(tallaRepository.findById(2)).thenReturn(Optional.of(talla(2, "39.0", false)));

        assertThatThrownBy(() -> service.crearSku(new SkuCreateRequest(10, 2, null, null)))
            .isInstanceOf(CatalogoBusinessException.class)
            .hasMessageContaining("talla no esta activa");

        verify(skuRepository, never()).save(any());
    }

    @Test
    void createSkuRequiresModeloColorAndTalla() {
        assertThatThrownBy(() -> service.crearSku(new SkuCreateRequest(null, 2, null, null)))
            .isInstanceOf(CatalogoBusinessException.class)
            .hasMessageContaining("idModeloColor");
        assertThatThrownBy(() -> service.crearSku(new SkuCreateRequest(10, null, null, null)))
            .isInstanceOf(CatalogoBusinessException.class)
            .hasMessageContaining("idTalla");
    }

    @Test
    void createSkuRejectsWeightOutsideTheAllowedRange() {
        when(modeloColorRepository.findById(10)).thenReturn(Optional.of(modeloColor(10, modelo(5, 1, true))));
        when(tallaRepository.findById(2)).thenReturn(Optional.of(talla(2, "39.0", true)));

        assertThatThrownBy(() -> service.crearSku(new SkuCreateRequest(10, 2, null, 0)))
            .isInstanceOf(CatalogoBusinessException.class)
            .hasMessageContaining("pesoGramos");
        assertThatThrownBy(() -> service.crearSku(new SkuCreateRequest(10, 2, null, 10001)))
            .isInstanceOf(CatalogoBusinessException.class)
            .hasMessageContaining("pesoGramos");

        verify(skuRepository, never()).save(any());
    }

    @Test
    void createSkuRejectsA15CharacterBarcode() {
        when(modeloColorRepository.findById(10)).thenReturn(Optional.of(modeloColor(10, modelo(5, 1, true))));
        when(tallaRepository.findById(2)).thenReturn(Optional.of(talla(2, "39.0", true)));

        assertThatThrownBy(() -> service.crearSku(new SkuCreateRequest(10, 2, "123456789012345", null)))
            .isInstanceOf(CatalogoBusinessException.class)
            .hasMessageContaining("codigoBarrasEan");
    }

    @Test
    void createSkuAcceptsAValidBarcodeAndKeepItTrimmed() {
        when(modeloColorRepository.findById(10)).thenReturn(Optional.of(modeloColor(10, modelo(5, 1, true))));
        when(tallaRepository.findById(2)).thenReturn(Optional.of(talla(2, "39.0", true)));
        when(skuRepository.save(any(CalzadoSku.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.crearSku(new SkuCreateRequest(10, 2, " 7501000001391 ", null));

        ArgumentCaptor<CalzadoSku> captor = ArgumentCaptor.forClass(CalzadoSku.class);
        verify(skuRepository).save(captor.capture());
        assertThat(captor.getValue().getCodigoBarrasEan()).isEqualTo("7501000001391");
    }

    @Test
    void updateSkuKeepsStoredValuesWhenBothFieldsAreNull() {
        CalzadoSku existente = sku("TV001-39", "65.00");
        existente.setPesoGramos(900);
        existente.setCodigoBarrasEan("7501000001391");
        when(skuRepository.findById(20)).thenReturn(Optional.of(existente));
        when(skuRepository.save(any(CalzadoSku.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SkuAdmin result = service.actualizarSku(20, new SkuUpdateRequest(null, null));

        assertThat(result.codigoBarrasEan()).isEqualTo("7501000001391");
        assertThat(existente.getCodigoBarrasEan()).isEqualTo("7501000001391");
        assertThat(existente.getPesoGramos()).isEqualTo(900);
    }

    @Test
    void updateSkuClearsTheBarcodeOnEmptyString() {
        CalzadoSku existente = sku("TV001-39", "65.00");
        existente.setCodigoBarrasEan("7501000001391");
        when(skuRepository.findById(20)).thenReturn(Optional.of(existente));
        when(skuRepository.save(any(CalzadoSku.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.actualizarSku(20, new SkuUpdateRequest("", null));

        assertThat(existente.getCodigoBarrasEan()).isNull();
    }

    @Test
    void updateSkuChangesOnlyTheProvidedWeight() {
        CalzadoSku existente = sku("TV001-39", "65.00");
        existente.setCodigoBarrasEan("7501000001391");
        when(skuRepository.findById(20)).thenReturn(Optional.of(existente));
        when(skuRepository.save(any(CalzadoSku.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.actualizarSku(20, new SkuUpdateRequest(null, 1200));

        assertThat(existente.getPesoGramos()).isEqualTo(1200);
        assertThat(existente.getCodigoBarrasEan()).isEqualTo("7501000001391");
    }

    @Test
    void updateSkuRejectsUnknownId() {
        when(skuRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.actualizarSku(99, new SkuUpdateRequest(null, null)))
            .isInstanceOf(CatalogoBusinessException.class)
            .hasMessageContaining("SKU no encontrado");
    }

    @Test
    void patchActiveFlipsTheFlagAndNeverDeletes() {
        CalzadoSku existente = sku("TV001-39", "65.00");
        existente.setActive(true);
        when(skuRepository.findById(20)).thenReturn(Optional.of(existente));
        when(skuRepository.save(any(CalzadoSku.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SkuAdmin result = service.actualizarSkuActivo(20, new SkuActiveUpdateRequest(false));

        assertThat(result.active()).isFalse();
        assertThat(existente.getActive()).isFalse();
        verify(skuRepository).save(existente);
        verify(skuRepository, never()).delete(any(CalzadoSku.class));
        verify(skuRepository, never()).deleteById(any());
    }

    @Test
    void patchActiveRejectsMissingFlag() {
        CalzadoSku existente = sku("TV001-39", "65.00");
        when(skuRepository.findById(20)).thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> service.actualizarSkuActivo(20, new SkuActiveUpdateRequest(null)))
            .isInstanceOf(CatalogoBusinessException.class)
            .hasMessageContaining("active");

        verify(skuRepository, never()).save(any());
    }

    @Test
    void createSkuRejectsABarcodeAlreadyUsedByAnotherSku() {
        when(modeloColorRepository.findById(10)).thenReturn(Optional.of(modeloColor(10, modelo(5, 1, true))));
        when(tallaRepository.findById(2)).thenReturn(Optional.of(talla(2, "39.0", true)));
        when(skuRepository.existsByCodigoBarrasEan("7501000001391")).thenReturn(true);

        assertThatThrownBy(() -> service.crearSku(new SkuCreateRequest(10, 2, "7501000001391", null)))
            .isInstanceOf(CatalogoBusinessException.class)
            .hasMessageContaining("codigo de barras");

        verify(skuRepository, never()).save(any());
    }

    @Test
    void updateSkuRejectsABarcodeAlreadyUsedByAnotherSku() {
        CalzadoSku existente = sku("TV001-39", "65.00");
        existente.setId(20);
        when(skuRepository.findById(20)).thenReturn(Optional.of(existente));
        when(skuRepository.existsByCodigoBarrasEanAndIdNot("7501000002404", 20)).thenReturn(true);

        assertThatThrownBy(() -> service.actualizarSku(20, new SkuUpdateRequest("7501000002404", null)))
            .isInstanceOf(CatalogoBusinessException.class)
            .hasMessageContaining("codigo de barras");

        verify(skuRepository, never()).save(any());
    }

    // ------------------------------------------------------------------
    // Colour variants
    // ------------------------------------------------------------------

    @Test
    void addColorStartsWithoutAPhoto() {
        when(modeloRepository.findById(5)).thenReturn(Optional.of(modelo(5, 1, true)));
        when(colorRepository.findById(3)).thenReturn(Optional.of(color(3, "Negro")));
        when(modeloColorRepository.save(any(ModeloColor.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        ModeloColorAdmin result = service.agregarColor(5, new ColorVarianteCreateRequest(3));

        assertThat(result.colorId()).isEqualTo(3);
        assertThat(result.color()).isEqualTo("Negro");
        assertThat(result.fotoPrincipalUrl()).isNull();
    }

    @Test
    void addColorRejectsADuplicateVariantBeforeSaving() {
        when(modeloRepository.findById(5)).thenReturn(Optional.of(modelo(5, 1, true)));
        when(colorRepository.findById(3)).thenReturn(Optional.of(color(3, "Negro")));
        when(modeloColorRepository.existsByModeloIdAndColorId(5, 3)).thenReturn(true);

        assertThatThrownBy(() -> service.agregarColor(5, new ColorVarianteCreateRequest(3)))
            .isInstanceOf(CatalogoBusinessException.class)
            .hasMessageContaining("La variante de color ya existe para ese modelo");

        verify(modeloColorRepository, never()).save(any());
    }

    @Test
    void addColorRejectsAnUnknownColour() {
        when(modeloRepository.findById(5)).thenReturn(Optional.of(modelo(5, 1, true)));
        when(colorRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.agregarColor(5, new ColorVarianteCreateRequest(99)))
            .isInstanceOf(CatalogoBusinessException.class)
            .hasMessageContaining("Color no encontrado: 99");

        verify(modeloColorRepository, never()).save(any());
    }

    @Test
    void addColorRejectsAnUnknownModel() {
        when(modeloRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.agregarColor(99, new ColorVarianteCreateRequest(3)))
            .isInstanceOf(CatalogoBusinessException.class)
            .hasMessageContaining("Modelo no encontrado: 99");
    }

    @Test
    void addColorRequiresAColourId() {
        when(modeloRepository.findById(5)).thenReturn(Optional.of(modelo(5, 1, true)));

        assertThatThrownBy(() -> service.agregarColor(5, new ColorVarianteCreateRequest(null)))
            .isInstanceOf(CatalogoBusinessException.class)
            .hasMessageContaining("idColor");
    }

    @Test
    void uploadColorImageSetsTheNewUrlAndDeletesThePreviousManagedFile() {
        ModeloColor variante = modeloColor(10, modelo(5, 1, true), "Negro");
        variante.setFotoPrincipalUrl("/storage/calzado/viejo.jpg");
        when(modeloColorRepository.findByModeloIdAndColorId(5, 3)).thenReturn(Optional.of(variante));
        when(imagenStorage.guardar(any())).thenReturn("/storage/calzado/nuevo.jpg");
        when(imagenStorage.esGestionada("/storage/calzado/viejo.jpg")).thenReturn(true);
        when(modeloColorRepository.save(any(ModeloColor.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        ModeloColorAdmin result = service.subirImagenColor(5, 3,
            new MockMultipartFile("imagen", "foto.jpg", "image/jpeg", "x".getBytes()));

        assertThat(result.fotoPrincipalUrl()).isEqualTo("/storage/calzado/nuevo.jpg");
        verify(imagenStorage).borrar("/storage/calzado/viejo.jpg");
    }

    @Test
    void uploadColorImageRejectsAnEmptyFileAndSavesNothing() {
        ModeloColor variante = modeloColor(10, modelo(5, 1, true), "Negro");
        when(modeloColorRepository.findByModeloIdAndColorId(5, 3)).thenReturn(Optional.of(variante));

        assertThatThrownBy(() -> service.subirImagenColor(5, 3,
            new MockMultipartFile("imagen", "foto.jpg", "image/jpeg", new byte[0])))
            .isInstanceOf(CatalogoBusinessException.class)
            .hasMessageContaining("imagen");

        verify(imagenStorage, never()).guardar(any());
        verify(modeloColorRepository, never()).save(any());
    }

    @Test
    void uploadColorImageOverAnUnmanagedUrlDoesNotDeleteAnything() {
        ModeloColor variante = modeloColor(10, modelo(5, 1, true), "Negro");
        variante.setFotoPrincipalUrl("/assets/img/calzado/default-shoe.webp");
        when(modeloColorRepository.findByModeloIdAndColorId(5, 3)).thenReturn(Optional.of(variante));
        when(imagenStorage.guardar(any())).thenReturn("/storage/calzado/nuevo.jpg");
        when(imagenStorage.esGestionada("/assets/img/calzado/default-shoe.webp")).thenReturn(false);
        when(modeloColorRepository.save(any(ModeloColor.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        service.subirImagenColor(5, 3,
            new MockMultipartFile("imagen", "foto.jpg", "image/jpeg", "x".getBytes()));

        verify(imagenStorage, never()).borrar(any());
    }

    @Test
    void uploadColorImageRejectsAnUnknownVariant() {
        when(modeloColorRepository.findByModeloIdAndColorId(5, 3)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.subirImagenColor(5, 3,
            new MockMultipartFile("imagen", "foto.jpg", "image/jpeg", "x".getBytes())))
            .isInstanceOf(CatalogoBusinessException.class)
            .hasMessageContaining("Variante de color no encontrada para el modelo 5: color 3");

        verify(imagenStorage, never()).guardar(any());
    }

    @Test
    void uploadColorImageSavesTheRowBeforeDeletingThePreviousFile() {
        ModeloColor variante = modeloColor(10, modelo(5, 1, true), "Negro");
        variante.setFotoPrincipalUrl("/storage/calzado/viejo.jpg");
        when(modeloColorRepository.findByModeloIdAndColorId(5, 3)).thenReturn(Optional.of(variante));
        when(imagenStorage.guardar(any())).thenReturn("/storage/calzado/nuevo.jpg");
        when(imagenStorage.esGestionada("/storage/calzado/viejo.jpg")).thenReturn(true);
        when(modeloColorRepository.save(any(ModeloColor.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        service.subirImagenColor(5, 3,
            new MockMultipartFile("imagen", "foto.jpg", "image/jpeg", "x".getBytes()));

        InOrder inOrder = inOrder(modeloColorRepository, imagenStorage);
        inOrder.verify(modeloColorRepository).save(any(ModeloColor.class));
        inOrder.verify(imagenStorage).borrar("/storage/calzado/viejo.jpg");
    }

    @Test
    void deleteColorImageNullsTheUrlAndRemovesTheManagedFile() {
        ModeloColor variante = modeloColor(10, modelo(5, 1, true), "Negro");
        variante.setFotoPrincipalUrl("/storage/calzado/viejo.jpg");
        when(modeloColorRepository.findByModeloIdAndColorId(5, 3)).thenReturn(Optional.of(variante));
        when(imagenStorage.esGestionada("/storage/calzado/viejo.jpg")).thenReturn(true);
        when(modeloColorRepository.save(any(ModeloColor.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        ModeloColorAdmin result = service.eliminarImagenColor(5, 3);

        assertThat(result.fotoPrincipalUrl()).isNull();
        verify(imagenStorage).borrar("/storage/calzado/viejo.jpg");
    }

    // ------------------------------------------------------------------
    // Size CRUD
    // ------------------------------------------------------------------

    @Test
    void listTallasReturnsEverySizeIncludingInactiveOnes() {
        when(tallaRepository.findAllByOrderByTallaEurAsc())
            .thenReturn(List.of(talla(1, "39.0", true), talla(2, "40.0", false)));

        List<TallaAdmin> result = service.listarTallas();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(TallaAdmin::tallaEur)
            .containsExactly(new BigDecimal("39.0"), new BigDecimal("40.0"));
        assertThat(result).anyMatch(talla -> Boolean.FALSE.equals(talla.active()));
    }

    @Test
    void createTallaRejectsMissingBody() {
        assertThatThrownBy(() -> service.crearTalla(null))
            .isInstanceOf(CatalogoBusinessException.class)
            .hasMessageContaining("cuerpo");

        verify(tallaRepository, never()).save(any());
    }

    @Test
    void createTallaRequiresTallaEur() {
        assertThatThrownBy(() -> service.crearTalla(
            new TallaCreateRequest(null, null, null, null, new BigDecimal("25.0"))))
            .isInstanceOf(CatalogoBusinessException.class)
            .hasMessageContaining("tallaEur");

        verify(tallaRepository, never()).save(any());
    }

    @Test
    void createTallaRejectsNonPositiveTallaEur() {
        assertThatThrownBy(() -> service.crearTalla(
            new TallaCreateRequest(BigDecimal.ZERO, null, null, null, new BigDecimal("25.0"))))
            .isInstanceOf(CatalogoBusinessException.class)
            .hasMessageContaining("tallaEur");

        verify(tallaRepository, never()).save(any());
    }

    @Test
    void createTallaRejectsMoreThanOneDecimal() {
        assertThatThrownBy(() -> service.crearTalla(
            new TallaCreateRequest(new BigDecimal("39.55"), null, null, null, new BigDecimal("25.0"))))
            .isInstanceOf(CatalogoBusinessException.class)
            .hasMessageContaining("tallaEur");

        verify(tallaRepository, never()).save(any());
    }

    @Test
    void createTallaRejectsValuesAboveTheColumnRange() {
        assertThatThrownBy(() -> service.crearTalla(
            new TallaCreateRequest(new BigDecimal("100.0"), null, null, null, new BigDecimal("25.0"))))
            .isInstanceOf(CatalogoBusinessException.class)
            .hasMessageContaining("tallaEur");

        verify(tallaRepository, never()).save(any());
    }

    @Test
    void createTallaRejectsDuplicateBeforeInsert() {
        when(tallaRepository.existsByTallaEur(new BigDecimal("39.0"))).thenReturn(true);

        assertThatThrownBy(() -> service.crearTalla(
            new TallaCreateRequest(new BigDecimal("39.0"), null, null, null, new BigDecimal("25.0"))))
            .isInstanceOf(CatalogoBusinessException.class)
            .hasMessage("Ya existe una talla con tallaEur 39.0");

        verify(tallaRepository, never()).save(any());
    }

    @Test
    void createTallaPersistsEveryProvidedField() {
        when(tallaRepository.existsByTallaEur(new BigDecimal("39.0"))).thenReturn(false);
        when(tallaRepository.save(any(TallaEquivalencia.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TallaAdmin result = service.crearTalla(new TallaCreateRequest(
            new BigDecimal("39.0"), new BigDecimal("6.5"), new BigDecimal("8.5"),
            new BigDecimal("6.0"), new BigDecimal("24.5")));

        assertThat(result.tallaEur()).isEqualByComparingTo("39.0");
        assertThat(result.tallaUsHombre()).isEqualByComparingTo("6.5");
        assertThat(result.tallaUsMujer()).isEqualByComparingTo("8.5");
        assertThat(result.tallaUk()).isEqualByComparingTo("6.0");
        assertThat(result.tallaCmMx()).isEqualByComparingTo("24.5");
        assertThat(result.active()).isTrue();

        ArgumentCaptor<TallaEquivalencia> captor = ArgumentCaptor.forClass(TallaEquivalencia.class);
        verify(tallaRepository).save(captor.capture());
        assertThat(captor.getValue().getTallaEur()).isEqualByComparingTo("39.0");
        assertThat(captor.getValue().getActive()).isTrue();
    }

    @Test
    void createTallaRequiresTallaCmMx() {
        assertThatThrownBy(() -> service.crearTalla(
            new TallaCreateRequest(new BigDecimal("39.0"), null, null, null, null)))
            .isInstanceOf(CatalogoBusinessException.class)
            .hasMessageContaining("tallaCmMx");

        verify(tallaRepository, never()).save(any());
    }

    @Test
    void createTallaRejectsNonPositiveTallaCmMx() {
        assertThatThrownBy(() -> service.crearTalla(
            new TallaCreateRequest(new BigDecimal("39.0"), null, null, null, BigDecimal.ZERO)))
            .isInstanceOf(CatalogoBusinessException.class)
            .hasMessageContaining("tallaCmMx");

        verify(tallaRepository, never()).save(any());
    }

    @Test
    void createTallaAllowsNullOptionalSizes() {
        when(tallaRepository.existsByTallaEur(new BigDecimal("39.0"))).thenReturn(false);
        when(tallaRepository.save(any(TallaEquivalencia.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TallaAdmin result = service.crearTalla(
            new TallaCreateRequest(new BigDecimal("39.0"), null, null, null, new BigDecimal("24.5")));

        assertThat(result.tallaUsHombre()).isNull();
        assertThat(result.tallaUsMujer()).isNull();
        assertThat(result.tallaUk()).isNull();
    }

    @Test
    void updateTallaRejectsMissingBody() {
        TallaEquivalencia existente = talla(5, "39.0", true);
        when(tallaRepository.findById(5)).thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> service.actualizarTalla(5, null))
            .isInstanceOf(CatalogoBusinessException.class)
            .hasMessageContaining("cuerpo");

        verify(tallaRepository, never()).save(any());
    }

    @Test
    void updateTallaRejectsUnknownId() {
        when(tallaRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.actualizarTalla(99,
            new TallaUpdateRequest(null, null, null, new BigDecimal("25.0"))))
            .isInstanceOf(CatalogoBusinessException.class)
            .hasMessage("Talla no encontrada: 99");
    }

    @Test
    void updateTallaKeepsStoredValuesWhenFieldsAreNull() {
        TallaEquivalencia existente = talla(5, "39.0", true);
        existente.setTallaUsHombre(new BigDecimal("6.5"));
        when(tallaRepository.findById(5)).thenReturn(Optional.of(existente));
        when(tallaRepository.save(any(TallaEquivalencia.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.actualizarTalla(5, new TallaUpdateRequest(null, null, null, null));

        assertThat(existente.getTallaUsHombre()).isEqualByComparingTo("6.5");
        assertThat(existente.getTallaCmMx()).isEqualByComparingTo("25.0");
    }

    @Test
    void updateTallaNeverChangesTallaEur() {
        // tallaEur is absent from TallaUpdateRequest on purpose: an already printed or scanned SKU
        // code embeds it, so it must never change.
        TallaEquivalencia existente = talla(5, "39.0", true);
        when(tallaRepository.findById(5)).thenReturn(Optional.of(existente));
        when(tallaRepository.save(any(TallaEquivalencia.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TallaAdmin result = service.actualizarTalla(5, new TallaUpdateRequest(
            new BigDecimal("7.0"), new BigDecimal("9.0"), new BigDecimal("6.5"), new BigDecimal("25.5")));

        assertThat(result.tallaEur()).isEqualByComparingTo("39.0");
        assertThat(result.tallaUsHombre()).isEqualByComparingTo("7.0");
        assertThat(result.tallaCmMx()).isEqualByComparingTo("25.5");
    }

    @Test
    void patchTallaActiveRejectsMissingFlag() {
        TallaEquivalencia existente = talla(5, "39.0", true);
        when(tallaRepository.findById(5)).thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> service.actualizarTallaActivo(5, new TallaActiveUpdateRequest(null)))
            .isInstanceOf(CatalogoBusinessException.class)
            .hasMessageContaining("active");

        verify(tallaRepository, never()).save(any());
    }

    @Test
    void patchTallaActiveFlipsTheFlagAndNeverDeletes() {
        TallaEquivalencia existente = talla(5, "39.0", true);
        when(tallaRepository.findById(5)).thenReturn(Optional.of(existente));
        when(tallaRepository.save(any(TallaEquivalencia.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TallaAdmin result = service.actualizarTallaActivo(5, new TallaActiveUpdateRequest(false));

        assertThat(result.active()).isFalse();
        assertThat(existente.getActive()).isFalse();
        verify(tallaRepository).save(existente);
        verify(tallaRepository, never()).delete(any(TallaEquivalencia.class));
        verify(tallaRepository, never()).deleteById(any());
    }

    @Test
    void patchTallaActiveRejectsUnknownId() {
        when(tallaRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.actualizarTallaActivo(99, new TallaActiveUpdateRequest(false)))
            .isInstanceOf(CatalogoBusinessException.class)
            .hasMessage("Talla no encontrada: 99");
    }

    private void stubNuevoCorrelativo() {
        when(tipoCalzadoRepository.findById(1)).thenReturn(Optional.of(tipo()));
        when(marcaRepository.findById(1)).thenReturn(Optional.of(marca()));
        when(modeloRepository.findByTipoCalzadoIdAndMarcaId(1, 1)).thenReturn(List.of());
    }

    private static ModeloCreateRequest crearRequest(Integer correlativo, BigDecimal precio, Boolean esOutlet, BigDecimal descuento) {
        return new ModeloCreateRequest(
            1, 1, correlativo, "Zapato Urbano", "Cuero", "Textil", "Goma", "Cordones",
            "UNISEX", "2025", precio, esOutlet, descuento, true);
    }

    private static ModeloUpdateRequest updateRequest(BigDecimal precio, Boolean esOutlet, BigDecimal descuento, Boolean visible) {
        return new ModeloUpdateRequest(
            null, null, null, null, null, null, null, null, null, null,
            precio, esOutlet, descuento, visible);
    }

    private static Modelo modelo(Integer id, int correlativo, boolean visible) {
        Modelo modelo = new Modelo();
        modelo.setId(id);
        modelo.setTipoCalzado(tipo());
        modelo.setMarca(marca());
        modelo.setCorrelativo(correlativo);
        modelo.setNombre("Modelo " + correlativo);
        modelo.setMaterialExterior("Cuero");
        modelo.setTipoSuela("Goma");
        modelo.setTipoCierre("Cordones");
        modelo.setGenero("UNISEX");
        modelo.setTemporada("2025");
        modelo.setPrecioListaBase(new BigDecimal("100.00"));
        modelo.setEsOutlet(false);
        modelo.setPorcentajeDescuento(BigDecimal.ZERO);
        modelo.setEsVisibleWeb(visible);
        return modelo;
    }

    private static CalzadoSku sku(String codigo, String precio) {
        CalzadoSku sku = new CalzadoSku();
        sku.setSku(codigo);
        sku.setPrecioVentaVigente(new BigDecimal(precio));
        sku.setActive(true);
        TallaEquivalencia talla = new TallaEquivalencia();
        talla.setTallaEur(new BigDecimal("40"));
        talla.setTallaCmMx(new BigDecimal("25.0"));
        sku.setTalla(talla);
        return sku;
    }

    private static TipoCalzado tipo() {
        TipoCalzado tipo = new TipoCalzado();
        tipo.setId(1);
        tipo.setCodigoLetra("T");
        tipo.setNombre("Tenis");
        return tipo;
    }

    private static Marca marca() {
        Marca marca = new Marca();
        marca.setId(1);
        marca.setCodigoLetra("M");
        marca.setNombre("MarcaX");
        return marca;
    }

    private static ModeloColor modeloColor(Integer id, Modelo modelo) {
        return modeloColor(id, modelo, "Verde");
    }

    private static Color color(Integer id, String nombre) {
        Color color = new Color();
        color.setId(id);
        color.setNombre(nombre);
        color.setCodigoHex("#000000");
        return color;
    }

    private static ModeloColor modeloColor(Integer id, Modelo modelo, String nombreColor) {
        Color color = new Color();
        color.setNombre(nombreColor);
        ModeloColor modeloColor = new ModeloColor();
        modeloColor.setId(id);
        modeloColor.setModelo(modelo);
        modeloColor.setColor(color);
        return modeloColor;
    }

    private static TallaEquivalencia talla(Integer id, String tallaEur, boolean active) {
        TallaEquivalencia talla = new TallaEquivalencia();
        talla.setId(id);
        talla.setTallaEur(new BigDecimal(tallaEur));
        talla.setTallaCmMx(new BigDecimal("25.0"));
        talla.setActive(active);
        return talla;
    }
}
