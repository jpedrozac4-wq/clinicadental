package com.clinica.dental.seguridad;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Stateless security configuration for the ERP and the B2C storefront.
 *
 * <p>The storefront reads public catalog, checkout and tracking endpoints anonymously, while every
 * back-office route is bound to the nine canonical roles and their granular {@code OP_*} permissions
 * (see {@link CustomUserDetailsService}). Authorization is role based at the route level and can be
 * refined per use case with {@code @PreAuthorize}.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${clinica.security.cors.allowed-origins:http://localhost,http://localhost:80,http://localhost:8080,http://127.0.0.1,http://127.0.0.1:8080}")
    private String allowedOrigins;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .exceptionHandling(handling -> handling
                .authenticationEntryPoint((request, response, authException) ->
                    writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "No autenticado"))
                .accessDeniedHandler((request, response, accessDeniedException) ->
                    writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, "Acceso denegado"))
            )
            .authorizeHttpRequests(auth -> auth
                // ---- Static frontend assets ----
                .requestMatchers("/", "/index.html", "/portal-erp.html", "/login.html",
                                 "/assets/**", "/favicon.ico", "/*.css", "/*.js").permitAll()

                // Error dispatch must stay open, otherwise any handler exception
                // resurfaces as a 401 on the /error forward.
                .requestMatchers("/error").permitAll()

                // ---- Authentication ----
                .requestMatchers("/api/auth/login", "/api/auth/logout").permitAll()
                .requestMatchers("/api/public/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/storage/**").permitAll()

                // ---- Back-office catalog administration (must precede the public catalog rule) ----
                .requestMatchers("/api/catalogo/admin/**")
                    .hasAnyAuthority("OP_CATALOGO_CREAR_EDITAR", "ROLE_ADMIN")

                // ---- Public B2C storefront ----
                .requestMatchers(HttpMethod.GET, "/api/catalogo/**").permitAll()
                // Catalog writes stay restricted to the catalog permission.
                .requestMatchers("/api/catalogo/**").hasAnyAuthority("OP_CATALOGO_CREAR_EDITAR", "ROLE_ADMIN")
                .requestMatchers("/api/checkout/**").permitAll()
                .requestMatchers("/api/tracking/**").permitAll()
                .requestMatchers("/api/marketing/public/**").permitAll()

                // ---- Back office by department ----
                .requestMatchers("/api/gerencia/**").hasAnyAuthority(
                    "ROLE_ADMIN", "ROLE_GERENTE_GENERAL", "ROLE_GERENTE_FINANCIERO", "ROLE_GERENTE_VENTAS")
                .requestMatchers("/api/direccion/**").hasAnyAuthority(
                    "OP_PRECIOS_SOLICITAR_CAMBIO", "OP_PRECIOS_APROBAR_CAMBIO",
                    "ROLE_ADMIN", "ROLE_GERENTE_GENERAL", "ROLE_GERENTE_VENTAS")
                // Arqueo audit is its own permission (OP_POS_ARQUEO_AUDITAR). This matcher must
                // precede the general /api/pos/** rule below, because Spring Security evaluates
                // rules in order and a later rule for this exact path would never be reached.
                .requestMatchers("/api/pos/sesiones/*/auditar")
                    .hasAnyAuthority("OP_POS_ARQUEO_AUDITAR", "ROLE_ADMIN")
                .requestMatchers("/api/pos/**").hasAnyAuthority(
                    "OP_POS_VENTA_COBRAR", "OP_POS_CAJA_APERTURA_CIERRE", "OP_DEVOLUCION_PROCESAR",
                    "ROLE_ADMIN", "ROLE_GERENTE_VENTAS")
                .requestMatchers("/api/inventario/**").hasAnyAuthority(
                    "OP_STOCK_CONSULTAR", "OP_KARDEX_AUDITAR", "OP_ABASTECIMIENTO_SOLICITAR",
                    "OP_ABASTECIMIENTO_DESPACHAR", "OP_STOCK_BAJA_SOLICITAR", "OP_STOCK_BAJA_APROBAR",
                    "ROLE_ADMIN")
                .requestMatchers("/api/compras/**").hasAnyAuthority(
                    "OP_COMPRAS_SOLICITAR", "OP_COMPRAS_ORDEN_EMITIR", "OP_ACONDICIONAMIENTO_APROBAR",
                    "ROLE_ADMIN", "ROLE_GERENTE_GENERAL")
                .requestMatchers("/api/logistica/**").hasAnyAuthority(
                    "OP_PICKING_PACKING_BODEGA", "OP_DESPACHO_ASIGNAR_RUTA", "OP_DESPACHO_CONFIRMAR_OTP",
                    "ROLE_ADMIN", "ROLE_ENCARGADO_ALMACEN", "ROLE_AUXILIAR_ALMACEN")
                .requestMatchers("/api/facturacion/**").hasAnyAuthority(
                    "OP_FACTURAS_NOTAS_CREDITO_EMITIR", "OP_CONCILIACION_PASARELA_VER",
                    "ROLE_ADMIN", "ROLE_CONTADOR", "ROLE_GERENTE_FINANCIERO")
                .requestMatchers("/api/soporte/**").hasAnyAuthority(
                    "OP_TICKETS_GESTIONAR_DERIVAR", "OP_TICKETS_SUPERVISAR_ALERTAS",
                    "ROLE_ADMIN", "ROLE_GERENTE_GENERAL")
                .requestMatchers("/api/marketing/**").hasAnyAuthority(
                    "ROLE_ADMIN", "ROLE_GERENTE_VENTAS")
                .requestMatchers("/api/rrhh/**").hasAnyAuthority(
                    "ROLE_ADMIN", "ROLE_GERENTE_GENERAL", "ROLE_GERENTE_FINANCIERO")
                .requestMatchers("/api/administracion/**").hasAnyAuthority(
                    "OP_USUARIOS_ROLES_ADMIN", "OP_AUDITORIA_LOGS_VER", "ROLE_ADMIN")
                .requestMatchers("/api/auditoria/**").hasAnyAuthority(
                    "OP_AUDITORIA_LOGS_VER", "ROLE_ADMIN")

                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
            .map(String::trim)
            .filter(origin -> !origin.isEmpty())
            .toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        configuration.setExposedHeaders(List.of("Content-Disposition"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private static void writeJsonError(HttpServletResponse response, int status, String message)
            throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"status\":\"ERROR\",\"message\":\"" + message + "\"}");
    }
}
