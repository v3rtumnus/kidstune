package at.kidstune.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record BindProfileRequest(
        @NotBlank String profileId
) {}
