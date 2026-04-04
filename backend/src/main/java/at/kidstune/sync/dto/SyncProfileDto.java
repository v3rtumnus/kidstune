package at.kidstune.sync.dto;

import at.kidstune.profile.AgeGroup;
import at.kidstune.profile.AvatarColor;
import at.kidstune.profile.AvatarIcon;
import at.kidstune.profile.ChildProfile;

public record SyncProfileDto(
        String id,
        String name,
        AvatarIcon avatarIcon,
        AvatarColor avatarColor,
        AgeGroup ageGroup
) {
    public static SyncProfileDto from(ChildProfile p) {
        return new SyncProfileDto(p.getId(), p.getName(), p.getAvatarIcon(), p.getAvatarColor(), p.getAgeGroup());
    }
}
