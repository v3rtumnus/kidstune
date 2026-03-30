package at.kidstune.device;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PairedDeviceRepository extends JpaRepository<PairedDevice, String> {

    long countByFamilyId(String familyId);
}