package dev.openallay.guide;

import static org.junit.jupiter.api.Assertions.*;

import dev.openallay.agent.AgentEvent;
import dev.openallay.agent.AgentResult;
import dev.openallay.agent.AgentState;
import dev.openallay.context.ContextCapability;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.guide.history.*;
import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelRole;
import dev.openallay.tool.ToolResult;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

final class GuideServiceForkTest {
    private static final UUID ACTOR = UUID.fromString("8b02f884-8199-4624-a7a8-4d4fc036c580");
    private static final Instant NOW = Instant.parse("2026-10-02T08:00:00Z");
    private static final GuideHistoryScope SCOPE = GuideHistoryScope.derive(
            ACTOR, GuideHistoryScope.Kind.MULTIPLAYER, "service-fork.example");
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path temporary;

    @Test
    void forkContinuesFromExactContextWithNewRequestsAndNoProviderOrToolReplay() {
        Local local = new Local();
        GuideService service = service(local, null);
        UUID first = success(service.ask("first task").join());
        List<ModelMessage> firstContext = context("first task", "actual first answer");
        local.complete(first, firstContext, firstContext);
        UUID active = success(service.ask("still running successor").join());
        String branch = success(service.forkSession("main", first, "branch").join());

        assertEquals("branch", branch);
        assertEquals(2, local.calls);
        assertEquals(firstContext, local.hydrated.get("branch"));
        GuideRequestSnapshot inherited = session(service, "branch").requests().getFirst();
        assertNotEquals(first, inherited.requestId());
        assertEquals(GuideRequestStatus.COMPLETED, inherited.status());
        assertEquals("first task", inherited.userMessage());
        assertEquals(2, session(service, "main").requests().size());
        assertFalse(session(service, "main").requests().getLast().terminal());
        UUID continuation = success(service.ask("change direction").join());
        assertNotEquals(active, continuation);
        assertNotEquals(inherited.requestId(), continuation);
        assertEquals(3, local.calls);
        assertEquals(firstContext, local.historyAtAsk.get(continuation));
        assertTrue(success(service.closeSession("main").join()));
        assertEquals(2, session(service, "branch").requests().size());
    }

