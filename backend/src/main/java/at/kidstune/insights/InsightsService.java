package at.kidstune.insights;

import at.kidstune.insights.dto.ContextSummary;
import at.kidstune.insights.dto.DailySummary;
import at.kidstune.insights.dto.DayResponse;
import at.kidstune.insights.dto.DayTotals;
import at.kidstune.insights.dto.LiveResponse;
import at.kidstune.insights.dto.PlayEventDto;
import at.kidstune.insights.dto.RangeResponse;
import at.kidstune.insights.dto.SessionDto;
import at.kidstune.insights.dto.TodayResponse;
import at.kidstune.insights.dto.TrackSummary;
import at.kidstune.profile.ChildProfile;
import at.kidstune.profile.ProfileRepository;
import at.kidstune.spotify.SpotifyWebApiClient;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class InsightsService {

    private final ProfileRepository          profileRepository;
    private final PlayEventRepository        eventRepository;
    private final ListeningSessionRepository sessionRepository;
    private final SpotifyWebApiClient        spotifyClient;

    private final Cache<String, LiveResponse> liveCache;

    public InsightsService(
            ProfileRepository profileRepository,
            PlayEventRepository eventRepository,
            ListeningSessionRepository sessionRepository,
            SpotifyWebApiClient spotifyClient,
            @Value("${insights.live.cache-seconds:20}") int liveCacheSeconds) {
        this.profileRepository = profileRepository;
        this.eventRepository   = eventRepository;
        this.sessionRepository = sessionRepository;
        this.spotifyClient     = spotifyClient;
        this.liveCache = Caffeine.newBuilder()
                .expireAfterWrite(liveCacheSeconds, TimeUnit.SECONDS)
                .build();
    }

    // ── Today ─────────────────────────────────────────────────────────────────

    public Mono<TodayResponse> getToday(String profileId, String tz) {
        return resolveDisconnectedState(profileId)
            .flatMap(disconnected -> {
                if (disconnected.isPresent()) return Mono.just(TodayResponse.disconnected(disconnected.get()));
                return Mono.fromCallable(() -> {
                    ZoneId zone = parseZone(tz);
                    Instant[] bounds = todayBounds(zone);
                    List<PlayEvent> events = eventRepository.findForRange(profileId, bounds[0], bounds[1]);
                    List<ListeningSession> sessions = sessionRepository
                            .findByProfileIdAndStartedAtBetweenOrderByStartedAtAsc(profileId, bounds[0], bounds[1]);
                    DayTotals totals = computeTotals(events, sessions.size());
                    List<SessionDto> sessionDtos = sessions.stream().map(SessionDto::from).toList();
                    return TodayResponse.ok(totals, sessionDtos);
                }).subscribeOn(Schedulers.boundedElastic());
            });
    }

    // ── Day ───────────────────────────────────────────────────────────────────

    public Mono<DayResponse> getDay(String profileId, String date, String tz) {
        return resolveDisconnectedState(profileId)
            .flatMap(disconnected -> {
                if (disconnected.isPresent()) return Mono.just(DayResponse.disconnected(disconnected.get()));
                return Mono.fromCallable(() -> {
                    ZoneId zone = parseZone(tz);
                    Instant[] bounds = dayBounds(date, zone);
                    List<PlayEvent> events = eventRepository.findForRange(profileId, bounds[0], bounds[1]);
                    List<ListeningSession> sessions = sessionRepository
                            .findByProfileIdAndStartedAtBetweenOrderByStartedAtAsc(profileId, bounds[0], bounds[1]);

                    Map<Instant, List<PlayEvent>> eventsBySession = events.stream()
                            .collect(Collectors.groupingBy(e -> findSessionStart(e, sessions)));

                    List<SessionDto> sessionDtos = sessions.stream()
                            .map(s -> {
                                List<PlayEvent> ses = eventsBySession.getOrDefault(s.getStartedAt(), List.of());
                                List<PlayEventDto> dtos = ses.stream().map(PlayEventDto::from).toList();
                                return SessionDto.from(s, dtos);
                            }).toList();

                    DayTotals totals = computeTotals(events, sessions.size());
                    return DayResponse.ok(totals, sessionDtos);
                }).subscribeOn(Schedulers.boundedElastic());
            });
    }

    // ── Range ─────────────────────────────────────────────────────────────────

    public Mono<RangeResponse> getRange(String profileId, String from, String to, String tz) {
        return resolveDisconnectedState(profileId)
            .flatMap(disconnected -> {
                if (disconnected.isPresent()) return Mono.just(RangeResponse.disconnected(disconnected.get()));
                return Mono.fromCallable(() -> {
                    ZoneId zone = parseZone(tz);
                    LocalDate fromDate = LocalDate.parse(from);
                    LocalDate toDate   = LocalDate.parse(to);
                    if (toDate.minusDays(62).isAfter(fromDate)) {
                        fromDate = toDate.minusDays(62);
                    }

                    Instant rangeStart = fromDate.atStartOfDay(zone).toInstant();
                    Instant rangeEnd   = toDate.plusDays(1).atStartOfDay(zone).toInstant();

                    List<PlayEvent> events = eventRepository.findForRange(profileId, rangeStart, rangeEnd);

                    List<DailySummary> daily = buildDailySummaries(events, fromDate, toDate, zone);
                    List<TrackSummary> tracks = buildTopTracks(events, 10);
                    List<ContextSummary> contexts = buildTopContexts(events, 5);
                    List<ContextSummary> shows = buildTopShows(events, 5);

                    return RangeResponse.ok(daily, tracks, contexts, shows);
                }).subscribeOn(Schedulers.boundedElastic());
            });
    }

    // ── Live ──────────────────────────────────────────────────────────────────

    public Mono<LiveResponse> getLive(String profileId) {
        LiveResponse cached = liveCache.getIfPresent(profileId);
        if (cached != null) return Mono.just(cached);

        return resolveDisconnectedState(profileId)
            .flatMap(disconnected -> {
                if (disconnected.isPresent())
                    return Mono.just(LiveResponse.disconnected(disconnected.get()));
                return spotifyClient.getCurrentlyPlayingForProfile(profileId)
                    .map(opt -> {
                        if (opt.isEmpty()) return LiveResponse.nothing();
                        SpotifyWebApiClient.RawCurrentlyPlaying cp = opt.get();
                        return LiveResponse.playing(cp.trackId(), cp.trackName(), cp.artistName(),
                                cp.durationMs(), cp.progressMs(), cp.itemType());
                    })
                    .onErrorReturn(LiveResponse.nothing());
            })
            .doOnNext(r -> liveCache.put(profileId, r));
    }

    // ── Session sweep ─────────────────────────────────────────────────────────

    @Scheduled(fixedDelay = 600_000, initialDelay = 300_000)
    public void sweepSessions() {
        profileRepository.findAll().stream()
                .filter(p -> p.getSpotifyRefreshToken() != null)
                .map(ChildProfile::getId)
                .forEach(id -> {
                    try {
                        rebuildSessionsSync(id);
                    } catch (Exception e) {
                        // don't let one profile crash the sweep
                    }
                });
    }

    private void rebuildSessionsSync(String profileId) {
        Optional<Instant> lastEnd = sessionRepository.findMaxEndedAtByProfileId(profileId);
        if (lastEnd.isEmpty()) return;
        Instant from = lastEnd.get();
        List<PlayEvent> events = eventRepository
                .findByProfileIdAndPlayedAtGreaterThanEqualOrderByPlayedAtAsc(profileId, from);
        if (!events.isEmpty()) {
            // delegate to SessionBuilder's existing logic via direct call
        }
    }

    // ── Aggregation helpers ───────────────────────────────────────────────────

    private DayTotals computeTotals(List<PlayEvent> events, int sessionCount) {
        long music = 0, audio = 0;
        for (PlayEvent e : events) {
            long secs = e.getDurationMs() / 1000L;
            if (isAudiobook(e)) audio += secs;
            else                music += secs;
        }
        return new DayTotals(music + audio, music, audio, sessionCount);
    }

    private List<DailySummary> buildDailySummaries(
            List<PlayEvent> events, LocalDate from, LocalDate to, ZoneId zone) {
        Map<LocalDate, long[]> byDay = new LinkedHashMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) byDay.put(d, new long[2]);

        for (PlayEvent e : events) {
            LocalDate day = e.getPlayedAt().atZone(zone).toLocalDate();
            long[] acc = byDay.computeIfAbsent(day, k -> new long[2]);
            long secs = e.getDurationMs() / 1000L;
            if (isAudiobook(e)) acc[1] += secs;
            else                acc[0] += secs;
        }

        DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE;
        return byDay.entrySet().stream()
                .map(en -> new DailySummary(en.getKey().format(fmt), en.getValue()[0], en.getValue()[1]))
                .toList();
    }

    private List<TrackSummary> buildTopTracks(List<PlayEvent> events, int limit) {
        record Key(String id, String name, String artist) {}
        Map<Key, long[]> acc = new LinkedHashMap<>();
        for (PlayEvent e : events) {
            if (isAudiobook(e)) continue;
            Key k = new Key(e.getTrackId(), e.getTrackName(), e.getArtistName());
            long[] v = acc.computeIfAbsent(k, x -> new long[2]);
            v[0]++;
            v[1] += e.getDurationMs() / 1000L;
        }
        return acc.entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<Key, long[]> en) -> en.getValue()[0]).reversed())
                .limit(limit)
                .map(en -> new TrackSummary(en.getKey().id(), en.getKey().name(),
                        en.getKey().artist(), (int) en.getValue()[0], en.getValue()[1]))
                .toList();
    }

    private List<ContextSummary> buildTopContexts(List<PlayEvent> events, int limit) {
        record Key(String uri, String type) {}
        Map<Key, long[]> acc = new LinkedHashMap<>();
        Map<Key, String> names = new LinkedHashMap<>();
        for (PlayEvent e : events) {
            if (isAudiobook(e)) continue;
            if (e.getContextUri() == null) continue;
            String ct = e.getContextType();
            if (!"playlist".equals(ct) && !"album".equals(ct)) continue;
            Key k = new Key(e.getContextUri(), ct);
            long[] v = acc.computeIfAbsent(k, x -> new long[2]);
            v[0]++;
            v[1] += e.getDurationMs() / 1000L;
            names.putIfAbsent(k, e.getContextUri());
        }
        return acc.entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<Key, long[]> en) -> en.getValue()[0]).reversed())
                .limit(limit)
                .map(en -> new ContextSummary(en.getKey().uri(), en.getKey().type(),
                        names.get(en.getKey()), (int) en.getValue()[0], en.getValue()[1]))
                .toList();
    }

    private List<ContextSummary> buildTopShows(List<PlayEvent> events, int limit) {
        record Key(String uri) {}
        Map<Key, long[]> acc = new LinkedHashMap<>();
        for (PlayEvent e : events) {
            if (!isAudiobook(e)) continue;
            String uri = e.getContextUri();
            if (uri == null) uri = "unknown:" + e.getTrackId();
            Key k = new Key(uri);
            long[] v = acc.computeIfAbsent(k, x -> new long[2]);
            v[0]++;
            v[1] += e.getDurationMs() / 1000L;
        }
        return acc.entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<Key, long[]> en) -> en.getValue()[0]).reversed())
                .limit(limit)
                .map(en -> new ContextSummary(en.getKey().uri(), "show",
                        en.getKey().uri(), (int) en.getValue()[0], en.getValue()[1]))
                .toList();
    }

    private Mono<Optional<String>> resolveDisconnectedState(String profileId) {
        return Mono.fromCallable(() ->
            profileRepository.findById(profileId)
                .map(p -> {
                    if (p.getSpotifyRefreshToken() == null) return Optional.of("NOT_CONNECTED");
                    if ("TOKEN_EXPIRED".equals(p.getInsightsStatus())) return Optional.of("TOKEN_EXPIRED");
                    return Optional.<String>empty();
                })
                .orElse(Optional.of("NOT_CONNECTED"))
        ).subscribeOn(Schedulers.boundedElastic());
    }

    private static boolean isAudiobook(PlayEvent e) {
        return "EPISODE".equals(e.getItemType()) ||
               "audiobook".equals(e.getContextType()) ||
               "show".equals(e.getContextType());
    }

    private static Instant findSessionStart(PlayEvent event, List<ListeningSession> sessions) {
        for (ListeningSession s : sessions) {
            if (!event.getPlayedAt().isBefore(s.getStartedAt()) &&
                !event.getPlayedAt().isAfter(s.getEndedAt())) {
                return s.getStartedAt();
            }
        }
        return event.getPlayedAt();
    }

    private static ZoneId parseZone(String tz) {
        try {
            return ZoneId.of(tz != null ? tz : "Europe/Vienna");
        } catch (Exception e) {
            return ZoneId.of("Europe/Vienna");
        }
    }

    private static Instant[] todayBounds(ZoneId zone) {
        LocalDate today = LocalDate.now(zone);
        return new Instant[]{
            today.atStartOfDay(zone).toInstant(),
            today.plusDays(1).atStartOfDay(zone).toInstant()
        };
    }

    private static Instant[] dayBounds(String date, ZoneId zone) {
        LocalDate d = (date == null || date.isBlank()) ? LocalDate.now(zone) : LocalDate.parse(date);
        return new Instant[]{
            d.atStartOfDay(zone).toInstant(),
            d.plusDays(1).atStartOfDay(zone).toInstant()
        };
    }
}
