package dev.openallay.guide;

import dev.openallay.model.ModelMessage;
import java.util.List;

/** Exclusive prepared local control. It owns no Agent request or player-facing answer. */
public interface GuidePreparedCompaction extends AutoCloseable {
    GuideCompactResult outcome();

    /** Exact safe actual history captured by the control lease, before preparation. */
    List<ModelMessage> source();

    List<ModelMessage> projection();

    /** Includes session ownership, cancellation and the exact captured endpoint state. */
    boolean current();

    /** Call only after the durable projection has been saved under the service generation fence. */
    boolean publish();

    /** Releases an unpublished reservation. It never changes the actual model context. */
    @Override
    void close();
}
