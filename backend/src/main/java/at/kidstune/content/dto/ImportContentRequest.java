package at.kidstune.content.dto;

import at.kidstune.content.ContentScope;
import at.kidstune.content.ContentType;
import at.kidstune.content.SpotifyItemInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Request body for POST /api/v1/content/import.
 * Each item may target multiple profiles.
 */
public record ImportContentRequest(
        @NotEmpty @Valid List<ImportItem> items
) {

    public record ImportItem(
            @NotBlank String spotifyUri,
            @NotNull ContentScope scope,
            String title,
            String imageUrl,
            String artistName,
            ContentType contentTypeOverride,
            SpotifyItemInfo spotifyItemInfo,
            @NotEmpty List<@NotBlank String> profileIds
    ) {}
}
