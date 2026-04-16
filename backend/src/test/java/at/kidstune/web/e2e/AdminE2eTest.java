package at.kidstune.web.e2e;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E smoke tests for all Admin pages.
 *
 * <p>Each test registers a fresh account and navigates to an admin route,
 * asserting that the page renders without a 5xx error. This catches template
 * processing errors (e.g. wrong SpEL accessor on a JPA entity) that only
 * surface at render time.
 */
@Tag("e2e")
class AdminE2eTest extends AbstractE2eTest {

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Registers, lands on the dashboard, then navigates to the given path. */
    private void registerAndNavigateTo(String path) {
        registerAndGetEmail();
        page.navigate(baseUrl() + path);
    }

    /** Asserts no 5xx error page is shown (checks that the page title doesn't contain "Error"). */
    private void assertNoServerError() {
        assertThat(page.title()).doesNotContainIgnoringCase("error");
        // Spring Boot default error page uses this heading
        assertThat(page.locator("h1").count() == 0 ||
                   !page.locator("h1").first().innerText().toLowerCase().contains("whitelabel"))
                .isTrue();
    }

    // ── families ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /web/admin/families — renders without error")
    void shouldRenderAdminFamilies() {
        registerAndNavigateTo("/web/admin/families");
        assertNoServerError();
        assertThat(page.url()).contains("/web/admin/families");
    }

    // ── profiles ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /web/admin/profiles (empty) — renders without error")
    void shouldRenderAdminProfilesEmpty() {
        registerAndNavigateTo("/web/admin/profiles");
        assertNoServerError();
        assertThat(page.url()).contains("/web/admin/profiles");
    }

    @Test
    @DisplayName("GET /web/admin/profiles — renders with a child profile present")
    void shouldRenderAdminProfilesWithData() {
        registerAndGetEmail();

        // Create a child profile so the table has at least one row to render
        page.navigate(baseUrl() + "/web/profiles/new");
        page.fill("input[name=name]", "AdminTestKid-" + System.currentTimeMillis());
        page.locator("label[for=icon-BEAR]").click();
        page.locator("label[for=color-RED]").click();
        page.selectOption("select[name=ageGroup]", new com.microsoft.playwright.options.SelectOption().setIndex(0));
        page.locator("button.btn-primary[type=submit]").click();
        assertThat(page.url()).contains("/web/profiles");

        // Now visit the admin profiles page — this is where the SpEL bug triggered
        page.navigate(baseUrl() + "/web/admin/profiles");
        assertNoServerError();
        assertThat(page.url()).contains("/web/admin/profiles");
        // The table should be present (not the empty-state placeholder)
        assertThat(page.locator("table").count()).isGreaterThan(0);
    }

    // ── content ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /web/admin/content — renders without error")
    void shouldRenderAdminContent() {
        registerAndNavigateTo("/web/admin/content");
        assertNoServerError();
        assertThat(page.url()).contains("/web/admin/content");
    }

    // ── resolved ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /web/admin/resolved — renders without error")
    void shouldRenderAdminResolved() {
        registerAndNavigateTo("/web/admin/resolved");
        assertNoServerError();
        assertThat(page.url()).contains("/web/admin/resolved");
    }

    // ── favorites ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /web/admin/favorites — renders without error")
    void shouldRenderAdminFavorites() {
        registerAndNavigateTo("/web/admin/favorites");
        assertNoServerError();
        assertThat(page.url()).contains("/web/admin/favorites");
    }

    // ── requests ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /web/admin/requests — renders without error")
    void shouldRenderAdminRequests() {
        registerAndNavigateTo("/web/admin/requests");
        assertNoServerError();
        assertThat(page.url()).contains("/web/admin/requests");
    }

    // ── devices ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /web/admin/devices — renders without error")
    void shouldRenderAdminDevices() {
        registerAndNavigateTo("/web/admin/devices");
        assertNoServerError();
        assertThat(page.url()).contains("/web/admin/devices");
    }

    // ── danger zone ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /web/admin/danger-zone — renders without error")
    void shouldRenderAdminDangerZone() {
        registerAndNavigateTo("/web/admin/danger-zone");
        assertNoServerError();
        assertThat(page.url()).contains("/web/admin/danger-zone");
    }
}
