package dev.openallay.client.gui;

import static org.junit.jupiter.api.Assertions.*;
import dev.openallay.client.gui.clipboard.ImageClipboard;
import dev.openallay.model.image.ImageReference;
import dev.openallay.tool.ToolResult;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;

final class GuideClientUiStateTest {
    private static final ImageReference IMAGE = new ImageReference("a".repeat(64), "image/png", 3, 2, 70);

    @Test void closeReopenAndSettingsNavigationDetachOnlyViewsNotDraftsOrLeases() {
        var f = new Fixture();
        var fullscreen = f.state.attach(GuideClientUiState.Surface.FULLSCREEN, "one");
        f.state.setText("one", "unfinished task");
        f.readyImage();
        assertTrue(f.state.visible(GuideClientUiState.Surface.FULLSCREEN, "one"));
        fullscreen.close();
        assertFalse(f.state.visible(GuideClientUiState.Surface.FULLSCREEN, "one"));
        assertEquals(0, f.releases);
        var hud = f.state.attach(GuideClientUiState.Surface.HUD_INPUT, "one");
        assertEquals("unfinished task", f.state.readText("one"));
        assertEquals(List.of(IMAGE), f.state.images().references());
        hud.close();
        var reopened = f.state.attach(GuideClientUiState.Surface.FULLSCREEN, "one");
        assertEquals("unfinished task", f.state.readText("one"));
        assertEquals(List.of(IMAGE), f.state.images().references());
        assertEquals(1, f.readsOnAttach, "only the explicit paste reads clipboard");
        reopened.close();
        f.state.close();
        assertEquals(1, f.releases);
    }

    @Test void unavailableClipboardOrFailedImportPreservesExistingImagesAndText() {
        var f = new Fixture();
        f.readyImage();
        f.state.setText("one", "keep my task");
        f.clipboardUnavailable = true;
        f.state.images().paste(); f.work.runAll(); f.client.runAll();
        assertEquals("keep my task", f.state.readText("one"));
        assertEquals(List.of(IMAGE), f.state.images().references());
        assertEquals(ComposerImageDraft.Notice.CLIPBOARD_UNAVAILABLE, f.state.imageNotice());
        f.clipboardUnavailable = false;
        f.state.images().paste(); f.work.runAll(); f.client.runAll();
        f.imports.getLast().complete(new ToolResult.Failure<>("copy_failed", "fake import failure"));
        f.client.runAll();
        assertEquals("keep my task", f.state.readText("one"));
        assertEquals(List.of(IMAGE), f.state.images().references());
        assertFalse(f.state.images().pending());
        assertEquals(ComposerImageDraft.Notice.IMPORT_FAILED, f.state.imageNotice());
    }

    @Test void sessionAndForkDraftsAreDistinctButReturningRestoresTheOriginal() {
        var f = new Fixture();
        f.state.setText("one", "source draft");
        f.readyImage();
        f.state.selectSession("fork");
        assertEquals("", f.state.readText("fork"));
        assertTrue(f.state.images().empty());
        f.state.setText("fork", "fork draft");
        f.state.selectSession("one");
        assertEquals("source draft", f.state.readText("one"));
        assertEquals(List.of(IMAGE), f.state.images().references());
        assertEquals("fork draft", f.state.readText("fork"));
    }

    @Test void latePasteAfterViewClosedAndSessionChangedReturnsToCapturedSessionOnly() {
        var f = new Fixture();
        var view = f.state.attach(GuideClientUiState.Surface.FULLSCREEN, "one");
        f.state.images().paste();
        view.close();
        f.state.selectSession("two");
        f.work.runAll(); f.client.runAll();
        assertEquals(1, f.imports.size());
        f.imports.getFirst().complete(new ToolResult.Success<>(IMAGE)); f.client.runAll();
        assertTrue(f.state.images().empty());
        f.state.selectSession("one");
        assertEquals(List.of(IMAGE), f.state.images().references());
        assertTrue(f.discarded.isEmpty());
    }

