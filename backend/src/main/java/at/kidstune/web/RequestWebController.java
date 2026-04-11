package at.kidstune.web;

import at.kidstune.profile.ChildProfile;
import at.kidstune.profile.ProfileRepository;
import at.kidstune.requests.ContentRequest;
import at.kidstune.requests.ContentRequestRepository;
import at.kidstune.requests.ContentRequestService;
import at.kidstune.requests.ContentRequestStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/web/requests")
public class RequestWebController {

    private final ContentRequestRepository requestRepository;
    private final ContentRequestService    requestService;
    private final ProfileRepository        profileRepository;
    private final AvatarHelper             avatarHelper;

    public RequestWebController(ContentRequestRepository requestRepository,
                                ContentRequestService requestService,
                                ProfileRepository profileRepository,
                                AvatarHelper avatarHelper) {
        this.requestRepository = requestRepository;
        this.requestService    = requestService;
        this.profileRepository = profileRepository;
        this.avatarHelper      = avatarHelper;
    }

    // ── GET /web/requests ─────────────────────────────────────────────────────

    @GetMapping
    public Mono<String> index(Model model, @AuthenticationPrincipal String familyId) {
        return Mono.fromCallable(() -> {
            List<String> profileIds = profileRepository.findByFamilyId(familyId)
                    .stream().map(ChildProfile::getId).toList();
            long pendingCount = profileIds.isEmpty() ? 0 :
                    requestRepository.countByProfileIdInAndStatus(profileIds, ContentRequestStatus.PENDING);
            model.addAttribute("familyId",     familyId);
            model.addAttribute("pendingCount", pendingCount);
            return "web/requests/index";
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // ── GET /web/requests/pending (HTMX partial) ──────────────────────────────

    @GetMapping("/pending")
    public Mono<String> pendingPartial(Model model, @AuthenticationPrincipal String familyId) {
        return Mono.fromCallable(() -> {
            List<ChildProfile> profiles = profileRepository.findByFamilyId(familyId);
            List<String> profileIds     = profiles.stream().map(ChildProfile::getId).toList();

            Map<String, ChildProfile> profileMap = profiles.stream()
                    .collect(Collectors.toMap(ChildProfile::getId, p -> p));

            List<ContentRequest> pending = profileIds.isEmpty()
                    ? List.of()
                    : requestRepository.findByProfileIdInAndStatusOrderByRequestedAtDesc(
                            profileIds, ContentRequestStatus.PENDING);

            List<RequestView> views = pending.stream()
                    .map(r -> toView(r, profileMap.get(r.getProfileId())))
                    .toList();

            model.addAttribute("requests",     views);
            model.addAttribute("familyId",     familyId);
            model.addAttribute("hasSiblings",  profiles.size() > 1);
            return "web/fragments/pending-requests :: requests";
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // ── GET /web/requests/history (HTMX partial) ─────────────────────────────

    @GetMapping("/history")
    public Mono<String> historyPartial(
            @RequestParam(value = "tab", defaultValue = "APPROVED") String tab,
            Model model,
            @AuthenticationPrincipal String familyId) {

        ContentRequestStatus status;
        try { status = ContentRequestStatus.valueOf(tab.toUpperCase()); }
        catch (IllegalArgumentException e) { status = ContentRequestStatus.APPROVED; }

        final ContentRequestStatus finalStatus = status;

        return Mono.fromCallable(() -> {
            List<ChildProfile> profiles = profileRepository.findByFamilyId(familyId);
            List<String> profileIds     = profiles.stream().map(ChildProfile::getId).toList();

            Map<String, ChildProfile> profileMap = profiles.stream()
                    .collect(Collectors.toMap(ChildProfile::getId, p -> p));

            List<ContentRequest> requests = profileIds.isEmpty()
                    ? List.of()
                    : requestRepository.findByProfileIdInAndStatusInOrderByResolvedAtDesc(
                            profileIds, List.of(finalStatus));

            List<RequestView> views = requests.stream()
                    .map(r -> toView(r, profileMap.get(r.getProfileId())))
                    .toList();

            model.addAttribute("requests", views);
            model.addAttribute("tab",      finalStatus.name());
            return "web/fragments/pending-requests :: history";
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // ── POST /web/requests/{id}/approve ──────────────────────────────────────

    @PostMapping("/{id}/approve")
    public Mono<String> approve(
            @PathVariable("id") String id,
            Model model,
            @AuthenticationPrincipal String familyId) {

        return requestService.approveRequest(id, familyId)
                .thenReturn("web/fragments/empty :: empty");
    }

    // ── POST /web/requests/{id}/approve-all ──────────────────────────────────

    @PostMapping("/{id}/approve-all")
    public Mono<String> approveAll(
            @PathVariable("id") String id,
            Model model,
            @AuthenticationPrincipal String familyId) {

        return requestService.approveForAllProfiles(id, familyId)
                .thenReturn("web/fragments/empty :: empty");
    }

    // ── POST /web/requests/{id}/reject ───────────────────────────────────────

    @PostMapping("/{id}/reject")
    public Mono<String> reject(
            @PathVariable("id") String id,
            Model model,
            @AuthenticationPrincipal String familyId,
            ServerWebExchange exchange) {

        return exchange.getFormData().flatMap(form -> {
            String note = form.getFirst("note");
            return requestService.rejectRequest(id, familyId, note)
                    .thenReturn("web/fragments/empty :: empty");
        });
    }

    // ── POST /web/requests/bulk-approve ──────────────────────────────────────

    @PostMapping("/bulk-approve")
    public Mono<Void> bulkApprove(
            ServerWebExchange exchange,
            @AuthenticationPrincipal String familyId) {

        return exchange.getFormData().flatMap(form -> {
            List<String> requestIds = form.get("requestIds");
            Mono<Void> action = (requestIds != null && !requestIds.isEmpty())
                    ? requestService.bulkApprove(requestIds, familyId)
                    : Mono.empty();

            return action.then(Mono.fromRunnable(() -> {
                exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.FOUND);
                exchange.getResponse().getHeaders().setLocation(URI.create("/web/requests"));
            })).then(exchange.getResponse().setComplete());
        });
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private RequestView toView(ContentRequest request, ChildProfile profile) {
        String emoji = profile != null ? avatarHelper.emoji(profile.getAvatarIcon()) : "?";
        String color = profile != null ? avatarHelper.cssColor(profile.getAvatarColor()) : "#6c757d";
        return new RequestView(request, profile, emoji, color);
    }

    public record RequestView(
            ContentRequest  request,
            ChildProfile    profile,
            String          emoji,
            String          cssColor
    ) {}
}