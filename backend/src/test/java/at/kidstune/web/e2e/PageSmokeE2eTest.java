package at.kidstune.web.e2e;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.SelectOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke tests that hit every GET page in the web dashboard and assert no 5xx
 * is returned. This catches template rendering errors (wrong SpEL accessors,
 * missing model attributes, etc.) before they reach production.
 *
 * Pages requiring data (e.g. profile edit) create the data first.
 * HTMX partials and OAuth callbacks are excluded — they require special
 * state that can't be set up generically.
 */
@Tag("e2e")
class PageSmokeE2eTest extends AbstractE2eTest {

    // ── helpers ───────────────────────────────────────────────────────────────

    private void assertNoServerError(String url) {
        String title = page.title().toLowerCase();
        assertThat(title)
                .as("Expected no server error on " + url)
                .doesNotContain("error");
        // Spring Boot Whitelabel error page
        assertThat(page.content())
                .as("Expected no Whitelabel error on " + url)
                .doesNotContain("Whitelabel Error Page");
    }

    /** Creates a child profile and returns its edit-page URL segment (profile id from URL). */
    private String createProfileAndGetId() {
        page.navigate(baseUrl() + "/web/profiles/new");
        page.fill("input[name=name]", "Smoke-" + System.currentTimeMillis());
        page.locator("label[for=icon-BEAR]").click();
        page.locator("label[for=color-RED]").click();
        page.selectOption("select[name=ageGroup]", new SelectOption().setIndex(0));
        page.locator("button.btn-primary[type=submit]").click();
        assertThat(page.url()).contains("/web/profiles");
        // Extract first profile's edit link
        String editHref = page.locator("a[href*='/edit']").first().getAttribute("href");
        // href is like /web/profiles/{id}/edit
        String[] parts = editHref.split("/");
        return parts[parts.length - 2]; // id segment
    }

    // ── public pages (no auth) ────────────────────────────────────────────────

    @Test
    @DisplayName("GET /web/login — renders without error")
    void loginPage() {
        page.navigate(baseUrl() + "/web/login");
        assertNoServerError("/web/login");
        assertThat(page.locator("input[name=email]").isVisible()).isTrue();
    }

    @Test
    @DisplayName("GET /web/register — renders without error")
    void registerPage() {
        page.navigate(baseUrl() + "/web/register");
        assertNoServerError("/web/register");
        assertThat(page.locator("input[name=email]").isVisible()).isTrue();
    }

    // ── authenticated pages ───────────────────────────────────────────────────

    @Test
    @DisplayName("GET /web/dashboard — renders without error")
    void dashboardPage() {
        registerAndGetEmail();
        assertNoServerError("/web/dashboard");
    }

    @Test
    @DisplayName("GET /web/profiles — renders without error")
    void profilesPage() {
        registerAndGetEmail();
        page.navigate(baseUrl() + "/web/profiles");
        assertNoServerError("/web/profiles");
    }

    @Test
    @DisplayName("GET /web/profiles/new — renders without error")
    void profilesNewPage() {
        registerAndGetEmail();
        page.navigate(baseUrl() + "/web/profiles/new");
        assertNoServerError("/web/profiles/new");
        assertThat(page.locator("input[name=name]").isVisible()).isTrue();
    }

    @Test
    @DisplayName("GET /web/profiles/{id}/edit — renders without error")
    void profileEditPage() {
        registerAndGetEmail();
        String profileId = createProfileAndGetId();
        page.navigate(baseUrl() + "/web/profiles/" + profileId + "/edit");
        assertNoServerError("/web/profiles/" + profileId + "/edit");
        assertThat(page.locator("input[name=name]").isVisible()).isTrue();
    }

    @Test
    @DisplayName("GET /web/requests — renders without error")
    void requestsPage() {
        registerAndGetEmail();
        page.navigate(baseUrl() + "/web/requests");
        assertNoServerError("/web/requests");
    }

    @Test
    @DisplayName("GET /web/import — renders without error")
    void importPage() {
        registerAndGetEmail();
        page.navigate(baseUrl() + "/web/import");
        assertNoServerError("/web/import");
    }

