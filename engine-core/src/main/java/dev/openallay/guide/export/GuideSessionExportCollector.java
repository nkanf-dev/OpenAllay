package dev.openallay.guide.export;

import dev.openallay.guide.GuideRequestSnapshot;
import dev.openallay.guide.GuideTimelineEntry;
import dev.openallay.guide.history.GuideHistoryAccess;
import dev.openallay.guide.history.GuideHistoryCursor;
import dev.openallay.guide.history.GuideHistoryException;
import dev.openallay.guide.history.GuideHistoryPage;
import dev.openallay.guide.history.GuideHistoryPageRequest;
import dev.openallay.guide.history.GuideHistoryScope;
import dev.openallay.model.ModelMessage;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Reads a complete durable session without mutating the GUI's history window. */
public final class GuideSessionExportCollector {
    private static final int PAGE_BATCH = 128;

    public record SequencedRequest(long sequence, GuideRequestSnapshot request) {
        public SequencedRequest {
            if (sequence < 0) throw new IllegalArgumentException("request sequence is negative");
            Objects.requireNonNull(request, "request");
        }
    }

    private final GuideHistoryScope scope;
    private final GuideHistoryAccess history;

    public GuideSessionExportCollector(GuideHistoryScope scope, GuideHistoryAccess history) {
        if ((scope == null) != (history == null)) {
            throw new IllegalArgumentException("history scope and access must be configured together");
        }
        this.scope = scope;
        this.history = history;
    }

    public CompletableFuture<GuideSessionExportSnapshot> collect(
            String sessionId,
            List<SequencedRequest> captured,
            Map<UUID, List<ModelMessage>> originals,
            long upperSequence,
            Instant capturedAt) {
        requireSession(sessionId);
        if (upperSequence < -1) throw new IllegalArgumentException("invalid export sequence boundary");
        captured = List.copyOf(captured);
        Map<UUID, List<ModelMessage>> copiedOriginals = new LinkedHashMap<>();
        originals.forEach((id, messages) -> copiedOriginals.put(id, List.copyOf(messages)));
        Map<UUID, List<ModelMessage>> liveOriginals = Map.copyOf(copiedOriginals);
        Objects.requireNonNull(capturedAt, "capturedAt");
        for (SequencedRequest value : captured) {
            if (!value.request().sessionId().equals(sessionId)) {
                throw new IllegalArgumentException("captured request belongs to another session");
            }
            if (value.sequence() > upperSequence) {
                throw new IllegalArgumentException("captured request exceeds export sequence boundary");
            }
        }
        TreeMap<Long, GuideRequestSnapshot> ordered = new TreeMap<>();
        putAll(ordered, captured);
        if (history == null) {
            return CompletableFuture.completedFuture(
                    snapshot(sessionId, ordered, liveOriginals, capturedAt));
        }
        // The durable read can finish after the live request changes. Overlay the invocation-time
        // capture only after paging so the exported point in time wins by sequence and identity.
        List<SequencedRequest> live = captured;
        return loadEarlier(sessionId, null, ordered, new HashSet<>(), upperSequence)
                .thenCompose(ignored -> {
                    putAll(ordered, live);
                    return loadOriginals(ordered, liveOriginals);
                })
                .thenApply(contexts -> snapshot(sessionId, ordered, contexts, capturedAt));
    }

    private CompletableFuture<Map<UUID, List<ModelMessage>>> loadOriginals(
            Map<Long, GuideRequestSnapshot> ordered,
            Map<UUID, List<ModelMessage>> live) {
        Map<UUID, List<ModelMessage>> contexts = new LinkedHashMap<>(live);
        CompletableFuture<Void> loaded = CompletableFuture.completedFuture(null);
        for (GuideRequestSnapshot request : ordered.values()) {
            UUID id = request.requestId();
            if (!live.containsKey(id)) {
                loaded = loaded.thenCompose(ignored -> history.requestContext(scope, id)
                        .thenAccept(messages -> contexts.put(id, List.copyOf(messages))));
            }
        }
        return loaded.handle((ignored, failure) -> {
            if (failure != null) {
                throw new java.util.concurrent.CompletionException(exportFailure(failure));
            }
            return Map.copyOf(contexts);
        });
    }

