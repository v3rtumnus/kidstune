package at.kidstune.profile;

import at.kidstune.common.SecurityUtils;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/profiles")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public Mono<ResponseEntity<?>> listProfiles() {
        return SecurityUtils.getFamilyId()
                .flatMap(profileService::listProfiles)
                .map(ResponseEntity::ok);
    }

    @PostMapping
    public Mono<ResponseEntity<?>> createProfile(@RequestBody @Valid ProfileRequest request) {
        return SecurityUtils.getFamilyId()
                .flatMap(familyId -> profileService.createProfile(familyId, request))
                .map(profile -> ResponseEntity.status(HttpStatus.CREATED).<ProfileResponse>body(profile));
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<?>> updateProfile(
            @PathVariable String id,
            @RequestBody @Valid ProfileRequest request) {
        return SecurityUtils.getFamilyId()
                .flatMap(familyId -> profileService.updateProfile(id, familyId, request))
                .map(ResponseEntity::ok);
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deleteProfile(@PathVariable String id) {
        return SecurityUtils.getFamilyId()
                .flatMap(familyId -> profileService.deleteProfile(id, familyId))
                .thenReturn(ResponseEntity.<Void>noContent().build());
    }
}