    @Test
    void memoryForkRetainsImagesFromOriginalRequestsEvenWhenSafeProjectionIsCompacted() throws Exception {
        Local local = new Local();
        dev.openallay.model.image.FileImageAttachmentStore images = new dev.openallay.model.image.FileImageAttachmentStore(
                temporary.resolve("images"));
        // A synthetic PNG fixture, not a game capture or clipboard read.
        var pixels = new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB);
        pixels.setRGB(0, 0, 0xff123456);
        var encoded = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(pixels, "png", encoded); pixels.flush();
        var reference = images.importImage(ACTOR, encoded.toByteArray());
        GuideService service = new GuideService(ACTOR, local, new Remote(),
                (capabilities, correlation) -> new ToolResult.Success<>(ToolInvocationContext.developmentConsole(correlation)),
                Runnable::run, Clock.fixed(NOW, ZoneOffset.UTC), dev.openallay.json.EngineJson.create(), null, null, images);
        UUID request = success(service.ask("historical picture").join());
        List<ModelMessage> original = List.of(ModelMessage.userInput("historical picture", List.of(reference)),
                new ModelMessage(ModelRole.ASSISTANT, List.of(new ModelContent.Text("picture result"))));
        List<ModelMessage> projected = context("compacted summary", "safe completed tail");
        local.complete(request, projected, original);
        awaitReleased(service, request);
        assertEquals("image-branch", success(service.forkSession("main", request, "image-branch").join()));
        service.closeSession("main").join();
        assertEquals(0, images.collect(ACTOR));
        assertArrayEquals(encoded.toByteArray(), images.read(ACTOR, reference));
        assertEquals(projected, local.hydrated.get("image-branch"));
        service.shutdown().get(2, java.util.concurrent.TimeUnit.SECONDS);
    }

    @Test
    void memoryForkRetainsNestedToolImagesFromCompactedOriginalRequests() throws Exception {
        Local local = new Local();
        dev.openallay.model.image.FileImageAttachmentStore images = new dev.openallay.model.image.FileImageAttachmentStore(
                temporary.resolve("images"));
        // A synthetic PNG fixture, not a game capture or clipboard read.
        var pixels = new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB);
        pixels.setRGB(0, 0, 0xff123456);
        var encoded = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(pixels, "png", encoded); pixels.flush();
        var reference = images.importImage(ACTOR, encoded.toByteArray());
        GuideService service = new GuideService(ACTOR, local, new Remote(),
                (capabilities, correlation) -> new ToolResult.Success<>(ToolInvocationContext.developmentConsole(correlation)),
                Runnable::run, Clock.fixed(NOW, ZoneOffset.UTC), dev.openallay.json.EngineJson.create(), null, null, images);
        UUID request = success(service.ask("historical picture").join());
        List<ModelMessage> original = List.of(ModelMessage.userText("historical picture"),
                new ModelMessage(ModelRole.ASSISTANT, List.of(new ModelContent.ToolUse(
                        "view", "capture", new com.google.gson.JsonObject()))),
                new ModelMessage(ModelRole.USER, List.of(new ModelContent.ToolResult("view",
                        new com.google.gson.JsonPrimitive("captured"), false, List.of(reference)))),
                new ModelMessage(ModelRole.ASSISTANT, List.of(new ModelContent.Text("picture result"))));
        List<ModelMessage> projected = context("compacted summary", "safe completed tail");
        local.complete(request, projected, original);
        awaitReleased(service, request);
        assertEquals("image-branch", success(service.forkSession("main", request, "image-branch").join()));
        service.closeSession("main").join();
        assertEquals(0, images.collect(ACTOR));
        assertArrayEquals(encoded.toByteArray(), images.read(ACTOR, reference));
        assertEquals(projected, local.hydrated.get("image-branch"));
        service.shutdown().get(2, java.util.concurrent.TimeUnit.SECONDS);
    }


    @Test
    void memoryForkFinishesIndependentlyAfterSourceClearDuringImageRetentionWithoutPartialAttach() throws Exception {
        Local local = new Local();
        var retained = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        dev.openallay.model.image.ImageAttachmentStore images = new dev.openallay.model.image.ImageAttachmentStore() {
            @Override public dev.openallay.model.image.ImageInputLimits limits() {
                return dev.openallay.model.image.ImageInputLimits.defaults();
            }
            @Override public dev.openallay.model.image.ImageReference importImage(UUID actor, byte[] bytes) {
                throw new UnsupportedOperationException();
            }
            @Override public dev.openallay.model.image.ImageReference importImage(UUID actor, String owner, byte[] bytes) {
                throw new UnsupportedOperationException();
            }
            @Override public byte[] read(UUID actor, dev.openallay.model.image.ImageReference reference) {
                throw new UnsupportedOperationException();
            }
            @Override public void reconcile(UUID actor, String namespace,
                    Map<String, List<dev.openallay.model.image.ImageReference>> owners) {}
            @Override public void retain(UUID actor, String owner,
                    List<dev.openallay.model.image.ImageReference> references) throws java.io.IOException {
                if (!owner.contains(":session:race-branch:")) return;
                retained.countDown();
                try {
                    if (!release.await(2, java.util.concurrent.TimeUnit.SECONDS)) {
                        throw new java.io.IOException("synthetic fork retain was not released");
                    }
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt(); throw new java.io.IOException(failure);
                }
            }
            @Override public void release(UUID actor, String owner) {}
            @Override public int collect(UUID actor) { return 0; }
        };
        GuideService service = new GuideService(ACTOR, local, new Remote(),
                (capabilities, correlation) -> new ToolResult.Success<>(ToolInvocationContext.developmentConsole(correlation)),
                Runnable::run, Clock.fixed(NOW, ZoneOffset.UTC), dev.openallay.json.EngineJson.create(), null, null, images);
        UUID first = success(service.ask("captured source").join());
        List<ModelMessage> original = context("captured source", "actual source answer");
        local.complete(first, original, original);
        awaitReleased(service, first);
        CompletableFuture<ToolResult<String>> forking = service.forkSession("main", first, "race-branch");
        try {
            assertTrue(retained.await(2, java.util.concurrent.TimeUnit.SECONDS));
            assertFalse(forking.isDone());
            assertTrue(success(service.clearSelectedSession().join()));
            assertEquals(0, session(service, "main").requests().size());
            assertFalse(service.snapshot().sessions().stream().anyMatch(session -> session.sessionId().equals("race-branch")));
            release.countDown();
            assertEquals("race-branch", success(forking.get(2, java.util.concurrent.TimeUnit.SECONDS)));
            assertEquals("main", service.snapshot().selectedSession());
            assertEquals(1, session(service, "race-branch").requests().size());
            assertEquals(original, local.hydrated.get("race-branch"));
            assertEquals("race-child", success(service.forkSession("race-branch",
                    session(service, "race-branch").requests().getFirst().requestId(), "race-child").join()));
            assertEquals(original, local.hydrated.get("race-child"));
        } finally { release.countDown(); }
    }

    @Test
    void rejectsActiveRequestAndMissingRealContextWithoutDisplayReconstruction() {
        Local local = new Local();
        GuideService service = service(local, null);
        UUID active = success(service.ask("unfinished").join());
        assertCode("fork_boundary_unavailable", service.forkSession("main", active, "bad").join());
        local.events.get(active).accept(new AgentEvent.FinalText("display only"));
        local.release(active);
        assertCode("fork_context_unavailable", service.forkSession("main", active, "bad").join());
        assertEquals(1, service.snapshot().sessions().size());
        assertEquals(1, local.calls);
    }

    @Test
    void cancelledUnfinalizedRequestCannotForkThenFinalizedSafeContextCanFork() {
        Local local = new Local();
        GuideService service = service(local, null);
        UUID request = success(service.ask("stop task").join());
        assertTrue(success(service.cancel().join()));
        assertCode("fork_boundary_unavailable", service.forkSession("main", request, "too-early").join());
        List<ModelMessage> finalized = context("stop task", "request ended: agent_cancelled");
        local.events.get(request).accept(new AgentEvent.ContextFinalized(finalized, finalized));
        local.release(request);
        assertEquals("after-stop", success(service.forkSession("main", request, "after-stop").join()));
        assertEquals(finalized, local.hydrated.get("after-stop"));
        assertEquals(GuideRequestStatus.CANCELLED, session(service, "after-stop").requests().getFirst().status());
        assertEquals(1, local.calls);
    }

    @Test
    void cancelledForkRetainsOnlyTheImmutableCheckpointDiagnosticPrefixCapturedAtStop() {
        Local local = new Local(); GuideService service = service(local, null);
        UUID request = success(service.ask("stopped diagnostics").join());
        List<ModelMessage> original = context("stopped diagnostics", "safe stopped answer");
        var initial = new dev.openallay.agent.context.ContextCheckpoint(UUID.randomUUID(), 0, original.size(),
                dev.openallay.agent.context.ContextSourceHash.compute(dev.openallay.json.EngineJson.create(), original), "fork-model", NOW,
                dev.openallay.agent.context.ContextCheckpoint.Status.SUCCEEDED, "initial diagnostic", null, null, 10);
        local.events.get(request).accept(new AgentEvent.ContextUpdated(original, original));
        local.events.get(request).accept(new AgentEvent.ContextCompacted(initial));
        service.cancel().join(); local.release(request);
        UUID successor = success(service.ask("successor diagnostics").join());
        var later = new dev.openallay.agent.context.ContextCheckpoint(UUID.randomUUID(), 0, original.size(),
                dev.openallay.agent.context.ContextSourceHash.compute(dev.openallay.json.EngineJson.create(), original), "fork-model", NOW,
                dev.openallay.agent.context.ContextCheckpoint.Status.FAILED, null,
                "synthetic_successor_failure", "successor-only diagnostic", 10);
        local.events.get(successor).accept(new AgentEvent.ContextCompacted(later));
        local.events.get(request).accept(new AgentEvent.ContextFinalized(original, original));
        assertEquals("diagnostic-branch", success(service.forkSession("main", request, "diagnostic-branch").join()));
        assertEquals(1, session(service, "diagnostic-branch").checkpoints().size());
        assertEquals(initial.sourceHash(), session(service, "diagnostic-branch").checkpoints().getFirst().sourceHash());
        assertEquals("initial diagnostic", session(service, "diagnostic-branch").checkpoints().getFirst().summary());
        assertTrue(session(service, "diagnostic-branch").checkpoints().stream()
                .noneMatch(checkpoint -> "synthetic_successor_failure".equals(checkpoint.failureCode())));
    }

    @Test
    void lateCancelledFinalizationCannotCopySuccessorContextIntoOldRequestBoundary() {
        Local local = new Local(); GuideService service = service(local, null);
        UUID stopped = success(service.ask("stopped task").join());
        service.cancel().join();
        local.release(stopped);
        UUID successor = success(service.ask("new task").join());
        List<ModelMessage> successorContext = context("new task", "new facts");
        local.events.get(successor).accept(new AgentEvent.ContextUpdated(successorContext, successorContext));
        List<ModelMessage> stoppedContext = context("stopped task", "safe stopped result");
        local.events.get(stopped).accept(new AgentEvent.ContextFinalized(stoppedContext, stoppedContext));
        assertEquals("stopped-branch", success(service.forkSession("main", stopped, "stopped-branch").join()));
        assertEquals(stoppedContext, local.hydrated.get("stopped-branch"));
        assertFalse(local.hydrated.get("stopped-branch").equals(successorContext));
        assertFalse(session(service, "main").requests().getLast().terminal());
        assertEquals(2, local.calls);
    }

    @Test
    void sameCheckpointIdentityKeepsFinalFailureWithoutResurrectingSummaryOnLaterPublish() {
        Local local = new Local(); History history = new History(); GuideService service = service(local, history);
        UUID request = success(service.ask("compensated checkpoint").join());
        List<ModelMessage> messages = context("compensated checkpoint", "actual retained context");
        UUID checkpointId = UUID.randomUUID();
        var succeeded = new dev.openallay.agent.context.ContextCheckpoint(checkpointId, 0, messages.size(),
                dev.openallay.agent.context.ContextSourceHash.compute(dev.openallay.json.EngineJson.create(), messages), "fork-model", NOW,
                dev.openallay.agent.context.ContextCheckpoint.Status.SUCCEEDED, "must not resurrect", null, null, 10);
        var failed = new dev.openallay.agent.context.ContextCheckpoint(checkpointId, 0, messages.size(),
                succeeded.sourceHash(), "fork-model", NOW,
                dev.openallay.agent.context.ContextCheckpoint.Status.FAILED, null,
                "synthetic_compensation", "runtime did not accept saved summary", 10);
        local.events.get(request).accept(new AgentEvent.ContextCompacted(succeeded));
        local.events.get(request).accept(new AgentEvent.ContextCompacted(failed));
        service.selectSession("other").join(); service.selectSession("main").join();
        List<dev.openallay.agent.context.ContextCheckpoint> changes = history.commits.stream()
                .flatMap(commit -> commit.mutations().stream())
                .filter(GuideHistoryMutation.AppendCheckpoint.class::isInstance)
                .map(GuideHistoryMutation.AppendCheckpoint.class::cast)
                .map(GuideHistoryMutation.AppendCheckpoint::checkpoint)
                .filter(checkpoint -> checkpoint.checkpointId().equals(checkpointId)).toList();
        assertEquals(List.of(succeeded, failed), changes);
        assertEquals(List.of(failed), session(service, "main").checkpoints());
        local.complete(request, messages, messages);
        List<dev.openallay.agent.context.ContextCheckpoint> finalChanges = history.commits.stream()
                .flatMap(commit -> commit.mutations().stream())
                .filter(GuideHistoryMutation.AppendCheckpoint.class::isInstance)
                .map(GuideHistoryMutation.AppendCheckpoint.class::cast)
                .map(GuideHistoryMutation.AppendCheckpoint::checkpoint)
                .filter(checkpoint -> checkpoint.checkpointId().equals(checkpointId)).toList();
        assertEquals(List.of(succeeded, failed), finalChanges);
    }

    @Test
    void durableForkDrainsWriterAndUsesCapturedCutoffWithoutHijackingNewSelection() {
        Local local = new Local();
        History history = new History();
        GuideService service = service(local, history);
        UUID first = success(service.ask("first").join());
        List<ModelMessage> firstContext = context("first", "actual answer");
        local.complete(first, firstContext, firstContext);
        history.deferWrites = true;
        service.selectSession("main").join();
        UUID successor = success(service.ask("source continues").join());
        CompletableFuture<ToolResult<String>> forking = service.forkSession("main", first, "branch");
        assertNull(history.forkRequest);
        history.completeWrites();
        assertEquals(first, history.forkRequest.mutation().cutoff().requestId());
        assertEquals(0, history.forkRequest.mutation().cutoff().sequence());
        service.selectSession("new-view").join();
        service.selectSession("main").join();
        history.forkCompletion.complete(result(history.forkRequest, firstContext));

        assertEquals("branch", success(forking.join()));
        assertEquals("main", service.snapshot().selectedSession());
        assertEquals(1, session(service, "branch").requests().size());
        assertEquals(firstContext, local.hydrated.get("branch"));
        service.selectSession("branch").join();
        UUID continuation = success(service.ask("branch continuation").join());
        assertEquals(continuation, session(service, "branch").requests().getLast().requestId());
        assertEquals(3, local.calls);
        // The fake still defers writes. A prior selection write owns the serial writer;
        // acknowledge it before inspecting the delivered append mutation.
        history.completeWrites();
        assertTrue(history.commits.stream().flatMap(commit -> commit.mutations().stream())
                .filter(GuideHistoryMutation.UpsertMessage.class::isInstance)
                .map(GuideHistoryMutation.UpsertMessage.class::cast)
                .anyMatch(message -> message.sessionId().equals("branch") && message.ordinal() == 2));
        assertFalse(history.commits.stream().flatMap(commit -> commit.mutations().stream())
                .filter(GuideHistoryMutation.UpsertMessage.class::isInstance)
                .map(GuideHistoryMutation.UpsertMessage.class::cast)
                .anyMatch(message -> message.sessionId().equals("branch") && message.ordinal() < 2));
        assertFalse(session(service, "main").requests().stream()
                .filter(request -> request.requestId().equals(successor)).findFirst().orElseThrow().terminal());
        assertEquals(3, local.calls);
    }

    @Test
    void contextLoadingCancellationNeverCreatesAGuessedWholeSessionForkBoundary() {
        Local local = new Local(); local.hasContext = false;
        History history = new History(); GuideService service = service(local, history);
        UUID request = success(service.ask("cancel before durable context is ready").join());
        assertEquals(GuideRequestStatus.CONTEXT_LOADING, session(service, "main").requests().getLast().status());
        assertTrue(success(service.cancel().join()));
        assertFalse(history.commits.stream().flatMap(commit -> commit.mutations().stream())
                .anyMatch(mutation -> mutation instanceof GuideHistoryMutation.CaptureRequestBoundary boundary
                        && boundary.requestId().equals(request)));
        assertFalse(history.commits.stream().flatMap(commit -> commit.mutations().stream())
                .anyMatch(GuideHistoryMutation.ReplaceContext.class::isInstance));
        assertTrue(history.commits.stream().flatMap(commit -> commit.mutations().stream())
                .filter(GuideHistoryMutation.ReplaceRequestContext.class::isInstance)
                .map(GuideHistoryMutation.ReplaceRequestContext.class::cast)
                .anyMatch(original -> original.requestId().equals(request) && original.messages().size() == 2));
        history.contextCompletion.complete(new GuideHistoryContextSeed("main",
                context("durable previous task", "previous facts"), List.of(), 10));
        assertEquals(0, local.calls);
        assertFalse(history.commits.stream().flatMap(commit -> commit.mutations().stream())
                .anyMatch(mutation -> mutation instanceof GuideHistoryMutation.CaptureRequestBoundary boundary
                        && boundary.requestId().equals(request)));
    }

    @Test
    void sessionLatestForkResolvesDurableNewestRatherThanOlderLoadedTerminalWithActiveSuccessor() {
        Local local = new Local(); History history = new History(); GuideService service = service(local, history);
        UUID older = success(service.ask("old loaded request").join());
        local.complete(older, context("old", "answer"), context("old", "answer"));
        UUID active = success(service.ask("source active").join());
        GuideRequestSnapshot latest = new GuideRequestSnapshot(UUID.randomUUID(), "main", GuideTopology.CLIENT_LOCAL,
                "true latest completed request", List.of(), GuideRequestStatus.COMPLETED, List.of(),
                dev.openallay.model.ModelUsage.empty(), null, null, NOW, NOW, NOW);
        GuideHistoryCursor cursor = new GuideHistoryCursor(0, latest.requestId());
        history.page = new GuideHistoryPage("main", List.of(latest), cursor, cursor, false, true);
        CompletableFuture<ToolResult<String>> forking = service.forkSelectedSession();
        assertEquals(GuideHistoryPageRequest.Direction.NEWEST, history.pageRequests.getLast().direction());
        assertEquals(2, history.pageRequests.getLast().count());
        assertEquals(latest.requestId(), history.forkRequest.mutation().cutoff().requestId());
        history.forkCompletion.complete(result(history.forkRequest, context("latest", "facts")));
        assertInstanceOf(ToolResult.Success.class, forking.join());
        assertEquals(2, local.calls);
        assertFalse(session(service, "main").requests().stream().filter(request -> request.requestId().equals(active))
                .findFirst().orElseThrow().terminal());
    }

    @Test
    void partitionDeletionCannotPassACommittedButUnattachedForkCallback() {
        Local local = new Local(); History history = new History(); GuideService service = service(local, history);
        UUID first = success(service.ask("first").join());
        local.complete(first, context("first", "answer"), context("first", "answer"));
        CompletableFuture<ToolResult<String>> forking = service.forkSession("main", first, "branch");
        assertNotNull(history.forkRequest);
        assertFalse(forking.isDone());
        assertCode("history_delete_busy", service.deleteCurrentHistory().join());
        history.forkCompletion.complete(result(history.forkRequest, context("first", "answer")));
        assertEquals("branch", success(forking.join()));
    }

    @Test
    void deletedSourceBeforeWriterDrainRejectsForkRatherThanResurrectingSource() {
        Local local = new Local(); History history = new History(); GuideService service = service(local, history);
        UUID first = success(service.ask("first").join());
        local.complete(first, context("first", "answer"), context("first", "answer"));
        history.deferWrites = true;
        service.selectSession("fresh").join();
        CompletableFuture<ToolResult<String>> forking = service.forkSession("main", first, "branch");
        service.closeSession("main").join();
        history.completeWrites();
        assertCode("fork_source_changed", forking.join());
        assertNull(history.forkRequest);
        assertFalse(service.snapshot().sessions().stream().anyMatch(session -> session.sessionId().equals("main")));
        assertFalse(service.snapshot().sessions().stream().anyMatch(session -> session.sessionId().equals("branch")));
    }

    private static GuideHistoryForkResult result(GuideHistoryForkRequest request, List<ModelMessage> context) {
        UUID id = UUID.randomUUID();
        GuideRequestSnapshot inherited = new GuideRequestSnapshot(id, request.mutation().sessionId(),
                GuideTopology.CLIENT_LOCAL, "inherited first", List.of(), GuideRequestStatus.COMPLETED,
                List.of(), dev.openallay.model.ModelUsage.empty(), null, null, NOW, NOW, NOW,
                request.mutation().modelSelection());
        GuideHistoryCursor cursor = new GuideHistoryCursor(0, id);
        return new GuideHistoryForkResult(new GuideHistoryMetadata.Session(request.mutation().sessionId(),
                request.mutation().ordinal(), request.mutation().modelSelection(), 1, cursor, cursor),
                new GuideHistoryPage(request.mutation().sessionId(), List.of(inherited), cursor, cursor, false, false),
                context, List.of(), 2);
    }
    private static GuideService service(Local local, History history) {
        return new GuideService(ACTOR, local, new Remote(),
                (capabilities, correlation) -> new ToolResult.Success<>(ToolInvocationContext.developmentConsole(correlation)),
                Runnable::run, Clock.fixed(NOW, ZoneOffset.UTC), dev.openallay.json.EngineJson.create(), history == null ? null : SCOPE, history);
    }
    private static void awaitReleased(GuideService service, UUID requestId) throws Exception {
        CompletableFuture<Void> released = new CompletableFuture<>();
        try (GuideSubscription ignored = service.subscribe(snapshot -> {
            boolean working = snapshot.sessions().stream().anyMatch(session ->
                    requestId.equals(session.workingRequestId()));
            if (!working) released.complete(null);
        })) {
            released.get(2, java.util.concurrent.TimeUnit.SECONDS);
        }
    }
    private static GuideSessionSnapshot session(GuideService service, String id) {
        return service.snapshot().sessions().stream().filter(session -> session.sessionId().equals(id)).findFirst().orElseThrow();
    }
    private static List<ModelMessage> context(String question, String answer) {
        return List.of(ModelMessage.userText(question), new ModelMessage(ModelRole.ASSISTANT, List.of(new ModelContent.Text(answer))));
    }
    @SuppressWarnings("unchecked") private static <T> T success(ToolResult<T> result) {
        return ((ToolResult.Success<T>) assertInstanceOf(ToolResult.Success.class, result,
                () -> "Expected accepted fork/request, got " + result)).value();
    }
    private static void assertCode(String code, ToolResult<?> result) {
        assertEquals(code, ((ToolResult.Failure<?>) assertInstanceOf(ToolResult.Failure.class, result)).code());
    }
    private static final class Local implements GuideLocalEndpoint {
        private final Map<UUID, Consumer<AgentEvent>> events = new LinkedHashMap<>();
        private final Map<UUID, CompletableFuture<AgentResult>> completions = new LinkedHashMap<>();
        private final Map<String, List<ModelMessage>> hydrated = new LinkedHashMap<>();
        private final Map<UUID, List<ModelMessage>> historyAtAsk = new LinkedHashMap<>();
        private int calls;
        private boolean hasContext = true;
        @Override public Set<ContextCapability> requiredContext() { return Set.of(); }
        @Override public boolean hasContext(UUID actor, String sessionId) { return hasContext; }
        @Override public Optional<GuideContextSpec> contextSpec(String profileId) {
            return Optional.of(new GuideContextSpec(new dev.openallay.agent.context.ContextBudget(8_192, 1_024),
                    256, "fork-model"));
        }
        @Override public void hydrateContext(UUID actor, String sessionId, List<ModelMessage> messages,
                List<dev.openallay.agent.context.ContextCheckpoint> checkpoints) { hydrated.put(sessionId, messages); }
        @Override public CompletableFuture<AgentResult> ask(UUID actor, String sessionId, UUID requestId,
                String question, ToolInvocationContext context, Consumer<AgentEvent> events) {
            calls++; this.events.put(requestId, events);
            historyAtAsk.put(requestId, hydrated.getOrDefault(sessionId, List.of()));
            events.accept(new AgentEvent.StateChanged(AgentState.MODEL_WAIT));
            CompletableFuture<AgentResult> completion = new CompletableFuture<>();
            completions.put(requestId, completion);
            return completion;
        }
        private void complete(UUID request, List<ModelMessage> projected, List<ModelMessage> original) {
            events.get(request).accept(new AgentEvent.ContextUpdated(projected, original));
            events.get(request).accept(new AgentEvent.FinalText("visible answer"));
            release(request);
        }
        private void release(UUID request) {
            completions.get(request).complete(new AgentResult(AgentState.COMPLETED,
                    "visible answer", null, null, null));
        }
        @Override public boolean cancel(UUID actor, String sessionId) { return true; }
        @Override public void clearSession(UUID actor, String sessionId) { hydrated.remove(sessionId); }
        @Override public void clearActor(UUID actor) { hydrated.clear(); }
    }
    private static final class Remote implements GuideRemoteEndpoint {
        @Override public boolean serverModelAvailable() { return false; }
        @Override public boolean serverToolsAvailable() { return false; }
        @Override public boolean ask(UUID id, String session, String question, Consumer<AgentEvent> events) { return false; }
        @Override public boolean cancel(UUID requestId) { return false; }
        @Override public void disconnect() {}
    }
    private static final class History implements GuideHistoryAccess {
        private boolean deferWrites;
        private final List<GuideHistoryCommit> commits = new ArrayList<>();
        private final List<CompletableFuture<Void>> writes = new ArrayList<>();
        private GuideHistoryForkRequest forkRequest;
        private GuideHistoryPage page;
        private final List<GuideHistoryPageRequest> pageRequests = new ArrayList<>();
        private final CompletableFuture<GuideHistoryContextSeed> contextCompletion = new CompletableFuture<>();
        private final CompletableFuture<GuideHistoryForkResult> forkCompletion = new CompletableFuture<>();
        @Override public CompletableFuture<Optional<GuideHistoryMetadata>> metadata(GuideHistoryScope scope) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        @Override public CompletableFuture<GuideHistoryPage> page(GuideHistoryPageRequest request) {
            pageRequests.add(request);
            return CompletableFuture.completedFuture(page);
        }
        @Override public CompletableFuture<GuideHistoryContextSeed> context(GuideHistoryContextRequest request) {
            return contextCompletion;
        }
        @Override public CompletableFuture<Void> commit(GuideHistoryCommit commit) {
            commits.add(commit);
            CompletableFuture<Void> future = new CompletableFuture<>(); writes.add(future);
            if (!deferWrites) future.complete(null); return future;
        }
        private void completeWrites() {
            for (int index = 0; index < writes.size(); index++) writes.get(index).complete(null);
        }
        @Override public CompletableFuture<GuideHistoryForkResult> fork(GuideHistoryForkRequest request) {
            forkRequest = request; return forkCompletion;
        }
        @Override public CompletableFuture<Void> delete(GuideHistoryDeleteScope scope) { return CompletableFuture.completedFuture(null); }
        @Override public CompletableFuture<Void> resetDatabase() { return CompletableFuture.completedFuture(null); }
        @Override public CompletableFuture<Void> flush() { return CompletableFuture.completedFuture(null); }
        @Override public GuideHistoryActivity activity() { return GuideHistoryActivity.idle(); }
    }
}
