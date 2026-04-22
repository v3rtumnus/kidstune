package at.kidstune.insights.dto;

public record ContextSummary(
        String contextUri,
        String contextType,
        String displayName,
        int    playCount,
        long   totalSeconds
) {}
