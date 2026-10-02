package dev.openallay.guide.history;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import dev.openallay.agent.context.ContextCheckpoint;
import dev.openallay.agent.context.ContextSourceHash;
import dev.openallay.agent.context.ContextStructure;
import dev.openallay.agent.context.ModelContextCodec;
import dev.openallay.guide.GuideFailure;
import dev.openallay.guide.GuideMessage;
import dev.openallay.guide.GuideRequestSnapshot;
import dev.openallay.guide.GuideRequestStatus;
import dev.openallay.guide.GuideSource;
import dev.openallay.guide.GuideTimelineEntry;
import dev.openallay.guide.GuideTopology;
import dev.openallay.guide.GuideUsageSnapshot;
import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelRole;
import dev.openallay.model.ModelUsage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** JDBC store used only behind the asynchronous history repository. */
public final class SqliteGuideHistoryStore implements GuideHistoryStore {
    private static final String INTERRUPTION_MESSAGE =
            "The previous client process ended before this request completed";
    private static final ModelMessage INTERRUPTION_NOTE = new ModelMessage(ModelRole.ASSISTANT,
            List.of(new ModelContent.Text(
                    "[OpenAllay request ended: request_interrupted] " + INTERRUPTION_MESSAGE)));
    private static final Map<String, List<ColumnSignature>>
            HISTORY_LAYOUT = historyLayout();
    private static final Map<String, List<ForeignKeySignature>>
            HISTORY_OWNERSHIP = historyOwnership();

    private final Path database;
    private final Clock clock;
    private final GuideHistoryCodec codec;
    private final ModelContextCodec modelContexts = new ModelContextCodec();
    private final FailureInjector failureInjector;

    public SqliteGuideHistoryStore(Path database, Clock clock, GuideHistoryCodec codec) {
        this(database, clock, codec, ignored -> {});
    }

    SqliteGuideHistoryStore(
            Path database,
            Clock clock,
            GuideHistoryCodec codec,
            FailureInjector failureInjector) {
        this.database = Objects.requireNonNull(database, "database").toAbsolutePath().normalize();
        this.clock = Objects.requireNonNull(clock, "clock");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.failureInjector = Objects.requireNonNull(failureInjector, "failureInjector");
    }

    @Override
    public Optional<GuideHistoryMetadata> metadata(GuideHistoryScope scope) {
        Objects.requireNonNull(scope, "scope");
        try (Connection connection = open()) {
            PartitionHeader header = readHeader(connection, scope);
            if (header == null) {
                return Optional.empty();
            }
            recoverInterruptedRows(connection, scope);
            header = readHeader(connection, scope);
            List<GuideHistoryMetadata.Session> sessions = new ArrayList<>();
            try (PreparedStatement query = connection.prepareStatement("""
                    select s.session_id, s.ordinal, s.model_selection_json,
                           count(r.request_id) as request_count,
                           min(r.sequence) as first_sequence,
                           max(r.sequence) as last_sequence
                    from sessions s
                    left join requests r
                      on r.scope_id = s.scope_id and r.session_id = s.session_id
                    where s.scope_id = ?
                    group by s.session_id, s.ordinal, s.model_selection_json
                    order by s.ordinal
                    """)) {
                query.setString(1, scope.scopeId());
                try (ResultSet result = query.executeQuery()) {
                    while (result.next()) {
                        long count = result.getLong("request_count");
                        Long firstSequence = nullableLong(result, "first_sequence");
                        Long lastSequence = nullableLong(result, "last_sequence");
                        sessions.add(new GuideHistoryMetadata.Session(
                                result.getString("session_id"),
                                result.getInt("ordinal"),
                                codec.decodeModelSelection(result.getString("model_selection_json")),
                                count,
                                firstSequence == null ? null : cursor(
                                        connection, scope.scopeId(),
                                        result.getString("session_id"), firstSequence),
                                lastSequence == null ? null : cursor(
                                        connection, scope.scopeId(),
                                        result.getString("session_id"), lastSequence),
                                sessionUsage(connection, scope.scopeId(), result.getString("session_id"), false),
                                sessionUsage(connection, scope.scopeId(), result.getString("session_id"), true)));
                    }
                }
            }
            return Optional.of(new GuideHistoryMetadata(
                    scope, header.selectedSession, sessions, header.updatedAt));
        } catch (SQLException failure) {
            throw new GuideHistoryException(
                    "history_metadata_failed", "Unable to load guide history metadata", failure);
        } catch (IllegalArgumentException | JsonParseException malformed) {
            throw new GuideHistoryException(
                    "history_corrupt", "Guide history metadata is malformed", malformed);
        }
    }

    private GuideUsageSnapshot sessionUsage(
            Connection connection, String scopeId, String sessionId, boolean inherited) throws SQLException {
        GuideUsageSnapshot total = GuideUsageSnapshot.empty();
        try (PreparedStatement query = connection.prepareStatement("""
                select usage_projection_json from requests
                where scope_id = ? and session_id = ? and
                """ + (inherited ? "usage_origin_request_id is not null" : "usage_origin_request_id is null"))) {
            query.setString(1, scopeId);
            query.setString(2, sessionId);
            try (ResultSet result = query.executeQuery()) {
                while (result.next()) total = total.plus(codec.decodeUsageProjection(result.getString(1)));
            }
        }
        return total;
    }

    @Override
    public GuideHistoryPage page(GuideHistoryPageRequest request) {
        Objects.requireNonNull(request, "request");
        try (Connection connection = open()) {
            List<SequencedRequest> loaded = readPage(connection, request);
            List<GuideRequestSnapshot> snapshots = loaded.stream()
                    .map(SequencedRequest::request).toList();
            GuideHistoryCursor first = loaded.isEmpty() ? null : loaded.getFirst().cursor();
            GuideHistoryCursor last = loaded.isEmpty() ? null : loaded.getLast().cursor();
            boolean hasEarlier = first != null && requestExists(
                    connection, request.scope().scopeId(), request.sessionId(),
                    "sequence < ?", first.sequence());
            boolean hasLater = last != null && requestExists(
                    connection, request.scope().scopeId(), request.sessionId(),
                    "sequence > ?", last.sequence());
            return new GuideHistoryPage(
                    request.sessionId(), snapshots, first, last, hasEarlier, hasLater);
        } catch (SQLException failure) {
            throw new GuideHistoryException(
                    "history_page_failed", "Unable to load a guide history page", failure);
        } catch (IllegalArgumentException | JsonParseException malformed) {
            throw new GuideHistoryException(
                    "history_corrupt", "A guide history page is malformed", malformed);
        }
    }

