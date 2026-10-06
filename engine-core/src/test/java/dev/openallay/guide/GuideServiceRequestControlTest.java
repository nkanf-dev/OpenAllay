package dev.openallay.guide;

import static org.junit.jupiter.api.Assertions.*;

import dev.openallay.agent.AgentEvent;
import dev.openallay.agent.AgentResult;
import dev.openallay.agent.AgentState;
import dev.openallay.context.ContextCapability;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.model.ModelMessage;
import dev.openallay.tool.ToolResult;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

final class GuideServiceRequestControlTest {
    @Test
    void terminalDisplayDoesNotDrainUntilEndpointActuallyReleasesAndUsesFreshRequestIdentity() {
        Endpoint endpoint = new Endpoint();
        GuideService service = service(endpoint);
        UUID first = success(service.ask("first").join());
        UUID receipt = success(service.followUp("second").join());
        assertEquals(1, endpoint.calls.size());
        assertEquals(1, session(service, "main").requests().size());
        assertEquals(List.of("first"), session(service, "main").messages().stream().map(GuideMessage::text).toList());
        assertEquals(receipt, service.pendingMessages("main").getFirst().id());
        endpoint.terminal(first, "answer");
        assertEquals(1, endpoint.calls.size());
        assertEquals(first, session(service, "main").workingRequestId());
        endpoint.release(first, "answer");
        assertEquals(2, endpoint.calls.size());
        Call followup = endpoint.calls.getLast();
        assertNotEquals(first, followup.id());
        assertNotEquals(receipt, followup.id());
        assertEquals("second", followup.text());
        assertTrue(service.pendingMessages("main").isEmpty());
        assertEquals(List.of("first", "answer", "second"), session(service, "main").messages().stream()
                .map(GuideMessage::text).toList());
        endpoint.release(first, "duplicate late answer");
        assertEquals(2, endpoint.calls.size());
    }

    @Test
    void observerHandoffBeforeEndpointCompletionPreservesFinalContextBeforeQueueDrain() {
        ArrayDeque<Runnable> ownerQueue = new ArrayDeque<>();
        Endpoint endpoint = new Endpoint();
        GuideService service = new GuideService(UUID.randomUUID(), endpoint, offline(),
                (required, correlation) -> new ToolResult.Success<>(ToolInvocationContext.developmentConsole(correlation)),
                ownerQueue::addLast, Clock.systemUTC(), dev.openallay.json.EngineJson.create());
        CompletableFuture<ToolResult<UUID>> asking = service.ask("original goal");
        runOwner(ownerQueue);
        UUID first = success(asking.join());
        CompletableFuture<ToolResult<UUID>> queued = service.followUp("next goal");
        runOwner(ownerQueue);
        success(queued.join());
        List<ModelMessage> finalContext = List.of(ModelMessage.userText("original goal"),
                new ModelMessage(dev.openallay.model.ModelRole.ASSISTANT,
                        List.of(new dev.openallay.model.ModelContent.Text("final answer"))));
        // Match ClientGuideRuntime's owner-thread delivery contract: observer tasks precede
        // the endpoint's owner-thread result handoff, even though GuideService schedules apply.
        ownerQueue.addLast(() -> endpoint.emit(first, new AgentEvent.ContextUpdated(finalContext, finalContext)));
        ownerQueue.addLast(() -> endpoint.emit(first, new AgentEvent.ContextFinalized(finalContext, finalContext)));
        ownerQueue.addLast(() -> endpoint.terminal(first, "final answer"));
        ownerQueue.addLast(() -> endpoint.release(first, "final answer"));
        runOwner(ownerQueue);
        assertEquals(2, endpoint.calls.size());
        CompletableFuture<ToolResult<dev.openallay.guide.export.GuideSessionExportSnapshot>> exporting =
                service.captureSelectedSessionForExport();
        runOwner(ownerQueue);
        assertEquals(finalContext, success(exporting.join()).requests().getFirst().originalContext());
    }

    @Test
    void queuedInstructionsStayInTheirSessionAcrossSelectionAndCaptureModelOnlyAtDispatch() {
        Endpoint endpoint = new Endpoint();
        GuideService service = service(endpoint);
        UUID first = success(service.ask("main goal").join());
        UUID queued = success(service.followUp("main next").join());
        assertTrue(success(service.editPending(queued, "main edited").join()));
        success(service.setModelSelection(GuideModelSelection.client("next")).join());
        success(service.selectSession("other").join());
        UUID other = success(service.ask("other goal").join());
        endpoint.terminal(first, "main done");
        endpoint.release(first, "main done");
        Call delivered = endpoint.calls.getLast();
        assertEquals("main", delivered.session());
        assertEquals("next", delivered.profile());
        assertEquals("main edited", delivered.text());
        assertEquals(other, session(service, "other").workingRequestId());
    }

