package at.kidstune.web;

import at.kidstune.profile.AvatarColor;
import at.kidstune.profile.AvatarIcon;
import org.springframework.stereotype.Component;

/**
 * Spring-managed bean exposed as {@code avatarHelper} in Thymeleaf templates.
 * Provides emoji characters and CSS colors for avatar display.
 */
@Component("avatarHelper")
public class AvatarHelper {

    public String emoji(AvatarIcon icon) {
        if (icon == null) return "?";
        return switch (icon) {
            case BEAR    -> "\uD83D\uDC3B"; // 🐻
            case FOX     -> "\uD83E\uDD8A"; // 🦊
            case BUNNY   -> "\uD83D\uDC30"; // 🐰
            case OWL     -> "\uD83E\uDDA9"; // 🦉
            case CAT     -> "\uD83D\uDC31"; // 🐱
            case PENGUIN -> "\uD83D\uDC27"; // 🐧
        };
    }

    public String cssColor(AvatarColor color) {
        if (color == null) return "#6c757d";
        return switch (color) {
            case RED    -> "#e74c3c";
            case BLUE   -> "#3498db";
            case GREEN  -> "#2ecc71";
            case PURPLE -> "#9b59b6";
            case ORANGE -> "#e67e22";
            case PINK   -> "#e91e8c";
        };
    }

    public String colorLabel(AvatarColor color) {
        if (color == null) return "";
        return switch (color) {
            case RED    -> "Rot";
            case BLUE   -> "Blau";
            case GREEN  -> "Gr\u00FCn";
            case PURPLE -> "Lila";
            case ORANGE -> "Orange";
            case PINK   -> "Pink";
        };
    }

    public String iconLabel(AvatarIcon icon) {
        if (icon == null) return "";
        return switch (icon) {
            case BEAR    -> "B\u00E4r";
            case FOX     -> "Fuchs";
            case BUNNY   -> "Hase";
            case OWL     -> "Eule";
            case CAT     -> "Katze";
            case PENGUIN -> "Pinguin";
        };
    }
}
