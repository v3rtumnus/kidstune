package at.kidstune.web;

import at.kidstune.family.DuplicateEmailException;
import at.kidstune.family.FamilyService;
import at.kidstune.family.InvalidCredentialsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.time.Instant;

@Controller
@RequestMapping("/web")
public class WebLoginController {

    private static final Logger log = LoggerFactory.getLogger(WebLoginController.class);

    /** Session attribute that stores the URL the user originally requested before being redirected to login. */
    public static final String SESSION_LOGIN_REDIRECT = "loginRedirect";

    private final FamilyService             familyService;
    private final RememberMeTokenRepository rememberMeTokenRepository;
    private final RememberMeWebFilter       rememberMeWebFilter;

    public WebLoginController(FamilyService familyService,
                              RememberMeTokenRepository rememberMeTokenRepository,
                              RememberMeWebFilter rememberMeWebFilter) {
        this.familyService             = familyService;
        this.rememberMeTokenRepository = rememberMeTokenRepository;
        this.rememberMeWebFilter       = rememberMeWebFilter;
    }

    // ── GET /web/login ───────────────────────────────────────────────────────

    @GetMapping("/login")
    public Mono<String> loginPage(
            @RequestParam(value = "error", required = false) String error,
            Model model) {
        if (error != null) {
            model.addAttribute("errorMessage", "E-Mail oder Passwort falsch");
        }
        return Mono.just("web/login");
    }

    // ── POST /web/login ──────────────────────────────────────────────────────

    @PostMapping("/login")
    public Mono<Void> processLogin(ServerWebExchange exchange) {
        return exchange.getFormData().flatMap(form -> {
            String  email      = form.getFirst("email");
            String  password   = form.getFirst("password");
            boolean rememberMe = "true".equalsIgnoreCase(form.getFirst("rememberMe"));

            return Mono.fromCallable(() -> familyService.authenticate(email, password))
                    .subscribeOn(Schedulers.boundedElastic())
                    .flatMap(familyId -> exchange.getSession().flatMap(session -> {
                        session.getAttributes().put(WebSessionSecurityContextRepository.SESSION_FAMILY_ID, familyId);

                        if (rememberMe) {
                            return issueRememberMeCookie(exchange, familyId);
                        }
                        return Mono.empty();
                    }))
                    .then(redirectAfterLogin(exchange))
                    .onErrorResume(InvalidCredentialsException.class, e ->
                            renderLoginError(exchange, "E-Mail oder Passwort falsch"));
        });
    }

    // ── GET /web/register ────────────────────────────────────────────────────

    @GetMapping("/register")
    public Mono<String> registerPage(Model model) {
        model.addAttribute("registerForm", new RegisterForm());
        return Mono.just("web/register");
    }

    // ── POST /web/register ───────────────────────────────────────────────────

    @PostMapping("/register")
    public Mono<Object> processRegister(Model model, ServerWebExchange exchange) {
        return exchange.getFormData().flatMap(form -> {
            String email           = form.getFirst("email");
            String password        = form.getFirst("password");
            String confirmPassword = form.getFirst("confirmPassword");

            // ── Validate input ────────────────────────────────────────────────────
            String emailError    = validateEmail(email);
            String passwordError = validatePassword(password, confirmPassword);

            if (emailError != null || passwordError != null) {
                model.addAttribute("email", email);
                model.addAttribute("emailError", emailError);
                model.addAttribute("passwordError", passwordError);
                return Mono.just((Object) "web/register");
            }

            final String finalEmail    = email;
            final String finalPassword = password;
            return Mono.fromCallable(() -> familyService.register(finalEmail, finalPassword))
                    .subscribeOn(Schedulers.boundedElastic())
                    .flatMap(familyId -> exchange.getSession().flatMap(session -> {
                        session.getAttributes().put(WebSessionSecurityContextRepository.SESSION_FAMILY_ID, familyId);
                        exchange.getResponse().setStatusCode(HttpStatus.FOUND);
                        exchange.getResponse().getHeaders().setLocation(URI.create("/web/dashboard"));
                        return exchange.getResponse().setComplete();
                    }))
                    .cast(Object.class)
                    .onErrorResume(DuplicateEmailException.class, e -> {
                        model.addAttribute("email", finalEmail);
                        model.addAttribute("emailError", "E-Mail bereits vergeben");
                        return Mono.just("web/register");
                    });
        });
    }

