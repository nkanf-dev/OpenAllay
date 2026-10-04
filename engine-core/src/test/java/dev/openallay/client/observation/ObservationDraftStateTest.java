package dev.openallay.client.observation;

import static org.junit.jupiter.api.Assertions.*;
import dev.openallay.client.gui.GuideClientUiState;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ObservationDraftStateTest {
    @Test void entryAnchorSurvivesViewsSettingsAndSessionRoundTripWithoutClipboardOrRecapture() {
        var f = new ObservationUiFixture(); var first = ObservationUiFixture.anchor(0, true, true);
        assertTrue(f.state.seedObservation("one", first));
        var full = f.state.attach(GuideClientUiState.Surface.FULLSCREEN, "one"); full.close();
        f.state.selectSession("two"); assertTrue(f.state.observation("two").isEmpty());
        f.state.selectSession("one");
        assertSame(first, f.state.observation("one").orElseThrow());
        assertFalse(f.state.seedObservation("one", ObservationUiFixture.anchor(1, false, false)));
        assertEquals(0, f.clipboardReads); assertEquals(0, f.releases);
        assertEquals(List.of(ObservationUiFixture.FRAME), f.retained.getLast());
    }

    @Test void removeIsExplicitAndImageRemovalKeepsNativeIdentitySourceTimeAndFocus() {
        var f = new ObservationUiFixture(); var first = ObservationUiFixture.anchor(0, true, true);
        f.state.seedObservation("one", first); f.state.removeObservationImage("one");
        var withoutImage = f.state.observation("one").orElseThrow();
        assertEquals(first.associationId(), withoutImage.associationId());
        assertEquals(first.capturedAt(), withoutImage.capturedAt());
        assertSame(first.focus(), withoutImage.focus()); assertTrue(withoutImage.image().isEmpty());
        f.state.removeObservation("one");
        assertFalse(f.state.seedObservation("one", ObservationUiFixture.anchor(2, true, false)));
        assertTrue(f.state.observation("one").isEmpty());
        assertTrue(f.state.replaceObservation(f.state.captureObservation("one"), ObservationUiFixture.anchor(3, false, false)));
    }

    @Test void oldAcceptanceAndLateFrameCannotClearOrRelabelRefreshedAnchor() {
        var f = new ObservationUiFixture(); f.state.seedObservation("one", ObservationUiFixture.anchor(0, true, true));
        var submitted = f.state.captureObservation("one"); var refresh = ObservationUiFixture.anchor(1, false, false);
        assertTrue(f.state.replaceObservation(submitted, refresh));
        assertFalse(f.state.acceptedObservation(submitted));
        assertFalse(f.state.replaceObservation(submitted, ObservationUiFixture.anchor(2, true, false)));
        assertSame(refresh, f.state.observation("one").orElseThrow());
        f.state.selectSession("two");
        assertTrue(f.state.acceptedObservation(f.state.captureObservation("one")));
        assertTrue(f.state.observation("one").isEmpty()); assertTrue(f.state.observation("two").isEmpty());
    }

    @Test void voiceAndInFlightLeasePinsCapturedFrameAlongsidePlayerPasteUntilExplicitCompletion() {
        var f = new ObservationUiFixture(); f.state.images().restore(List.of(ObservationUiFixture.PASTE));
        f.state.seedObservation("one", ObservationUiFixture.anchor(0, true, true));
        var capture = f.state.captureObservation("one"); var lease = f.state.leaseObservation(capture);
        f.state.removeObservation("one");
        assertEquals(List.of(ObservationUiFixture.PASTE, ObservationUiFixture.FRAME), f.retained.getLast());
        assertSame(capture, lease.capture());
        lease.close(); lease.close();
        assertEquals(List.of(ObservationUiFixture.PASTE), f.retained.getLast());
        f.state.close(); assertEquals(1, f.releases);
        assertFalse(f.state.acceptedObservation(capture));
        assertThrows(IllegalStateException.class, () -> f.state.leaseObservation(capture));
    }

    @Test void pendingVoiceRetainsItsFrozenAnchorAndExplicitRecoveryUsesThatSource() {
        var f = new ObservationUiFixture(); var first = ObservationUiFixture.anchor(0, true, true);
        f.state.seedObservation("one", first);
        var insertion = f.state.captureInsertion("one"); var observation = f.state.captureObservation("one");
        f.state.setText("one", "later typed draft");
        f.state.replaceObservation(observation, ObservationUiFixture.anchor(1, false, false));
        assertEquals(GuideClientUiState.InsertionResult.PENDING,
                f.state.insertTranscript(insertion, "spoken at the menu", observation));
        var pending = f.state.pendingInsertions("one").getFirst();
        assertSame(first, pending.observation().orElseThrow());
        assertEquals(List.of(ObservationUiFixture.FRAME), f.retained.getLast());
        assertTrue(f.state.applyPendingInsertion(pending.id()));
        assertSame(first, f.state.observation("one").orElseThrow());
        assertEquals("later typed draft\nspoken at the menu", f.state.readText("one"));
    }

    @Test void submissionRefsDoNotMutateComposerAndRejectAnotherConnectionCapture() {
        var f = new ObservationUiFixture(); f.state.images().restore(List.of(ObservationUiFixture.PASTE));
        f.state.seedObservation("one", ObservationUiFixture.anchor(0, true, true));
        assertEquals(List.of(ObservationUiFixture.PASTE, ObservationUiFixture.FRAME),
                f.state.inputImageReferences("one", f.state.captureObservation("one")));
        assertEquals(List.of(ObservationUiFixture.PASTE), f.state.images().references());
        var other = new ObservationUiFixture();
        assertEquals(List.of(), other.state.inputImageReferences("one", f.state.captureObservation("one")));
        assertFalse(other.state.replaceObservation(f.state.captureObservation("one"), ObservationUiFixture.anchor(2, false, false)));
    }
}
