package at.kidstune.insights;

import at.kidstune.profile.ChildProfile;
import at.kidstune.profile.ProfileRepository;
import at.kidstune.spotify.SpotifyWebApiClient;
import at.kidstune.spotify.SpotifyWebApiClient.RawProfilePlayEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class RecentlyPlayedPoller {

    @Value("${insights.poller.enabled:true}")
    private boolean pollerEnabled;

    private static final Logger log = LoggerFactory.getLogger(RecentlyPlayedPoller.class);

    private final SpotifyWebApiClient  spotifyClient;
    private final ProfileRepository    profileRepository;
    private final PlayEventRepository  eventRepository;
    private final SessionBuilder       sessionBuilder;

    public RecentlyPlayedPoller(SpotifyWebApiClient spotifyClient,
                                ProfileRepository profileRepository,
                                PlayEventRepository eventRepository,
                                SessionBuilder sessionBuilder) {
        this.spotifyClient    = spotifyClient;
        this.profileRepository = profileRepository;
        this.eventRepository  = eventRepository;
        this.sessionBuilder   = sessionBuilder;
    }

    @Scheduled(fixedDelayString = "${insights.poller.interval-ms:180000}",
               initialDelayString = "${insights.poller.initial-delay-ms:300000}")
    public void poll() {
        if (!pollerEnabled) return;
        List<ChildProfile> profiles = profileRepository.findAll().stream()
                .filter(p -> p.getSpotifyRefreshToken() != null)
                .toList();

        if (profiles.isEmpty()) return;

        Flux.fromIterable(profiles)
            .flatMap(this::pollProfile, 1)   // sequential to avoid hammering Spotify
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe(
                null,
                e -> log.error("Unexpected poller error", e),
                () -> log.debug("Poll cycle complete for {} profiles", profiles.size()));
    }

    Mono<Void> pollProfile(ChildProfile profile) {
        String profileId = profile.getId();

        long afterMillis = eventRepository.findMaxPlayedAtByProfileId(profileId)
                .map(Instant::toEpochMilli)
                .orElse(Instant.now().minus(24, ChronoUnit.HOURS).toEpochMilli());

        return spotifyClient.getProfileRecentlyPlayed(profileId, afterMillis)
            .flatMap(items -> Mono.fromCallable(() -> {
                persistEvents(profileId, items);
                if (!items.isEmpty()) {
                    sessionBuilder.rebuildRecent(profileId);
                }
                clearInsightsError(profile);
                return (Void) null;
            }).subscribeOn(Schedulers.boundedElastic()))
            .onErrorResume(e -> handlePollError(profileId, e));
    }

    @Transactional
    void persistEvents(String profileId, List<RawProfilePlayEvent> items) {
        for (RawProfilePlayEvent item : items) {
            if (eventRepository.existsByProfileIdAndPlayedAtAndTrackId(
                    profileId, item.playedAt(), item.trackId())) {
                continue;
            }
            PlayEvent event = new PlayEvent();
            event.setProfileId(profileId);
            event.setPlayedAt(item.playedAt());
            event.setTrackId(item.trackId());
            event.setTrackName(truncate(item.trackName(), 255));
            event.setArtistName(truncate(item.artistName(), 255));
            event.setDurationMs(item.durationMs());
            event.setItemType(item.itemType());
            event.setContextType(item.contextType());
            event.setContextUri(truncate(item.contextUri(), 128));
            event.setRawJson(item.rawJson());
            event.setCreatedAt(Instant.now());
            eventRepository.save(event);
        }
    }

    private Mono<Void> handlePollError(String profileId, Throwable e) {
        if (isInvalidGrant(e)) {
            log.warn("Spotify token expired for profile {}; marking TOKEN_EXPIRED", profileId);
            return Mono.fromCallable(() -> {
                profileRepository.findById(profileId).ifPresent(p -> {
                    p.setInsightsStatus("TOKEN_EXPIRED");
                    profileRepository.save(p);
                });
                return (Void) null;
            }).subscribeOn(Schedulers.boundedElastic());
        }
        if (isRateLimit(e)) {
            log.warn("Spotify rate-limited for profile {}; will retry on next cycle", profileId);
        } else {
            log.warn("Poll error for profile {}: {}", profileId, e.getMessage());
        }
        return Mono.empty();
    }

    private void clearInsightsError(ChildProfile profile) {
        if (!"OK".equals(profile.getInsightsStatus())) {
            profileRepository.findById(profile.getId()).ifPresent(p -> {
                p.setInsightsStatus("OK");
                profileRepository.save(p);
            });
        }
    }

    private static boolean isInvalidGrant(Throwable e) {
        if (e instanceof WebClientResponseException wce) {
            return wce.getResponseBodyAsString().contains("invalid_grant");
        }
        String msg = e.getMessage();
        return msg != null && msg.contains("invalid_grant");
    }

    private static boolean isRateLimit(Throwable e) {
        return e instanceof WebClientResponseException.TooManyRequests;
    }

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) return s;
        return s.substring(0, max);
    }
}
