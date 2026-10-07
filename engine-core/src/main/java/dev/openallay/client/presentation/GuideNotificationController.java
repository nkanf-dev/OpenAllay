package dev.openallay.client.presentation;

import dev.openallay.guide.GuidePresentationEvent;
import dev.openallay.guide.GuidePresentationListener;
import dev.openallay.guide.GuideService;
import dev.openallay.guide.ui.GuideUiConfig;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** Connection-owned live receipts, explicit read state and task batching. No Minecraft or I/O. */
public final class GuideNotificationController implements GuidePresentationListener, AutoCloseable {
    private static final int MAX_PENDING_TASKS = 8;
    private static final long QUIET_MILLIS = 600;
    private static final long MAX_BATCH_MILLIS = 2000;
    private final Supplier<GuideUiConfig.Notifications> settings;
    private final Clock clock;
    private final GuideNotificationPort port;
    private final Map<GuidePresentationEvent.Key, Receipt> receipts = new LinkedHashMap<>();
    private final Map<Task, Batch> pending = new LinkedHashMap<>();
    private final Map<Task, OwnedToast> owned = new LinkedHashMap<>();
    private final List<OwnedToast> tests = new ArrayList<>();
    private GuideService service;
    private UUID generation;
    private UUID actor;
    private Set<GuidePresentationEvent.Key> visible = dev.openallay.util.Java8Collections.setOf();
    private String visibleSession;
    private boolean windowActive;
    private boolean enabled;
    private boolean closed;
    private int overflowTasks;

