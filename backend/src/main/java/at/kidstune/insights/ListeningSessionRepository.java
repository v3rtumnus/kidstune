package at.kidstune.insights;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ListeningSessionRepository extends JpaRepository<ListeningSession, Long> {

    @Query("SELECT MAX(s.startedAt) FROM ListeningSession s WHERE s.profileId = :profileId")
    Optional<Instant> findMaxStartedAtByProfileId(@Param("profileId") String profileId);

    Optional<ListeningSession> findByProfileIdAndStartedAt(String profileId, Instant startedAt);

    List<ListeningSession> findByProfileIdAndStartedAtBetweenOrderByStartedAtAsc(
            String profileId, Instant from, Instant to);

    @Modifying
    @Transactional
    @Query("DELETE FROM ListeningSession s WHERE s.profileId = :profileId AND s.startedAt >= :from")
    void deleteByProfileIdAndStartedAtGreaterThanEqual(@Param("profileId") String profileId,
                                                       @Param("from") Instant from);

    void deleteByProfileId(String profileId);
}
