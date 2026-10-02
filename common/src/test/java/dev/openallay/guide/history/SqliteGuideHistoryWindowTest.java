package dev.openallay.guide.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import dev.openallay.agent.context.ContextBudget;
import dev.openallay.agent.context.ContextStructure;
import dev.openallay.guide.GuideFailure;
import dev.openallay.guide.GuideModelSelection;
import dev.openallay.guide.GuideRequestSnapshot;
import dev.openallay.guide.GuideRequestStatus;
import dev.openallay.guide.GuideSessionSnapshot;
import dev.openallay.guide.GuideTimelineEntry;
import dev.openallay.guide.GuideToolActivity;
import dev.openallay.guide.GuideToolMessage;
import dev.openallay.guide.GuideToolStatus;
import dev.openallay.guide.GuideTopology;
import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelRole;
import dev.openallay.model.ModelUsage;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SqliteGuideHistoryWindowTest {
    private static final GuideHistoryScope SCOPE = GuideHistoryScope.derive(
            UUID.fromString("cadf6fca-e522-4f31-8b00-c482431a236c"),
            GuideHistoryScope.Kind.MULTIPLAYER,
            "window.example");
    private static final Instant NOW = Instant.parse("2026-07-18T12:00:00Z");

    @TempDir Path temporary;

    @Test
    void standaloneControlUsageRestoresOnceAlongsideRequestQuotesAndClearsWithSession() {
        SqliteGuideHistoryStore store = store(temporary.resolve("control-usage.db"));
        GuideHistoryFixture original = partition(1, false, true);
        GuideHistoryFixture.seed(store, original);
        var quote = new dev.openallay.guide.GuideUsageSnapshot(100, 5, 25, 0, 1, 1, false, false,
                new java.math.BigDecimal("0.123"), false);
        var control = new dev.openallay.guide.GuideUsageSnapshot(200, 10, 0, 0, 2, 1, true, true,
                new java.math.BigDecimal("0.456"), true);
        GuideRequestSnapshot request = original.sessions().getFirst().requests().getFirst().withUsageProjection(quote);
        store.commit(new GuideHistoryCommit(SCOPE, List.of(
                new GuideHistoryMutation.UpsertRequest(0, request),
                new GuideHistoryMutation.UpsertSessionUsage("main", control))));
        var metadata = store.metadata(SCOPE).orElseThrow().sessions().getFirst();
        assertEquals(3, metadata.usage().actualCalls());
        assertEquals(300, metadata.usage().inputTokens());
        assertEquals(0, new java.math.BigDecimal("0.579").compareTo(metadata.usage().estimatedUsd()));
        assertEquals(control, metadata.controlUsage());
        assertTrue(metadata.usage().costIncomplete());
        store.commit(new GuideHistoryCommit(SCOPE, List.of(new GuideHistoryMutation.UpsertSession(
                "main", 0, GuideModelSelection.client("changed-profile")))));
        assertEquals(control, store.metadata(SCOPE).orElseThrow().sessions().getFirst().controlUsage());
        store.commit(new GuideHistoryCommit(SCOPE, List.of(new GuideHistoryMutation.ClearSession("main"))));
        assertEquals(dev.openallay.guide.GuideUsageSnapshot.empty(),
                store.metadata(SCOPE).orElseThrow().sessions().getFirst().usage());
    }

    @Test
    void completeSessionUsageRestoresWithoutReadingPagesOrRepricingAndSeparatesInheritance() {
        Path database = temporary.resolve("usage.db");
        SqliteGuideHistoryStore store = store(database);
        GuideHistoryFixture original = partition(3, false, true);
        GuideHistoryFixture.seed(store, original);
        var quote = new dev.openallay.guide.GuideUsageSnapshot(100, 5, 25, 10, 2, 2, false, false,
                new java.math.BigDecimal("0.123456789"), false);
        GuideRequestSnapshot first = original.sessions().getFirst().requests().getFirst().withUsageProjection(quote);
        GuideRequestSnapshot source = original.sessions().getFirst().requests().get(1);
        GuideRequestSnapshot inherited = new GuideRequestSnapshot(source.requestId(), source.sessionId(),
                source.topology(), source.userMessage(), source.timeline(), source.status(), source.sources(),
                source.usage(), source.retryAfterMillis(), source.failure(), source.createdAt(), source.updatedAt(),
                source.terminalAt(), source.modelSelection(), source.progress(), quote, first.requestId());
        GuideRequestSnapshot last = original.sessions().getFirst().requests().getLast()
                .withUsageProjection(dev.openallay.guide.GuideUsageSnapshot.unknown());
        store.commit(new GuideHistoryCommit(SCOPE, List.of(
                new GuideHistoryMutation.UpsertRequest(0, first),
                new GuideHistoryMutation.UpsertRequest(1, inherited),
                new GuideHistoryMutation.UpsertRequest(2, last))));
        var metadata = store(database).metadata(SCOPE).orElseThrow().sessions().getFirst();
        assertEquals(3, metadata.requestCount());
        assertEquals(2, metadata.usage().actualCalls());
        assertEquals(100, metadata.usage().inputTokens());
        assertEquals(quote.estimatedUsd(), metadata.usage().estimatedUsd());
        assertTrue(metadata.usage().costIncomplete());
        assertEquals(quote, metadata.inheritedUsage());
        var page = store.page(new GuideHistoryPageRequest(SCOPE, "main",
                GuideHistoryPageRequest.Direction.NEWEST, null, 1));
        assertEquals(1, page.requests().size());
        assertEquals(dev.openallay.guide.GuideUsageSnapshot.unknown(), page.requests().getFirst().usageProjection());
        assertEquals(metadata.usage(), store(database).metadata(SCOPE).orElseThrow().sessions().getFirst().usage());
    }

    @Test
    void metadataPagesAndContextUseIndependentBoundaries() throws Exception {
        Path database = temporary.resolve("history.db");
        SqliteGuideHistoryStore store = store(database);
        GuideHistoryFixture.seed(store, partition(5, false, true));
        List<ModelMessage> actualContext = List.of(
                ModelMessage.userText("actual previous question"),
                new ModelMessage(ModelRole.ASSISTANT,
                        List.of(new ModelContent.Text("actual previous answer"))));
        store.commit(new GuideHistoryCommit(SCOPE, List.of(
                new GuideHistoryMutation.ReplaceContext("main", actualContext))));

        GuideHistoryMetadata metadata = store.metadata(SCOPE).orElseThrow();
        assertEquals(5, metadata.sessions().getFirst().requestCount());
        assertEquals(0, metadata.sessions().getFirst().first().sequence());
        assertEquals(4, metadata.sessions().getFirst().last().sequence());

        GuideHistoryPage newest = store.page(new GuideHistoryPageRequest(
                SCOPE, "main", GuideHistoryPageRequest.Direction.NEWEST, null, 2));
        assertEquals(List.of("question-3", "question-4"), newest.requests().stream()
                .map(GuideRequestSnapshot::userMessage).toList());
        assertTrue(newest.hasEarlier());
        assertFalse(newest.hasLater());

        GuideHistoryPage earlier = store.page(new GuideHistoryPageRequest(
                SCOPE, "main", GuideHistoryPageRequest.Direction.BEFORE,
                newest.first(), 2));
        assertEquals(List.of("question-1", "question-2"), earlier.requests().stream()
                .map(GuideRequestSnapshot::userMessage).toList());
        GuideHistoryPage later = store.page(new GuideHistoryPageRequest(
                SCOPE, "main", GuideHistoryPageRequest.Direction.AFTER,
                earlier.last(), 1));
        assertEquals(List.of("question-3"), later.requests().stream()
                .map(GuideRequestSnapshot::userMessage).toList());

        GuideHistoryContextRequest contextRequest = new GuideHistoryContextRequest(
                SCOPE, "main", new ContextBudget(1_200, 100), 50, "same-model");
        GuideHistoryContextSeed context = store.context(contextRequest);
        assertEquals(actualContext, context.messages());
        assertTrue(context.estimatedTokens() <= contextRequest.availableHistoryTokens());
        ContextStructure.units(context.messages());

        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                var statement = connection.prepareStatement("""
                        update timeline_entries set payload_json = 'not-json'
                        where scope_id = ? and request_id = ?
                        """)) {
            statement.setString(1, SCOPE.scopeId());
            statement.setString(2, requestId(0).toString());
            statement.executeUpdate();
        }
        assertEquals(5, store.metadata(SCOPE).orElseThrow()
                .sessions().getFirst().requestCount());
        assertEquals(actualContext, store.context(contextRequest).messages());
        GuideHistoryException corrupt = assertThrows(
                GuideHistoryException.class,
                () -> store.page(new GuideHistoryPageRequest(
                        SCOPE, "main", GuideHistoryPageRequest.Direction.NEWEST,
                        null, 5)));
        assertEquals("history_corrupt", corrupt.code());
    }

    @Test
    void minimumCommitUpdatesOneRequestWithoutReplacingUnrelatedRows() throws Exception {
        Path database = temporary.resolve("incremental.db");
        SqliteGuideHistoryStore store = store(database);
        GuideHistoryFixture original = partition(2, false, true);
        GuideHistoryFixture.seed(store, original);
        long unrelatedRow = rowId(database, "requests", requestId(0));
        long unrelatedTimeline = rowId(database, "timeline_entries", requestId(0));
        GuideRequestSnapshot previous = original.sessions().getFirst().requests().get(1);
        GuideRequestSnapshot updated = new GuideRequestSnapshot(
                previous.requestId(), previous.sessionId(), previous.topology(),
                previous.userMessage(), previous.timeline(), previous.status(), previous.sources(),
                new ModelUsage(21, 8, 3), previous.retryAfterMillis(), previous.failure(),
                previous.createdAt(), NOW.plusSeconds(30), previous.terminalAt(),
                previous.modelSelection());
        GuideTimelineEntry.Tool previousTool =
                (GuideTimelineEntry.Tool) previous.timeline().get(1);
        GuideTimelineEntry.Tool updatedTool = new GuideTimelineEntry.Tool(
                previousTool.ordinal(),
                new GuideToolActivity(
                        previousTool.activity().invocationId(),
                        previousTool.activity().index(),
                        previousTool.activity().toolId(),
                        previousTool.activity().status(),
                        previousTool.activity().normalized(),
                        List.of(GuideToolMessage.of(
                                GuideToolMessage.Key.RESULT_COMPLETED,
                                "minecraft:updated_recipe")),
                        previousTool.activity().sources()));

        store.commit(new GuideHistoryCommit(SCOPE, List.of(
                new GuideHistoryMutation.UpsertPartition("main", NOW.plusSeconds(30)),
                new GuideHistoryMutation.UpsertRequest(1, updated),
                new GuideHistoryMutation.UpsertTimelineEntry(
                        updated.requestId(), updatedTool))));

        assertEquals(unrelatedRow, rowId(database, "requests", requestId(0)));
        assertEquals(unrelatedTimeline, rowId(database, "timeline_entries", requestId(0)));
        GuideHistoryPage page = store.page(new GuideHistoryPageRequest(
                SCOPE, "main", GuideHistoryPageRequest.Direction.NEWEST, null, 2));
        assertEquals(ModelUsage.empty(), page.requests().getFirst().usage());
        assertEquals(new ModelUsage(21, 8, 3), page.requests().getLast().usage());
        assertEquals(List.of(GuideToolMessage.of(
                        GuideToolMessage.Key.RESULT_COMPLETED,
                        "minecraft:updated_recipe")),
                ((GuideTimelineEntry.Tool) page.requests().getLast().timeline().get(1))
                        .activity().presentationMessages());
    }

    @Test
    void incrementalCommitCreatesACompleteFreshPartition() {
        SqliteGuideHistoryStore store = store(temporary.resolve("fresh-commit.db"));
        GuideRequestSnapshot request = partition(1, false, false)
                .sessions().getFirst().requests().getFirst();
        List<GuideHistoryMutation> mutations = new ArrayList<>();
        mutations.add(new GuideHistoryMutation.UpsertPartition("main", NOW));
        mutations.add(new GuideHistoryMutation.UpsertSession(
                "main", 0, GuideModelSelection.client("profile")));
        mutations.add(new GuideHistoryMutation.UpsertRequest(0, request));
        request.timeline().forEach(entry -> mutations.add(
                new GuideHistoryMutation.UpsertTimelineEntry(request.requestId(), entry)));
        mutations.add(new GuideHistoryMutation.ReplaceRequestSources(
                request.requestId(), request.sources()));

        store.commit(new GuideHistoryCommit(SCOPE, mutations));

        assertEquals(1, store.metadata(SCOPE).orElseThrow()
                .sessions().getFirst().requestCount());
        assertEquals(request, store.page(new GuideHistoryPageRequest(
                SCOPE, "main", GuideHistoryPageRequest.Direction.NEWEST, null, 1))
                .requests().getFirst());
    }

    @Test
    void failedIncrementalTransactionRollsBackAllMutations() {
        Path database = temporary.resolve("rollback.db");
        SqliteGuideHistoryStore baseline = store(database);
        GuideHistoryFixture original = partition(1, false, false);
        GuideHistoryFixture.seed(baseline, original);
        GuideRequestSnapshot previous = original.sessions().getFirst().requests().getFirst();
        GuideRequestSnapshot failed = new GuideRequestSnapshot(
                previous.requestId(), previous.sessionId(), previous.topology(),
                previous.userMessage(), previous.timeline(), GuideRequestStatus.FAILED,
                previous.sources(), previous.usage(), null,
                new GuideFailure("test_failure", "should roll back"),
                previous.createdAt(), NOW.plusSeconds(20), NOW.plusSeconds(20),
                previous.modelSelection());
        SqliteGuideHistoryStore injected = new SqliteGuideHistoryStore(
                database,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new GuideHistoryCodec(),
                mutation -> {
                    if (mutation == SqliteGuideHistoryStore.Mutation.COMMIT) {
                        throw new java.sql.SQLException("injected");
                    }
                });

        assertThrows(GuideHistoryException.class, () -> injected.commit(
                new GuideHistoryCommit(SCOPE, List.of(
                        new GuideHistoryMutation.UpsertPartition(
                                "main", NOW.plusSeconds(20)),
                        new GuideHistoryMutation.UpsertRequest(0, failed)))));

        GuideRequestSnapshot restored = baseline.page(new GuideHistoryPageRequest(
                SCOPE, "main", GuideHistoryPageRequest.Direction.NEWEST, null, 1))
                .requests().getFirst();
        assertEquals(GuideRequestStatus.COMPLETED, restored.status());
        assertNotEquals(NOW.plusSeconds(20), baseline.metadata(SCOPE).orElseThrow().updatedAt());
    }

    @Test
    void metadataRecoveryInterruptsOrphanWithoutRepeatingIt() {
        SqliteGuideHistoryStore store = store(temporary.resolve("recovery.db"));
        GuideHistoryFixture.seed(store, partition(1, true, false));

        store.metadata(SCOPE).orElseThrow();
        GuideRequestSnapshot recovered = store.page(new GuideHistoryPageRequest(
                SCOPE, "main", GuideHistoryPageRequest.Direction.NEWEST, null, 1))
                .requests().getFirst();

        assertEquals(GuideRequestStatus.INTERRUPTED, recovered.status());
        assertEquals("request_interrupted", recovered.failure().code());
        assertFalse(((GuideTimelineEntry.Assistant) recovered.timeline().getFirst()).streaming());
    }

    @Test
    void largeUiPartitionDoesNotCreateModelContextFromDisplayRows() {
        SqliteGuideHistoryStore store = store(temporary.resolve("large.db"));
        GuideHistoryFixture.seed(store, partition(256, false, false));

        assertEquals(256, store.metadata(SCOPE).orElseThrow()
                .sessions().getFirst().requestCount());
        GuideHistoryPage page = store.page(new GuideHistoryPageRequest(
                SCOPE, "main", GuideHistoryPageRequest.Direction.NEWEST, null, 7));
        assertEquals(7, page.requests().size());
        GuideHistoryContextRequest request = new GuideHistoryContextRequest(
                SCOPE, "main", new ContextBudget(700, 100), 50, "same-model");
        GuideHistoryContextSeed seed = store.context(request);
        assertTrue(seed.messages().isEmpty());
        assertEquals(0, seed.estimatedTokens());
    }

    private SqliteGuideHistoryStore store(Path database) {
        return new SqliteGuideHistoryStore(
                database, Clock.fixed(NOW, ZoneOffset.UTC), new GuideHistoryCodec());
    }

    private static GuideHistoryFixture partition(
            int count, boolean activeLast, boolean withTool) {
        List<GuideRequestSnapshot> requests = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            boolean active = activeLast && index == count - 1;
            List<GuideTimelineEntry> timeline = withTool && index == count - 1
                    ? toolTimeline(index, active)
                    : List.of(new GuideTimelineEntry.Assistant(
                            0, "answer-" + index, active, List.of()));
            requests.add(new GuideRequestSnapshot(
                    requestId(index),
                    "main",
                    GuideTopology.CLIENT_LOCAL,
                    "question-" + index,
                    timeline,
                    active ? GuideRequestStatus.MODEL_WAIT : GuideRequestStatus.COMPLETED,
                    List.of(),
                    ModelUsage.empty(),
                    null,
                    null,
                    NOW.plusSeconds(index),
                    NOW.plusSeconds(index + 1),
                    active ? null : NOW.plusSeconds(index + 1),
                    GuideModelSelection.client("profile")));
        }
        return new GuideHistoryFixture(
                SCOPE,
                "main",
                List.of(new GuideSessionSnapshot(
                        "main", List.of(), requests, List.of(),
                        GuideModelSelection.client("profile"))),
                NOW.plusSeconds(count + 1));
    }

    private static List<GuideTimelineEntry> toolTimeline(int index, boolean active) {
        GuideToolActivity activity = new GuideToolActivity(
                "call-1", 0, "openallay:get_recipe", GuideToolStatus.SUCCEEDED,
                new JsonObject(), List.of(GuideToolMessage.of(
                        GuideToolMessage.Key.RESULT_COMPLETED,
                        "minecraft:recipe_" + index)), List.of());
        return List.of(
                new GuideTimelineEntry.Assistant(0, "checking", false, List.of()),
                new GuideTimelineEntry.Tool(1, activity),
                new GuideTimelineEntry.Assistant(2, "answer-" + index, active, List.of()));
    }

    private static UUID requestId(int index) {
        return new UUID(0, index + 1L);
    }

    private static long rowId(Path database, String table, UUID requestId) throws Exception {
        if (!table.equals("requests") && !table.equals("timeline_entries")) {
            throw new IllegalArgumentException("unsupported table");
        }
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                var query = connection.prepareStatement(
                        "select rowid from " + table + " where scope_id = ? and request_id = ?")) {
            query.setString(1, SCOPE.scopeId());
            query.setString(2, requestId.toString());
            try (var result = query.executeQuery()) {
                return result.next() ? result.getLong(1) : -1;
            }
        }
    }
}