    @Test void disconnectFencesLateImportAndVoiceIntoAnotherConnection() {
        var old = new Fixture();
        var voice = old.state.captureInsertion("one");
        old.state.images().paste(); old.work.runAll(); old.client.runAll();
        old.state.close();
        old.imports.getFirst().complete(new ToolResult.Success<>(IMAGE)); old.client.runAll();
        assertEquals(List.of(IMAGE), old.discarded);
        assertEquals(GuideClientUiState.InsertionResult.REJECTED, old.state.insertTranscript(voice, "late"));
        var next = new Fixture();
        assertEquals(GuideClientUiState.InsertionResult.REJECTED, next.state.insertTranscript(voice, "wrong world"));
        assertEquals("", next.state.readText("one"));
        assertTrue(next.state.images().empty());
    }

    @Test void voiceAppendsCapturedDraftAndEditedDraftBecomesExplicitPendingResult() {
        var f = new Fixture();
        f.state.setText("one", "existing");
        var first = f.state.captureInsertion("one");
        assertEquals(GuideClientUiState.InsertionResult.INSERTED, f.state.insertTranscript(first, "spoken"));
        assertEquals("existing\nspoken", f.state.readText("one"));
        var edited = f.state.captureInsertion("one");
        f.state.setText("one", "new edit");
        f.state.selectSession("two");
        assertEquals(GuideClientUiState.InsertionResult.PENDING, f.state.insertTranscript(edited, "late spoken"));
        assertEquals("new edit", f.state.readText("one"));
        assertEquals("", f.state.readText("two"));
        var pending = f.state.pendingInsertions("one").getFirst();
        assertTrue(f.state.applyPendingInsertion(pending.id()));
        assertEquals("new edit\nlate spoken", f.state.readText("one"));
        assertFalse(f.state.applyPendingInsertion(pending.id()));
    }

    @Test void refusedVoiceSendIsPendingWithoutChangingTextImagesRevisionOrEditIntent() {
        var f = new Fixture(); f.state.setText("one", "typed replacement"); f.readyImage();
        UUID edit = UUID.randomUUID();
        f.state.beginPendingEdit("one", edit, GuideClientUiState.DraftMode.STEER);
        var capture = f.state.captureInsertion("one");
        var intent = f.state.intent("one");
        long revision = f.state.revision("one"); int leases = f.retains.size();
        f.state.selectSession("two");
        assertTrue(f.state.retainPendingTranscript(capture, "spoken request not sent"));
        assertEquals("typed replacement", f.state.readText("one")); assertEquals(revision, f.state.revision("one"));
        assertEquals(intent, f.state.intent("one")); assertEquals(leases, f.retains.size());
        assertEquals("", f.state.readText("two")); assertTrue(f.state.pendingInsertions("two").isEmpty());
        var pending = f.state.pendingInsertions("one").getFirst();
        assertEquals("one", pending.session()); assertEquals("spoken request not sent", pending.text());
        f.state.selectSession("one"); assertEquals(List.of(IMAGE), f.state.images().references());
        assertTrue(f.state.applyPendingInsertion(pending.id()));
        assertEquals("typed replacement\nspoken request not sent", f.state.readText("one"));
        assertEquals(intent, f.state.intent("one")); assertEquals(List.of(IMAGE), f.state.images().references());
    }

    @Test void refusedVoiceRetentionRequiresLiveCapturedOwnerGenerationAndExistingSession() {
        var f = new Fixture(); var capture = f.state.captureInsertion("one");
        assertFalse(f.state.retainPendingTranscript(null, "spoken"));
        assertFalse(f.state.retainPendingTranscript(capture, "  "));
        assertFalse(f.state.retainPendingTranscript(capture, null));
        assertFalse(f.state.retainPendingTranscript(new GuideClientUiState.Insertion(
                "other owner", capture.generation(), "one", capture.revision()), "spoken"));
        assertFalse(f.state.retainPendingTranscript(new GuideClientUiState.Insertion(
                capture.ownerId(), capture.generation() + 1, "one", capture.revision()), "spoken"));
        assertFalse(f.state.retainPendingTranscript(new GuideClientUiState.Insertion(
                capture.ownerId(), capture.generation(), "never-created", capture.revision()), "spoken"));
        assertTrue(f.state.pendingInsertions("one").isEmpty());
        f.state.close(); assertFalse(f.state.retainPendingTranscript(capture, "late"));
        var next = new Fixture(); assertFalse(next.state.retainPendingTranscript(capture, "wrong connection"));
        assertTrue(next.state.pendingInsertions("one").isEmpty());
    }

