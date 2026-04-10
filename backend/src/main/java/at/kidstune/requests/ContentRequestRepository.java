package at.kidstune.requests;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    Page<ContentRequest> findByStatus(ContentRequestStatus status, Pageable pageable);

    long countByProfileIdAndStatus(String profileId, ContentRequestStatus status);

    /**
     * Fetches all PENDING requests for a profile with a pessimistic write lock.
     * Use inside a @Transactional block to serialize concurrent request creation.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ContentRequest r WHERE r.profileId = :profileId AND r.status = 'PENDING'")
    List<ContentRequest> findPendingForUpdate(@Param("profileId") String profileId);

    Optional<ContentRequest> findByApproveToken(String approveToken);

    /**
     * Fetches a request by its approve token with a pessimistic write lock.
     * Ensures only one concurrent caller can claim and clear the token.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ContentRequest r WHERE r.approveToken = :token")
    Optional<ContentRequest> findByApproveTokenForUpdate(@Param("token") String token);

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
