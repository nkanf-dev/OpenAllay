package dev.openallay.agent;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import dev.openallay.model.CancellationSignal;
import dev.openallay.model.ModelClient;
import dev.openallay.model.ModelEvent;
import dev.openallay.model.ModelFailure;
import dev.openallay.model.ModelRequest;
import dev.openallay.model.ModelTurn;
import dev.openallay.model.ModelUsage;
import dev.openallay.model.ObservingModelClient;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

final class ModelEventAdmissionTest {
    private static final class ForeignEvent implements ModelEvent {}
    @Test void allElevenExactFinalVariantsAreAdmittedAndReasoningStaysRedacted() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        ModelUsage usage = ModelUsage.empty();
        List<ModelEvent> known = List.of(new ModelEvent.TextDelta("text"),
                new ModelEvent.ReasoningDelta("private reasoning"),
                new ModelEvent.ToolUseComplete("id", "tool", new JsonObject()),
                new ModelEvent.UsageUpdate(usage), new ModelEvent.UsageStarted(id, "model"),
                new ModelEvent.UsageObserved(id, "model", usage), new ModelEvent.AttemptStarted(1, 1L),
                new ModelEvent.ResponseStarted(), new ModelEvent.RateLimited(0, 1),
                new ModelEvent.MessageComplete("end_turn"), new ModelFailure("failure", "message", null));
        for (ModelEvent event : known) {
            assertSame(event, ModelEvent.requireKnown(event));
            assertTrue(java.lang.reflect.Modifier.isFinal(event.getClass().getModifiers()));
            AgentEvent.ModelProgress progress = new AgentEvent.ModelProgress(event);
            if (event instanceof ModelEvent.ReasoningDelta) {
                assertEquals("", ((ModelEvent.ReasoningDelta) progress.event()).text());
            } else assertSame(event, progress.event());
        }
        assertThrows(NullPointerException.class, () -> new AgentEvent.ModelProgress(null));
    }
    @Test void foreignImplementationIsRejectedBeforePublicationEvenForUsageObservingBypass() {
        List<AgentEvent> published = new ArrayList<>();
        ModelClient actual = new ModelClient() {
            @Override public boolean observesUsage() { return true; }
            @Override public CompletableFuture<ModelTurn> complete(ModelRequest request,
                    Consumer<ModelEvent> sink, CancellationSignal cancellation) {
                sink.accept(new ForeignEvent());
                return CompletableFuture.completedFuture(null);
            }
        };
        assertSame(actual, ObservingModelClient.observe(actual));
        assertThrows(IncompatibleClassChangeError.class, () ->
                ObservingModelClient.observe(actual).complete(null,
                        event -> published.add(new AgentEvent.ModelProgress(event)), new CancellationSignal()));
        assertTrue(published.isEmpty());
        assertThrows(IncompatibleClassChangeError.class, () -> ModelEvent.requireKnown(new ForeignEvent()));
    }
}
