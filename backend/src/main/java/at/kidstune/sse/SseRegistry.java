package at.kidstune.sse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton registry that holds one {@link Sinks.Many} per authenticated family.
 *
 * <p>SSE clients call {@link #register} to obtain a {@link Flux} that emits the
 * current pending-request count whenever the backend changes it.  Sinks are created
 * lazily and live for the application lifetime (they are lightweight when no one is
 * subscribed).
 *
 * <p>{@link #emit} is called by {@link at.kidstune.requests.ContentRequestService}
 * after every state-change that may alter the pending count.
 */
@Component
public class SseRegistry {

    private static final Logger log = LoggerFactory.getLogger(SseRegistry.class);

    private final ConcurrentHashMap<String, Sinks.Many<Long>> sinks = new ConcurrentHashMap<>();

    /**
     * Returns a {@link Flux} that emits pending counts for the given family.
     * Multiple concurrent SSE connections for the same family all receive the same events.
     */
    public Flux<Long> register(String familyId) {
        Sinks.Many<Long> sink = sinks.computeIfAbsent(familyId,
                k -> Sinks.many().multicast().directBestEffort());
        return sink.asFlux();
    }

    /**
     * Pushes a new pending count to all currently connected SSE clients for this family.
     * A no-op if no one is connected.
     */
    public void emit(String familyId, long count) {
        Sinks.Many<Long> sink = sinks.get(familyId);
        if (sink != null) {
            Sinks.EmitResult result = sink.tryEmitNext(count);
            if (result.isFailure() && result != Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER) {
                log.warn("SSE emit failed for family {} (result={})", familyId, result);
            }
        }
    }
}
