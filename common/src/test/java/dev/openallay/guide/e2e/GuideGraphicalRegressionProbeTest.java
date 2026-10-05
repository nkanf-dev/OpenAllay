package dev.openallay.guide.e2e;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Fixture protocol tests only. Real Screen callbacks/extractions still require packaged native CI. */
final class GuideGraphicalRegressionProbeTest {
    @Test
    void delayedProducerMustInstallAndInitializeTheSameOwnedScreenBeforeOneCallback() {
        Object reader = new Object();
        Object guide = new Object();
        Object settings = new Object();
        AtomicReference<Object> current = new AtomicReference<>(reader);
        AtomicInteger opens = new AtomicInteger();
        AtomicInteger settingsCallbacks = new AtomicInteger();
        ArrayDeque<Runnable> clientTurns = new ArrayDeque<>();
        List<String> order = new ArrayList<>();
        AtomicReference<Boolean> extracted = new AtomicReference<>(false);
        var transition = new GuideGraphicalRegressionProbe.NativeScreenTransition<>(reader);

        transition.request(current::get, () -> {
            order.add("reader-removed"); current.set(null);
        }, () -> {
            opens.incrementAndGet();
            // Model the real beforeGuide producer: capture -> custody -> release -> install.
            clientTurns.addLast(() -> {
                order.add("captured");
                clientTurns.addLast(() -> {
                    order.add("custody-acknowledged");
                    clientTurns.addLast(() -> {
                        order.add("producer-released"); current.set(guide);
                        clientTurns.addLast(() -> { order.add("native-extracted"); extracted.set(true); });
                    });
                });
            });
        });
        assertNull(current.get());
        assertFalse(transition.ready(current.get(), screen -> screen == guide, screen -> extracted.get()));
        assertEquals(1, opens.get());
        assertEquals(0, settingsCallbacks.get());

        clientTurns.removeFirst().run();
        assertFalse(transition.ready(current.get(), screen -> screen == guide, screen -> extracted.get()));
        clientTurns.removeFirst().run();
        assertFalse(transition.ready(current.get(), screen -> screen == guide, screen -> extracted.get()));
        clientTurns.removeFirst().run();
        assertSame(guide, current.get());
        assertFalse(transition.ready(current.get(), screen -> screen == guide, screen -> extracted.get()));
        assertThrows(IllegalStateException.class, () -> transition.consume(current::get,
                screen -> fail("must not click uninitialized Guide")));
        clientTurns.removeFirst().run();
        assertTrue(transition.ready(current.get(), screen -> screen == guide, screen -> extracted.get()));

        transition.consume(current::get, screen -> {
            assertSame(guide, screen); settingsCallbacks.incrementAndGet(); current.set(settings);
        });
        assertSame(settings, current.get());
        assertEquals(List.of("reader-removed", "captured", "custody-acknowledged", "producer-released", "native-extracted"), order);
        assertEquals(1, opens.get());
        assertEquals(1, settingsCallbacks.get());
        assertThrows(IllegalStateException.class, () -> transition.consume(current::get, screen -> settingsCallbacks.incrementAndGet()));
        assertEquals(1, settingsCallbacks.get());
    }

