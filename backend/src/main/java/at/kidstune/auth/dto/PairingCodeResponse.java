package at.kidstune.auth.dto;

import java.time.Instant;

public record PairingCodeResponse(String code, Instant expiresAt) {}