    @Test
    void stopClearsWholeQueueAndLateReleasedWorkerCannotDrainRecreatedSession() {
        Endpoint endpoint = new Endpoint();
        GuideService service = service(endpoint);
        UUID first = success(service.ask("first").join());
        success(service.followUp("must not run").join());
        success(service.steer("also must not run").join());
        assertTrue(success(service.cancel().join()));
        assertTrue(service.pendingMessages("main").isEmpty());
        endpoint.release(first, "late first");
        assertEquals(1, endpoint.calls.size());
        UUID second = success(service.ask("second").join());
        success(service.followUp("closed queue").join());
        success(service.closeSession("main").join());
        UUID recreated = success(service.ask("new session goal").join());
        endpoint.terminal(second, "late old session answer");
        endpoint.release(second, "late old session answer");
        assertEquals(recreated, session(service, "main").workingRequestId());
        assertEquals(3, endpoint.calls.size());
        assertEquals(List.of("new session goal"), session(service, "main").messages().stream().map(GuideMessage::text).toList());
    }

    @Test
    void finalTurnUnconsumedSteerBecomesFollowupButAppliedSteerIsDurableUserNotQueuedHistory() {
        Endpoint endpoint = new Endpoint();
        GuideService service = service(endpoint);
        UUID first = success(service.ask("goal").join());
        UUID steer = success(service.steer("next safe boundary").join());
        assertEquals(GuidePendingMessage.Kind.STEER, service.pendingMessages("main").getFirst().kind());
        assertTrue(session(service, "main").requests().getFirst().timeline().isEmpty());
        endpoint.terminal(first, "already final");
        endpoint.release(first, "already final");
        assertEquals("next safe boundary", endpoint.calls.getLast().text());
        UUID next = endpoint.calls.getLast().id();
        UUID applied = success(service.steer("actual supplemental instruction").join());
        ModelMessage message = ModelMessage.userText("actual supplemental instruction");
        endpoint.emit(next, new AgentEvent.SteerApplied(applied, message));
        endpoint.emit(next, new AgentEvent.SteerApplied(applied, message));
        assertTrue(service.pendingMessages("main").isEmpty());
        assertEquals(1, session(service, "main").requests().getLast().timeline().size());
        assertEquals(new GuideTimelineEntry.User(0, applied, "actual supplemental instruction"),
                session(service, "main").requests().getLast().timeline().getFirst());
        assertNotEquals(steer, next);
    }

    @Test
    void cancellationOfOneQueuedMessageAndDisconnectDoNotLeaveHiddenWork() {
        Endpoint endpoint = new Endpoint();
        GuideService service = service(endpoint);
        UUID first = success(service.ask("goal").join());
        UUID removed = success(service.followUp("remove").join());
        success(service.followUp("keep until disconnect").join());
        assertTrue(success(service.cancelPending(removed).join()));
        assertFalse(success(service.cancelPending(removed).join()));
        assertEquals(1, service.pendingMessages("main").size());
        CompletableFuture<Void> disconnected = service.disconnect();
        assertFalse(disconnected.isDone());
        endpoint.release(first, "endpoint resources actually closed");
        disconnected.join();
        endpoint.terminal(first, "late disconnected");
        endpoint.release(first, "late disconnected");
        assertEquals(1, endpoint.calls.size());
        assertTrue(service.pendingMessages("main").isEmpty());
        assertTrue(session(service, "main").requests().isEmpty());
    }

    @Test
    void providerFailureDrainsOnlyAfterCleanupAndRejectedSteerIsNotLost() {
        Endpoint endpoint = new Endpoint();
        GuideService service = service(endpoint);
        UUID first = success(service.ask("goal").join());
        UUID steer = success(service.steer("supplement after failed turn").join());
        endpoint.emit(first, new AgentEvent.SteerRejected(steer));
        endpoint.emit(first, new AgentEvent.ModelProgress(new dev.openallay.model.ModelEvent.RateLimited(500, 1)));
        assertEquals(1, endpoint.calls.size());
        endpoint.emit(first, new AgentEvent.Failed("network_failure", "disconnected provider"));
        assertEquals(1, endpoint.calls.size());
        endpoint.futures.get(first).complete(new AgentResult(AgentState.FAILED, null,
                "network_failure", "disconnected provider", null));
        assertEquals(2, endpoint.calls.size());
        assertEquals("supplement after failed turn", endpoint.calls.getLast().text());
    }

