package dev.openallay.client.observation;

import static org.junit.jupiter.api.Assertions.*;
import dev.openallay.world.ClientObservationAnchor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

final class ClientObservationInputCoordinatorTest {
    @Test void gameplayEntrySamplesFocusOnlyAndOpensAfterTheClientDispatch() {
        var f = new ObservationUiFixture(); var input = new Input(false); var jobs = new ArrayList<Runnable>();
        var coordinator = new ClientObservationInputCoordinator(input, jobs::add); var opened = new ArrayList<String>();
        var result = coordinator.beforeGuide(f.state, "one", () -> true, () -> opened.add("open"));
        assertEquals(1, input.focusCalls); assertEquals(0, input.frameCalls); assertTrue(opened.isEmpty());
        while (!jobs.isEmpty()) jobs.removeFirst().run();
        assertTrue(result.join()); assertEquals(List.of("open"), opened);
        assertSame(input.focus, f.state.observation("one").orElseThrow());
        assertTrue(f.state.observation("one").orElseThrow().image().isEmpty());
    }

    @Test void menuEntryWaitsForActualNativeFrameWithoutOpeningOrSwappingEarly() {
        var f = new ObservationUiFixture(); var input = new Input(true); var opened = new ArrayList<String>();
        var coordinator = new ClientObservationInputCoordinator(input, Runnable::run);
        var result = coordinator.beforeGuide(f.state, "one", () -> true, () -> opened.add("open"));
        assertFalse(result.isDone()); assertTrue(opened.isEmpty()); assertEquals(1, input.frameCalls);
        var actualFrame = ObservationUiFixture.anchor(1, true, true); input.frame.complete(actualFrame);
        assertTrue(result.join()); assertSame(actualFrame, f.state.observation("one").orElseThrow());
        assertEquals(List.of("open"), opened);
    }

    @Test void lateFrameRespectsRemovalAndConnectionFence() {
        var f = new ObservationUiFixture(); var input = new Input(false);
        var coordinator = new ClientObservationInputCoordinator(input, Runnable::run);
        f.state.seedObservation("one", input.focus);
        var attached = coordinator.attachCurrentFrame(f.state, "one"); f.state.removeObservation("one");
        input.frame.complete(ObservationUiFixture.anchor(1, true, false));
        assertFalse(attached.join()); assertTrue(f.state.observation("one").isEmpty());
        var next = new Input(true); var opened = new ArrayList<String>();
        var entry = new ClientObservationInputCoordinator(next, Runnable::run)
                .beforeGuide(f.state, "two", () -> false, () -> opened.add("wrong"));
        next.frame.complete(ObservationUiFixture.anchor(2, true, true));
        assertFalse(entry.join()); assertTrue(opened.isEmpty());
    }

    @Test void captureStaysWithOriginalSessionAndFailureKeepsDraftUnchanged() {
        var f = new ObservationUiFixture(); var input = new Input(false);
        var coordinator = new ClientObservationInputCoordinator(input, Runnable::run);
        f.state.seedObservation("one", input.focus); f.state.setText("one", "keep");
        var attached = coordinator.attachCurrentFrame(f.state, "one"); f.state.selectSession("two");
        var frame = ObservationUiFixture.anchor(2, true, false); input.frame.complete(frame);
        assertTrue(attached.join()); assertSame(frame, f.state.observation("one").orElseThrow());
        assertTrue(f.state.observation("two").isEmpty()); assertEquals("keep", f.state.readText("one"));
        var failed = new Input(false); var before = f.state.captureObservation("one");
        var failure = new ClientObservationInputCoordinator(failed, Runnable::run).attachCurrentFrame(f.state, "one");
        failed.frame.completeExceptionally(new IllegalStateException("no frame"));
        assertTrue(failure.isCompletedExceptionally()); assertEquals(before, f.state.captureObservation("one"));
    }

    @Test void explicitDraftRemovalDoesNotTriggerAnotherLightweightSeedCapture() {
        var f = new ObservationUiFixture(); var input = new Input(false);
        var coordinator = new ClientObservationInputCoordinator(input, Runnable::run);
        coordinator.seedFocus(f.state, "one"); f.state.removeObservation("one"); coordinator.seedFocus(f.state, "one");
        assertEquals(1, input.focusCalls); assertTrue(f.state.observation("one").isEmpty());
    }

