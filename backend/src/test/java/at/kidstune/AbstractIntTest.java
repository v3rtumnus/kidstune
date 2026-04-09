package at.kidstune;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MariaDBContainer;

/**
 * Base class for all {@code @SpringBootTest} integration tests.
 *
 * <p>Uses the <em>singleton container</em> pattern: the MariaDB container is
 * started once in a {@code static} initializer and lives for the entire JVM
 * lifetime (Testcontainers registers a JVM shutdown hook automatically).
 * The container is intentionally <strong>not</strong> annotated with
 * {@code @Container}, so the Testcontainers JUnit 5 extension never stops it
 * between test classes.  This means all 19 integration-test classes share a
 * single container instead of each starting their own, which is the primary
 * driver of the suite-time reduction.
 *
 * <p>The common {@link DynamicPropertySource} registers the datasource URL
 * (read from the running container) plus the four shared test properties.
 * Tests that also mock the Spotify API via MockWebServer declare their own
 * {@code @DynamicPropertySource} for the URL-specific overrides
 * ({@code spotify.api-base-url}, etc.); Spring discovers and merges all
 * {@code @DynamicPropertySource} methods from the full class hierarchy.
 */
public abstract class AbstractIntTest {

    protected static final MariaDBContainer<?> mariadb =
            new MariaDBContainer<>("mariadb:11")
                    .withDatabaseName("kidstune")
                    .withUsername("kidstune")
                    .withPassword("kidstune");

    static {
        mariadb.start();
    }

    @DynamicPropertySource
    static void overrideCommonProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      mariadb::getJdbcUrl);
        registry.add("spring.datasource.username", mariadb::getUsername);
        registry.add("spring.datasource.password", mariadb::getPassword);
        registry.add("spotify.client-id",     () -> "test-client-id");
        registry.add("spotify.client-secret", () -> "test-client-secret");
        registry.add("kidstune.jwt-secret",   () -> "test-jwt-secret-32-characters-!!");
        registry.add("kidstune.base-url",     () -> "http://localhost");
    }
}
