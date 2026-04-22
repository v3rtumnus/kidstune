package at.kidstune.insights;

import at.kidstune.common.OwnershipService;
import at.kidstune.common.SecurityUtils;
import at.kidstune.insights.dto.DayResponse;
import at.kidstune.insights.dto.LiveResponse;
import at.kidstune.insights.dto.RangeResponse;
import at.kidstune.insights.dto.TodayResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class InsightsController {

    private final InsightsService    insightsService;
    private final OwnershipService   ownershipService;

    public InsightsController(InsightsService insightsService,
                              OwnershipService ownershipService) {
        this.insightsService  = insightsService;
        this.ownershipService = ownershipService;
    }

    @GetMapping("/api/v1/insights/profiles/{profileId}/today")
    public Mono<ResponseEntity<TodayResponse>> today(
            @PathVariable String profileId,
            @RequestParam(defaultValue = "Europe/Vienna") String tz) {

        return SecurityUtils.getFamilyId()
            .flatMap(familyId -> ownershipService.requireProfileOwnership(profileId, familyId))
            .then(insightsService.getToday(profileId, tz))
            .map(ResponseEntity::ok);
    }

    @GetMapping("/api/v1/insights/profiles/{profileId}/day")
    public Mono<ResponseEntity<DayResponse>> day(
            @PathVariable String profileId,
            @RequestParam(required = false) String date,
            @RequestParam(defaultValue = "Europe/Vienna") String tz) {

        return SecurityUtils.getFamilyId()
            .flatMap(familyId -> ownershipService.requireProfileOwnership(profileId, familyId))
            .then(insightsService.getDay(profileId, date, tz))
            .map(ResponseEntity::ok);
    }

    @GetMapping("/api/v1/insights/profiles/{profileId}/range")
    public Mono<ResponseEntity<RangeResponse>> range(
            @PathVariable String profileId,
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(defaultValue = "Europe/Vienna") String tz) {

        return SecurityUtils.getFamilyId()
            .flatMap(familyId -> ownershipService.requireProfileOwnership(profileId, familyId))
            .then(insightsService.getRange(profileId, from, to, tz))
            .map(ResponseEntity::ok);
    }

    @GetMapping("/api/v1/insights/profiles/{profileId}/live")
    public Mono<ResponseEntity<LiveResponse>> live(
            @PathVariable String profileId) {

        return SecurityUtils.getFamilyId()
            .flatMap(familyId -> ownershipService.requireProfileOwnership(profileId, familyId))
            .then(insightsService.getLive(profileId))
            .map(r -> r.playing() ? ResponseEntity.ok(r)
                    : ResponseEntity.status(r.connected() ? 204 : 200).body(r));
    }
}
