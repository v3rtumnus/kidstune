package at.kidstune.web.e2e;

import at.kidstune.AbstractIntTest;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base class for Playwright E2E tests.
 *
 * <p>Inherits from {@link AbstractIntTest} to reuse the shared singleton MariaDB
 * Testcontainers container — no extra database infrastructure needed.
 *
 * <p>A single Chromium browser process is shared across all test classes (started
 * {@code @BeforeAll}, closed {@code @AfterAll}).  Each test method gets its own
 * {@link BrowserContext} + {@link Page} so sessions never bleed between tests.
 *
 * <p>Spring Boot starts on a random port; {@link #baseUrl()} provides the full
 * base URL.  All tests use {@link #uniqueEmail()} so they are fully independent
 * and can run in any order without duplicate-email conflicts.
 */
@Tag("e2e")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractE2eTest extends AbstractIntTest {

    @LocalServerPort
    protected int port;

    // ── Shared browser (one per JVM) ──────────────────────────────────────────

    protected static Playwright playwright;
    protected static Browser    browser;

    @BeforeAll
    static void launchBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions().setHeadless(true));
    }

    @AfterAll
    static void closeBrowser() {
        if (playwright != null) {
            playwright.close();         // also closes the browser
        }
    }

    // ── Per-test context + page (fresh session each time) ─────────────────────

    protected BrowserContext context;
    protected Page           page;

    @BeforeEach
    void createContextAndPage() {
        context = browser.newContext(new Browser.NewContextOptions()
                .setBaseURL(baseUrl()));
        page = context.newPage();
    }

    @AfterEach
    void closeContext() {
        context.close();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    protected String baseUrl() {
        return "http://localhost:" + port;
    }

    /** Returns a unique e-mail address for each call — prevents duplicate-email failures. */
    protected String uniqueEmail() {
        return "e2e-" + System.currentTimeMillis() + "@example.com";
    }

    /**
     * Registers a new account and lands on the dashboard.
     * Returns the e-mail that was used so callers can log in again if needed.
     */
    protected String registerAndGetEmail() {
        String email = uniqueEmail();
        page.navigate(baseUrl() + "/web/register");
        page.fill("input[name=email]",           email);
        page.fill("input[name=password]",        "Password123!");
        page.fill("input[name=confirmPassword]", "Password123!");
        page.click("button[type=submit]");
        assertThat(page.url()).contains("/web/dashboard");
        return email;
    }

    /** Navigates to /web/login and submits the given credentials. */
    protected void loginAs(String email, String password) {
        page.navigate(baseUrl() + "/web/login");
        page.fill("input[name=email]",    email);
        page.fill("input[name=password]", password);
        page.click("button[type=submit]");
    }
}
