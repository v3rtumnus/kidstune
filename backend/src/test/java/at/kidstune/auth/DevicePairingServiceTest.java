package at.kidstune.auth;

import at.kidstune.auth.dto.ConfirmPairingResponse;
import at.kidstune.auth.dto.PairingCodeResponse;
import at.kidstune.device.PairedDevice;
import at.kidstune.device.PairedDeviceRepository;
import at.kidstune.profile.ProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.transaction.support.TransactionTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DevicePairingServiceTest {

    static final String FAMILY_ID = "fam-123";
    static final String JWT_SECRET = "test-jwt-secret-32-characters-!!";

    @Mock PairingCodeRepository pairingCodeRepository;
    @Mock PairedDeviceRepository pairedDeviceRepository;
    @Mock ProfileRepository profileRepository;

    JwtTokenService jwtTokenService;
    DevicePairingService service;

    @BeforeEach
    void setUp() {
        jwtTokenService = new JwtTokenService(JWT_SECRET);
        // Use a no-op TransactionTemplate for unit tests (mocked repos don't need real transactions)
        TransactionTemplate txTemplate = new TransactionTemplate();
        service = new DevicePairingService(
                pairingCodeRepository, pairedDeviceRepository, profileRepository,
                jwtTokenService, txTemplate);
    }

    // ── generatePairingCode ───────────────────────────────────────────────────

    @Test
    void generatePairingCode_produces6DigitNumericCode() {
        when(pairingCodeRepository.deleteExpiredByFamilyId(anyString(), any())).thenReturn(0);
        when(pairingCodeRepository.countByFamilyId(FAMILY_ID)).thenReturn(0L);
        when(pairingCodeRepository.findByCode(anyString())).thenReturn(Optional.empty());
        when(pairingCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PairingCodeResponse response = service.doGeneratePairingCode(FAMILY_ID);

        assertThat(response.code()).matches("\\d{6}");
    }

    @Test
    void generatePairingCode_multipleCodesAreAllNumeric() {
        when(pairingCodeRepository.deleteExpiredByFamilyId(anyString(), any())).thenReturn(0);
        when(pairingCodeRepository.countByFamilyId(FAMILY_ID)).thenReturn(0L);
        when(pairingCodeRepository.findByCode(anyString())).thenReturn(Optional.empty());
        when(pairingCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        for (int i = 0; i < 20; i++) {
            PairingCodeResponse response = service.doGeneratePairingCode(FAMILY_ID);
            assertThat(response.code()).matches("\\d{6}");
        }
    }

    @Test
    void generatePairingCode_expiresAt_is5MinutesInFuture() {
        when(pairingCodeRepository.deleteExpiredByFamilyId(anyString(), any())).thenReturn(0);
        when(pairingCodeRepository.countByFamilyId(FAMILY_ID)).thenReturn(0L);
        when(pairingCodeRepository.findByCode(anyString())).thenReturn(Optional.empty());
        when(pairingCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Instant before = Instant.now();
        PairingCodeResponse response = service.doGeneratePairingCode(FAMILY_ID);
        Instant after = Instant.now();

        assertThat(response.expiresAt())
                .isAfterOrEqualTo(before.plusSeconds(DevicePairingService.CODE_EXPIRY_SECONDS))
                .isBeforeOrEqualTo(after.plusSeconds(DevicePairingService.CODE_EXPIRY_SECONDS));
    }

    @Test
    void generatePairingCode_throwsWhenMaxActiveCodesReached() {
        when(pairingCodeRepository.deleteExpiredByFamilyId(anyString(), any())).thenReturn(0);
        when(pairingCodeRepository.countByFamilyId(FAMILY_ID))
                .thenReturn((long) DevicePairingService.MAX_ACTIVE_CODES);

        assertThatThrownBy(() -> service.doGeneratePairingCode(FAMILY_ID))
                .isInstanceOf(PairingException.class)
                .hasMessageContaining("Maximum")
                .extracting(e -> ((PairingException) e).getCode())
                .isEqualTo("TOO_MANY_PAIRING_CODES");
    }

    // ── confirmPairing ────────────────────────────────────────────────────────

    @Test
    void confirmPairing_withValidCode_returnsDeviceToken() throws Exception {
        String code = "123456";
        PairingCode pairingCode = validPairingCode(code);

        when(pairingCodeRepository.findByCode(code)).thenReturn(Optional.of(pairingCode));
        when(pairedDeviceRepository.save(any())).thenAnswer(inv -> {
            PairedDevice d = inv.getArgument(0);
            if (d.getId() == null) {
                setField(d, "id", "dev-uuid-001");
                setField(d, "createdAt", Instant.now());
            }
            return d;
        });
        when(profileRepository.findByFamilyId(FAMILY_ID)).thenReturn(List.of());

        ConfirmPairingResponse response = service.doConfirmPairing(code, "Test Device");

        assertThat(response.deviceToken()).isNotBlank();
        assertThat(response.familyId()).isEqualTo(FAMILY_ID);

        JwtClaims claims = jwtTokenService.validateToken(response.deviceToken());
        assertThat(claims.familyId()).isEqualTo(FAMILY_ID);
        assertThat(claims.deviceType()).isEqualTo(DeviceType.KIDS);
    }

    @Test
    void confirmPairing_expiredCode_throwsPairingException() {
        String code = "654321";
        PairingCode expired = new PairingCode();
        expired.setCode(code);
        expired.setFamilyId(FAMILY_ID);
        expired.setExpiresAt(Instant.now().minusSeconds(60));

        when(pairingCodeRepository.findByCode(code)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.doConfirmPairing(code, "Device"))
                .isInstanceOf(PairingException.class)
                .extracting(e -> ((PairingException) e).getCode())
                .isEqualTo("PAIRING_CODE_EXPIRED");

        verify(pairingCodeRepository).delete(expired);
    }

    @Test
    void confirmPairing_nonExistentCode_throwsPairingException() {
        when(pairingCodeRepository.findByCode("000000")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.doConfirmPairing("000000", "Device"))
                .isInstanceOf(PairingException.class)
                .extracting(e -> ((PairingException) e).getCode())
                .isEqualTo("PAIRING_CODE_NOT_FOUND");
    }

    @Test
    void confirmPairing_deletesCodeAfterUse_singleUse() throws Exception {
        String code = "111222";
        PairingCode pairingCode = validPairingCode(code);

        when(pairingCodeRepository.findByCode(code)).thenReturn(Optional.of(pairingCode));
        when(pairedDeviceRepository.save(any())).thenAnswer(inv -> {
            PairedDevice d = inv.getArgument(0);
            if (d.getId() == null) {
                setField(d, "id", "dev-uuid-002");
                setField(d, "createdAt", Instant.now());
            }
            return d;
        });
        when(profileRepository.findByFamilyId(FAMILY_ID)).thenReturn(List.of());

        service.doConfirmPairing(code, "Device");

        verify(pairingCodeRepository).delete(pairingCode);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private PairingCode validPairingCode(String code) {
        PairingCode pc = new PairingCode();
        pc.setCode(code);
        pc.setFamilyId(FAMILY_ID);
        pc.setExpiresAt(Instant.now().plusSeconds(300));
        return pc;
    }

    private void setField(Object obj, String fieldName, Object value) throws Exception {
        Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, value);
    }
}
