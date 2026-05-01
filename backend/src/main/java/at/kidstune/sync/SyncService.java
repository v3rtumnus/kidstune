package at.kidstune.sync;

import at.kidstune.content.AllowedContent;
import at.kidstune.content.ContentRepository;
import at.kidstune.family.FamilyRepository;
import at.kidstune.favorites.Favorite;
import at.kidstune.favorites.FavoriteRepository;
import at.kidstune.profile.ChildProfile;
import at.kidstune.profile.ProfileRepository;
import at.kidstune.resolver.ResolvedAlbum;
import at.kidstune.resolver.ResolvedAlbumRepository;
import at.kidstune.resolver.ResolvedTrack;
import at.kidstune.resolver.ResolvedTrackRepository;
import at.kidstune.spotify.SpotifyWebApiClient;
import at.kidstune.sync.dto.DeltaSyncPayload;
import at.kidstune.sync.dto.FullSyncPayload;
import at.kidstune.sync.dto.SyncAlbumDto;
import at.kidstune.sync.dto.SyncContentEntryDto;
import at.kidstune.sync.dto.SyncFavoriteDto;
import at.kidstune.sync.dto.SyncProfileDto;
import at.kidstune.sync.dto.SyncTrackDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);

    private final ProfileRepository       profileRepo;
    private final FamilyRepository        familyRepo;
    private final ContentRepository       contentRepo;
    private final ResolvedAlbumRepository albumRepo;
    private final ResolvedTrackRepository trackRepo;
    private final FavoriteRepository      favoriteRepo;
    private final DeletionLogRepository   deletionLogRepo;
    private final SpotifyWebApiClient     spotifyClient;

    public SyncService(ProfileRepository profileRepo,
                       FamilyRepository familyRepo,
                       ContentRepository contentRepo,
                       ResolvedAlbumRepository albumRepo,
                       ResolvedTrackRepository trackRepo,
                       FavoriteRepository favoriteRepo,
                       DeletionLogRepository deletionLogRepo,
                       SpotifyWebApiClient spotifyClient) {
        this.profileRepo     = profileRepo;
        this.familyRepo      = familyRepo;
        this.contentRepo     = contentRepo;
        this.albumRepo       = albumRepo;
        this.trackRepo       = trackRepo;
        this.favoriteRepo    = favoriteRepo;
        this.deletionLogRepo = deletionLogRepo;
        this.spotifyClient   = spotifyClient;
    }

    // ── Full sync ──────────────────────────────────────────────────────────────

    public Mono<FullSyncPayload> fullSync(String profileId) {
        return Mono.fromCallable(() -> buildFullSync(profileId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private FullSyncPayload buildFullSync(String profileId) {
        ChildProfile profile = profileRepo.findById(profileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found"));

        Map<String, Instant> contextLastPlayed = fetchContextLastPlayed(profileId);

        List<AllowedContent> entries = contentRepo.findByProfileId(profileId);
        List<SyncContentEntryDto> content = buildContentTree(entries, contextLastPlayed);

        List<SyncFavoriteDto> favorites = favoriteRepo.findByProfileId(profileId)
                .stream().map(SyncFavoriteDto::from).toList();

        boolean pinAvailable = familyRepo.findById(profile.getFamilyId())
                .map(f -> f.getApprovalPinHash() != null)
                .orElse(false);

        return new FullSyncPayload(SyncProfileDto.from(profile), favorites, content, pinAvailable, Instant.now());
    }

    // ── Delta sync ─────────────────────────────────────────────────────────────

    public Mono<DeltaSyncPayload> deltaSync(String profileId, Instant since) {
        return Mono.fromCallable(() -> buildDeltaSync(profileId, since))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private DeltaSyncPayload buildDeltaSync(String profileId, Instant since) {
        ChildProfile profile = profileRepo.findById(profileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found"));

        Map<String, Instant> contextLastPlayed = fetchContextLastPlayed(profileId);

        // ── Added: entries created after `since` ───────────────────────────────
        List<AllowedContent> addedEntries = contentRepo.findByProfileIdAndCreatedAtAfter(profileId, since);
        List<SyncContentEntryDto> added = buildContentTree(addedEntries, contextLastPlayed);

        // ── Updated: existing entries with re-resolved albums since `since` ────
        Set<String> addedIds = addedEntries.stream().map(AllowedContent::getId).collect(Collectors.toSet());

        List<AllowedContent> allEntries = contentRepo.findByProfileId(profileId);
        List<String> existingIds = allEntries.stream()
                .map(AllowedContent::getId)
                .filter(id -> !addedIds.contains(id))
                .toList();

        List<SyncContentEntryDto> updated = Collections.emptyList();
        if (!existingIds.isEmpty()) {
            Set<String> updatedContentIds = albumRepo
                    .findByAllowedContentIdInAndResolvedAtAfter(existingIds, since)
                    .stream()
                    .map(ResolvedAlbum::getAllowedContentId)
                    .collect(Collectors.toSet());

            if (!updatedContentIds.isEmpty()) {
                Map<String, AllowedContent> byId = allEntries.stream()
                        .collect(Collectors.toMap(AllowedContent::getId, Function.identity()));
                List<AllowedContent> updatedEntries = updatedContentIds.stream()
                        .map(byId::get)
                        .filter(e -> e != null)
                        .toList();
                updated = buildContentTree(updatedEntries, contextLastPlayed);
            }
        }

        // ── Removed: content deletion log since `since` ────────────────────────
        List<String> removed = deletionLogRepo
                .findByProfileIdAndDeletedAtAfterAndType(profileId, since, DeletionLog.DeletionType.CONTENT)
                .stream()
                .map(DeletionLog::getSpotifyUri)
                .toList();

        // ── Favorites added/removed since `since` ──────────────────────────────
        List<SyncFavoriteDto> favoritesAdded = favoriteRepo
                .findByProfileIdAndAddedAtAfter(profileId, since)
                .stream().map(SyncFavoriteDto::from).toList();

        List<String> favoritesRemoved = deletionLogRepo
                .findByProfileIdAndDeletedAtAfterAndType(profileId, since, DeletionLog.DeletionType.FAVORITE)
                .stream()
                .map(DeletionLog::getSpotifyUri)
                .toList();

        boolean pinAvailable = familyRepo.findById(profile.getFamilyId())
                .map(f -> f.getApprovalPinHash() != null)
                .orElse(false);

        return new DeltaSyncPayload(added, updated, removed, favoritesAdded, favoritesRemoved, pinAvailable, Instant.now());
    }

    // ── Tree builder (shared by full + delta) ──────────────────────────────────

    /**
     * Builds a content tree for the given entries using exactly 3 DB queries regardless
     * of how many entries/albums/tracks there are (batch IN queries).
     *
     * @param contextLastPlayed  contextUri → most recent Instant from Spotify recently-played;
     *                           may be empty if the profile has no token or the call failed
     */
    private List<SyncContentEntryDto> buildContentTree(List<AllowedContent> entries,
                                                        Map<String, Instant> contextLastPlayed) {
        if (entries.isEmpty()) return List.of();

        List<String> contentIds = entries.stream().map(AllowedContent::getId).toList();

        // Batch-load all albums (newest release first) and all tracks (disc/track order)
        List<ResolvedAlbum> allAlbums = albumRepo.findByAllowedContentIdInOrderByReleaseDateDesc(contentIds);
        List<String> albumIds = allAlbums.stream().map(ResolvedAlbum::getId).toList();

        List<ResolvedTrack> allTracks = albumIds.isEmpty()
                ? List.of()
                : trackRepo.findByResolvedAlbumIdInOrderByDiscTrack(albumIds);

        // Index tracks by albumId
        Map<String, List<SyncTrackDto>> tracksByAlbumId = new LinkedHashMap<>();
        for (ResolvedTrack t : allTracks) {
            tracksByAlbumId.computeIfAbsent(t.getResolvedAlbumId(), k -> new ArrayList<>())
                    .add(SyncTrackDto.from(t));
        }

        // Index albums by contentId; also collect album URIs per contentId for last-listened lookup
        Map<String, List<SyncAlbumDto>> albumsByContentId = new LinkedHashMap<>();
        Map<String, List<String>> albumUrisByContentId = new LinkedHashMap<>();
        for (ResolvedAlbum a : allAlbums) {
            List<SyncTrackDto> tracks = tracksByAlbumId.getOrDefault(a.getId(), List.of());
            albumsByContentId.computeIfAbsent(a.getAllowedContentId(), k -> new ArrayList<>())
                    .add(SyncAlbumDto.from(a, tracks));
            albumUrisByContentId.computeIfAbsent(a.getAllowedContentId(), k -> new ArrayList<>())
                    .add(a.getSpotifyAlbumUri());
        }

        // Build final list preserving input order
        return entries.stream()
                .map(e -> {
                    Instant lastListenedAt = resolveLastListened(
                            e.getSpotifyUri(),
                            albumUrisByContentId.getOrDefault(e.getId(), List.of()),
                            contextLastPlayed);
                    return SyncContentEntryDto.from(
                            e,
                            albumsByContentId.getOrDefault(e.getId(), List.of()),
                            lastListenedAt);
                })
                .toList();
    }

    /**
     * Finds the most recent play time for a content entry by checking its own URI first,
     * then any of its resolved album URIs (for ARTIST scope entries).
     */
    private Instant resolveLastListened(String entryUri,
                                        List<String> albumUris,
                                        Map<String, Instant> contextLastPlayed) {
        if (contextLastPlayed.isEmpty()) return null;

        Instant direct = contextLastPlayed.get(entryUri);
        if (direct != null && albumUris.isEmpty()) return direct;

        Instant viaAlbum = albumUris.stream()
                .map(contextLastPlayed::get)
                .filter(t -> t != null)
                .max(Instant::compareTo)
                .orElse(null);

        if (direct == null) return viaAlbum;
        if (viaAlbum == null) return direct;
        return direct.isAfter(viaAlbum) ? direct : viaAlbum;
    }

    /**
     * Fetches the recently-played context URIs for the profile and builds a map of
     * contextUri → most recent playedAt. Returns an empty map if the profile has no
     * Spotify token or the Spotify call fails.
     */
    private Map<String, Instant> fetchContextLastPlayed(String profileId) {
        try {
            List<SpotifyWebApiClient.RawProfilePlayEvent> events =
                    spotifyClient.getProfileRecentlyPlayed(profileId, 0).block();
            if (events == null || events.isEmpty()) return Map.of();

            Map<String, Instant> result = new LinkedHashMap<>();
            for (SpotifyWebApiClient.RawProfilePlayEvent e : events) {
                if (e.contextUri() == null) continue;
                result.merge(e.contextUri(), e.playedAt(),
                        (existing, newVal) -> newVal.isAfter(existing) ? newVal : existing);
            }
            return result;
        } catch (Exception ex) {
            log.debug("Could not fetch recently-played for profile {}: {}", profileId, ex.getMessage());
            return Map.of();
        }
    }
}