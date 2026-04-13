package at.kidstune.web.e2e;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E tests for the Settings page, including the quick-approval PIN management card.
 */
@Tag("e2e")
class SettingsE2eTest extends AbstractE2eTest {

    // ── Page navigation ───────────────────────────────────────────────────────

    @Test
    @DisplayName("should show settings page with all three cards")
    void shouldShowSettingsPage() {
        registerAndGetEmail();
        page.navigate(baseUrl() + "/web/settings");

        assertThat(page.locator("h1").textContent()).contains("Einstellungen");
        assertThat(page.locator("h5:has-text('Benachrichtigungs-E-Mails')").isVisible()).isTrue();
        assertThat(page.locator("h5:has-text('Schnell-Freigabe PIN')").isVisible()).isTrue();
        assertThat(page.locator("h5:has-text('Spotify-Konto')").isVisible()).isTrue();
    }

    // ── PIN initially not configured ──────────────────────────────────────────

    @Test
    @DisplayName("PIN card shows 'Nicht eingerichtet' badge when no PIN is set")
    void shouldShowNichtEingerichtetWhenNoPinSet() {
        registerAndGetEmail();
        page.navigate(baseUrl() + "/web/settings");

        assertThat(page.locator(".badge:has-text('Nicht eingerichtet')").isVisible()).isTrue();
    }

    @Test
    @DisplayName("PIN card shows 'PIN festlegen' label on the submit button when no PIN is set")
    void shouldShowPinFestlegenButtonWhenNoPinSet() {
        registerAndGetEmail();
        page.navigate(baseUrl() + "/web/settings");

        assertThat(page.locator("button:has-text('PIN speichern')").isVisible()).isTrue();
        // "PIN entfernen" button must NOT appear yet
        assertThat(page.locator("button:has-text('PIN entfernen')").count()).isEqualTo(0);
    }

    // ── Setting a PIN ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("setting a valid PIN shows success message and Aktiv badge")
    void shouldSavePinSuccessfully() {
        registerAndGetEmail();
        page.navigate(baseUrl() + "/web/settings");

        page.fill("input[name=newPin]",     "1234");
        page.fill("input[name=confirmPin]", "1234");
        page.click("button:has-text('PIN speichern')");

        assertThat(page.locator(".alert-success:has-text('PIN erfolgreich gespeichert')").isVisible()).isTrue();
        assertThat(page.locator(".badge:has-text('Aktiv')").isVisible()).isTrue();
        assertThat(page.locator("button:has-text('PIN entfernen')").isVisible()).isTrue();
    }

    @Test
    @DisplayName("changing an existing PIN shows 'PIN ändern' button label")
    void shouldShowPinAendernAfterPinIsSet() {
        registerAndGetEmail();
        page.navigate(baseUrl() + "/web/settings");

        // Set initial PIN
        page.fill("input[name=newPin]",     "5678");
        page.fill("input[name=confirmPin]", "5678");
        page.click("button:has-text('PIN speichern')");

        // After save the button label should now read 'PIN ändern'
        assertThat(page.locator("button:has-text('PIN ändern')").isVisible()).isTrue();
    }

    // ── PIN validation errors ─────────────────────────────────────────────────

    @Test
    @DisplayName("mismatched PINs show an inline error message")
    void shouldShowErrorOnPinMismatch() {
        registerAndGetEmail();
        page.navigate(baseUrl() + "/web/settings");

        page.fill("input[name=newPin]",     "1234");
        page.fill("input[name=confirmPin]", "9999");
        page.click("button:has-text('PIN speichern')");

        assertThat(page.locator(".alert-danger:has-text('stimmen nicht überein')").isVisible()).isTrue();
        assertThat(page.locator(".badge:has-text('Nicht eingerichtet')").isVisible()).isTrue();
    }

    @Test
    @DisplayName("PIN with fewer than 4 digits shows an inline error message")
    void shouldShowErrorOnShortPin() {
        registerAndGetEmail();
        page.navigate(baseUrl() + "/web/settings");

        page.fill("input[name=newPin]",     "12");
        page.fill("input[name=confirmPin]", "12");
        // Bypass HTML5 pattern validation to test server-side error handling
        page.waitForNavigation(() ->
                page.evaluate("document.querySelector('form[action*=\"set-pin\"]').submit()"));

        assertThat(page.locator(".alert-danger").isVisible()).isTrue();
    }

    // ── Clearing the PIN ──────────────────────────────────────────────────────

    @Test
    @DisplayName("clearing the PIN shows 'Nicht eingerichtet' badge and info message")
    void shouldClearPinSuccessfully() {
        registerAndGetEmail();
        page.navigate(baseUrl() + "/web/settings");

        // Set PIN first
        page.fill("input[name=newPin]",     "4321");
        page.fill("input[name=confirmPin]", "4321");
        page.click("button:has-text('PIN speichern')");
        assertThat(page.locator(".badge:has-text('Aktiv')").isVisible()).isTrue();

        // Clear it — accept the confirm() dialog, then click
        page.onDialog(dialog -> dialog.accept());
        page.waitForNavigation(() ->
                page.locator("button:has-text('PIN entfernen')").click());

        assertThat(page.locator(".alert-info:has-text('PIN entfernt')").isVisible()).isTrue();
        assertThat(page.locator(".badge:has-text('Nicht eingerichtet')").isVisible()).isTrue();
        assertThat(page.locator("button:has-text('PIN entfernen')").count()).isEqualTo(0);
    }
}
