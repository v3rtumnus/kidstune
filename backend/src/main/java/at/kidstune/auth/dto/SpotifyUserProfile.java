package at.kidstune.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Internal DTO mapping Spotify's GET /v1/me response (identity only). */
public record SpotifyUserProfile(
        @JsonProperty("id")           String id,
        @JsonProperty("display_name") String displayName
) {}
