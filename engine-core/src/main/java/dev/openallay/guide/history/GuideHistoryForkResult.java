package dev.openallay.guide.history;

import dev.openallay.agent.context.ContextCheckpoint;
import dev.openallay.agent.context.ModelContextCodec;
import dev.openallay.model.ModelMessage;
import java.util.List;

/** Bounded display window plus the exact provider-neutral context at the fork boundary. */
@dev.openallay.value.ValueType(GuideHistoryForkResult.ValueSchemaProvider.class)
public final class GuideHistoryForkResult {
    private final GuideHistoryMetadata.Session session;
    private final GuideHistoryPage page;
    private final List<ModelMessage> messages;
    private final List<ContextCheckpoint> checkpoints;
    private final int nextMessageOrdinal;
    public GuideHistoryForkResult(GuideHistoryMetadata.Session session, GuideHistoryPage page, List<ModelMessage> messages, List<ContextCheckpoint> checkpoints, int nextMessageOrdinal) {

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

        this.session = session;
        this.page = page;
        this.messages = messages;
        this.checkpoints = checkpoints;
        this.nextMessageOrdinal = nextMessageOrdinal;
    }
    public GuideHistoryMetadata.Session session() { return session; }
    public GuideHistoryPage page() { return page; }
    public List<ModelMessage> messages() { return messages; }
    public List<ContextCheckpoint> checkpoints() { return checkpoints; }
    public int nextMessageOrdinal() { return nextMessageOrdinal; }
public GuideHistoryForkResult(GuideHistoryMetadata.Session session, GuideHistoryPage page,
            List<ModelMessage> messages, List<ContextCheckpoint> checkpoints) {
        this(session, page, messages, checkpoints, 0);
    }
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
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GuideHistoryForkResult)) return false;
        GuideHistoryForkResult that = (GuideHistoryForkResult) other;
        return java.util.Objects.equals(session, that.session) && java.util.Objects.equals(page, that.page) && java.util.Objects.equals(messages, that.messages) && java.util.Objects.equals(checkpoints, that.checkpoints) && nextMessageOrdinal == that.nextMessageOrdinal;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(session);
        hash = 31 * hash + java.util.Objects.hashCode(page);
        hash = 31 * hash + java.util.Objects.hashCode(messages);
        hash = 31 * hash + java.util.Objects.hashCode(checkpoints);
        hash = 31 * hash + Integer.hashCode(nextMessageOrdinal);
        return hash;
    }
    @Override public String toString() { return "GuideHistoryForkResult[session=" + session + ", page=" + page + ", messages=" + messages + ", checkpoints=" + checkpoints + ", nextMessageOrdinal=" + nextMessageOrdinal + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<GuideHistoryForkResult> schema() {
            return new dev.openallay.value.ValueSchema<>(GuideHistoryForkResult.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<GuideHistoryForkResult>>asList(new dev.openallay.value.ValueSchema.Component<>(GuideHistoryForkResult.class, "session", GuideHistoryForkResult::session), new dev.openallay.value.ValueSchema.Component<>(GuideHistoryForkResult.class, "page", GuideHistoryForkResult::page), new dev.openallay.value.ValueSchema.Component<>(GuideHistoryForkResult.class, "messages", GuideHistoryForkResult::messages), new dev.openallay.value.ValueSchema.Component<>(GuideHistoryForkResult.class, "checkpoints", GuideHistoryForkResult::checkpoints), new dev.openallay.value.ValueSchema.Component<>(GuideHistoryForkResult.class, "nextMessageOrdinal", GuideHistoryForkResult::nextMessageOrdinal)), arguments -> new GuideHistoryForkResult((GuideHistoryMetadata.Session) arguments[0], (GuideHistoryPage) arguments[1], (List) arguments[2], (List) arguments[3], (Integer) arguments[4]));
        }
    }
}