    public GuideNotificationController(Supplier<GuideUiConfig.Notifications> settings,
                                       Clock clock, GuideNotificationPort port) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.port = Objects.requireNonNull(port, "port");
        enabled = config().enabled();
    }

    @Override public void bound(GuideService next) {
        if (closed) return;
        if (service == next) return;
        clearConnection();
        service = Objects.requireNonNull(next, "service");
        generation = next.presentationGeneration();
        actor = next.snapshot().actorId();
        enabled = config().enabled();
    }

    @Override public void event(GuidePresentationEvent event) {
        if (closed || generation == null || !generation.equals(event.key().connectionGeneration())
                || !actor.equals(event.key().actorId()) || receipts.containsKey(event.key())
                || !currentOwner(event.key())) return;
        settingsChanged();
        // Keep small semantic preview only. Original reply, cards and history remain unchanged.
        GuidePresentationEvent stored = new GuidePresentationEvent(event.key(), event.kind(),
                event.content(), preview(event.preview()), event.cardPreviews(), event.createdAt());
        receipts.put(stored.key(), new Receipt(stored));
        GuideUiConfig.Notifications config = config();
        if (!enabled || !eligible(stored, config) || suppressed(stored, config)
                || stored.kind() == GuidePresentationEvent.Kind.CARD_BATCH && stored.cardPreviews().isEmpty()) return;
        Task task = Task.of(stored.key());
        Batch batch = pending.get(task);
        if (batch == null) {
            if (pending.size() == MAX_PENDING_TASKS) {
                Task first = pending.keySet().iterator().next();
                pending.remove(first);
                overflowTasks++;
            }
            batch = new Batch(task, clock.instant());
            pending.put(task, batch);
        }
        batch.events.add(stored.key());
        batch.last = clock.instant();
    }

    @Override public void invalidated(UUID connectionGeneration) {
        if (Objects.equals(generation, connectionGeneration)) clearConnection();
    }

    /** Called once per client tick, independently of HUD enabled/rendering. */
    public void tick() {
        if (closed) return;
        settingsChanged();
        pruneDeletedSessions();
        Instant now = clock.instant();
        tests.removeIf(test -> {
            if (!test.handle.finished()) return false;
            test.retire();
            return true;
        });
        owned.entrySet().removeIf(entry -> {
            OwnedToast toast = entry.getValue();
            if (!toast.handle.finished()) return false;
            toast.retire();
            return true;
        });
        if (!enabled || generation == null) return;
        GuideUiConfig.Notifications config = config();
        for (Batch batch : dev.openallay.util.Java8Collections.listCopyOf(pending.values())) {
            long quiet = java.time.Duration.between(batch.last, now).toMillis();
            long age = java.time.Duration.between(batch.first, now).toMillis();
            if (quiet < QUIET_MILLIS && age < MAX_BATCH_MILLIS) continue;
            pending.remove(batch.task);
            List<GuidePresentationEvent> deliverable = dev.openallay.util.Java8Collections.toList(batch.events.stream()
                    .map(receipts::get).filter(Objects::nonNull).map(receipt -> receipt.event)
                    .filter(event -> eligible(event, config))
                    .filter(event -> !suppressed(event, config)));
            if (deliverable.isEmpty()) continue; // Suppression is not read acknowledgement.
            OwnedToast previous = owned.get(batch.task);
            GuideNotificationPort.Fence fence = previous == null
                    ? new GuideNotificationPort.Fence() : previous.fence;
            Set<GuidePresentationEvent.Key> merged = new LinkedHashSet<>();
            if (previous != null) merged.addAll(previous.events);
            merged.addAll(batch.events);
            List<GuidePresentationEvent> all = dev.openallay.util.Java8Collections.toList(merged.stream().map(receipts::get)
                    .filter(Objects::nonNull).map(receipt -> receipt.event)
                    .filter(event -> eligible(event, config))
                    .filter(event -> !suppressed(event, config)));
            GuideNotificationPort.Notification notification = notification(batch.task, all,
                    config.durationSeconds(), fence, overflowTasks);
            if (notification.cardPreviews().isEmpty() && !notification.replyCompleted()
                    && !notification.taskCompleted() && !notification.taskFailed()) continue;
            overflowTasks = 0;
            if (previous == null) {
                if (owned.size() == MAX_PENDING_TASKS) {
                    Task oldest = owned.keySet().iterator().next();
                    owned.remove(oldest).hide();
                }
                GuideNotificationPort.Handle handle = port.show(notification);
                owned.put(batch.task, new OwnedToast(fence, handle, merged));
            } else {
                previous.events.addAll(merged);
                previous.handle.update(notification); // Same task upgrades its owned object, no restart/spam.
            }
            deliverable.forEach(event -> receipts.get(event.key()).delivered = true);
        }
    }

    /** Invoke directly from settings publication, so disabled queued/native objects are fenced now. */
    public void settingsChanged() {
        boolean next = config().enabled();
        if (!next && enabled) {
            pending.clear();
            overflowTasks = 0;
            owned.values().forEach(OwnedToast::hide);
            owned.clear();
            tests.forEach(OwnedToast::hide);
            tests.clear();
        }
        enabled = next; // Enabling starts with future receipts; it does not replay history or unread state.
        suppressVisibleSession();
    }

    public List<GuidePresentationEvent> receipts(GuideService owner, String sessionId) {
        if (owner != service || generation == null) return dev.openallay.util.Java8Collections.listOf();
        return dev.openallay.util.Java8Collections.toList(receipts.values().stream().map(receipt -> receipt.event)
                .filter(event -> event.key().sessionId().equals(sessionId) && currentOwner(event.key())));
    }

    public int unread(GuideService owner, String sessionId) {
        if (owner != service || generation == null) return 0;
        // A task summary and its final reply are facts for one request, not two unread badges.
        return (int) receipts.values().stream().filter(receipt -> !receipt.seen)
                .map(receipt -> receipt.event.key())
                .filter(key -> key.sessionId().equals(sessionId) && currentOwner(key)).map(Task::of).distinct().count();
    }

    /** Full guide presence suppresses this session. Exact rendered keys remain separate read evidence. */
    public void visible(GuideService owner, String sessionId,
                        Set<GuidePresentationEvent.Key> actuallyVisible, boolean active) {
        if (owner != service || generation == null) return;
        visibleSession = sessionId;
        windowActive = active;
        visible = actuallyVisible.stream().filter(receipts::containsKey)
                .filter(key -> key.connectionGeneration().equals(generation)
                        && key.sessionId().equals(sessionId)).collect(java.util.stream.Collectors.toUnmodifiableSet());
        suppressVisibleSession();
    }

    public void clearVisibility(GuideService owner) {
        if (owner == service) { visible = dev.openallay.util.Java8Collections.setOf(); visibleSession = null; windowActive = false; }
    }

    /** Explicit real content-view acknowledgement. Toast expiry, HUD preview and suppression do not call it. */
    public void markSeen(Set<GuidePresentationEvent.Key> keys) {
        for (GuidePresentationEvent.Key key : keys) {
            Receipt receipt = receipts.get(key);
            if (receipt != null && Objects.equals(generation, key.connectionGeneration())) receipt.seen = true;
        }
    }

    public boolean seen(GuidePresentationEvent.Key key) {
        Receipt receipt = receipts.get(key);
        return receipt != null && receipt.seen;
    }
    public boolean delivered(GuidePresentationEvent.Key key) {
        Receipt receipt = receipts.get(key);
        return receipt != null && receipt.delivered;
    }

    /** Local settings preview; never creates an Agent request or unread receipt. */
    public void testNotification() { testNotification(config()); }
    public void testNotification(GuideUiConfig.Notifications config) {
        if (closed) return;
        GuideNotificationPort.Fence fence = new GuideNotificationPort.Fence();
        UUID id = UUID.randomUUID();
        GuideNotificationPort.Notification preview = new GuideNotificationPort.Notification(
                generation == null ? id : generation, actor == null ? new UUID(0, 0) : actor,
                id, "preview", id, "", 0, dev.openallay.util.Java8Collections.listOf(), true, true, false,
                config.durationSeconds(), fence);
        tests.add(new OwnedToast(fence, port.show(preview), dev.openallay.util.Java8Collections.setOf()));
    }

    @Override public void close() { clearConnection(); closed = true; }

    private boolean suppressed(GuidePresentationEvent event, GuideUiConfig.Notifications config) {
        return config.policy() != GuideUiConfig.NotificationPolicy.ALWAYS
                && windowActive && event.key().sessionId().equals(visibleSession);
    }
    private void suppressVisibleSession() {
        if (!windowActive || visibleSession == null
                || config().policy() == GuideUiConfig.NotificationPolicy.ALWAYS) return;
        pending.entrySet().removeIf(entry -> entry.getKey().session.equals(visibleSession));
        owned.entrySet().removeIf(entry -> {
            if (!entry.getKey().session.equals(visibleSession)) return false;
            entry.getValue().hide();
            return true;
        });
        // Receipt read state is untouched. Other sessions keep independent pending/owned objects.
    }
    private GuideUiConfig.Notifications config() { return Objects.requireNonNull(settings.get(), "notification settings"); }
    private static boolean eligible(GuidePresentationEvent event, GuideUiConfig.Notifications config) {
        return switch (event.kind()) {
            case REPLY_FINAL -> config.replyCompleted();
            case CARD_BATCH -> config.cardBatches();
            // Task completion is a fallback under the reply option, never an unconfigurable extra toast.
            case TASK_COMPLETED -> config.replyCompleted();
            case TASK_FAILED -> config.taskFailures();
        };
    }
    private GuideNotificationPort.Notification notification(Task task, List<GuidePresentationEvent> events,
                                                           int duration, GuideNotificationPort.Fence fence,
                                                           int additionalTasks) {
        String text = "", failure = "";
        Set<GuidePresentationEvent.ContentRef> cards = new LinkedHashSet<>();
        Map<GuidePresentationEvent.ContentRef, GuidePresentationEvent.CardPreview> previews = new LinkedHashMap<>();
        boolean reply = false, complete = false, failed = false;
        for (GuidePresentationEvent event : dev.openallay.util.Java8Collections.toList(events.stream()
                .sorted(java.util.Comparator.comparingLong(event -> event.key().sequence())))) {
            switch (event.kind()) {
                case REPLY_FINAL -> { reply = true; text = event.preview(); }
                case CARD_BATCH -> {
                    cards.addAll(event.content());
                    event.cardPreviews().forEach(card -> previews.putIfAbsent(card.source(), card));
                }
                case TASK_COMPLETED -> complete = true;
                case TASK_FAILED -> { failed = true; failure = event.preview(); }
            }
        }
        return new GuideNotificationPort.Notification(task.generation, task.actor, task.owner,
                task.session, task.request, failed ? failure : text, cards.size(), dev.openallay.util.Java8Collections.listCopyOf(previews.values()),
                reply, complete, failed, duration, fence, additionalTasks);
    }
    private boolean currentOwner(GuidePresentationEvent.Key key) {
        return service != null && service.presentationSessionOwner(key.sessionId())
                .filter(key.sessionOwner()::equals).isPresent();
    }
    private void pruneDeletedSessions() {
        receipts.entrySet().removeIf(entry -> !currentOwner(entry.getKey()));
        pending.entrySet().removeIf(entry -> service == null || service.presentationSessionOwner(entry.getKey().session)
                .filter(entry.getKey().owner::equals).isEmpty());
        owned.entrySet().removeIf(entry -> {
            if (service != null && service.presentationSessionOwner(entry.getKey().session)
                    .filter(entry.getKey().owner::equals).isPresent()) return false;
            entry.getValue().hide();
            return true;
        });
    }
    private void clearConnection() {
        owned.values().forEach(OwnedToast::hide);
        tests.forEach(OwnedToast::hide);
        owned.clear(); tests.clear(); pending.clear(); receipts.clear();
        visible = dev.openallay.util.Java8Collections.setOf(); visibleSession = null; windowActive = false; overflowTasks = 0;
        service = null; generation = null; actor = null;
    }
    private static String preview(String text) {
        int count = text.codePointCount(0, text.length());
        return count <= 512 ? text : text.substring(0, text.offsetByCodePoints(0, 512)) + "…";
    }
    private static final class Receipt {
        final GuidePresentationEvent event;
        boolean seen, delivered;
        Receipt(GuidePresentationEvent event) { this.event = event; }
    }
    @dev.openallay.value.ValueType(Task.ValueSchemaProvider.class)