    @Override
    public GuideHistoryContextSeed context(GuideHistoryContextRequest request) {
        Objects.requireNonNull(request, "request");
        try (Connection connection = open()) {
            if (readHeader(connection, request.scope()) == null) {
                return new GuideHistoryContextSeed(request.sessionId(), List.of(), List.of(), 0);
            }
            List<ModelMessage> messages = readContext(
                    connection, request.scope().scopeId(), request.sessionId());
            if (messages.isEmpty()) {
                return new GuideHistoryContextSeed(request.sessionId(), List.of(), List.of(), 0);
            }
            // Do not discard over-budget structural units. Agent owns reduction and compaction.
            int estimated = request.estimator().estimate("", messages, List.of());
            List<ContextCheckpoint> checkpoints = readApplicableCheckpoint(
                    connection, request.scope().scopeId(), request.sessionId(),
                    request.modelIdentifier(), messages);
            return new GuideHistoryContextSeed(
                    request.sessionId(), messages, checkpoints, estimated);
        } catch (SQLException failure) {
            throw new GuideHistoryException(
                    "history_context_failed", "Unable to prepare guide history context", failure);
        } catch (IllegalArgumentException | JsonParseException malformed) {
            throw new GuideHistoryException(
                    "history_corrupt", "Guide history context is malformed", malformed);
        }
    }

