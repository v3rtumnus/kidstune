package at.kidstune.content.dto;

import java.util.List;

/**
 * Response body for POST /api/v1/content/import.
 */
public record ImportContentResponse(
        int created,
        List<ProfileSummary> profiles
) {

    public record ProfileSummary(
            String id,
            String name,
            int newContentCount
    ) {}
}
