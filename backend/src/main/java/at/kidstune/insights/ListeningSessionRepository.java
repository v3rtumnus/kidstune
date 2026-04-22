package at.kidstune.insights;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ListeningSessionRepository extends JpaRepository<ListeningSession, Long> {

    @Query("SELECT MAX(s.endedAt) FROM ListeningSession s WHERE s.profileId = :profileId")
    Optional<Instant> findMaxEndedAtByProfileId(@Param("profileId") String profileId);

    Optional<ListeningSession> findByProfileIdAndStartedAt(String profileId, Instant startedAt);

    List<ListeningSession> findByProfileIdAndStartedAtBetweenOrderByStartedAtAsc(
            String profileId, Instant from, Instant to);

    void deleteByProfileId(String profileId);
}
