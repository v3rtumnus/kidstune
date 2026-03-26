package at.kidstune.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Returned to the Parent App after a successful OAuth callback. */
public record CallbackResponse(
        @JsonProperty("familyId")     String familyId,
        @JsonProperty("accessToken")  String accessToken,
        @JsonProperty("expiresIn")    int    expiresIn
) {}
