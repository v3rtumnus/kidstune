package at.kidstune.device;

import at.kidstune.common.SecurityUtils;
import at.kidstune.device.dto.DeviceResponse;
import at.kidstune.device.dto.ReassignProfileRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/v1/devices")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @GetMapping
    public Mono<ResponseEntity<List<DeviceResponse>>> listDevices() {
        return SecurityUtils.getFamilyId()
                .flatMap(deviceService::listDevices)
                .map(ResponseEntity::ok);
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> unpairDevice(@PathVariable String id) {
        return SecurityUtils.getFamilyId()
                .flatMap(familyId -> deviceService.unpairDevice(id, familyId))
                .thenReturn(ResponseEntity.<Void>noContent().build());
    }

    @PutMapping("/{id}/profile")
    public Mono<ResponseEntity<DeviceResponse>> reassignProfile(
            @PathVariable String id,
            @RequestBody @Valid ReassignProfileRequest request) {
        return SecurityUtils.getFamilyId()
                .flatMap(familyId -> deviceService.reassignProfile(id, familyId, request.profileId()))
                .map(ResponseEntity::ok);
    }
}
