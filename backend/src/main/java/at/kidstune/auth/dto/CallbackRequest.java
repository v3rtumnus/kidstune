package at.kidstune.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Body of POST /api/v1/auth/spotify/callback sent by the Parent App
 * after receiving the auth code from Spotify's redirect.
 */
public record CallbackRequest(
        @JsonProperty("code")  String code,
        @JsonProperty("state") String state
) {}
