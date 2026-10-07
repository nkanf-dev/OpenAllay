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
    private final Map<String, Long> generations = new LinkedHashMap<>();
    private long lifetime;
    private String changedSession;
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
        lifetime++;
        drafts.values().forEach(values -> values.removeIf(Attachment::pending));
    }

    public void observeSession(String session) {
        // Selection is a view concern. A captured paste still belongs to its original session.
    }

    public void selectSession(String session) {
        Objects.requireNonNull(session, "session");
        if (session.equals(this.session)) return;
        this.session = session;
        drafts.computeIfAbsent(session, ignored -> new ArrayList<>());
        generations.putIfAbsent(session, 0L);
    }

    private void invalidatePending() {
        if (session != null) generations.merge(session, 1L, Long::sum);
        if (session != null) drafts.getOrDefault(session, dev.openallay.util.Java8Collections.listOf()).removeIf(Attachment::pending);
    }

    public List<Attachment> attachments() {
        return attachments(session);
    }

    public List<Attachment> attachments(String session) {
        return session == null ? dev.openallay.util.Java8Collections.listOf() : dev.openallay.util.Java8Collections.listCopyOf(drafts.getOrDefault(session, dev.openallay.util.Java8Collections.listOf()));
    }

    public boolean pending() { return attachments().stream().anyMatch(Attachment::pending); }
    public boolean empty() { return attachments().isEmpty(); }
    public List<ImageReference> references() {
        return dev.openallay.util.Java8Collections.toList(attachments().stream().map(Attachment::reference).filter(Objects::nonNull));
    }

    public void paste() {
        if (!attached || session == null) return;
        Scope scope = new Scope(session, generation(), lifetime);
        UUID id = UUID.randomUUID();
        drafts.get(session).add(new Attachment(id, null, null));
        notifyChanged(session, Notice.PROCESSING);
        ImageClipboard captured;
        try {
            captured = clipboard.capture();
        } catch (RuntimeException | LinkageError | java.awt.AWTError unavailable) {
            fail(scope, id, Notice.CLIPBOARD_UNAVAILABLE);
            return;
        }
        try {
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
                        replace(scope.session, id, new Attachment(id, null, image.preview()));
                        notifyChanged(scope.session, Notice.PROCESSING);
                        // The importer owns actor-scoped asynchronous storage, never the render loop.
                        try {
                            importer.apply(image.png()).whenComplete((result, failure) -> client.execute(() -> {
                                if (!current(scope, id)) {
                                    if (result instanceof ToolResult.Success<?>) {
                                        @SuppressWarnings("unchecked")
                                        ToolResult.Success<ImageReference> imported = (ToolResult.Success<ImageReference>) result;
                                        discardedImport.accept(imported.value());
                                    }
                                    return;
                                }
                                if (failure != null || !(result instanceof ToolResult.Success<?>)) {
                                    fail(scope, id, Notice.IMPORT_FAILED);
                                    return;
                                }
                                @SuppressWarnings("unchecked")
                                ToolResult.Success<ImageReference> imported = (ToolResult.Success<ImageReference>) result;
                                replace(scope.session, id, new Attachment(id, imported.value(), image.preview()));
                                notifyChanged(scope.session, Notice.READY);
                            }));
                        } catch (RuntimeException failed) {
                            fail(scope, id, Notice.IMPORT_FAILED);
                        }
                    });
                } catch (Exception | LinkageError | java.awt.AWTError failed) {
                    client.execute(() -> fail(scope, id, Notice.CLIPBOARD_UNAVAILABLE));
                } finally {
                    captured.close();
                }
            });
        } catch (RuntimeException | LinkageError | java.awt.AWTError rejected) {
            captured.close();
            fail(scope, id, Notice.CLIPBOARD_UNAVAILABLE);
        }
    }

    private boolean current(Scope scope, UUID id) {
        return attached && lifetime == scope.lifetime
                && generations.getOrDefault(scope.session, -1L) == scope.generation
                && drafts.getOrDefault(scope.session, dev.openallay.util.Java8Collections.listOf()).stream().anyMatch(value -> value.id.equals(id));
    }

    private void fail(Scope scope, UUID id, Notice notice) {
        if (!current(scope, id)) return;
        drafts.get(scope.session).removeIf(value -> value.id.equals(id));
        notifyChanged(scope.session, notice);
    }

    private void replace(String session, UUID id, Attachment replacement) {
        List<Attachment> images = drafts.get(session);
        for (int index = 0; index < images.size(); index++) {
            if (images.get(index).id.equals(id)) { images.set(index, replacement); return; }
        }
    }

    public void remove(UUID id) {
        if (session != null && drafts.get(session).removeIf(value -> value.id.equals(id))) notifyChanged(session, Notice.NONE);
    }

    /** Editing a queued message replaces this draft, invalidating every earlier paste completion. */
    public void restore(List<ImageReference> references) {
        invalidatePending();
        List<Attachment> values = drafts.get(session);
        values.clear();
        references.forEach(reference -> values.add(new Attachment(UUID.randomUUID(), reference, null)));
        notifyChanged(session, Notice.NONE);
    }

    public void preview(UUID id, ClipboardImageEncoder.Preview preview) {
        attachments().stream().filter(value -> value.id.equals(id) && value.reference != null)
                .findFirst().ifPresent(value -> replace(session, id, new Attachment(id, value.reference, preview)));
    }

    public Submission captureSubmission() {
        return new Submission(session, generation(), dev.openallay.util.Java8Collections.toList(attachments().stream().map(Attachment::id)));
    }

    /** Only an accepted submission can remove its captured images; later additions survive. */
    public boolean accepted(Submission submission) {
        if (!attached || generations.getOrDefault(submission.session, -1L) != submission.generation) return false;
        drafts.get(submission.session).removeIf(value -> submission.ids.contains(value.id));
        notifyChanged(submission.session, Notice.NONE);
        return true;
    }

    public List<ImageReference> retainedReferences() {
        return dev.openallay.util.Java8Collections.toList(drafts.values().stream().flatMap(List::stream).map(Attachment::reference)
                .filter(Objects::nonNull).distinct());
    }

    public long generation() { return generations.getOrDefault(session, 0L); }
    public String changedSession() { return changedSession; }
    private void notifyChanged(String session, Notice notice) {
        changedSession = session;
        changed.accept(notice);
    }
    public void clear() { restore(dev.openallay.util.Java8Collections.listOf()); }
    public boolean attached() { return attached; }
    @dev.openallay.value.ValueType(Attachment.ValueSchemaProvider.class)
