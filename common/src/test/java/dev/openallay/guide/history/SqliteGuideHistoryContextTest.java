package dev.openallay.guide.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.openallay.agent.context.ContextBudget;
import dev.openallay.agent.context.ContextCheckpoint;
import dev.openallay.agent.context.ContextSourceHash;
import dev.openallay.agent.context.ContextStructure;
import dev.openallay.agent.context.ModelContextCodec;
import dev.openallay.agent.context.Utf8ContextTokenEstimator;
import dev.openallay.guide.GuideModelSelection;
import dev.openallay.guide.GuideRequestSnapshot;
import dev.openallay.guide.GuideRequestStatus;
import dev.openallay.guide.GuideSessionSnapshot;
import dev.openallay.guide.GuideTimelineEntry;
import dev.openallay.guide.GuideTopology;
import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelRole;
import dev.openallay.model.ModelUsage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Model transcript persistence must not depend on player-visible timeline projections. */
final class SqliteGuideHistoryContextTest {
    private static final UUID ACTOR = UUID.fromString("9ee4aa88-f882-4556-97d1-cc7313753a93");
    private static final UUID REQUEST = UUID.fromString("e35bab3a-0841-43aa-99d8-b7d5e9b25f8b");
    private static final Instant NOW = Instant.parse("2026-09-30T12:00:00Z");
    private static final GuideHistoryScope SCOPE = scope("context.example");
    private static final String MODEL = "test:model";

    @TempDir Path temporary;

