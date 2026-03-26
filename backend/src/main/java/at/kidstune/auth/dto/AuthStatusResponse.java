package at.kidstune.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthStatusResponse(
        @JsonProperty("authenticated")   boolean authenticated,
        @JsonProperty("spotifyConnected") boolean spotifyConnected,
        @JsonProperty("familyId")         String  familyId,
        @JsonProperty("tokenExpiresAt")   Instant tokenExpiresAt
) {
    public static AuthStatusResponse unauthenticated() {
        return new AuthStatusResponse(false, false, null, null);
    }
}