public static final class Attachment {
    private final UUID id;
    private final ImageReference reference;
    private final ClipboardImageEncoder.Preview preview;
    public Attachment(UUID id, ImageReference reference, ClipboardImageEncoder.Preview preview) {
        this.id = id;
        this.reference = reference;
        this.preview = preview;
    }
    public UUID id() { return id; }
    public ImageReference reference() { return reference; }
    public ClipboardImageEncoder.Preview preview() { return preview; }
public boolean pending() { return reference == null; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Attachment)) return false;
        Attachment that = (Attachment) other;
        return java.util.Objects.equals(id, that.id) && java.util.Objects.equals(reference, that.reference) && java.util.Objects.equals(preview, that.preview);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(reference);
        hash = 31 * hash + java.util.Objects.hashCode(preview);
        return hash;
    }
    @Override public String toString() { return "Attachment[id=" + id + ", reference=" + reference + ", preview=" + preview + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Attachment> schema() {
            return new dev.openallay.value.ValueSchema<>(Attachment.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Attachment>>asList(new dev.openallay.value.ValueSchema.Component<>(Attachment.class, "id", Attachment::id), new dev.openallay.value.ValueSchema.Component<>(Attachment.class, "reference", Attachment::reference), new dev.openallay.value.ValueSchema.Component<>(Attachment.class, "preview", Attachment::preview)), arguments -> new Attachment((UUID) arguments[0], (ImageReference) arguments[1], (ClipboardImageEncoder.Preview) arguments[2]));
        }
    }
}
    @dev.openallay.value.ValueType(Submission.ValueSchemaProvider.class)
public static final class Submission {
    private final String session;
    private final long generation;
    private final List<UUID> ids;
    public Submission(String session, long generation, List<UUID> ids) {
 ids = dev.openallay.util.Java8Collections.listCopyOf(ids);
        this.session = session;
        this.generation = generation;
        this.ids = ids;
    }
    public String session() { return session; }
    public long generation() { return generation; }
    public List<UUID> ids() { return ids; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Submission)) return false;
        Submission that = (Submission) other;
        return java.util.Objects.equals(session, that.session) && generation == that.generation && java.util.Objects.equals(ids, that.ids);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(session);
        hash = 31 * hash + Long.hashCode(generation);
        hash = 31 * hash + java.util.Objects.hashCode(ids);
        return hash;
    }
    @Override public String toString() { return "Submission[session=" + session + ", generation=" + generation + ", ids=" + ids + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Submission> schema() {
            return new dev.openallay.value.ValueSchema<>(Submission.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Submission>>asList(new dev.openallay.value.ValueSchema.Component<>(Submission.class, "session", Submission::session), new dev.openallay.value.ValueSchema.Component<>(Submission.class, "generation", Submission::generation), new dev.openallay.value.ValueSchema.Component<>(Submission.class, "ids", Submission::ids)), arguments -> new Submission((String) arguments[0], (Long) arguments[1], (List) arguments[2]));
        }
    }
}
    @dev.openallay.value.ValueType(Scope.ValueSchemaProvider.class)
private static final class Scope {
    private final String session;
    private final long generation;
    private final long lifetime;
    private Scope(String session, long generation, long lifetime) {
        this.session = session;
        this.generation = generation;
        this.lifetime = lifetime;
    }
    public String session() { return session; }
    public long generation() { return generation; }
    public long lifetime() { return lifetime; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Scope)) return false;
        Scope that = (Scope) other;
        return java.util.Objects.equals(session, that.session) && generation == that.generation && lifetime == that.lifetime;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(session);
        hash = 31 * hash + Long.hashCode(generation);
        hash = 31 * hash + Long.hashCode(lifetime);
        return hash;
    }
    @Override public String toString() { return "Scope[session=" + session + ", generation=" + generation + ", lifetime=" + lifetime + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Scope> schema() {
            return new dev.openallay.value.ValueSchema<>(Scope.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Scope>>asList(new dev.openallay.value.ValueSchema.Component<>(Scope.class, "session", Scope::session), new dev.openallay.value.ValueSchema.Component<>(Scope.class, "generation", Scope::generation), new dev.openallay.value.ValueSchema.Component<>(Scope.class, "lifetime", Scope::lifetime)), arguments -> new Scope((String) arguments[0], (Long) arguments[1], (Long) arguments[2]));
        }
    }
}
    public enum Notice { NONE, PROCESSING, READY, CLIPBOARD_UNAVAILABLE, IMPORT_FAILED }
}
