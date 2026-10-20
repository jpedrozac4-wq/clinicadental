package com.empresa.calzado.erp.seguridad;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Authentication entry point for the ERP and the storefront.
 *
 * <p>Login exchanges credentials for a stateless JWT. The token carries the canonical
 * {@code ROLE_*} authorities plus the granular {@code OP_*} permissions, which is what both the
 * back office menu and the endpoint matchers rely on.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String OP_PREFIX = "OP_";
    private static final String ROLE_PREFIX = "ROLE_";

    private final AuthenticationManager authenticationManager;
    private final SystemUserRepository userRepository;
    private final JwtService jwtService;

    public AuthController(
            AuthenticationManager authenticationManager,
            SystemUserRepository userRepository,
            JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest request) {
        Authentication auth = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );

        SystemUser user = userRepository.findByUsername(auth.getName())
            .orElseThrow(() -> new IllegalStateException("Usuario autenticado no encontrado"));

        user.setLastLogin(OffsetDateTime.now());
        userRepository.save(user);

        List<String> authorities = auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .sorted()
            .toList();

        String token = jwtService.issueToken(user.getUsername(), user.getFullName(), authorities);

        return ResponseEntity.ok(Map.ofEntries(
            Map.entry("status", "SUCCESS"),
            Map.entry("token", token),
            Map.entry("tokenType", "Bearer"),
            Map.entry("expiresIn", jwtService.getExpirationSeconds()),
            Map.entry("username", user.getUsername()),
            Map.entry("fullName", user.getFullName()),
            Map.entry("email", user.getEmail()),
            Map.entry("roles", filterByPrefix(authorities, ROLE_PREFIX)),
            Map.entry("permissions", filterByPrefix(authorities, OP_PREFIX)),
            Map.entry("authorities", authorities)
        ));
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> currentUser(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("status", "ERROR", "message", "No autenticado"));
        }

        List<String> authorities = auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .sorted()
            .toList();

        SystemUser user = userRepository.findByUsername(auth.getName()).orElse(null);

        return ResponseEntity.ok(Map.of(
            "status", "SUCCESS",
            "username", auth.getName(),
            "fullName", user != null ? user.getFullName() : auth.getName(),
            "email", user != null ? user.getEmail() : "",
            "roles", filterByPrefix(authorities, ROLE_PREFIX),
            "permissions", filterByPrefix(authorities, OP_PREFIX),
            "authorities", authorities
        ));
    }

    /**
     * Stateless logout: the token is discarded by the client. The endpoint exists so the frontend
     * has one canonical place to close a session and so audit hooks can be attached later.
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout() {
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Sesión finalizada"));
    }

    private static List<String> filterByPrefix(List<String> authorities, String prefix) {
        return authorities.stream()
            .filter(authority -> authority.startsWith(prefix))
            .map(authority -> authority.substring(prefix.length()))
            .toList();
    }

    public record LoginRequest(String username, String password) {}
}
