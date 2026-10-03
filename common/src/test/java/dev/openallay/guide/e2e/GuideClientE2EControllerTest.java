package dev.openallay.guide.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import dev.openallay.agent.AgentEvent;
import dev.openallay.agent.AgentResult;
import dev.openallay.agent.AgentState;
import dev.openallay.context.ContextCapability;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.guide.GuideLocalEndpoint;
import dev.openallay.guide.GuideModelMode;
import dev.openallay.guide.GuideRemoteEndpoint;
import dev.openallay.guide.GuideServiceManager;
import dev.openallay.guide.history.GuideHistoryAccess;
import dev.openallay.guide.history.GuideHistoryCommit;
import dev.openallay.guide.history.GuideHistoryContextRequest;
import dev.openallay.guide.history.GuideHistoryContextSeed;
import dev.openallay.guide.history.GuideHistoryMetadata;
import dev.openallay.guide.history.GuideHistoryScope;
import dev.openallay.model.ModelUsage;
import dev.openallay.model.ModelEvent;
import dev.openallay.tool.ToolResult;
import dev.openallay.recipe.RecipeProviderReadiness;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class GuideClientE2EControllerTest {
    @TempDir Path temporary;

    @Test
    void professionalScreenshotMatrixRequiresBothExplicitOptInProperties() {
        String enabled = GuideClientE2EConfig.ENABLED;
        String matrix = "openallay.e2e.screenshotMatrix";
        String previousEnabled = System.getProperty(enabled);
        String previousMatrix = System.getProperty(matrix);
        try {
            System.clearProperty(enabled);
            System.setProperty(matrix, "professional");
            assertFalse(GuideClientE2EController.professionalScreenshots());
            System.setProperty(enabled, "true");
            assertTrue(GuideClientE2EController.professionalScreenshots());
            System.setProperty(matrix, "other");
            assertFalse(GuideClientE2EController.professionalScreenshots());
        } finally {
            if (previousEnabled == null) System.clearProperty(enabled); else System.setProperty(enabled, previousEnabled);
            if (previousMatrix == null) System.clearProperty(matrix); else System.setProperty(matrix, previousMatrix);
        }
    }

    @Test
    void distinctLiveNativeScenarioReusesGuardedGraphicalLifecycleOnly() {
        assertTrue(GuideClientE2EController.graphicalScenario("ui-manual-regressions"));
        assertTrue(GuideClientE2EController.graphicalScenario("ui-live-ux-regressions"));
        assertFalse(GuideClientE2EController.graphicalScenario("phase-4-semantic-history"));
        assertFalse(GuideClientE2EController.graphicalScenario("ui-stop"));
    }

    @Test
    void writesCanonicalReportAndRequestsCleanShutdown() throws Exception {
        Path report = temporary.resolve("nested/report.json");
        ArrayDeque<Runnable> clientTasks = new ArrayDeque<>();
        String playerValue = "player-plain-value-123456";
        GuideClientE2EConfig config = new GuideClientE2EConfig(
                playerValue, "e2e", "question", GuideModelMode.CLIENT, report, true);
        GuideServiceManager services = new GuideServiceManager(
                new CompletingLocal(),
                new NoRemote(),
                (capabilities, correlation) -> new ToolResult.Success<>(
                        ToolInvocationContext.developmentConsole(correlation)),
                clientTasks::addLast,
                Clock.systemUTC(),
                new Gson());
        AtomicBoolean shutdown = new AtomicBoolean();
        GuideClientE2EController controller = new GuideClientE2EController(
                config, "fabric", "26.2", "test", services, new Gson(),
                () -> shutdown.set(true));

        assertFalse(controller.finished());
        controller.tick(UUID.fromString("30ab22ed-23fb-46f2-82ca-d4a656698eec"));
        assertFalse(controller.finished());
        while (!clientTasks.isEmpty()) clientTasks.removeFirst().run();

        assertTrue(controller.finished());
        assertTrue(shutdown.get());
        String encoded = Files.readString(report);
        var json = JsonParser.parseString(encoded).getAsJsonObject();
        assertEquals(playerValue, json.get("scenario").getAsString());
        assertEquals("fabric", json.get("loader").getAsString());
        assertEquals("COMPLETED", json.get("outcome").getAsString());
        assertEquals("e2e", json.get("sessionId").getAsString());
    }

    @Test
    void waitsForDurableHistoryHydrationBeforeStarting() throws Exception {
        Path report = temporary.resolve("delayed/report.json");
        ArrayDeque<Runnable> clientTasks = new ArrayDeque<>();
        DelayedHistory history = new DelayedHistory();
        GuideClientE2EConfig config = new GuideClientE2EConfig(
                "fixture", "e2e", "question", GuideModelMode.CLIENT, report, true);
        GuideServiceManager services = new GuideServiceManager(
                new CompletingLocal(),
                new NoRemote(),
                (capabilities, correlation) -> new ToolResult.Success<>(
                        ToolInvocationContext.developmentConsole(correlation)),
                clientTasks::addLast,
                Clock.systemUTC(),
                new Gson(),
                history,
                actor -> GuideHistoryScope.derive(
                        actor, GuideHistoryScope.Kind.SINGLEPLAYER, "fixture-world"));
        AtomicBoolean shutdown = new AtomicBoolean();
        GuideClientE2EController controller = new GuideClientE2EController(
                config, "fabric", "26.2", "test", services, new Gson(),
                () -> shutdown.set(true));
        UUID actor = UUID.fromString("30ab22ed-23fb-46f2-82ca-d4a656698eec");

        controller.tick(actor);
        while (!clientTasks.isEmpty()) clientTasks.removeFirst().run();

        assertFalse(controller.finished());
        assertFalse(Files.exists(report));

        history.loaded.complete(java.util.Optional.empty());
        while (!clientTasks.isEmpty()) clientTasks.removeFirst().run();
        controller.tick(actor);
        while (!clientTasks.isEmpty()) clientTasks.removeFirst().run();

        assertTrue(controller.finished());
        assertTrue(shutdown.get());
        assertEquals("COMPLETED", JsonParser.parseString(Files.readString(report))
                .getAsJsonObject().get("outcome").getAsString());
    }

    @Test
    void waitsForRecipeProvidersAndSubmitsOnlyOnceWhenReady() throws Exception {
        Path report = temporary.resolve("readiness/report.json");
        ArrayDeque<Runnable> clientTasks = new ArrayDeque<>();
        CountingLocal local = new CountingLocal();
        AtomicReference<RecipeProviderReadiness> readiness = new AtomicReference<>(
                RecipeProviderReadiness.waiting(
                        "recipe_provider_loading", "Viewer registry is loading"));
        GuideServiceManager services = services(local, clientTasks);
        GuideClientE2EController controller = new GuideClientE2EController(
                config(report), "fabric", "26.2", "test", services, new Gson(),
                () -> {}, readiness::get);
        UUID actor = UUID.fromString("30ab22ed-23fb-46f2-82ca-d4a656698eec");

        controller.tick(actor);
        while (!clientTasks.isEmpty()) clientTasks.removeFirst().run();
        assertFalse(controller.finished());
        assertEquals(0, local.calls.get());

        readiness.set(RecipeProviderReadiness.ready());
        controller.tick(actor);
        controller.tick(actor);
        while (!clientTasks.isEmpty()) clientTasks.removeFirst().run();

        assertTrue(controller.finished());
        assertEquals(1, local.calls.get());
        assertEquals("COMPLETED", JsonParser.parseString(Files.readString(report))
                .getAsJsonObject().get("outcome").getAsString());
    }

    @Test
    void recipeProviderFailureWritesHarnessFailureWithoutSubmitting() throws Exception {
        Path report = temporary.resolve("readiness-failed/report.json");
        ArrayDeque<Runnable> clientTasks = new ArrayDeque<>();
        CountingLocal local = new CountingLocal();
        GuideClientE2EController controller = new GuideClientE2EController(
                config(report), "fabric", "26.2", "test", services(local, clientTasks), new Gson(),
                () -> {}, () -> RecipeProviderReadiness.failed(
                        "recipe_provider_failed", "JEI capture failed for player-plain-value"));

        controller.tick(UUID.fromString("30ab22ed-23fb-46f2-82ca-d4a656698eec"));
        while (!clientTasks.isEmpty()) clientTasks.removeFirst().run();

        assertTrue(controller.finished());
        assertEquals(0, local.calls.get());
        var json = JsonParser.parseString(Files.readString(report)).getAsJsonObject();
        assertEquals("HARNESS_FAILED", json.get("outcome").getAsString());
        assertEquals("recipe_provider_failed", json.get("failureCode").getAsString());
        assertEquals("JEI capture failed for player-plain-value", json.get("failureMessage").getAsString());
    }

    @Test
    void seedsLongHistoryBeforeReportingTheAcceptanceRequest() throws Exception {
        Path report = temporary.resolve("history-seed/report.json");
        ArrayDeque<Runnable> clientTasks = new ArrayDeque<>();
        CountingLocal local = new CountingLocal();
        GuideClientE2EConfig config = new GuideClientE2EConfig(
                "fixture", "e2e", "question", GuideModelMode.CLIENT, report, true, 3);
        GuideClientE2EController controller = new GuideClientE2EController(
                config, "fabric", "26.2", "test", services(local, clientTasks), new Gson(),
                () -> {});

        controller.tick(UUID.fromString("30ab22ed-23fb-46f2-82ca-d4a656698eec"));
        while (!clientTasks.isEmpty()) clientTasks.removeFirst().run();

        assertTrue(controller.finished());
        assertEquals(4, local.calls.get());
        var json = JsonParser.parseString(Files.readString(report)).getAsJsonObject();
        assertEquals(4, json.getAsJsonObject("historyMetrics")
                .get("totalRequests").getAsLong());
        assertEquals("COMPLETED", json.get("outcome").getAsString());
    }

    @Test
    void optInStopCancelsTheActualRequestOnceAndRetainsPendingToolWithoutResult() throws Exception {
        String property = "openallay.e2e.cancelOnToolStart";
        String previous = System.getProperty(property);
        try {
            System.setProperty(property, "true");
            Path report = temporary.resolve("actual-stop/report.json");
            ArrayDeque<Runnable> tasks = new ArrayDeque<>();
            PendingJavascriptLocal local = new PendingJavascriptLocal();
            GuideServiceManager services = services(local, tasks);
            AtomicBoolean shutdown = new AtomicBoolean();
            GuideClientE2EController controller = new GuideClientE2EController(
                    config(report), "fabric", "26.2", "test", services, new Gson(),
                    () -> shutdown.set(true));
            UUID actor = UUID.fromString("30ab22ed-23fb-46f2-82ca-d4a656698eec");
            controller.tick(actor);
            while (!tasks.isEmpty()) tasks.removeFirst().run();
            controller.tick(actor);
            assertTrue(controller.finished());
            assertTrue(shutdown.get());
            assertEquals(1, local.cancelCalls.get());
            var request = services.forActor(actor).snapshot().sessions().stream()
                    .flatMap(value -> value.requests().stream()).findFirst().orElseThrow();
            assertEquals(local.requestId, request.requestId());
            assertEquals(dev.openallay.guide.GuideRequestStatus.CANCELLED, request.status());
            assertEquals(dev.openallay.guide.GuideToolStatus.RUNNING, request.tools().getFirst().status());
            assertEquals(null, request.tools().getFirst().normalized());
            var encoded = JsonParser.parseString(Files.readString(report)).getAsJsonObject();
            assertEquals("CANCELLED", encoded.get("outcome").getAsString());
            assertTrue(encoded.getAsJsonObject("actualStop").get("accepted").getAsBoolean());
            assertTrue(encoded.getAsJsonObject("actualStop").get("pendingToolHasNoNormalizedResult").getAsBoolean());
        } finally {
            if (previous == null) System.clearProperty(property); else System.setProperty(property, previous);
        }
    }

    private static final class PendingJavascriptLocal implements GuideLocalEndpoint {
        private final AtomicInteger cancelCalls = new AtomicInteger();
        private UUID requestId;
        @Override public Set<ContextCapability> requiredContext() { return Set.of(); }
        @Override public CompletableFuture<AgentResult> ask(UUID actor, String sessionId, UUID requestId,
                String question, ToolInvocationContext context, Consumer<AgentEvent> events) {
            this.requestId = requestId;
            events.accept(new AgentEvent.ToolStarted("actual-pending-js", "openallay:run_javascript"));
            return new CompletableFuture<>();
        }
        @Override public boolean cancel(UUID actor, String sessionId) { cancelCalls.incrementAndGet(); return true; }
        @Override public void clearSession(UUID actor, String sessionId) {}
        @Override public void clearActor(UUID actor) {}
    }

    private static GuideClientE2EConfig config(Path report) {
        return new GuideClientE2EConfig(
                "fixture", "e2e", "question", GuideModelMode.CLIENT, report, true);
    }

    private static GuideServiceManager services(
            GuideLocalEndpoint local, ArrayDeque<Runnable> clientTasks) {
        return new GuideServiceManager(
                local,
                new NoRemote(),
                (capabilities, correlation) -> new ToolResult.Success<>(
                        ToolInvocationContext.developmentConsole(correlation)),
                clientTasks::addLast,
                Clock.systemUTC(),
                new Gson());
    }

    private static final class CompletingLocal implements GuideLocalEndpoint {
        @Override public Set<ContextCapability> requiredContext() { return Set.of(); }
        @Override public java.util.Optional<dev.openallay.guide.GuideContextSpec> contextSpec(
                String profileId) {
            return java.util.Optional.of(new dev.openallay.guide.GuideContextSpec(
                    new dev.openallay.agent.context.ContextBudget(8_192, 1_024), 256, "fixture-model"));
        }

        @Override
        public CompletableFuture<AgentResult> ask(
                UUID actor,
                String sessionId,
                UUID requestId,
                String question,
                ToolInvocationContext context,
                Consumer<AgentEvent> events) {
            events.accept(new AgentEvent.StateChanged(AgentState.MODEL_WAIT));
            events.accept(new AgentEvent.ModelProgress(new ModelEvent.TextDelta("done")));
            events.accept(new AgentEvent.ModelProgress(
                    new ModelEvent.UsageUpdate(new ModelUsage(2, 1, 0))));
            events.accept(new AgentEvent.StateChanged(AgentState.COMPLETED));
            events.accept(new AgentEvent.FinalText("done"));
            return CompletableFuture.completedFuture(
                    new AgentResult(AgentState.COMPLETED, "done", null, null, null));
        }

        @Override public boolean cancel(UUID actor, String sessionId) { return true; }
        @Override public void clearSession(UUID actor, String sessionId) {}
        @Override public void clearActor(UUID actor) {}
    }

    private static final class CountingLocal implements GuideLocalEndpoint {
        private final AtomicInteger calls = new AtomicInteger();
        private final CompletingLocal delegate = new CompletingLocal();

        @Override public Set<ContextCapability> requiredContext() { return Set.of(); }

        @Override
        public CompletableFuture<AgentResult> ask(
                UUID actor,
                String sessionId,
                UUID requestId,
                String question,
                ToolInvocationContext context,
                Consumer<AgentEvent> events) {
            calls.incrementAndGet();
            return delegate.ask(actor, sessionId, requestId, question, context, events);
        }

        @Override public boolean cancel(UUID actor, String sessionId) { return true; }
        @Override public void clearSession(UUID actor, String sessionId) {}
        @Override public void clearActor(UUID actor) {}
    }

    private static final class NoRemote implements GuideRemoteEndpoint {
        @Override public boolean serverModelAvailable() { return false; }
        @Override public boolean serverToolsAvailable() { return false; }
        @Override public boolean ask(UUID requestId, String sessionId, String question,
                Consumer<AgentEvent> events) { return false; }
        @Override public boolean cancel(UUID requestId) { return false; }
        @Override public void disconnect() {}
    }

    private static final class DelayedHistory implements GuideHistoryAccess {
        private final CompletableFuture<java.util.Optional<GuideHistoryMetadata>> loaded = new CompletableFuture<>();

        @Override
        public CompletableFuture<java.util.Optional<GuideHistoryMetadata>> metadata(GuideHistoryScope scope) {
            return loaded;
        }

        @Override
        public CompletableFuture<GuideHistoryContextSeed> context(GuideHistoryContextRequest request) {
            return CompletableFuture.completedFuture(new GuideHistoryContextSeed(
                    request.sessionId(), List.of(), List.of(), 0));
        }

        @Override
        public CompletableFuture<Void> commit(GuideHistoryCommit commit) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> delete(
                dev.openallay.guide.history.GuideHistoryDeleteScope scope) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException());
        }

        @Override
        public CompletableFuture<Void> resetDatabase() {
            return CompletableFuture.failedFuture(new UnsupportedOperationException());
        }

        @Override
        public CompletableFuture<Void> flush() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public dev.openallay.guide.history.GuideHistoryActivity activity() {
            return dev.openallay.guide.history.GuideHistoryActivity.idle();
        }
    }
}
