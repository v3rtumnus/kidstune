package at.kidstune.web.e2e;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E tests covering the main dashboard and navigation layout.
 */
@Tag("e2e")
class DashboardE2eTest extends AbstractE2eTest {

    @Test
    @DisplayName("should show dashboard with nav links after login")
    void shouldShowDashboardAfterLogin() {
        registerAndGetEmail();

        assertThat(page.title()).contains("KidsTune");
        // Sidebar nav links (desktop layout)
        assertThat(page.locator(".sidebar a[href*='/web/profiles']").isVisible()).isTrue();
        assertThat(page.locator(".sidebar a[href*='/web/requests']").isVisible()).isTrue();
        assertThat(page.locator(".sidebar a[href*='/web/settings']").isVisible()).isTrue();
    }

    @Test
    @DisplayName("should redirect unauthenticated users from dashboard to login")
    void shouldRedirectToLoginWhenUnauthenticated() {
        // No login — navigate directly to the protected dashboard
        page.navigate(baseUrl() + "/web/dashboard");

        assertThat(page.url()).contains("/web/login");
    }

    @Test
    @DisplayName("should show requests badge link in sidebar")
    void shouldShowRequestsBadgeLink() {
        registerAndGetEmail();

        // The "Anfragen" (Requests) nav link is always present in the sidebar
        assertThat(page.locator(".sidebar a[href*='/web/requests']").isVisible()).isTrue();
    }

    @Test
    @DisplayName("should show logout button in sidebar")
    void shouldShowLogoutButtonInSidebar() {
        registerAndGetEmail();

        assertThat(page.locator(".sidebar form[action*='logout'] button[type=submit]").isVisible()).isTrue();
    }
}