    @Test void nativeProducerReleasesOnlyAfterActualDraftPinAcknowledgementAndBeforeOpening() {
        var acknowledgement = new CompletableFuture<dev.openallay.tool.ToolResult<Boolean>>();
        var state = delayedState(acknowledgement); var input = new Input(true); var order = new ArrayList<String>();
        input.order = order;
        var result = new ClientObservationInputCoordinator(input, Runnable::run)
                .beforeGuide(state, "one", () -> true, () -> order.add("open"));
        var anchor = ObservationUiFixture.anchor(1, true, true); input.frame.complete(anchor);
        assertFalse(result.isDone()); assertTrue(order.isEmpty()); assertFalse(state.observationImagesSettled().isDone());
        acknowledgement.complete(new dev.openallay.tool.ToolResult.Success<>(true));
        assertTrue(result.join()); assertEquals(List.of("release", "open"), order);
        assertSame(anchor, input.released.getFirst());
    }

    @Test void staleNativeCaptureReleasesImmediatelyWithoutDraftPinOrSourceRelabel() {
        var f = new ObservationUiFixture(); var input = new Input(false);
        var coordinator = new ClientObservationInputCoordinator(input, Runnable::run);
        f.state.seedObservation("one", input.focus);
        var result = coordinator.attachCurrentFrame(f.state, "one"); f.state.removeObservation("one");
        var anchor = ObservationUiFixture.anchor(2, true, false); input.frame.complete(anchor);
        assertFalse(result.join()); assertEquals(List.of(anchor), input.released);
        assertTrue(f.state.observation("one").isEmpty());
    }

    @Test void failedCustodyKeepsProducerUntilConnectionCleanupAndRemovesOnlyThatUnpinnedFrame() {
        var acknowledgement = new CompletableFuture<dev.openallay.tool.ToolResult<Boolean>>();
        var state = delayedState(acknowledgement); var input = new Input(true); var opened = new ArrayList<String>();
        var result = new ClientObservationInputCoordinator(input, Runnable::run)
                .beforeGuide(state, "one", () -> true, () -> opened.add("open"));
        var anchor = ObservationUiFixture.anchor(1, true, true); input.frame.complete(anchor);
        acknowledgement.complete(new dev.openallay.tool.ToolResult.Failure<>("pin_failed", "No draft custody"));
        assertTrue(result.isCompletedExceptionally()); assertTrue(input.released.isEmpty()); assertTrue(opened.isEmpty());
        var retainedFocus = state.observation("one").orElseThrow();
        assertSame(anchor.focus(), retainedFocus.focus());
        assertEquals(anchor.associationId(), retainedFocus.associationId());
        assertEquals(anchor.capturedAt(), retainedFocus.capturedAt());
        assertTrue(retainedFocus.image().isEmpty());
    }

    private static dev.openallay.client.gui.GuideClientUiState delayedState(
            CompletableFuture<dev.openallay.tool.ToolResult<Boolean>> acknowledgement) {
        return new dev.openallay.client.gui.GuideClientUiState("one",
                dev.openallay.client.gui.clipboard.ImageClipboard.Read::empty, Runnable::run, Runnable::run,
                bytes -> CompletableFuture.completedFuture(new dev.openallay.tool.ToolResult.Success<>(ObservationUiFixture.PASTE)),
                ignored -> {}, (owner, refs) -> acknowledgement, ignored -> {});
    }

    private static final class Input implements GuideObservationInputActions {
        final ClientObservationAnchor focus;
        final CompletableFuture<ClientObservationAnchor> frame = new CompletableFuture<>();
        int focusCalls; int frameCalls;
        final List<ClientObservationAnchor> released = new ArrayList<>();
        List<String> order = new ArrayList<>();
        Input(boolean menu) { focus = ObservationUiFixture.anchor(0, false, menu); }
        @Override public ClientObservationAnchor captureFocus() { focusCalls++; return focus; }
        @Override public CompletableFuture<ClientObservationAnchor> captureCurrentFrame() { frameCalls++; return frame; }
        @Override public CompletableFuture<Void> releaseCapture(ClientObservationAnchor anchor) {
            released.add(anchor); order.add("release"); return CompletableFuture.completedFuture(null);
        }
    }
}
