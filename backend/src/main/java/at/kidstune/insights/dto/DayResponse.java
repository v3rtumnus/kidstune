package at.kidstune.insights.dto;

import java.util.List;

public record DayResponse(
        boolean connected,
        String  status,
        DayTotals totals,
        List<SessionDto> sessions
) {
    public static DayResponse disconnected(String statusCode) {
        return new DayResponse(false, statusCode, DayTotals.empty(), List.of());
    }

    public static DayResponse ok(DayTotals totals, List<SessionDto> sessions) {
        return new DayResponse(true, "OK", totals, sessions);
    }
}