    @Test
    void textRequestRegistersItsOwnerBeforeRetentionWhenAnImageStoreIsInstalled() {
        Owner owner = new Owner(); PinStore store = new PinStore(); Endpoint endpoint = new Endpoint();
        GuideService service = imageService(endpoint, store, owner);
        CompletableFuture<ToolResult<UUID>> asking = service.ask("text with attachment support installed");
        owner.runAll();
        UUID request = success(asking.join());
        assertEquals(request, session(service, "main").workingRequestId());
        assertEquals(ModelMessage.userText("text with attachment support installed"), service.userInput(request));
        assertEquals(1, endpoint.calls.size());
        assertEquals(request, endpoint.calls.getFirst().id());
        assertTrue(service.pendingMessages("main").isEmpty());
    }

    @Test
    void slowImageSteerAdmissionCannotBeOvertakenByTextOrFollowup() throws Exception {
        Owner owner = new Owner();
        PinStore store = new PinStore();
        Endpoint endpoint = new Endpoint();
        GuideService service = imageService(endpoint, store, owner);
        CompletableFuture<ToolResult<UUID>> asking = service.ask("active goal");
        owner.runAll();
        success(asking.join());
        store.blockImage = image('a');
        ModelMessage firstMessage = new ModelMessage(dev.openallay.model.ModelRole.USER, List.of(
                new dev.openallay.model.ModelContent.Text("A first"),
                new dev.openallay.model.ModelContent.Image(store.blockImage),
                new dev.openallay.model.ModelContent.Image(image('e'))));
        CompletableFuture<ToolResult<UUID>> first = service.steer(firstMessage);
        owner.runAll();
        assertTrue(store.entered.await(2, java.util.concurrent.TimeUnit.SECONDS));
        CompletableFuture<ToolResult<UUID>> second = service.steer("B second");
        CompletableFuture<ToolResult<UUID>> followup = service.followUp("C follow-up");
        owner.runAll();
        assertFalse(second.isDone());
        assertFalse(followup.isDone());
        assertTrue(endpoint.steers.isEmpty());
        store.release.countDown();
        owner.runNext();
        owner.runAll();
        success(first.join());
        success(second.join());
        success(followup.join());
        assertEquals(List.of("A first", "B second"), endpoint.steers.stream()
                .map(GuidePendingMessage::displayText).toList());
        assertEquals(1, endpoint.calls.size(), "follow-up never enters the active request");
        assertEquals(List.of("A first", "B second", "C follow-up"), service.pendingMessages("main").stream()
                .map(GuidePendingMessage::text).toList());
    }

    @Test
    void failedEarlierImageAdmissionUnblocksLaterSteerWithoutReorderingOrLosingIt() throws Exception {
        Owner owner = new Owner();
        PinStore store = new PinStore();
        Endpoint endpoint = new Endpoint();
        GuideService service = imageService(endpoint, store, owner);
        var asking = service.ask("active goal"); owner.runAll(); success(asking.join());
        store.blockImage = image('b'); store.failBlocked = true;
        var first = service.steer(withImage("A cannot be retained", store.blockImage));
        owner.runAll(); assertTrue(store.entered.await(2, java.util.concurrent.TimeUnit.SECONDS));
        var second = service.steer("B retained"); owner.runAll();
        assertFalse(second.isDone()); assertTrue(endpoint.steers.isEmpty());
        store.release.countDown(); owner.runNext(); owner.runAll();
        assertInstanceOf(ToolResult.Failure.class, first.join()); success(second.join());
        assertEquals(List.of("B retained"), endpoint.steers.stream().map(GuidePendingMessage::displayText).toList());
    }