    @Test void acceptedOldSubmissionClearsOnlyCapturedTextAndImagesEvenAfterSwitch() {
        var f = new Fixture();
        f.readyImage(); f.state.setText("one", "send me");
        var text = f.state.captureInsertion("one");
        var images = f.state.images().captureSubmission();
        f.state.selectSession("two"); f.state.setText("two", "keep new session");
        assertTrue(f.state.clearAcceptedText(text, "send me"));
        assertTrue(f.state.images().accepted(images));
        assertEquals("keep new session", f.state.readText("two"));
        f.state.selectSession("one");
        assertEquals("", f.state.readText("one"));
        assertTrue(f.state.images().empty());
        f.state.setText("one", "send second");
        var newer = f.state.captureInsertion("one");
        f.state.setText("one", "later typing");
        assertFalse(f.state.clearAcceptedText(newer, "send second"));
        assertEquals("later typing", f.state.readText("one"));
    }

    @Test void retainLeaseWritesAreSerializedAndFailureDoesNotRemoveStrongDraftReferences() {
        var f = new Fixture();
        f.delayedLease = new CompletableFuture<>();
        f.readyImage();
        assertEquals(List.of(IMAGE), f.state.images().references());
        f.state.setText("one", "keep");
        assertFalse(GuideClientUiState.submissionAccepted(true, new ToolResult.Success<>(false)));
        assertEquals(List.of(IMAGE), f.state.images().references());
        int before = f.retains.size();
        f.state.images().clear();
        assertEquals(before, f.retains.size(), "later retains must wait for the previous completion");
        f.delayedLease.complete(new ToolResult.Failure<>("copy_failed", "fake pin failure"));
        assertEquals(List.of(), f.retains.getLast());
        f.state.close();
        assertEquals(1, f.releases);
    }

    @Test void pendingEditIntentSurvivesCloseSettingsAndSessionRoundTrip() {
        var f = new Fixture();
        var oldView = f.state.attach(GuideClientUiState.Surface.FULLSCREEN, "one");
        UUID target = UUID.randomUUID();
        f.state.beginPendingEdit("one", target, GuideClientUiState.DraftMode.STEER);
        f.state.setText("one", "edited queued instruction");
        f.readyImage();
        oldView.close();
        f.state.selectSession("two");
        assertEquals(GuideClientUiState.DraftIntent.defaults(), f.state.intent("two"));
        var reopened = f.state.attach(GuideClientUiState.Surface.FULLSCREEN, "one");
        f.state.selectSession("one");
        assertEquals(target, f.state.intent("one").pendingId());
        assertTrue(f.state.intent("one").steer());
        assertFalse(f.state.intent("one").editInvalid());
        assertEquals("edited queued instruction", f.state.readText("one"));
        assertEquals(List.of(IMAGE), f.state.images().references());
        assertEquals(GuideClientUiState.SubmissionRoute.EDIT_PENDING,
                GuideClientUiState.submissionRoute(f.state.intent("one"), false));
        reopened.close();
        assertEquals(target, f.state.intent("one").pendingId());
    }

    @Test void editAlreadyConsumedKeepsDraftAndRequiresExplicitUseAsNew() {
        var f = new Fixture();
        UUID target = UUID.randomUUID();
        f.state.beginPendingEdit("one", target, GuideClientUiState.DraftMode.FOLLOW_UP);
        f.state.setText("one", "preserve replacement"); f.readyImage();
        var capture = f.state.captureIntent("one");
        assertFalse(GuideClientUiState.submissionAccepted(true, new ToolResult.Success<>(false)));
        assertTrue(f.state.invalidatePendingEdit(capture));
        assertEquals(target, f.state.intent("one").pendingId());
        assertTrue(f.state.intent("one").editInvalid());
        assertEquals(GuideClientUiState.SubmissionRoute.EDIT_INVALID,
                GuideClientUiState.submissionRoute(f.state.intent("one"), false));
        assertEquals("preserve replacement", f.state.readText("one"));
        assertEquals(List.of(IMAGE), f.state.images().references());
        f.state.resetIntent("one");
        assertEquals(GuideClientUiState.SubmissionRoute.ASK,
                GuideClientUiState.submissionRoute(f.state.intent("one"), false));
        assertEquals("preserve replacement", f.state.readText("one"));
        assertEquals(List.of(IMAGE), f.state.images().references());
    }

