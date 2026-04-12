package at.kidstune.web.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E tests covering login, registration, and logout flows.
 */
@Tag("e2e")
class AuthE2eTest extends AbstractE2eTest {

    @Test
    @DisplayName("should show login page at /web/login")
    void shouldShowLoginPageAtWebLogin() {
        page.navigate(baseUrl() + "/web/login");

        assertThat(page.locator("input[name=email]").isVisible()).isTrue();
        assertThat(page.locator("input[name=password]").isVisible()).isTrue();
        assertThat(page.locator("button[type=submit]").isVisible()).isTrue();
    }

    @Test
    @DisplayName("should register a new account and redirect to dashboard")
    void shouldRegisterNewAccountAndRedirectToDashboard() {
        page.navigate(baseUrl() + "/web/register");
        page.fill("input[name=email]",           uniqueEmail());
        page.fill("input[name=password]",        "Password123!");
        page.fill("input[name=confirmPassword]", "Password123!");
        page.click("button[type=submit]");

        assertThat(page.url()).contains("/web/dashboard");
    }

    @Test
    @DisplayName("should show validation error for mismatched passwords")
    void shouldShowValidationErrorForMismatchedPasswords() {
        page.navigate(baseUrl() + "/web/register");
        page.fill("input[name=email]",           uniqueEmail());
        page.fill("input[name=password]",        "Password123!");
        page.fill("input[name=confirmPassword]", "DifferentPassword!");
        page.click("button[type=submit]");

        // Stays on register page and shows Bootstrap is-invalid feedback
        assertThat(page.url()).contains("/web/register");
        assertThat(page.locator(".is-invalid").count()).isGreaterThan(0);
    }

    @Test
    @DisplayName("should show error on login with bad credentials")
    void shouldShowErrorOnLoginWithBadCredentials() {
        page.navigate(baseUrl() + "/web/login");
        page.fill("input[name=email]",    "nobody@example.com");
        page.fill("input[name=password]", "wrongpassword");
        page.click("button[type=submit]");

        // Redirects back to /web/login?error=... and shows alert-danger
        assertThat(page.url()).contains("/web/login");
        assertThat(page.locator(".alert-danger").isVisible()).isTrue();
    }

    @Test
    @DisplayName("should show error on duplicate email registration")
    void shouldShowErrorOnDuplicateEmailRegistration() {
        // First registration succeeds
        String email = uniqueEmail();
        page.navigate(baseUrl() + "/web/register");
        page.fill("input[name=email]",           email);
        page.fill("input[name=password]",        "Password123!");
        page.fill("input[name=confirmPassword]", "Password123!");
        page.click("button[type=submit]");
        assertThat(page.url()).contains("/web/dashboard");

        // Second attempt with the same email — in a fresh context (no session)
        try (BrowserContext ctx2 = browser.newContext(new Browser.NewContextOptions().setBaseURL(baseUrl()));
             Page page2 = ctx2.newPage()) {

            page2.navigate(baseUrl() + "/web/register");
            page2.fill("input[name=email]",           email);
            page2.fill("input[name=password]",        "Password123!");
            page2.fill("input[name=confirmPassword]", "Password123!");
            page2.click("button[type=submit]");

            assertThat(page2.url()).contains("/web/register");
            assertThat(page2.locator(".is-invalid").count()).isGreaterThan(0);
        }
    }

    @Test
    @DisplayName("should logout and redirect to login page")
    void shouldLogoutAndRedirectToLogin() {
        registerAndGetEmail();

        // The layout sidebar contains the logout form (desktop view)
        page.locator(".sidebar form[action*='logout'] button[type=submit]").click();

        assertThat(page.url()).contains("/web/login");
        // Session is gone — navigating to dashboard bounces back to login
        page.navigate(baseUrl() + "/web/dashboard");
        assertThat(page.url()).contains("/web/login");
    }
}
