package dev.openallay.guide.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonPrimitive;
import dev.openallay.guide.GuideModelSelection;
import dev.openallay.guide.GuideRequestSnapshot;
import dev.openallay.guide.GuideRequestStatus;
import dev.openallay.guide.GuideTimelineEntry;
import dev.openallay.guide.GuideTopology;
import dev.openallay.guide.history.GuideHistoryAccess;
import dev.openallay.guide.history.GuideHistoryActivity;
import dev.openallay.guide.history.GuideHistoryCommit;
import dev.openallay.guide.history.GuideHistoryDeleteScope;
import dev.openallay.guide.history.GuideHistoryPage;
import dev.openallay.guide.history.GuideHistoryPageRequest;
import dev.openallay.guide.history.GuideHistoryScope;
import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelRole;
import dev.openallay.model.ModelUsage;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

final class GuideSessionExportCollectorTest {
    private static final UUID ACTOR = UUID.fromString("b6c595a7-9e33-4ad2-af63-126503472b23");
    private static final GuideHistoryScope SCOPE = GuideHistoryScope.derive(
            ACTOR, GuideHistoryScope.Kind.SINGLEPLAYER, "world");
    private static final Instant NOW = Instant.parse("2026-07-19T12:00:00Z");

    @Test
    void readsEveryEarlierPageAndOverlaysTheInvocationTimeLiveRequest() {
        GuideRequestSnapshot durableFour = request(4, "durable-four", true);
        GuideRequestSnapshot liveFour = request(4, "live-four", false);
        PagingHistory history = new PagingHistory(List.of(
                page(List.of(request(3, "three", true), durableFour), 3, 4, true),
                page(List.of(request(1, "one", true), request(2, "two", true)), 1, 2, false)));
        GuideSessionExportCollector collector = new GuideSessionExportCollector(SCOPE, history);

        GuideSessionExportSnapshot export = collector.collect(
                "main",
                List.of(new GuideSessionExportCollector.SequencedRequest(4, liveFour)),
                Map.of(), 4, NOW).join();

        assertEquals(List.of("one", "two", "three", "live-four"), export.requests().stream()
                .map(GuideSessionExportSnapshot.Request::userMessage).toList());
        assertEquals(2, history.requests.size());
        assertEquals(GuideHistoryPageRequest.Direction.NEWEST,
                history.requests.get(0).direction());
        assertEquals(GuideHistoryPageRequest.Direction.BEFORE,
                history.requests.get(1).direction());
    }

    @Test
    void historyFailureIsMappedToOneClosedExportFailure() {
        GuideHistoryAccess history = new PagingHistory(List.of()) {
            @Override public CompletableFuture<GuideHistoryPage> page(GuideHistoryPageRequest request) {
                return CompletableFuture.failedFuture(new IllegalStateException("database path"));
            }
        };
        GuideSessionExportCollector collector = new GuideSessionExportCollector(SCOPE, history);

        Throwable failure = org.junit.jupiter.api.Assertions.assertThrows(
                java.util.concurrent.CompletionException.class,
                () -> collector.collect("main", List.of(), Map.of(), 3, NOW).join()).getCause();

        dev.openallay.guide.history.GuideHistoryException mapped = assertInstanceOf(
                dev.openallay.guide.history.GuideHistoryException.class, failure);
        assertEquals("history_export_failed", mapped.code());
        assertTrue(mapped.getMessage().contains("complete guide session"));
    }

    @Test
    void unsupportedLayoutKeepsItsActionableFailureAcrossTheExportBoundary() {
        var unsupported = new dev.openallay.guide.history.GuideHistoryException(
                "history_layout_unsupported", "The original database was not changed; preserve it");
        GuideHistoryAccess history = new PagingHistory(List.of()) {
            @Override public CompletableFuture<GuideHistoryPage> page(GuideHistoryPageRequest request) {
                return CompletableFuture.failedFuture(unsupported);
            }
        };
        Throwable failure = org.junit.jupiter.api.Assertions.assertThrows(
                java.util.concurrent.CompletionException.class,
                () -> new GuideSessionExportCollector(SCOPE, history)
                        .collect("main", List.of(), Map.of(), 3, NOW).join()).getCause();
        org.junit.jupiter.api.Assertions.assertSame(unsupported, failure);
    }

    @Test
    void nonProgressingEarlierCursorFailsInsteadOfLooping() {
        GuideHistoryPage newest = page(List.of(request(3, "three", true)), 3, 3, true);
        GuideHistoryPage repeated = page(List.of(request(3, "three", true)), 3, 3, true);
        GuideSessionExportCollector collector = new GuideSessionExportCollector(
                SCOPE, new PagingHistory(List.of(newest, repeated)));

        Throwable failure = org.junit.jupiter.api.Assertions.assertThrows(
                java.util.concurrent.CompletionException.class,
                () -> collector.collect("main", List.of(), Map.of(), 3, NOW).join()).getCause();

        assertEquals("history_export_failed",
                assertInstanceOf(
                        dev.openallay.guide.history.GuideHistoryException.class,
                        failure).code());
    }

