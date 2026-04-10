package at.kidstune.device;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PairedDeviceRepository extends JpaRepository<PairedDevice, String> {

    long countByFamilyId(String familyId);

    List<PairedDevice> findByFamilyId(String familyId);

    Optional<PairedDevice> findByIdAndFamilyId(String id, String familyId);

    Optional<PairedDevice> findByIdAndProfileId(String id, String profileId);

    @Modifying
    @Query("UPDATE PairedDevice d SET d.lastSeenAt = :now WHERE d.id = :id")
    int updateLastSeenAt(@Param("id") String id, @Param("now") Instant now);
}
