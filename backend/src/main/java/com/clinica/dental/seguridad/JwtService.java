package com.clinica.dental.seguridad;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * Stateless JWT issuing and validation for the ERP session.
 *
 * <p>Tokens are signed with HS256 using a secret provided through configuration so the
 * application stays stateless and horizontally scalable. Authorities travel inside the
 * token so protected endpoints never need a database round trip to authorize a request.
 */
@Service
public class JwtService {

    private static final String CLAIM_AUTHORITIES = "authorities";
    private static final String CLAIM_FULL_NAME = "fullName";

    private final SecretKey signingKey;
    private final long expirationSeconds;
    private final String issuer;

    public JwtService(
            @Value("${clinica.security.jwt.secret}") String secret,
            @Value("${clinica.security.jwt.expiration-seconds:28800}") long expirationSeconds,
            @Value("${clinica.security.jwt.issuer:clinica-dental}") String issuer) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                "clinica.security.jwt.secret must be at least 32 bytes long to sign HS256 tokens");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationSeconds = expirationSeconds;
        this.issuer = issuer;
    }

    public String issueToken(String username, String fullName, List<String> authorities) {
        Instant now = Instant.now();
        return Jwts.builder()
            .issuer(issuer)
            .subject(username)
            .claim(CLAIM_FULL_NAME, fullName)
            .claim(CLAIM_AUTHORITIES, authorities)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(expirationSeconds)))
            .signWith(signingKey)
            .compact();
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }

    /**
     * Parses and validates a token. Returns {@code null} when the token is absent, malformed,
     * expired or signed with a different key, so callers can treat it as an anonymous request.
     */
    public Claims parseClaims(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        } catch (RuntimeException invalidToken) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public List<String> extractAuthorities(Claims claims) {
        Object raw = claims.get(CLAIM_AUTHORITIES);
        if (raw instanceof List<?> list) {
            return (List<String>) list;
        }
        return List.of();
    }
}
