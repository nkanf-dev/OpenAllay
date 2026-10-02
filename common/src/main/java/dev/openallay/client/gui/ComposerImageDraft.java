package dev.openallay.client.gui;

import dev.openallay.client.ClientEventDispatcher;
import dev.openallay.client.gui.clipboard.ClipboardImageEncoder;
import dev.openallay.client.gui.clipboard.ImageClipboard;
import dev.openallay.model.image.ImageReference;
import dev.openallay.tool.ToolResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

/** Client-owned draft images. Only explicit paste reads the clipboard; late work cannot change another draft. */
public final class ComposerImageDraft {
    private final ImageClipboard clipboard;
    private final Executor worker;
    private final ClientEventDispatcher client;
    private final Function<byte[], CompletableFuture<ToolResult<ImageReference>>> importer;
    private final Consumer<Notice> changed;
    private final Consumer<ImageReference> discardedImport;
    private final Map<String, List<Attachment>> drafts = new LinkedHashMap<>();
    private String session;
    private long generation;
    private boolean attached;

    public ComposerImageDraft(ImageClipboard clipboard, Executor worker, ClientEventDispatcher client,
            Function<byte[], CompletableFuture<ToolResult<ImageReference>>> importer, Consumer<Notice> changed) {
        this(clipboard, worker, client, importer, changed, ignored -> {});
    }

    public ComposerImageDraft(ImageClipboard clipboard, Executor worker, ClientEventDispatcher client,
            Function<byte[], CompletableFuture<ToolResult<ImageReference>>> importer, Consumer<Notice> changed,
            Consumer<ImageReference> discardedImport) {
        this.clipboard = Objects.requireNonNull(clipboard, "clipboard");
        this.worker = Objects.requireNonNull(worker, "worker");
        this.client = Objects.requireNonNull(client, "client");
        this.importer = Objects.requireNonNull(importer, "importer");
        this.changed = Objects.requireNonNull(changed, "changed");
        this.discardedImport = Objects.requireNonNull(discardedImport, "discardedImport");
    }

    public void attach(String session) {
        attached = true;
        selectSession(session);
    }

    public void detach() {
        attached = false;
        invalidatePending();
    }

    public void observeSession(String session) {
        if (attached && !Objects.equals(this.session, session)) invalidatePending();
    }

    public void selectSession(String session) {
        Objects.requireNonNull(session, "session");
        if (session.equals(this.session)) return;
        invalidatePending();
        this.session = session;
        drafts.computeIfAbsent(session, ignored -> new ArrayList<>());
    }

    private void invalidatePending() {
        generation++;
        if (session != null) drafts.getOrDefault(session, List.of()).removeIf(Attachment::pending);
    }

    public List<Attachment> attachments() {
        return session == null ? List.of() : List.copyOf(drafts.get(session));
    }

    public boolean pending() { return attachments().stream().anyMatch(Attachment::pending); }
    public boolean empty() { return attachments().isEmpty(); }
    public List<ImageReference> references() {
        return attachments().stream().map(Attachment::reference).filter(Objects::nonNull).toList();
    }

