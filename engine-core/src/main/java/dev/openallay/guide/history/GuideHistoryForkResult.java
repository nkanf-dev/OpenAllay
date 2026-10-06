package dev.openallay.guide.history;

import dev.openallay.agent.context.ContextCheckpoint;
import dev.openallay.agent.context.ModelContextCodec;
import dev.openallay.model.ModelMessage;
import java.util.List;

/** Bounded display window plus the exact provider-neutral context at the fork boundary. */
public record GuideHistoryForkResult(
        GuideHistoryMetadata.Session session,
        GuideHistoryPage page,
        List<ModelMessage> messages,
        List<ContextCheckpoint> checkpoints,
        int nextMessageOrdinal) {
    public GuideHistoryForkResult {
        if (nextMessageOrdinal < 0) throw new IllegalArgumentException("message ordinal is invalid");
        java.util.Objects.requireNonNull(session, "session");
        java.util.Objects.requireNonNull(page, "page");
        if (session.requestCount() == 0 || page.requests().isEmpty()
                || page.requests().stream().anyMatch(request -> !request.terminal()
                        || !session.sessionId().equals(request.sessionId()))) {
            throw new IllegalArgumentException("fork requires a nonempty completed request page");
        }
        if (!session.sessionId().equals(page.sessionId())) {
            throw new IllegalArgumentException("fork page belongs to another session");
        }
        messages = ModelContextCodec.safe(messages);
        checkpoints = List.copyOf(checkpoints);
    }

    public GuideHistoryForkResult(GuideHistoryMetadata.Session session, GuideHistoryPage page,
            List<ModelMessage> messages, List<ContextCheckpoint> checkpoints) {
        this(session, page, messages, checkpoints, 0);
    }

    /** Only source-hash-matching complete structural units enter a new runtime session. */
    public static List<ContextCheckpoint> reusableCheckpoints(
            List<ContextCheckpoint> checkpoints, List<ModelMessage> messages) {
        List<dev.openallay.agent.context.ContextStructure.Unit> units =
                dev.openallay.agent.context.ContextStructure.units(messages);
        return checkpoints.stream().filter(checkpoint -> {
            if (checkpoint.status() != ContextCheckpoint.Status.SUCCEEDED
                    || checkpoint.sourceToIndexExclusive() > messages.size()) return false;
            try {
                dev.openallay.agent.context.ContextStructure.requireBoundary(
                        units, checkpoint.sourceFromIndex(), messages.size());
                dev.openallay.agent.context.ContextStructure.requireBoundary(
                        units, checkpoint.sourceToIndexExclusive(), messages.size());
            } catch (IllegalArgumentException split) {
                return false;
            }
            return checkpoint.sourceHash().equals(dev.openallay.agent.context.ContextSourceHash.compute(
                    dev.openallay.json.EngineJson.create(),
                    messages.subList(checkpoint.sourceFromIndex(), checkpoint.sourceToIndexExclusive())));
        }).toList();
    }
}
