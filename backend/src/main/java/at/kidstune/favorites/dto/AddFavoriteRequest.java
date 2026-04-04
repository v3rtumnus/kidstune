package at.kidstune.favorites.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AddFavoriteRequest(
        @NotBlank
        @Pattern(regexp = "spotify:track:.+", message = "Must be a spotify:track: URI")
        String spotifyTrackUri,

        @NotBlank
        String trackTitle,

        String trackImageUrl,

        String artistName
) {}