    @Test
    void stopRevokesReadyAndInFlightSteerAdmissionsAndCancelledImageEditCannotReviveMessage() throws Exception {
        Owner owner = new Owner(); PinStore store = new PinStore(); Endpoint endpoint = new Endpoint();
        GuideService service = imageService(endpoint, store, owner);
        var asking = service.ask("active goal"); owner.runAll(); success(asking.join());
        var accepted = service.steer("original A"); owner.runAll(); UUID original = success(accepted.join());
        store.blockImage = image('c');
        var editing = service.editPending(original, withImage("edited A", store.blockImage));
        owner.runAll(); assertTrue(store.entered.await(2, java.util.concurrent.TimeUnit.SECONDS));
        var later = service.steer("B waiting behind edit"); owner.runAll(); assertFalse(later.isDone());
        var removing = service.cancelPending(original); owner.runAll(); assertTrue(success(removing.join()));
        store.release.countDown(); owner.runNext(); owner.runAll();
        assertFalse(success(editing.join())); success(later.join());
        assertEquals(List.of("original A", "B waiting behind edit"), endpoint.steers.stream()
                .map(GuidePendingMessage::displayText).toList());
        store.gate(image('d'));
        var inFlight = service.steer(withImage("C stopped", store.blockImage));
        owner.runAll(); assertTrue(store.entered.await(2, java.util.concurrent.TimeUnit.SECONDS));
        var ready = service.steer("D stopped"); owner.runAll(); assertFalse(ready.isDone());
        var stopping = service.cancel(); owner.runAll(); assertTrue(success(stopping.join()));
        assertInstanceOf(ToolResult.Failure.class, ready.join());
        store.release.countDown(); owner.runNext(); owner.runAll();
        assertInstanceOf(ToolResult.Failure.class, inFlight.join());
        assertEquals(2, endpoint.steers.size());
        assertTrue(service.pendingMessages("main").isEmpty());
    }

    @Test void capturedVoiceAdmissionUsesOriginalSessionAndReportsActualRequestRatherThanPendingReceipt() {
        ArrayDeque<Runnable> queue = new ArrayDeque<>(); Endpoint endpoint = new Endpoint();
        GuideService service = new GuideService(UUID.randomUUID(), endpoint, offline(),
                (required, correlation) -> new ToolResult.Success<>(ToolInvocationContext.developmentConsole(correlation)),
                queue::addLast, Clock.systemUTC(), dev.openallay.json.EngineJson.create());
        UUID owner = service.presentationSessionOwner("main").orElseThrow();
        var selection = service.selectSession("other"); runOwner(queue); success(selection.join());
        var delivered = service.followUp("main", owner, "/compact spoken literal", () -> true);
        runOwner(queue); GuideService.InputReceipt receipt = success(delivered.join());
        assertFalse(receipt.queued()); assertEquals(1, endpoint.calls.size());
        assertEquals(receipt.id(), endpoint.calls.getFirst().id());
        assertEquals("main", endpoint.calls.getFirst().session());
        assertEquals("/compact spoken literal", endpoint.calls.getFirst().text());
        assertEquals("other", service.snapshot().selectedSession());
        assertTrue(service.pendingMessages("main").isEmpty());
        assertTrue(session(service, "other").requests().isEmpty()); assertTrue(endpoint.steers.isEmpty());
    }

    @Test void capturedVoiceDuringRealWorkIsFifoFollowUpNotSteerAndNeverEditsExistingQueue() {
        Endpoint endpoint = new Endpoint(); GuideService service = service(endpoint);
        UUID owner = service.presentationSessionOwner("main").orElseThrow();
        UUID first = success(service.ask("active goal").join());
        UUID existing = success(service.followUp("existing typed follow-up").join());
        service.selectSession("other").join();
        GuideService.InputReceipt voice = success(service.followUp("main", owner, "spoken follow-up", () -> true).join());
        assertTrue(voice.queued()); assertNotEquals(first, voice.id()); assertNotEquals(existing, voice.id());
        assertEquals(List.of("existing typed follow-up", "spoken follow-up"), service.pendingMessages("main").stream()
                .map(GuidePendingMessage::text).toList());
        assertTrue(service.pendingMessages("main").stream().allMatch(value -> value.kind() == GuidePendingMessage.Kind.FOLLOW_UP));
        assertTrue(endpoint.steers.isEmpty()); assertEquals(1, endpoint.calls.size());
        endpoint.terminal(first, "done but not released"); assertEquals(1, endpoint.calls.size());
        endpoint.release(first, "done"); assertEquals("existing typed follow-up", endpoint.calls.getLast().text());
        UUID second = endpoint.calls.getLast().id(); endpoint.terminal(second, "second done"); endpoint.release(second, "second done");
        assertEquals("spoken follow-up", endpoint.calls.getLast().text()); assertEquals("main", endpoint.calls.getLast().session());
        assertNotEquals(voice.id(), endpoint.calls.getLast().id()); assertEquals("other", service.snapshot().selectedSession());
    }

