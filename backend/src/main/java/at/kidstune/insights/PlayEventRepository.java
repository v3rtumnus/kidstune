package at.kidstune.insights;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PlayEventRepository extends JpaRepository<PlayEvent, Long> {

    boolean existsByProfileIdAndPlayedAtAndTrackId(String profileId, Instant playedAt, String trackId);

    @Query("SELECT MAX(e.playedAt) FROM PlayEvent e WHERE e.profileId = :profileId")
    Optional<Instant> findMaxPlayedAtByProfileId(@Param("profileId") String profileId);

    List<PlayEvent> findByProfileIdAndPlayedAtBetweenOrderByPlayedAtAsc(
            String profileId, Instant from, Instant to);

    List<PlayEvent> findByProfileIdAndPlayedAtGreaterThanEqualOrderByPlayedAtAsc(
            String profileId, Instant from);

    @Query("SELECT e FROM PlayEvent e WHERE e.profileId = :profileId " +
           "AND e.playedAt >= :from AND e.playedAt < :to " +
           "ORDER BY e.playedAt ASC")
    List<PlayEvent> findForRange(
            @Param("profileId") String profileId,
            @Param("from") Instant from,
            @Param("to") Instant to);

    void deleteByProfileId(String profileId);
}