    @Test void oldEditAcceptOrRejectionCannotClearANewTargetOrChangedMode() {
        var f = new Fixture();
        UUID first = UUID.randomUUID(); UUID second = UUID.randomUUID();
        f.state.beginPendingEdit("one", first, GuideClientUiState.DraftMode.FOLLOW_UP);
        var old = f.state.captureIntent("one");
        f.state.beginPendingEdit("one", second, GuideClientUiState.DraftMode.STEER);
        assertFalse(f.state.clearAcceptedIntent(old));
        assertFalse(f.state.invalidatePendingEdit(old));
        assertEquals(second, f.state.intent("one").pendingId());
        var current = f.state.captureIntent("one");
        f.state.setText("one", "newer text keeps the same target");
        assertTrue(f.state.clearAcceptedIntent(current));
        assertEquals(GuideClientUiState.DraftIntent.defaults(), f.state.intent("one"));
        assertEquals("newer text keeps the same target", f.state.readText("one"));
        f.state.setMode("one", GuideClientUiState.DraftMode.STEER);
        var steer = f.state.captureIntent("one");
        f.state.setMode("one", GuideClientUiState.DraftMode.FOLLOW_UP);
        assertFalse(f.state.clearAcceptedIntent(steer));
    }

    @Test void voiceModeRevisionAndStopClearSemanticsPreserveAnInvalidEditUntilExplicitAction() {
        var f = new Fixture();
        f.state.setText("one", "existing");
        var voice = f.state.captureInsertion("one");
        f.state.setMode("one", GuideClientUiState.DraftMode.STEER);
        assertEquals(GuideClientUiState.InsertionResult.PENDING, f.state.insertTranscript(voice, "old capture"));
        UUID target = UUID.randomUUID();
        f.state.beginPendingEdit("one", target, GuideClientUiState.DraftMode.STEER);
        f.state.stopIntent("one");
        assertTrue(f.state.intent("one").editInvalid());
        assertEquals(target, f.state.intent("one").pendingId());
        assertEquals("existing", f.state.readText("one"));
        f.state.clearDraft("one");
        assertEquals(GuideClientUiState.DraftIntent.defaults(), f.state.intent("one"));
        assertEquals("", f.state.readText("one"));
        assertTrue(f.state.pendingInsertions("one").isEmpty());
        f.state.setMode("one", GuideClientUiState.DraftMode.STEER);
        f.state.stopIntent("one");
        assertEquals(GuideClientUiState.DraftIntent.defaults(), f.state.intent("one"));
        var disconnected = f.state.captureIntent("one");
        f.state.close();
        assertFalse(f.state.clearAcceptedIntent(disconnected));
    }

    @Test void capturedEditInFlightSurvivesViewTransferAndDefersMissingSnapshotUntilAccepted() {
        var f = new Fixture();
        UUID target = UUID.randomUUID();
        f.state.beginPendingEdit("one", target, GuideClientUiState.DraftMode.FOLLOW_UP);
        f.state.setText("one", "replacement"); f.readyImage();
        var text = f.state.captureInsertion("one");
        var image = f.state.images().captureSubmission();
        var intent = f.state.captureIntent("one");
        CompletableFuture<ToolResult<Boolean>> accepted = new CompletableFuture<>();
        assertTrue(f.state.beginIntentSubmission(intent));
        var oldView = f.state.attach(GuideClientUiState.Surface.FULLSCREEN, "one"); oldView.close();
        var reopened = f.state.attach(GuideClientUiState.Surface.HUD_INPUT, "one");
        // Snapshot queue is now empty, but the exact edit acceptance callback has not run yet.
        assertTrue(f.state.pendingEditSubmissionInFlight("one"));
        assertTrue(f.state.intentSubmissionInFlight("one"));
        assertFalse(f.state.beginIntentSubmission(f.state.captureIntent("one")), "new view cannot double-submit");
        accepted.whenComplete((result, failure) -> {
            try {
                if (GuideClientUiState.submissionAccepted(true, result)) {
                    assertTrue(f.state.clearAcceptedText(text, "replacement"));
                    assertTrue(f.state.clearAcceptedIntent(intent));
                    assertTrue(f.state.images().accepted(image));
                }
            } finally { f.state.completeIntentSubmission(intent); }
        });
        accepted.complete(new ToolResult.Success<>(true));
        assertEquals("", f.state.readText("one"));
        assertTrue(f.state.images().empty());
        assertEquals(GuideClientUiState.DraftIntent.defaults(), f.state.intent("one"));
        assertFalse(f.state.intentSubmissionInFlight("one"));
        assertFalse(f.state.pendingEditSubmissionInFlight("one"));
        reopened.close();
    }