    // ── POST /web/logout ─────────────────────────────────────────────────────

    @PostMapping("/logout")
    public Mono<Void> logout(ServerWebExchange exchange) {
        return exchange.getSession().flatMap(session -> {
            String familyId = session.getAttribute(WebSessionSecurityContextRepository.SESSION_FAMILY_ID);

            Mono<Void> deleteTokens = Mono.empty();
            if (familyId != null) {
                deleteTokens = Mono.fromRunnable(() -> rememberMeTokenRepository.deleteByFamilyId(familyId))
                        .subscribeOn(Schedulers.boundedElastic())
                        .then();
            }

            // Expire the remember_me cookie
            exchange.getResponse().addCookie(
                    org.springframework.http.ResponseCookie.from(RememberMeWebFilter.COOKIE_NAME, "")
                            .httpOnly(true)
                            .path("/")
                            .maxAge(java.time.Duration.ZERO)
                            .sameSite("Strict")
                            .build()
            );

            return deleteTokens
                    .then(session.invalidate())
                    .then(Mono.fromRunnable(() -> {
                        exchange.getResponse().setStatusCode(HttpStatus.FOUND);
                        exchange.getResponse().getHeaders().setLocation(URI.create("/web/login"));
                    }))
                    .then(exchange.getResponse().setComplete());
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Mono<Void> issueRememberMeCookie(ServerWebExchange exchange, String familyId) {
        String series   = RememberMeWebFilter.generateToken();
        String rawToken = RememberMeWebFilter.generateToken();
        String hash     = RememberMeWebFilter.sha256Hex(rawToken);
        Instant now     = Instant.now();

        RememberMeToken token = new RememberMeToken();
        token.setSeries(series);
        token.setTokenHash(hash);
        token.setFamilyId(familyId);
        token.setCreatedAt(now);
        token.setExpiresAt(now.plus(RememberMeWebFilter.COOKIE_TTL));

        return Mono.fromRunnable(() -> rememberMeTokenRepository.save(token))
                .subscribeOn(Schedulers.boundedElastic())
                .then(Mono.fromRunnable(() -> rememberMeWebFilter.setCookie(exchange, series, rawToken)));
    }

    private Mono<Void> redirectAfterLogin(ServerWebExchange exchange) {
        return exchange.getSession().flatMap(session -> {
            String redirect = session.getAttribute(SESSION_LOGIN_REDIRECT);
            if (redirect != null) {
                session.getAttributes().remove(SESSION_LOGIN_REDIRECT);
            }
            String target = (redirect != null && !redirect.startsWith("/web/login")) ? redirect : "/web/dashboard";
            exchange.getResponse().setStatusCode(HttpStatus.FOUND);
            exchange.getResponse().getHeaders().setLocation(URI.create(target));
            return exchange.getResponse().setComplete();
        });
    }

    private Mono<Void> renderLoginError(ServerWebExchange exchange, String errorMessage) {
        // Render the login page with an error by forwarding to Thymeleaf.
        // Since we're in a void-returning handler (no Model injection), we redirect
        // with a query param that the template reads.
        exchange.getResponse().setStatusCode(HttpStatus.FOUND);
        exchange.getResponse().getHeaders().setLocation(URI.create("/web/login?error=credentials"));
        return exchange.getResponse().setComplete();
    }

    // ── Validation helpers ────────────────────────────────────────────────────

    private String validateEmail(String email) {
        if (email == null || email.isBlank()) return "E-Mail-Adresse ist erforderlich";
        if (!email.contains("@") || !email.contains(".")) return "Keine gültige E-Mail-Adresse";
        return null;
    }

    private String validatePassword(String password, String confirmPassword) {
        if (password == null || password.length() < 8) return "Passwort muss mindestens 8 Zeichen lang sein";
        if (!password.equals(confirmPassword))          return "Passwörter stimmen nicht überein";
        return null;
    }

    // ── Inner form beans (used as model attributes) ───────────────────────────

    public static class LoginForm {}
    public static class RegisterForm {}
}