package at.kidstune.insights.dto;

import java.util.List;

public record TodayResponse(
        boolean     connected,
        String      status,
        DayTotals   totals,
        List<SessionDto> sessions
) {
    public static TodayResponse disconnected(String statusCode) {
        return new TodayResponse(false, statusCode, DayTotals.empty(), List.of());
    }

    public static TodayResponse ok(DayTotals totals, List<SessionDto> sessions) {
        return new TodayResponse(true, "OK", totals, sessions);
    }
}
