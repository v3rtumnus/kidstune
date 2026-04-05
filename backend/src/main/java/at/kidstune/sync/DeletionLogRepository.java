package at.kidstune.sync;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

public interface DeletionLogRepository extends JpaRepository<DeletionLog, String> {

    List<DeletionLog> findByProfileIdAndDeletedAtAfterAndType(
            String profileId, Instant since, DeletionLog.DeletionType type);

    @Modifying
    @Transactional
    void deleteByDeletedAtBefore(Instant cutoff);
}