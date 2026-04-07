package at.kidstune.requests;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ContentRequestRepository extends JpaRepository<ContentRequest, String> {

    long countByProfileIdInAndStatus(List<String> profileIds, ContentRequestStatus status);

    List<ContentRequest> findByProfileIdInAndStatusOrderByRequestedAtDesc(
            List<String> profileIds, ContentRequestStatus status);

    List<ContentRequest> findByProfileIdInAndStatusInOrderByResolvedAtDesc(
            List<String> profileIds, List<ContentRequestStatus> statuses);

    List<ContentRequest> findByProfileIdIn(List<String> profileIds);

    long countByProfileIdAndStatus(String profileId, ContentRequestStatus status);

    Optional<ContentRequest> findByApproveToken(String approveToken);

    @Query("SELECT r FROM ContentRequest r WHERE r.status = 'PENDING' " +
           "AND r.requestedAt < :cutoff AND r.digestSentAt IS NULL")
    List<ContentRequest> findPendingOlderThanWithoutDigest(@Param("cutoff") Instant cutoff);

    @Modifying
    @Query("UPDATE ContentRequest r SET r.status = 'EXPIRED', r.resolvedAt = :now " +
           "WHERE r.status = 'PENDING' AND r.requestedAt < :cutoff")
    int expireOldRequests(@Param("cutoff") Instant cutoff, @Param("now") Instant now);

    @Query("SELECT DISTINCT r.profileId FROM ContentRequest r " +
           "WHERE r.status = 'PENDING' AND r.requestedAt < :cutoff")
    List<String> findProfileIdsWithPendingOlderThan(@Param("cutoff") Instant cutoff);
}
