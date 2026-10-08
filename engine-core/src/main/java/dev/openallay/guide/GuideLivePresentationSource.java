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
                final class $oaPattern0_Holder { dev.openallay.guide.GuideTimelineEntry value; GuideTimelineEntry.Tool bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
final class $oaPattern1_Holder { dev.openallay.agent.AgentEvent value; AgentEvent.ToolCompleted bound; }
final $oaPattern1_Holder $oaPattern1_holder = new $oaPattern1_Holder();
if ((($oaPattern0_holder.value = entry) instanceof dev.openallay.guide.GuideTimelineEntry.Tool && (($oaPattern0_holder.bound = (GuideTimelineEntry.Tool) $oaPattern0_holder.value) != null))
                        && (($oaPattern1_holder.value = event) instanceof dev.openallay.agent.AgentEvent.ToolCompleted && (($oaPattern1_holder.bound = (AgentEvent.ToolCompleted) $oaPattern1_holder.value) != null))
                        && $oaPattern0_holder.bound.activity().invocationId().equals($oaPattern1_holder.bound.invocationId())
                        && $oaPattern0_holder.bound.activity().status() == GuideToolStatus.SUCCEEDED) {
                    GuideToolDetailView detail = GuideToolDetailPresenter.project($oaPattern0_holder.bound.activity(), false);
                    for (int ordinal = 0; ordinal < detail.cards().size(); ordinal++) {
                        if (!(detail.cards().get(ordinal) instanceof GuideDetailCard.Error)) {
                            GuideToolIntent summary = cardSummary(detail.cards().get(ordinal));
                            add(admission, cards, previews, $oaPattern0_holder.bound.ordinal(),
                                    "tool:" + $oaPattern0_holder.bound.activity().invocationId() + ":card:" + ordinal,
                                    dev.openallay.util.Java8Strings.isBlank(detail.intent().title()) ? summary.title() : detail.intent().title(),
                                    dev.openallay.util.Java8Strings.isBlank(detail.intent().description()) ? summary.description() : detail.intent().description());
                        }
                    }
                } else {
final class $oaPattern2_Holder { dev.openallay.guide.GuideTimelineEntry value; GuideTimelineEntry.Assistant bound; }
final $oaPattern2_Holder $oaPattern2_holder = new $oaPattern2_Holder();
if ((($oaPattern2_holder.value = entry) instanceof dev.openallay.guide.GuideTimelineEntry.Assistant && (($oaPattern2_holder.bound = (GuideTimelineEntry.Assistant) $oaPattern2_holder.value) != null)) && !$oaPattern2_holder.bound.streaming()) {
                    collect(admission, cards, previews, $oaPattern2_holder.bound.ordinal(), $oaPattern2_holder.bound.semantic().blocks());
                }
}
            }
            if (!cards.isEmpty()) emit(admission, after, GuidePresentationEvent.Kind.CARD_BATCH, cards, "", previews);
        }
        if (after.status() == GuideRequestStatus.COMPLETED && after.terminal()) {
            if (event instanceof AgentEvent.FinalText) {
                for (int index = after.timeline().size() - 1; index >= 0; index--) {
                    final class $oaPattern3_Holder { dev.openallay.guide.GuideTimelineEntry value; GuideTimelineEntry.Assistant bound; }
final $oaPattern3_Holder $oaPattern3_holder = new $oaPattern3_Holder();
if ((($oaPattern3_holder.value = after.timeline().get(index)) instanceof dev.openallay.guide.GuideTimelineEntry.Assistant && (($oaPattern3_holder.bound = (GuideTimelineEntry.Assistant) $oaPattern3_holder.value) != null))) {
                        String text = $oaPattern3_holder.bound.semantic().fallbackText();
                        if (!dev.openallay.util.Java8Strings.isBlank(text)) emit(admission, after, GuidePresentationEvent.Kind.REPLY_FINAL,
                                dev.openallay.util.Java8Collections.listOf(new GuidePresentationEvent.ContentRef($oaPattern3_holder.bound.ordinal(), "reply")), text, dev.openallay.util.Java8Collections.listOf());
                        break;
                    }
                }
            }
            List<GuidePresentationEvent.ContentRef> completedContent = after.timeline().stream()
                    .filter(GuideTimelineEntry.Assistant.class::isInstance)
                    .map(GuideTimelineEntry.Assistant.class::cast)
                    .filter(assistant -> !dev.openallay.util.Java8Strings.isBlank(assistant.semantic().fallbackText()))
                    .reduce((first, second) -> second)
                    .map(assistant -> dev.openallay.util.Java8Collections.listOf(new GuidePresentationEvent.ContentRef(assistant.ordinal(), "reply")))
                    .orElse(dev.openallay.util.Java8Collections.listOf(new GuidePresentationEvent.ContentRef(-1, "task-completed")));
            emit(admission, after, GuidePresentationEvent.Kind.TASK_COMPLETED, completedContent, "", dev.openallay.util.Java8Collections.listOf());
        } else if (after.status() == GuideRequestStatus.FAILED && after.terminal()) {
            emit(admission, after, GuidePresentationEvent.Kind.TASK_FAILED,
                    dev.openallay.util.Java8Collections.listOf(new GuidePresentationEvent.ContentRef(-1, "task-failed")),
                    after.failure() == null ? "" : after.failure().message(), dev.openallay.util.Java8Collections.listOf());
        }
        if (after.terminal()) admitted.remove(after.requestId());
    }

    private void collect(Admission admission, List<GuidePresentationEvent.ContentRef> cards,
                         List<GuidePresentationEvent.CardPreview> previews, int ordinal, List<SemanticBlock> blocks) {
        for (SemanticBlock block : blocks) {
            final class $oaPattern4_Holder { dev.openallay.guide.semantic.SemanticBlock value; SemanticBlock.Component bound; }
final $oaPattern4_Holder $oaPattern4_holder = new $oaPattern4_Holder();
if ((($oaPattern4_holder.value = block) instanceof dev.openallay.guide.semantic.SemanticBlock.Component && (($oaPattern4_holder.bound = (SemanticBlock.Component) $oaPattern4_holder.value) != null))
                    && !($oaPattern4_holder.bound.component() instanceof RichComponent.ProgressSteps)
                    && !($oaPattern4_holder.bound.component() instanceof RichComponent.StatusBadge)) {
                RichComponent value = $oaPattern4_holder.bound.component();
                String title = componentTitle(value);
                add(admission, cards, previews, ordinal, "node:" + $oaPattern4_holder.bound.nodeId(),
                        dev.openallay.util.Java8Strings.isBlank(title) ? value.fallbackText() : title, value.fallbackText());
            } else {
final class $oaPattern5_Holder { dev.openallay.guide.semantic.SemanticBlock value; SemanticBlock.Table bound; }
final $oaPattern5_Holder $oaPattern5_holder = new $oaPattern5_Holder();
if ((($oaPattern5_holder.value = block) instanceof dev.openallay.guide.semantic.SemanticBlock.Table && (($oaPattern5_holder.bound = (SemanticBlock.Table) $oaPattern5_holder.value) != null))) {
                // Tables have no title/description fields. Never substitute raw cells or an unrelated reply.
                add(admission, cards, previews, ordinal, "node:" + $oaPattern5_holder.bound.nodeId(), "", "");
            } else {
final class $oaPattern6_Holder { dev.openallay.guide.semantic.SemanticBlock value; SemanticBlock.Quote bound; }
final $oaPattern6_Holder $oaPattern6_holder = new $oaPattern6_Holder();
if ((($oaPattern6_holder.value = block) instanceof dev.openallay.guide.semantic.SemanticBlock.Quote && (($oaPattern6_holder.bound = (SemanticBlock.Quote) $oaPattern6_holder.value) != null))) {
                collect(admission, cards, previews, ordinal, $oaPattern6_holder.bound.content());
            } else {
final class $oaPattern7_Holder { dev.openallay.guide.semantic.SemanticBlock value; SemanticBlock.ListBlock bound; }
final $oaPattern7_Holder $oaPattern7_holder = new $oaPattern7_Holder();
if ((($oaPattern7_holder.value = block) instanceof dev.openallay.guide.semantic.SemanticBlock.ListBlock && (($oaPattern7_holder.bound = (SemanticBlock.ListBlock) $oaPattern7_holder.value) != null))) {
                $oaPattern7_holder.bound.items().forEach(items -> collect(admission, cards, previews, ordinal, items));
            }
}
}
}
        }
    }
    private static String componentTitle(RichComponent value) {
        java.util.Objects.requireNonNull(value);
        final class $oaPattern8_Holder { dev.openallay.guide.semantic.RichComponent value; RichComponent.RecipeGrid bound; }
final $oaPattern8_Holder $oaPattern8_holder = new $oaPattern8_Holder();
if ((($oaPattern8_holder.value = value) instanceof dev.openallay.guide.semantic.RichComponent.RecipeGrid && (($oaPattern8_holder.bound = (RichComponent.RecipeGrid) $oaPattern8_holder.value) != null))) {
            return $oaPattern8_holder.bound.label();
        } else {
final class $oaPattern9_Holder { dev.openallay.guide.semantic.RichComponent value; RichComponent.ChoiceGroup bound; }
final $oaPattern9_Holder $oaPattern9_holder = new $oaPattern9_Holder();
if ((($oaPattern9_holder.value = value) instanceof dev.openallay.guide.semantic.RichComponent.ChoiceGroup && (($oaPattern9_holder.bound = (RichComponent.ChoiceGroup) $oaPattern9_holder.value) != null))) {
            return $oaPattern9_holder.bound.prompt();
        } else {
final class $oaPattern10_Holder { dev.openallay.guide.semantic.RichComponent value; RichComponent.ItemRow bound; }
final $oaPattern10_Holder $oaPattern10_holder = new $oaPattern10_Holder();
if ((($oaPattern10_holder.value = value) instanceof dev.openallay.guide.semantic.RichComponent.ItemRow && (($oaPattern10_holder.bound = (RichComponent.ItemRow) $oaPattern10_holder.value) != null))) {
            return labels(dev.openallay.util.Java8Collections.toList($oaPattern10_holder.bound.items().stream().map(RichComponent.Item::label)));
        } else {
final class $oaPattern11_Holder { dev.openallay.guide.semantic.RichComponent value; RichComponent.IngredientCheck bound; }
final $oaPattern11_Holder $oaPattern11_holder = new $oaPattern11_Holder();
if ((($oaPattern11_holder.value = value) instanceof dev.openallay.guide.semantic.RichComponent.IngredientCheck && (($oaPattern11_holder.bound = (RichComponent.IngredientCheck) $oaPattern11_holder.value) != null))) {
            return labels(dev.openallay.util.Java8Collections.toList($oaPattern11_holder.bound.ingredients().stream().map(RichComponent.Ingredient::label)));
        } else {
final class $oaPattern12_Holder { dev.openallay.guide.semantic.RichComponent value; RichComponent.SourceSummary bound; }
final $oaPattern12_Holder $oaPattern12_holder = new $oaPattern12_Holder();
if ((($oaPattern12_holder.value = value) instanceof dev.openallay.guide.semantic.RichComponent.SourceSummary && (($oaPattern12_holder.bound = (RichComponent.SourceSummary) $oaPattern12_holder.value) != null))) {
            return labels(dev.openallay.util.Java8Collections.toList($oaPattern12_holder.bound.sources().stream().map(RichComponent.Source::label)));
        }
}
}
}
}
        return "";
    }
    private static String labels(List<String> labels) {
        return labels.stream().filter(label -> !dev.openallay.util.Java8Strings.isBlank(label)).distinct()
                .reduce((first, next) -> first + ", " + next).orElse("");
    }
    private static GuideToolIntent cardSummary(GuideDetailCard card) {
        // Use only canonical typed display fields. Never read execution arguments or normalized raw values here.
        java.util.Objects.requireNonNull(card);
        final class $oaPattern13_Holder { dev.openallay.guide.ui.GuideDetailCard value; GuideDetailCard.ItemGrid bound; }
final $oaPattern13_Holder $oaPattern13_holder = new $oaPattern13_Holder();
if ((($oaPattern13_holder.value = card) instanceof dev.openallay.guide.ui.GuideDetailCard.ItemGrid && (($oaPattern13_holder.bound = (GuideDetailCard.ItemGrid) $oaPattern13_holder.value) != null))) {
            return new GuideToolIntent(
                    labels(dev.openallay.util.Java8Collections.toList($oaPattern13_holder.bound.items().stream().map(dev.openallay.guide.ui.GuideItemView::displayName))),
                    $oaPattern13_holder.bound.items().stream().map(item -> item.displayName() + " × " + item.count())
                            .reduce((first, next) -> first + ", " + next).orElse(""));
        } else {
final class $oaPattern14_Holder { dev.openallay.guide.ui.GuideDetailCard value; GuideDetailCard.Recipe bound; }
final $oaPattern14_Holder $oaPattern14_holder = new $oaPattern14_Holder();
if ((($oaPattern14_holder.value = card) instanceof dev.openallay.guide.ui.GuideDetailCard.Recipe && (($oaPattern14_holder.bound = (GuideDetailCard.Recipe) $oaPattern14_holder.value) != null))) {
            return new GuideToolIntent(
                    labels(dev.openallay.util.Java8Collections.toList($oaPattern14_holder.bound.recipe().outputs().stream().map(dev.openallay.guide.ui.GuideRecipeCard.Output::displayName))),
                    $oaPattern14_holder.bound.recipe().outputs().stream().map(output -> output.displayName() + " × " + output.count())
                            .reduce((first, next) -> first + ", " + next).orElse(""));
        }
}
        return GuideToolIntent.none();
    }
    private void add(Admission admission, List<GuidePresentationEvent.ContentRef> cards,
                     List<GuidePresentationEvent.CardPreview> previews, int ordinal,
                     String id, String title, String description) {
        GuidePresentationEvent.ContentRef ref = new GuidePresentationEvent.ContentRef(ordinal, id);
        if (!admission.content.add(ref)) return;
        cards.add(ref);
        if (!dev.openallay.util.Java8Strings.isBlank(title) && !dev.openallay.util.Java8Strings.isBlank(description)) {
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
