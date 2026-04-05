package at.kidstune.device;

import at.kidstune.device.dto.DeviceResponse;
import at.kidstune.profile.ProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.List;

@Service
public class DeviceService {

    private final PairedDeviceRepository pairedDeviceRepository;
    private final ProfileRepository profileRepository;
    private final TransactionTemplate transactionTemplate;

    public DeviceService(PairedDeviceRepository pairedDeviceRepository,
                         ProfileRepository profileRepository,
                         TransactionTemplate transactionTemplate) {
        this.pairedDeviceRepository = pairedDeviceRepository;
        this.profileRepository = profileRepository;
        this.transactionTemplate = transactionTemplate;
    }

    public Mono<List<DeviceResponse>> listDevices(String familyId) {
        return Mono.fromCallable(() ->
                pairedDeviceRepository.findByFamilyId(familyId).stream()
                        .map(DeviceResponse::from)
                        .toList()
        ).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Void> unpairDevice(String deviceId, String familyId) {
        return Mono.fromCallable(() -> {
            PairedDevice device = pairedDeviceRepository.findByIdAndFamilyId(deviceId, familyId)
                    .orElseThrow(() -> new DeviceException(
                            "Device not found", "DEVICE_NOT_FOUND", HttpStatus.NOT_FOUND));
            pairedDeviceRepository.delete(device);
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    public Mono<DeviceResponse> reassignProfile(String deviceId, String familyId, String newProfileId) {
        return Mono.fromCallable(() ->
                transactionTemplate.execute(status -> {
                    PairedDevice device = pairedDeviceRepository.findByIdAndFamilyId(deviceId, familyId)
                            .orElseThrow(() -> new DeviceException(
                                    "Device not found", "DEVICE_NOT_FOUND", HttpStatus.NOT_FOUND));

                    profileRepository.findById(newProfileId)
                            .filter(p -> p.getFamilyId().equals(familyId))
                            .orElseThrow(() -> new DeviceException(
                                    "Profile not found", "PROFILE_NOT_FOUND", HttpStatus.NOT_FOUND));

                    device.setProfileId(newProfileId);
                    return DeviceResponse.from(pairedDeviceRepository.save(device));
                })
        ).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Void> updateLastSeen(String deviceId) {
        return Mono.fromCallable(() ->
                transactionTemplate.execute(status -> {
                    pairedDeviceRepository.updateLastSeenAt(deviceId, Instant.now());
                    return null;
                })
        ).subscribeOn(Schedulers.boundedElastic()).then();
    }
}