    @Override
    public List<ModelMessage> requestContext(GuideHistoryScope scope, UUID requestId) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(requestId, "requestId");
        try (Connection connection = open()) {
            if (readHeader(connection, scope) == null) {
                return List.of();
            }
            return readRequestContext(connection, scope.scopeId(), requestId);
        } catch (SQLException failure) {
            throw new GuideHistoryException(
                    "history_context_failed", "Unable to load original guide request context", failure);
        } catch (IllegalArgumentException | JsonParseException malformed) {
            throw new GuideHistoryException(
                    "history_corrupt", "Original guide request context is malformed", malformed);
        }
    }

    private List<ModelMessage> readRequestContext(
            Connection connection, String scopeId, UUID requestId) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("""
                select payload_json from request_model_context
                where scope_id = ? and request_id = ?
                """)) {
            query.setString(1, scopeId);
            query.setString(2, requestId.toString());
            try (ResultSet result = query.executeQuery()) {
                return result.next() ? modelContexts.decode(result.getString("payload_json"))
                        : List.of();
            }
        }
    }

    private List<ModelMessage> readContext(
            Connection connection, String scopeId, String sessionId) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("""
                select payload_json from model_context where scope_id = ? and session_id = ?
                """)) {
            query.setString(1, scopeId);
            query.setString(2, sessionId);
            try (ResultSet result = query.executeQuery()) {
                return result.next() ? modelContexts.decode(result.getString("payload_json"))
                        : List.of();
            }
        }
    }

    @Override
    public void commit(GuideHistoryCommit commit) {
        Objects.requireNonNull(commit, "commit");
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try {
                for (GuideHistoryMutation mutation : commit.mutations()) {
                    applyMutation(connection, commit.scope(), mutation);
                }
                failureInjector.beforeCommit(Mutation.COMMIT);
                connection.commit();
            } catch (SQLException | RuntimeException failure) {
                rollback(connection, failure);
                if (failure instanceof GuideHistoryException known) {
                    throw known;
                }
                throw new GuideHistoryException(
                        "history_write_failed", "Unable to commit durable guide history", failure);
            }
        } catch (SQLException failure) {
            throw new GuideHistoryException(
                    "history_write_failed", "Unable to commit durable guide history", failure);
        }
    }

    @Override
    public void delete(GuideHistoryDeleteScope scope) {
        Objects.requireNonNull(scope, "scope");
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try {
                switch (scope) {
                    case GuideHistoryDeleteScope.Partition partition ->
                        deletePartition(connection, partition.scope());
                    case GuideHistoryDeleteScope.Actor actor ->
                        deleteActor(connection, actor.actorId());
                }
                failureInjector.beforeCommit(Mutation.DELETE);
                connection.commit();
            } catch (SQLException | RuntimeException failure) {
                rollback(connection, failure);
                throw deleteFailure(failure);
            }
        } catch (SQLException | RuntimeException failure) {
            if (failure instanceof GuideHistoryException historyFailure) {
                throw historyFailure;
            }
            throw deleteFailure(failure);
        }
    }

    @Override
    public void resetDatabase() {
        try (Connection connection = openRaw()) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("pragma foreign_keys=off");
            }
            connection.setAutoCommit(false);
            try {
                for (String table : applicationTables(connection)) {
                    try (Statement statement = connection.createStatement()) {
                        statement.execute("drop table " + quoteIdentifier(table));
                    }
                }
                createLayoutObjects(connection);
                failureInjector.beforeCommit(Mutation.RESET);
                connection.commit();
            } catch (SQLException | RuntimeException failure) {
                rollback(connection, failure);
                throw deleteFailure(failure);
            }
        } catch (SQLException | RuntimeException failure) {
            if (failure instanceof GuideHistoryException historyFailure
                    && historyFailure.code().equals("history_delete_failed")) {
                throw historyFailure;
            }
            throw deleteFailure(failure);
        }
    }

    private Connection open() {
        boolean emptyFile;
        try {
            emptyFile = !Files.exists(database) || Files.size(database) == 0;
        } catch (IOException failure) {
            throw new GuideHistoryException(
                    "history_open_failed", "Unable to inspect the guide history database", failure);
        }
        Connection connection;
        try {
            connection = openRaw();
        } catch (GuideHistoryException failure) {
            if (!emptyFile && failure.getCause() instanceof SQLException) {
                throw new GuideHistoryException("history_corrupt",
                        "Guide history database is malformed; database was not changed", failure);
            }
            throw failure;
        }
        try {
            ensureLayout(connection, emptyFile);
            configure(connection);
            configureJournalMode(connection);
            return connection;
        } catch (SQLException failure) {
            try {
                connection.close();
            } catch (SQLException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw new GuideHistoryException(
                    "history_corrupt", "Guide history database layout is malformed; database was not changed", failure);
        } catch (RuntimeException failure) {
            try {
                connection.close();
            } catch (SQLException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    private Connection openRaw() {
        try {
            Path parent = database.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            return DriverManager.getConnection("jdbc:sqlite:" + database);
        } catch (IOException | SQLException failure) {
            throw new GuideHistoryException(
                    "history_open_failed", "Unable to open the guide history database", failure);
        }
    }

    private static void configure(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("pragma foreign_keys=on");
            statement.execute("pragma synchronous=full");
        }
    }

    private static void configureJournalMode(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("pragma journal_mode=wal");
        }
    }

    private static void ensureLayout(Connection connection, boolean emptyFile) throws SQLException {
        List<String> tables = applicationTables(connection);
        if (tables.isEmpty() && emptyFile) {
            createLayout(connection);
            return;
        }
        if (!Set.copyOf(tables).equals(HISTORY_LAYOUT.keySet())) {
            throw corruptLayout("Guide history database has an unrecognized table set");
        }
        for (Map.Entry<String, List<ColumnSignature>> table : HISTORY_LAYOUT.entrySet()) {
            if (!tableSignature(connection, table.getKey()).equals(table.getValue())) {
                throw corruptLayout(
                        "Guide history table " + table.getKey() + " has an unrecognized structure");
            }
            if (!foreignKeys(connection, table.getKey()).equals(HISTORY_OWNERSHIP.get(table.getKey()))) {
                throw corruptLayout(
                        "Guide history table " + table.getKey() + " has unrecognized ownership");
            }
        }
    }

    private static List<ForeignKeySignature> foreignKeys(Connection connection, String table)
            throws SQLException {
        List<ForeignKeySignature> actual = new ArrayList<>();
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(
                        "pragma foreign_key_list(" + quoteIdentifier(table) + ")")) {
            while (result.next()) {
                actual.add(new ForeignKeySignature(
                        result.getInt("seq"), result.getString("table"),
                        result.getString("from"), result.getString("to"),
                        result.getString("on_update"), result.getString("on_delete"),
                        result.getString("match")));
            }
        }
        return sortedForeignKeys(actual);
    }

    private static Map<String, List<ForeignKeySignature>> historyOwnership() {
        Map<String, List<ForeignKeySignature>> tables = new LinkedHashMap<>();
        tables.put("partitions", List.of());
        tables.put("sessions", List.of(foreignKey(0, "partitions", "scope_id")));
        List<ForeignKeySignature> sessionOwner = List.of(
                foreignKey(0, "sessions", "scope_id"), foreignKey(1, "sessions", "session_id"));
        List<ForeignKeySignature> requestOwner = List.of(
                foreignKey(0, "requests", "scope_id"), foreignKey(1, "requests", "request_id"));
        tables.put("requests", sortedForeignKeys(sessionOwner));
        List<ForeignKeySignature> messageOwners = new ArrayList<>(sessionOwner);
        messageOwners.addAll(requestOwner);
        tables.put("messages", sortedForeignKeys(messageOwners));
        tables.put("timeline_entries", sortedForeignKeys(requestOwner));
        tables.put("request_sources", sortedForeignKeys(requestOwner));
        tables.put("compaction_checkpoints", sortedForeignKeys(sessionOwner));
        tables.put("model_context", sortedForeignKeys(sessionOwner));
        tables.put("request_model_context", sortedForeignKeys(requestOwner));
        return Map.copyOf(tables);
    }

    private static ForeignKeySignature foreignKey(int sequence, String owner, String column) {
        return new ForeignKeySignature(sequence, owner, column, column, "NO ACTION", "CASCADE", "NONE");
    }

    private static List<ForeignKeySignature> sortedForeignKeys(List<ForeignKeySignature> keys) {
        return keys.stream().sorted(java.util.Comparator.comparing(ForeignKeySignature::toString)).toList();
    }

    private static List<ColumnSignature> tableSignature(Connection connection, String table)
            throws SQLException {
        List<ColumnSignature> columns = new ArrayList<>();
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(
                        "pragma table_xinfo(" + quoteIdentifier(table) + ")")) {
            while (result.next()) {
                columns.add(new ColumnSignature(
                        result.getString("name"),
                        result.getString("type"),
                        result.getInt("notnull") != 0,
                        result.getInt("pk"),
                        result.getInt("hidden")));
            }
        }
        return List.copyOf(columns);
    }

    private static Map<String, List<ColumnSignature>> historyLayout() {
        Map<String, List<ColumnSignature>> tables = new LinkedHashMap<>();
        tables.put("partitions", columns(
                column("scope_id", "TEXT", false, 1),
                column("actor_id", "TEXT", true, 0),
                column("connection_kind", "TEXT", true, 0),
                column("selected_session", "TEXT", true, 0),
                column("capture_mode", "TEXT", true, 0),
                column("updated_at", "TEXT", true, 0)));
        tables.put("sessions", columns(
                column("scope_id", "TEXT", true, 1),
                column("session_id", "TEXT", true, 2),
                column("ordinal", "INTEGER", true, 0),
                column("model_selection_json", "TEXT", true, 0)));
        tables.put("requests", columns(
                column("scope_id", "TEXT", true, 1),
                column("session_id", "TEXT", true, 0),
                column("request_id", "TEXT", true, 2),
                column("sequence", "INTEGER", true, 0),
                column("topology", "TEXT", true, 0),
                column("model_selection_json", "TEXT", true, 0),
                column("user_message", "TEXT", true, 0),
                column("status", "TEXT", true, 0),
                column("model_usage_json", "TEXT", true, 0),
                column("usage_projection_json", "TEXT", true, 0),
                column("usage_origin_request_id", "TEXT", false, 0),
                column("retry_after_millis", "INTEGER", false, 0),
                column("failure_code", "TEXT", false, 0),
                column("failure_message", "TEXT", false, 0),
                column("created_at", "TEXT", true, 0),
                column("updated_at", "TEXT", true, 0),
                column("terminal_at", "TEXT", false, 0)));
        tables.put("messages", columns(
                column("scope_id", "TEXT", true, 1),
                column("session_id", "TEXT", true, 2),
                column("ordinal", "INTEGER", true, 3),
                column("request_id", "TEXT", true, 0),
                column("role", "TEXT", true, 0),
                column("message_text", "TEXT", true, 0),
                column("created_at", "TEXT", true, 0)));
        tables.put("timeline_entries", payloadTableSignature());
        tables.put("request_sources", payloadTableSignature());
        tables.put("compaction_checkpoints", columns(
                column("scope_id", "TEXT", true, 1),
                column("session_id", "TEXT", true, 2),
                column("ordinal", "INTEGER", true, 3),
                column("checkpoint_id", "TEXT", true, 0),
                column("payload_json", "TEXT", true, 0)));
        tables.put("model_context", columns(
                column("scope_id", "TEXT", true, 1),
                column("session_id", "TEXT", true, 2),
                column("payload_json", "TEXT", true, 0)));
        tables.put("request_model_context", columns(
                column("scope_id", "TEXT", true, 1),
                column("request_id", "TEXT", true, 2),
                column("payload_json", "TEXT", true, 0)));
        return Map.copyOf(tables);
    }

    private static List<ColumnSignature> payloadTableSignature() {
        return columns(
                column("scope_id", "TEXT", true, 1),
                column("request_id", "TEXT", true, 2),
                column("ordinal", "INTEGER", true, 3),
                column("payload_json", "TEXT", true, 0));
    }

    private static List<ColumnSignature> columns(ColumnSignature... columns) {
        return List.of(columns);
    }

    private static ColumnSignature column(
            String name, String type, boolean notNull, int primaryKeyPosition) {
        return new ColumnSignature(name, type, notNull, primaryKeyPosition, 0);
    }

    private static GuideHistoryException corruptLayout(String message) {
        return new GuideHistoryException("history_corrupt", message + "; database was not changed");
    }

    private static void createLayout(Connection connection) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            createLayoutObjects(connection);
            connection.commit();
        } catch (SQLException failure) {
            rollback(connection, failure);
            throw failure;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    private static void createLayoutObjects(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    create table partitions(
                        scope_id text primary key,
                        actor_id text not null,
                        connection_kind text not null,
                        selected_session text not null,
                        capture_mode text not null check(capture_mode = 'NORMAL'),
                        updated_at text not null
                    )
                    """);
            statement.execute("""
                    create table sessions(
                        scope_id text not null,
                        session_id text not null,
                        ordinal integer not null check(ordinal >= 0),
                        model_selection_json text not null,
                        primary key(scope_id, session_id),
                        unique(scope_id, ordinal),
                        foreign key(scope_id) references partitions(scope_id) on delete cascade
                    )
                    """);
            statement.execute("""
                    create table requests(
                        scope_id text not null,
                        session_id text not null,
                        request_id text not null,
                        sequence integer not null check(sequence >= 0),
                        topology text not null,
                        model_selection_json text not null,
                        user_message text not null,
                        status text not null,
                        model_usage_json text not null,
                        usage_projection_json text not null,
                        usage_origin_request_id text,
                        retry_after_millis integer,
                        failure_code text,
                        failure_message text,
                        created_at text not null,
                        updated_at text not null,
                        terminal_at text,
                        primary key(scope_id, request_id),
                        unique(scope_id, session_id, sequence),
                        foreign key(scope_id, session_id)
                            references sessions(scope_id, session_id) on delete cascade
                    )
                    """);
            statement.execute("""
                    create table messages(
                        scope_id text not null,
                        session_id text not null,
                        ordinal integer not null check(ordinal >= 0),
                        request_id text not null,
                        role text not null,
                        message_text text not null,
                        created_at text not null,
                        primary key(scope_id, session_id, ordinal),
                        foreign key(scope_id, session_id)
                            references sessions(scope_id, session_id) on delete cascade,
                        foreign key(scope_id, request_id)
                            references requests(scope_id, request_id) on delete cascade
                    )
                    """);
            statement.execute("""
                    create table timeline_entries(
                        scope_id text not null,
                        request_id text not null,
                        ordinal integer not null check(ordinal >= 0),
                        payload_json text not null,
                        primary key(scope_id, request_id, ordinal),
                        foreign key(scope_id, request_id)
                            references requests(scope_id, request_id) on delete cascade
                    )
                    """);
            statement.execute("""
                    create table request_sources(
                        scope_id text not null,
                        request_id text not null,
                        ordinal integer not null check(ordinal >= 0),
                        payload_json text not null,
                        primary key(scope_id, request_id, ordinal),
                        foreign key(scope_id, request_id)
                            references requests(scope_id, request_id) on delete cascade
                    )
                    """);
            statement.execute("create index sessions_updated_lookup on sessions(scope_id, ordinal)");
            statement.execute("create index requests_order_lookup on requests(scope_id, session_id, sequence)");
            statement.execute("create index timeline_order_lookup on timeline_entries(scope_id, request_id, ordinal)");
            createCheckpointTable(statement);
            statement.execute("""
                    create table model_context(
                        scope_id text not null,
                        session_id text not null,
                        payload_json text not null,
                        primary key(scope_id, session_id),
                        foreign key(scope_id, session_id)
                            references sessions(scope_id, session_id) on delete cascade
                    )
                    """);
            statement.execute("""
                    create table request_model_context(
                        scope_id text not null,
                        request_id text not null,
                        payload_json text not null,
                        primary key(scope_id, request_id),
                        foreign key(scope_id, request_id)
                            references requests(scope_id, request_id) on delete cascade
                    )
                    """);
        }
    }

    private static void createCheckpointTable(Statement statement) throws SQLException {
        statement.execute("""
                create table compaction_checkpoints(
                    scope_id text not null,
                    session_id text not null,
                    ordinal integer not null check(ordinal >= 0),
                    checkpoint_id text not null,
                    payload_json text not null,
                    primary key(scope_id, session_id, ordinal),
                    unique(scope_id, checkpoint_id),
                    foreign key(scope_id, session_id)
                        references sessions(scope_id, session_id) on delete cascade
                )
                """);
        statement.execute("""
                create index checkpoint_order_lookup
                on compaction_checkpoints(scope_id, session_id, ordinal)
                """);
    }

    private static void deletePartition(Connection connection, GuideHistoryScope scope)
            throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement(
                "delete from partitions where scope_id = ? and actor_id = ?")) {
            delete.setString(1, scope.scopeId());
            delete.setString(2, scope.actorId().toString());
            delete.executeUpdate();
        }
    }

    private static void deleteActor(Connection connection, UUID actorId) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement(
                "delete from partitions where actor_id = ?")) {
            delete.setString(1, actorId.toString());
            delete.executeUpdate();
        }
    }

    private static List<String> applicationTables(Connection connection) throws SQLException {
        List<String> tables = new ArrayList<>();
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("""
                        select name from sqlite_master
                        where type = 'table' and name not glob 'sqlite_*'
                        order by name
                        """)) {
            while (result.next()) {
                tables.add(result.getString(1));
            }
        }
        return List.copyOf(tables);
    }

    private static String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private static GuideHistoryException deleteFailure(Throwable failure) {
        return new GuideHistoryException(
                "history_delete_failed", "Unable to delete durable guide history", failure);
    }

    private void applyMutation(
            Connection connection,
            GuideHistoryScope scope,
            GuideHistoryMutation mutation) throws SQLException {
        String scopeId = scope.scopeId();
        switch (mutation) {
            case GuideHistoryMutation.UpsertPartition partition -> {
                try (PreparedStatement statement = connection.prepareStatement("""
                        insert into partitions(
                            scope_id, actor_id, connection_kind, selected_session,
                            capture_mode, updated_at)
                        values (?, ?, ?, ?, 'NORMAL', ?)
                        on conflict(scope_id) do update set
                            selected_session = excluded.selected_session,
                            updated_at = excluded.updated_at
                        """)) {
                    statement.setString(1, scopeId);
                    statement.setString(2, scope.actorId().toString());
                    statement.setString(3, scope.kind().name());
                    statement.setString(4, partition.selectedSession());
                    statement.setString(5, partition.updatedAt().toString());
                    statement.executeUpdate();
                }
            }
            case GuideHistoryMutation.UpsertSession session -> {
                try (PreparedStatement statement = connection.prepareStatement("""
                        insert into sessions(scope_id, session_id, ordinal, model_selection_json)
                        values (?, ?, ?, ?)
                        on conflict(scope_id, session_id) do update set
                            ordinal = excluded.ordinal,
                            model_selection_json = excluded.model_selection_json
                        """)) {
                    statement.setString(1, scopeId);
                    statement.setString(2, session.sessionId());
                    statement.setInt(3, session.ordinal());
                    statement.setString(4, codec.encodeModelSelection(session.modelSelection()));
                    statement.executeUpdate();
                }
            }
            case GuideHistoryMutation.UpsertRequest request ->
                    upsertRequest(connection, scopeId, request.sequence(), request.request());
            case GuideHistoryMutation.UpsertMessage message ->
                    upsertMessage(connection, scopeId, message);
            case GuideHistoryMutation.UpsertTimelineEntry timeline -> {
                try (PreparedStatement statement = connection.prepareStatement("""
                        insert into timeline_entries(scope_id, request_id, ordinal, payload_json)
                        values (?, ?, ?, ?)
                        on conflict(scope_id, request_id, ordinal) do update set
                            payload_json = excluded.payload_json
                        """)) {
                    statement.setString(1, scopeId);
                    statement.setString(2, timeline.requestId().toString());
                    statement.setInt(3, timeline.entry().ordinal());
                    statement.setString(4, codec.encodeEntry(timeline.entry()));
                    statement.executeUpdate();
                }
            }
            case GuideHistoryMutation.ReplaceRequestSources sources -> {
                try (PreparedStatement delete = connection.prepareStatement("""
                        delete from request_sources where scope_id = ? and request_id = ?
                        """)) {
                    delete.setString(1, scopeId);
                    delete.setString(2, sources.requestId().toString());
                    delete.executeUpdate();
                }
                for (int ordinal = 0; ordinal < sources.sources().size(); ordinal++) {
                    try (PreparedStatement insert = connection.prepareStatement("""
                            insert into request_sources(
                                scope_id, request_id, ordinal, payload_json)
                            values (?, ?, ?, ?)
                            """)) {
                        insert.setString(1, scopeId);
                        insert.setString(2, sources.requestId().toString());
                        insert.setInt(3, ordinal);
                        insert.setString(4, codec.encodeSources(
                                List.of(sources.sources().get(ordinal))));
                        insert.executeUpdate();
                    }
                }
            }
            case GuideHistoryMutation.ReplaceContext context -> {
                try (PreparedStatement statement = connection.prepareStatement("""
                        insert into model_context(scope_id, session_id, payload_json)
                        values (?, ?, ?)
                        on conflict(scope_id, session_id) do update set
                            payload_json = excluded.payload_json
                        """)) {
                    statement.setString(1, scopeId);
                    statement.setString(2, context.sessionId());
                    statement.setString(3, modelContexts.encode(context.messages()));
                    statement.executeUpdate();
                }
            }
            case GuideHistoryMutation.ReplaceRequestContext context -> {
                try (PreparedStatement statement = connection.prepareStatement("""
                        insert into request_model_context(scope_id, request_id, payload_json)
                        values (?, ?, ?)
                        on conflict(scope_id, request_id) do update set
                            payload_json = excluded.payload_json
                        """)) {
                    statement.setString(1, scopeId);
                    statement.setString(2, context.requestId().toString());
                    statement.setString(3, modelContexts.encode(context.messages()));
                    statement.executeUpdate();
                }
            }
            case GuideHistoryMutation.UpsertCheckpoint checkpoint -> {
                try (PreparedStatement statement = connection.prepareStatement("""
                        insert into compaction_checkpoints(
                            scope_id, session_id, ordinal, checkpoint_id, payload_json)
                        values (?, ?, ?, ?, ?)
                        on conflict(scope_id, session_id, ordinal) do update set
                            checkpoint_id = excluded.checkpoint_id,
                            payload_json = excluded.payload_json
                        """)) {
                    statement.setString(1, scopeId);
                    statement.setString(2, checkpoint.sessionId());
                    statement.setInt(3, checkpoint.ordinal());
                    statement.setString(4, checkpoint.checkpoint().checkpointId().toString());
                    statement.setString(5, codec.encodeCheckpoint(checkpoint.checkpoint()));
                    statement.executeUpdate();
                }
            }
            case GuideHistoryMutation.DeleteSession session -> {
                try (PreparedStatement statement = connection.prepareStatement("""
                        delete from sessions where scope_id = ? and session_id = ?
                        """)) {
                    statement.setString(1, scopeId);
                    statement.setString(2, session.sessionId());
                    statement.executeUpdate();
                }
            }
            case GuideHistoryMutation.ClearSession session -> {
                for (String table : List.of("messages", "compaction_checkpoints", "model_context")) {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "delete from " + table + " where scope_id = ? and session_id = ?")) {
                        statement.setString(1, scopeId);
                        statement.setString(2, session.sessionId());
                        statement.executeUpdate();
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        delete from requests where scope_id = ? and session_id = ?
                        """)) {
                    statement.setString(1, scopeId);
                    statement.setString(2, session.sessionId());
                    statement.executeUpdate();
                }
            }
        }
    }

    private void upsertRequest(
            Connection connection,
            String scopeId,
            long sequence,
            GuideRequestSnapshot request) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into requests(
                    scope_id, session_id, request_id, sequence, topology,
                    model_selection_json, user_message, status,
                    model_usage_json, usage_projection_json, usage_origin_request_id,
                    retry_after_millis, failure_code, failure_message,
                    created_at, updated_at, terminal_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict(scope_id, request_id) do update set
                    session_id = excluded.session_id,
                    sequence = excluded.sequence,
                    topology = excluded.topology,
                    model_selection_json = excluded.model_selection_json,
                    user_message = excluded.user_message,
                    status = excluded.status,
                    model_usage_json = excluded.model_usage_json,
                    usage_projection_json = excluded.usage_projection_json,
                    usage_origin_request_id = excluded.usage_origin_request_id,
                    retry_after_millis = excluded.retry_after_millis,
                    failure_code = excluded.failure_code,
                    failure_message = excluded.failure_message,
                    created_at = excluded.created_at,
                    updated_at = excluded.updated_at,
                    terminal_at = excluded.terminal_at
                """)) {
            statement.setString(1, scopeId);
            statement.setString(2, request.sessionId());
            statement.setString(3, request.requestId().toString());
            statement.setLong(4, sequence);
            statement.setString(5, request.topology().name());
            statement.setString(6, codec.encodeModelSelection(request.modelSelection()));
            statement.setString(7, request.userMessage());
            statement.setString(8, request.status().name());
            statement.setString(9, codec.encodeModelUsage(request.usage()));
            statement.setString(10, codec.encodeUsageProjection(request.usageProjection()));
            statement.setString(11, request.usageOriginRequestId() == null
                    ? null : request.usageOriginRequestId().toString());
            nullableLong(statement, 12, request.retryAfterMillis());
            statement.setString(13, request.failure() == null ? null : request.failure().code());
            statement.setString(14, request.failure() == null ? null : request.failure().message());
            statement.setString(15, request.createdAt().toString());
            statement.setString(16, request.updatedAt().toString());
            statement.setString(17,
                    request.terminalAt() == null ? null : request.terminalAt().toString());
            statement.executeUpdate();
        }
    }

    private static void upsertMessage(
            Connection connection,
            String scopeId,
            GuideHistoryMutation.UpsertMessage mutation) throws SQLException {
        GuideMessage message = mutation.message();
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into messages(
                    scope_id, session_id, ordinal, request_id, role, message_text, created_at)
                values (?, ?, ?, ?, ?, ?, ?)
                on conflict(scope_id, session_id, ordinal) do update set
                    request_id = excluded.request_id,
                    role = excluded.role,
                    message_text = excluded.message_text,
                    created_at = excluded.created_at
                """)) {
            statement.setString(1, scopeId);
            statement.setString(2, mutation.sessionId());
            statement.setInt(3, mutation.ordinal());
            statement.setString(4, message.requestId().toString());
            statement.setString(5, message.role().name());
            statement.setString(6, message.text());
            statement.setString(7, message.createdAt().toString());
            statement.executeUpdate();
        }
    }

    private GuideHistoryCursor cursor(
            Connection connection,
            String scopeId,
            String sessionId,
            long sequence) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("""
                select request_id from requests
                where scope_id = ? and session_id = ? and sequence = ?
                """)) {
            query.setString(1, scopeId);
            query.setString(2, sessionId);
            query.setLong(3, sequence);
            try (ResultSet result = query.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalArgumentException("history cursor row is missing");
                }
                return new GuideHistoryCursor(sequence,
                        UUID.fromString(result.getString("request_id")));
            }
        }
    }

    private List<SequencedRequest> readPage(
            Connection connection,
            GuideHistoryPageRequest request) throws SQLException {
        if (request.cursor() != null) {
            requireCursor(connection, request);
        }
        String comparison = switch (request.direction()) {
            case NEWEST -> "";
            case BEFORE -> " and sequence < ?";
            case AFTER -> " and sequence > ?";
        };
        String order = request.direction() == GuideHistoryPageRequest.Direction.AFTER
                ? " order by sequence asc limit ?"
                : " order by sequence desc limit ?";
        String sql = requestColumns()
                + " from requests where scope_id = ? and session_id = ?"
                + comparison + order;
        List<SequencedRequest> loaded = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setString(1, request.scope().scopeId());
            query.setString(2, request.sessionId());
            int next = 3;
            if (request.cursor() != null) {
                query.setLong(next++, request.cursor().sequence());
            }
            query.setInt(next, request.count());
            try (ResultSet result = query.executeQuery()) {
                while (result.next()) {
                    loaded.add(readRequestRow(
                            connection, request.scope().scopeId(), result));
                }
            }
        }
        if (request.direction() != GuideHistoryPageRequest.Direction.AFTER) {
            java.util.Collections.reverse(loaded);
        }
        return List.copyOf(loaded);
    }

    private void requireCursor(Connection connection, GuideHistoryPageRequest request)
            throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("""
                select request_id from requests
                where scope_id = ? and session_id = ? and sequence = ?
                """)) {
            query.setString(1, request.scope().scopeId());
            query.setString(2, request.sessionId());
            query.setLong(3, request.cursor().sequence());
            try (ResultSet result = query.executeQuery()) {
                if (!result.next() || !request.cursor().requestId().toString()
                        .equals(result.getString("request_id"))) {
                    throw new GuideHistoryException(
                            "history_cursor_stale", "Guide history cursor is stale");
                }
            }
        }
    }

    private static boolean requestExists(
            Connection connection,
            String scopeId,
            String sessionId,
            String comparison,
            long sequence) throws SQLException {
        if (!comparison.equals("sequence < ?") && !comparison.equals("sequence > ?")) {
            throw new IllegalArgumentException("unsupported history comparison");
        }
        try (PreparedStatement query = connection.prepareStatement(
                "select 1 from requests where scope_id = ? and session_id = ? and "
                        + comparison + " limit 1")) {
            query.setString(1, scopeId);
            query.setString(2, sessionId);
            query.setLong(3, sequence);
            try (ResultSet result = query.executeQuery()) {
                return result.next();
            }
        }
    }

    private static String requestColumns() {
        return """
                select sequence, session_id, request_id, topology, user_message, status,
                       model_selection_json,
                       model_usage_json, usage_projection_json, usage_origin_request_id,
                       retry_after_millis, failure_code, failure_message,
                       created_at, updated_at, terminal_at
                """;
    }

    private SequencedRequest readRequestRow(
            Connection connection,
            String scopeId,
            ResultSet result) throws SQLException {
        UUID requestId = UUID.fromString(result.getString("request_id"));
        String sessionId = required(result.getString("session_id"), "request session");
        GuideFailure failure = result.getString("failure_code") == null
                ? null
                : new GuideFailure(
                        result.getString("failure_code"),
                        required(result.getString("failure_message"), "failure message"));
        long sequence = result.getLong("sequence");
        GuideRequestSnapshot request = new GuideRequestSnapshot(
                requestId,
                sessionId,
                GuideTopology.valueOf(result.getString("topology")),
                result.getString("user_message"),
                readTimeline(connection, scopeId, requestId),
                GuideRequestStatus.valueOf(result.getString("status")),
                readSources(connection, scopeId, requestId),
                codec.decodeModelUsage(result.getString("model_usage_json")),
                nullableLong(result, "retry_after_millis"),
                failure,
                Instant.parse(result.getString("created_at")),
                Instant.parse(result.getString("updated_at")),
                nullableInstant(result.getString("terminal_at")),
                codec.decodeModelSelection(result.getString("model_selection_json")),
                GuideRequestSnapshot.legacyProgress(
                        GuideRequestStatus.valueOf(result.getString("status")),
                        nullableLong(result, "retry_after_millis"),
                        Instant.parse(result.getString("created_at")),
                        Instant.parse(result.getString("updated_at"))),
                codec.decodeUsageProjection(result.getString("usage_projection_json")),
                result.getString("usage_origin_request_id") == null ? null
                        : UUID.fromString(result.getString("usage_origin_request_id")));
        return new SequencedRequest(new GuideHistoryCursor(sequence, requestId), request);
    }

    private List<ContextCheckpoint> readApplicableCheckpoint(
            Connection connection,
            String scopeId,
            String sessionId,
            String modelIdentifier,
            List<ModelMessage> messages) throws SQLException {
        List<ContextStructure.Unit> units = ContextStructure.units(messages);
        try (PreparedStatement query = connection.prepareStatement("""
                select checkpoint_id, payload_json from compaction_checkpoints
                where scope_id = ? and session_id = ? order by ordinal desc
                """)) {
            query.setString(1, scopeId);
            query.setString(2, sessionId);
            try (ResultSet result = query.executeQuery()) {
                while (result.next()) {
                    ContextCheckpoint checkpoint = codec.decodeCheckpoint(
                            result.getString("payload_json"));
                    if (!checkpoint.checkpointId().toString()
                            .equals(result.getString("checkpoint_id"))) {
                        throw new IllegalArgumentException("durable checkpoint row identity does not match");
                    }
                    if (checkpoint.status() != ContextCheckpoint.Status.SUCCEEDED
                            || !checkpoint.modelIdentifier().equals(modelIdentifier)
                            || checkpoint.sourceFromIndex() != 0
                            || checkpoint.sourceToIndexExclusive() > messages.size()) {
                        continue;
                    }
                    boolean boundary = checkpoint.sourceToIndexExclusive() == messages.size()
                            || units.stream().anyMatch(unit -> unit.fromIndex()
                                    == checkpoint.sourceToIndexExclusive());
                    if (boundary && checkpoint.sourceHash().equals(ContextSourceHash.compute(
                            new Gson(), messages.subList(0, checkpoint.sourceToIndexExclusive())))) {
                        return List.of(checkpoint);
                    }
                }
            }
        }
        return List.of();
    }

    private void recoverInterruptedRows(Connection connection, GuideHistoryScope scope)
            throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        Instant recoveredAt = clock.instant();
        try {
            List<InterruptedRequest> active = new ArrayList<>();
            try (PreparedStatement query = connection.prepareStatement("""
                    select r.request_id, r.session_id, r.user_message,
                           r.sequence = (select max(latest.sequence) from requests latest
                               where latest.scope_id = r.scope_id
                                 and latest.session_id = r.session_id) as latest_request
                    from requests r where r.scope_id = ? and r.terminal_at is null
                    order by r.session_id, r.sequence
                    """)) {
                query.setString(1, scope.scopeId());
                try (ResultSet result = query.executeQuery()) {
                    while (result.next()) {
                        active.add(new InterruptedRequest(
                                UUID.fromString(result.getString("request_id")),
                                required(result.getString("session_id"), "interrupted request session"),
                                required(result.getString("user_message"), "interrupted request user message"),
                                result.getInt("latest_request") != 0));
                    }
                }
            }
            if (active.isEmpty()) {
                connection.commit();
                return;
            }
            boolean changed = false;
            for (InterruptedRequest request : active) {
                // Claim only still-active rows. Recovery never changes a terminal request.
                try (PreparedStatement update = connection.prepareStatement("""
                        update requests set status = 'INTERRUPTED',
                            retry_after_millis = null,
                            failure_code = 'request_interrupted',
                            failure_message = ?, updated_at = ?, terminal_at = ?
                        where scope_id = ? and request_id = ? and terminal_at is null
                        """)) {
                    update.setString(1, INTERRUPTION_MESSAGE);
                    update.setString(2, recoveredAt.toString());
                    update.setString(3, recoveredAt.toString());
                    update.setString(4, scope.scopeId());
                    update.setString(5, request.requestId().toString());
                    if (update.executeUpdate() == 0) {
                        continue;
                    }
                }
                changed = true;
                for (GuideTimelineEntry entry : readTimeline(
                        connection, scope.scopeId(), request.requestId())) {
                    if (entry instanceof GuideTimelineEntry.Assistant assistant
                            && assistant.streaming()) {
                        GuideTimelineEntry.Assistant closed = new GuideTimelineEntry.Assistant(
                                assistant.ordinal(), assistant.text(), assistant.semantic(),
                                false, assistant.sources());
                        try (PreparedStatement update = connection.prepareStatement("""
                                update timeline_entries set payload_json = ?
                                where scope_id = ? and request_id = ? and ordinal = ?
                                """)) {
                            update.setString(1, codec.encodeEntry(closed));
                            update.setString(2, scope.scopeId());
                            update.setString(3, request.requestId().toString());
                            update.setInt(4, closed.ordinal());
                            update.executeUpdate();
                        }
                    }
                }
                List<ModelMessage> original = readRequestContext(
                        connection, scope.scopeId(), request.requestId());
                boolean missingOriginal = original.isEmpty();
                ModelMessage acceptedUser = ModelMessage.userText(request.userMessage());
                if (missingOriginal) {
                    // Accepted request text is known input, not a reversed display/tool projection.
                    original = List.of(acceptedUser);
                }
                List<ModelMessage> recoveredOriginal = withInterruptionNote(original);
                applyMutation(connection, scope, new GuideHistoryMutation.ReplaceRequestContext(
                        request.requestId(), recoveredOriginal));
                if (request.latestRequest()) {
                    // Older interrupted rows must not replace a newer session snapshot.
                    List<ModelMessage> current = readContext(
                            connection, scope.scopeId(), request.sessionId());
                    if (current.isEmpty()) {
                        current = original;
                    } else if (missingOriginal && !current.getLast().equals(acceptedUser)
                            && !(current.size() >= 2 && current.getLast().equals(INTERRUPTION_NOTE)
                                    && current.get(current.size() - 2).equals(acceptedUser))) {
                        List<ModelMessage> withUser = new ArrayList<>(current);
                        withUser.add(acceptedUser);
                        current = List.copyOf(withUser);
                    }
                    applyMutation(connection, scope, new GuideHistoryMutation.ReplaceContext(
                            request.sessionId(), withInterruptionNote(current)));
                }
            }
            if (changed) {
                try (PreparedStatement update = connection.prepareStatement("""
                        update partitions set updated_at = ? where scope_id = ?
                        """)) {
                    update.setString(1, recoveredAt.toString());
                    update.setString(2, scope.scopeId());
                    update.executeUpdate();
                }
                failureInjector.beforeCommit(Mutation.RECOVER);
            }
            connection.commit();
        } catch (SQLException | RuntimeException failure) {
            rollback(connection, failure);
            throw failure;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    private static List<ModelMessage> withInterruptionNote(List<ModelMessage> messages) {
        if (!messages.isEmpty() && messages.getLast().equals(INTERRUPTION_NOTE)) {
            return messages;
        }
        List<ModelMessage> retained = new ArrayList<>(messages);
        retained.add(INTERRUPTION_NOTE);
        return List.copyOf(retained);
    }

    private static PartitionHeader readHeader(Connection connection, GuideHistoryScope scope)
            throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("""
                select actor_id, connection_kind, selected_session, capture_mode, updated_at
                from partitions where scope_id = ?
                """)) {
            query.setString(1, scope.scopeId());
            try (ResultSet result = query.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                if (!scope.actorId().equals(UUID.fromString(result.getString("actor_id")))
                        || scope.kind() != GuideHistoryScope.Kind.valueOf(
                                result.getString("connection_kind"))) {
                    throw new IllegalArgumentException("durable history scope metadata does not match");
                }
                if (!"NORMAL".equals(result.getString("capture_mode"))) {
                    throw new IllegalArgumentException("unsupported durable capture mode");
                }
                return new PartitionHeader(
                        result.getString("selected_session"),
                        Instant.parse(result.getString("updated_at")));
            }
        }
    }

    private List<GuideTimelineEntry> readTimeline(
            Connection connection, String scopeId, UUID requestId) throws SQLException {
        List<GuideTimelineEntry> timeline = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement("""
                select ordinal, payload_json from timeline_entries
                where scope_id = ? and request_id = ? order by ordinal
                """)) {
            query.setString(1, scopeId);
            query.setString(2, requestId.toString());
            try (ResultSet result = query.executeQuery()) {
                while (result.next()) {
                    GuideTimelineEntry decoded = codec.decodeEntry(result.getString("payload_json"));
                    if (decoded.ordinal() != result.getInt("ordinal")) {
                        throw new IllegalArgumentException("durable timeline row identity does not match");
                    }
                    timeline.add(decoded);
                }
            }
        }
        for (int ordinal = 0; ordinal < timeline.size(); ordinal++) {
            if (timeline.get(ordinal).ordinal() != ordinal) {
                throw new IllegalArgumentException("durable timeline ordinals are not contiguous");
            }
        }
        return List.copyOf(timeline);
    }

    private List<GuideSource> readSources(
            Connection connection, String scopeId, UUID requestId) throws SQLException {
        List<GuideSource> sources = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement("""
                select ordinal, payload_json from request_sources
                where scope_id = ? and request_id = ? order by ordinal
                """)) {
            query.setString(1, scopeId);
            query.setString(2, requestId.toString());
            try (ResultSet result = query.executeQuery()) {
                int expected = 0;
                while (result.next()) {
                    if (result.getInt("ordinal") != expected++) {
                        throw new IllegalArgumentException("durable source ordinals are not contiguous");
                    }
                    List<GuideSource> decoded = codec.decodeSources(result.getString("payload_json"));
                    if (decoded.size() != 1) {
                        throw new IllegalArgumentException("durable source row must contain one source");
                    }
                    sources.add(decoded.getFirst());
                }
            }
        }
        return List.copyOf(sources);
    }

    private static Long nullableLong(ResultSet result, String field) throws SQLException {
        long value = result.getLong(field);
        return result.wasNull() ? null : value;
    }

    private static void nullableLong(PreparedStatement statement, int index, Long value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.BIGINT);
        } else {
            statement.setLong(index, value);
        }
    }

    private static Instant nullableInstant(String value) {
        return value == null ? null : Instant.parse(value);
    }

    private static String required(String value, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + " is missing");
        }
        return value;
    }

    private static void rollback(Connection connection, Throwable original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    private record InterruptedRequest(
            UUID requestId, String sessionId, String userMessage, boolean latestRequest) {}

    private record PartitionHeader(
            String selectedSession,
            Instant updatedAt) {}

    private record SequencedRequest(
            GuideHistoryCursor cursor,
            GuideRequestSnapshot request) {}

    private record ColumnSignature(
            String name,
            String type,
            boolean notNull,
            int primaryKeyPosition,
            int hidden) {}

    private record ForeignKeySignature(
            int sequence,
            String owner,
            String fromColumn,
            String toColumn,
            String onUpdate,
            String onDelete,
            String match) {}

    enum Mutation {
        COMMIT,
        RECOVER,
        DELETE,
        RESET
    }

    @FunctionalInterface
    interface FailureInjector {
        void beforeCommit(Mutation mutation) throws SQLException;
    }

}
