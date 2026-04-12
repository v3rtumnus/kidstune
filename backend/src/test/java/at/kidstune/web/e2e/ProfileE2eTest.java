package at.kidstune.web.e2e;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E tests covering child-profile creation and deletion.
 */
@Tag("e2e")
class ProfileE2eTest extends AbstractE2eTest {

    /**
     * Creates a new child profile via the web UI and returns the name used.
     * Leaves the browser on the profiles list page after creation.
     */
    private String createProfile() {
        String name = "TestKind-" + System.currentTimeMillis();

        page.navigate(baseUrl() + "/web/profiles/new");

        // Name
        page.fill("input[name=name]", name);

        // Avatar icon — click the label for the first radio (BEAR)
        page.locator("label[for=icon-BEAR]").click();

        // Avatar color — click the label for the first radio (RED)
        page.locator("label[for=color-RED]").click();

        // Age group — select the first option
        page.selectOption("select[name=ageGroup]", new com.microsoft.playwright.options.SelectOption().setIndex(0));

        // Use btn-primary class to target the profile-form submit, not the sidebar logout button
        page.locator("button.btn-primary[type=submit]").click();

        // After successful creation, controller redirects to /web/profiles
        assertThat(page.url()).contains("/web/profiles");

        return name;
    }

    @Test
    @DisplayName("should create a profile and see it in the profile list")
    void shouldCreateProfileAndSeeItInList() {
        registerAndGetEmail();

        String name = createProfile();

        // The profile name appears in the list
        assertThat(page.locator("h5.text-truncate", new com.microsoft.playwright.Page.LocatorOptions()
                .setHasText(name)).count()).isGreaterThan(0);
    }

    @Test
    @DisplayName("should delete a profile and it disappears from the list")
    void shouldDeleteProfile() {
        registerAndGetEmail();

        String name = createProfile();

        // Navigate to the profile's edit page to reach the delete button.
        // Find the "Bearbeiten" (Edit) link for our profile card.
        page.locator(".card", new com.microsoft.playwright.Page.LocatorOptions().setHasText(name))
                .locator("a[href*='/edit']")
                .click();

        assertThat(page.url()).contains("/edit");

        // Click the delete button (inside the Danger Zone form) — handle the confirm dialog
        page.onDialog(dialog -> dialog.accept());
        page.locator("form[action*='/delete'] button[type=submit]").click();

        // After deletion, redirected back to profiles list
        assertThat(page.url()).contains("/web/profiles");

        // Profile name is no longer present
        assertThat(page.locator("h5.text-truncate", new com.microsoft.playwright.Page.LocatorOptions()
                .setHasText(name)).count()).isEqualTo(0);
    }
}
