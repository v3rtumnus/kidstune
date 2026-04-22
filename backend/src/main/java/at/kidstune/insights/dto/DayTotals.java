package at.kidstune.insights.dto;

public record DayTotals(
        long totalSeconds,
        long musicSeconds,
        long audiobookSeconds,
        int  sessionCount
) {
    public static DayTotals empty() {
        return new DayTotals(0, 0, 0, 0);
    }
}
