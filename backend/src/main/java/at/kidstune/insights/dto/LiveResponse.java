package at.kidstune.insights.dto;

public record LiveResponse(
        boolean connected,
        String  status,
        boolean playing,
        String  trackId,
        String  trackName,
        String  artistName,
        int     durationMs,
        int     progressMs,
        String  itemType
) {
    public static LiveResponse disconnected(String statusCode) {
        return new LiveResponse(false, statusCode, false, null, null, null, 0, 0, null);
    }

    public static LiveResponse nothing() {
        return new LiveResponse(true, "OK", false, null, null, null, 0, 0, null);
    }

    public static LiveResponse playing(String trackId, String trackName, String artistName,
                                       int durationMs, int progressMs, String itemType) {
        return new LiveResponse(true, "OK", true, trackId, trackName, artistName,
                durationMs, progressMs, itemType);
    }
}
