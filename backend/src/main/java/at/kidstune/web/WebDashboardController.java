package at.kidstune.web;

import at.kidstune.content.AllowedContent;
import at.kidstune.content.ContentRepository;
import at.kidstune.device.PairedDeviceRepository;
import at.kidstune.profile.ProfileRepository;
import at.kidstune.requests.ContentRequestRepository;
import at.kidstune.requests.ContentRequestStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

@Controller
@RequestMapping("/web")
public class WebDashboardController {

    private final ProfileRepository       profileRepository;
    private final ContentRepository       contentRepository;
    private final ContentRequestRepository requestRepository;
    private final PairedDeviceRepository  deviceRepository;

    public WebDashboardController(
            ProfileRepository profileRepository,
            ContentRepository contentRepository,
            ContentRequestRepository requestRepository,
            PairedDeviceRepository deviceRepository) {
        this.profileRepository = profileRepository;
        this.contentRepository = contentRepository;
        this.requestRepository  = requestRepository;
        this.deviceRepository   = deviceRepository;
    }

    @GetMapping("/dashboard")
    public Mono<String> dashboard(Model model, @AuthenticationPrincipal String familyId) {
        return Mono.fromCallable(() -> {
            List<String> profileIds = profileRepository.findByFamilyId(familyId)
                    .stream().map(p -> p.getId()).toList();

            long profileCount        = profileIds.size();
            long contentCount        = profileIds.isEmpty() ? 0 : contentRepository.countByProfileIdIn(profileIds);
            long pendingRequestCount = profileIds.isEmpty() ? 0 :
                    requestRepository.countByProfileIdInAndStatus(profileIds, ContentRequestStatus.PENDING);
            long deviceCount         = deviceRepository.countByFamilyId(familyId);
            List<AllowedContent> recentActivity = profileIds.isEmpty() ? List.of() :
                    contentRepository.findRecentByProfileIds(profileIds, PageRequest.of(0, 5));

            model.addAttribute("profileCount",        profileCount);
            model.addAttribute("contentCount",        contentCount);
            model.addAttribute("pendingRequestCount", pendingRequestCount);
            model.addAttribute("deviceCount",         deviceCount);
            model.addAttribute("recentActivity",      recentActivity);
            model.addAttribute("familyId",            familyId);
            return "web/dashboard";
        }).subscribeOn(Schedulers.boundedElastic());
    }
}