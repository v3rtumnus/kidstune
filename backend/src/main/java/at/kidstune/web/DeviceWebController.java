package at.kidstune.web;

import at.kidstune.auth.DevicePairingService;
import at.kidstune.device.PairedDevice;
import at.kidstune.device.PairedDeviceRepository;
import at.kidstune.profile.ChildProfile;
import at.kidstune.profile.ProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/web/devices")
public class DeviceWebController {

    private static final Logger log = LoggerFactory.getLogger(DeviceWebController.class);

    private final PairedDeviceRepository deviceRepository;
    private final ProfileRepository      profileRepository;
    private final DevicePairingService   devicePairingService;

    public DeviceWebController(PairedDeviceRepository deviceRepository,
                               ProfileRepository profileRepository,
                               DevicePairingService devicePairingService) {
        this.deviceRepository    = deviceRepository;
        this.profileRepository   = profileRepository;
        this.devicePairingService = devicePairingService;
    }

    // ── GET /web/devices ──────────────────────────────────────────────────────

    @GetMapping
    public Mono<String> index(Model model, @AuthenticationPrincipal String familyId) {
        return Mono.fromCallable(() -> {
            List<PairedDevice> devices = deviceRepository.findByFamilyId(familyId);

            List<String> profileIds = devices.stream()
                    .filter(d -> d.getProfileId() != null)
                    .map(PairedDevice::getProfileId).distinct().toList();
            Map<String, String> profileNames = profileIds.isEmpty() ? Map.of() :
                    profileRepository.findAllById(profileIds).stream()
                            .collect(Collectors.toMap(ChildProfile::getId, ChildProfile::getName));

            List<DeviceRow> rows = devices.stream().map(d -> new DeviceRow(
                    d,
                    d.getProfileId() != null
                            ? profileNames.getOrDefault(d.getProfileId(), d.getProfileId())
                            : null
            )).toList();

            model.addAttribute("rows",     rows);
            model.addAttribute("familyId", familyId);
            return "web/devices/index";
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // ── POST /web/devices/{id}/delete ─────────────────────────────────────────

    @PostMapping("/{id}/delete")
    public Mono<Void> delete(@PathVariable("id") String id,
                             @AuthenticationPrincipal String familyId,
                             ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
            deviceRepository.findByIdAndFamilyId(id, familyId)
                    .ifPresentOrElse(
                            deviceRepository::delete,
                            () -> { throw new ResponseStatusException(HttpStatus.NOT_FOUND); }
                    );
            return null;
        })
        .subscribeOn(Schedulers.boundedElastic())
        .then(Mono.fromRunnable(() -> {
            exchange.getResponse().setStatusCode(HttpStatus.FOUND);
            exchange.getResponse().getHeaders().setLocation(URI.create("/web/devices"));
        }))
        .then(exchange.getResponse().setComplete());
    }

    // ── POST /web/devices/pair ────────────────────────────────────────────────

    /**
     * Generates a pairing code for the authenticated family and returns an
     * HTMX partial showing the 6-digit code with a 5-minute countdown.
     */
    @PostMapping("/pair")
    public Mono<String> generatePairingCode(Model model,
                                            @AuthenticationPrincipal String familyId) {
        return devicePairingService.generatePairingCode(familyId)
                .doOnNext(resp -> {
                    model.addAttribute("pairingCode", resp.code());
                    model.addAttribute("expiresAt",   resp.expiresAt().toEpochMilli());
                })
                .thenReturn("web/devices/pairing-code :: pairingCode");
    }

    // ── View model ────────────────────────────────────────────────────────────

    public record DeviceRow(PairedDevice device, String profileName) {}
}
