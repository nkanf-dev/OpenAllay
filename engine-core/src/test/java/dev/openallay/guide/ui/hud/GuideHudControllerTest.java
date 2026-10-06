package dev.openallay.guide.ui.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openallay.agent.AgentEvent;
import dev.openallay.agent.AgentResult;
import dev.openallay.client.ClientEventDispatcher;
import dev.openallay.context.ContextCapability;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.guide.GuideLocalEndpoint;
import dev.openallay.guide.GuideRemoteEndpoint;
import dev.openallay.guide.GuideService;
import dev.openallay.guide.GuideServiceManager;
import dev.openallay.guide.GuideSnapshot;
import dev.openallay.guide.ui.GuideDisplayConfig;
import dev.openallay.guide.ui.GuideUiConfig;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

final class GuideHudControllerTest {
    private static final UUID ACTOR = UUID.fromString("30ab22ed-23fb-46f2-82ca-d4a656698eec");
    private static final GuideDisplayConfig ENABLED = GuideDisplayConfig.defaults()
            .withUi(GuideUiConfig.defaults().withHud(GuideUiConfig.Hud.defaults().withEnabled(true)));

    @Test
    void disabledAndEnabledIdleHudNeverCreatesAServiceOrAnyRequest() {
        Fixture fixture = new Fixture();
        GuideDisplayConfig[] config = {GuideDisplayConfig.defaults()};
        try (GuideHudController controller = new GuideHudController(fixture.manager, () -> config[0])) {
            controller.tick();
            controller.tick();
            assertNull(fixture.manager.current());
            assertFalse(controller.view().hasContent());
            assertFalse(controller.view().hud().enabled());
            assertTrue(fixture.dispatcher.queued.isEmpty());

            config[0] = ENABLED;
            controller.tick();
            assertNull(fixture.manager.current());
            assertTrue(controller.view().hud().enabled());
            assertFalse(controller.view().hasContent());
            assertTrue(fixture.dispatcher.queued.isEmpty());
        }
        fixture.assertPassive();
    }

    @Test
    void disabledHudDoesNotSubscribeToAnExistingService() throws Exception {
        Fixture fixture = new Fixture();
        GuideService service = fixture.manager.forActor(ACTOR);
        try (GuideHudController controller = new GuideHudController(fixture.manager,
                GuideDisplayConfig::defaults)) {
            controller.tick();
            assertEquals(0, listenerCount(service));
            assertTrue(fixture.dispatcher.queued.isEmpty());
            assertFalse(controller.view().hasContent());
        }
        assertTrue(service.snapshot().sessions().getFirst().requests().isEmpty());
        fixture.assertPassive();
    }

    @Test
    void burstOffersPublishOnlyNewestSnapshotOnceAtTheNextTick() throws Exception {
        Fixture fixture = new Fixture();
        GuideService service = fixture.manager.forActor(ACTOR);
        try (GuideHudController controller = new GuideHudController(fixture.manager, () -> ENABLED)) {
            controller.tick();
            assertEquals("main", controller.view().selectedSession());
            fixture.dispatcher.runAll();
            assertEquals(1, listenerCount(service));
            controller.tick();
            GuideHudView before = controller.view();

            service.selectSession("one");
            service.selectSession("two");
            service.selectSession("three");
            fixture.dispatcher.runAll();
            assertSame(before, controller.view(), "event callbacks must not project a render view");
            controller.tick();
            assertEquals("three", controller.view().selectedSession());
            GuideHudView published = controller.view();
            controller.tick();
            assertSame(published, controller.view(), "an unchanged tick must not redo projection");
            assertTrue(service.snapshot().sessions().stream().allMatch(session -> session.requests().isEmpty()));
        }
        assertEquals(0, listenerCount(service));
        fixture.assertPassive();
    }

    @Test
    void liveConfigChangeUpdatesImmutableViewWithoutCreatingRequests() {
        Fixture fixture = new Fixture();
        fixture.manager.forActor(ACTOR);
        GuideDisplayConfig[] config = {ENABLED};
        try (GuideHudController controller = new GuideHudController(fixture.manager, () -> config[0])) {
            controller.tick();
            fixture.dispatcher.runAll();
            controller.tick();
            config[0] = ENABLED.withAssistantName("New Allay").withUi(ENABLED.ui().withHud(
                    ENABLED.ui().hud().withBackgroundOpacity(0).withCollapsed(true)));
            controller.tick();
            assertEquals("New Allay", controller.view().assistantName());
            assertEquals(0, controller.view().hud().backgroundOpacity());
            assertTrue(controller.view().hud().collapsed());
            assertEquals("main", controller.view().selectedSession());
        }
        fixture.assertPassive();
    }

    @Test
    void disablingClearsViewAndFenceRemovesRegistrationThatArrivesAfterClose() throws Exception {
        Fixture fixture = new Fixture();
        GuideService service = fixture.manager.forActor(ACTOR);
        GuideDisplayConfig[] config = {ENABLED};
        try (GuideHudController controller = new GuideHudController(fixture.manager, () -> config[0])) {
            controller.tick();
            assertEquals(1, fixture.dispatcher.queued.size(), "subscription registration is deferred");
            config[0] = GuideDisplayConfig.defaults();
            controller.tick();
            assertEquals("", controller.view().selectedSession());
            assertFalse(controller.view().hasContent());
            fixture.dispatcher.runAll();
            assertEquals(0, listenerCount(service), "late registration must not leak a HUD listener");

            config[0] = ENABLED;
            controller.tick();
            fixture.dispatcher.runAll();
            assertEquals(1, listenerCount(service));
            config[0] = GuideDisplayConfig.defaults();
            controller.tick();
            assertEquals(0, listenerCount(service));
        }
        fixture.assertPassive();
    }