    private CompletableFuture<Void> loadEarlier(
            String sessionId,
            GuideHistoryCursor before,
            TreeMap<Long, GuideRequestSnapshot> ordered,
            Set<GuideHistoryCursor> visited,
            long upperSequence) {
        GuideHistoryPageRequest.Direction direction = before == null
                ? GuideHistoryPageRequest.Direction.NEWEST
                : GuideHistoryPageRequest.Direction.BEFORE;
        GuideHistoryPageRequest request = new GuideHistoryPageRequest(
                scope, sessionId, direction, before, PAGE_BATCH);
        CompletableFuture<GuideHistoryPage> loading;
        try {
            loading = Objects.requireNonNull(history.page(request), "history page future");
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(exportFailure(failure));
        }
        return loading.handle((page, failure) -> {
                    if (failure != null) throw new java.util.concurrent.CompletionException(
                            exportFailure(failure));
                    validatePage(sessionId, before, page);
                    if (page.first() != null) {
                        long sequence = page.first().sequence();
                        for (GuideRequestSnapshot value : page.requests()) {
                            long currentSequence = sequence++;
                            if (currentSequence > upperSequence) continue;
                            GuideRequestSnapshot previous = ordered.put(currentSequence, value);
                            if (previous != null
                                    && !previous.requestId().equals(value.requestId())) {
                                throw new java.util.concurrent.CompletionException(exportFailure(
                                        new IllegalStateException("history sequence collision")));
                            }
                        }
                    }
                    return page;
                })
                .thenCompose(page -> {
                    if (!page.hasEarlier()) return CompletableFuture.completedFuture(null);
                    GuideHistoryCursor next = page.first();
                    if (next == null || !visited.add(next)
                            || before != null && next.sequence() >= before.sequence()) {
                        return CompletableFuture.failedFuture(exportFailure(
                                new IllegalStateException("history cursor did not progress")));
                    }
                    return loadEarlier(sessionId, next, ordered, visited, upperSequence);
                });
    }

    private static void validatePage(
            String sessionId, GuideHistoryCursor before, GuideHistoryPage page) {
        if (page == null || !page.sessionId().equals(sessionId)) {
            throw new java.util.concurrent.CompletionException(exportFailure(
                    new IllegalStateException("history page belongs to another session")));
        }
        if (before != null && page.last() != null && page.last().sequence() >= before.sequence()) {
            throw new java.util.concurrent.CompletionException(exportFailure(
                    new IllegalStateException("history page crossed its cursor")));
        }
    }

    private static void putAll(
            Map<Long, GuideRequestSnapshot> target, List<SequencedRequest> requests) {
        Set<UUID> identities = new HashSet<>();
        for (SequencedRequest value : requests) {
            if (!identities.add(value.request().requestId())) {
                throw new IllegalArgumentException("duplicate captured request identity");
            }
            GuideRequestSnapshot previous = target.put(value.sequence(), value.request());
            if (previous != null && !previous.requestId().equals(value.request().requestId())) {
                throw new IllegalArgumentException("captured history sequence collision");
            }
        }
    }

    private static GuideSessionExportSnapshot snapshot(
            String sessionId,
            TreeMap<Long, GuideRequestSnapshot> ordered,
            Map<UUID, List<ModelMessage>> originals,
            Instant capturedAt) {
        return new GuideSessionExportSnapshot(
                sessionId,
                ordered.values().stream().map(request -> project(
                        request, originals.getOrDefault(request.requestId(), List.of()))).toList(),
                capturedAt);
    }

    private static GuideSessionExportSnapshot.Request project(
            GuideRequestSnapshot request, List<ModelMessage> originalContext) {
        List<GuideSessionExportSnapshot.Entry> timeline = request.timeline().stream()
                .map(GuideSessionExportCollector::projectEntry)
                .toList();
        return new GuideSessionExportSnapshot.Request(
                request.requestId(), request.createdAt(), request.status(), request.userMessage(),
                timeline, originalContext, request.failure());
    }

    private static GuideSessionExportSnapshot.Entry projectEntry(GuideTimelineEntry entry) {
        Objects.requireNonNull(entry);
        if (entry instanceof GuideTimelineEntry.User user) {
            return new GuideSessionExportSnapshot.Entry.User(user.messageId(), user.text());
        } else if (entry instanceof GuideTimelineEntry.Assistant assistant) {
            return new GuideSessionExportSnapshot.Entry.Assistant(
                    assistant.text(), assistant.streaming());
        } else if (entry instanceof GuideTimelineEntry.Tool tool) {
            return new GuideSessionExportSnapshot.Entry.Tool(
                    tool.activity().invocationId(), tool.activity().toolId(),
                    tool.activity().status());
        }
        throw new IncompatibleClassChangeError();
    }

    private static GuideHistoryException exportFailure(Throwable failure) {
        Throwable cause = unwrap(failure);
        return cause instanceof GuideHistoryException historyFailure
                && (historyFailure.code().equals("history_export_failed")
                        || historyFailure.code().equals("history_layout_unsupported"))
                ? historyFailure
                : new GuideHistoryException(
                        "history_export_failed",
                        "Unable to read the complete guide session for export",
                        cause);
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof java.util.concurrent.CompletionException
                        || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static void requireSession(String sessionId) {
        if (sessionId == null || !sessionId.matches("[a-zA-Z0-9_.-]+")) {
            throw new IllegalArgumentException("invalid export session ID");
        }
    }
}
