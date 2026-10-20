package com.clinica.dental.seguridad;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String VALID_SECRET = "unit-test-secret-key-with-32-bytes-min";
    private static final String ISSUER = "clinica-dental";

    private static JwtService service(String secret, long expirationSeconds) {
        return new JwtService(secret, expirationSeconds, ISSUER);
    }

    @Test
    void issuesTokenCarryingSubjectAuthoritiesAndIssuer() {
        JwtService jwtService = service(VALID_SECRET, 3600);

        String token = jwtService.issueToken(
            "gerentegeneral",
            "Lic. Roberto Alarcon",
            List.of("ROLE_GERENTE_GENERAL", "OP_PRECIOS_APROBAR_CAMBIO")
        );

        Claims claims = jwtService.parseClaims(token);

        assertThat(claims).isNotNull();
        assertThat(claims.getSubject()).isEqualTo("gerentegeneral");
        assertThat(claims.getIssuer()).isEqualTo(ISSUER);
        assertThat(claims.get("fullName", String.class)).isEqualTo("Lic. Roberto Alarcon");
        assertThat(jwtService.extractAuthorities(claims))
            .containsExactly("ROLE_GERENTE_GENERAL", "OP_PRECIOS_APROBAR_CAMBIO");
    }

    @Test
    void rejectsTokenSignedWithADifferentKey() {
        String foreignToken = service("another-secret-key-with-32-bytes-long!", 3600)
            .issueToken("admin", "Admin", List.of("ROLE_ADMIN"));

        assertThat(service(VALID_SECRET, 3600).parseClaims(foreignToken)).isNull();
    }

    @Test
    void rejectsExpiredToken() {
        String expiredToken = service(VALID_SECRET, -60)
            .issueToken("cajero1", "Cajera", List.of("ROLE_CAJERO"));

        assertThat(service(VALID_SECRET, 3600).parseClaims(expiredToken)).isNull();
    }

    @Test
    void treatsMissingOrBlankTokenAsAnonymous() {
        JwtService jwtService = service(VALID_SECRET, 3600);

        assertThat(jwtService.parseClaims(null)).isNull();
        assertThat(jwtService.parseClaims("")).isNull();
        assertThat(jwtService.parseClaims("   ")).isNull();
        assertThat(jwtService.parseClaims("not-a-jwt")).isNull();
    }

    @Test
    void refusesToStartWithASecretTooShortForHs256() {
        assertThatThrownBy(() -> service("too-short", 3600))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("at least 32 bytes");
    }

    @Test
    void exposesConfiguredExpiration() {
        assertThat(service(VALID_SECRET, 28800).getExpirationSeconds()).isEqualTo(28800);
    }
}
