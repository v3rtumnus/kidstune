package at.kidstune.insights.dto;

public record DailySummary(
        String date,
        long   musicSeconds,
        long   audiobookSeconds
) {}
