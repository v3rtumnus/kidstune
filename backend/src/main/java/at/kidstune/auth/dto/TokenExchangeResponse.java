package at.kidstune.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Internal DTO mapping Spotify's /api/token response. */
public record TokenExchangeResponse(
        @JsonProperty("access_token")  String accessToken,
        @JsonProperty("token_type")    String tokenType,
        @JsonProperty("expires_in")    int    expiresIn,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("scope")         String scope
) {}
