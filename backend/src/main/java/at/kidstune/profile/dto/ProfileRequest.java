package at.kidstune.profile.dto;

import at.kidstune.profile.AgeGroup;
import at.kidstune.profile.AvatarColor;
import at.kidstune.profile.AvatarIcon;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProfileRequest(
        @NotBlank(message = "Name is required")
        @Size(min = 1, max = 50, message = "Name must be between 1 and 50 characters")
        String name,

        @NotNull(message = "avatarIcon is required")
        AvatarIcon avatarIcon,

        @NotNull(message = "avatarColor is required")
        AvatarColor avatarColor,

        @NotNull(message = "ageGroup is required")
        AgeGroup ageGroup
) {}
