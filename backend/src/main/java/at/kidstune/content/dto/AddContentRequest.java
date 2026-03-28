package at.kidstune.content.dto;

import at.kidstune.content.ContentScope;
import at.kidstune.content.ContentType;
import at.kidstune.content.SpotifyItemInfo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AddContentRequest(
        @NotBlank String spotifyUri,
        @NotNull ContentScope scope,
        @NotBlank String title,
        String imageUrl,
        String artistName,
        ContentType contentTypeOverride,  // null → auto-classify via heuristic
        SpotifyItemInfo spotifyItemInfo   // null → defaults to MUSIC if no override
) {}