package at.kidstune.insights.dto;

import java.util.List;

public record RangeResponse(
        boolean connected,
        String  status,
        List<DailySummary>  dailySummaries,
        List<TrackSummary>  topMusicTracks,
        List<ContextSummary> topMusicContexts,
        List<ContextSummary> topAudiobookShows
) {
    public static RangeResponse disconnected(String statusCode) {
        return new RangeResponse(false, statusCode, List.of(), List.of(), List.of(), List.of());
    }

    public static RangeResponse ok(List<DailySummary> daily,
                                   List<TrackSummary> tracks,
                                   List<ContextSummary> contexts,
                                   List<ContextSummary> shows) {
        return new RangeResponse(true, "OK", daily, tracks, contexts, shows);
    }
}
