package dev.openallay.guide.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The database contract is its current exact structure, not a persisted release number. */
final class SqliteGuideHistoryLayoutTest {
    private static final Instant NOW = Instant.parse("2026-09-30T12:00:00Z");
    private static final GuideHistoryScope SCOPE = GuideHistoryScope.derive(
            UUID.fromString("473680e9-558a-4ffd-806f-810b37b2f7d6"),
            GuideHistoryScope.Kind.MULTIPLAYER, "layout.example");
    private static final Set<String> TABLES = Set.of(
            "partitions", "sessions", "requests", "messages", "timeline_entries",
            "request_sources", "compaction_checkpoints", "model_context", "request_model_context",
            "request_context_boundaries");

    @TempDir Path temporary;

    @Test
    void oldTokenOnlyRequestShapeIsRejectedWithoutResettingTheDatabase() throws Exception {
        Path database = temporary.resolve("old-token-shape.db");
        store(database).metadata(SCOPE);
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                var statement = connection.createStatement()) {
            statement.execute("alter table requests rename column model_usage_json to input_tokens");
            statement.execute("alter table requests rename column usage_projection_json to output_tokens");
            statement.execute("alter table requests rename column usage_origin_request_id to cache_read_tokens");
        }
        byte[] before = Files.readAllBytes(database);
        GuideHistoryException failure = assertThrows(GuideHistoryException.class,
                () -> store(database).metadata(SCOPE));
        assertEquals("history_corrupt", failure.code());
        assertTrue(Arrays.equals(before, Files.readAllBytes(database)));
    }

    @Test
    void preForkCurrentLayoutIsRejectedWithoutResettingOrMigratingTheDatabase() throws Exception {
        Path database = temporary.resolve("pre-fork-layout.db");
        store(database).metadata(SCOPE);
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                var statement = connection.createStatement()) {
            statement.execute("drop table request_context_boundaries");
        }
        byte[] before = Files.readAllBytes(database);
        GuideHistoryException failure = assertThrows(GuideHistoryException.class,
                () -> store(database).metadata(SCOPE));
        assertEquals("history_corrupt", failure.code());
        assertTrue(Arrays.equals(before, Files.readAllBytes(database)));
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                var rows = connection.createStatement().executeQuery("""
                        select name from sqlite_master where type = 'table' and name not glob 'sqlite_*'
                        """)) {
            Set<String> expected = new java.util.HashSet<>(TABLES);
            expected.remove("request_context_boundaries");
            Set<String> actual = new java.util.HashSet<>();
            while (rows.next()) actual.add(rows.getString(1));
            assertEquals(expected, actual);
        }
    }

    @Test
    void missingAndZeroLengthFilesCreateOnlyCurrentTables() throws Exception {
        for (boolean exists : List.of(false, true)) {
            Path database = temporary.resolve(exists ? "empty.db" : "missing.db");
            if (exists) Files.createFile(database);
            SqliteGuideHistoryStore store = store(database);

            assertTrue(store.metadata(SCOPE).isEmpty());

            try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                    var result = connection.createStatement().executeQuery("""
                            select name from sqlite_master
                            where type = 'table' and name not glob 'sqlite_*'
                            """)) {
                Set<String> actual = new java.util.HashSet<>();
                while (result.next()) actual.add(result.getString(1));
                assertEquals(TABLES, actual);
            }
            store.commit(new GuideHistoryCommit(SCOPE, List.of(
                    new GuideHistoryMutation.UpsertPartition("main", NOW),
                    new GuideHistoryMutation.UpsertSession("main", 0,
                            dev.openallay.guide.GuideModelSelection.client("profile")))));
            assertEquals("main", store.metadata(SCOPE).orElseThrow().selectedSession());
        }
    }

    @Test
    void nonemptyDatabaseWithoutApplicationTablesIsNotImplicitlyInitialized() throws Exception {
        Path database = temporary.resolve("nonempty-foreign.db");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                var statement = connection.createStatement()) {
            statement.execute("create table retained(value text)");
            statement.execute("drop table retained");
        }
        assertTrue(Files.size(database) > 0);
        byte[] before = Files.readAllBytes(database);

        GuideHistoryException failure = assertThrows(GuideHistoryException.class,
                () -> store(database).metadata(SCOPE));

        assertEquals("history_corrupt", failure.code());
        assertTrue(Arrays.equals(before, Files.readAllBytes(database)));
    }

    @Test
    void malformedNonDatabaseFileIsUntouched() throws Exception {
        Path database = temporary.resolve("not-a-database.db");
        byte[] before = "This file must not be adapted or reset.".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(database, before);

        GuideHistoryException failure = assertThrows(GuideHistoryException.class,
                () -> store(database).metadata(SCOPE));

        assertEquals("history_corrupt", failure.code());
        assertTrue(Arrays.equals(before, Files.readAllBytes(database)));
        assertFalse(Files.exists(database.resolveSibling("not-a-database.db-wal")));
    }

    @Test
    void extraTableFailsEveryNormalOperationWithoutChangingAnyFile() throws Exception {
        Path database = temporary.resolve("extra-table.db");
        SqliteGuideHistoryStore store = store(database);
        store.metadata(SCOPE);
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                var statement = connection.createStatement()) {
            statement.execute("create table foreign_data(value text)");
            statement.execute("insert into foreign_data values ('retained')");
        }
        byte[] before = Files.readAllBytes(database);
        var contextRequest = new GuideHistoryContextRequest(SCOPE, "main",
                new dev.openallay.agent.context.ContextBudget(4_000, 100), 10, "test:model");
        UUID requestId = UUID.fromString("a771dd11-f9e0-49f3-9f74-6cd83311d0a8");
        List<Runnable> operations = List.of(
                () -> store.metadata(SCOPE),
                () -> store.page(new GuideHistoryPageRequest(SCOPE, "main",
                        GuideHistoryPageRequest.Direction.NEWEST, null, 1)),
                () -> store.context(contextRequest),
                () -> store.requestContext(SCOPE, requestId),
                () -> store.commit(new GuideHistoryCommit(SCOPE, List.of(
                        new GuideHistoryMutation.UpsertPartition("main", NOW)))),
                () -> store.fork(new GuideHistoryForkRequest(SCOPE,
                        new GuideHistoryMutation.ForkSession("main", new GuideHistoryCursor(0, requestId),
                                "branch", 1, dev.openallay.guide.GuideModelSelection.client("profile")))),
                () -> store.delete(new GuideHistoryDeleteScope.Partition(SCOPE)));
        for (Runnable operation : operations) {
            GuideHistoryException failure = assertThrows(GuideHistoryException.class, operation::run);
            assertEquals("history_corrupt", failure.code());
            assertTrue(failure.getMessage().contains("was not changed"));
            assertTrue(Arrays.equals(before, Files.readAllBytes(database)));
            assertFalse(Files.exists(database.resolveSibling("extra-table.db-wal")));
        }
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                var result = connection.createStatement().executeQuery("select value from foreign_data")) {
            assertTrue(result.next());
            assertEquals("retained", result.getString(1));
        }
    }

    @Test
    void missingTableExtraColumnAndWrongColumnTypeFailClosed() throws Exception {
        List<String> damage = List.of(
                "drop table request_model_context",
                "drop table request_context_boundaries",
                "alter table request_context_boundaries add column foreign_column text",
                "alter table requests add column foreign_column text",
                """
                drop table model_context;
                create table model_context(
                    scope_id text not null,
                    session_id text not null,
                    payload_json integer not null,
                    primary key(scope_id, session_id),
                    foreign key(scope_id, session_id)
                        references sessions(scope_id, session_id) on delete cascade
                )
                """);
        for (int index = 0; index < damage.size(); index++) {
            Path database = temporary.resolve("damaged-columns-" + index + ".db");
            SqliteGuideHistoryStore store = store(database);
            store.metadata(SCOPE);
            try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                    var statement = connection.createStatement()) {
                for (String sql : damage.get(index).split(";")) {
                    if (!sql.isBlank()) statement.execute(sql);
                }
            }
            byte[] before = Files.readAllBytes(database);

            GuideHistoryException failure = assertThrows(GuideHistoryException.class,
                    () -> store.metadata(SCOPE));

            assertEquals("history_corrupt", failure.code());
            assertTrue(Arrays.equals(before, Files.readAllBytes(database)));
        }
    }

    @Test
    void missingAndNonCascadingOwnershipFailClosedAcrossDisplayAndContextTables() throws Exception {
        List<String> damage = List.of(
                """
                drop table request_context_boundaries;
                create table request_context_boundaries(
                    scope_id text not null,
                    request_id text not null,
                    payload_json text not null,
                    checkpoints_json text not null,
                    primary key(scope_id, request_id)
                )
                """,
                """
                drop table timeline_entries;
                create table timeline_entries(
                    scope_id text not null,
                    request_id text not null,
                    ordinal integer not null check(ordinal >= 0),
                    payload_json text not null,
                    primary key(scope_id, request_id, ordinal)
                )
                """,
                """
                drop table request_model_context;
                create table request_model_context(
                    scope_id text not null,
                    request_id text not null,
                    payload_json text not null,
                    primary key(scope_id, request_id),
                    foreign key(scope_id, request_id)
                        references requests(scope_id, request_id) on delete restrict
                )
                """);
        for (int index = 0; index < damage.size(); index++) {
            Path database = temporary.resolve("damaged-ownership-" + index + ".db");
            SqliteGuideHistoryStore store = store(database);
            store.metadata(SCOPE);
            try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                    var statement = connection.createStatement()) {
                for (String sql : damage.get(index).split(";")) {
                    if (!sql.isBlank()) statement.execute(sql);
                }
            }
            byte[] before = Files.readAllBytes(database);

            GuideHistoryException failure = assertThrows(GuideHistoryException.class,
                    () -> store.metadata(SCOPE));

            assertEquals("history_corrupt", failure.code());
            assertTrue(failure.getMessage().contains("ownership"));
            assertTrue(Arrays.equals(before, Files.readAllBytes(database)));
        }
    }

    private static SqliteGuideHistoryStore store(Path database) {
        return new SqliteGuideHistoryStore(database, Clock.fixed(NOW, ZoneOffset.UTC),
                new GuideHistoryCodec());
    }
}
