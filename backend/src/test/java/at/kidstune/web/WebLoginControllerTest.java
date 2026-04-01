package at.kidstune.web;

import at.kidstune.family.FamilyService;
import at.kidstune.family.InvalidCredentialsException;
import org.junit.jupiter.api.DisplayName;
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

    @Mock FamilyService             familyService;
    @Mock RememberMeTokenRepository rememberMeTokenRepository;
    @Mock RememberMeWebFilter       rememberMeWebFilter;

    @InjectMocks
    WebLoginController controller;

    // ── loginPage ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("loginPage returns web/login view name")
    void loginPageReturnsViewName() {
        org.springframework.ui.Model model =
                new org.springframework.ui.ExtendedModelMap();
        StepVerifier.create(controller.loginPage(null, model))
                .expectNext("web/login")
                .verifyComplete();
    }

    @Test
    @DisplayName("loginPage with error param adds errorMessage attribute")
    void loginPageWithErrorParamAddsErrorMessage() {
        org.springframework.ui.Model model =
                new org.springframework.ui.ExtendedModelMap();
        StepVerifier.create(controller.loginPage("credentials", model))
                .expectNext("web/login")
                .verifyComplete();
        assertThat(model.asMap()).containsKey("errorMessage");
    }

    // ── processLogin ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("processLogin with valid credentials sets familyId in session and redirects to dashboard")
    void processLoginValidCredentialsSetsSessionAndRedirects() {
        when(familyService.authenticate("user@example.com", "secret123"))
                .thenReturn("family-001");

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/web/login").build());

        StepVerifier.create(controller.processLogin(
                        "user@example.com", "secret123", false, exchange))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(FOUND);
        assertThat(exchange.getResponse().getHeaders().getFirst("Location"))
                .isEqualTo("/web/dashboard");

        exchange.getSession().subscribe(session -> {
            String familyId = session.getAttribute(WebSessionSecurityContextRepository.SESSION_FAMILY_ID);
            assertThat(familyId).isEqualTo("family-001");
        });
    }

    @Test
    @DisplayName("processLogin with invalid credentials redirects to login with error param")
    void processLoginInvalidCredentialsRedirectsWithError() {
        when(familyService.authenticate("bad@example.com", "wrong"))
                .thenThrow(new InvalidCredentialsException());

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/web/login").build());

        StepVerifier.create(controller.processLogin(
                        "bad@example.com", "wrong", false, exchange))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(FOUND);
        String location = exchange.getResponse().getHeaders().getFirst("Location");
        assertThat(location).startsWith("/web/login");
        assertThat(location).contains("error");
    }

    @Test
    @DisplayName("processLogin redirects to originally requested URL saved in session")
    void processLoginRedirectsToSavedUrl() {
        when(familyService.authenticate("user@example.com", "pass1234"))
                .thenReturn("family-002");

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/web/login").build());

        // Simulate entry point having saved the original URL
        exchange.getSession().subscribe(session ->
                session.getAttributes().put(WebLoginController.SESSION_LOGIN_REDIRECT, "/web/settings"));

        StepVerifier.create(controller.processLogin(
                        "user@example.com", "pass1234", false, exchange))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(FOUND);
        assertThat(exchange.getResponse().getHeaders().getFirst("Location"))
                .isEqualTo("/web/settings");
    }

    // ── registerPage ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("registerPage returns web/register view name")
    void registerPageReturnsViewName() {
        org.springframework.ui.Model model =
                new org.springframework.ui.ExtendedModelMap();
        StepVerifier.create(controller.registerPage(model))
                .expectNext("web/register")
                .verifyComplete();
    }

    // ── processRegister ───────────────────────────────────────────────────────

    @Test
    @DisplayName("processRegister with mismatched passwords re-renders form with error")
    void processRegisterMismatchedPasswordsReRendersForm() {
        org.springframework.ui.Model model =
                new org.springframework.ui.ExtendedModelMap();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/web/register").build());

        StepVerifier.create(controller.processRegister(
                        "a@b.de", "password1", "password2", model, exchange))
                .expectNext("web/register")
                .verifyComplete();

        assertThat(model.asMap()).containsKey("passwordError");
    }

    @Test
    @DisplayName("processRegister with short password re-renders form with error")
    void processRegisterShortPasswordReRendersForm() {
        org.springframework.ui.Model model =
                new org.springframework.ui.ExtendedModelMap();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/web/register").build());

        StepVerifier.create(controller.processRegister(
                        "a@b.de", "short", "short", model, exchange))
                .expectNext("web/register")
                .verifyComplete();

        assertThat(model.asMap()).containsKey("passwordError");
    }

    // ── logout ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("logout invalidates session and redirects to login")
    void logoutInvalidatesSessionAndRedirects() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/web/logout").build());

        exchange.getSession().subscribe(session ->
                session.getAttributes().put(
                        WebSessionSecurityContextRepository.SESSION_FAMILY_ID, "family-abc"));

        StepVerifier.create(controller.logout(exchange))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(FOUND);
        assertThat(exchange.getResponse().getHeaders().getFirst("Location"))
                .isEqualTo("/web/login");
    }
}