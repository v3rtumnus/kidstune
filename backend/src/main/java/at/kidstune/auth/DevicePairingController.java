package at.kidstune.auth;

import at.kidstune.auth.dto.ConfirmPairingRequest;
import at.kidstune.auth.dto.ConfirmPairingResponse;
import at.kidstune.auth.dto.PairingCodeResponse;
import at.kidstune.common.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/auth/pair")
public class DevicePairingController {

    private final DevicePairingService devicePairingService;

    public DevicePairingController(DevicePairingService devicePairingService) {
        this.devicePairingService = devicePairingService;
    }

    /** POST /api/v1/auth/pair – requires PARENT auth; returns a 6-digit code. */
    @PostMapping
    public Mono<ResponseEntity<PairingCodeResponse>> generateCode() {
        return SecurityUtils.getFamilyId()
                .flatMap(devicePairingService::generatePairingCode)
                .map(code -> ResponseEntity.status(HttpStatus.CREATED).body(code));
    }

    /** POST /api/v1/auth/pair/confirm – public; exchanges code for a device token. */
    @PostMapping("/confirm")
    public Mono<ResponseEntity<ConfirmPairingResponse>> confirmPairing(
            @RequestBody @Valid ConfirmPairingRequest request) {
        return devicePairingService.confirmPairing(request.code(), request.deviceName())
                .map(ResponseEntity::ok);
    }
}
