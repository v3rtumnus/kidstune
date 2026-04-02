package at.kidstune.web;

import at.kidstune.profile.ChildProfile;
import at.kidstune.profile.ProfileRepository;
import at.kidstune.requests.ContentRequest;
import at.kidstune.requests.ContentRequestService;
import at.kidstune.requests.ContentRequestStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Controller
@RequestMapping("/web/approve")
public class ApproveTokenController {

    private final ContentRequestService requestService;
    private final ProfileRepository     profileRepository;

    public ApproveTokenController(ContentRequestService requestService,
                                   ProfileRepository profileRepository) {
        this.requestService    = requestService;
        this.profileRepository = profileRepository;
    }

    /**
     * Public one-click approval endpoint linked from notification emails.
     * No authentication required – the approve_token itself is the authorization.
     */
    @GetMapping("/{token}")
    public Mono<String> approve(@PathVariable("token") String token, Model model) {
        return requestService.approveByToken(token)
                .flatMap(request -> Mono.fromCallable(() -> {
                    if (request.getStatus() == ContentRequestStatus.APPROVED
                            && request.getApproveToken() == null) {
                        // Just approved now – show success
                        ChildProfile profile = profileRepository
                                .findById(request.getProfileId()).orElse(null);
                        model.addAttribute("childName",
                                profile != null ? profile.getName() : "Dein Kind");
                        model.addAttribute("title", request.getTitle());
                        return "web/approve/success";
                    }
                    // Was already approved/rejected/expired before this click
                    return statusPage(request.getStatus(), model);
                }).subscribeOn(Schedulers.boundedElastic()))
                .onErrorResume(org.springframework.web.server.ResponseStatusException.class, ex -> {
                    // Token not found or already nulled
                    model.addAttribute("status", "HANDLED");
                    return Mono.just("web/approve/handled");
                });
    }

    private String statusPage(ContentRequestStatus status, Model model) {
        model.addAttribute("status", status.name());
        return switch (status) {
            case EXPIRED -> "web/approve/expired";
            default      -> "web/approve/handled";
        };
    }
}
