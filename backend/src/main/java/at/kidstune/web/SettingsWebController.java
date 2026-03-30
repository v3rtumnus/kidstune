package at.kidstune.web;

import at.kidstune.family.Family;
import at.kidstune.family.FamilyRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Controller
@RequestMapping("/web/settings")
public class SettingsWebController {

    private final FamilyRepository familyRepository;

    public SettingsWebController(FamilyRepository familyRepository) {
        this.familyRepository = familyRepository;
    }

    @GetMapping
    public Mono<String> settingsPage(Model model, @AuthenticationPrincipal String familyId) {
        return Mono.fromCallable(() -> {
            Family family = familyRepository.findById(familyId).orElseThrow();
            model.addAttribute("notificationEmails",
                    family.getNotificationEmails() != null ? family.getNotificationEmails() : "");
            model.addAttribute("familyId", familyId);
            model.addAttribute("saved", false);
            return "web/settings";
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping
    public Mono<String> saveSettings(
            @RequestParam("notificationEmails") String notificationEmails,
            Model model,
            @AuthenticationPrincipal String familyId) {

        return Mono.fromCallable(() -> {
            String trimmed = notificationEmails == null ? "" : notificationEmails.strip();
            Family family = familyRepository.findById(familyId).orElseThrow();
            family.setNotificationEmails(trimmed.isEmpty() ? null : trimmed);
            familyRepository.save(family);

            model.addAttribute("notificationEmails", trimmed);
            model.addAttribute("familyId", familyId);
            model.addAttribute("saved", true);
            return "web/settings";
        }).subscribeOn(Schedulers.boundedElastic());
    }
}