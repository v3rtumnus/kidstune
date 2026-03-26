package at.kidstune.profile;

import at.kidstune.common.ApiError;
import at.kidstune.profile.dto.ProfileRequest;
import at.kidstune.profile.dto.ProfileResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/v1/profiles")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public Mono<ResponseEntity<?>> listProfiles(
            @RequestHeader(name = "X-Family-Id", required = false) String familyId) {

        if (familyId == null || familyId.isBlank()) {
            return missingFamilyId();
        }
        return profileService.listProfiles(familyId)
                .map(ResponseEntity::ok);
    }

    @PostMapping
    public Mono<ResponseEntity<?>> createProfile(
            @RequestHeader(name = "X-Family-Id", required = false) String familyId,
            @RequestBody @Valid ProfileRequest request) {

        if (familyId == null || familyId.isBlank()) {
            return missingFamilyId();
        }
        return profileService.createProfile(familyId, request)
                .map(profile -> ResponseEntity.status(HttpStatus.CREATED).<ProfileResponse>body(profile));
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<?>> updateProfile(
            @PathVariable String id,
            @RequestHeader(name = "X-Family-Id", required = false) String familyId,
            @RequestBody @Valid ProfileRequest request) {

        if (familyId == null || familyId.isBlank()) {
            return missingFamilyId();
        }
        return profileService.updateProfile(id, familyId, request)
                .map(ResponseEntity::ok);
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deleteProfile(
            @PathVariable String id,
            @RequestHeader(name = "X-Family-Id", required = false) String familyId) {

        if (familyId == null || familyId.isBlank()) {
            return Mono.just(ResponseEntity.badRequest().build());
        }
        return profileService.deleteProfile(id, familyId)
                .thenReturn(ResponseEntity.<Void>noContent().build());
    }

    private static Mono<ResponseEntity<?>> missingFamilyId() {
        return Mono.just(ResponseEntity.badRequest()
                .body(new ApiError("X-Family-Id header is required", "MISSING_FAMILY_ID")));
    }
}
