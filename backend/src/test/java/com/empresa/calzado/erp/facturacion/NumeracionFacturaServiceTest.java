package com.empresa.calzado.erp.facturacion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the correlative numbering rule (project Decision 34): MAX(numeric suffix) + 1 PER PREFIX,
 * computed inside the invoicing transaction after taking a pessimistic write lock on the document
 * type row. Legacy hexadecimal correlativos from the defect are ignored; only all-digit suffixes
 * are parsed.
 *
 * <p>The repositories are mocked, so this pins the numbering arithmetic and the lock call without a
 * database. The suffix table is asserted case by case because a single sample does not prove the
 * parse rule.
 */
@ExtendWith(MockitoExtension.class)
class NumeracionFacturaServiceTest {

    @Mock private FacturaRepository facturaRepository;
    @Mock private FacturaTipoRepository facturaTipoRepository;

    @InjectMocks private NumeracionFacturaService service;

    @Test
    void elPrefijoSeDerivaDelTipoDeComprobante() {
        assertThat(NumeracionFacturaService.prefijoDe("FACTURA")).isEqualTo("FAC");
        assertThat(NumeracionFacturaService.prefijoDe("BOLETA")).isEqualTo("TKT");
        assertThat(NumeracionFacturaService.prefijoDe("NOTA_CREDITO")).isEqualTo("NC");
    }

    @Test
    void unTipoDesconocidoNoTienePrefijo() {
        assertThatThrownBy(() -> NumeracionFacturaService.prefijoDe("OTRO"))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void sinCorrelativosPreviosEmpiezaEnUno() {
        when(facturaTipoRepository.findByCodeConBloqueoPesimista("FACTURA"))
            .thenReturn(Optional.of(tipo("FACTURA")));
        when(facturaRepository.findCorrelativosPorPrefijo("FAC-")).thenReturn(List.of());

        String numero = service.siguiente(tipo("FACTURA"));

        assertThat(numero).isEqualTo("FAC-000001");
        verify(facturaTipoRepository).findByCodeConBloqueoPesimista("FACTURA");
    }

    @Test
    void tomaElMaximoMasUnoYNoElConteo() {
        when(facturaTipoRepository.findByCodeConBloqueoPesimista("FACTURA"))
            .thenReturn(Optional.of(tipo("FACTURA")));
        when(facturaRepository.findCorrelativosPorPrefijo("FAC-")).thenReturn(List.of(
            "FAC-000101", "FAC-000003", "FAC-000099", "FAC-000007"));

        assertThat(service.siguiente(tipo("FACTURA"))).isEqualTo("FAC-000102");
    }

    @Test
    void ignoraLosCorrelativosHexadecimalesLegados() {
        when(facturaTipoRepository.findByCodeConBloqueoPesimista("FACTURA"))
            .thenReturn(Optional.of(tipo("FACTURA")));
        when(facturaRepository.findCorrelativosPorPrefijo("FAC-")).thenReturn(List.of(
            "FAC-A1B2C3D4", "FAC-0F3E9A11", "FAC-000101"));

        assertThat(service.siguiente(tipo("FACTURA"))).isEqualTo("FAC-000102");
    }

    @Test
    void soloCuentaElSufijoTotalmenteNumerico() {
        assertThat(NumeracionFacturaService.siguienteNumero("FAC", List.of(
            "FAC-000010", "FAC-12A4", "FAC-", "FAC-000009", "FAC--5", "FAC-000011")))
            .isEqualTo(12);
    }

    @Test
    void ignoraCorrelativosDeOtroPrefijo() {
        assertThat(NumeracionFacturaService.siguienteNumero("FAC", List.of(
            "TKT-000900", "NC-000900", "FAC-000005")))
            .isEqualTo(6);
    }

    @Test
    void unSufijoNoNumericoNoRompeElCalculo() {
        assertThat(NumeracionFacturaService.siguienteNumero("FAC", List.of(
            "FAC-ABCDEF", "FAC-000002")))
            .isEqualTo(3);
    }

    @Test
    void conservaSeisDigitosConRellenoDeCeros() {
        when(facturaTipoRepository.findByCodeConBloqueoPesimista("BOLETA"))
            .thenReturn(Optional.of(tipo("BOLETA")));
        when(facturaRepository.findCorrelativosPorPrefijo("TKT-")).thenReturn(List.of("TKT-000041"));

        assertThat(service.siguiente(tipo("BOLETA"))).isEqualTo("TKT-000042");
    }

    private static FacturaTipo tipo(String code) {
        FacturaTipo tipo = new FacturaTipo();
        tipo.setCode(code);
        return tipo;
    }
}
