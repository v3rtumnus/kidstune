package at.kidstune.push;

import tools.jackson.databind.ObjectMapper;
import nl.martijndwars.webpush.PushService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link PushNotificationService#buildPayload}.
 *
 * Verifies that Jackson serialises the payload correctly – especially with
 * characters that are dangerous when concatenated into raw JSON strings:
 * backslash, double-quote, control characters, and Unicode.
 */
class PushNotificationServiceTest {

    private PushNotificationService service;

    @BeforeEach
    void setUp() {
        service = new PushNotificationService(
                null, null, mock(PushService.class), new ObjectMapper());
        ReflectionTestUtils.setField(service, "baseUrl", "https://kidstune.test");
    }

    @Test
    void buildPayload_produces_valid_json() {
        String json = service.buildPayload("Anna", "Bibi und Tina");

        assertThatCode(() -> new ObjectMapper().readTree(json)).doesNotThrowAnyException();
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildPayload_contains_expected_fields() throws Exception {
        String json = service.buildPayload("Anna", "Bibi und Tina");

        Map<String, String> map = new ObjectMapper().readValue(json, Map.class);
        assertThat(map.get("title")).isEqualTo("Neuer Musikwunsch");
        assertThat(map.get("body")).contains("Anna").contains("Bibi und Tina");
        assertThat(map.get("url")).isEqualTo("https://kidstune.test/web/requests");
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildPayload_escapes_double_quotes_in_title() throws Exception {
        String json = service.buildPayload("Anna", "Song with \"quotes\"");

        assertThatCode(() -> new ObjectMapper().readTree(json)).doesNotThrowAnyException();
        Map<String, String> map = new ObjectMapper().readValue(json, Map.class);
        assertThat(map.get("body")).contains("Song with \"quotes\"");
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildPayload_escapes_backslash_in_title() throws Exception {
        String json = service.buildPayload("Anna", "Path\\To\\Song");

        assertThatCode(() -> new ObjectMapper().readTree(json)).doesNotThrowAnyException();
        Map<String, String> map = new ObjectMapper().readValue(json, Map.class);
        assertThat(map.get("body")).contains("Path\\To\\Song");
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildPayload_handles_null_child_name_gracefully() throws Exception {
        String json = service.buildPayload(null, "Some Song");

        assertThatCode(() -> new ObjectMapper().readTree(json)).doesNotThrowAnyException();
        Map<String, String> map = new ObjectMapper().readValue(json, Map.class);
        assertThat(map.get("body")).contains("Ein Kind");
    }

    @Test
    void buildPayload_handles_null_title_gracefully() {
        String json = service.buildPayload("Anna", null);

        assertThatCode(() -> new ObjectMapper().readTree(json)).doesNotThrowAnyException();
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildPayload_handles_unicode_characters() throws Exception {
        String json = service.buildPayload("Äächen", "Österreich \uD83C\uDFB5");

        assertThatCode(() -> new ObjectMapper().readTree(json)).doesNotThrowAnyException();
        Map<String, String> map = new ObjectMapper().readValue(json, Map.class);
        assertThat(map.get("body")).contains("Äächen").contains("Österreich \uD83C\uDFB5");
    }
}
