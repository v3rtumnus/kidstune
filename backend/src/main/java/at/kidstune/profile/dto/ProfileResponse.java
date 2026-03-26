package at.kidstune.profile.dto;

import at.kidstune.profile.AgeGroup;
import at.kidstune.profile.AvatarColor;
import at.kidstune.profile.AvatarIcon;
import at.kidstune.profile.ChildProfile;

import java.time.Instant;

public record ProfileResponse(
        String id,
        String familyId,
        String name,
        AvatarIcon avatarIcon,
        AvatarColor avatarColor,
        AgeGroup ageGroup,
        Instant createdAt,
        Instant updatedAt
) {
    public static ProfileResponse from(ChildProfile p) {
        return new ProfileResponse(
                p.getId(), p.getFamilyId(), p.getName(),
                p.getAvatarIcon(), p.getAvatarColor(), p.getAgeGroup(),
                p.getCreatedAt(), p.getUpdatedAt()
        );
    }
}
