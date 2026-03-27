package at.kidstune.content.dto;

public record ContentCheckResponse(boolean allowed, String reason) {

    public static ContentCheckResponse allowed(String reason) {
        return new ContentCheckResponse(true, reason);
    }

    public static ContentCheckResponse denied() {
        return new ContentCheckResponse(false, "DENIED");
    }
}
