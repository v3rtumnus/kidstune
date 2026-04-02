package at.kidstune.requests;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ContentRequestRepository extends JpaRepository<ContentRequest, String> {

    long countByProfileIdInAndStatus(List<String> profileIds, ContentRequestStatus status);

    List<ContentRequest> findByProfileIdInAndStatusOrderByRequestedAtDesc(
            List<String> profileIds, ContentRequestStatus status);

    List<ContentRequest> findByProfileIdInAndStatusInOrderByResolvedAtDesc(
            List<String> profileIds, List<ContentRequestStatus> statuses);

    List<ContentRequest> findByProfileIdIn(List<String> profileIds);

    long countByProfileIdAndStatus(String profileId, ContentRequestStatus status);

    @Modifying
    @Query("UPDATE ContentRequest r SET r.status = 'EXPIRED', r.resolvedAt = :now " +
           "WHERE r.status = 'PENDING' AND r.requestedAt < :cutoff")
    int expireOldRequests(@Param("cutoff") Instant cutoff, @Param("now") Instant now);
}
