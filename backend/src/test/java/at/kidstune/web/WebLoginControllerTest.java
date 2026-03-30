package at.kidstune.web;

import at.kidstune.auth.SpotifyConfig;
import at.kidstune.auth.SpotifyTokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.FOUND;

@ExtendWith(MockitoExtension.class)
class WebLoginControllerTest {

    @Mock SpotifyTokenService tokenService;
    @Mock SpotifyConfig       spotifyConfig;

    @InjectMocks
    WebLoginController controller;

    // ── loginPage ────────────────────────────────────────────────────────────

    @Test
    void `loginPage returns web-login view name`() {
        StepVerifier.create(controller.loginPage())
                .expectNext("web/login")
                .verifyComplete();
    }

    // ── spotifyLogin ─────────────────────────────────────────────────────────

    @Test
    void `spotifyLogin stores pkce verifier in session and redirects to Spotify`() {
        when(spotifyConfig.getAccountsBaseUrl()).thenReturn("https://accounts.spotify.com");
        when(spotifyConfig.getClientId()).thenReturn("test-client-id");
        when(spotifyConfig.getWebRedirectUri()).thenReturn("https://example.com/web/auth/callback");

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/web/auth/spotify-login").build());

        StepVerifier.create(controller.spotifyLogin(exchange))
                .verifyComplete();

        // Response must be a redirect to Spotify
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(FOUND);
        String location = exchange.getResponse().getHeaders().getFirst("Location");
        assertThat(location).startsWith("https://accounts.spotify.com/authorize");
        assertThat(location).contains("client_id=test-client-id");
        assertThat(location).contains("code_challenge_method=S256");
        assertThat(location).contains("redirect_uri=");

        // PKCE verifier must be stored in the session
        exchange.getSession().subscribe(session -> {
            boolean hasPkceEntry = session.getAttributes().keySet().stream()
                    .anyMatch(k -> k.startsWith("pkce_"));
            assertThat(hasPkceEntry).isTrue();
        });
    }

    // ── callback ─────────────────────────────────────────────────────────────

    @Test
    void `callback with missing PKCE state redirects to login with error`() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/web/auth/callback?code=abc&state=unknown").build());

        // No PKCE verifier stored in session → should redirect to /web/login?error=expired
        StepVerifier.create(controller.callback("abc", "unknown", exchange))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(FOUND);
        String location = exchange.getResponse().getHeaders().getFirst("Location");
        assertThat(location).isEqualTo("/web/login?error=expired");
    }

    @Test
    void `callback with valid PKCE state stores familyId in session and redirects to dashboard`() {
        when(spotifyConfig.getWebRedirectUri()).thenReturn("https://example.com/web/auth/callback");
        when(tokenService.exchangeCodeAndPersist("code123", "verifier", "https://example.com/web/auth/callback"))
                .thenReturn(reactor.core.publisher.Mono.just("family-id-001"));

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/web/auth/callback?code=code123&state=state-xyz").build());

        // Pre-populate PKCE verifier in session
        exchange.getSession().subscribe(session ->
                session.getAttributes().put("pkce_state-xyz", "verifier"));

        StepVerifier.create(controller.callback("code123", "state-xyz", exchange))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(FOUND);
        assertThat(exchange.getResponse().getHeaders().getFirst("Location")).isEqualTo("/web/dashboard");

        exchange.getSession().subscribe(session ->
                assertThat(session.getAttribute(WebSessionAuthFilter.SESSION_FAMILY_ID))
                        .isEqualTo("family-id-001"));
    }

    // ── logout ───────────────────────────────────────────────────────────────

    @Test
    void `logout invalidates session and redirects to login`() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/web/logout").build());

        // Populate session so there's something to invalidate
        exchange.getSession().subscribe(session ->
                session.getAttributes().put(WebSessionAuthFilter.SESSION_FAMILY_ID, "some-family"));

        StepVerifier.create(controller.logout(exchange))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(FOUND);
        assertThat(exchange.getResponse().getHeaders().getFirst("Location")).isEqualTo("/web/login");
    }
}