    @Test
    void acceptedReaderObservationReopenWaitsForTheActualProductProducerAndRelease() {
        String session = "session";
        Object reader = new Object(); Object guide = new Object();
        AtomicReference<Object> current = new AtomicReference<>(reader);
        ArrayDeque<Runnable> clientTurns = new ArrayDeque<>();
        var state = new dev.openallay.client.gui.GuideClientUiState(session,
                dev.openallay.client.gui.clipboard.ImageClipboard.Read::empty, Runnable::run, clientTurns::addLast,
                bytes -> java.util.concurrent.CompletableFuture.completedFuture(
                        new dev.openallay.tool.ToolResult.Failure<>("unused_import", "No frame import in this fixture")),
                ignored -> {}, (owner, refs) -> java.util.concurrent.CompletableFuture.completedFuture(
                        new dev.openallay.tool.ToolResult.Success<>(true)), ignored -> {});
        var focus = gameplayFocus();
        assertTrue(state.seedObservation(session, dev.openallay.world.ClientObservationAnchor.focus(focus)));
        assertTrue(state.acceptedObservation(state.captureObservation(session)));
        assertFalse(state.observationInitialized(session)); // Real successful reader Send takes this branch.
        var attachment = state.attach(dev.openallay.client.gui.GuideClientUiState.Surface.HUD_INPUT, session);
        var release = new java.util.concurrent.CompletableFuture<Void>();
        AtomicInteger captures = new AtomicInteger(); AtomicInteger opens = new AtomicInteger();
        var input = new dev.openallay.client.observation.GuideObservationInputActions() {
            @Override public dev.openallay.world.ClientObservationAnchor captureFocus() {
                captures.incrementAndGet(); return dev.openallay.world.ClientObservationAnchor.focus(focus);
            }
            @Override public java.util.concurrent.CompletableFuture<dev.openallay.world.ClientObservationAnchor> captureCurrentFrame() {
                throw new AssertionError("Gameplay reopen must not capture native menu pixels");
            }
            @Override public java.util.concurrent.CompletableFuture<Void> releaseCapture(
                    dev.openallay.world.ClientObservationAnchor anchor) { return release; }
        };
        var producer = new dev.openallay.client.observation.ClientObservationInputCoordinator(input, clientTurns::addLast);
        var transition = new GuideGraphicalRegressionProbe.NativeScreenTransition<>(reader);
        transition.request(current::get, () -> { attachment.close(); current.set(null); }, () ->
                producer.beforeGuide(state, session, () -> current.get() == null && !state.closed(),
                        () -> { opens.incrementAndGet(); current.set(guide); }));
        assertFalse(state.visible(dev.openallay.client.gui.GuideClientUiState.Surface.HUD_INPUT, session));
        assertFalse(transition.ready(current.get(), screen -> screen == guide, screen -> true));
        assertEquals(1, captures.get()); assertEquals(0, opens.get());
        clientTurns.removeFirst().run(); // Product complete: seed the observation on its owner dispatcher.
        assertTrue(state.observationInitialized(session));
        assertFalse(transition.ready(current.get(), screen -> screen == guide, screen -> true));
        clientTurns.removeFirst().run(); // Product custody continuation: release the temporary capture.
        assertFalse(transition.ready(current.get(), screen -> screen == guide, screen -> true));
        assertEquals(0, opens.get());
        release.complete(null);
        assertFalse(transition.ready(current.get(), screen -> screen == guide, screen -> true));
        clientTurns.removeFirst().run(); // Only the product release continuation installs the Guide owner.
        assertTrue(transition.ready(current.get(), screen -> screen == guide,
                screen -> state.observationInitialized(session) && state.observationImagesSettled().isDone()));
        transition.consume(current::get, screen -> assertSame(guide, screen));
        assertEquals(1, captures.get()); assertEquals(1, opens.get());
        state.close();
    }

    private static dev.openallay.world.WorldFocusObservation gameplayFocus() {
        var time = java.time.Instant.parse("2026-10-04T00:00:00Z");
        var actor = java.util.UUID.fromString("00000000-0000-0000-0000-000000000001");
        var evidence = new dev.openallay.context.EvidenceMetadata(dev.openallay.context.DataAuthority.CLIENT_VISIBLE,
                dev.openallay.context.DataCompleteness.PARTIAL, time, "minecraft:client_focus",
                "test:detached_focus", "test-game", "test-platform", java.util.Map.of());
        var empty = new dev.openallay.world.WorldFocusObservation.Item("minecraft:air", 0, "", 0, 0,
                new com.google.gson.JsonObject(), true, "");
        return new dev.openallay.world.WorldFocusObservation(time, actor, "minecraft:overworld",
                new dev.openallay.world.WorldFocusObservation.Camera(0, 64, 0, 0, 0, 70, "first_person", true, false, actor),
                new dev.openallay.world.WorldFocusObservation.Target("none", null, null, null), empty, empty,
                new dev.openallay.world.WorldFocusObservation.Screen("", "", 300, 200, false, false, "gameplay"),
                new dev.openallay.world.WorldFocusObservation.Menu("native.Menu", 1, 0, "minecraft:generic_9x3",
                        true, false, true, 63, empty, ""),
                new dev.openallay.world.WorldFocusObservation.Hover(0, 0, true, "none", -1, -1, null, ""), evidence);
    }

