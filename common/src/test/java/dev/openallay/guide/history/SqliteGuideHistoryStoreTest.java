package dev.openallay.guide.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openallay.guide.GuideMessage;
import dev.openallay.guide.GuideModelSelection;
import dev.openallay.guide.GuideRequestSnapshot;
import dev.openallay.guide.GuideRequestStatus;
import dev.openallay.guide.GuideSessionSnapshot;
import dev.openallay.guide.GuideTimelineEntry;
import dev.openallay.guide.GuideTopology;
import dev.openallay.model.ModelUsage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SqliteGuideHistoryStoreTest {
    private static final UUID ACTOR =
            UUID.fromString("028407d5-d694-4189-97dd-62e6cc137164");
    private static final UUID OTHER_ACTOR =
            UUID.fromString("efb0aa19-0378-442c-81dd-eb05468dfcad");
    private static final Instant RECOVERY_TIME = Instant.parse("2026-07-18T06:00:00Z");

    @TempDir Path temporary;

    @Test
    void currentLayoutPagesAndRemainsWritable() throws Exception {
        SqliteGuideHistoryStore store = store();
        GuideHistoryFixture saved = partition(
                "current-layout.example", "main", completed("main", "saved"));

        GuideHistoryFixture.seed(store, saved);

        assertEquals(saved.sessions().getFirst().requests().getFirst(),
                pageRequest(store, saved.scope(), saved.sessions().getFirst().sessionId()));
        assertEquals(9, queryInt("select count(*) from sqlite_master "
                + "where type = 'table' and name not glob 'sqlite_*'"));
    }

    @Test
    void roundTripsIndependentSessionAndCapturedRequestSelections() throws Exception {
        GuideRequestSnapshot clientRequest = completed(
                "client",
                "client answer",
                GuideTopology.CLIENT_LOCAL,
                GuideModelSelection.client("openrouter-claude"));
        GuideRequestSnapshot serverRequest = completed(
                "server",
                "server answer",
                GuideTopology.SERVER,
                GuideModelSelection.server());
        GuideHistoryFixture partition = new GuideHistoryFixture(
                GuideHistoryScope.derive(
                        ACTOR, GuideHistoryScope.Kind.MULTIPLAYER, "selection.example"),
                "client",
                List.of(
                        new GuideSessionSnapshot(
                                "client", List.of(), List.of(clientRequest), List.of(),
                                GuideModelSelection.client("openrouter-claude")),
                        new GuideSessionSnapshot(
                                "server", List.of(), List.of(serverRequest), List.of(),
                                GuideModelSelection.server())),
                RECOVERY_TIME);
        SqliteGuideHistoryStore store = store();

        GuideHistoryFixture.seed(store, partition);

        assertEquals(List.of(GuideModelSelection.client("openrouter-claude"), GuideModelSelection.server()),
                store.metadata(partition.scope()).orElseThrow().sessions().stream()
                        .map(GuideHistoryMetadata.Session::modelSelection).toList());
        assertEquals(clientRequest, pageRequest(store, partition.scope(), "client"));
        assertEquals(serverRequest, pageRequest(store, partition.scope(), "server"));
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database());
                var result = connection.createStatement().executeQuery(
                        "select model_selection_json from sessions order by ordinal")) {
            assertTrue(result.next());
            assertEquals(
                    "{\"kind\":\"CLIENT\",\"profileId\":\"openrouter-claude\"}",
                    result.getString(1));
            assertTrue(result.next());
            assertEquals("{\"kind\":\"SERVER\"}", result.getString(1));
        }
    }

    @Test
    void recoversActiveRequestAsInterruptedWithoutChangingTimelineOrder() {
        SqliteGuideHistoryStore store = store();
        GuideRequestSnapshot active = active("main", "partial");
        GuideHistoryFixture original = partition("active.example", "main", active);
        GuideHistoryFixture.seed(store, original);

        store.metadata(original.scope());
        GuideRequestSnapshot request = pageRequest(store, original.scope(), "main");

        assertEquals(GuideRequestStatus.INTERRUPTED, request.status());
        assertEquals("request_interrupted", request.failure().code());
        assertEquals(RECOVERY_TIME, request.terminalAt());
        assertEquals(List.of(0), request.timeline().stream().map(GuideTimelineEntry::ordinal).toList());
        assertFalse(((GuideTimelineEntry.Assistant) request.timeline().getFirst()).streaming());
        store.metadata(original.scope());
        assertEquals(request, pageRequest(store, original.scope(), "main"));
    }

    @Test
    void corruptPartitionIsDiagnosedWithoutBlockingAnotherPartition() throws Exception {
        SqliteGuideHistoryStore store = store();
        GuideHistoryFixture corrupt = partition("corrupt.example", "main", completed("main", "bad"));
        GuideHistoryFixture healthy = partition("healthy.example", "main", completed("main", "good"));
        GuideHistoryFixture.seed(store, corrupt);
        GuideHistoryFixture.seed(store, healthy);
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database());
                var update = connection.prepareStatement(
                        "update timeline_entries set payload_json = '{}' where scope_id = ?")) {
            update.setString(1, corrupt.scope().scopeId());
            update.executeUpdate();
        }

        assertEquals(1, store.metadata(corrupt.scope()).orElseThrow()
                .sessions().getFirst().requestCount());
        GuideHistoryException failure = assertThrows(GuideHistoryException.class,
                () -> pageRequest(store, corrupt.scope(), "main"));
        assertEquals("history_corrupt", failure.code());
        assertEquals(healthy.sessions().getFirst().requests().getFirst(),
                pageRequest(store, healthy.scope(), "main"));
    }

    @Test
    void foreignDatabaseFailsClosedWithoutMutation() throws Exception {
        Path database = temporary.resolve("foreign-layout.sqlite3");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                var statement = connection.createStatement()) {
            statement.execute("create table unrelated_data(value text)");
            statement.execute("insert into unrelated_data(value) values ('retained')");
        }

        GuideHistoryException failure = assertThrows(
                GuideHistoryException.class,
                () -> store(database).metadata(scope("foreign-layout.example")));

        assertEquals("history_corrupt", failure.code());
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                var statement = connection.createStatement()) {
            assertEquals(1, queryInt(statement, "select count(*) from unrelated_data"));
            assertEquals(0, queryInt(statement,
                    "select count(*) from sqlite_master where type = 'table' and name = 'partitions'"));
        }
    }

    @Test
    void partitionDeleteRemovesOnlyTheExactActorPartition() {
        SqliteGuideHistoryStore store = store();
        GuideHistoryFixture current = partition(
                ACTOR, "current.example", "main", completed("main", "current"));
        GuideHistoryFixture sameActorOtherScope = partition(
                ACTOR, "other.example", "main", completed("main", "same actor"));
        GuideHistoryFixture otherActor = partition(
                OTHER_ACTOR, "current.example", "main", completed("main", "other actor"));
        GuideHistoryFixture.seed(store, current);
        GuideHistoryFixture.seed(store, sameActorOtherScope);
        GuideHistoryFixture.seed(store, otherActor);

        store.delete(GuideHistoryDeleteScope.partition(current.scope()));

        assertTrue(store.metadata(current.scope()).isEmpty());
        assertEquals(
                sameActorOtherScope.sessions().getFirst().requests().getFirst(),
                pageRequest(store, sameActorOtherScope.scope(), "main"));
        assertEquals(otherActor.sessions().getFirst().requests().getFirst(),
                pageRequest(store, otherActor.scope(), "main"));
    }

    @Test
    void actorDeleteRemovesEveryMatchingPartitionAndNoOtherActor() {
        SqliteGuideHistoryStore store = store();
        GuideHistoryFixture first = partition(
                ACTOR, "first.example", "main", completed("main", "first"));
        GuideHistoryFixture second = partition(
                ACTOR, "second.example", "main", completed("main", "second"));
        GuideHistoryFixture retained = partition(
                OTHER_ACTOR, "first.example", "main", completed("main", "retained"));
        GuideHistoryFixture.seed(store, first);
        GuideHistoryFixture.seed(store, second);
        GuideHistoryFixture.seed(store, retained);

        store.delete(GuideHistoryDeleteScope.actor(ACTOR));

        assertTrue(store.metadata(first.scope()).isEmpty());
        assertTrue(store.metadata(second.scope()).isEmpty());
        assertEquals(retained.sessions().getFirst().requests().getFirst(),
                pageRequest(store, retained.scope(), "main"));
    }

    @Test
    void deleteScopesRejectMissingActorAndPartitionIdentity() {
        assertThrows(NullPointerException.class, () -> GuideHistoryDeleteScope.actor(null));
        assertThrows(NullPointerException.class, () -> GuideHistoryDeleteScope.partition(null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new GuideHistoryScope(ACTOR, GuideHistoryScope.Kind.MULTIPLAYER, "not-a-scope"));
    }

    @Test
    void injectedDeleteFailureRollsBackEveryMatchingPartition() {
        SqliteGuideHistoryStore store = store();
        GuideHistoryFixture first = partition(
                ACTOR, "rollback-first.example", "main", completed("main", "first"));
        GuideHistoryFixture second = partition(
                ACTOR, "rollback-second.example", "main", completed("main", "second"));
        GuideHistoryFixture.seed(store, first);
        GuideHistoryFixture.seed(store, second);
        SqliteGuideHistoryStore failing = new SqliteGuideHistoryStore(
                database(),
                Clock.fixed(RECOVERY_TIME, ZoneOffset.UTC),
                new GuideHistoryCodec(),
                mutation -> {
                    if (mutation == SqliteGuideHistoryStore.Mutation.DELETE) {
                        throw new java.sql.SQLException("injected after delete");
                    }
                });

        GuideHistoryException failure = assertThrows(
                GuideHistoryException.class,
                () -> failing.delete(GuideHistoryDeleteScope.actor(ACTOR)));

        assertEquals("history_delete_failed", failure.code());
        assertEquals(first.sessions().getFirst().requests().getFirst(),
                pageRequest(store, first.scope(), "main"));
        assertEquals(second.sessions().getFirst().requests().getFirst(),
                pageRequest(store, second.scope(), "main"));
    }

    @Test
    void explicitResetReplacesMalformedLayoutWithoutDeletingSiblingFiles() throws Exception {
        SqliteGuideHistoryStore store = store();
        GuideHistoryFixture original = partition(
                ACTOR, "reset.example", "main", completed("main", "saved"));
        GuideHistoryFixture.seed(store, original);
        Path retainedTrace = temporary.resolve("developer-trace.json");
        Files.writeString(retainedTrace, "retained");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database());
                var statement = connection.createStatement()) {
            statement.execute("create table foreign_data(value text)");
            statement.execute("insert into foreign_data values ('retained')");
        }

        store.resetDatabase();

        assertTrue(store.metadata(original.scope()).isEmpty());
        assertEquals("retained", Files.readString(retainedTrace));
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database());
                var statement = connection.createStatement()) {
            assertEquals(0, queryInt(statement,
                    "select count(*) from sqlite_master where type = 'table' and name = 'foreign_data'"));
            assertEquals(0, queryInt(statement, "select count(*) from partitions"));
        }
    }

    @Test
    void injectedResetFailureRestoresMalformedLayoutAndEveryPriorRow() throws Exception {
        SqliteGuideHistoryStore store = store();
        GuideHistoryFixture original = partition(
                ACTOR, "reset-rollback.example", "main", completed("main", "saved"));
        GuideHistoryFixture.seed(store, original);
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database());
                var statement = connection.createStatement()) {
            statement.execute("create table foreign_data(value text)");
            statement.execute("insert into foreign_data values ('retained')");
        }
        SqliteGuideHistoryStore failing = new SqliteGuideHistoryStore(
                database(),
                Clock.fixed(RECOVERY_TIME, ZoneOffset.UTC),
                new GuideHistoryCodec(),
                mutation -> {
                    if (mutation == SqliteGuideHistoryStore.Mutation.RESET) {
                        throw new java.sql.SQLException("injected after layout recreation");
                    }
                });

        GuideHistoryException failure = assertThrows(
                GuideHistoryException.class, failing::resetDatabase);

        assertEquals("history_delete_failed", failure.code());
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database());
                var statement = connection.createStatement()) {
            assertEquals(1, queryInt(statement, "select count(*) from foreign_data"));
            assertEquals(1, queryInt(statement, "select count(*) from partitions"));
            assertEquals(2, queryInt(statement, "select count(*) from messages"));
        }
    }

    private static GuideRequestSnapshot pageRequest(
            GuideHistoryStore store, GuideHistoryScope scope, String sessionId) {
        return store.page(new GuideHistoryPageRequest(scope, sessionId,
                GuideHistoryPageRequest.Direction.NEWEST, null, 1)).requests().getFirst();
    }

    private SqliteGuideHistoryStore store() {
        return store(database());
    }

    private SqliteGuideHistoryStore store(Path database) {
        return new SqliteGuideHistoryStore(
                database,
                Clock.fixed(RECOVERY_TIME, ZoneOffset.UTC),
                new GuideHistoryCodec());
    }

    private static GuideHistoryScope scope(String server) {
        return GuideHistoryScope.derive(ACTOR, GuideHistoryScope.Kind.MULTIPLAYER, server);
    }

    private Path database() {
        return temporary.resolve("history.sqlite3");
    }

    private int queryInt(String query) throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database());
                var statement = connection.createStatement()) {
            return queryInt(statement, query);
        }
    }

    private static int queryInt(java.sql.Statement statement, String query) throws Exception {
        try (var result = statement.executeQuery(query)) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }

    private static GuideHistoryFixture partition(
            String server, String sessionId, GuideRequestSnapshot request) {
        return partition(ACTOR, server, sessionId, request);
    }

    private static GuideHistoryFixture partition(
            UUID actor,
            String server,
            String sessionId,
            GuideRequestSnapshot request) {
        GuideHistoryScope scope = GuideHistoryScope.derive(
                actor, GuideHistoryScope.Kind.MULTIPLAYER, server);
        List<GuideMessage> messages = request.status() == GuideRequestStatus.COMPLETED
                ? List.of(
                        new GuideMessage(request.requestId(), GuideMessage.Role.USER,
                                request.userMessage(), request.createdAt()),
                        new GuideMessage(request.requestId(), GuideMessage.Role.ASSISTANT,
                                request.assistantText(), request.terminalAt()))
                : List.of(new GuideMessage(
                        request.requestId(), GuideMessage.Role.USER,
                        request.userMessage(), request.createdAt()));
        return new GuideHistoryFixture(
                scope,
                sessionId,
                List.of(new GuideSessionSnapshot(sessionId, messages, List.of(request))),
                request.updatedAt());
    }

    private static GuideRequestSnapshot completed(String sessionId, String answer) {
        return completed(
                sessionId,
                answer,
                GuideTopology.CLIENT_LOCAL,
                GuideModelSelection.client("default"));
    }

    private static GuideRequestSnapshot completed(
            String sessionId,
            String answer,
            GuideTopology topology,
            GuideModelSelection modelSelection) {
        UUID requestId = UUID.nameUUIDFromBytes((sessionId + answer).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Instant terminal = Instant.parse("2026-07-18T05:00:00Z");
        return new GuideRequestSnapshot(
                requestId,
                sessionId,
                topology,
                "question " + answer,
                List.of(
                        new GuideTimelineEntry.Assistant(0, "checking " + answer, false, List.of()),
                        new GuideTimelineEntry.Assistant(1, answer, false, List.of())),
                GuideRequestStatus.COMPLETED,
                List.of(),
                new ModelUsage(10, 2, 0),
                null,
                null,
                terminal.minusSeconds(2),
                terminal,
                terminal,
                modelSelection);
    }

    private static GuideRequestSnapshot active(String sessionId, String partial) {
        Instant updated = Instant.parse("2026-07-18T05:30:00Z");
        GuideRequestSnapshot request = new GuideRequestSnapshot(
                UUID.nameUUIDFromBytes(partial.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                sessionId,
                GuideTopology.CLIENT_LOCAL,
                "question active",
                List.of(new GuideTimelineEntry.Assistant(0, partial, true, List.of())),
                GuideRequestStatus.MODEL_WAIT,
                List.of(),
                ModelUsage.empty(),
                null,
                null,
                updated.minusSeconds(1),
                updated,
                null);
        assertNotNull(request);
        return request;
    }

}
