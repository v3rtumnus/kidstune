package at.kidstune.config;

import at.kidstune.auth.JwtAuthenticationFilter;
import at.kidstune.auth.LastSeenFilter;
import at.kidstune.web.RememberMeWebFilter;
import at.kidstune.web.WebLoginController;
import at.kidstune.web.WebSessionSecurityContextRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.OrServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher;
import reactor.core.publisher.Mono;

import java.net.URI;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    public static final String KIDS_ROLE   = "KIDS";
    public static final String PARENT_ROLE = "PARENT";

    private final JwtAuthenticationFilter              jwtAuthenticationFilter;
    private final LastSeenFilter                       lastSeenFilter;
    private final WebSessionSecurityContextRepository  webSessionSecurityContextRepository;
    private final RememberMeWebFilter                  rememberMeWebFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          LastSeenFilter lastSeenFilter,
                          WebSessionSecurityContextRepository webSessionSecurityContextRepository,
                          RememberMeWebFilter rememberMeWebFilter) {
        this.jwtAuthenticationFilter             = jwtAuthenticationFilter;
        this.lastSeenFilter                      = lastSeenFilter;
        this.webSessionSecurityContextRepository = webSessionSecurityContextRepository;
        this.rememberMeWebFilter                 = rememberMeWebFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // ── Web dashboard security (session-based) ───────────────────────────────
    // Order(1): evaluated first; only matches /web/** requests.
    // The RememberMeWebFilter runs before SECURITY_CONTEXT_SERVER_WEB_EXCHANGE
    // so it can populate the session before the security context is loaded.

    @Bean
    @Order(1)
    public SecurityWebFilterChain webFilterChain(ServerHttpSecurity http) {
        return http
                .securityMatcher(new OrServerWebExchangeMatcher(
                        new PathPatternParserServerWebExchangeMatcher("/web/**"),
                        new PathPatternParserServerWebExchangeMatcher("/api/v1/profiles/*/import-liked-songs")
                ))
                .securityContextRepository(webSessionSecurityContextRepository)
                .addFilterBefore(rememberMeWebFilter, SecurityWebFiltersOrder.SECURITY_CONTEXT_SERVER_WEB_EXCHANGE)
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(
                                "/web/login",
                                "/web/register",
                                "/web/approve/**"
                        ).permitAll()
                        .anyExchange().authenticated()
                )
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint((exchange, ex) ->
                                exchange.getSession().flatMap(session -> {
                                    // Save the originally requested URL so login can redirect back.
                                    String path  = exchange.getRequest().getURI().getPath();
                                    String query = exchange.getRequest().getURI().getQuery();
                                    String full  = query != null ? path + "?" + query : path;
                                    session.getAttributes().put(WebLoginController.SESSION_LOGIN_REDIRECT, full);
                                    exchange.getResponse().setStatusCode(HttpStatus.FOUND);
                                    exchange.getResponse().getHeaders().setLocation(URI.create("/web/login"));
                                    return Mono.empty();
                                })
                        )
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
                .addFilterAfter(lastSeenFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .authorizeExchange(exchanges -> exchanges

                        // ── Public ───────────────────────────────────────────
                        .pathMatchers("/actuator/health").permitAll()
                        .pathMatchers("/webjars/**").permitAll()
                        .pathMatchers("/", "/favicon.ico", "/robots.txt").permitAll()
                        .pathMatchers(
                                "/api/v1/auth/spotify/**",
                                "/api/v1/auth/pair/confirm",
                                "/api/v1/auth/status"
                        ).permitAll()

                        // ── KIDS-specific (more specific first) ──────────────
                        .pathMatchers("/api/v1/sync/**").hasRole(KIDS_ROLE)
                        .pathMatchers("/api/v1/profiles/*/favorites/**").hasAnyRole(KIDS_ROLE, PARENT_ROLE)
                        .pathMatchers(HttpMethod.POST, "/api/v1/profiles/*/content-requests").hasAnyRole(KIDS_ROLE, PARENT_ROLE)

                        // ── PARENT-only ──────────────────────────────────────
                        .pathMatchers(HttpMethod.POST, "/api/v1/auth/pair").hasRole(PARENT_ROLE)
                        .pathMatchers("/api/v1/devices/**").hasRole(PARENT_ROLE)
                        .pathMatchers("/api/v1/profiles/**").hasRole(PARENT_ROLE)
                        .pathMatchers("/api/v1/content/**").hasRole(PARENT_ROLE)
                        .pathMatchers("/api/v1/spotify/**").hasRole(PARENT_ROLE)
                        .pathMatchers("/api/v1/content-requests", "/api/v1/content-requests/**").hasRole(PARENT_ROLE)

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