    @Test
    void aNonNullWrongServiceStateOrSessionOwnerMustFailRatherThanBeAcceptedOrClicked() {
        Object reader = new Object(); Object unrelatedGuide = new Object();
        AtomicReference<Object> current = new AtomicReference<>(reader);
        var transition = new GuideGraphicalRegressionProbe.NativeScreenTransition<>(reader);
        transition.request(current::get, () -> current.set(null), () -> current.set(unrelatedGuide));
        assertThrows(IllegalStateException.class, () -> transition.ready(current.get(), screen -> false, screen -> true));
    }

    @Test
    void installedScreenIdentityCannotBeReplacedEvenByAnotherOtherwiseValidGuide() {
        Object reader = new Object(); Object guide = new Object(); Object replacement = new Object();
        AtomicReference<Object> current = new AtomicReference<>(reader);
        var transition = new GuideGraphicalRegressionProbe.NativeScreenTransition<>(reader);
        transition.request(current::get, () -> current.set(null), () -> current.set(guide));
        assertFalse(transition.ready(current.get(), screen -> true, screen -> false));
        current.set(replacement);
        assertThrows(IllegalStateException.class, () -> transition.ready(current.get(), screen -> true, screen -> true));
        assertThrows(IllegalStateException.class, () -> transition.consume(current::get, screen -> fail("must not click replacement")));
    }

    @Test
    void replacedSourceRefusedCloseOrSecondRequestNeverIssuesAnOpenRetry() {
        Object reader = new Object(); AtomicReference<Object> current = new AtomicReference<>(reader);
        AtomicInteger opens = new AtomicInteger();
        var refused = new GuideGraphicalRegressionProbe.NativeScreenTransition<>(reader);
        assertThrows(IllegalStateException.class, () -> refused.request(current::get, () -> {}, opens::incrementAndGet));
        assertEquals(0, opens.get());
        assertThrows(IllegalStateException.class, () -> refused.request(current::get, () -> current.set(null), opens::incrementAndGet));
        assertEquals(0, opens.get());
        var stale = new GuideGraphicalRegressionProbe.NativeScreenTransition<>(reader);
        current.set(new Object());
        assertThrows(IllegalStateException.class, () -> stale.request(current::get, () -> current.set(null), opens::incrementAndGet));
        assertEquals(0, opens.get());
    }

    @Test
    void aDestructiveOrThrowingCallbackIsConsumedOnceWithoutReadingItsDestroyedOwner() {
        Object reader = new Object(); Object guide = new Object();
        AtomicReference<Object> current = new AtomicReference<>(reader);
        AtomicInteger callbacks = new AtomicInteger();
        var transition = new GuideGraphicalRegressionProbe.NativeScreenTransition<>(reader);
        transition.request(current::get, () -> current.set(null), () -> current.set(guide));
        assertTrue(transition.ready(current.get(), screen -> screen == guide, screen -> true));
        assertThrows(IllegalStateException.class, () -> transition.consume(current::get, screen -> {
            callbacks.incrementAndGet(); current.set(null); throw new IllegalStateException("native callback failure");
        }));
        assertNull(current.get());
        assertThrows(IllegalStateException.class, () -> transition.consume(current::get, screen -> callbacks.incrementAndGet()));
        assertEquals(1, callbacks.get());
    }
}