    @Test void unrelatedTargetAndRejectedOrFailedEditDoNotHideInvalidityOrLeakBusyMarker() {
        var f = new Fixture(); UUID target = UUID.randomUUID(); UUID newTarget = UUID.randomUUID();
        f.state.beginPendingEdit("one", target, GuideClientUiState.DraftMode.STEER);
        f.state.setText("one", "keep replacement"); f.readyImage();
        var intent = f.state.captureIntent("one");
        assertTrue(f.state.beginIntentSubmission(intent));
        f.state.setMode("one", GuideClientUiState.DraftMode.FOLLOW_UP);
        assertFalse(f.state.pendingEditSubmissionInFlight("one"), "changed intent must not skip real missing-target validation");
        assertTrue(f.state.intentSubmissionInFlight("one"), "underlying acceptance still owns its waiter");
        f.state.beginPendingEdit("one", newTarget, GuideClientUiState.DraftMode.FOLLOW_UP);
        f.state.completeIntentSubmission(f.state.captureIntent("one"));
        assertTrue(f.state.intentSubmissionInFlight("one"), "a newer capture cannot complete an older waiter");
        f.state.completeIntentSubmission(intent);
        assertFalse(f.state.intentSubmissionInFlight("one"));
        var rejected = f.state.captureIntent("one");
        assertTrue(f.state.beginIntentSubmission(rejected));
        try {
            ToolResult<Boolean> result = new ToolResult.Success<>(false);
            assertFalse(GuideClientUiState.submissionAccepted(true, result));
            assertTrue(f.state.invalidatePendingEdit(rejected));
        } finally { f.state.completeIntentSubmission(rejected); }
        assertTrue(f.state.intent("one").editInvalid());
        assertEquals(newTarget, f.state.intent("one").pendingId());
        assertEquals("keep replacement", f.state.readText("one"));
        assertEquals(List.of(IMAGE), f.state.images().references());
        assertFalse(f.state.intentSubmissionInFlight("one"));
        f.state.resetIntent("one");
        var failed = f.state.captureIntent("one");
        assertTrue(f.state.beginIntentSubmission(failed));
        try { throw new IllegalStateException("fake dispatch failure"); }
        catch (IllegalStateException expected) { assertEquals("fake dispatch failure", expected.getMessage()); }
        finally { f.state.completeIntentSubmission(failed); }
        assertFalse(f.state.intentSubmissionInFlight("one"));
    }

    private static final class Fixture {
        final Queue work = new Queue();
        final Queue client = new Queue();
        final List<CompletableFuture<ToolResult<ImageReference>>> imports = new ArrayList<>();
        final List<ImageReference> discarded = new ArrayList<>();
        final List<List<ImageReference>> retains = new ArrayList<>();
        CompletableFuture<ToolResult<Boolean>> delayedLease;
        int releases;
        int readsOnAttach;
        boolean clipboardUnavailable;
        final GuideClientUiState state = new GuideClientUiState("one", () -> {
            readsOnAttach++;
            if (clipboardUnavailable) return ImageClipboard.Read.unavailable();
            return ImageClipboard.Read.image(new BufferedImage(3, 2, BufferedImage.TYPE_INT_ARGB));
        }, work, client::execute, png -> {
            assertFalse(work.running);
            var future = new CompletableFuture<ToolResult<ImageReference>>(); imports.add(future); return future;
        }, discarded::add, (owner, refs) -> {
            retains.add(List.copyOf(refs));
            return delayedLease == null ? CompletableFuture.completedFuture(new ToolResult.Success<>(true)) : delayedLease;
        }, owner -> releases++);
        void readyImage() {
            state.images().paste(); work.runAll(); client.runAll();
            imports.getLast().complete(new ToolResult.Success<>(IMAGE)); client.runAll();
        }
    }
    private static final class Queue implements Executor {
        final List<Runnable> jobs = new ArrayList<>(); boolean running;
        public void execute(Runnable command) { jobs.add(command); }
        void runAll() { running = true; try { while (!jobs.isEmpty()) jobs.removeFirst().run(); } finally { running = false; } }
    }
}
