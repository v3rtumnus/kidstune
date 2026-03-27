package at.kidstune.config;

import at.kidstune.auth.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    public static final String KIDS_ROLE = "KIDS";
    public static final String PARENT_ROLE = "PARENT";
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .addFilterAt(jwtAuthenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .authorizeExchange(exchanges -> exchanges

                        // ── Public ───────────────────────────────────────────
                        .pathMatchers("/actuator/health").permitAll()
                        .pathMatchers(
                                "/api/v1/auth/spotify/**",
                                "/api/v1/auth/pair/confirm",
                                "/api/v1/auth/status"
                        ).permitAll()

                        // ── KIDS-specific (more specific first) ──────────────
                        .pathMatchers("/api/v1/sync/**").hasRole(KIDS_ROLE)
                        .pathMatchers("/api/v1/profiles/*/favorites/**").hasAnyRole(KIDS_ROLE, PARENT_ROLE)
                        .pathMatchers(HttpMethod.POST, "/api/v1/content-requests").hasRole(KIDS_ROLE)

                        // ── PARENT-only ──────────────────────────────────────
                        .pathMatchers(HttpMethod.POST, "/api/v1/auth/pair").hasRole(PARENT_ROLE)
                        .pathMatchers("/api/v1/profiles/**").hasRole(PARENT_ROLE)
                        .pathMatchers("/api/v1/content/**").hasRole(PARENT_ROLE)
                        .pathMatchers("/api/v1/spotify/**").hasRole(PARENT_ROLE)
                        .pathMatchers(HttpMethod.GET,  "/api/v1/content-requests").hasRole(PARENT_ROLE)
                        .pathMatchers(HttpMethod.PUT,  "/api/v1/content-requests/**").hasRole(PARENT_ROLE)

                        // ── Anything else requires authentication ─────────────
                        .anyExchange().authenticated()
                )
                .build();
    }
}
