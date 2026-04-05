package at.kidstune.sync;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DeletionLogCleanupJobTest {

    @Mock
    DeletionLogRepository deletionLogRepo;

    @InjectMocks
    DeletionLogCleanupJob job;

    @Test
    void purgeOldEntries_deletes_entries_older_than_30_days() {
        Instant before = Instant.now();

        job.purgeOldEntries();

        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(deletionLogRepo).deleteByDeletedAtBefore(cutoffCaptor.capture());

        Instant cutoff = cutoffCaptor.getValue();
        Instant expectedLowerBound = before.minus(30, ChronoUnit.DAYS);
        Instant expectedUpperBound = Instant.now().minus(30, ChronoUnit.DAYS);

        assertThat(cutoff).isAfterOrEqualTo(expectedLowerBound)
                .isBeforeOrEqualTo(expectedUpperBound);
    }
}
