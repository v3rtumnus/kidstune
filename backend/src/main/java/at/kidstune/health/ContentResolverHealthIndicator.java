package at.kidstune.health;

import at.kidstune.resolver.ContentResolver;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Actuator endpoint exposing active content resolution job count.
 * Accessible at {@code GET /actuator/contentResolver}.
 */
@Component
@Endpoint(id = "contentResolver")
public class ContentResolverHealthIndicator {

    private final ContentResolver contentResolver;

    public ContentResolverHealthIndicator(ContentResolver contentResolver) {
        this.contentResolver = contentResolver;
    }

    @ReadOperation
    public Map<String, Object> status() {
        return Map.of(
            "status",     "UP",
            "activeJobs", contentResolver.getActiveJobCount()
        );
    }
}
