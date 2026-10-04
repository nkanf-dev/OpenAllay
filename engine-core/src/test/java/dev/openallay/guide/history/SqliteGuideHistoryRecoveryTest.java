package dev.openallay.guide.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import dev.openallay.agent.context.ContextBudget;
import dev.openallay.agent.context.ContextStructure;
import dev.openallay.guide.GuideModelSelection;
import dev.openallay.guide.GuideRequestSnapshot;
import dev.openallay.guide.GuideRequestStatus;
import dev.openallay.guide.GuideTimelineEntry;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SqliteGuideHistoryRecoveryTest {
    private static final Instant NOW = Instant.parse("2026-09-30T12:00:00Z");
    private static final GuideHistoryScope SCOPE = GuideHistoryScope.derive(
            UUID.fromString("4d850f59-1a0e-447a-bfef-c7c3ccb40c1d"),
            GuideHistoryScope.Kind.MULTIPLAYER, "recovery.example");
    private static final UUID FIRST = new UUID(0, 1);
    private static final UUID SECOND = new UUID(0, 2);
    private static final ModelMessage NOTE = new ModelMessage(ModelRole.ASSISTANT,
            List.of(new ModelContent.Text("[OpenAllay request ended: request_interrupted] "
                    + "The previous client process ended before this request completed")));

    @TempDir Path temporary;

    @Test
    void missingSnapshotsRetainAcceptedUserAndPlainInterruptionOnly() {
        SqliteGuideHistoryStore store = store(temporary.resolve("missing.db"));
        GuideRequestSnapshot request = request(FIRST, "main", "accepted user question", true);
        seed(store, new RequestRow(0, request));

        store.metadata(SCOPE);

        List<ModelMessage> expected = List.of(ModelMessage.userText(request.userMessage()), NOTE);
        assertEquals(expected, current(store, "main"));
        assertEquals(expected, store.requestContext(SCOPE, FIRST));
        assertInterrupted(store, "main", FIRST);
        store.metadata(SCOPE);
        assertEquals(expected, current(store, "main"));
        assertEquals(expected, store.requestContext(SCOPE, FIRST));
    }

    @Test
    void missingOriginalAddsAcceptedUserWithoutReplacingPriorCurrentTranscript() {
        SqliteGuideHistoryStore store = store(temporary.resolve("missing-original.db"));
        GuideRequestSnapshot request = request(FIRST, "main", "new accepted question", true);
        seed(store, new RequestRow(0, request));
        List<ModelMessage> prior = transcript("prior");
        store.commit(new GuideHistoryCommit(SCOPE, List.of(
                new GuideHistoryMutation.ReplaceContext("main", prior))));

        store.metadata(SCOPE);

        List<ModelMessage> expected = new ArrayList<>(prior);
        expected.add(ModelMessage.userText(request.userMessage()));
        expected.add(NOTE);
        assertEquals(expected, current(store, "main"));
        assertEquals(List.of(ModelMessage.userText(request.userMessage()), NOTE),
                store.requestContext(SCOPE, FIRST));
        ContextStructure.units(current(store, "main"));
    }

    @Test
    void missingOriginalDoesNotDuplicateAlreadyRetainedAcceptedUser() {
        SqliteGuideHistoryStore store = store(temporary.resolve("retained-user.db"));
        GuideRequestSnapshot request = request(FIRST, "main", "already accepted", true);
        seed(store, new RequestRow(0, request));
        List<ModelMessage> accepted = List.of(ModelMessage.userText(request.userMessage()));
        store.commit(new GuideHistoryCommit(SCOPE, List.of(
                new GuideHistoryMutation.ReplaceContext("main", accepted))));

        store.metadata(SCOPE);

        assertEquals(withNote(accepted), current(store, "main"));
        assertEquals(withNote(accepted), store.requestContext(SCOPE, FIRST));
    }

    @Test
    void missingOriginalDoesNotRepeatAcceptedUserAndInterruptionAlreadyInCurrent() {
        SqliteGuideHistoryStore store = store(temporary.resolve("retained-recovery.db"));
        GuideRequestSnapshot request = request(FIRST, "main", "already ended", true);
        seed(store, new RequestRow(0, request));
        List<ModelMessage> ended = List.of(ModelMessage.userText(request.userMessage()), NOTE);
        store.commit(new GuideHistoryCommit(SCOPE, List.of(
                new GuideHistoryMutation.ReplaceContext("main", ended))));

        store.metadata(SCOPE);

        assertEquals(ended, current(store, "main"));
        assertEquals(ended, store.requestContext(SCOPE, FIRST));
    }

    @Test
    void concurrentInterruptedRequestsKeepIndependentOriginalsAndNewestCurrent() {
        SqliteGuideHistoryStore store = store(temporary.resolve("same-session.db"));
        seed(store, new RequestRow(0, request(FIRST, "main", "first user", true)),
                new RequestRow(1, request(SECOND, "main", "second user", true)));
        List<ModelMessage> first = transcript("first");
        List<ModelMessage> second = transcript("second");
        store.commit(new GuideHistoryCommit(SCOPE, List.of(
                new GuideHistoryMutation.ReplaceRequestContext(FIRST, first),
                new GuideHistoryMutation.ReplaceRequestContext(SECOND, second),
                new GuideHistoryMutation.ReplaceContext("main", second))));

        store.metadata(SCOPE);

        assertEquals(withNote(first), store.requestContext(SCOPE, FIRST));
        assertEquals(withNote(second), store.requestContext(SCOPE, SECOND));
        assertEquals(withNote(second), current(store, "main"));
        assertInterrupted(store, "main", FIRST);
        assertInterrupted(store, "main", SECOND);
        store.metadata(SCOPE);
        assertEquals(withNote(second), current(store, "main"));
        assertEquals(withNote(first), store.requestContext(SCOPE, FIRST));
    }

    @Test
    void olderInterruptedRequestCannotOverwriteNewerTerminalSessionContext() {
        SqliteGuideHistoryStore store = store(temporary.resolve("terminal-newest.db"));
        seed(store, new RequestRow(0, request(FIRST, "main", "old active user", true)),
                new RequestRow(1, request(SECOND, "main", "new terminal user", false)));
        List<ModelMessage> older = transcript("older");
        List<ModelMessage> newest = transcript("newest");
        store.commit(new GuideHistoryCommit(SCOPE, List.of(
                new GuideHistoryMutation.ReplaceRequestContext(FIRST, older),
                new GuideHistoryMutation.ReplaceRequestContext(SECOND, newest),
                new GuideHistoryMutation.ReplaceContext("main", newest))));

        store.metadata(SCOPE);

        assertEquals(withNote(older), store.requestContext(SCOPE, FIRST));
        assertEquals(newest, store.requestContext(SCOPE, SECOND));
        assertEquals(newest, current(store, "main"));
        assertInterrupted(store, "main", FIRST);
        assertEquals(GuideRequestStatus.COMPLETED, page(store, "main").stream()
                .filter(request -> request.requestId().equals(SECOND)).findFirst().orElseThrow().status());
    }

    @Test
    void recoveryUpdatesEachInterruptedSessionSnapshotWithoutCrossSessionWrites() {
        SqliteGuideHistoryStore store = store(temporary.resolve("sessions.db"));
        seed(store, new RequestRow(0, request(FIRST, "main", "main user", true)),
                new RequestRow(0, request(SECOND, "other", "other user", true)));
        List<ModelMessage> main = transcript("main");
        List<ModelMessage> other = transcript("other");
        store.commit(new GuideHistoryCommit(SCOPE, List.of(
                new GuideHistoryMutation.ReplaceRequestContext(FIRST, main),
                new GuideHistoryMutation.ReplaceRequestContext(SECOND, other),
                new GuideHistoryMutation.ReplaceContext("main", main),
                new GuideHistoryMutation.ReplaceContext("other", other))));

        store.metadata(SCOPE);

        assertEquals(withNote(main), current(store, "main"));
        assertEquals(withNote(other), current(store, "other"));
        assertEquals(withNote(main), store.requestContext(SCOPE, FIRST));
        assertEquals(withNote(other), store.requestContext(SCOPE, SECOND));
    }

    @Test
    void failedRecoveryRollsBackStatusTimelineAndBothTranscriptsTogether() {
        Path database = temporary.resolve("rollback.db");
        SqliteGuideHistoryStore store = store(database);
        seed(store, new RequestRow(0, request(FIRST, "main", "active user", true)));
        List<ModelMessage> original = transcript("original");
        List<ModelMessage> projected = List.of(ModelMessage.userText("actual retained projection"));
        store.commit(new GuideHistoryCommit(SCOPE, List.of(
                new GuideHistoryMutation.ReplaceRequestContext(FIRST, original),
                new GuideHistoryMutation.ReplaceContext("main", projected))));
        SqliteGuideHistoryStore injected = new SqliteGuideHistoryStore(database,
                Clock.fixed(NOW, ZoneOffset.UTC), new GuideHistoryCodec(), mutation -> {
                    if (mutation == SqliteGuideHistoryStore.Mutation.RECOVER) {
                        throw new java.sql.SQLException("injected recovery failure");
                    }
                });

        assertThrows(GuideHistoryException.class, () -> injected.metadata(SCOPE));

        GuideRequestSnapshot unchanged = page(store, "main").getFirst();
        assertEquals(GuideRequestStatus.MODEL_WAIT, unchanged.status());
        assertTrue(((GuideTimelineEntry.Assistant) unchanged.timeline().getFirst()).streaming());
        assertEquals(original, store.requestContext(SCOPE, FIRST));
        assertEquals(projected, current(store, "main"));
        store.metadata(SCOPE);
        assertInterrupted(store, "main", FIRST);
        assertEquals(withNote(original), store.requestContext(SCOPE, FIRST));
        assertEquals(withNote(projected), current(store, "main"));
    }

    @Test
    void alreadyRecordedTrailingInterruptionIsNotAppendedAgain() {
        SqliteGuideHistoryStore store = store(temporary.resolve("existing-note.db"));
        seed(store, new RequestRow(0, request(FIRST, "main", "active user", true)));
        List<ModelMessage> messages = withNote(transcript("existing"));
        store.commit(new GuideHistoryCommit(SCOPE, List.of(
                new GuideHistoryMutation.ReplaceRequestContext(FIRST, messages),
                new GuideHistoryMutation.ReplaceContext("main", messages))));

        store.metadata(SCOPE);
        store.metadata(SCOPE);

        assertEquals(messages, store.requestContext(SCOPE, FIRST));
        assertEquals(messages, current(store, "main"));
    }

    @Test
    void malformedOriginalFailsClosedInsteadOfReplacingGenuineContextWithDisplayText() throws Exception {
        Path database = temporary.resolve("corrupt-original.db");
        SqliteGuideHistoryStore store = store(database);
        seed(store, new RequestRow(0, request(FIRST, "main", "active user", true)));
        List<ModelMessage> messages = transcript("retained");
        store.commit(new GuideHistoryCommit(SCOPE, List.of(
                new GuideHistoryMutation.ReplaceRequestContext(FIRST, messages),
                new GuideHistoryMutation.ReplaceContext("main", messages))));
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                var statement = connection.createStatement()) {
            statement.execute("update request_model_context set payload_json = '{}'");
        }

        GuideHistoryException failure = assertThrows(GuideHistoryException.class,
                () -> store.metadata(SCOPE));

        assertEquals("history_corrupt", failure.code());
        assertEquals(messages, current(store, "main"));
        GuideRequestSnapshot unchanged = page(store, "main").getFirst();
        assertEquals(GuideRequestStatus.MODEL_WAIT, unchanged.status());
        assertTrue(((GuideTimelineEntry.Assistant) unchanged.timeline().getFirst()).streaming());
    }

    private static void assertInterrupted(GuideHistoryStore store, String session, UUID requestId) {
        GuideRequestSnapshot request = page(store, session).stream()
                .filter(value -> value.requestId().equals(requestId)).findFirst().orElseThrow();
        assertEquals(GuideRequestStatus.INTERRUPTED, request.status());
        assertEquals("request_interrupted", request.failure().code());
        assertEquals(NOW, request.terminalAt());
        assertFalse(((GuideTimelineEntry.Assistant) request.timeline().getFirst()).streaming());
    }

    private static List<GuideRequestSnapshot> page(GuideHistoryStore store, String session) {
        return store.page(new GuideHistoryPageRequest(SCOPE, session,
                GuideHistoryPageRequest.Direction.NEWEST, null, 10)).requests();
    }

    private static List<ModelMessage> current(GuideHistoryStore store, String session) {
        return store.context(new GuideHistoryContextRequest(SCOPE, session,
                new ContextBudget(4_000, 100), 10, "test:model")).messages();
    }

    private static List<ModelMessage> withNote(List<ModelMessage> messages) {
        List<ModelMessage> retained = new ArrayList<>(messages);
        retained.add(NOTE);
        return List.copyOf(retained);
    }

    private static List<ModelMessage> transcript(String identity) {
        JsonObject input = new JsonObject();
        input.addProperty("source", "return '" + identity + "';");
        JsonObject result = new JsonObject();
        result.addProperty("message", "actual " + identity + " result");
        List<ModelMessage> messages = List.of(ModelMessage.userText("model user " + identity),
                new ModelMessage(ModelRole.ASSISTANT, List.of(new ModelContent.ToolUse(
                        identity, "openallay:run_javascript", input))),
                new ModelMessage(ModelRole.USER, List.of(new ModelContent.ToolResult(
                        identity, result, false))));
        ContextStructure.units(messages);
        return messages;
    }

    private static GuideRequestSnapshot request(UUID id, String session, String user, boolean active) {
        return new GuideRequestSnapshot(id, session, GuideTopology.CLIENT_LOCAL, user,
                List.of(new GuideTimelineEntry.Assistant(0, "display only assistant", active, List.of())),
                active ? GuideRequestStatus.MODEL_WAIT : GuideRequestStatus.COMPLETED,
                List.of(), ModelUsage.empty(), null, null, NOW, NOW, active ? null : NOW,
                GuideModelSelection.client("profile"));
    }

    private static void seed(GuideHistoryStore store, RequestRow... rows) {
        List<GuideHistoryMutation> mutations = new ArrayList<>();
        mutations.add(new GuideHistoryMutation.UpsertPartition("main", NOW));
        LinkedHashMap<String, Integer> sessions = new LinkedHashMap<>();
        for (RequestRow row : rows) {
            sessions.putIfAbsent(row.request().sessionId(), sessions.size());
        }
        sessions.forEach((session, ordinal) -> mutations.add(new GuideHistoryMutation.UpsertSession(
                session, ordinal, GuideModelSelection.client("profile"))));
        for (RequestRow row : rows) {
            mutations.add(new GuideHistoryMutation.UpsertRequest(row.sequence(), row.request()));
            row.request().timeline().forEach(entry -> mutations.add(
                    new GuideHistoryMutation.UpsertTimelineEntry(row.request().requestId(), entry)));
        }
        store.commit(new GuideHistoryCommit(SCOPE, mutations));
    }

    private static SqliteGuideHistoryStore store(Path database) {
        return new SqliteGuideHistoryStore(database, Clock.fixed(NOW, ZoneOffset.UTC),
                new GuideHistoryCodec());
    }

    private record RequestRow(long sequence, GuideRequestSnapshot request) {}
}
