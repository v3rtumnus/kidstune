package at.kidstune.content.dto;

import at.kidstune.content.ContentScope;
import at.kidstune.content.ContentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record BulkAddContentRequest(
        @NotBlank String spotifyUri,
        @NotNull ContentScope scope,
        @NotBlank String title,
        String imageUrl,
        String artistName,
        ContentType contentTypeOverride,
        @NotEmpty List<@NotBlank String> profileIds
) {}
