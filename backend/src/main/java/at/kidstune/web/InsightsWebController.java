package at.kidstune.web;

import at.kidstune.insights.InsightsService;
import at.kidstune.insights.dto.DayResponse;
import at.kidstune.insights.dto.RangeResponse;
import at.kidstune.profile.ChildProfile;
import at.kidstune.profile.ProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Controller
@RequestMapping("/web/insights")
public class InsightsWebController {

    private final ProfileRepository profileRepository;
    private final InsightsService   insightsService;

    public InsightsWebController(ProfileRepository profileRepository,
                                 InsightsService insightsService) {
        this.profileRepository = profileRepository;
        this.insightsService   = insightsService;
    }

    // ── Today (default) ───────────────────────────────────────────────────────

    @GetMapping({"", "/"})
    public Mono<String> today(
            Model model,
            @AuthenticationPrincipal String familyId,
            @RequestParam(defaultValue = "Europe/Vienna") String tz) {

        return Mono.fromCallable(() -> {
            List<ChildProfile> profiles = profileRepository.findByFamilyId(familyId);
            model.addAttribute("profiles", profiles);
            model.addAttribute("familyId", familyId);
            model.addAttribute("tz", tz);
            model.addAttribute("todayDate", LocalDate.now(parseZone(tz)).format(DateTimeFormatter.ISO_LOCAL_DATE));
            return "web/insights/today";
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // ── Day detail ────────────────────────────────────────────────────────────

    @GetMapping("/profiles/{profileId}/day")
    public Mono<String> day(
            @PathVariable String profileId,
            @RequestParam(required = false) String date,
            @RequestParam(defaultValue = "Europe/Vienna") String tz,
            Model model,
            @AuthenticationPrincipal String familyId) {

        return Mono.fromCallable(() -> {
            profileRepository.findById(profileId).ifPresent(p -> {
                if (!p.getFamilyId().equals(familyId)) return;
                model.addAttribute("profile", p);
            });
            String effectiveDate = (date == null || date.isBlank())
                    ? LocalDate.now(parseZone(tz)).format(DateTimeFormatter.ISO_LOCAL_DATE)
                    : date;
            model.addAttribute("date", effectiveDate);
            model.addAttribute("profileId", profileId);
            model.addAttribute("familyId", familyId);
            model.addAttribute("tz", tz);
            return "web/insights/day";
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // ── Range summary ─────────────────────────────────────────────────────────

    @GetMapping("/profiles/{profileId}/range")
    public Mono<String> range(
            @PathVariable String profileId,
            @RequestParam(defaultValue = "week") String period,
            @RequestParam(defaultValue = "Europe/Vienna") String tz,
            Model model,
            @AuthenticationPrincipal String familyId) {

        return Mono.fromCallable(() -> {
            profileRepository.findById(profileId).ifPresent(p -> {
                if (!p.getFamilyId().equals(familyId)) return;
                model.addAttribute("profile", p);
            });

            ZoneId zone = parseZone(tz);
            LocalDate today = LocalDate.now(zone);
            LocalDate from  = "month".equals(period) ? today.minusDays(29) : today.minusDays(6);

            model.addAttribute("profileId", profileId);
            model.addAttribute("familyId", familyId);
            model.addAttribute("tz", tz);
            model.addAttribute("period", period);
            model.addAttribute("from", from.format(DateTimeFormatter.ISO_LOCAL_DATE));
            model.addAttribute("to",   today.format(DateTimeFormatter.ISO_LOCAL_DATE));
            return "web/insights/range";
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // ── JSON data endpoints (called by dashboard JS; session-auth) ───────────

    @GetMapping("/profiles/{profileId}/day-data")
    @ResponseBody
    public Mono<ResponseEntity<DayResponse>> dayData(
            @PathVariable String profileId,
            @RequestParam(required = false) String date,
            @RequestParam(defaultValue = "Europe/Vienna") String tz,
            @AuthenticationPrincipal String familyId) {

        return profileRepository.findById(profileId)
                .filter(p -> p.getFamilyId().equals(familyId))
                .map(p -> insightsService.getDay(profileId, date, tz)
                        .map(ResponseEntity::ok))
                .orElse(Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build()));
    }

    @GetMapping("/profiles/{profileId}/range-data")
    @ResponseBody
    public Mono<ResponseEntity<RangeResponse>> rangeData(
            @PathVariable String profileId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "Europe/Vienna") String tz,
            @AuthenticationPrincipal String familyId) {

        return profileRepository.findById(profileId)
                .filter(p -> p.getFamilyId().equals(familyId))
                .map(p -> insightsService.getRange(profileId, from, to, tz)
                        .map(ResponseEntity::ok))
                .orElse(Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build()));
    }

    // ── HTMX fragments ────────────────────────────────────────────────────────

    @GetMapping("/profiles/{profileId}/fragments/now-playing")
    public Mono<String> nowPlayingFragment(
            @PathVariable String profileId,
            Model model,
            @AuthenticationPrincipal String familyId) {

        return insightsService.getLive(profileId)
            .map(live -> {
                model.addAttribute("live", live);
                model.addAttribute("profileId", profileId);
                return "web/insights/fragments/now-playing-badge";
            });
    }

    @GetMapping("/profiles/{profileId}/fragments/today-card")
    public Mono<String> todayCardFragment(
            @PathVariable String profileId,
            @RequestParam(defaultValue = "Europe/Vienna") String tz,
            Model model,
            @AuthenticationPrincipal String familyId) {

        return insightsService.getToday(profileId, tz)
            .map(today -> {
                model.addAttribute("today", today);
                model.addAttribute("profileId", profileId);
                model.addAttribute("tz", tz);
                return "web/insights/fragments/timeline-strip";
            });
    }

    private static ZoneId parseZone(String tz) {
        try {
            return ZoneId.of(tz);
        } catch (Exception e) {
            return ZoneId.of("Europe/Vienna");
        }
    }
}
