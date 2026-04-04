package at.kidstune.sync;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface DeletionLogRepository extends JpaRepository<DeletionLog, String> {

    List<DeletionLog> findByProfileIdAndDeletedAtAfterAndType(
            String profileId, Instant since, DeletionLog.DeletionType type);
}