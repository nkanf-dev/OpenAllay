package dev.openallay.guide;

import dev.openallay.agent.AgentEvent;
import dev.openallay.guide.semantic.RichComponent;
import dev.openallay.guide.semantic.SemanticBlock;
import dev.openallay.guide.ui.GuideDetailCard;
import dev.openallay.guide.ui.GuideToolDetailPresenter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Receipts can only come from admitted live before/after transitions, never loaded rows. */
final class GuideLivePresentationSource {
    private final UUID actor;
    private final UUID generation = UUID.randomUUID();
    private final Map<UUID, Admission> admitted = new HashMap<>();
    private final CopyOnWriteArrayList<Consumer<GuidePresentationEvent>> listeners = new CopyOnWriteArrayList<>();
    private volatile boolean valid = true;
    private long sequence;

    GuideLivePresentationSource(UUID actor) { this.actor = actor; }
    UUID generation() { return generation; }
    GuideSubscription subscribe(Consumer<GuidePresentationEvent> listener) {
        java.util.Objects.requireNonNull(listener, "listener");
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }
    void admit(UUID owner, GuideRequestSnapshot request) {
        if (valid) admitted.put(request.requestId(), new Admission(owner, request.sessionId()));
    }
    void discardSession(UUID owner) {
        admitted.entrySet().removeIf(entry -> entry.getValue().owner.equals(owner));
    }
    void invalidate() {
        valid = false;
        admitted.clear();
        listeners.clear();
    }

    void applied(UUID owner, GuideRequestSnapshot before, GuideRequestSnapshot after, AgentEvent event) {
        Admission admission = admitted.get(before.requestId());
        if (!valid || admission == null || !admission.owner.equals(owner)
                || !admission.session.equals(after.sessionId()) || before.terminal() || before == after) return;
        // Stable semantic closure and successful native projections only. Streaming/progress never notify.
        if (event instanceof AgentEvent.ToolCompleted || event instanceof AgentEvent.ToolStarted
                || event instanceof AgentEvent.FinalText || event instanceof AgentEvent.SteerApplied) {
            List<GuidePresentationEvent.ContentRef> cards = new ArrayList<>();
            for (GuideTimelineEntry entry : after.timeline()) {
                if (entry instanceof GuideTimelineEntry.Tool tool
                        && event instanceof AgentEvent.ToolCompleted completed
                        && tool.activity().invocationId().equals(completed.invocationId())
                        && tool.activity().status() == GuideToolStatus.SUCCEEDED) {
                    List<GuideDetailCard> projected = GuideToolDetailPresenter.project(tool.activity(), false).cards();
                    for (int ordinal = 0; ordinal < projected.size(); ordinal++) {
                        if (!(projected.get(ordinal) instanceof GuideDetailCard.Error)) {
                            add(admission, cards, tool.ordinal(), "tool:" + tool.activity().invocationId() + ":card:" + ordinal);
                        }
                    }
                } else if (entry instanceof GuideTimelineEntry.Assistant assistant && !assistant.streaming()) {
                    collect(admission, cards, assistant.ordinal(), assistant.semantic().blocks());
                }
            }
            if (!cards.isEmpty()) emit(admission, after, GuidePresentationEvent.Kind.CARD_BATCH, cards, "");
        }
        if (after.status() == GuideRequestStatus.COMPLETED && after.terminal()) {
            if (event instanceof AgentEvent.FinalText) {
                for (int index = after.timeline().size() - 1; index >= 0; index--) {
                    if (after.timeline().get(index) instanceof GuideTimelineEntry.Assistant assistant) {
                        String text = assistant.semantic().fallbackText();
                        if (!text.isBlank()) emit(admission, after, GuidePresentationEvent.Kind.REPLY_FINAL,
                                List.of(new GuidePresentationEvent.ContentRef(assistant.ordinal(), "reply")), text);
                        break;
                    }
                }
            }
            List<GuidePresentationEvent.ContentRef> completedContent = after.timeline().stream()
                    .filter(GuideTimelineEntry.Assistant.class::isInstance)
                    .map(GuideTimelineEntry.Assistant.class::cast)
                    .filter(assistant -> !assistant.semantic().fallbackText().isBlank())
                    .reduce((first, second) -> second)
                    .map(assistant -> List.of(new GuidePresentationEvent.ContentRef(assistant.ordinal(), "reply")))
                    .orElse(List.of(new GuidePresentationEvent.ContentRef(-1, "task-completed")));
            emit(admission, after, GuidePresentationEvent.Kind.TASK_COMPLETED, completedContent, "");
        } else if (after.status() == GuideRequestStatus.FAILED && after.terminal()) {
            emit(admission, after, GuidePresentationEvent.Kind.TASK_FAILED,
                    List.of(new GuidePresentationEvent.ContentRef(-1, "task-failed")),
                    after.failure() == null ? "" : after.failure().message());
        }
        if (after.terminal()) admitted.remove(after.requestId());
    }

    private void collect(Admission admission, List<GuidePresentationEvent.ContentRef> cards,
                         int ordinal, List<SemanticBlock> blocks) {
        for (SemanticBlock block : blocks) {
            if (block instanceof SemanticBlock.Component component
                    && !(component.component() instanceof RichComponent.ProgressSteps)
                    && !(component.component() instanceof RichComponent.StatusBadge)) {
                add(admission, cards, ordinal, "node:" + component.nodeId());
            } else if (block instanceof SemanticBlock.Table table) {
                add(admission, cards, ordinal, "node:" + table.nodeId());
            } else if (block instanceof SemanticBlock.Quote quote) {
                collect(admission, cards, ordinal, quote.content());
            } else if (block instanceof SemanticBlock.ListBlock list) {
                list.items().forEach(items -> collect(admission, cards, ordinal, items));
            }
        }
    }
    private void add(Admission admission, List<GuidePresentationEvent.ContentRef> cards, int ordinal, String id) {
        GuidePresentationEvent.ContentRef ref = new GuidePresentationEvent.ContentRef(ordinal, id);
        if (admission.content.add(ref)) cards.add(ref);
    }
    private void emit(Admission admission, GuideRequestSnapshot request, GuidePresentationEvent.Kind kind,
                      List<GuidePresentationEvent.ContentRef> content, String preview) {
        GuidePresentationEvent event = new GuidePresentationEvent(new GuidePresentationEvent.Key(
                generation, actor, admission.owner, admission.session, request.requestId(), ++sequence),
                kind, content, preview, request.updatedAt());
        for (Consumer<GuidePresentationEvent> listener : listeners) {
            try { listener.accept(event); }
            catch (RuntimeException ignored) { /* A presentation observer cannot fail the task. */ }
        }
    }
    private static final class Admission {
        private final UUID owner;
        private final String session;
        private final Set<GuidePresentationEvent.ContentRef> content = new HashSet<>();
        private Admission(UUID owner, String session) { this.owner = owner; this.session = session; }
    }
}
