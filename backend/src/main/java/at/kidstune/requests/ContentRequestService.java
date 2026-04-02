package at.kidstune.requests;

import at.kidstune.content.AllowedContent;
import at.kidstune.content.ContentRepository;
import at.kidstune.content.ContentScope;
import at.kidstune.profile.ChildProfile;
import at.kidstune.profile.ProfileRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.List;

@Service
public class ContentRequestService {

    private final ContentRequestRepository requestRepository;
    private final ContentRepository        contentRepository;
    private final ProfileRepository        profileRepository;

    public ContentRequestService(ContentRequestRepository requestRepository,
                                  ContentRepository contentRepository,
                                  ProfileRepository profileRepository) {
        this.requestRepository = requestRepository;
        this.contentRepository = contentRepository;
        this.profileRepository = profileRepository;
    }

    /** Approve a single request: creates AllowedContent for the requesting profile. */
    public Mono<Void> approveRequest(String requestId, String familyId) {
        return db(() -> {
            ContentRequest request = getRequestForFamily(requestId, familyId);
            addContentIfMissing(request.getProfileId(), request);
            request.setStatus(ContentRequestStatus.APPROVED);
            request.setResolvedAt(Instant.now());
            request.setResolvedBy(familyId);
            requestRepository.save(request);
            return null;
        });
    }

    /** Approve for all profiles in the family: creates AllowedContent for every profile. */
    public Mono<Void> approveForAllProfiles(String requestId, String familyId) {
        return db(() -> {
            ContentRequest request = getRequestForFamily(requestId, familyId);
            List<String> profileIds = profileRepository.findByFamilyId(familyId)
                    .stream().map(ChildProfile::getId).toList();
            for (String profileId : profileIds) {
                addContentIfMissing(profileId, request);
            }
            request.setStatus(ContentRequestStatus.APPROVED);
            request.setResolvedAt(Instant.now());
            request.setResolvedBy(familyId);
            requestRepository.save(request);
            return null;
        });
    }

    /** Reject a request with an optional parent note. */
    public Mono<Void> rejectRequest(String requestId, String familyId, String note) {
        return db(() -> {
            ContentRequest request = getRequestForFamily(requestId, familyId);
            request.setStatus(ContentRequestStatus.REJECTED);
            request.setResolvedAt(Instant.now());
            request.setResolvedBy(familyId);
            if (note != null && !note.isBlank()) {
                request.setParentNote(note.strip());
            }
            requestRepository.save(request);
            return null;
        });
    }

    /** Bulk-approve a list of request IDs. Skips requests that don't belong to the family. */
    public Mono<Void> bulkApprove(List<String> requestIds, String familyId) {
        return db(() -> {
            for (String requestId : requestIds) {
                try {
                    ContentRequest request = getRequestForFamily(requestId, familyId);
                    if (request.getStatus() == ContentRequestStatus.PENDING) {
                        addContentIfMissing(request.getProfileId(), request);
                        request.setStatus(ContentRequestStatus.APPROVED);
                        request.setResolvedAt(Instant.now());
                        request.setResolvedBy(familyId);
                        requestRepository.save(request);
                    }
                } catch (IllegalArgumentException ignored) {
                    // skip — request doesn't belong to this family
                }
            }
            return null;
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ContentRequest getRequestForFamily(String requestId, String familyId) {
        ContentRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Anfrage nicht gefunden: " + requestId));
        profileRepository.findById(request.getProfileId())
                .filter(p -> p.getFamilyId().equals(familyId))
                .orElseThrow(() -> new IllegalArgumentException("Anfrage nicht gefunden: " + requestId));
        return request;
    }

    private void addContentIfMissing(String profileId, ContentRequest request) {
        if (contentRepository.existsByProfileIdAndSpotifyUri(profileId, request.getSpotifyUri())) {
            return;
        }
        AllowedContent content = new AllowedContent();
        content.setProfileId(profileId);
        content.setSpotifyUri(request.getSpotifyUri());
        content.setContentType(request.getContentType());
        content.setScope(scopeFromUri(request.getSpotifyUri()));
        content.setTitle(request.getTitle());
        content.setImageUrl(request.getImageUrl());
        content.setArtistName(request.getArtistName());
        contentRepository.save(content);
    }

    /** Infers ContentScope from the Spotify URI prefix. Defaults to ALBUM. */
    static ContentScope scopeFromUri(String uri) {
        if (uri == null)                            return ContentScope.ALBUM;
        if (uri.startsWith("spotify:track:"))       return ContentScope.TRACK;
        if (uri.startsWith("spotify:album:"))       return ContentScope.ALBUM;
        if (uri.startsWith("spotify:playlist:"))    return ContentScope.PLAYLIST;
        if (uri.startsWith("spotify:artist:"))      return ContentScope.ARTIST;
        return ContentScope.ALBUM;
    }

    private <T> Mono<T> db(java.util.concurrent.Callable<T> callable) {
        return Mono.fromCallable(callable).subscribeOn(Schedulers.boundedElastic());
    }
}