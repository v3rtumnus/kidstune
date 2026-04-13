package at.kidstune.content.dto;

import at.kidstune.content.ContentScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PinApproveRequest(
        @NotBlank String spotifyUri,
        @NotNull ContentScope scope,
        @NotBlank String title,
        String imageUrl,
        String artistName,
        @NotBlank @Size(min = 4, max = 4) @Pattern(regexp = "\\d{4}") String pin
) {}
