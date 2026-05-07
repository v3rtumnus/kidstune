package at.kidstune.resolver;

import at.kidstune.content.AllowedContent;
import at.kidstune.content.ContentRepository;
import at.kidstune.content.ContentScope;
import at.kidstune.content.ContentType;
import at.kidstune.content.ContentTypeClassifier;
import at.kidstune.content.SpotifyItemInfo;
import at.kidstune.profile.ChildProfile;
import at.kidstune.profile.ProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resolves {@link AllowedContent} entries into concrete {@link ResolvedAlbum} and
 * {@link ResolvedTrack} rows that the Kids App can sync for offline use.
 *
 * <ul>
 *   <li>ARTIST – all albums and their tracks.</li>
 *   <li>ALBUM  – single album with all tracks (genres → content-type heuristic).</li>
 *   <li>PLAYLIST – tracks grouped per album; each album stored once.</li>
 *   <li>TRACK – the track's parent album plus the track itself.</li>
 * </ul>
 *
 * A daily scheduled job at 04:00 re-resolves ARTIST and PLAYLIST entries to pick up
 * new releases/changes, applying a diff (add new albums, remove deleted ones).
 */
@Service
public class ContentResolver {

    private static final Logger log = LoggerFactory.getLogger(ContentResolver.class);

    private final ResolvedAlbumRepository albumRepo;
    private final ResolvedTrackRepository trackRepo;
    private final ContentRepository       contentRepo;
    private final ProfileRepository       profileRepo;
    private final ContentResolverSpotifyClient spotifyClient;
    private final ContentTypeClassifier   classifier;
    private final AtomicInteger activeJobs = new AtomicInteger(0);

    // Progress state for the bulk resolve-all operation
    private volatile boolean resolveAllRunning      = false;
    private volatile String  abortReason            = null;
    private volatile Instant rateLimitCooldownUntil = Instant.MIN;
    private final AtomicInteger progressTotal     = new AtomicInteger(0);
    private final AtomicInteger progressCompleted = new AtomicInteger(0);
    private final AtomicInteger progressFailed    = new AtomicInteger(0);

    public record ResolutionProgress(boolean running, int completed, int total, int failed,
                                     String abortReason, Instant rateLimitCooldownUntil) {}

    public ResolutionProgress getResolutionProgress() {
        Instant cooldown = Instant.now().isBefore(rateLimitCooldownUntil) ? rateLimitCooldownUntil : null;
        return new ResolutionProgress(resolveAllRunning, progressCompleted.get(), progressTotal.get(),
                progressFailed.get(), abortReason, cooldown);
    }

