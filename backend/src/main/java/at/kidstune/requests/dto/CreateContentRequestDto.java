package at.kidstune.requests.dto;

import at.kidstune.content.ContentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateContentRequestDto(
        @NotBlank String spotifyUri,
        @NotBlank String title,
        @NotNull  ContentType contentType,
        String imageUrl,
        String artistName
) {}