    @Test void capturedVoiceRejectedOwnerDisconnectedOrUnavailableModelDoesNotCreateWork() {
        Endpoint endpoint = new Endpoint(); GuideService service = service(endpoint);
        UUID oldOwner = service.presentationSessionOwner("main").orElseThrow();
        success(service.closeSession("main").join());
        assertNotEquals(oldOwner, service.presentationSessionOwner("main").orElseThrow());
        assertInstanceOf(ToolResult.Failure.class, service.followUp("main", oldOwner, "stale speech", () -> true).join());
        UUID owner = service.presentationSessionOwner("main").orElseThrow();
        GuideService unavailable = new GuideService(UUID.randomUUID(), null, offline(),
                (required, correlation) -> new ToolResult.Success<>(ToolInvocationContext.developmentConsole(correlation)),
                Runnable::run, Clock.systemUTC(), dev.openallay.json.EngineJson.create());
        UUID unavailableOwner = unavailable.presentationSessionOwner("main").orElseThrow();
        ToolResult<GuideService.InputReceipt> rejected = unavailable.followUp("main", unavailableOwner, "no model", () -> true).join();
        assertInstanceOf(ToolResult.Failure.class, rejected); assertTrue(endpoint.calls.isEmpty());
        assertTrue(session(unavailable, "main").requests().isEmpty()); assertTrue(unavailable.pendingMessages("main").isEmpty());
        service.disconnect().join();
        assertInstanceOf(ToolResult.Failure.class, service.followUp("main", owner, "disconnected", () -> true).join());
        assertTrue(endpoint.calls.isEmpty());
    }

    @Test void capturedVoiceFenceIsCheckedBeforePreparationAndAgainBeforeActualAdmission() {
        for (boolean afterPreparation : List.of(false, true)) {
            ArrayDeque<Runnable> queue = new ArrayDeque<>(); Endpoint endpoint = new Endpoint();
            GuideService service = new GuideService(UUID.randomUUID(), endpoint, offline(),
                    (required, correlation) -> new ToolResult.Success<>(ToolInvocationContext.developmentConsole(correlation)),
                    queue::addLast, Clock.systemUTC(), dev.openallay.json.EngineJson.create());
            UUID owner = service.presentationSessionOwner("main").orElseThrow();
            java.util.concurrent.atomic.AtomicBoolean allowed = new java.util.concurrent.atomic.AtomicBoolean(true);
            var delivered = service.followUp("main", owner, "cancel before admission", allowed::get);
            if (afterPreparation) { queue.removeFirst().run(); assertFalse(delivered.isDone()); }
            allowed.set(false); runOwner(queue);
            assertInstanceOf(ToolResult.Failure.class, delivered.join()); assertTrue(endpoint.calls.isEmpty());
            assertTrue(service.pendingMessages("main").isEmpty()); assertTrue(session(service, "main").requests().isEmpty());
            allowed.set(true);
            var retry = service.followUp("main", owner, "explicit retry", allowed::get); runOwner(queue);
            assertFalse(success(retry.join()).queued()); assertEquals(1, endpoint.calls.size());
        }
    }

    @Test void capturedVoiceFinalOwnerFenceRejectsSessionClearBetweenPreparationAndAdmission() {
        ArrayDeque<Runnable> queue = new ArrayDeque<>(); Endpoint endpoint = new Endpoint();
        GuideService service = new GuideService(UUID.randomUUID(), endpoint, offline(),
                (required, correlation) -> new ToolResult.Success<>(ToolInvocationContext.developmentConsole(correlation)),
                queue::addLast, Clock.systemUTC(), dev.openallay.json.EngineJson.create());
        UUID owner = service.presentationSessionOwner("main").orElseThrow();
        var delivered = service.followUp("main", owner, "stale after clear", () -> true);
        var cleared = service.clearSelectedSession();
        queue.removeFirst().run(); queue.removeFirst().run(); runOwner(queue);
        assertInstanceOf(ToolResult.Success.class, cleared.join());
        assertNotEquals(owner, service.presentationSessionOwner("main").orElseThrow());
        assertInstanceOf(ToolResult.Failure.class, delivered.join()); assertTrue(endpoint.calls.isEmpty());
        assertTrue(service.pendingMessages("main").isEmpty());
    }

