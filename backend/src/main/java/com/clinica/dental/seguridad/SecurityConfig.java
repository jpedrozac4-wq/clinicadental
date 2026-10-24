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
 * Configuracion de seguridad stateless del sistema de clinica dental.
 *
 * <p>Toda la API es cerrada: no hay portal publico. El login entrega un JWT que transporta los
 * roles canonicos ({@code ROLE_ADMINISTRADOR}, {@code ROLE_DENTISTA}, {@code ROLE_RECEPCIONISTA})
 * junto con los permisos granulares {@code OP_*} definidos en la matriz de roles. La autorizacion
 * se aplica por metodo HTTP y ruta, y puede refinarse por caso de uso con {@code @PreAuthorize}.
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
                // ---- Frontend estatico (en produccion lo sirve Nginx) ----
                .requestMatchers("/", "/index.html", "/favicon.ico", "/*.css", "/*.js").permitAll()

                // Error dispatch must stay open, otherwise any handler exception
                // resurfaces as a 401 on the /error forward.
                .requestMatchers("/error").permitAll()

                // ---- Autenticacion ----
                .requestMatchers("/api/auth/login", "/api/auth/logout").permitAll()

                // ---- Gestion de pacientes (S1, S2, S3) ----
                // El historial clinico tiene permiso propio y se declara antes que la
                // lectura general, porque Spring Security evalua las reglas en orden.
                .requestMatchers(HttpMethod.GET, "/api/pacientes/*/historial")
                    .hasAnyAuthority("OP_PACIENTE_HISTORIAL_VER", "ROLE_ADMINISTRADOR")
                .requestMatchers(HttpMethod.GET, "/api/pacientes/**")
                    .hasAnyAuthority("OP_PACIENTE_VER", "ROLE_ADMINISTRADOR")
                .requestMatchers(HttpMethod.POST, "/api/pacientes")
                    .hasAnyAuthority("OP_PACIENTE_CREAR", "ROLE_ADMINISTRADOR")
                .requestMatchers(HttpMethod.PUT, "/api/pacientes/*")
                    .hasAnyAuthority("OP_PACIENTE_EDITAR", "ROLE_ADMINISTRADOR")
                .requestMatchers(HttpMethod.DELETE, "/api/pacientes/*")
                    .hasAnyAuthority("OP_PACIENTE_ELIMINAR", "ROLE_ADMINISTRADOR")

                // ---- Catalogo de tratamientos (S7) ----
                .requestMatchers(HttpMethod.GET, "/api/tratamientos/**")
                    .hasAnyAuthority("OP_TRATAMIENTO_VER", "ROLE_ADMINISTRADOR")
                .requestMatchers(HttpMethod.POST, "/api/tratamientos", "/api/tratamientos/**")
                    .hasAnyAuthority("OP_TRATAMIENTO_CREAR", "ROLE_ADMINISTRADOR")
                .requestMatchers(HttpMethod.PUT, "/api/tratamientos/*")
                    .hasAnyAuthority("OP_TRATAMIENTO_EDITAR", "ROLE_ADMINISTRADOR")

                // ---- Tratamientos realizados (S8) ----
                .requestMatchers(HttpMethod.GET, "/api/tratamientos-realizados/**")
                    .hasAnyAuthority("OP_REALIZADO_VER", "ROLE_ADMINISTRADOR")
                .requestMatchers(HttpMethod.POST, "/api/tratamientos-realizados")
                    .hasAnyAuthority("OP_REALIZADO_REGISTRAR", "ROLE_ADMINISTRADOR")

                // ---- Citas (S4, S6) ----
                // Reprogramar y cancelar se declaran antes que la lectura de la agenda.
                .requestMatchers(HttpMethod.PATCH, "/api/citas/*/reprogramar")
                    .hasAnyAuthority("OP_CITA_REPROGRAMAR", "ROLE_ADMINISTRADOR")
                .requestMatchers(HttpMethod.PATCH, "/api/citas/*/cancelar")
                    .hasAnyAuthority("OP_CITA_CANCELAR", "ROLE_ADMINISTRADOR")
                .requestMatchers(HttpMethod.GET, "/api/citas/**")
                    .hasAnyAuthority("OP_CITA_VER", "ROLE_ADMINISTRADOR")
                .requestMatchers(HttpMethod.POST, "/api/citas")
                    .hasAnyAuthority("OP_CITA_CREAR", "ROLE_ADMINISTRADOR")

                // ---- Recordatorios de citas (S5) ----
                .requestMatchers(HttpMethod.GET, "/api/recordatorios/**")
                    .hasAnyAuthority("OP_RECORDATORIO_VER", "ROLE_ADMINISTRADOR")
                .requestMatchers(HttpMethod.POST, "/api/recordatorios/**")
                    .hasAnyAuthority("OP_RECORDATORIO_ENVIAR", "ROLE_ADMINISTRADOR")

                // ---- Facturacion y pagos (S9, S10) ----
                // El pago sobre una factura concreta precede a la lectura de comprobantes.
                .requestMatchers(HttpMethod.POST, "/api/facturas/*/pagos")
                    .hasAnyAuthority("OP_PAGO_REGISTRAR", "ROLE_ADMINISTRADOR")
                .requestMatchers(HttpMethod.POST, "/api/facturas")
                    .hasAnyAuthority("OP_FACTURA_EMITIR", "ROLE_ADMINISTRADOR")
                .requestMatchers(HttpMethod.GET, "/api/facturas/**")
                    .hasAnyAuthority("OP_FACTURA_VER", "ROLE_ADMINISTRADOR")

                // ---- Seguridad, usuarios y auditoria ----
                .requestMatchers("/api/usuarios/**", "/api/roles/**", "/api/permisos/**")
                    .hasAnyAuthority("OP_USUARIOS_ADMIN", "ROLE_ADMINISTRADOR")
                .requestMatchers("/api/auditoria/**")
                    .hasAnyAuthority("OP_AUDITORIA_VER", "ROLE_ADMINISTRADOR")

                // ---- Reportes y estadisticas (S21) ----
                .requestMatchers(HttpMethod.GET, "/api/reportes/**")
                    .hasAnyAuthority("OP_REPORTE_VER", "ROLE_ADMINISTRADOR")

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
