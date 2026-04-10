package at.kidstune.profile;

import at.kidstune.profile.dto.ProfileRequest;
import at.kidstune.profile.dto.ProfileResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

@Service
public class ProfileService {

    static final int MAX_PROFILES_PER_FAMILY = 6;

    private final ProfileRepository   profileRepository;
    private final TransactionTemplate txTemplate;

    public ProfileService(ProfileRepository profileRepository, TransactionTemplate txTemplate) {
        this.profileRepository = profileRepository;
        this.txTemplate        = txTemplate;
    }

    public Mono<List<ProfileResponse>> listProfiles(String familyId) {
        return Mono.fromCallable(() ->
                profileRepository.findByFamilyId(familyId).stream()
                        .map(ProfileResponse::from)
                        .toList()
        ).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Creates a new child profile. The check-then-insert is wrapped in a
     * TransactionTemplate so it runs atomically on the boundedElastic thread.
     * {@code @Transactional} on a Mono-returning method is inert because the
     * transaction opens on the calling thread while DB work runs on a different thread.
     */
    public Mono<ProfileResponse> createProfile(String familyId, ProfileRequest request) {
        return Mono.fromCallable(() ->
            txTemplate.execute(status -> {
                if (profileRepository.countByFamilyId(familyId) >= MAX_PROFILES_PER_FAMILY) {
                    throw new ProfileException(
                            "Maximum of " + MAX_PROFILES_PER_FAMILY + " profiles per family reached",
                            "MAX_PROFILES_REACHED");
                }
                if (profileRepository.existsByFamilyIdAndName(familyId, request.name())) {
                    throw new ProfileException("Profile name already exists", "DUPLICATE_NAME");
                }

                ChildProfile profile = new ChildProfile();
                profile.setFamilyId(familyId);
                profile.setName(request.name());
                profile.setAvatarIcon(request.avatarIcon());
                profile.setAvatarColor(request.avatarColor());
                profile.setAgeGroup(request.ageGroup());

                return ProfileResponse.from(profileRepository.save(profile));
            })
        ).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<ProfileResponse> updateProfile(String profileId, String familyId, ProfileRequest request) {
        return Mono.fromCallable(() -> {
            ChildProfile profile = profileRepository.findById(profileId)
                    .filter(p -> p.getFamilyId().equals(familyId))
                    .orElseThrow(() -> new ProfileException(
                            "Profile not found", "PROFILE_NOT_FOUND", HttpStatus.NOT_FOUND));

            if (!profile.getName().equals(request.name()) &&
                    profileRepository.existsByFamilyIdAndNameAndIdNot(familyId, request.name(), profileId)) {
                throw new ProfileException("Profile name already exists", "DUPLICATE_NAME");
            }

            profile.setName(request.name());
            profile.setAvatarIcon(request.avatarIcon());
            profile.setAvatarColor(request.avatarColor());
            profile.setAgeGroup(request.ageGroup());

            return ProfileResponse.from(profileRepository.save(profile));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /** Deletes a profile. Wrapped in TransactionTemplate so delete is atomic. */
    public Mono<Void> deleteProfile(String profileId, String familyId) {
        return Mono.fromCallable(() ->
            txTemplate.execute(status -> {
                ChildProfile profile = profileRepository.findById(profileId)
                        .filter(p -> p.getFamilyId().equals(familyId))
                        .orElseThrow(() -> new ProfileException(
                                "Profile not found", "PROFILE_NOT_FOUND", HttpStatus.NOT_FOUND));
                profileRepository.delete(profile);
                return null;
            })
        ).subscribeOn(Schedulers.boundedElastic()).then();
    }
}