    @Test
    void serviceReplacementDetachesOldConnectionAndIgnoresItsPendingOffers() throws Exception {
        Fixture fixture = new Fixture();
        GuideService old = fixture.manager.forActor(ACTOR);
        try (GuideHudController controller = new GuideHudController(fixture.manager, () -> ENABLED)) {
            controller.tick();
            fixture.dispatcher.runAll();
            old.selectSession("old-session");
            fixture.dispatcher.runAll();

            GuideService replacement = fixture.manager.forActor(UUID.randomUUID());
            controller.tick();
            assertEquals("main", controller.view().selectedSession());
            fixture.dispatcher.runUntil(() -> listenerCount(replacement) == 1);
            assertEquals(0, listenerCount(old));
            assertEquals(1, listenerCount(replacement));
            controller.tick();
            assertEquals("main", controller.view().selectedSession());
        }
        fixture.assertPassive();
    }

    @Test
    void disconnectAndCloseClearOnlyHudAndCloseNeverResubscribes() throws Exception {
        Fixture fixture = new Fixture();
        GuideService service = fixture.manager.forActor(ACTOR);
        GuideHudController controller = new GuideHudController(fixture.manager, () -> ENABLED);
        controller.tick();
        fixture.dispatcher.runAll();
        controller.disconnect();
        assertEquals(0, listenerCount(service));
        assertSame(service, fixture.manager.current(), "HUD disconnect must not disconnect shared service");
        assertFalse(controller.view().hasContent());
        assertEquals("", controller.view().selectedSession());
        controller.tick();
        fixture.dispatcher.runAll();
        assertEquals(1, listenerCount(service));
        controller.close();
        controller.close();
        service.selectSession("after-close");
        fixture.dispatcher.runAll();
        controller.tick();
        assertEquals(0, listenerCount(service));
        assertEquals("", controller.view().selectedSession());
        fixture.assertPassive();
    }

    /** The service deliberately has no public listener inspection API. */
    private static int listenerCount(GuideService service) throws ReflectiveOperationException {
        var field = GuideService.class.getDeclaredField("listeners");
        field.setAccessible(true);
        return ((Collection<?>) field.get(service)).size();
    }

    private static final class Fixture {
        final QueuedDispatcher dispatcher = new QueuedDispatcher();
        final PassiveLocal local = new PassiveLocal();
        final PassiveRemote remote = new PassiveRemote();
        int captures;
        final GuideServiceManager manager = new GuideServiceManager(local, remote,
                (capabilities, correlation) -> {
                    captures++;
                    throw new AssertionError("HUD must not capture Game state");
                }, dispatcher, Clock.systemUTC(), dev.openallay.json.EngineJson.create());

        void assertPassive() {
            assertEquals(0, captures);
            assertEquals(0, local.askCalls);
            assertEquals(0, local.contextEstimateCalls);
            assertEquals(0, remote.askCalls);
        }
    }

    @FunctionalInterface
    private interface OwnerCondition { boolean satisfied() throws Exception; }

    private static final class QueuedDispatcher implements ClientEventDispatcher {
        final java.util.concurrent.LinkedBlockingQueue<Runnable> queued =
                new java.util.concurrent.LinkedBlockingQueue<>();
        @Override public void execute(Runnable event) { queued.add(event); }
        void runAll() {
            Runnable event;
            while ((event = queued.poll()) != null) event.run();
        }
        void runUntil(OwnerCondition condition) throws Exception {
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
            while (!condition.satisfied()) {
                long remaining = deadline - System.nanoTime();
                assertTrue(remaining > 0, "Real previous-connection cleanup and replacement subscription must settle");
                Runnable event = queued.poll(remaining, java.util.concurrent.TimeUnit.NANOSECONDS);
                org.junit.jupiter.api.Assertions.assertNotNull(event,
                        "No owner callback arrived before the real cleanup deadline");
                event.run();
            }
            runAll();
        }
    }

    private static final class PassiveLocal implements GuideLocalEndpoint {
        int askCalls;
        int contextEstimateCalls;
        @Override public Set<ContextCapability> requiredContext() { return Set.of(); }
        @Override public CompletableFuture<AgentResult> ask(UUID actor, String session, UUID request,
                String question, ToolInvocationContext context, Consumer<AgentEvent> events) {
            askCalls++;
            throw new AssertionError("HUD must not send requests");
        }
        @Override public java.util.Optional<dev.openallay.guide.GuideContextEstimate> contextEstimate(
                String profile, UUID actor, String session) {
            contextEstimateCalls++;
            throw new AssertionError("HUD must not estimate tokens");
        }
        @Override public boolean cancel(UUID actor, String session) { return false; }
        @Override public void clearSession(UUID actor, String session) {}
        @Override public void clearActor(UUID actor) {}
    }

    private static final class PassiveRemote implements GuideRemoteEndpoint {
        int askCalls;
        @Override public boolean serverModelAvailable() { return false; }
        @Override public boolean serverToolsAvailable() { return false; }
        @Override public boolean ask(UUID request, String session, String question,
                Consumer<AgentEvent> events) {
            askCalls++;
            throw new AssertionError("HUD must not send requests");
        }
        @Override public boolean cancel(UUID request) { return false; }
        @Override public void disconnect() {}
    }
}
