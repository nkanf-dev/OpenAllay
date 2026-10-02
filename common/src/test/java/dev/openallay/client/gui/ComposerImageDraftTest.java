package dev.openallay.client.gui;

import static org.junit.jupiter.api.Assertions.*;
import dev.openallay.client.gui.clipboard.ImageClipboard;
import dev.openallay.model.image.ImageReference;
import dev.openallay.tool.ToolResult;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;

final class ComposerImageDraftTest {
    private static final ImageReference IMAGE = new ImageReference("a".repeat(64), "image/png", 3, 2, 70);

    @Test
    void constructingAndAttachingNeverReadClipboard() {
        var fixture = new Fixture();
        assertEquals(0, fixture.reads);
        fixture.draft.attach("one");
        assertEquals(0, fixture.reads);
    }

    @Test
    void imageEncodingRunsOffClientAndMultipleImagesAreRemovable() {
        var fixture = new Fixture(); fixture.draft.attach("one");
        fixture.draft.paste(); fixture.draft.paste();
        assertEquals(0, fixture.reads); assertTrue(fixture.draft.pending());
        fixture.work.runAll();
        assertEquals(2, fixture.reads); assertEquals(0, fixture.imports.size());
        fixture.client.runAll();
        assertEquals(2, fixture.imports.size());
        fixture.imports.forEach(future -> future.complete(new ToolResult.Success<>(IMAGE)));
        fixture.client.runAll();
        assertFalse(fixture.draft.pending()); assertEquals(2, fixture.draft.references().size());
        fixture.draft.remove(fixture.draft.attachments().getFirst().id());
        assertEquals(1, fixture.draft.references().size());
    }

    @Test
    void sessionSwitchBeforeEncodingPreventsImportAndLatePasting() {
        var fixture = new Fixture(); fixture.draft.attach("one"); fixture.draft.paste();
        fixture.draft.selectSession("two"); fixture.work.runAll(); fixture.client.runAll();
        assertTrue(fixture.draft.empty()); assertTrue(fixture.imports.isEmpty());
        fixture.draft.selectSession("one"); assertTrue(fixture.draft.empty());
    }

    @Test
    void closeOrAwayAndBackInvalidateInFlightImportCallbacks() {
        for (boolean close : new boolean[] {true, false}) {
            var fixture = new Fixture(); fixture.draft.attach("one"); fixture.draft.paste();
            fixture.work.runAll(); fixture.client.runAll();
            if (close) { fixture.draft.detach(); fixture.draft.attach("one"); }
            else { fixture.draft.observeSession("two"); fixture.draft.observeSession("one"); }
            fixture.imports.getFirst().complete(new ToolResult.Success<>(IMAGE)); fixture.client.runAll();
            assertTrue(fixture.draft.empty());
            assertEquals(List.of(IMAGE), fixture.discardedImports);
        }
    }

    @Test
    void acceptedClearsOnlyCapturedImagesAndFailureKeepsDraft() {
        var fixture = new Fixture(); fixture.draft.attach("one"); fixture.readyImage();
        var submitted = fixture.draft.captureSubmission();
        fixture.readyImage();
        // A rejected request never calls accepted and therefore retains both images.
        assertEquals(2, fixture.draft.references().size());
        assertTrue(fixture.draft.accepted(submitted));
        assertEquals(1, fixture.draft.references().size());
        fixture.draft.selectSession("two");
        assertFalse(fixture.draft.accepted(submitted));
        fixture.draft.selectSession("one"); assertEquals(1, fixture.draft.references().size());
    }

    @Test
    void missingPendingEditRetainsImageDraftAndTextDecision() {
        var fixture = new Fixture(); fixture.draft.attach("one"); fixture.readyImage();
        var submitted = fixture.draft.captureSubmission();
        ToolResult<Boolean> alreadyConsumed = new ToolResult.Success<>(false);
        if (OpenAllayScreen.submissionAccepted(true, alreadyConsumed)) fixture.draft.accepted(submitted);
        assertEquals(1, fixture.draft.references().size());
        assertFalse(OpenAllayScreen.submissionAccepted(true, alreadyConsumed));
    }

    @Test
    void removalDuringImportAndEmptyClipboardCannotAddImageLater() {
        var fixture = new Fixture(); fixture.draft.attach("one"); fixture.draft.paste();
        fixture.work.runAll(); fixture.client.runAll();
        fixture.draft.remove(fixture.draft.attachments().getFirst().id());
        fixture.imports.getFirst().complete(new ToolResult.Success<>(IMAGE)); fixture.client.runAll();
        assertTrue(fixture.draft.empty());
        fixture.clipboardImage = false; fixture.draft.paste(); fixture.work.runAll(); fixture.client.runAll();
        assertTrue(fixture.draft.empty());
    }

    private static final class Fixture {
        final QueuedExecutor work = new QueuedExecutor();
        final QueuedExecutor client = new QueuedExecutor();
        final List<CompletableFuture<ToolResult<ImageReference>>> imports = new ArrayList<>();
        final List<ImageReference> discardedImports = new ArrayList<>();
        int reads;
        boolean clipboardImage = true;
        final ComposerImageDraft draft = new ComposerImageDraft(() -> {
            reads++;
            return clipboardImage ? ImageClipboard.Read.image(new BufferedImage(3, 2, BufferedImage.TYPE_INT_ARGB))
                    : ImageClipboard.Read.empty();
        }, work, client::execute, png -> {
            assertFalse(work.running, "Import must be accepted back on the client dispatcher");
            assertTrue(png.length > 0);
            var future = new CompletableFuture<ToolResult<ImageReference>>(); imports.add(future); return future;
        }, ignored -> {}, discardedImports::add);
        void readyImage() {
            draft.paste(); work.runAll(); client.runAll();
            imports.getLast().complete(new ToolResult.Success<>(IMAGE)); client.runAll();
        }
    }
    private static final class QueuedExecutor implements Executor {
        final List<Runnable> jobs = new ArrayList<>(); boolean running;
        public void execute(Runnable command) { jobs.add(command); }
        void runAll() {
            running = true;
            try { while (!jobs.isEmpty()) jobs.removeFirst().run(); }
            finally { running = false; }
        }
    }
}
