package at.kidstune.auth;

import at.kidstune.auth.dto.ConfirmPairingResponse;
import at.kidstune.auth.dto.PairingCodeResponse;
import at.kidstune.device.PairedDevice;
import at.kidstune.device.PairedDeviceRepository;
import at.kidstune.profile.ProfileRepository;
import at.kidstune.profile.dto.ProfileResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

@Service
public class DevicePairingService {

    static final int MAX_ACTIVE_CODES = 5;
    static final int CODE_EXPIRY_SECONDS = 5 * 60; // 5 minutes

    private final PairingCodeRepository pairingCodeRepository;
    private final PairedDeviceRepository pairedDeviceRepository;
    private final ProfileRepository profileRepository;
    private final JwtTokenService jwtTokenService;
    private final TransactionTemplate transactionTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    public DevicePairingService(PairingCodeRepository pairingCodeRepository,
                                PairedDeviceRepository pairedDeviceRepository,
                                ProfileRepository profileRepository,
                                JwtTokenService jwtTokenService,
                                TransactionTemplate transactionTemplate) {
        this.pairingCodeRepository = pairingCodeRepository;
        this.pairedDeviceRepository = pairedDeviceRepository;
        this.profileRepository = profileRepository;
        this.jwtTokenService = jwtTokenService;
        this.transactionTemplate = transactionTemplate;
    }

    public Mono<PairingCodeResponse> generatePairingCode(String familyId) {
        return Mono.fromCallable(() ->
                transactionTemplate.execute(status -> doGeneratePairingCode(familyId))
        ).subscribeOn(Schedulers.boundedElastic());
    }

    PairingCodeResponse doGeneratePairingCode(String familyId) {
        // Cleanup expired codes for this family first
        pairingCodeRepository.deleteExpiredByFamilyId(familyId, Instant.now());

        long activeCount = pairingCodeRepository.countByFamilyId(familyId);
        if (activeCount >= MAX_ACTIVE_CODES) {
            throw new PairingException(
                    "Maximum of " + MAX_ACTIVE_CODES + " active pairing codes per family reached",
                    "TOO_MANY_PAIRING_CODES",
                    HttpStatus.TOO_MANY_REQUESTS);
        }

        String code = generateUniqueCode();
        Instant expiresAt = Instant.now().plusSeconds(CODE_EXPIRY_SECONDS);

        PairingCode pairingCode = new PairingCode();
        pairingCode.setFamilyId(familyId);
        pairingCode.setCode(code);
        pairingCode.setExpiresAt(expiresAt);
        pairingCodeRepository.save(pairingCode);

        return new PairingCodeResponse(code, expiresAt);
    }

    public Mono<ConfirmPairingResponse> confirmPairing(String code, String deviceName) {
        return Mono.fromCallable(() ->
                transactionTemplate.execute(status -> doConfirmPairing(code, deviceName))
        ).subscribeOn(Schedulers.boundedElastic());
    }

    ConfirmPairingResponse doConfirmPairing(String code, String deviceName) {
        Instant now = Instant.now();

        PairingCode pairingCode = pairingCodeRepository.findByCode(code)
                .orElseThrow(() -> new PairingException(
                        "Pairing code not found or already used",
                        "PAIRING_CODE_NOT_FOUND",
                        HttpStatus.GONE));

        if (pairingCode.getExpiresAt().isBefore(now)) {
            pairingCodeRepository.delete(pairingCode);
            throw new PairingException(
                    "Pairing code has expired",
                    "PAIRING_CODE_EXPIRED",
                    HttpStatus.GONE);
        }

        // Single-use: delete the code immediately
        pairingCodeRepository.delete(pairingCode);

        // Pre-generate the device ID so we can compute the JWT before the first save
        String deviceId = java.util.UUID.randomUUID().toString();
        String token = jwtTokenService.createDeviceToken(
                pairingCode.getFamilyId(), deviceId, DeviceType.KIDS);

        PairedDevice device = new PairedDevice();
        device.setId(deviceId);
        device.setFamilyId(pairingCode.getFamilyId());
        device.setDeviceName(deviceName);
        device.setDeviceType(DeviceType.KIDS);
        device.setDeviceTokenHash(hashToken(token));
        pairedDeviceRepository.save(device);

        List<ProfileResponse> profiles = profileRepository
                .findByFamilyId(pairingCode.getFamilyId()).stream()
                .map(ProfileResponse::from)
                .toList();

        return new ConfirmPairingResponse(token, pairingCode.getFamilyId(), profiles);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Generates a 6-digit numeric code, retrying if there is a collision. */
    private String generateUniqueCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String code = String.format("%06d", secureRandom.nextInt(1_000_000));
            if (pairingCodeRepository.findByCode(code).isEmpty()) {
                return code;
            }
        }
        throw new PairingException(
                "Could not generate unique pairing code; please try again",
                "CODE_GENERATION_FAILED",
                HttpStatus.SERVICE_UNAVAILABLE);
    }

    /** SHA-256 hash of the raw JWT, base64-URL-encoded (no padding). */
    static String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
