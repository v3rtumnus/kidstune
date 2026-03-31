package at.kidstune.config;

import at.kidstune.auth.JwtAuthenticationFilter;
import at.kidstune.web.WebSessionSecurityContextRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher;
import reactor.core.publisher.Mono;

import java.net.URI;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    public static final String KIDS_ROLE   = "KIDS";
    public static final String PARENT_ROLE = "PARENT";

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final WebSessionSecurityContextRepository webSessionSecurityContextRepository;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          WebSessionSecurityContextRepository webSessionSecurityContextRepository) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.webSessionSecurityContextRepository = webSessionSecurityContextRepository;
    }

    // ── Web dashboard security (session-based) ───────────────────────────────
    // Order(1): evaluated first; only matches /web/** requests.
    // Authentication is loaded by WebSessionSecurityContextRepository, which reads
    // the familyId WebSession attribute written by WebLoginController after OAuth.
    // SecurityContextServerWebExchangeWebFilter populates the Reactor context with
    // that authentication before AuthorizationWebFilter runs.

    @Bean
    @Order(1)
    public SecurityWebFilterChain webFilterChain(ServerHttpSecurity http) {
        return http
                .securityMatcher(new PathPatternParserServerWebExchangeMatcher("/web/**"))
                .securityContextRepository(webSessionSecurityContextRepository)
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        // Login / callback / approve links are public
                        .pathMatchers(
                                "/web/login",
                                "/web/auth/spotify-login",
                                "/web/auth/callback",
                                "/web/approve/**"
                        ).permitAll()
                        .anyExchange().authenticated()
                )
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint((exchange, ex) -> {
                            exchange.getResponse().setStatusCode(HttpStatus.FOUND);
                            exchange.getResponse().getHeaders().setLocation(URI.create("/web/login"));
                            return Mono.empty();
                        })
                )
                .build();
    }

    // ── API / everything else (JWT-based) ────────────────────────────────────
    // Order(2): evaluated after the web chain; handles /api/** and static resources.

    @Bean
    @Order(2)
    public SecurityWebFilterChain apiFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .addFilterAt(jwtAuthenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .authorizeExchange(exchanges -> exchanges

                        // ── Public ───────────────────────────────────────────
                        .pathMatchers("/actuator/health").permitAll()
                        .pathMatchers("/webjars/**").permitAll()
                        .pathMatchers("/favicon.ico", "/robots.txt").permitAll()
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
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint((exchange, ex) -> {
                            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                            return exchange.getResponse().setComplete();
                        })
                        .accessDeniedHandler((exchange, ex) -> {
                            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                            return exchange.getResponse().setComplete();
                        })
                )
                .build();
    }
}