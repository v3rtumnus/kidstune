package at.kidstune.requests.dto;

import java.util.List;

public record PendingCountResponse(
        List<ProfileCount> profiles,
        long               total
) {
    public record ProfileCount(String id, String name, long count) {}
}