    @Test void capturedVoiceHeldBehindOlderImageAdmissionKeepsFifoAndHonorsLateCancellation() throws Exception {
        for (boolean cancelled : List.of(false, true)) {
            Owner owner = new Owner(); PinStore store = new PinStore(); Endpoint endpoint = new Endpoint();
            GuideService service = imageService(endpoint, store, owner);
            var asking = service.ask("active goal"); owner.runAll(); success(asking.join());
            UUID sessionOwner = service.presentationSessionOwner("main").orElseThrow();
            store.gate(image(cancelled ? 'd' : 'e'));
            var older = service.followUp(withImage("older image follow-up", store.blockImage)); owner.runAll();
            assertTrue(store.entered.await(2, java.util.concurrent.TimeUnit.SECONDS));
            java.util.concurrent.atomic.AtomicBoolean allowed = new java.util.concurrent.atomic.AtomicBoolean(true);
            var voice = service.followUp("main", sessionOwner, "voice behind image", allowed::get); owner.runAll();
            assertFalse(voice.isDone()); assertFalse(older.isDone());
            if (cancelled) allowed.set(false);
            store.release.countDown(); owner.runNext(); owner.runAll(); success(older.join());
            if (cancelled) {
                assertInstanceOf(ToolResult.Failure.class, voice.join());
                assertEquals(List.of("older image follow-up"), service.pendingMessages("main").stream().map(GuidePendingMessage::text).toList());
            } else {
                assertTrue(success(voice.join()).queued());
                assertEquals(List.of("older image follow-up", "voice behind image"), service.pendingMessages("main").stream().map(GuidePendingMessage::text).toList());
            }
            assertEquals(1, endpoint.calls.size()); assertTrue(endpoint.steers.isEmpty());
        }
    }