    public void paste() {
        if (!attached || session == null) return;
        Scope scope = new Scope(session, generation);
        UUID id = UUID.randomUUID();
        drafts.get(session).add(new Attachment(id, null, null));
        changed.accept(Notice.PROCESSING);
        ImageClipboard captured;
        try {
            captured = clipboard.capture();
        } catch (RuntimeException | LinkageError | java.awt.AWTError unavailable) {
            fail(scope, id, Notice.CLIPBOARD_UNAVAILABLE);
            return;
        }
        worker.execute(() -> {
            try {
                ImageClipboard.Read read = captured.read();
                if (read.status() != ImageClipboard.Status.IMAGE) {
                    client.execute(() -> fail(scope, id, read.status() == ImageClipboard.Status.EMPTY
                            ? Notice.NONE : Notice.CLIPBOARD_UNAVAILABLE));
                    return;
                }
                ClipboardImageEncoder.Encoded image = ClipboardImageEncoder.encode(read.image());
                client.execute(() -> {
                    if (!current(scope, id)) return;
                    replace(id, new Attachment(id, null, image.preview()));
                    changed.accept(Notice.PROCESSING);
                    // The importer owns actor-scoped asynchronous storage, never the render loop.
                    try {
                        importer.apply(image.png()).whenComplete((result, failure) -> client.execute(() -> {
                            if (!current(scope, id)) {
                                if (result instanceof ToolResult.Success<ImageReference> imported) {
                                    discardedImport.accept(imported.value());
                                }
                                return;
                            }
                            if (failure != null || !(result instanceof ToolResult.Success<ImageReference> imported)) {
                                fail(scope, id, Notice.IMPORT_FAILED);
                                return;
                            }
                            replace(id, new Attachment(id, imported.value(), image.preview()));
                            changed.accept(Notice.READY);
                        }));
                    } catch (RuntimeException failed) {
                        fail(scope, id, Notice.IMPORT_FAILED);
                    }
                });
            } catch (Exception | LinkageError | java.awt.AWTError failed) {
                client.execute(() -> fail(scope, id, Notice.CLIPBOARD_UNAVAILABLE));
            }
        });
    }

    private boolean current(Scope scope, UUID id) {
        return attached && generation == scope.generation && Objects.equals(session, scope.session)
                && drafts.get(session).stream().anyMatch(value -> value.id.equals(id));
    }

    private void fail(Scope scope, UUID id, Notice notice) {
        if (!current(scope, id)) return;
        drafts.get(session).removeIf(value -> value.id.equals(id));
        changed.accept(notice);
    }

    private void replace(UUID id, Attachment replacement) {
        List<Attachment> images = drafts.get(session);
        for (int index = 0; index < images.size(); index++) {
            if (images.get(index).id.equals(id)) { images.set(index, replacement); return; }
        }
    }

    public void remove(UUID id) {
        if (session != null && drafts.get(session).removeIf(value -> value.id.equals(id))) changed.accept(Notice.NONE);
    }

    /** Editing a queued message replaces this draft, invalidating every earlier paste completion. */
    public void restore(List<ImageReference> references) {
        invalidatePending();
        List<Attachment> values = drafts.get(session);
        values.clear();
        references.forEach(reference -> values.add(new Attachment(UUID.randomUUID(), reference, null)));
        changed.accept(Notice.NONE);
    }

    public void preview(UUID id, ClipboardImageEncoder.Preview preview) {
        attachments().stream().filter(value -> value.id.equals(id) && value.reference != null)
                .findFirst().ifPresent(value -> replace(id, new Attachment(id, value.reference, preview)));
    }

    public Submission captureSubmission() {
        return new Submission(session, generation, attachments().stream().map(Attachment::id).toList());
    }

    /** Only an accepted submission can remove its captured images; later additions survive. */
    public boolean accepted(Submission submission) {
        if (!attached || generation != submission.generation || !Objects.equals(session, submission.session)) return false;
        drafts.get(session).removeIf(value -> submission.ids.contains(value.id));
        changed.accept(Notice.NONE);
        return true;
    }

    public List<ImageReference> retainedReferences() {
        return drafts.values().stream().flatMap(List::stream).map(Attachment::reference)
                .filter(Objects::nonNull).distinct().toList();
    }

    public long generation() { return generation; }
    public boolean attached() { return attached; }
    public record Attachment(UUID id, ImageReference reference, ClipboardImageEncoder.Preview preview) {
        public boolean pending() { return reference == null; }
    }
    public record Submission(String session, long generation, List<UUID> ids) {
        public Submission { ids = List.copyOf(ids); }
    }
    private record Scope(String session, long generation) {}
    public enum Notice { NONE, PROCESSING, READY, CLIPBOARD_UNAVAILABLE, IMPORT_FAILED }
}