    public ContentResolver(ResolvedAlbumRepository albumRepo,
                           ResolvedTrackRepository trackRepo,
                           ContentRepository contentRepo,
                           ProfileRepository profileRepo,
                           ContentResolverSpotifyClient spotifyClient,
                           ContentTypeClassifier classifier) {
        this.albumRepo     = albumRepo;
        this.trackRepo     = trackRepo;
        this.contentRepo   = contentRepo;
        this.profileRepo   = profileRepo;
        this.spotifyClient = spotifyClient;
        this.classifier    = classifier;
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /** Returns the number of content resolution jobs currently in progress. */
    public int getActiveJobCount() {
        return activeJobs.get();
    }

    /**
     * Triggers resolution in a background thread (called from ContentService after
     * a successful addContent / addContentBulk save).
     */
    @Async
    public void resolveAsync(AllowedContent content) {
        activeJobs.incrementAndGet();
        try {
            resolve(content);
        } catch (Exception e) {
            // already logged in resolve()
        } finally {
            activeJobs.decrementAndGet();
        }
    }

    /**
     * Resolves a batch of content entries sequentially in a single background thread
     * with a short pause between items.  Keeps a running progress counter so the
     * admin UI can poll for status.  Use this instead of scattering N parallel
     * {@link #resolveAsync} calls – parallel jobs saturate the Spotify rate limit.
     *
     * <p>Already-resolved items (resolvedAt != null) are skipped so that re-triggering
     * after a 429 abort only processes the remaining unresolved entries.  Items are
     * sorted cheapest-first (TRACK → ALBUM → PLAYLIST → ARTIST) to maximise throughput
     * before any rate-limiting kicks in.
     *
     * <p>On a 429 with a Retry-After that exceeds the cap the offending item is skipped
     * (its resolvedAt stays null, so the next trigger will retry it) and processing
     * continues with the next item rather than aborting the entire run.
     */
    @Async
    public void resolveAllAsync(List<AllowedContent> items) {
        // Refuse to start if we're still within a Spotify-issued rate-limit cooldown window.
        if (Instant.now().isBefore(rateLimitCooldownUntil)) {
            log.info("resolveAll: skipped – still in Spotify rate-limit cooldown until {}", rateLimitCooldownUntil);
            return;
        }

        // Skip items that are genuinely resolved (resolvedAt set AND have at least one
        // resolved album child). Items marked resolved but with no children are re-queued.
        // Items with a permanent error code (e.g. 403) are excluded; they can be retried
        // individually via the per-item re-resolve button.
        List<ContentScope> scopeOrder = List.of(
                ContentScope.TRACK, ContentScope.ALBUM, ContentScope.PLAYLIST, ContentScope.ARTIST);
        Set<String> contentIdsWithAlbums = albumRepo.findAllowedContentIdsWithAlbums();
        List<AllowedContent> pending = items.stream()
                .filter(c -> c.getResolutionErrorCode() == null)
                .filter(c -> c.getResolvedAt() == null || !contentIdsWithAlbums.contains(c.getId()))
                .sorted(Comparator.comparingInt(c -> scopeOrder.indexOf(c.getScope())))
                .collect(Collectors.toList());

        resolveAllRunning = true;
        abortReason = null;
        progressTotal.set(pending.size());
        progressCompleted.set(0);
        progressFailed.set(0);
        log.info("resolveAll: {} unresolved items to process ({} already resolved, skipped)",
                 pending.size(), items.size() - pending.size());
        try {
            for (int i = 0; i < pending.size(); i++) {
                AllowedContent content = pending.get(i);
                log.info("resolveAll: [{}/{}] {} {}", i + 1, pending.size(),
                         content.getScope(), content.getSpotifyUri());
                activeJobs.incrementAndGet();
                try {
                    resolve(content);
                } catch (WebClientResponseException e) {
                    progressFailed.incrementAndGet();
                    if (e.getStatusCode().value() == 429) {
                        String retryAfter = e.getHeaders().getFirst("Retry-After");
                        long waitSeconds = retryAfter != null ? parseLong(retryAfter, 30L) : 30L;
                        if (waitSeconds > 300) {
                            // Retry-After > 5 min means Spotify has issued a prolonged ban;
                            // record the cooldown window, abort the run – the item stays
                            // unresolved for the next trigger.
                            rateLimitCooldownUntil = Instant.now().plusSeconds(waitSeconds);
                            abortReason = "Spotify rate limit (Retry-After: " + waitSeconds
                                    + "s) – verbleibende Einträge werden beim nächsten Lauf nachgeholt";
                            log.warn("resolveAll: aborting at [{}/{}] – Spotify Retry-After {}s > 5min, cooldown until {}",
                                     i + 1, pending.size(), waitSeconds, rateLimitCooldownUntil);
                            break;
                        }
                        // Short rate limit: sleep and continue rather than aborting the entire run.
                        log.warn("resolveAll: [{}/{}] rate-limited (Retry-After {}s), sleeping then continuing",
                                 i + 1, pending.size(), waitSeconds);
                        Thread.sleep(waitSeconds * 1000 + 500);
                        continue;
                    }
                    log.warn("resolveAll: [{}/{}] failed for {}: {}",
                             i + 1, pending.size(), content.getId(), e.getMessage());
                } catch (Exception e) {
                    log.warn("resolveAll: [{}/{}] failed for {}: {}",
                             i + 1, pending.size(), content.getId(), e.getMessage());
                    progressFailed.incrementAndGet();
                } finally {
                    activeJobs.decrementAndGet();
                    progressCompleted.incrementAndGet();
                }
                if (abortReason != null) break;
                if (i < pending.size() - 1) {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.warn("resolveAll interrupted after [{}/{}]", i + 1, pending.size());
                        break;
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("resolveAll interrupted");
        } finally {
            resolveAllRunning = false;
            log.info("resolveAll: done – {}/{} items completed{}",
                     progressCompleted.get(), progressTotal.get(),
                     abortReason != null ? " (aborted: " + abortReason + ")" : "");
        }
    }

    private static long parseLong(String s, long fallback) {
        try { return Math.max(1L, Long.parseLong(s.trim())); }
        catch (NumberFormatException e) { return fallback; }
    }

    /**
     * Synchronous resolution – resolves {@code content} and persists all
     * {@link ResolvedAlbum} / {@link ResolvedTrack} rows.
     * Sets {@link AllowedContent#setResolvedAt} on success.
     */
    public void resolve(AllowedContent content) {
        String familyId = familyIdFor(content);
        if (familyId == null) {
            log.warn("Cannot resolve content {}: profile {} not found",
                     content.getId(), content.getProfileId());
            return;
        }

        try {
            switch (content.getScope()) {
                case ARTIST   -> resolveArtist(content, familyId);
                case ALBUM    -> resolveAlbum(content, familyId);
                case PLAYLIST -> resolvePlaylist(content, familyId);
                case TRACK    -> resolveTrack(content, familyId);
            }
            content.setResolvedAt(Instant.now());
            content.setResolutionErrorCode(null);
            contentRepo.save(content);
        } catch (WebClientResponseException e) {
            log.error("Resolution failed for content {}: {} – Spotify response: {}",
                    content.getId(), e.getMessage(), e.getResponseBodyAsString());
            if (e.getStatusCode().value() == 403) {
                content.setResolutionErrorCode(403);
                contentRepo.save(content);
            }
            throw e;
        } catch (Exception e) {
            log.error("Resolution failed for content {}: {}", content.getId(), e.getMessage(), e);
        }
    }

    // ── Scheduled re-resolution ────────────────────────────────────────────────

    /**
     * Daily re-resolution of ARTIST and PLAYLIST entries at 04:00.
     * Only diffs are applied: new albums added, removed albums deleted (tracks cascade).
     */
    @Scheduled(cron = "0 0 4 * * *")
    public void reResolveArtistsAndPlaylists() {
        List<AllowedContent> entries;
        try {
            entries = contentRepo.findByScopeIn(List.of(ContentScope.ARTIST, ContentScope.PLAYLIST));
        } catch (Exception e) {
            log.error("Re-resolution aborted: failed to load ARTIST/PLAYLIST entries from DB: {}",
                      e.getMessage(), e);
            return;
        }

        log.info("Re-resolution: {} ARTIST/PLAYLIST entries to process", entries.size());
        for (int i = 0; i < entries.size(); i++) {
            AllowedContent content = entries.get(i);
            log.info("Re-resolution: [{}/{}] {} {}", i + 1, entries.size(),
                     content.getScope(), content.getSpotifyUri());
            try {
                reResolve(content);
            } catch (WebClientResponseException e) {
                if (e.getStatusCode().value() == 429) {
                    String retryAfter = e.getHeaders().getFirst("Retry-After");
                    log.warn("Re-resolution: aborting at [{}/{}] due to Spotify 429 (Retry-After {}s) – remaining entries skipped",
                             i + 1, entries.size(), retryAfter);
                    return;
                }
                log.warn("Re-resolution failed for content {}: {}", content.getId(), e.getMessage());
            } catch (Exception e) {
                log.warn("Re-resolution failed for content {}: {}", content.getId(), e.getMessage());
            }
            if (i < entries.size() - 1) {
                try {
                    Thread.sleep(10_000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("Re-resolution interrupted after [{}/{}]", i + 1, entries.size());
                    return;
                }
            }
        }
        log.info("Re-resolution: done – {} entries processed", entries.size());
    }

    // ── Scope handlers ─────────────────────────────────────────────────────────

    private void resolveArtist(AllowedContent content, String familyId) {
        String artistId = idFromUri(content.getSpotifyUri());
        List<AlbumData> albums = spotifyClient.getArtistAlbums(familyId, artistId).block();
        if (albums == null) throw new IllegalStateException(
                "Spotify token unavailable for family " + familyId + "; content " + content.getId());

        for (AlbumData album : albums) {
            try {
                persistAlbumWithTracks(content, familyId, album);
            } catch (Exception e) {
                log.warn("Skipping album {} while resolving artist {}: {}",
                         album.uri(), content.getSpotifyUri(), e.getMessage());
            }
        }
    }

    private void resolveAlbum(AllowedContent content, String familyId) {
        String albumId = idFromUri(content.getSpotifyUri());
        AlbumData album = spotifyClient.getAlbumDetails(familyId, albumId).block();
        if (album == null) throw new IllegalStateException(
                "Spotify token unavailable for family " + familyId + "; content " + content.getId());
        persistAlbumWithTracks(content, familyId, album);
    }

    private void resolvePlaylist(AllowedContent content, String familyId) {
        String playlistId = idFromUri(content.getSpotifyUri());
        List<TrackData> tracks = spotifyClient.getPlaylistTracks(familyId, playlistId).block();
        if (tracks == null) throw new IllegalStateException(
                "Spotify token unavailable for family " + familyId + "; content " + content.getId());
        if (tracks.isEmpty()) return;
        persistPlaylistTracks(content, tracks);
    }

    private void resolveTrack(AllowedContent content, String familyId) {
        String trackId = idFromUri(content.getSpotifyUri());
        TrackData track = spotifyClient.getTrack(familyId, trackId).block();
        if (track == null) throw new IllegalStateException(
                "Spotify token unavailable for family " + familyId + "; content " + content.getId());

        String albumUri   = track.albumUri()   != null ? track.albumUri()   : content.getSpotifyUri();
        String albumTitle = track.albumTitle() != null ? track.albumTitle() : "Unknown Album";

        ResolvedAlbum resolvedAlbum = buildAndSaveAlbum(
                content, albumUri, albumTitle, track.albumImageUrl(),
                null, 1,
                classifier.classify(new SpotifyItemInfo("album", List.of(), albumTitle, 1, track.durationMs())));

        persistTrack(resolvedAlbum, track);
    }

    // ── Re-resolution (diff logic) ─────────────────────────────────────────────

    /**
     * Diffs the current resolved albums for {@code content} against a fresh Spotify
     * fetch.  Adds new albums (+ their tracks), removes albums no longer present.
     * Albums that exist in both old and new are left untouched.
     * Package-private for unit testing.
     */
    void reResolve(AllowedContent content) {
        String familyId = familyIdFor(content);
        if (familyId == null) {
            log.warn("Profile not found for content {} – skipping re-resolution", content.getId());
            return;
        }

        Map<String, ResolvedAlbum> existingByUri = albumRepo.findByAllowedContentId(content.getId())
                .stream()
                .collect(Collectors.toMap(ResolvedAlbum::getSpotifyAlbumUri, Function.identity()));

        if (content.getScope() == ContentScope.ARTIST) {
            reResolveArtist(content, familyId, existingByUri);
        } else {
            reResolvePlaylist(content, familyId, existingByUri);
        }

        content.setResolvedAt(Instant.now());
        contentRepo.save(content);
    }

    private void reResolveArtist(AllowedContent content, String familyId,
                                  Map<String, ResolvedAlbum> existingByUri) {
        String artistId = idFromUri(content.getSpotifyUri());
        List<AlbumData> freshAlbums = spotifyClient.getArtistAlbums(familyId, artistId).block();
        if (freshAlbums == null) freshAlbums = List.of();

        Set<String> freshUris = freshAlbums.stream().map(AlbumData::uri).collect(Collectors.toSet());

        // Remove albums no longer present
        removeStaleAlbums(existingByUri, freshUris);

        // Add new albums
        for (AlbumData album : freshAlbums) {
            if (!existingByUri.containsKey(album.uri())) {
                try {
                    persistAlbumWithTracks(content, familyId, album);
                } catch (WebClientResponseException e) {
                    if (e.getStatusCode().value() == 429) throw e; // let scheduler abort
                    log.warn("Skipping new album {} during artist re-resolution: {}",
                             album.uri(), e.getMessage());
                } catch (Exception e) {
                    log.warn("Skipping new album {} during artist re-resolution: {}",
                             album.uri(), e.getMessage());
                }
            }
        }
    }

    private void reResolvePlaylist(AllowedContent content, String familyId,
                                    Map<String, ResolvedAlbum> existingByUri) {
        String playlistId = idFromUri(content.getSpotifyUri());
        String freshSnapshot = spotifyClient.getPlaylistSnapshotId(familyId, playlistId).block();
        if (freshSnapshot != null && freshSnapshot.equals(content.getSpotifySnapshotId())) {
            log.info("Playlist {} snapshot unchanged ({}), skipping re-resolution",
                     content.getSpotifyUri(), freshSnapshot);
            return;
        }
        // Full delete + re-fetch: playlist order/composition may have changed entirely
        existingByUri.values().forEach(albumRepo::delete);
        resolvePlaylist(content, familyId);
        content.setSpotifySnapshotId(freshSnapshot);
    }

    private void removeStaleAlbums(Map<String, ResolvedAlbum> existingByUri, Set<String> freshUris) {
        for (Map.Entry<String, ResolvedAlbum> existing : existingByUri.entrySet()) {
            if (!freshUris.contains(existing.getKey())) {
                albumRepo.delete(existing.getValue());
            }
        }
    }

    // ── Persistence helpers ────────────────────────────────────────────────────

    /**
     * Fetches tracks for {@code album} from Spotify, classifies the album, then
     * persists the {@link ResolvedAlbum} and all its {@link ResolvedTrack}s.
     */
    private void persistAlbumWithTracks(AllowedContent content, String familyId, AlbumData album) {
        String albumId = idFromUri(album.uri());
        List<TrackData> tracks = spotifyClient.getAlbumTracks(
                familyId, albumId, album.uri(), album.title(), album.imageUrl()).block();
        if (tracks == null) tracks = List.of();

        long avgDurationMs = avgDuration(tracks);
        // Classify with Spotify genre data if available (ALBUM scope returns full album objects
        // with genres); fall back to the type already set on AllowedContent when genres are absent
        // (e.g. ARTIST scope returns SimplifiedAlbumObjects which have no genres field).
        ContentType contentType = album.genres().isEmpty()
                ? content.getContentType()
                : classifier.classify(new SpotifyItemInfo(
                        "album", album.genres(), album.title(), tracks.size(), avgDurationMs));

        ResolvedAlbum resolvedAlbum = buildAndSaveAlbum(
                content, album.uri(), album.title(), album.imageUrl(),
                album.releaseDate(), tracks.size(), contentType);

        for (TrackData track : tracks) {
            persistTrack(resolvedAlbum, track);
        }
    }

    /**
     * Persists a playlist's flat track list, grouping by source album URI while
     * preserving the original playlist position on every {@link ResolvedTrack}.
     */
    private void persistPlaylistTracks(AllowedContent content, List<TrackData> tracks) {
        // Build position map before grouping so playlist order is never lost
        Map<String, Integer> positionByUri = new LinkedHashMap<>();
        for (int i = 0; i < tracks.size(); i++) {
            positionByUri.putIfAbsent(tracks.get(i).uri(), i);
        }

        Map<String, List<TrackData>> byAlbum = groupByAlbum(tracks);

        for (Map.Entry<String, List<TrackData>> entry : byAlbum.entrySet()) {
            List<TrackData> albumTracks = entry.getValue();
            TrackData first = albumTracks.get(0);

            String albumTitle = first.albumTitle() != null ? first.albumTitle() : "Unknown Album";
            ContentType contentType = content.getContentType();

            ResolvedAlbum resolvedAlbum = buildAndSaveAlbum(
                    content, entry.getKey(), albumTitle, first.albumImageUrl(),
                    null, albumTracks.size(), contentType);

            for (TrackData track : albumTracks) {
                persistTrack(resolvedAlbum, track, positionByUri.get(track.uri()));
            }
        }
    }

    private ResolvedAlbum buildAndSaveAlbum(AllowedContent content,
                                             String uri, String title, String imageUrl,
                                             String releaseDate, int totalTracks,
                                             ContentType contentType) {
        ResolvedAlbum album = new ResolvedAlbum();
        album.setAllowedContentId(content.getId());
        album.setSpotifyAlbumUri(uri);
        album.setTitle(title);
        album.setImageUrl(imageUrl);
        album.setReleaseDate(releaseDate);
        album.setTotalTracks(totalTracks > 0 ? totalTracks : null);
        album.setContentType(contentType);
        album.setResolvedAt(Instant.now());
        return albumRepo.save(album);
    }

    private void persistTrack(ResolvedAlbum album, TrackData data) {
        persistTrack(album, data, null);
    }

    private void persistTrack(ResolvedAlbum album, TrackData data, Integer playlistPosition) {
        ResolvedTrack track = new ResolvedTrack();
        track.setResolvedAlbumId(album.getId());
        track.setSpotifyTrackUri(data.uri());
        track.setTitle(data.title() != null ? data.title() : "Unknown Track");
        track.setArtistName(data.artistName());
        track.setDurationMs(data.durationMs() > 0 ? data.durationMs() : null);
        track.setTrackNumber(data.trackNumber() > 0 ? data.trackNumber() : null);
        track.setDiscNumber(data.discNumber() > 0 ? data.discNumber() : null);
        track.setImageUrl(album.getImageUrl());
        track.setPlaylistPosition(playlistPosition);
        trackRepo.save(track);
    }

    // ── Utilities ──────────────────────────────────────────────────────────────

    private String familyIdFor(AllowedContent content) {
        return profileRepo.findById(content.getProfileId())
                .map(ChildProfile::getFamilyId)
                .orElse(null);
    }

    /** Groups a flat track list into {@code albumUri → tracks} while preserving order. */
    private static Map<String, List<TrackData>> groupByAlbum(Collection<TrackData> tracks) {
        Map<String, List<TrackData>> map = new LinkedHashMap<>();
        for (TrackData t : tracks) {
            if (t.albumUri() != null) {
                map.computeIfAbsent(t.albumUri(), k -> new ArrayList<>()).add(t);
            }
        }
        return map;
    }

    private static long avgDuration(List<TrackData> tracks) {
        if (tracks.isEmpty()) return 0L;
        return (long) tracks.stream().mapToLong(TrackData::durationMs).average().orElse(0);
    }

    static String idFromUri(String uri) {
        if (uri == null) return "";
        int idx = uri.lastIndexOf(':');
        return idx >= 0 ? uri.substring(idx + 1) : uri;
    }
}
