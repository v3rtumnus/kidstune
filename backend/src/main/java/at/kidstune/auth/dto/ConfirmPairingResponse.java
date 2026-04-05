package at.kidstune.auth.dto;

import at.kidstune.profile.dto.ProfileResponse;

import java.util.List;

public record ConfirmPairingResponse(
        String deviceToken,
        String familyId,
        List<ProfileResponse> profiles
) {}
