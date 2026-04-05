package at.kidstune.device.dto;

import jakarta.validation.constraints.NotBlank;

public record ReassignProfileRequest(@NotBlank String profileId) {}