    @Test
    void exportUsesRequestOriginalsRatherThanActiveCompactionAndLiveCaptureWins() {
        GuideRequestSnapshot first = request(1, "first", true);
        GuideRequestSnapshot second = request(2, "second", false);
        List<ModelMessage> original = List.of(new ModelMessage(ModelRole.USER, List.of(
                new ModelContent.ToolResult("error-1", new JsonPrimitive(
                        "status: failure\ncode: javascript_error\nmessage: TypeError"), true))));
        List<ModelMessage> captured = List.of(ModelMessage.userText("point-in-time result"));
        List<UUID> loaded = new ArrayList<>();
        PagingHistory history = new PagingHistory(List.of(page(List.of(first, second), 1, 2, false))) {
            @Override public CompletableFuture<List<ModelMessage>> requestContext(
                    GuideHistoryScope scope, UUID requestId) {
                loaded.add(requestId);
                return CompletableFuture.completedFuture(original);
            }
        };
        GuideSessionExportSnapshot exported = new GuideSessionExportCollector(SCOPE, history)
                .collect("main", List.of(new GuideSessionExportCollector.SequencedRequest(2, second)),
                        Map.of(second.requestId(), captured), 2, NOW).join();
        assertEquals(original, exported.requests().get(0).originalContext());
        assertEquals(captured, exported.requests().get(1).originalContext());
        assertEquals(List.of(first.requestId()), loaded);
    }

    @Test
    void missingOriginalReadFailsExportInsteadOfSilentlyDroppingErrors() {
        PagingHistory history = new PagingHistory(List.of(page(
                List.of(request(1, "first", true)), 1, 1, false))) {
            @Override public CompletableFuture<List<ModelMessage>> requestContext(
                    GuideHistoryScope scope, UUID requestId) {
                return CompletableFuture.failedFuture(new IllegalStateException("private path"));
            }
        };
        Throwable failure = org.junit.jupiter.api.Assertions.assertThrows(
                java.util.concurrent.CompletionException.class,
                () -> new GuideSessionExportCollector(SCOPE, history)
                        .collect("main", List.of(), Map.of(), 3, NOW).join()).getCause();
        assertEquals("history_export_failed", assertInstanceOf(
                dev.openallay.guide.history.GuideHistoryException.class, failure).code());
    }

    @Test
    void futureRequestsCommittedAfterExportCaptureAreExcludedBySessionSequence() {
        GuideRequestSnapshot first = request(1, "captured", true);
        GuideRequestSnapshot later = request(2, "after-click", true);
        var history = new PagingHistory(List.of(page(List.of(first, later), 1, 2, false)));
        var exported = new GuideSessionExportCollector(SCOPE, history)
                .collect("main", List.of(), Map.of(), 1, NOW).join();
        assertEquals(List.of("captured"), exported.requests().stream()
                .map(GuideSessionExportSnapshot.Request::userMessage).toList());
    }

    @Test
    void explicitEmptyLiveOriginalWinsOverLaterDurableResult() {
        GuideRequestSnapshot live = request(1, "active", false);
        var history = new PagingHistory(List.of(page(List.of(live), 1, 1, false))) {
            @Override public CompletableFuture<List<ModelMessage>> requestContext(
                    GuideHistoryScope scope, UUID requestId) {
                throw new AssertionError("live capture must not read a later durable original");
            }
        };
        var exported = new GuideSessionExportCollector(SCOPE, history)
                .collect("main", List.of(new GuideSessionExportCollector.SequencedRequest(1, live)),
                        Map.of(live.requestId(), List.of()), 1, NOW).join();
        assertTrue(exported.requests().getFirst().originalContext().isEmpty());
    }

    private static GuideHistoryPage page(
            List<GuideRequestSnapshot> requests,
            long first,
            long last,
            boolean earlier) {
        return new GuideHistoryPage(
                "main",
                requests,
                new dev.openallay.guide.history.GuideHistoryCursor(
                        first, requests.getFirst().requestId()),
                new dev.openallay.guide.history.GuideHistoryCursor(
                        last, requests.getLast().requestId()),
                earlier,
                false);
    }

    private static GuideRequestSnapshot request(int sequence, String question, boolean terminal) {
        UUID id = UUID.nameUUIDFromBytes(("export-" + sequence).getBytes(StandardCharsets.UTF_8));
        Instant at = NOW.plusSeconds(sequence);
        return new GuideRequestSnapshot(
                id,
                "main",
                GuideTopology.CLIENT_LOCAL,
                question,
                List.of(new GuideTimelineEntry.Assistant(0, "answer-" + sequence, !terminal, List.of())),
                terminal ? GuideRequestStatus.COMPLETED : GuideRequestStatus.MODEL_WAIT,
                List.of(),
                ModelUsage.empty(),
                null,
                null,
                at,
                at,
                terminal ? at : null,
                GuideModelSelection.client("default"));
    }

    private static class PagingHistory implements GuideHistoryAccess {
        private final List<GuideHistoryPage> pages;
        private final List<GuideHistoryPageRequest> requests = new ArrayList<>();

        private PagingHistory(List<GuideHistoryPage> pages) {
            this.pages = new ArrayList<>(pages);
        }

        @Override public CompletableFuture<GuideHistoryPage> page(GuideHistoryPageRequest request) {
            requests.add(request);
            return CompletableFuture.completedFuture(pages.removeFirst());
        }
        @Override public CompletableFuture<List<ModelMessage>> requestContext(
                GuideHistoryScope scope, UUID requestId) {
            return CompletableFuture.completedFuture(List.of());
        }
        @Override public CompletableFuture<Void> commit(GuideHistoryCommit commit) {
            return CompletableFuture.completedFuture(null);
        }
        @Override public CompletableFuture<Void> delete(GuideHistoryDeleteScope scope) {
            return CompletableFuture.completedFuture(null);
        }
        @Override public CompletableFuture<Void> resetDatabase() {
            return CompletableFuture.completedFuture(null);
        }
        @Override public CompletableFuture<Void> flush() {
            return CompletableFuture.completedFuture(null);
        }
        @Override public GuideHistoryActivity activity() {
            return new GuideHistoryActivity(0, false);
        }
    }
}