    @Test
    void roundTripsActualCallsResultsErrorsAndOriginalRequestSeparately() throws Exception {
        Path database = temporary.resolve("round-trip.db");
        SqliteGuideHistoryStore writer = store(database);
        GuideHistoryFixture.seed(writer, partition(SCOPE, false));
        List<ModelMessage> original = transcript(true);
        List<ModelMessage> current = List.of(ModelMessage.userText("Derived compacted context"));
        writer.commit(new GuideHistoryCommit(SCOPE, List.of(
                new GuideHistoryMutation.ReplaceContext("main", current),
                new GuideHistoryMutation.ReplaceRequestContext(REQUEST, original))));

        SqliteGuideHistoryStore reopened = store(database);
        assertEquals(current, reopened.context(contextRequest(SCOPE, 4_000)).messages());
        assertEquals(ModelContextCodec.safe(original), reopened.requestContext(SCOPE, REQUEST));
        assertEquals(3, ContextStructure.units(reopened.requestContext(SCOPE, REQUEST)).size());
        ModelContent.ToolUse call = (ModelContent.ToolUse)
                reopened.requestContext(SCOPE, REQUEST).get(1).content().getFirst();
        assertEquals("openallay:load_skills", call.name());
        assertEquals(1, call.input().getAsJsonArray("skills").size());
        assertEquals("openallay:crafting", call.input().getAsJsonArray("skills").get(0).getAsString());
        ModelContent.ToolResult result = (ModelContent.ToolResult)
                reopened.requestContext(SCOPE, REQUEST).get(2).content().getFirst();
        assertTrue(result.error());
        assertEquals("skill_missing", result.value().getAsJsonObject().get("code").getAsString());
        assertEquals("Skill openallay:crafting was not found",
                result.value().getAsJsonObject().get("message").getAsString());
        assertEquals(NOW.toString(),
                result.value().getAsJsonObject().get("capturedAt").getAsString());
        assertFalse(reopened.requestContext(SCOPE, REQUEST).stream().flatMap(
                message -> message.content().stream()).anyMatch(ModelContent.Reasoning.class::isInstance));
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                var resultSet = connection.createStatement().executeQuery(
                        "select payload_json from request_model_context")) {
            assertTrue(resultSet.next());
            assertFalse(resultSet.getString(1).contains("private-reasoning"));
            assertFalse(resultSet.getString(1).contains("private-signature"));
            assertFalse(resultSet.getString(1).contains("durableProjection"));
        }
    }

    @Test
    void knownCredentialBoundarySurvivesDurableContextAndTextExport() throws Exception {
        String secret = "opaqueSavedCredentialAlpha";
        var redactor = new dev.openallay.agent.KnownSecretRedactor(java.util.Set.of(secret));
        JsonObject input = new JsonObject();
        input.addProperty("source", "return '" + secret + "';");
        input.addProperty(secret, "ordinary");
        JsonObject value = new JsonObject();
        value.addProperty(secret, secret);
        value.addProperty("fact", 42);
        List<ModelMessage> safe = redactor.messages(List.of(ModelMessage.userText("question"),
                new ModelMessage(ModelRole.ASSISTANT, List.of(
                        new ModelContent.Reasoning("private-reasoning", "private-signature"),
                        new ModelContent.ToolUse("call", "openallay:run_javascript", input))),
                new ModelMessage(ModelRole.USER, List.of(new ModelContent.ToolResult("call", value, false)))));
        Path database = temporary.resolve("known-credential.db");
        SqliteGuideHistoryStore writer = store(database);
        GuideHistoryFixture.seed(writer, partition(SCOPE, false));
        writer.commit(new GuideHistoryCommit(SCOPE, List.of(
                new GuideHistoryMutation.ReplaceContext("main", safe),
                new GuideHistoryMutation.ReplaceRequestContext(REQUEST, safe))));
        SqliteGuideHistoryStore reopened = store(database);
        List<ModelMessage> original = reopened.requestContext(SCOPE, REQUEST);
        assertEquals(safe, original);
        assertEquals(safe, reopened.context(contextRequest(SCOPE, 4000)).messages());
        var snapshot = new dev.openallay.guide.export.GuideSessionExportSnapshot("main", List.of(
                new dev.openallay.guide.export.GuideSessionExportSnapshot.Request(REQUEST, NOW,
                        GuideRequestStatus.COMPLETED, "question", List.of(), original, null)), NOW);
        var exported = new dev.openallay.client.gui.export.GuideSessionExporter(temporary).export(snapshot);
        String text = Files.readString(temporary.resolve("openallay/exports").resolve(exported.filename()));
        assertFalse(text.contains(secret));
        assertFalse(text.contains("private-reasoning"));
        assertFalse(text.contains("private-signature"));
        assertTrue(text.contains("42"));
        assertTrue(text.contains("[REDACTED]"));
    }

    @Test
    void absentSnapshotNeverReconstructsDisplayHistory() {
        SqliteGuideHistoryStore store = store(temporary.resolve("no-snapshot.db"));
        assertTrue(store.context(contextRequest(SCOPE, 4_000)).messages().isEmpty());
        GuideHistoryFixture.seed(store, partition(SCOPE, false));

        GuideHistoryContextSeed seed = store.context(contextRequest(SCOPE, 4_000));
        assertTrue(seed.messages().isEmpty());
        assertTrue(seed.checkpoints().isEmpty());
        assertEquals(0, seed.estimatedTokens());
        assertTrue(store.requestContext(SCOPE, REQUEST).isEmpty());
        assertEquals("UI answer is not model context", store.page(new GuideHistoryPageRequest(
                SCOPE, "main", GuideHistoryPageRequest.Direction.NEWEST, null, 1))
                .requests().getFirst().assistantText());
    }

    @Test
    void overBudgetSnapshotKeepsAllStructuralUnitsForAgentCompaction() {
        SqliteGuideHistoryStore store = store(temporary.resolve("over-budget.db"));
        GuideHistoryFixture.seed(store, partition(SCOPE, false));
        List<ModelMessage> messages = transcript(false);
        store.commit(new GuideHistoryCommit(SCOPE, List.of(
                new GuideHistoryMutation.ReplaceContext("main", messages))));
        GuideHistoryContextRequest request = contextRequest(SCOPE, 240);

        GuideHistoryContextSeed seed = store.context(request);

        assertEquals(messages, seed.messages());
        assertEquals(new Utf8ContextTokenEstimator().estimate("", messages, List.of()),
                seed.estimatedTokens());
        assertTrue(seed.estimatedTokens() > request.availableHistoryTokens());
        assertEquals(3, ContextStructure.units(seed.messages()).size());
        assertTrue(((ModelContent.ToolResult) seed.messages().get(2).content().getFirst()).error());
    }

    @Test
    void replacingCurrentSnapshotLeavesOriginalRequestsAndOtherScopesIntact() {
        SqliteGuideHistoryStore store = store(temporary.resolve("isolated.db"));
        GuideHistoryScope other = scope("other.example");
        GuideHistoryFixture.seed(store, partition(SCOPE, false));
        GuideHistoryFixture.seed(store, partition(other, false));
        List<ModelMessage> original = transcript(false);
        store.commit(new GuideHistoryCommit(SCOPE, List.of(
                new GuideHistoryMutation.ReplaceContext("main", original),
                new GuideHistoryMutation.ReplaceRequestContext(REQUEST, original))));
        List<ModelMessage> otherMessages = List.of(ModelMessage.userText("other scope"));
        store.commit(new GuideHistoryCommit(other, List.of(
                new GuideHistoryMutation.ReplaceContext("main", otherMessages),
                new GuideHistoryMutation.ReplaceRequestContext(REQUEST, otherMessages))));
        List<ModelMessage> replacement = List.of(ModelMessage.userText("current projection"));

        store.commit(new GuideHistoryCommit(SCOPE, List.of(
                new GuideHistoryMutation.ReplaceContext("main", replacement))));

        assertEquals(replacement, store.context(contextRequest(SCOPE, 4_000)).messages());
        assertEquals(original, store.requestContext(SCOPE, REQUEST));
        assertEquals(otherMessages, store.context(contextRequest(other, 4_000)).messages());
        assertEquals(otherMessages, store.requestContext(other, REQUEST));
    }

    @Test
    void checkpointRequiresMatchingModelSourceHashAndWholeUnitBoundary() {
        SqliteGuideHistoryStore store = store(temporary.resolve("checkpoint.db"));
        GuideHistoryFixture.seed(store, partition(SCOPE, false));
        List<ModelMessage> messages = transcript(false);
        ContextCheckpoint valid = checkpoint(1, 3, messages.subList(0, 3), MODEL);
        ContextCheckpoint stale = checkpoint(2, 3,
                List.of(ModelMessage.userText("different source")), MODEL);
        ContextCheckpoint split = checkpoint(3, 2, messages.subList(0, 2), MODEL);
        ContextCheckpoint wrongModel = checkpoint(4, 3, messages.subList(0, 3), "other:model");
        store.commit(new GuideHistoryCommit(SCOPE, List.of(
                new GuideHistoryMutation.ReplaceContext("main", messages),
                new GuideHistoryMutation.UpsertCheckpoint("main", 0, valid),
                new GuideHistoryMutation.UpsertCheckpoint("main", 1, stale),
                new GuideHistoryMutation.UpsertCheckpoint("main", 2, split),
                new GuideHistoryMutation.UpsertCheckpoint("main", 3, wrongModel))));

        assertEquals(List.of(valid), store.context(contextRequest(SCOPE, 4_000)).checkpoints());
        store.commit(new GuideHistoryCommit(SCOPE, List.of(
                new GuideHistoryMutation.ReplaceContext("main",
                        List.of(ModelMessage.userText("unrelated current snapshot"))))));
        assertTrue(store.context(contextRequest(SCOPE, 4_000)).checkpoints().isEmpty());
    }

    @Test
    void rejectsInvalidContextMutationBeforeAnythingCanBeWritten() {
        List<ModelMessage> orphan = List.of(new ModelMessage(ModelRole.USER, List.of(
                new ModelContent.ToolResult("missing", new JsonObject(), true))));
        List<ModelMessage> unmatched = List.of(new ModelMessage(ModelRole.ASSISTANT, List.of(
                new ModelContent.ToolUse("missing", "openallay:load_skills", new JsonObject()))));

        assertThrows(IllegalArgumentException.class,
                () -> new GuideHistoryMutation.ReplaceContext("main", orphan));
        assertThrows(IllegalArgumentException.class,
                () -> new GuideHistoryMutation.ReplaceRequestContext(REQUEST, unmatched));
        assertThrows(IllegalArgumentException.class,
                () -> new GuideHistoryMutation.ReplaceContext("bad/session", List.of()));
    }

    @Test
    void corruptSnapshotsFailClosedWithoutResetOrUiFallback() throws Exception {
        Path database = temporary.resolve("corrupt-context.db");
        SqliteGuideHistoryStore store = store(database);
        GuideHistoryFixture.seed(store, partition(SCOPE, false));
        List<ModelMessage> messages = transcript(false);
        store.commit(new GuideHistoryCommit(SCOPE, List.of(
                new GuideHistoryMutation.ReplaceContext("main", messages),
                new GuideHistoryMutation.ReplaceRequestContext(REQUEST, messages))));
        for (String malformed : List.of("not-json", "[", "{}", "{\"extra\":true,\"messages\":[]}",
                "{\"messages\":[{\"role\":\"USER\",\"content\":[{"
                        + "\"type\":\"tool_result\",\"toolUseId\":\"orphan\",\"value\":{},\"error\":true}]}]}")) {
            corrupt(database, "model_context", malformed);
            byte[] before = Files.readAllBytes(database);
            GuideHistoryException failure = assertThrows(GuideHistoryException.class,
                    () -> store.context(contextRequest(SCOPE, 4_000)));
            assertEquals("history_corrupt", failure.code());
            assertTrue(Arrays.equals(before, Files.readAllBytes(database)));
            assertEquals(messages, store.requestContext(SCOPE, REQUEST));
            assertEquals(1, store.page(new GuideHistoryPageRequest(SCOPE, "main",
                    GuideHistoryPageRequest.Direction.NEWEST, null, 1)).requests().size());
        }
        corrupt(database, "request_model_context", "{}");
        GuideHistoryException failure = assertThrows(GuideHistoryException.class,
                () -> store.requestContext(SCOPE, REQUEST));
        assertEquals("history_corrupt", failure.code());
    }

    @Test
    void failedTransactionRestoresBothSnapshotKindsAndPartitionTimestamp() {
        Path database = temporary.resolve("rollback-context.db");
        SqliteGuideHistoryStore store = store(database);
        GuideHistoryFixture original = partition(SCOPE, false);
        GuideHistoryFixture.seed(store, original);
        List<ModelMessage> messages = transcript(false);
        store.commit(new GuideHistoryCommit(SCOPE, List.of(
                new GuideHistoryMutation.ReplaceContext("main", messages),
                new GuideHistoryMutation.ReplaceRequestContext(REQUEST, messages))));
        SqliteGuideHistoryStore injected = new SqliteGuideHistoryStore(database,
                Clock.fixed(NOW, ZoneOffset.UTC), new GuideHistoryCodec(), mutation -> {
                    if (mutation == SqliteGuideHistoryStore.Mutation.COMMIT) {
                        throw new java.sql.SQLException("injected context commit failure");
                    }
                });
        List<ModelMessage> replacement = List.of(ModelMessage.userText("must roll back"));

        assertThrows(GuideHistoryException.class, () -> injected.commit(new GuideHistoryCommit(
                SCOPE, List.of(new GuideHistoryMutation.UpsertPartition("main", NOW.plusSeconds(30)),
                        new GuideHistoryMutation.ReplaceContext("main", replacement),
                        new GuideHistoryMutation.ReplaceRequestContext(REQUEST, replacement)))));

        assertEquals(messages, store.context(contextRequest(SCOPE, 4_000)).messages());
        assertEquals(messages, store.requestContext(SCOPE, REQUEST));
        assertEquals(original.updatedAt(), store.metadata(SCOPE).orElseThrow().updatedAt());
        GuideHistoryException missingOwner = assertThrows(GuideHistoryException.class,
                () -> store.commit(new GuideHistoryCommit(SCOPE, List.of(
                        new GuideHistoryMutation.ReplaceContext("missing", replacement)))));
        assertEquals("history_write_failed", missingOwner.code());
        GuideHistoryException missingRequest = assertThrows(GuideHistoryException.class,
                () -> store.commit(new GuideHistoryCommit(SCOPE, List.of(
                        new GuideHistoryMutation.ReplaceRequestContext(new UUID(0, 999), replacement)))));
        assertEquals("history_write_failed", missingRequest.code());
    }

    @Test
    void clearAndDeleteSessionCascadeCurrentAndOriginalContext() throws Exception {
        for (boolean clear : List.of(true, false)) {
            Path database = temporary.resolve(clear ? "clear.db" : "delete-session.db");
            SqliteGuideHistoryStore store = store(database);
            GuideHistoryFixture.seed(store, partition(SCOPE, false));
            store.commit(new GuideHistoryCommit(SCOPE, List.of(
                    new GuideHistoryMutation.ReplaceContext("main", transcript(false)),
                    new GuideHistoryMutation.ReplaceRequestContext(REQUEST, transcript(false)))));

            store.commit(new GuideHistoryCommit(SCOPE, List.of(clear
                    ? new GuideHistoryMutation.ClearSession("main")
                    : new GuideHistoryMutation.DeleteSession("main"))));

            assertTrue(store.context(contextRequest(SCOPE, 4_000)).messages().isEmpty());
            assertTrue(store.requestContext(SCOPE, REQUEST).isEmpty());
            assertEquals(0, count(database, "model_context"));
            assertEquals(0, count(database, "request_model_context"));
            assertEquals(clear ? 1 : 0, count(database, "sessions"));
        }
    }

    @Test
    void partitionAndActorDeleteCascadeBothContextKinds() throws Exception {
        for (boolean actor : List.of(false, true)) {
            Path database = temporary.resolve(actor ? "delete-actor.db" : "delete-partition.db");
            SqliteGuideHistoryStore store = store(database);
            GuideHistoryFixture.seed(store, partition(SCOPE, false));
            store.commit(new GuideHistoryCommit(SCOPE, List.of(
                    new GuideHistoryMutation.ReplaceContext("main", transcript(false)),
                    new GuideHistoryMutation.ReplaceRequestContext(REQUEST, transcript(false)))));

            store.delete(actor ? new GuideHistoryDeleteScope.Actor(ACTOR)
                    : new GuideHistoryDeleteScope.Partition(SCOPE));

            assertEquals(0, count(database, "model_context"));
            assertEquals(0, count(database, "request_model_context"));
            assertTrue(store.context(contextRequest(SCOPE, 4_000)).messages().isEmpty());
            assertTrue(store.requestContext(SCOPE, REQUEST).isEmpty());
        }
    }

    @Test
    void metadataRecoveryPreservesDedicatedSnapshots() {
        SqliteGuideHistoryStore store = store(temporary.resolve("recover-context.db"));
        GuideHistoryFixture.seed(store, partition(SCOPE, true));
        List<ModelMessage> messages = transcript(false);
        store.commit(new GuideHistoryCommit(SCOPE, List.of(
                new GuideHistoryMutation.ReplaceContext("main", messages),
                new GuideHistoryMutation.ReplaceRequestContext(REQUEST, messages))));

        store.metadata(SCOPE);
        assertEquals(GuideRequestStatus.INTERRUPTED, store.page(new GuideHistoryPageRequest(
                SCOPE, "main", GuideHistoryPageRequest.Direction.NEWEST, null, 1))
                .requests().getFirst().status());
        List<ModelMessage> recovered = new java.util.ArrayList<>(messages);
        recovered.add(new ModelMessage(ModelRole.ASSISTANT, List.of(new ModelContent.Text(
                "[OpenAllay request ended: request_interrupted] "
                        + "The previous client process ended before this request completed"))));
        assertEquals(recovered, store.context(contextRequest(SCOPE, 4_000)).messages());
        assertEquals(recovered, store.requestContext(SCOPE, REQUEST));
        store.metadata(SCOPE);
        assertEquals(recovered, store.context(contextRequest(SCOPE, 4_000)).messages());
        assertEquals(recovered, store.requestContext(SCOPE, REQUEST));
    }

    @Test
    void damagedCurrentContextOwnershipFailsClosedBeforePayloadReads() throws Exception {
        Path database = temporary.resolve("missing-ownership.db");
        SqliteGuideHistoryStore store = store(database);
        GuideHistoryFixture.seed(store, partition(SCOPE, false));
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                var statement = connection.createStatement()) {
            statement.execute("drop table model_context");
            statement.execute("""
                    create table model_context(
                        scope_id text not null,
                        session_id text not null,
                        payload_json text not null,
                        primary key(scope_id, session_id)
                    )
                    """);
        }
        byte[] before = Files.readAllBytes(database);

        GuideHistoryException failure = assertThrows(GuideHistoryException.class,
                () -> store.context(contextRequest(SCOPE, 4_000)));

        assertEquals("history_corrupt", failure.code());
        assertTrue(Arrays.equals(before, Files.readAllBytes(database)));
    }

    private SqliteGuideHistoryStore store(Path database) {
        return new SqliteGuideHistoryStore(database, Clock.fixed(NOW, ZoneOffset.UTC),
                new GuideHistoryCodec());
    }

    private static GuideHistoryScope scope(String endpoint) {
        return GuideHistoryScope.derive(ACTOR, GuideHistoryScope.Kind.MULTIPLAYER, endpoint);
    }

    private static GuideHistoryContextRequest contextRequest(GuideHistoryScope scope, int window) {
        return new GuideHistoryContextRequest(scope, "main", new ContextBudget(window, 100), 10, MODEL);
    }

    private static GuideHistoryFixture partition(GuideHistoryScope scope, boolean active) {
        GuideRequestSnapshot request = new GuideRequestSnapshot(REQUEST, "main",
                GuideTopology.CLIENT_LOCAL, "UI question is not model context",
                List.of(new GuideTimelineEntry.Assistant(
                        0, "UI answer is not model context", active, List.of())),
                active ? GuideRequestStatus.MODEL_WAIT : GuideRequestStatus.COMPLETED,
                List.of(), ModelUsage.empty(), null, null, NOW, NOW, active ? null : NOW,
                GuideModelSelection.client("profile"));
        return new GuideHistoryFixture(scope, "main",
                List.of(new GuideSessionSnapshot("main", List.of(), List.of(request), List.of(),
                        GuideModelSelection.client("profile"))), NOW);
    }

    private static List<ModelMessage> transcript(boolean reasoning) {
        JsonObject input = JsonParser.parseString(
                "{\"skills\":[\"openallay:crafting\"],\"source\":\"return skills;\"}").getAsJsonObject();
        JsonObject result = new JsonObject();
        result.addProperty("code", "skill_missing");
        result.addProperty("message", "Skill openallay:crafting was not found");
        result.addProperty("capturedAt", NOW.toString());
        ModelContent.ToolUse call = new ModelContent.ToolUse("actual-call-id", "openallay:load_skills", input);
        List<ModelContent> assistant = reasoning
                ? List.of(new ModelContent.Reasoning("private-reasoning", "private-signature"), call)
                : List.of(call);
        return List.of(ModelMessage.userText("Use the crafting Skill"),
                new ModelMessage(ModelRole.ASSISTANT, assistant),
                new ModelMessage(ModelRole.USER, List.of(new ModelContent.ToolResult(
                        "actual-call-id", result, true))),
                new ModelMessage(ModelRole.ASSISTANT,
                        List.of(new ModelContent.Text("The requested Skill is unavailable."))));
    }

    private static ContextCheckpoint checkpoint(
            long id, int end, List<ModelMessage> source, String model) {
        return new ContextCheckpoint(new UUID(0, id), 0, end,
                ContextSourceHash.compute(new Gson(), source), model, NOW,
                ContextCheckpoint.Status.SUCCEEDED, "grounded summary", null, null, 10);
    }

    private static void corrupt(Path database, String table, String payload) throws Exception {
        if (!List.of("model_context", "request_model_context").contains(table)) {
            throw new IllegalArgumentException("unsupported fixture table");
        }
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                var statement = connection.prepareStatement("update " + table + " set payload_json = ?")) {
            statement.setString(1, payload);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static int count(Path database, String table) throws Exception {
        if (!List.of("model_context", "request_model_context", "sessions").contains(table)) {
            throw new IllegalArgumentException("unsupported fixture table");
        }
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                var result = connection.createStatement().executeQuery("select count(*) from " + table)) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }
}
