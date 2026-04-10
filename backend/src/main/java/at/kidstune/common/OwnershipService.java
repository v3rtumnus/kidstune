package at.kidstune.common;

import at.kidstune.profile.ProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * Verifies that a profile (or a list of profiles) belongs to the authenticated family.
 * Throws 404 (not found, to avoid leaking profile existence) on violation.
 */
@Service
public class OwnershipService {

    private final ProfileRepository profileRepository;

    public OwnershipService(ProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    /**
     * Returns an empty Mono that completes normally if {@code profileId} belongs to
     * {@code familyId}, or terminates with a 404 ResponseStatusException otherwise.
     */
    public Mono<Void> requireProfileOwnership(String profileId, String familyId) {
        return Mono.fromCallable(() -> {
            if (!profileRepository.existsByIdAndFamilyId(profileId, familyId)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found");
            }
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    /**
     * Like {@link #requireProfileOwnership} but validates every id in the list.
     * Fails fast on the first unowned profile id.
     */
    public Mono<Void> requireAllProfilesOwnership(List<String> profileIds, String familyId) {
        return Mono.fromCallable(() -> {
            for (String profileId : profileIds) {
                if (!profileRepository.existsByIdAndFamilyId(profileId, familyId)) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found");
                }
            }
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }
}
