package at.kidstune.device.dto;

import at.kidstune.auth.DeviceType;
import at.kidstune.device.PairedDevice;

import java.time.Instant;

public record DeviceResponse(
        String id,
        String familyId,
        String deviceName,
        DeviceType deviceType,
        String profileId,
        Instant lastSeenAt,
        Instant createdAt
) {
    public static DeviceResponse from(PairedDevice d) {
        return new DeviceResponse(
                d.getId(),
                d.getFamilyId(),
                d.getDeviceName(),
                d.getDeviceType(),
                d.getProfileId(),
                d.getLastSeenAt(),
                d.getCreatedAt()
        );
    }
}
