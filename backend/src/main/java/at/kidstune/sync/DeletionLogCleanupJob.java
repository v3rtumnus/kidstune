package at.kidstune.sync;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class DeletionLogCleanupJob {

    private final DeletionLogRepository deletionLogRepo;

    public DeletionLogCleanupJob(DeletionLogRepository deletionLogRepo) {
        this.deletionLogRepo = deletionLogRepo;
    }

    /** Runs daily at 03:00 UTC and removes deletion log entries older than 30 days. */
    @Scheduled(cron = "0 0 3 * * *")
    public void purgeOldEntries() {
        Instant cutoff = Instant.now().minus(30, ChronoUnit.DAYS);
        deletionLogRepo.deleteByDeletedAtBefore(cutoff);
    }
}
