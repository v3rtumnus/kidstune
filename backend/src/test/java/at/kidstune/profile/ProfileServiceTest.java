package at.kidstune.profile;

import at.kidstune.profile.dto.ProfileRequest;
import at.kidstune.profile.dto.ProfileResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Optional;

import static at.kidstune.profile.ProfileService.MAX_PROFILES_PER_FAMILY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock ProfileRepository    profileRepository;
    @Mock TransactionTemplate  txTemplate;

    ProfileService profileService;

    static final String FAMILY_ID = "fam-001";
    static final ProfileRequest VALID_REQUEST =
            new ProfileRequest("Lena", AvatarIcon.FOX, AvatarColor.PURPLE, AgeGroup.PRESCHOOL);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        // Make txTemplate.execute() actually invoke the callback, just as the real
        // TransactionTemplate does.  Without this stub the mock returns null and
        // the lambda body is never executed, causing every transactional method to
        // silently return null instead of running the repository calls.
        // lenient() suppresses UnnecessaryStubbingException for tests that don't
        // call transactional methods (e.g. listProfiles, updateProfile).
        lenient().when(txTemplate.execute(any())).thenAnswer(inv -> {
            TransactionCallback<?> cb = inv.getArgument(0);
            return cb.doInTransaction(null);
        });
        profileService = new ProfileService(profileRepository, txTemplate);
    }

    // ── createProfile ─────────────────────────────────────────────────────────

    @Test
    void createProfile_happy_path_saves_and_returns_response() {
        when(profileRepository.countByFamilyId(FAMILY_ID)).thenReturn(2L);
        when(profileRepository.existsByFamilyIdAndName(FAMILY_ID, "Lena")).thenReturn(false);
        when(profileRepository.save(any())).thenAnswer(inv -> {
            ChildProfile p = inv.getArgument(0);
            p.setId("prof-1");
            return p;
        });

        ProfileResponse result = profileService.createProfile(FAMILY_ID, VALID_REQUEST).block();

        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("Lena");
        assertThat(result.avatarIcon()).isEqualTo(AvatarIcon.FOX);
        assertThat(result.avatarColor()).isEqualTo(AvatarColor.PURPLE);
        assertThat(result.ageGroup()).isEqualTo(AgeGroup.PRESCHOOL);
        assertThat(result.familyId()).isEqualTo(FAMILY_ID);
    }

    @Test
    void createProfile_duplicate_name_emits_DUPLICATE_NAME_error() {
        when(profileRepository.countByFamilyId(FAMILY_ID)).thenReturn(1L);
        when(profileRepository.existsByFamilyIdAndName(FAMILY_ID, "Lena")).thenReturn(true);

        StepVerifier.create(profileService.createProfile(FAMILY_ID, VALID_REQUEST))
                .expectErrorMatches(e -> e instanceof ProfileException pe
                        && "DUPLICATE_NAME".equals(pe.getCode()))
                .verify();
    }

    @Test
    void createProfile_at_max_capacity_emits_MAX_PROFILES_REACHED_error() {
        when(profileRepository.countByFamilyId(FAMILY_ID)).thenReturn((long) MAX_PROFILES_PER_FAMILY);

        ProfileRequest request = new ProfileRequest("Extra", AvatarIcon.BEAR, AvatarColor.BLUE, AgeGroup.TODDLER);

        StepVerifier.create(profileService.createProfile(FAMILY_ID, request))
                .expectErrorMatches(e -> e instanceof ProfileException pe
                        && "MAX_PROFILES_REACHED".equals(pe.getCode()))
                .verify();
    }

    // ── updateProfile ─────────────────────────────────────────────────────────

    @Test
    void updateProfile_saves_changes_and_returns_updated_response() {
        ChildProfile existing = profile("prof-1", FAMILY_ID, "OldName", AvatarIcon.OWL, AvatarColor.GREEN, AgeGroup.SCHOOL);
        when(profileRepository.findById("prof-1")).thenReturn(Optional.of(existing));
        when(profileRepository.existsByFamilyIdAndNameAndIdNot(FAMILY_ID, "Lena", "prof-1")).thenReturn(false);
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProfileResponse result = profileService.updateProfile("prof-1", FAMILY_ID, VALID_REQUEST).block();

        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("Lena");
        assertThat(result.avatarIcon()).isEqualTo(AvatarIcon.FOX);
    }

    @Test
    void updateProfile_unknown_id_emits_PROFILE_NOT_FOUND_error() {
        when(profileRepository.findById("unknown")).thenReturn(Optional.empty());

        StepVerifier.create(profileService.updateProfile("unknown", FAMILY_ID, VALID_REQUEST))
                .expectErrorMatches(e -> e instanceof ProfileException pe
                        && "PROFILE_NOT_FOUND".equals(pe.getCode()))
                .verify();
    }

    @Test
    void updateProfile_wrong_family_emits_PROFILE_NOT_FOUND_error() {
        ChildProfile otherFamily = profile("prof-1", "other-family", "Lena", AvatarIcon.FOX, AvatarColor.PURPLE, AgeGroup.PRESCHOOL);
        when(profileRepository.findById("prof-1")).thenReturn(Optional.of(otherFamily));

        StepVerifier.create(profileService.updateProfile("prof-1", FAMILY_ID, VALID_REQUEST))
                .expectErrorMatches(e -> e instanceof ProfileException pe
                        && "PROFILE_NOT_FOUND".equals(pe.getCode()))
                .verify();
    }

    // ── deleteProfile ─────────────────────────────────────────────────────────

    @Test
    void deleteProfile_removes_the_profile() {
        ChildProfile existing = profile("prof-1", FAMILY_ID, "Lena", AvatarIcon.FOX, AvatarColor.PURPLE, AgeGroup.PRESCHOOL);
        when(profileRepository.findById("prof-1")).thenReturn(Optional.of(existing));

        profileService.deleteProfile("prof-1", FAMILY_ID).block();

        verify(profileRepository).delete(existing);
    }

    @Test
    void deleteProfile_unknown_id_emits_PROFILE_NOT_FOUND_error() {
        when(profileRepository.findById("unknown")).thenReturn(Optional.empty());

        StepVerifier.create(profileService.deleteProfile("unknown", FAMILY_ID))
                .expectErrorMatches(e -> e instanceof ProfileException pe
                        && "PROFILE_NOT_FOUND".equals(pe.getCode()))
                .verify();
    }

    // ── listProfiles ──────────────────────────────────────────────────────────

    @Test
    void listProfiles_returns_all_profiles_for_family() {
        List<ChildProfile> profiles = List.of(
                profile("p1", FAMILY_ID, "Lena",   AvatarIcon.FOX,  AvatarColor.PURPLE, AgeGroup.PRESCHOOL),
                profile("p2", FAMILY_ID, "Tobias", AvatarIcon.BEAR, AvatarColor.BLUE,   AgeGroup.SCHOOL)
        );
        when(profileRepository.findByFamilyId(FAMILY_ID)).thenReturn(profiles);

        List<ProfileResponse> result = profileService.listProfiles(FAMILY_ID).block();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(ProfileResponse::name).containsExactly("Lena", "Tobias");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ChildProfile profile(String id, String familyId, String name,
                                 AvatarIcon icon, AvatarColor color, AgeGroup age) {
        ChildProfile p = new ChildProfile();
        p.setId(id);
        p.setFamilyId(familyId);
        p.setName(name);
        p.setAvatarIcon(icon);
        p.setAvatarColor(color);
        p.setAgeGroup(age);
        return p;
    }
}
