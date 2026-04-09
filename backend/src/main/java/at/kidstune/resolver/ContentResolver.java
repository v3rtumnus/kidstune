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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
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
        } finally {
            activeJobs.decrementAndGet();
        }
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
            contentRepo.save(content);
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
        List<AllowedContent> entries = contentRepo.findByScopeIn(
                List.of(ContentScope.ARTIST, ContentScope.PLAYLIST));

        log.info("Re-resolution: {} ARTIST/PLAYLIST entries to process", entries.size());
        for (AllowedContent content : entries) {
            try {
                reResolve(content);
            } catch (Exception e) {
                log.warn("Re-resolution failed for content {}: {}", content.getId(), e.getMessage());
            }
        }
    }

    // ── Scope handlers ─────────────────────────────────────────────────────────

    private void resolveArtist(AllowedContent content, String familyId) {
        String artistId = idFromUri(content.getSpotifyUri());
        List<AlbumData> albums = spotifyClient.getArtistAlbums(familyId, artistId).block();
        if (albums == null) return;

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
        if (album == null) return;
        persistAlbumWithTracks(content, familyId, album);
    }

    private void resolvePlaylist(AllowedContent content, String familyId) {
        String playlistId = idFromUri(content.getSpotifyUri());
        List<TrackData> tracks = spotifyClient.getPlaylistTracks(familyId, playlistId).block();
        if (tracks == null || tracks.isEmpty()) return;
        persistPlaylistTracks(content, groupByAlbum(tracks));
    }

    private void resolveTrack(AllowedContent content, String familyId) {
        String trackId = idFromUri(content.getSpotifyUri());
        TrackData track = spotifyClient.getTrack(familyId, trackId).block();
        if (track == null) return;

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
        List<TrackData> freshTracks = spotifyClient.getPlaylistTracks(familyId, playlistId).block();
        if (freshTracks == null) freshTracks = List.of();

        Map<String, List<TrackData>> freshByAlbum = groupByAlbum(freshTracks);

        Set<String> freshUris = freshByAlbum.keySet();

        // Remove albums no longer present
        removeStaleAlbums(existingByUri, freshUris);

        // Add new albums (with their tracks from the already-fetched batch)
        for (Map.Entry<String, List<TrackData>> entry : freshByAlbum.entrySet()) {
            if (!existingByUri.containsKey(entry.getKey())) {
                try {
                    persistPlaylistTracks(content, Map.of(entry.getKey(), entry.getValue()));
                } catch (Exception e) {
                    log.warn("Skipping new album {} during playlist re-resolution: {}",
                             entry.getKey(), e.getMessage());
                }
            }
        }
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
     * Persists playlist tracks already grouped by album URI.
     * No extra Spotify call needed – album info is embedded in each {@link TrackData}.
     */
    private void persistPlaylistTracks(AllowedContent content,
                                        Map<String, List<TrackData>> byAlbum) {
        for (Map.Entry<String, List<TrackData>> entry : byAlbum.entrySet()) {
            List<TrackData> albumTracks = entry.getValue();
            TrackData first = albumTracks.get(0);

            long avgDurationMs = avgDuration(albumTracks);
            String albumTitle = first.albumTitle() != null ? first.albumTitle() : "Unknown Album";

            // Playlist tracks carry no genre data – inherit from the AllowedContent type
            ContentType contentType = content.getContentType();

            ResolvedAlbum resolvedAlbum = buildAndSaveAlbum(
                    content, entry.getKey(), albumTitle, first.albumImageUrl(),
                    null, albumTracks.size(), contentType);

            for (TrackData track : albumTracks) {
                persistTrack(resolvedAlbum, track);
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
        ResolvedTrack track = new ResolvedTrack();
        track.setResolvedAlbumId(album.getId());
        track.setSpotifyTrackUri(data.uri());
        track.setTitle(data.title() != null ? data.title() : "Unknown Track");
        track.setArtistName(data.artistName());
        track.setDurationMs(data.durationMs() > 0 ? data.durationMs() : null);
        track.setTrackNumber(data.trackNumber() > 0 ? data.trackNumber() : null);
        track.setDiscNumber(data.discNumber() > 0 ? data.discNumber() : null);
        track.setImageUrl(album.getImageUrl());
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