    private static dev.openallay.model.image.ImageReference image(char hash) {
        return new dev.openallay.model.image.ImageReference(String.valueOf(hash).repeat(64), "image/png", 1, 1, 1);
    }
    private static ModelMessage withImage(String text, dev.openallay.model.image.ImageReference image) {
        return new ModelMessage(dev.openallay.model.ModelRole.USER, List.of(
                new dev.openallay.model.ModelContent.Text(text), new dev.openallay.model.ModelContent.Image(image)));
    }
    private static GuideService imageService(Endpoint endpoint, PinStore store, Owner owner) {
        return new GuideService(UUID.randomUUID(), endpoint, offline(),
                (required, correlation) -> new ToolResult.Success<>(ToolInvocationContext.developmentConsole(correlation)),
                owner::execute, Clock.systemUTC(), dev.openallay.json.EngineJson.create(), null, null, store);
    }
    private static final class Owner {
        private final java.util.concurrent.ConcurrentLinkedQueue<Runnable> queued = new java.util.concurrent.ConcurrentLinkedQueue<>();
        private final java.util.concurrent.Semaphore available = new java.util.concurrent.Semaphore(0);
        void execute(Runnable action) { queued.add(action); available.release(); }
        void runAll() { while (available.tryAcquire()) queued.remove().run(); }
        void runNext() throws Exception {
            assertTrue(available.tryAcquire(2, java.util.concurrent.TimeUnit.SECONDS), "image completion handoff must arrive");
            queued.remove().run();
        }
    }
    private static final class PinStore implements dev.openallay.model.image.ImageAttachmentStore {
        volatile dev.openallay.model.image.ImageReference blockImage;
        volatile boolean failBlocked;
        volatile java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
        volatile java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        void gate(dev.openallay.model.image.ImageReference reference) {
            blockImage = reference; failBlocked = false;
            entered = new java.util.concurrent.CountDownLatch(1); release = new java.util.concurrent.CountDownLatch(1);
        }
        public dev.openallay.model.image.ImageInputLimits limits() { return dev.openallay.model.image.ImageInputLimits.defaults(); }
        public dev.openallay.model.image.ImageReference importImage(UUID actor, byte[] bytes) { throw new UnsupportedOperationException(); }
        public dev.openallay.model.image.ImageReference importImage(UUID actor, String owner, byte[] bytes) { throw new UnsupportedOperationException(); }
        public byte[] read(UUID actor, dev.openallay.model.image.ImageReference reference) { return new byte[]{1}; }
        public void retain(UUID actor, String owner, List<dev.openallay.model.image.ImageReference> refs) throws java.io.IOException {
            if (owner.contains(":receipt:") && refs.contains(blockImage)) {
                entered.countDown();
                try {
                    if (!release.await(2, java.util.concurrent.TimeUnit.SECONDS)) throw new java.io.IOException("fixture gate not released");
                } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new java.io.IOException(interrupted); }
                if (failBlocked) throw new java.io.IOException("injected retain failure");
            }
        }
        public void reconcile(UUID actor, String namespace, Map<String, List<dev.openallay.model.image.ImageReference>> owners) {}
        public void release(UUID actor, String owner) {}
        public int collect(UUID actor) { return 0; }
    }

    private static GuideService service(Endpoint endpoint) {
        return new GuideService(UUID.randomUUID(), endpoint, offline(),
                (required, correlation) -> new ToolResult.Success<>(ToolInvocationContext.developmentConsole(correlation)),
                Runnable::run, Clock.systemUTC(), dev.openallay.json.EngineJson.create());
    }

    private static void runOwner(ArrayDeque<Runnable> queue) {
        while (!queue.isEmpty()) queue.removeFirst().run();
    }

    private static GuideRemoteEndpoint offline() {
        return new GuideRemoteEndpoint() {
            public boolean serverModelAvailable() { return false; }
            public boolean serverToolsAvailable() { return false; }
            public boolean ask(UUID id, String session, String text, Consumer<AgentEvent> events) { return false; }
            public boolean cancel(UUID id) { return false; }
            public void disconnect() {}
        };
    }

    private static GuideSessionSnapshot session(GuideService service, String id) {
        return service.snapshot().sessions().stream().filter(session -> session.sessionId().equals(id)).findFirst().orElseThrow();
    }
    @SuppressWarnings("unchecked")
    private static <T> T success(ToolResult<T> result) {
        return ((ToolResult.Success<T>) assertInstanceOf(ToolResult.Success.class, result,
                () -> "Expected accepted request, got " + result)).value();
    }
    private record Call(String profile, String session, UUID id, String text) {}
    private static final class Endpoint implements GuideLocalEndpoint {
        final List<Call> calls = new ArrayList<>();
        final Map<UUID, Consumer<AgentEvent>> events = new HashMap<>();
        final Map<UUID, CompletableFuture<AgentResult>> futures = new HashMap<>();
        final List<ModelMessage> steers = new ArrayList<>();
        public Set<ContextCapability> requiredContext() { return Set.of(); }
        public List<GuideClientModelProfile> profiles() {
            return List.of(new GuideClientModelProfile("default", "Default", true, true, "default", null,
                    dev.openallay.model.image.ImageInputCapability.SUPPORTED, "test"),
                    new GuideClientModelProfile("next", "Next", true, true, "next", null,
                            dev.openallay.model.image.ImageInputCapability.SUPPORTED, "test"));
        }
        public CompletableFuture<AgentResult> ask(UUID actor, String session, UUID id, String text,
                ToolInvocationContext context, Consumer<AgentEvent> events) {
            return ask("default", actor, session, id, text, context, events);
        }
        public CompletableFuture<AgentResult> ask(String profile, UUID actor, String session, UUID id, String text,
                ToolInvocationContext context, Consumer<AgentEvent> consumer) {
            calls.add(new Call(profile, session, id, text));
            events.put(id, consumer);
            CompletableFuture<AgentResult> future = new CompletableFuture<>();
            futures.put(id, future);
            consumer.accept(new AgentEvent.StateChanged(AgentState.MODEL_WAIT));
            return future;
        }
        public ToolResult<Boolean> steer(UUID actor, String session, UUID id, UUID receipt, ModelMessage message) {
            steers.add(message);
            return new ToolResult.Success<>(true);
        }
        public boolean cancelSteer(UUID actor, String session, UUID id, UUID receipt) { return true; }
        public boolean cancel(UUID actor, String session) { return true; }
        public void clearSession(UUID actor, String session) {}
        public void clearActor(UUID actor) {}
        void emit(UUID id, AgentEvent event) { events.get(id).accept(event); }
        void terminal(UUID id, String text) { emit(id, new AgentEvent.FinalText(text)); }
        void release(UUID id, String text) {
            futures.get(id).complete(new AgentResult(AgentState.COMPLETED, text, null, null, null));
        }
    }
}
