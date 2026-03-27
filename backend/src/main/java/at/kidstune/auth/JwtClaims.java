package at.kidstune.auth;

public record JwtClaims(String familyId, String deviceId, DeviceType deviceType) {}