    @Test
    @DisplayName("GET /web/devices — renders without error")
    void devicesPage() {
        registerAndGetEmail();
        page.navigate(baseUrl() + "/web/devices");
        assertNoServerError("/web/devices");
        assertThat(page.url()).contains("/web/devices");
    }

    @Test
    @DisplayName("GET /web/settings — renders without error")
    void settingsPage() {
        registerAndGetEmail();
        page.navigate(baseUrl() + "/web/settings");
        assertNoServerError("/web/settings");
    }

    @Test
    @DisplayName("GET /web/profiles/{id}/content — renders without error")
    void contentPage() {
        registerAndGetEmail();
        String profileId = createProfileAndGetId();
        page.navigate(baseUrl() + "/web/profiles/" + profileId + "/content");
        assertNoServerError("/web/profiles/" + profileId + "/content");
    }

    @Test
    @DisplayName("GET /web/profiles/{id}/content/add — renders without error")
    void contentAddPage() {
        registerAndGetEmail();
        String profileId = createProfileAndGetId();
        page.navigate(baseUrl() + "/web/profiles/" + profileId + "/content/add");
        assertNoServerError("/web/profiles/" + profileId + "/content/add");
    }

    // ── admin pages ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /web/admin/families — renders without error")
    void adminFamiliesPage() {
        registerAndGetEmail();
        page.navigate(baseUrl() + "/web/admin/families");
        assertNoServerError("/web/admin/families");
    }

    @Test
    @DisplayName("GET /web/admin/profiles (empty) — renders without error")
    void adminProfilesEmptyPage() {
        registerAndGetEmail();
        page.navigate(baseUrl() + "/web/admin/profiles");
        assertNoServerError("/web/admin/profiles");
    }

    @Test
    @DisplayName("GET /web/admin/profiles — renders with data without error")
    void adminProfilesWithDataPage() {
        registerAndGetEmail();
        createProfileAndGetId();
        page.navigate(baseUrl() + "/web/admin/profiles");
        assertNoServerError("/web/admin/profiles");
    }

    @Test
    @DisplayName("GET /web/admin/profiles/{id}/edit — renders without error")
    void adminProfileEditPage() {
        registerAndGetEmail();
        String profileId = createProfileAndGetId();
        page.navigate(baseUrl() + "/web/admin/profiles/" + profileId + "/edit");
        assertNoServerError("/web/admin/profiles/" + profileId + "/edit");
    }

    @Test
    @DisplayName("GET /web/admin/content — renders without error")
    void adminContentPage() {
        registerAndGetEmail();
        page.navigate(baseUrl() + "/web/admin/content");
        assertNoServerError("/web/admin/content");
    }

    @Test
    @DisplayName("GET /web/admin/resolved — renders without error")
    void adminResolvedPage() {
        registerAndGetEmail();
        page.navigate(baseUrl() + "/web/admin/resolved");
        assertNoServerError("/web/admin/resolved");
    }

    @Test
    @DisplayName("GET /web/admin/favorites — renders without error")
    void adminFavoritesPage() {
        registerAndGetEmail();
        page.navigate(baseUrl() + "/web/admin/favorites");
        assertNoServerError("/web/admin/favorites");
    }

    @Test
    @DisplayName("GET /web/admin/requests — renders without error")
    void adminRequestsPage() {
        registerAndGetEmail();
        page.navigate(baseUrl() + "/web/admin/requests");
        assertNoServerError("/web/admin/requests");
    }

    @Test
    @DisplayName("GET /web/admin/devices — renders without error")
    void adminDevicesPage() {
        registerAndGetEmail();
        page.navigate(baseUrl() + "/web/admin/devices");
        assertNoServerError("/web/admin/devices");
    }

    @Test
    @DisplayName("GET /web/admin/danger-zone — renders without error")
    void adminDangerZonePage() {
        registerAndGetEmail();
        page.navigate(baseUrl() + "/web/admin/danger-zone");
        assertNoServerError("/web/admin/danger-zone");
    }
}
