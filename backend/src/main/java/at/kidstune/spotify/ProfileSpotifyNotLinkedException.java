package at.kidstune.spotify;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when {@link SpotifyImportService#getImportSuggestions} is called for a profile
 * that has no linked Spotify account.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class ProfileSpotifyNotLinkedException extends RuntimeException {

    public ProfileSpotifyNotLinkedException(String profileId) {
        super("Spotify account not linked for profile: " + profileId);
    }
}
