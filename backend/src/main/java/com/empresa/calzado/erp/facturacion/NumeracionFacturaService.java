package com.empresa.calzado.erp.facturacion;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Issues the correlative number of a sale document (project Decision 34): the highest numeric suffix
 * already used for that prefix, plus one.
 *
 * <p>Inside the invoicing transaction it first takes a PESSIMISTIC WRITE lock on the
 * {@code cat_factura_tipo} row of the document being issued, so two concurrent invoices of the same
 * type serialize on that row and cannot both compute the same next number. The lock is held until
 * the transaction commits; that is the declared trade-off at RN-TEC-01's load and the eventual fix
 * is a counter table, which would need DDL.
 *
 * <p>Only all-digit suffixes are parsed. The legacy hexadecimal correlativos
 * ({@code FAC-A1B2C3D4}) written by the defect are ignored, so they do not poison the sequence.
 * The credit note keeps its own derived numbering and does not call this service.
 */
@Service
public class NumeracionFacturaService {

    /** Prefix of a formal invoice. */
    public static final String PREFIJO_FACTURA = "FAC";
    /** Prefix of a simplified sale receipt. */
    public static final String PREFIJO_BOLETA = "TKT";
    /** Prefix of a credit note, kept for completeness; the credit note does not call this service. */
    public static final String PREFIJO_NOTA_CREDITO = "NC";

    private static final int DIGITOS = 6;

    private static final Map<String, String> PREFIJOS = Map.of(
        "FACTURA", PREFIJO_FACTURA,
        "BOLETA", PREFIJO_BOLETA,
        "NOTA_CREDITO", PREFIJO_NOTA_CREDITO
    );

    private final FacturaRepository facturaRepository;
    private final FacturaTipoRepository facturaTipoRepository;

    public NumeracionFacturaService(
        FacturaRepository facturaRepository,
        FacturaTipoRepository facturaTipoRepository
    ) {
        this.facturaRepository = facturaRepository;
        this.facturaTipoRepository = facturaTipoRepository;
    }

    /**
     * The next correlative for the document type, for example {@code FAC-000102}. Must be called
     * inside the invoicing transaction so the type-row lock is held until the invoice commits.
     */
    @Transactional
    public String siguiente(FacturaTipo tipo) {
        String prefijo = prefijoDe(tipo.getCode());
        facturaTipoRepository.findByCodeConBloqueoPesimista(tipo.getCode());
        List<String> existentes = facturaRepository.findCorrelativosPorPrefijo(prefijo + "-");
        int numero = siguienteNumero(prefijo, existentes);
        return prefijo + "-" + String.format("%0" + DIGITOS + "d", numero);
    }

    /**
     * The prefix a document type uses. An unknown type is a broken invariant, not a client mistake,
     * so it fails loudly instead of inventing a prefix.
     */
    public static String prefijoDe(String tipoCode) {
        String prefijo = tipoCode == null ? null : PREFIJOS.get(tipoCode);
        if (prefijo == null) {
            throw new IllegalStateException("Tipo de comprobante sin prefijo de numeracion: " + tipoCode);
        }
        return prefijo;
    }

    /**
     * Highest all-digit suffix of {@code prefijo} plus one. A correlativo whose suffix is not
     * entirely numeric (a legacy hexadecimal, a stray value) is ignored.
     */
    static int siguienteNumero(String prefijo, List<String> correlativos) {
        if (correlativos == null) {
            return 1;
        }
        String prefijoGuion = prefijo + "-";
        int maximo = 0;
        for (String correlativo : correlativos) {
            if (correlativo == null || !correlativo.startsWith(prefijoGuion)) {
                continue;
            }
            String sufijo = correlativo.substring(prefijoGuion.length());
            if (sufijo.isEmpty() || !esNumerico(sufijo)) {
                continue;
            }
            try {
                maximo = Math.max(maximo, Integer.parseInt(sufijo));
            } catch (NumberFormatException sufijoDemasiadoGrande) {
                // A suffix beyond Integer range cannot be a real sequence value; ignore it rather
                // than fail the sale.
            }
        }
        return maximo + 1;
    }

    private static boolean esNumerico(String valor) {
        for (int i = 0; i < valor.length(); i++) {
            if (!Character.isDigit(valor.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