private static final class Task {
    private final UUID generation;
    private final UUID actor;
    private final UUID owner;
    private final String session;
    private final UUID request;
    private Task(UUID generation, UUID actor, UUID owner, String session, UUID request) {
        this.generation = generation;
        this.actor = actor;
        this.owner = owner;
        this.session = session;
        this.request = request;
    }
    public UUID generation() { return generation; }
    public UUID actor() { return actor; }
    public UUID owner() { return owner; }
    public String session() { return session; }
    public UUID request() { return request; }
static Task of(GuidePresentationEvent.Key key) {
            return new Task(key.connectionGeneration(), key.actorId(), key.sessionOwner(), key.sessionId(), key.requestId());
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Task)) return false;
        Task that = (Task) other;
        return java.util.Objects.equals(generation, that.generation) && java.util.Objects.equals(actor, that.actor) && java.util.Objects.equals(owner, that.owner) && java.util.Objects.equals(session, that.session) && java.util.Objects.equals(request, that.request);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(generation);
        hash = 31 * hash + java.util.Objects.hashCode(actor);
        hash = 31 * hash + java.util.Objects.hashCode(owner);
        hash = 31 * hash + java.util.Objects.hashCode(session);
        hash = 31 * hash + java.util.Objects.hashCode(request);
        return hash;
    }
    @Override public String toString() { return "Task[generation=" + generation + ", actor=" + actor + ", owner=" + owner + ", session=" + session + ", request=" + request + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Task> schema() {
            return new dev.openallay.value.ValueSchema<>(Task.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Task>>asList(new dev.openallay.value.ValueSchema.Component<>(Task.class, "generation", Task::generation), new dev.openallay.value.ValueSchema.Component<>(Task.class, "actor", Task::actor), new dev.openallay.value.ValueSchema.Component<>(Task.class, "owner", Task::owner), new dev.openallay.value.ValueSchema.Component<>(Task.class, "session", Task::session), new dev.openallay.value.ValueSchema.Component<>(Task.class, "request", Task::request)), arguments -> new Task((UUID) arguments[0], (UUID) arguments[1], (UUID) arguments[2], (String) arguments[3], (UUID) arguments[4]));
        }
    }
}
    private static final class Batch {
        final Task task;
        final Instant first;
        Instant last;
        final Set<GuidePresentationEvent.Key> events = new LinkedHashSet<>();
        Batch(Task task, Instant now) { this.task = task; first = now; last = now; }
    }
    private static final class OwnedToast {
        final GuideNotificationPort.Fence fence;
        final GuideNotificationPort.Handle handle;
        final Set<GuidePresentationEvent.Key> events;
        OwnedToast(GuideNotificationPort.Fence fence, GuideNotificationPort.Handle handle,
                   Set<GuidePresentationEvent.Key> events) {
            this.fence = fence; this.handle = handle;
            this.events = new LinkedHashSet<>(events);
        }
        void retire() { fence.invalidate(); }
        void hide() { fence.invalidate(); handle.hide(); }
    }
}
