package at.kidstune.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface PairingCodeRepository extends JpaRepository<PairingCode, String> {

    Optional<PairingCode> findByCode(String code);

    long countByFamilyId(String familyId);

    @Modifying
    @Query("DELETE FROM PairingCode c WHERE c.familyId = :familyId AND c.expiresAt < :now")
    int deleteExpiredByFamilyId(@Param("familyId") String familyId, @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM PairingCode c WHERE c.expiresAt < :now")
    int deleteAllExpired(@Param("now") Instant now);
}
