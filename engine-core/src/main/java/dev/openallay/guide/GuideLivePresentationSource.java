package dev.openallay.guide;

import dev.openallay.agent.AgentEvent;
import dev.openallay.guide.semantic.RichComponent;
import dev.openallay.guide.semantic.SemanticBlock;
import dev.openallay.guide.ui.GuideDetailCard;
import dev.openallay.guide.ui.GuideToolDetailPresenter;
import dev.openallay.guide.ui.GuideToolDetailView;
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
                || !before.requestId().equals(after.requestId()) || !admission.session.equals(before.sessionId())
                || !admission.session.equals(after.sessionId()) || before.terminal() || before == after) return;
        // Stable semantic closure and successful native projections only. Streaming/progress never notify.
        if (event instanceof AgentEvent.ToolCompleted || event instanceof AgentEvent.ToolStarted
                || event instanceof AgentEvent.FinalText || event instanceof AgentEvent.SteerApplied) {
            List<GuidePresentationEvent.ContentRef> cards = new ArrayList<>();
            List<GuidePresentationEvent.CardPreview> previews = new ArrayList<>();
            for (GuideTimelineEntry entry : after.timeline()) {
                if (entry instanceof GuideTimelineEntry.Tool tool
                        && event instanceof AgentEvent.ToolCompleted completed
                        && tool.activity().invocationId().equals(completed.invocationId())
                        && tool.activity().status() == GuideToolStatus.SUCCEEDED) {
                    GuideToolDetailView detail = GuideToolDetailPresenter.project(tool.activity(), false);
                    for (int ordinal = 0; ordinal < detail.cards().size(); ordinal++) {
                        if (!(detail.cards().get(ordinal) instanceof GuideDetailCard.Error)) {
                            GuideToolIntent summary = cardSummary(detail.cards().get(ordinal));
                            add(admission, cards, previews, tool.ordinal(),
                                    "tool:" + tool.activity().invocationId() + ":card:" + ordinal,
                                    detail.intent().title().isBlank() ? summary.title() : detail.intent().title(),
                                    detail.intent().description().isBlank() ? summary.description() : detail.intent().description());
                        }
                    }
                } else if (entry instanceof GuideTimelineEntry.Assistant assistant && !assistant.streaming()) {
                    collect(admission, cards, previews, assistant.ordinal(), assistant.semantic().blocks());
                }
            }
            if (!cards.isEmpty()) emit(admission, after, GuidePresentationEvent.Kind.CARD_BATCH, cards, "", previews);
        }
        if (after.status() == GuideRequestStatus.COMPLETED && after.terminal()) {
            if (event instanceof AgentEvent.FinalText) {
                for (int index = after.timeline().size() - 1; index >= 0; index--) {
                    if (after.timeline().get(index) instanceof GuideTimelineEntry.Assistant assistant) {
                        String text = assistant.semantic().fallbackText();
                        if (!text.isBlank()) emit(admission, after, GuidePresentationEvent.Kind.REPLY_FINAL,
                                List.of(new GuidePresentationEvent.ContentRef(assistant.ordinal(), "reply")), text, List.of());
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
            emit(admission, after, GuidePresentationEvent.Kind.TASK_COMPLETED, completedContent, "", List.of());
        } else if (after.status() == GuideRequestStatus.FAILED && after.terminal()) {
            emit(admission, after, GuidePresentationEvent.Kind.TASK_FAILED,
                    List.of(new GuidePresentationEvent.ContentRef(-1, "task-failed")),
                    after.failure() == null ? "" : after.failure().message(), List.of());
        }
        if (after.terminal()) admitted.remove(after.requestId());
    }

    private void collect(Admission admission, List<GuidePresentationEvent.ContentRef> cards,
                         List<GuidePresentationEvent.CardPreview> previews, int ordinal, List<SemanticBlock> blocks) {
        for (SemanticBlock block : blocks) {
            if (block instanceof SemanticBlock.Component component
                    && !(component.component() instanceof RichComponent.ProgressSteps)
                    && !(component.component() instanceof RichComponent.StatusBadge)) {
                RichComponent value = component.component();
                String title = componentTitle(value);
                add(admission, cards, previews, ordinal, "node:" + component.nodeId(),
                        title.isBlank() ? value.fallbackText() : title, value.fallbackText());
            } else if (block instanceof SemanticBlock.Table table) {
                // Tables have no title/description fields. Never substitute raw cells or an unrelated reply.
                add(admission, cards, previews, ordinal, "node:" + table.nodeId(), "", "");
            } else if (block instanceof SemanticBlock.Quote quote) {
                collect(admission, cards, previews, ordinal, quote.content());
            } else if (block instanceof SemanticBlock.ListBlock list) {
                list.items().forEach(items -> collect(admission, cards, previews, ordinal, items));
            }
        }
    }
    private static String componentTitle(RichComponent value) {
        java.util.Objects.requireNonNull(value);
        if (value instanceof RichComponent.RecipeGrid recipe) {
            return recipe.label();
        } else if (value instanceof RichComponent.ChoiceGroup choices) {
            return choices.prompt();
        } else if (value instanceof RichComponent.ItemRow items) {
            return labels(items.items().stream().map(RichComponent.Item::label).toList());
        } else if (value instanceof RichComponent.IngredientCheck ingredients) {
            return labels(ingredients.ingredients().stream().map(RichComponent.Ingredient::label).toList());
        } else if (value instanceof RichComponent.SourceSummary sources) {
            return labels(sources.sources().stream().map(RichComponent.Source::label).toList());
        }
        return "";
    }
    private static String labels(List<String> labels) {
        return labels.stream().filter(label -> !label.isBlank()).distinct()
                .reduce((first, next) -> first + ", " + next).orElse("");
    }
    private static GuideToolIntent cardSummary(GuideDetailCard card) {
        // Use only canonical typed display fields. Never read execution arguments or normalized raw values here.
        java.util.Objects.requireNonNull(card);
        if (card instanceof GuideDetailCard.ItemGrid items) {
            return new GuideToolIntent(
                    labels(items.items().stream().map(dev.openallay.guide.ui.GuideItemView::displayName).toList()),
                    items.items().stream().map(item -> item.displayName() + " × " + item.count())
                            .reduce((first, next) -> first + ", " + next).orElse(""));
        } else if (card instanceof GuideDetailCard.Recipe recipe) {
            return new GuideToolIntent(
                    labels(recipe.recipe().outputs().stream().map(dev.openallay.guide.ui.GuideRecipeCard.Output::displayName).toList()),
                    recipe.recipe().outputs().stream().map(output -> output.displayName() + " × " + output.count())
                            .reduce((first, next) -> first + ", " + next).orElse(""));
        }
        return GuideToolIntent.none();
    }
    private void add(Admission admission, List<GuidePresentationEvent.ContentRef> cards,
                     List<GuidePresentationEvent.CardPreview> previews, int ordinal,
                     String id, String title, String description) {
        GuidePresentationEvent.ContentRef ref = new GuidePresentationEvent.ContentRef(ordinal, id);
        if (!admission.content.add(ref)) return;
        cards.add(ref);
        if (!title.isBlank() && !description.isBlank()) {
            previews.add(new GuidePresentationEvent.CardPreview(ref, title, description));
        }
    }
    private void emit(Admission admission, GuideRequestSnapshot request, GuidePresentationEvent.Kind kind,
                      List<GuidePresentationEvent.ContentRef> content, String preview,
                      List<GuidePresentationEvent.CardPreview> cards) {
        GuidePresentationEvent event = new GuidePresentationEvent(new GuidePresentationEvent.Key(
                generation, actor, admission.owner, admission.session, request.requestId(), ++sequence),
                kind, content, preview, cards, request.updatedAt());
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
