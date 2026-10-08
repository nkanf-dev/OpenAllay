package dev.openallay.guide.history;

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
import dev.openallay.model.image.ImageAttachmentStore;
import dev.openallay.model.image.ImageReference;
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
            dev.openallay.util.Java8Collections.listOf(new ModelContent.Text(
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
    private final GuideImageOwnership imageOwnership;
    private final java.util.concurrent.atomic.AtomicBoolean reportedUnsupportedLayout =
            new java.util.concurrent.atomic.AtomicBoolean();
    // Accessed only under the image ownership lock. Ordinary UI/status work must not decode
    // every historical image repeatedly; failed reconciliation invalidates this scope cache.
    private final Set<GuideHistoryScope> initializedImageScopes = new java.util.HashSet<>();

    public SqliteGuideHistoryStore(Path database, Clock clock, GuideHistoryCodec codec) {
        this(database, clock, codec, null, ignored -> {});
    }

    public SqliteGuideHistoryStore(
            Path database, Clock clock, GuideHistoryCodec codec, ImageAttachmentStore images) {
        this(database, clock, codec, images, ignored -> {});
    }

    SqliteGuideHistoryStore(
            Path database, Clock clock, GuideHistoryCodec codec, FailureInjector failureInjector) {
        this(database, clock, codec, null, failureInjector);
    }

    SqliteGuideHistoryStore(
            Path database, Clock clock, GuideHistoryCodec codec,
            ImageAttachmentStore images, FailureInjector failureInjector) {
        this.database = Objects.requireNonNull(database, "database").toAbsolutePath().normalize();
        this.clock = Objects.requireNonNull(clock, "clock");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.failureInjector = Objects.requireNonNull(failureInjector, "failureInjector");
        this.imageOwnership = new GuideImageOwnership(this.database, images);
    }

    @Override
    public Optional<GuideHistoryMetadata> metadata(GuideHistoryScope scope) {
        Objects.requireNonNull(scope, "scope");
        try (GuideImageOwnership.Guard ignored = imageOwnership.lock();
                Connection connection = open()) {
            ensureImageOwnership(connection, scope);
            PartitionHeader header = readHeader(connection, scope);
            if (header == null) {
                return Optional.empty();
            }
            recoverInterruptedRows(connection, scope);
            header = readHeader(connection, scope);
            List<GuideHistoryMetadata.Session> sessions = new ArrayList<>();
            try (PreparedStatement query = connection.prepareStatement("select s.session_id, s.ordinal, s.model_selection_json, s.control_usage_json,\n       count(r.request_id) as request_count,\n       min(r.sequence) as first_sequence,\n       max(r.sequence) as last_sequence,\n       (select coalesce(max(m.ordinal), -1) + 1 from messages m\n        where m.scope_id = s.scope_id and m.session_id = s.session_id) as message_count\nfrom sessions s\nleft join requests r\n  on r.scope_id = s.scope_id and r.session_id = s.session_id\nwhere s.scope_id = ?\ngroup by s.session_id, s.ordinal, s.model_selection_json, s.control_usage_json\norder by s.ordinal\n")) {
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
                                sessionUsage(connection, scope.scopeId(), result.getString("session_id"), false)
                                        .plus(codec.decodeUsageProjection(result.getString("control_usage_json"))),
                                sessionUsage(connection, scope.scopeId(), result.getString("session_id"), true),
                                codec.decodeUsageProjection(result.getString("control_usage_json")),
                                result.getInt("message_count")));
                    }
                }
            }
            return Optional.of(new GuideHistoryMetadata(
                    scope, header.selectedSession, sessions, header.updatedAt));
        } catch (SQLException | IOException failure) {
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
        try (PreparedStatement query = connection.prepareStatement("select usage_projection_json from requests\nwhere scope_id = ? and session_id = ? and\n" + (inherited ? "usage_origin_request_id is not null" : "usage_origin_request_id is null"))) {
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
        try (GuideImageOwnership.Guard ignored = imageOwnership.lock();
                Connection connection = open()) {
            ensureImageOwnership(connection, request.scope());
            List<SequencedRequest> loaded = readPage(connection, request);
            List<GuideRequestSnapshot> snapshots = dev.openallay.util.Java8Collections.toList(loaded.stream()
                    .map(SequencedRequest::request));
            GuideHistoryCursor first = loaded.isEmpty() ? null : loaded.get(0).cursor();
            GuideHistoryCursor last = loaded.isEmpty() ? null : loaded.get(loaded.size() - 1).cursor();
            boolean hasEarlier = first != null && requestExists(
                    connection, request.scope().scopeId(), request.sessionId(),
                    "sequence < ?", first.sequence());
            boolean hasLater = last != null && requestExists(
                    connection, request.scope().scopeId(), request.sessionId(),
                    "sequence > ?", last.sequence());
            return new GuideHistoryPage(
                    request.sessionId(), snapshots, first, last, hasEarlier, hasLater);
        } catch (SQLException | IOException failure) {
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
        try (GuideImageOwnership.Guard ignored = imageOwnership.lock();
                Connection connection = open()) {
            ensureImageOwnership(connection, request.scope());
            if (readHeader(connection, request.scope()) == null) {
                return new GuideHistoryContextSeed(request.sessionId(), dev.openallay.util.Java8Collections.listOf(), dev.openallay.util.Java8Collections.listOf(), 0);
            }
            List<ModelMessage> messages = readContext(
                    connection, request.scope().scopeId(), request.sessionId());
            if (messages.isEmpty()) {
                return new GuideHistoryContextSeed(request.sessionId(), dev.openallay.util.Java8Collections.listOf(), dev.openallay.util.Java8Collections.listOf(), 0);
            }
            // Do not discard over-budget structural units. Agent owns reduction and compaction.
            int estimated = request.estimator().estimate("", messages, dev.openallay.util.Java8Collections.listOf());
            List<ContextCheckpoint> checkpoints = readApplicableCheckpoint(
                    connection, request.scope().scopeId(), request.sessionId(),
                    request.modelIdentifier(), messages);
            return new GuideHistoryContextSeed(
                    request.sessionId(), messages, checkpoints, estimated);
        } catch (SQLException | IOException failure) {
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
        try (GuideImageOwnership.Guard ignored = imageOwnership.lock();
                Connection connection = open()) {
            ensureImageOwnership(connection, scope);
            if (readHeader(connection, scope) == null) {
                return dev.openallay.util.Java8Collections.listOf();
            }
            return readRequestContext(connection, scope.scopeId(), requestId);
        } catch (SQLException | IOException failure) {
            throw new GuideHistoryException(
                    "history_context_failed", "Unable to load original guide request context", failure);
        } catch (IllegalArgumentException | JsonParseException malformed) {
            throw new GuideHistoryException(
                    "history_corrupt", "Original guide request context is malformed", malformed);
        }
    }

    private List<ModelMessage> readRequestContext(
            Connection connection, String scopeId, UUID requestId) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("select payload_json from request_model_context\nwhere scope_id = ? and request_id = ?\n")) {
            query.setString(1, scopeId);
            query.setString(2, requestId.toString());
            try (ResultSet result = query.executeQuery()) {
                return result.next() ? modelContexts.decode(result.getString("payload_json"))
                        : dev.openallay.util.Java8Collections.listOf();
            }
        }
    }

    private List<ModelMessage> readContext(
            Connection connection, String scopeId, String sessionId) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("select payload_json from model_context where scope_id = ? and session_id = ?\n")) {
            query.setString(1, scopeId);
            query.setString(2, sessionId);
            try (ResultSet result = query.executeQuery()) {
                return result.next() ? modelContexts.decode(result.getString("payload_json"))
                        : dev.openallay.util.Java8Collections.listOf();
            }
        }
    }

    @Override
    public void commit(GuideHistoryCommit commit) {
        Objects.requireNonNull(commit, "commit");
        boolean durable = false;
        try (GuideImageOwnership.Guard ignored = imageOwnership.lock();
                Connection connection = open()) {
            boolean ownershipChanges = commit.mutations().stream()
                    .anyMatch(SqliteGuideHistoryStore::changesImageOwnership);
            List<ImageReference> pending = new ArrayList<>();
            if (ownershipChanges) {
                reconcileScope(connection, commit.scope());
                pending.addAll(GuideImageOwnership.references(
                        readImageOwners(connection, commit.scope().scopeId())));
                for (GuideHistoryMutation mutation : commit.mutations()) {
                    {
final java.lang.Object $oaPattern0_value = mutation;
final boolean $oaPattern0_match = $oaPattern0_value instanceof GuideHistoryMutation.ReplaceContext;
GuideHistoryMutation.ReplaceContext $oaPattern0_bound = $oaPattern0_match ? (GuideHistoryMutation.ReplaceContext) $oaPattern0_value : null;
if ($oaPattern0_match) {
                        pending.addAll(GuideImageOwnership.references($oaPattern0_bound.messages()));
                    } else {
final java.lang.Object $oaPattern1_value = mutation;
final boolean $oaPattern1_match = $oaPattern1_value instanceof GuideHistoryMutation.ReplaceRequestContext;
GuideHistoryMutation.ReplaceRequestContext $oaPattern1_bound = $oaPattern1_match ? (GuideHistoryMutation.ReplaceRequestContext) $oaPattern1_value : null;
if ($oaPattern1_match) {
                        pending.addAll(GuideImageOwnership.references($oaPattern1_bound.messages()));
                    } else {
final java.lang.Object $oaPattern2_value = mutation;
final boolean $oaPattern2_match = $oaPattern2_value instanceof GuideHistoryMutation.CaptureRequestBoundary;
GuideHistoryMutation.CaptureRequestBoundary $oaPattern2_bound = $oaPattern2_match ? (GuideHistoryMutation.CaptureRequestBoundary) $oaPattern2_value : null;
if ($oaPattern2_match) {
                        pending.addAll(GuideImageOwnership.references($oaPattern2_bound.messages()));
                    }
}
}
}
                }
                // Verify and pin both existing and incoming assets before any SQL mutation.
                // The complete post-mutation owners are checked again before durable commit.
                imageOwnership.pin(commit.scope(), pending);
            } else {
                ensureImageOwnership(connection, commit.scope());
            }
            connection.setAutoCommit(false);
            try {
                for (GuideHistoryMutation mutation : commit.mutations()) {
                    applyMutation(connection, commit.scope(), mutation);
                }
                if (ownershipChanges) {
                    pending.addAll(GuideImageOwnership.references(
                            readImageOwners(connection, commit.scope().scopeId())));
                    imageOwnership.pin(commit.scope(), pending);
                }
                failureInjector.beforeCommit(Mutation.COMMIT);
                connection.commit();
                durable = true;
            } catch (SQLException | IOException | RuntimeException failure) {
                if (rollback(connection, failure) && ownershipChanges) {
                    recoverPinsAfterRollback(connection, commit.scope(), failure);
                }
                {
final java.lang.Object $oaPattern3_value = failure;
final boolean $oaPattern3_match = $oaPattern3_value instanceof GuideHistoryException;
GuideHistoryException $oaPattern3_bound = $oaPattern3_match ? (GuideHistoryException) $oaPattern3_value : null;
if ($oaPattern3_match) throw $oaPattern3_bound;
}
                throw new GuideHistoryException(
                        "history_write_failed", "Unable to commit durable guide history", failure);
            }
            // Filesystem and SQLite commits are not atomic. From this point the batch is
            // durable, so an IO failure must never report rollback or invite a batch retry.
            boolean removed = commit.mutations().stream().anyMatch(mutation ->
                    mutation instanceof GuideHistoryMutation.DeleteSession
                            || mutation instanceof GuideHistoryMutation.ClearSession);
            if (ownershipChanges) finishDurableImages(connection, dev.openallay.util.Java8Collections.listOf(commit.scope()), removed);
        } catch (SQLException | IOException failure) {
            if (durable) return; // Closing a durable JDBC transaction is not a failed commit.
            throw new GuideHistoryException(
                    "history_write_failed", "Unable to commit durable guide history", failure);
        } catch (IllegalArgumentException | JsonParseException malformed) {
            if (durable) return;
            throw new GuideHistoryException(
                    "history_corrupt", "Guide history context is malformed; database was not changed", malformed);
        }
    }

    @Override
    public GuideHistoryForkResult fork(GuideHistoryForkRequest request) {
        Objects.requireNonNull(request, "request");
        GuideHistoryForkResult durableResult = null;
        try (GuideImageOwnership.Guard ignored = imageOwnership.lock();
                Connection connection = open()) {
            reconcileScope(connection, request.scope());
            List<ImageReference> pending = new ArrayList<>(GuideImageOwnership.references(
                    readImageOwners(connection, request.scope().scopeId())));
            // The immutable cutoff and every inherited original/boundary transcript belong
            // to the complete source scope. Validate and retain all before creating a target.
            imageOwnership.pin(request.scope(), pending);
            connection.setAutoCommit(false);
            try {
                GuideHistoryForkResult result = applyForkSession(
                        connection, request.scope(), request.mutation());
                pending.addAll(GuideImageOwnership.references(
                        readImageOwners(connection, request.scope().scopeId())));
                imageOwnership.pin(request.scope(), pending);
                failureInjector.beforeCommit(Mutation.COMMIT);
                connection.commit();
                durableResult = result;
            } catch (SQLException | IOException | RuntimeException failure) {
                if (rollback(connection, failure)) {
                    recoverPinsAfterRollback(connection, request.scope(), failure);
                }
                {
final java.lang.Object $oaPattern4_value = failure;
final boolean $oaPattern4_match = $oaPattern4_value instanceof GuideHistoryException;
GuideHistoryException $oaPattern4_bound = $oaPattern4_match ? (GuideHistoryException) $oaPattern4_value : null;
if ($oaPattern4_match) throw $oaPattern4_bound;
}
                throw new GuideHistoryException(
                        "history_fork_failed", "Unable to fork durable guide history", failure);
            }
            // A fork cannot safely be replayed after durability. Manifest failures leave the
            // source+target write pins retained and still return the committed fork result.
            finishDurableImages(connection, dev.openallay.util.Java8Collections.listOf(request.scope()), false);
            return durableResult;
        } catch (SQLException | IOException failure) {
            if (durableResult != null) return durableResult;
            throw new GuideHistoryException(
                    "history_fork_failed", "Unable to fork durable guide history", failure);
        } catch (IllegalArgumentException | JsonParseException malformed) {
            if (durableResult != null) return durableResult;
            throw new GuideHistoryException(
                    "history_corrupt", "Guide history context is malformed; database was not changed", malformed);
        }
    }

    private GuideHistoryForkResult applyForkSession(
            Connection connection, GuideHistoryScope scope, GuideHistoryMutation.ForkSession fork)
            throws SQLException {
        String scopeId = scope.scopeId();
        if (readHeader(connection, scope) == null) {
            throw new GuideHistoryException("fork_unavailable", "The source partition does not exist");
        }
        try (PreparedStatement query = connection.prepareStatement(
                "select 1 from sessions where scope_id = ? and session_id = ?")) {
            query.setString(1, scopeId);
            query.setString(2, fork.sessionId());
            try (ResultSet result = query.executeQuery()) {
                if (result.next()) throw new GuideHistoryException(
                        "fork_target_exists", "The fork target session already exists");
            }
        }
        List<SequencedRequest> source = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement(requestColumns() + "from requests where scope_id = ? and session_id = ? and sequence <= ?\norder by sequence\n")) {
            query.setString(1, scopeId);
            query.setString(2, fork.sourceSessionId());
            query.setLong(3, fork.cutoff().sequence());
            try (ResultSet result = query.executeQuery()) {
                while (result.next()) source.add(readRequestRow(connection, scopeId, result));
            }
        }
        if (source.isEmpty() || !source.get(source.size() - 1).cursor().equals(fork.cutoff())
                || source.stream().anyMatch(row -> !row.request().terminal())) {
            throw new GuideHistoryException(
                    "fork_boundary_unavailable", "Fork requires an exact completed request boundary");
        }
        RequestBoundary boundary = readRequestBoundary(connection, scopeId, fork.cutoff().requestId());
        if (boundary == null || boundary.messages().isEmpty()) {
            throw new GuideHistoryException(
                    "fork_context_unavailable", "The completed request has no safe model context snapshot");
        }
        int ordinal;
        try (PreparedStatement query = connection.prepareStatement(
                "select coalesce(max(ordinal), -1) + 1 from sessions where scope_id = ?")) {
            query.setString(1, scopeId);
            try (ResultSet result = query.executeQuery()) {
                result.next();
                ordinal = result.getInt(1);
            }
        }
        applyMutation(connection, scope, new GuideHistoryMutation.UpsertSession(
                fork.sessionId(), ordinal, fork.modelSelection()));
        Map<UUID, UUID> identities = new LinkedHashMap<>();
        for (SequencedRequest row : source) {
            if (readRequestContext(connection, scopeId, row.request().requestId()).isEmpty()) {
                throw new GuideHistoryException("fork_context_unavailable",
                        "An inherited request has no original model transcript");
            }
            UUID targetId = UUID.randomUUID();
            identities.put(row.request().requestId(), targetId);
            GuideRequestSnapshot target = forkRequest(row.request(), targetId, fork.sessionId());
            upsertRequest(connection, scopeId, row.cursor().sequence(), target);
            for (String table : dev.openallay.util.Java8Collections.listOf("timeline_entries", "request_sources", "request_model_context", "request_context_boundaries")) {
                copyRequestPayload(connection, scopeId, table, row.request().requestId(), targetId);
            }
        }
        int messageOrdinal = 0;
        try (PreparedStatement query = connection.prepareStatement("select ordinal, request_id, role, message_text, created_at from messages\nwhere scope_id = ? and session_id = ? order by ordinal\n")) {
            query.setString(1, scopeId);
            query.setString(2, fork.sourceSessionId());
            try (ResultSet result = query.executeQuery()) {
                while (result.next()) {
                    UUID targetId = identities.get(UUID.fromString(result.getString("request_id")));
                    if (targetId == null) continue;
                    upsertMessage(connection, scopeId, new GuideHistoryMutation.UpsertMessage(
                            fork.sessionId(), messageOrdinal++, new GuideMessage(targetId,
                                    GuideMessage.Role.valueOf(result.getString("role")),
                                    result.getString("message_text"),
                                    Instant.parse(result.getString("created_at")))));
                }
            }
        }
        applyMutation(connection, scope, new GuideHistoryMutation.ReplaceContext(
                fork.sessionId(), boundary.messages()));
        // Checkpoints are diagnostic copies with fresh row identity. Only source-hash-valid
        // whole units may enter the new runtime reuse index.
        List<ContextCheckpoint> copied = new ArrayList<>();
        for (ContextCheckpoint checkpoint : boundary.checkpoints()) {
            ContextCheckpoint target = new ContextCheckpoint(UUID.randomUUID(),
                    checkpoint.sourceFromIndex(), checkpoint.sourceToIndexExclusive(),
                    checkpoint.sourceHash(), checkpoint.modelIdentifier(), checkpoint.createdAt(),
                    checkpoint.status(), checkpoint.summary(), checkpoint.failureCode(),
                    checkpoint.failureMessage(), checkpoint.estimatedProjectionTokens());
            applyMutation(connection, scope, new GuideHistoryMutation.UpsertCheckpoint(
                    fork.sessionId(), copied.size(), target));
            copied.add(target);
        }
        List<SequencedRequest> window = readPage(connection, new GuideHistoryPageRequest(
                scope, fork.sessionId(), GuideHistoryPageRequest.Direction.NEWEST, null, 120));
        GuideHistoryCursor first = cursor(connection, scopeId, fork.sessionId(), source.get(0).cursor().sequence());
        GuideHistoryCursor last = cursor(connection, scopeId, fork.sessionId(), fork.cutoff().sequence());
        GuideHistoryPage page = new GuideHistoryPage(fork.sessionId(),
                dev.openallay.util.Java8Collections.toList(window.stream().map(SequencedRequest::request)),
                window.get(0).cursor(), window.get(window.size() - 1).cursor(), source.size() > window.size(), false);
        return new GuideHistoryForkResult(new GuideHistoryMetadata.Session(
                fork.sessionId(), ordinal, fork.modelSelection(), source.size(), first, last,
                GuideUsageSnapshot.empty(), sessionUsage(connection, scopeId, fork.sessionId(), true),
                GuideUsageSnapshot.empty(), messageOrdinal),
                page, boundary.messages(), copied, messageOrdinal);
    }

    private static GuideRequestSnapshot forkRequest(
            GuideRequestSnapshot source, UUID requestId, String targetSessionId) {
        return new GuideRequestSnapshot(requestId, targetSessionId, source.topology(),
                source.userMessage(), source.timeline(), source.status(), source.sources(), source.usage(),
                source.retryAfterMillis(), source.failure(), source.createdAt(), source.updatedAt(),
                source.terminalAt(), source.modelSelection(), source.progress(), source.usageProjection(),
                source.usageOriginRequestId() == null ? source.requestId() : source.usageOriginRequestId());
    }

    private static void copyRequestPayload(Connection connection, String scopeId, String table,
            UUID sourceId, UUID targetId) throws SQLException {
        java.lang.String $oaSwitch1_exit_result;
$oaSwitch1_exit: {
switch ((table)) {
case "timeline_entries":
case "request_sources":
{
$oaSwitch1_exit_result = "ordinal, payload_json"; break $oaSwitch1_exit;
}
case "request_model_context":
{
$oaSwitch1_exit_result = "payload_json"; break $oaSwitch1_exit;
}
case "request_context_boundaries":
{
$oaSwitch1_exit_result = "payload_json, checkpoints_json"; break $oaSwitch1_exit;
}
default:
{
throw new IllegalArgumentException("unknown fork payload table");
}
}
}
String columns = $oaSwitch1_exit_result;
        try (PreparedStatement copy = connection.prepareStatement("insert into " + table
                + "(scope_id, request_id, " + columns + ") select scope_id, ?, " + columns
                + " from " + table + " where scope_id = ? and request_id = ?")) {
            copy.setString(1, targetId.toString());
            copy.setString(2, scopeId);
            copy.setString(3, sourceId.toString());
            copy.executeUpdate();
        }
    }

    private void captureRequestBoundary(Connection connection, String scopeId,
            GuideHistoryMutation.CaptureRequestBoundary boundary) throws SQLException {
        com.google.gson.JsonArray checkpoints = new com.google.gson.JsonArray();
        for (ContextCheckpoint checkpoint : boundary.checkpoints()) {
            checkpoints.add(dev.openallay.json.JsonTrees.parse(codec.encodeCheckpoint(checkpoint)));
        }
        try (PreparedStatement statement = connection.prepareStatement("insert into request_context_boundaries(scope_id, request_id, payload_json, checkpoints_json)\nvalues (?, ?, ?, ?)\non conflict(scope_id, request_id) do update set\n    payload_json = excluded.payload_json, checkpoints_json = excluded.checkpoints_json\n")) {
            statement.setString(1, scopeId);
            statement.setString(2, boundary.requestId().toString());
            statement.setString(3, modelContexts.encode(boundary.messages()));
            statement.setString(4, checkpoints.toString());
            statement.executeUpdate();
        }
    }

    private RequestBoundary readRequestBoundary(Connection connection, String scopeId, UUID requestId)
            throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("select payload_json, checkpoints_json from request_context_boundaries\nwhere scope_id = ? and request_id = ?\n")) {
            query.setString(1, scopeId);
            query.setString(2, requestId.toString());
            try (ResultSet result = query.executeQuery()) {
                if (!result.next()) return null;
                com.google.gson.JsonElement raw = dev.openallay.json.JsonTrees.parse(
                        result.getString("checkpoints_json"));
                if (!raw.isJsonArray()) throw new IllegalArgumentException("boundary checkpoints must be an array");
                List<ContextCheckpoint> checkpoints = new ArrayList<>();
                for (com.google.gson.JsonElement value : raw.getAsJsonArray()) {
                    checkpoints.add(codec.decodeCheckpoint(value.toString()));
                }
                return new RequestBoundary(modelContexts.decode(result.getString("payload_json")),
                        dev.openallay.util.Java8Collections.listCopyOf(checkpoints));
            }
        }
    }

    @dev.openallay.value.ValueType(RequestBoundary.ValueSchemaProvider.class)
private static final class RequestBoundary {
    private final List<ModelMessage> messages;
    private final List<ContextCheckpoint> checkpoints;
    private RequestBoundary(List<ModelMessage> messages, List<ContextCheckpoint> checkpoints) {
        this.messages = messages;
        this.checkpoints = checkpoints;
    }
    public List<ModelMessage> messages() { return messages; }
    public List<ContextCheckpoint> checkpoints() { return checkpoints; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RequestBoundary)) return false;
        RequestBoundary that = (RequestBoundary) other;
        return java.util.Objects.equals(messages, that.messages) && java.util.Objects.equals(checkpoints, that.checkpoints);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(messages);
        hash = 31 * hash + java.util.Objects.hashCode(checkpoints);
        return hash;
    }
    @Override public String toString() { return "RequestBoundary[messages=" + messages + ", checkpoints=" + checkpoints + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<RequestBoundary> schema() {
            return new dev.openallay.value.ValueSchema<>(RequestBoundary.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<RequestBoundary>>asList(new dev.openallay.value.ValueSchema.Component<>(RequestBoundary.class, "messages", RequestBoundary::messages), new dev.openallay.value.ValueSchema.Component<>(RequestBoundary.class, "checkpoints", RequestBoundary::checkpoints)), arguments -> new RequestBoundary((List) arguments[0], (List) arguments[1]));
        }
    }
}

    @Override
    public void delete(GuideHistoryDeleteScope scope) {
        Objects.requireNonNull(scope, "scope");
        GuideHistoryDeleteScope.requireKnown(scope);
        boolean durable = false;
        try (GuideImageOwnership.Guard ignored = imageOwnership.lock();
                Connection connection = open()) {
            List<GuideHistoryScope> affected;
            {
final java.lang.Object $oaPattern5_value = scope;
final boolean $oaPattern5_match = $oaPattern5_value instanceof GuideHistoryDeleteScope.Partition;
GuideHistoryDeleteScope.Partition $oaPattern5_bound = $oaPattern5_match ? (GuideHistoryDeleteScope.Partition) $oaPattern5_value : null;
if ($oaPattern5_match) {
                affected = dev.openallay.util.Java8Collections.listOf($oaPattern5_bound.scope());
            } else {
final java.lang.Object $oaPattern6_value = scope;
final boolean $oaPattern6_match = $oaPattern6_value instanceof GuideHistoryDeleteScope.Actor;
GuideHistoryDeleteScope.Actor $oaPattern6_bound = $oaPattern6_match ? (GuideHistoryDeleteScope.Actor) $oaPattern6_value : null;
if ($oaPattern6_match) {
                affected = dev.openallay.util.Java8Collections.toList(readScopes(connection).stream()
                        .filter(existing -> existing.actorId().equals($oaPattern6_bound.actorId())));
            } else {
                throw new IncompatibleClassChangeError();
            }
}
}
            connection.setAutoCommit(false);
            try {
                {
final java.lang.Object $oaPattern7_value = scope;
final boolean $oaPattern7_match = $oaPattern7_value instanceof GuideHistoryDeleteScope.Partition;
GuideHistoryDeleteScope.Partition $oaPattern7_bound = $oaPattern7_match ? (GuideHistoryDeleteScope.Partition) $oaPattern7_value : null;
if ($oaPattern7_match) {
                    deletePartition(connection, $oaPattern7_bound.scope());
                } else {
final java.lang.Object $oaPattern8_value = scope;
final boolean $oaPattern8_match = $oaPattern8_value instanceof GuideHistoryDeleteScope.Actor;
GuideHistoryDeleteScope.Actor $oaPattern8_bound = $oaPattern8_match ? (GuideHistoryDeleteScope.Actor) $oaPattern8_value : null;
if ($oaPattern8_match) {
                    deleteActor(connection, $oaPattern8_bound.actorId());
                } else {
                    throw new IncompatibleClassChangeError();
                }
}
}
                failureInjector.beforeCommit(Mutation.DELETE);
                connection.commit();
                durable = true;
            } catch (SQLException | RuntimeException failure) {
                rollback(connection, failure);
                throw deleteFailure(failure);
            }
            finishDurableImages(connection, affected, true);
        } catch (SQLException | RuntimeException failure) {
            if (durable) return;
            {
final java.lang.Object $oaPattern9_value = failure;
final boolean $oaPattern9_match = $oaPattern9_value instanceof GuideHistoryException;
GuideHistoryException $oaPattern9_bound = $oaPattern9_match ? (GuideHistoryException) $oaPattern9_value : null;
if ($oaPattern9_match) throw $oaPattern9_bound;
}
            throw deleteFailure(failure);
        }
    }

    @Override
    public void resetDatabase() {
        boolean durable = false;
        try (GuideImageOwnership.Guard ignored = imageOwnership.lock();
                Connection connection = openRaw()) {
            // Reset is explicit and may repair a foreign layout. An unreadable ownership
            // header leaves image bytes retained rather than guessing which actor owns them.
            List<GuideHistoryScope> affected;
            try {
                affected = readScopes(connection);
            } catch (SQLException | IllegalArgumentException unreadable) {
                affected = dev.openallay.util.Java8Collections.listOf();
            }
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
                durable = true;
            } catch (SQLException | RuntimeException failure) {
                rollback(connection, failure);
                throw deleteFailure(failure);
            }
            finishDurableImages(connection, affected, true);
        } catch (SQLException | RuntimeException failure) {
            if (durable) return;
            {
final java.lang.Object $oaPattern10_value = failure;
final boolean $oaPattern10_match = $oaPattern10_value instanceof GuideHistoryException;
GuideHistoryException $oaPattern10_bound = $oaPattern10_match ? (GuideHistoryException) $oaPattern10_value : null;
if ($oaPattern10_match
                    && $oaPattern10_bound.code().equals("history_delete_failed")) throw $oaPattern10_bound;
}
            throw deleteFailure(failure);
        }
    }

    /** Complete contexts, not paginated UI requests, determine durable image ownership. */
    private Map<String, List<ImageReference>> readImageOwners(Connection connection, String scopeId)
            throws SQLException {
        Map<String, List<ImageReference>> owners = new LinkedHashMap<>();
        try (PreparedStatement query = connection.prepareStatement("select 'session:' || session_id as owner, payload_json\nfrom model_context where scope_id = ?\nunion all\nselect 'request:' || request_id as owner, payload_json\nfrom request_model_context where scope_id = ?\nunion all\nselect 'boundary:' || request_id as owner, payload_json\nfrom request_context_boundaries where scope_id = ?\n")) {
            query.setString(1, scopeId);
            query.setString(2, scopeId);
            query.setString(3, scopeId);
            try (ResultSet result = query.executeQuery()) {
                while (result.next()) {
                    List<ImageReference> refs = GuideImageOwnership.references(
                            modelContexts.decode(result.getString("payload_json")));
                    if (!refs.isEmpty()) owners.put(result.getString("owner"), refs);
                }
            }
        }
        return dev.openallay.util.Java8Collections.mapCopyOf(owners);
    }

    private static boolean changesImageOwnership(GuideHistoryMutation mutation) {
        Objects.requireNonNull(mutation);
        {
final java.lang.Object $oaPattern11_value = mutation;
final boolean $oaPattern11_match = $oaPattern11_value instanceof GuideHistoryMutation.UpsertPartition;
GuideHistoryMutation.UpsertPartition $oaPattern11_bound = $oaPattern11_match ? (GuideHistoryMutation.UpsertPartition) $oaPattern11_value : null;
if ($oaPattern11_match) {
            return false;
        } else {
final java.lang.Object $oaPattern12_value = mutation;
final boolean $oaPattern12_match = $oaPattern12_value instanceof GuideHistoryMutation.UpsertSession;
GuideHistoryMutation.UpsertSession $oaPattern12_bound = $oaPattern12_match ? (GuideHistoryMutation.UpsertSession) $oaPattern12_value : null;
if ($oaPattern12_match) {
            return false;
        } else {
final java.lang.Object $oaPattern13_value = mutation;
final boolean $oaPattern13_match = $oaPattern13_value instanceof GuideHistoryMutation.UpsertSessionUsage;
GuideHistoryMutation.UpsertSessionUsage $oaPattern13_bound = $oaPattern13_match ? (GuideHistoryMutation.UpsertSessionUsage) $oaPattern13_value : null;
if ($oaPattern13_match) {
            return false;
        } else {
final java.lang.Object $oaPattern14_value = mutation;
final boolean $oaPattern14_match = $oaPattern14_value instanceof GuideHistoryMutation.UpsertRequest;
GuideHistoryMutation.UpsertRequest $oaPattern14_bound = $oaPattern14_match ? (GuideHistoryMutation.UpsertRequest) $oaPattern14_value : null;
if ($oaPattern14_match) {
            return false;
        } else {
final java.lang.Object $oaPattern15_value = mutation;
final boolean $oaPattern15_match = $oaPattern15_value instanceof GuideHistoryMutation.UpsertMessage;
GuideHistoryMutation.UpsertMessage $oaPattern15_bound = $oaPattern15_match ? (GuideHistoryMutation.UpsertMessage) $oaPattern15_value : null;
if ($oaPattern15_match) {
            return false;
        } else {
final java.lang.Object $oaPattern16_value = mutation;
final boolean $oaPattern16_match = $oaPattern16_value instanceof GuideHistoryMutation.UpsertTimelineEntry;
GuideHistoryMutation.UpsertTimelineEntry $oaPattern16_bound = $oaPattern16_match ? (GuideHistoryMutation.UpsertTimelineEntry) $oaPattern16_value : null;
if ($oaPattern16_match) {
            return false;
        } else {
final java.lang.Object $oaPattern17_value = mutation;
final boolean $oaPattern17_match = $oaPattern17_value instanceof GuideHistoryMutation.ReplaceRequestSources;
GuideHistoryMutation.ReplaceRequestSources $oaPattern17_bound = $oaPattern17_match ? (GuideHistoryMutation.ReplaceRequestSources) $oaPattern17_value : null;
if ($oaPattern17_match) {
            return false;
        } else {
final java.lang.Object $oaPattern18_value = mutation;
final boolean $oaPattern18_match = $oaPattern18_value instanceof GuideHistoryMutation.UpsertCheckpoint;
GuideHistoryMutation.UpsertCheckpoint $oaPattern18_bound = $oaPattern18_match ? (GuideHistoryMutation.UpsertCheckpoint) $oaPattern18_value : null;
if ($oaPattern18_match) {
            return false;
        } else {
final java.lang.Object $oaPattern19_value = mutation;
final boolean $oaPattern19_match = $oaPattern19_value instanceof GuideHistoryMutation.AppendCheckpoint;
GuideHistoryMutation.AppendCheckpoint $oaPattern19_bound = $oaPattern19_match ? (GuideHistoryMutation.AppendCheckpoint) $oaPattern19_value : null;
if ($oaPattern19_match) {
            return false;
        } else {
final java.lang.Object $oaPattern20_value = mutation;
final boolean $oaPattern20_match = $oaPattern20_value instanceof GuideHistoryMutation.ReplaceContext;
GuideHistoryMutation.ReplaceContext $oaPattern20_bound = $oaPattern20_match ? (GuideHistoryMutation.ReplaceContext) $oaPattern20_value : null;
if ($oaPattern20_match) {
            return true;
        } else {
final java.lang.Object $oaPattern21_value = mutation;
final boolean $oaPattern21_match = $oaPattern21_value instanceof GuideHistoryMutation.ReplaceRequestContext;
GuideHistoryMutation.ReplaceRequestContext $oaPattern21_bound = $oaPattern21_match ? (GuideHistoryMutation.ReplaceRequestContext) $oaPattern21_value : null;
if ($oaPattern21_match) {
            return true;
        } else {
final java.lang.Object $oaPattern22_value = mutation;
final boolean $oaPattern22_match = $oaPattern22_value instanceof GuideHistoryMutation.CaptureRequestBoundary;
GuideHistoryMutation.CaptureRequestBoundary $oaPattern22_bound = $oaPattern22_match ? (GuideHistoryMutation.CaptureRequestBoundary) $oaPattern22_value : null;
if ($oaPattern22_match) {
            return true;
        } else {
final java.lang.Object $oaPattern23_value = mutation;
final boolean $oaPattern23_match = $oaPattern23_value instanceof GuideHistoryMutation.ForkSession;
GuideHistoryMutation.ForkSession $oaPattern23_bound = $oaPattern23_match ? (GuideHistoryMutation.ForkSession) $oaPattern23_value : null;
if ($oaPattern23_match) {
            return true;
        } else {
final java.lang.Object $oaPattern24_value = mutation;
final boolean $oaPattern24_match = $oaPattern24_value instanceof GuideHistoryMutation.DeleteSession;
GuideHistoryMutation.DeleteSession $oaPattern24_bound = $oaPattern24_match ? (GuideHistoryMutation.DeleteSession) $oaPattern24_value : null;
if ($oaPattern24_match) {
            return true;
        } else {
final java.lang.Object $oaPattern25_value = mutation;
final boolean $oaPattern25_match = $oaPattern25_value instanceof GuideHistoryMutation.ClearSession;
GuideHistoryMutation.ClearSession $oaPattern25_bound = $oaPattern25_match ? (GuideHistoryMutation.ClearSession) $oaPattern25_value : null;
if ($oaPattern25_match) {
            return true;
        }
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
        throw new IncompatibleClassChangeError();
    }

    private void ensureImageOwnership(Connection connection, GuideHistoryScope scope)
            throws SQLException, IOException {
        if (imageOwnership.enabled() && !initializedImageScopes.contains(scope)) {
            reconcileScope(connection, scope);
        }
    }

    private void reconcileScope(Connection connection, GuideHistoryScope scope)
            throws SQLException, IOException {
        if (!imageOwnership.enabled()) return;
        initializedImageScopes.remove(scope);
        readHeader(connection, scope); // Validate actor authorization before resolving bytes.
        imageOwnership.reconcile(scope, readImageOwners(connection, scope.scopeId()));
        initializedImageScopes.add(scope);
    }

    private static List<GuideHistoryScope> readScopes(Connection connection) throws SQLException {
        List<GuideHistoryScope> scopes = new ArrayList<>();
        try (Statement query = connection.createStatement();
                ResultSet result = query.executeQuery(
                        "select scope_id, actor_id, connection_kind from partitions")) {
            while (result.next()) scopes.add(new GuideHistoryScope(
                    UUID.fromString(result.getString("actor_id")),
                    GuideHistoryScope.Kind.valueOf(result.getString("connection_kind")),
                    result.getString("scope_id")));
        }
        return dev.openallay.util.Java8Collections.listCopyOf(scopes);
    }

    private void recoverPinsAfterRollback(
            Connection connection, GuideHistoryScope scope, Throwable failure) {
        try {
            reconcileScope(connection, scope);
        } catch (SQLException | IOException | RuntimeException recovery) {
            failure.addSuppressed(recovery); // Keep pending pins if SQLite truth cannot be verified.
        }
    }

    private void finishDurableImages(
            Connection connection, List<GuideHistoryScope> changed, boolean collect) {
        if (!imageOwnership.enabled()) return;
        try {
            // Reconcile surviving actor scopes as well before any collection. Each namespace
            // is independent, so deleting one partition cannot release another partition.
            Set<UUID> actors = changed.stream().map(GuideHistoryScope::actorId)
                    .collect(java.util.stream.Collectors.toSet());
            List<GuideHistoryScope> scopes = new ArrayList<>(changed);
            if (collect) scopes.addAll(dev.openallay.util.Java8Collections.toList(readScopes(connection).stream()
                    .filter(scope -> actors.contains(scope.actorId()))));
            for (GuideHistoryScope scope : new java.util.LinkedHashSet<>(scopes)) {
                reconcileScope(connection, scope);
            }
            if (collect) for (UUID actor : actors) imageOwnership.collect(actor);
        } catch (SQLException | IOException | RuntimeException conservativeRetention) {
            initializedImageScopes.removeAll(changed);
            // The SQL commit already succeeded. Keep write pins/old owners, do not collect,
            // and retry reconciliation on the next image-enabled operation. This is durable
            // success, not a rollback; replaying the batch may corrupt durable history.
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

    private void ensureLayout(Connection connection, boolean emptyFile) throws SQLException {
        List<String> tables = applicationTables(connection);
        if (tables.isEmpty() && emptyFile) {
            createLayout(connection);
            return;
        }
        List<String> differences = new ArrayList<>();
        Set<String> actualTables = dev.openallay.util.Java8Collections.setCopyOf(tables);
        Set<String> missingTables = new java.util.TreeSet<>(HISTORY_LAYOUT.keySet());
        missingTables.removeAll(actualTables);
        Set<String> extraTables = new java.util.TreeSet<>(actualTables);
        extraTables.removeAll(HISTORY_LAYOUT.keySet());
        if (!missingTables.isEmpty() || !extraTables.isEmpty()) {
            differences.add("tables: missing=" + missingTables + ", extra=" + extraTables);
        }
        for (String table : new java.util.TreeSet<>(HISTORY_LAYOUT.keySet())) {
            if (!actualTables.contains(table)) continue;
            List<ColumnSignature> expected = HISTORY_LAYOUT.get(table);
            List<ColumnSignature> actual = tableSignature(connection, table);
            if (!actual.equals(expected)) {
                Set<String> expectedNames = expected.stream().map(ColumnSignature::name)
                        .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
                Set<String> actualNames = actual.stream().map(ColumnSignature::name)
                        .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
                Set<String> missing = new java.util.TreeSet<>(expectedNames);
                missing.removeAll(actualNames);
                Set<String> extra = new java.util.TreeSet<>(actualNames);
                extra.removeAll(expectedNames);
                differences.add("table " + table + ": columns missing=" + missing + ", extra=" + extra
                        + ", expected=" + expected + ", actual=" + actual);
            }
            List<ForeignKeySignature> ownership = foreignKeys(connection, table);
            if (!ownership.equals(HISTORY_OWNERSHIP.get(table))) {
                differences.add("table " + table + ": ownership expected=" + HISTORY_OWNERSHIP.get(table)
                        + ", actual=" + ownership);
            }
        }
        if (!differences.isEmpty()) {
            // Only schema metadata is logged. Never inspect or print persisted row values
            // to guess which earlier build wrote a layout that this build does not accept.
            if (reportedUnsupportedLayout.compareAndSet(false, true)) {
                dev.openallay.OpenAllayConstants.LOGGER.warn(
                        "Guide history layout does not match the current build: {}",
                        String.join("; ", differences));
            }
            throw unsupportedLayout();
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
        tables.put("partitions", dev.openallay.util.Java8Collections.listOf());
        tables.put("sessions", dev.openallay.util.Java8Collections.listOf(foreignKey(0, "partitions", "scope_id")));
        List<ForeignKeySignature> sessionOwner = dev.openallay.util.Java8Collections.listOf(foreignKey(0, "sessions", "scope_id"), foreignKey(1, "sessions", "session_id"));
        List<ForeignKeySignature> requestOwner = dev.openallay.util.Java8Collections.listOf(foreignKey(0, "requests", "scope_id"), foreignKey(1, "requests", "request_id"));
        tables.put("requests", sortedForeignKeys(sessionOwner));
        List<ForeignKeySignature> messageOwners = new ArrayList<>(sessionOwner);
        messageOwners.addAll(requestOwner);
        tables.put("messages", sortedForeignKeys(messageOwners));
        tables.put("timeline_entries", sortedForeignKeys(requestOwner));
        tables.put("request_sources", sortedForeignKeys(requestOwner));
        tables.put("compaction_checkpoints", sortedForeignKeys(sessionOwner));
        tables.put("model_context", sortedForeignKeys(sessionOwner));
        tables.put("request_model_context", sortedForeignKeys(requestOwner));
        tables.put("request_context_boundaries", sortedForeignKeys(requestOwner));
        return dev.openallay.util.Java8Collections.mapCopyOf(tables);
    }

    private static ForeignKeySignature foreignKey(int sequence, String owner, String column) {
        return new ForeignKeySignature(sequence, owner, column, column, "NO ACTION", "CASCADE", "NONE");
    }

    private static List<ForeignKeySignature> sortedForeignKeys(List<ForeignKeySignature> keys) {
        return dev.openallay.util.Java8Collections.toList(keys.stream().sorted(java.util.Comparator.comparing(ForeignKeySignature::toString)));
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
        return dev.openallay.util.Java8Collections.listCopyOf(columns);
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
                column("model_selection_json", "TEXT", true, 0),
                column("control_usage_json", "TEXT", true, 0)));
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
        tables.put("request_context_boundaries", columns(
                column("scope_id", "TEXT", true, 1),
                column("request_id", "TEXT", true, 2),
                column("payload_json", "TEXT", true, 0),
                column("checkpoints_json", "TEXT", true, 0)));
        return dev.openallay.util.Java8Collections.mapCopyOf(tables);
    }

    private static List<ColumnSignature> payloadTableSignature() {
        return columns(
                column("scope_id", "TEXT", true, 1),
                column("request_id", "TEXT", true, 2),
                column("ordinal", "INTEGER", true, 3),
                column("payload_json", "TEXT", true, 0));
    }

    private static List<ColumnSignature> columns(ColumnSignature... columns) {
        return dev.openallay.util.Java8Collections.listOf(columns);
    }

    private static ColumnSignature column(
            String name, String type, boolean notNull, int primaryKeyPosition) {
        return new ColumnSignature(name, type, notNull, primaryKeyPosition, 0);
    }

    private static GuideHistoryException unsupportedLayout() {
        return new GuideHistoryException("history_layout_unsupported",
                "Guide history uses a different database layout; the original database was not changed. "
                        + "Preserve or archive it and use a matching OpenAllay build to export it "
                        + "before starting new history.");
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
            statement.execute("create table partitions(\n    scope_id text primary key,\n    actor_id text not null,\n    connection_kind text not null,\n    selected_session text not null,\n    capture_mode text not null check(capture_mode = 'NORMAL'),\n    updated_at text not null\n)\n");
            statement.execute("create table sessions(\n    scope_id text not null,\n    session_id text not null,\n    ordinal integer not null check(ordinal >= 0),\n    model_selection_json text not null,\n    control_usage_json text not null,\n    primary key(scope_id, session_id),\n    unique(scope_id, ordinal),\n    foreign key(scope_id) references partitions(scope_id) on delete cascade\n)\n");
            statement.execute("create table requests(\n    scope_id text not null,\n    session_id text not null,\n    request_id text not null,\n    sequence integer not null check(sequence >= 0),\n    topology text not null,\n    model_selection_json text not null,\n    user_message text not null,\n    status text not null,\n    model_usage_json text not null,\n    usage_projection_json text not null,\n    usage_origin_request_id text,\n    retry_after_millis integer,\n    failure_code text,\n    failure_message text,\n    created_at text not null,\n    updated_at text not null,\n    terminal_at text,\n    primary key(scope_id, request_id),\n    unique(scope_id, session_id, sequence),\n    foreign key(scope_id, session_id)\n        references sessions(scope_id, session_id) on delete cascade\n)\n");
            statement.execute("create table messages(\n    scope_id text not null,\n    session_id text not null,\n    ordinal integer not null check(ordinal >= 0),\n    request_id text not null,\n    role text not null,\n    message_text text not null,\n    created_at text not null,\n    primary key(scope_id, session_id, ordinal),\n    foreign key(scope_id, session_id)\n        references sessions(scope_id, session_id) on delete cascade,\n    foreign key(scope_id, request_id)\n        references requests(scope_id, request_id) on delete cascade\n)\n");
            statement.execute("create table timeline_entries(\n    scope_id text not null,\n    request_id text not null,\n    ordinal integer not null check(ordinal >= 0),\n    payload_json text not null,\n    primary key(scope_id, request_id, ordinal),\n    foreign key(scope_id, request_id)\n        references requests(scope_id, request_id) on delete cascade\n)\n");
            statement.execute("create table request_sources(\n    scope_id text not null,\n    request_id text not null,\n    ordinal integer not null check(ordinal >= 0),\n    payload_json text not null,\n    primary key(scope_id, request_id, ordinal),\n    foreign key(scope_id, request_id)\n        references requests(scope_id, request_id) on delete cascade\n)\n");
            statement.execute("create index sessions_updated_lookup on sessions(scope_id, ordinal)");
            statement.execute("create index requests_order_lookup on requests(scope_id, session_id, sequence)");
            statement.execute("create index timeline_order_lookup on timeline_entries(scope_id, request_id, ordinal)");
            createCheckpointTable(statement);
            statement.execute("create table model_context(\n    scope_id text not null,\n    session_id text not null,\n    payload_json text not null,\n    primary key(scope_id, session_id),\n    foreign key(scope_id, session_id)\n        references sessions(scope_id, session_id) on delete cascade\n)\n");
            statement.execute("create table request_model_context(\n    scope_id text not null,\n    request_id text not null,\n    payload_json text not null,\n    primary key(scope_id, request_id),\n    foreign key(scope_id, request_id)\n        references requests(scope_id, request_id) on delete cascade\n)\n");
            statement.execute("create table request_context_boundaries(\n    scope_id text not null,\n    request_id text not null,\n    payload_json text not null,\n    checkpoints_json text not null,\n    primary key(scope_id, request_id),\n    foreign key(scope_id, request_id)\n        references requests(scope_id, request_id) on delete cascade\n)\n");
        }
    }

    private static void createCheckpointTable(Statement statement) throws SQLException {
        statement.execute("create table compaction_checkpoints(\n    scope_id text not null,\n    session_id text not null,\n    ordinal integer not null check(ordinal >= 0),\n    checkpoint_id text not null,\n    payload_json text not null,\n    primary key(scope_id, session_id, ordinal),\n    unique(scope_id, checkpoint_id),\n    foreign key(scope_id, session_id)\n        references sessions(scope_id, session_id) on delete cascade\n)\n");
        statement.execute("create index checkpoint_order_lookup\non compaction_checkpoints(scope_id, session_id, ordinal)\n");
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
                ResultSet result = statement.executeQuery("select name from sqlite_master\nwhere type = 'table' and name not glob 'sqlite_*'\norder by name\n")) {
            while (result.next()) {
                tables.add(result.getString(1));
            }
        }
        return dev.openallay.util.Java8Collections.listCopyOf(tables);
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
        Objects.requireNonNull(mutation);
        {
final java.lang.Object $oaPattern26_value = mutation;
final boolean $oaPattern26_match = $oaPattern26_value instanceof GuideHistoryMutation.UpsertPartition;
GuideHistoryMutation.UpsertPartition $oaPattern26_bound = $oaPattern26_match ? (GuideHistoryMutation.UpsertPartition) $oaPattern26_value : null;
if ($oaPattern26_match) {
            try (PreparedStatement statement = connection.prepareStatement("insert into partitions(\n    scope_id, actor_id, connection_kind, selected_session,\n    capture_mode, updated_at)\nvalues (?, ?, ?, ?, 'NORMAL', ?)\non conflict(scope_id) do update set\n    selected_session = excluded.selected_session,\n    updated_at = excluded.updated_at\n")) {
                statement.setString(1, scopeId);
                statement.setString(2, scope.actorId().toString());
                statement.setString(3, scope.kind().name());
                statement.setString(4, $oaPattern26_bound.selectedSession());
                statement.setString(5, $oaPattern26_bound.updatedAt().toString());
                statement.executeUpdate();
            }
        } else {
final java.lang.Object $oaPattern27_value = mutation;
final boolean $oaPattern27_match = $oaPattern27_value instanceof GuideHistoryMutation.UpsertSession;
GuideHistoryMutation.UpsertSession $oaPattern27_bound = $oaPattern27_match ? (GuideHistoryMutation.UpsertSession) $oaPattern27_value : null;
if ($oaPattern27_match) {
            try (PreparedStatement statement = connection.prepareStatement("insert into sessions(scope_id, session_id, ordinal, model_selection_json, control_usage_json)\nvalues (?, ?, ?, ?, ?)\non conflict(scope_id, session_id) do update set\n    ordinal = excluded.ordinal,\n    model_selection_json = excluded.model_selection_json\n")) {
                statement.setString(1, scopeId);
                statement.setString(2, $oaPattern27_bound.sessionId());
                statement.setInt(3, $oaPattern27_bound.ordinal());
                statement.setString(4, codec.encodeModelSelection($oaPattern27_bound.modelSelection()));
                statement.setString(5, codec.encodeUsageProjection(GuideUsageSnapshot.empty()));
                statement.executeUpdate();
            }
        } else {
final java.lang.Object $oaPattern28_value = mutation;
final boolean $oaPattern28_match = $oaPattern28_value instanceof GuideHistoryMutation.UpsertSessionUsage;
GuideHistoryMutation.UpsertSessionUsage $oaPattern28_bound = $oaPattern28_match ? (GuideHistoryMutation.UpsertSessionUsage) $oaPattern28_value : null;
if ($oaPattern28_match) {
            try (PreparedStatement statement = connection.prepareStatement("update sessions set control_usage_json = ? where scope_id = ? and session_id = ?\n")) {
                statement.setString(1, codec.encodeUsageProjection($oaPattern28_bound.controlUsage()));
                statement.setString(2, scopeId);
                statement.setString(3, $oaPattern28_bound.sessionId());
                if (statement.executeUpdate() != 1) throw new IllegalArgumentException("Session usage owner is absent");
            }
        } else {
final java.lang.Object $oaPattern29_value = mutation;
final boolean $oaPattern29_match = $oaPattern29_value instanceof GuideHistoryMutation.UpsertRequest;
GuideHistoryMutation.UpsertRequest $oaPattern29_bound = $oaPattern29_match ? (GuideHistoryMutation.UpsertRequest) $oaPattern29_value : null;
if ($oaPattern29_match) {
            upsertRequest(connection, scopeId, $oaPattern29_bound.sequence(), $oaPattern29_bound.request());
        } else {
final java.lang.Object $oaPattern30_value = mutation;
final boolean $oaPattern30_match = $oaPattern30_value instanceof GuideHistoryMutation.UpsertMessage;
GuideHistoryMutation.UpsertMessage $oaPattern30_bound = $oaPattern30_match ? (GuideHistoryMutation.UpsertMessage) $oaPattern30_value : null;
if ($oaPattern30_match) {
            upsertMessage(connection, scopeId, $oaPattern30_bound);
        } else {
final java.lang.Object $oaPattern31_value = mutation;
final boolean $oaPattern31_match = $oaPattern31_value instanceof GuideHistoryMutation.UpsertTimelineEntry;
GuideHistoryMutation.UpsertTimelineEntry $oaPattern31_bound = $oaPattern31_match ? (GuideHistoryMutation.UpsertTimelineEntry) $oaPattern31_value : null;
if ($oaPattern31_match) {
            try (PreparedStatement statement = connection.prepareStatement("insert into timeline_entries(scope_id, request_id, ordinal, payload_json)\nvalues (?, ?, ?, ?)\non conflict(scope_id, request_id, ordinal) do update set\n    payload_json = excluded.payload_json\n")) {
                statement.setString(1, scopeId);
                statement.setString(2, $oaPattern31_bound.requestId().toString());
                statement.setInt(3, $oaPattern31_bound.entry().ordinal());
                statement.setString(4, codec.encodeEntry($oaPattern31_bound.entry()));
                statement.executeUpdate();
            }
        } else {
final java.lang.Object $oaPattern32_value = mutation;
final boolean $oaPattern32_match = $oaPattern32_value instanceof GuideHistoryMutation.ReplaceRequestSources;
GuideHistoryMutation.ReplaceRequestSources $oaPattern32_bound = $oaPattern32_match ? (GuideHistoryMutation.ReplaceRequestSources) $oaPattern32_value : null;
if ($oaPattern32_match) {
            try (PreparedStatement delete = connection.prepareStatement("delete from request_sources where scope_id = ? and request_id = ?\n")) {
                delete.setString(1, scopeId);
                delete.setString(2, $oaPattern32_bound.requestId().toString());
                delete.executeUpdate();
            }
            for (int ordinal = 0; ordinal < $oaPattern32_bound.sources().size(); ordinal++) {
                try (PreparedStatement insert = connection.prepareStatement("insert into request_sources(\n    scope_id, request_id, ordinal, payload_json)\nvalues (?, ?, ?, ?)\n")) {
                    insert.setString(1, scopeId);
                    insert.setString(2, $oaPattern32_bound.requestId().toString());
                    insert.setInt(3, ordinal);
                    insert.setString(4, codec.encodeSources(
                            dev.openallay.util.Java8Collections.listOf($oaPattern32_bound.sources().get(ordinal))));
                    insert.executeUpdate();
                }
            }
        } else {
final java.lang.Object $oaPattern33_value = mutation;
final boolean $oaPattern33_match = $oaPattern33_value instanceof GuideHistoryMutation.ReplaceContext;
GuideHistoryMutation.ReplaceContext $oaPattern33_bound = $oaPattern33_match ? (GuideHistoryMutation.ReplaceContext) $oaPattern33_value : null;
if ($oaPattern33_match) {
            try (PreparedStatement statement = connection.prepareStatement("insert into model_context(scope_id, session_id, payload_json)\nvalues (?, ?, ?)\non conflict(scope_id, session_id) do update set\n    payload_json = excluded.payload_json\n")) {
                statement.setString(1, scopeId);
                statement.setString(2, $oaPattern33_bound.sessionId());
                statement.setString(3, modelContexts.encode($oaPattern33_bound.messages()));
                statement.executeUpdate();
            }
        } else {
final java.lang.Object $oaPattern34_value = mutation;
final boolean $oaPattern34_match = $oaPattern34_value instanceof GuideHistoryMutation.ReplaceRequestContext;
GuideHistoryMutation.ReplaceRequestContext $oaPattern34_bound = $oaPattern34_match ? (GuideHistoryMutation.ReplaceRequestContext) $oaPattern34_value : null;
if ($oaPattern34_match) {
            try (PreparedStatement statement = connection.prepareStatement("insert into request_model_context(scope_id, request_id, payload_json)\nvalues (?, ?, ?)\non conflict(scope_id, request_id) do update set\n    payload_json = excluded.payload_json\n")) {
                statement.setString(1, scopeId);
                statement.setString(2, $oaPattern34_bound.requestId().toString());
                statement.setString(3, modelContexts.encode($oaPattern34_bound.messages()));
                statement.executeUpdate();
            }
        } else {
final java.lang.Object $oaPattern35_value = mutation;
final boolean $oaPattern35_match = $oaPattern35_value instanceof GuideHistoryMutation.UpsertCheckpoint;
GuideHistoryMutation.UpsertCheckpoint $oaPattern35_bound = $oaPattern35_match ? (GuideHistoryMutation.UpsertCheckpoint) $oaPattern35_value : null;
if ($oaPattern35_match) {
            try (PreparedStatement statement = connection.prepareStatement("insert into compaction_checkpoints(\n    scope_id, session_id, ordinal, checkpoint_id, payload_json)\nvalues (?, ?, ?, ?, ?)\non conflict(scope_id, session_id, ordinal) do update set\n    checkpoint_id = excluded.checkpoint_id,\n    payload_json = excluded.payload_json\n")) {
                statement.setString(1, scopeId);
                statement.setString(2, $oaPattern35_bound.sessionId());
                statement.setInt(3, $oaPattern35_bound.ordinal());
                statement.setString(4, $oaPattern35_bound.checkpoint().checkpointId().toString());
                statement.setString(5, codec.encodeCheckpoint($oaPattern35_bound.checkpoint()));
                statement.executeUpdate();
            }
        } else {
final java.lang.Object $oaPattern36_value = mutation;
final boolean $oaPattern36_match = $oaPattern36_value instanceof GuideHistoryMutation.AppendCheckpoint;
GuideHistoryMutation.AppendCheckpoint $oaPattern36_bound = $oaPattern36_match ? (GuideHistoryMutation.AppendCheckpoint) $oaPattern36_value : null;
if ($oaPattern36_match) {
            Integer existingOrdinal = null;
            try (PreparedStatement query = connection.prepareStatement("select ordinal from compaction_checkpoints\nwhere scope_id = ? and session_id = ? and checkpoint_id = ?\n")) {
                query.setString(1, scopeId);
                query.setString(2, $oaPattern36_bound.sessionId());
                query.setString(3, $oaPattern36_bound.checkpoint().checkpointId().toString());
                try (ResultSet result = query.executeQuery()) {
                    if (result.next()) existingOrdinal = result.getInt("ordinal");
                }
            }
            if (existingOrdinal != null) {
                applyMutation(connection, scope, new GuideHistoryMutation.UpsertCheckpoint(
                        $oaPattern36_bound.sessionId(), existingOrdinal, $oaPattern36_bound.checkpoint()));
            } else {
                try (PreparedStatement statement = connection.prepareStatement("insert into compaction_checkpoints(\n    scope_id, session_id, ordinal, checkpoint_id, payload_json)\nselect ?, ?, coalesce(max(ordinal), -1) + 1, ?, ?\nfrom compaction_checkpoints where scope_id = ? and session_id = ?\n")) {
                    statement.setString(1, scopeId);
                    statement.setString(2, $oaPattern36_bound.sessionId());
                    statement.setString(3, $oaPattern36_bound.checkpoint().checkpointId().toString());
                    statement.setString(4, codec.encodeCheckpoint($oaPattern36_bound.checkpoint()));
                    statement.setString(5, scopeId);
                    statement.setString(6, $oaPattern36_bound.sessionId());
                    // The scoped checkpoint identity constraint rejects another session's ID.
                    statement.executeUpdate();
                }
            }
        } else {
final java.lang.Object $oaPattern37_value = mutation;
final boolean $oaPattern37_match = $oaPattern37_value instanceof GuideHistoryMutation.CaptureRequestBoundary;
GuideHistoryMutation.CaptureRequestBoundary $oaPattern37_bound = $oaPattern37_match ? (GuideHistoryMutation.CaptureRequestBoundary) $oaPattern37_value : null;
if ($oaPattern37_match) {
            captureRequestBoundary(connection, scopeId, $oaPattern37_bound);
        } else {
final java.lang.Object $oaPattern38_value = mutation;
final boolean $oaPattern38_match = $oaPattern38_value instanceof GuideHistoryMutation.ForkSession;
GuideHistoryMutation.ForkSession $oaPattern38_bound = $oaPattern38_match ? (GuideHistoryMutation.ForkSession) $oaPattern38_value : null;
if ($oaPattern38_match) {
            applyForkSession(connection, scope, $oaPattern38_bound);
        } else {
final java.lang.Object $oaPattern39_value = mutation;
final boolean $oaPattern39_match = $oaPattern39_value instanceof GuideHistoryMutation.DeleteSession;
GuideHistoryMutation.DeleteSession $oaPattern39_bound = $oaPattern39_match ? (GuideHistoryMutation.DeleteSession) $oaPattern39_value : null;
if ($oaPattern39_match) {
            try (PreparedStatement statement = connection.prepareStatement("delete from sessions where scope_id = ? and session_id = ?\n")) {
                statement.setString(1, scopeId);
                statement.setString(2, $oaPattern39_bound.sessionId());
                statement.executeUpdate();
            }
        } else {
final java.lang.Object $oaPattern40_value = mutation;
final boolean $oaPattern40_match = $oaPattern40_value instanceof GuideHistoryMutation.ClearSession;
GuideHistoryMutation.ClearSession $oaPattern40_bound = $oaPattern40_match ? (GuideHistoryMutation.ClearSession) $oaPattern40_value : null;
if ($oaPattern40_match) {
            applyMutation(connection, scope, new GuideHistoryMutation.UpsertSessionUsage(
                    $oaPattern40_bound.sessionId(), GuideUsageSnapshot.empty()));
            for (String table : dev.openallay.util.Java8Collections.listOf("messages", "compaction_checkpoints", "model_context")) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "delete from " + table + " where scope_id = ? and session_id = ?")) {
                    statement.setString(1, scopeId);
                    statement.setString(2, $oaPattern40_bound.sessionId());
                    statement.executeUpdate();
                }
            }
            try (PreparedStatement statement = connection.prepareStatement("delete from requests where scope_id = ? and session_id = ?\n")) {
                statement.setString(1, scopeId);
                statement.setString(2, $oaPattern40_bound.sessionId());
                statement.executeUpdate();
            }
        } else {
            throw new IncompatibleClassChangeError();
        }
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
    }

    private void upsertRequest(
            Connection connection,
            String scopeId,
            long sequence,
            GuideRequestSnapshot request) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("insert into requests(\n    scope_id, session_id, request_id, sequence, topology,\n    model_selection_json, user_message, status,\n    model_usage_json, usage_projection_json, usage_origin_request_id,\n    retry_after_millis, failure_code, failure_message,\n    created_at, updated_at, terminal_at)\nvalues (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)\non conflict(scope_id, request_id) do update set\n    session_id = excluded.session_id,\n    sequence = excluded.sequence,\n    topology = excluded.topology,\n    model_selection_json = excluded.model_selection_json,\n    user_message = excluded.user_message,\n    status = excluded.status,\n    model_usage_json = excluded.model_usage_json,\n    usage_projection_json = excluded.usage_projection_json,\n    usage_origin_request_id = excluded.usage_origin_request_id,\n    retry_after_millis = excluded.retry_after_millis,\n    failure_code = excluded.failure_code,\n    failure_message = excluded.failure_message,\n    created_at = excluded.created_at,\n    updated_at = excluded.updated_at,\n    terminal_at = excluded.terminal_at\n")) {
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
        try (PreparedStatement statement = connection.prepareStatement("insert into messages(\n    scope_id, session_id, ordinal, request_id, role, message_text, created_at)\nvalues (?, ?, ?, ?, ?, ?, ?)\non conflict(scope_id, session_id, ordinal) do update set\n    request_id = excluded.request_id,\n    role = excluded.role,\n    message_text = excluded.message_text,\n    created_at = excluded.created_at\n")) {
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
        try (PreparedStatement query = connection.prepareStatement("select request_id from requests\nwhere scope_id = ? and session_id = ? and sequence = ?\n")) {
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
        java.lang.String $oaSwitch0_exit_result;
$oaSwitch0_exit: {
switch ((request.direction())) {
case NEWEST:
{
$oaSwitch0_exit_result = ""; break $oaSwitch0_exit;
}
case BEFORE:
{
$oaSwitch0_exit_result = " and sequence < ?"; break $oaSwitch0_exit;
}
case AFTER:
{
$oaSwitch0_exit_result = " and sequence > ?"; break $oaSwitch0_exit;
}
default: throw new java.lang.IncompatibleClassChangeError();
}
}
String comparison = $oaSwitch0_exit_result;
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
        return dev.openallay.util.Java8Collections.listCopyOf(loaded);
    }

    private void requireCursor(Connection connection, GuideHistoryPageRequest request)
            throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("select request_id from requests\nwhere scope_id = ? and session_id = ? and sequence = ?\n")) {
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
        return "select sequence, session_id, request_id, topology, user_message, status,\n       model_selection_json,\n       model_usage_json, usage_projection_json, usage_origin_request_id,\n       retry_after_millis, failure_code, failure_message,\n       created_at, updated_at, terminal_at\n";
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
        try (PreparedStatement query = connection.prepareStatement("select checkpoint_id, payload_json from compaction_checkpoints\nwhere scope_id = ? and session_id = ? order by ordinal desc\n")) {
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
                            dev.openallay.json.EngineJson.create(), messages.subList(0, checkpoint.sourceToIndexExclusive())))) {
                        return dev.openallay.util.Java8Collections.listOf(checkpoint);
                    }
                }
            }
        }
        return dev.openallay.util.Java8Collections.listOf();
    }

    private void recoverInterruptedRows(Connection connection, GuideHistoryScope scope)
            throws SQLException, IOException {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        Instant recoveredAt = clock.instant();
        try {
            List<InterruptedRequest> active = new ArrayList<>();
            try (PreparedStatement query = connection.prepareStatement("select r.request_id, r.session_id, r.user_message,\n       r.sequence = (select max(latest.sequence) from requests latest\n           where latest.scope_id = r.scope_id\n             and latest.session_id = r.session_id) as latest_request\nfrom requests r where r.scope_id = ? and r.terminal_at is null\norder by r.session_id, r.sequence\n")) {
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
            List<ImageReference> pending = GuideImageOwnership.references(
                    readImageOwners(connection, scope.scopeId()));
            imageOwnership.pin(scope, pending);
            boolean changed = false;
            for (InterruptedRequest request : active) {
                // Claim only still-active rows. Recovery never changes a terminal request.
                try (PreparedStatement update = connection.prepareStatement("update requests set status = 'INTERRUPTED',\n    retry_after_millis = null,\n    failure_code = 'request_interrupted',\n    failure_message = ?, updated_at = ?, terminal_at = ?\nwhere scope_id = ? and request_id = ? and terminal_at is null\n")) {
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
                    {
final java.lang.Object $oaPattern41_value = entry;
final boolean $oaPattern41_match = $oaPattern41_value instanceof GuideTimelineEntry.Assistant;
GuideTimelineEntry.Assistant $oaPattern41_bound = $oaPattern41_match ? (GuideTimelineEntry.Assistant) $oaPattern41_value : null;
if ($oaPattern41_match
                            && $oaPattern41_bound.streaming()) {
                        GuideTimelineEntry.Assistant closed = new GuideTimelineEntry.Assistant(
                                $oaPattern41_bound.ordinal(), $oaPattern41_bound.text(), $oaPattern41_bound.semantic(),
                                false, $oaPattern41_bound.sources());
                        try (PreparedStatement update = connection.prepareStatement("update timeline_entries set payload_json = ?\nwhere scope_id = ? and request_id = ? and ordinal = ?\n")) {
                            update.setString(1, codec.encodeEntry(closed));
                            update.setString(2, scope.scopeId());
                            update.setString(3, request.requestId().toString());
                            update.setInt(4, closed.ordinal());
                            update.executeUpdate();
                        }
                    }
}
                }
                List<ModelMessage> original = readRequestContext(
                        connection, scope.scopeId(), request.requestId());
                boolean missingOriginal = original.isEmpty();
                ModelMessage acceptedUser = ModelMessage.userText(request.userMessage());
                if (missingOriginal) {
                    // Accepted request text is known input, not a reversed display/tool projection.
                    original = dev.openallay.util.Java8Collections.listOf(acceptedUser);
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
                    } else if (missingOriginal && !current.get(current.size() - 1).equals(acceptedUser)
                            && !(current.size() >= 2 && current.get(current.size() - 1).equals(INTERRUPTION_NOTE)
                                    && current.get(current.size() - 2).equals(acceptedUser))) {
                        List<ModelMessage> withUser = new ArrayList<>(current);
                        withUser.add(acceptedUser);
                        current = dev.openallay.util.Java8Collections.listCopyOf(withUser);
                    }
                    List<ModelMessage> recoveredCurrent = withInterruptionNote(current);
                    applyMutation(connection, scope, new GuideHistoryMutation.ReplaceContext(
                            request.sessionId(), recoveredCurrent));
                    // Process recovery closes only complete structural units. It does not resume
                    // any unrecorded tool step, and this boundary can seed an independent fork.
                    captureRequestBoundary(connection, scope.scopeId(),
                            new GuideHistoryMutation.CaptureRequestBoundary(
                                    request.requestId(), recoveredCurrent, dev.openallay.util.Java8Collections.listOf()));
                }
            }
            if (changed) {
                try (PreparedStatement update = connection.prepareStatement("update partitions set updated_at = ? where scope_id = ?\n")) {
                    update.setString(1, recoveredAt.toString());
                    update.setString(2, scope.scopeId());
                    update.executeUpdate();
                }
                failureInjector.beforeCommit(Mutation.RECOVER);
            }
            connection.commit();
            finishDurableImages(connection, dev.openallay.util.Java8Collections.listOf(scope), false);
        } catch (SQLException | IOException | RuntimeException failure) {
            if (rollback(connection, failure)) recoverPinsAfterRollback(connection, scope, failure);
            throw failure;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    private static List<ModelMessage> withInterruptionNote(List<ModelMessage> messages) {
        if (!messages.isEmpty() && messages.get(messages.size() - 1).equals(INTERRUPTION_NOTE)) {
            return messages;
        }
        List<ModelMessage> retained = new ArrayList<>(messages);
        retained.add(INTERRUPTION_NOTE);
        return dev.openallay.util.Java8Collections.listCopyOf(retained);
    }

    private static PartitionHeader readHeader(Connection connection, GuideHistoryScope scope)
            throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("select actor_id, connection_kind, selected_session, capture_mode, updated_at\nfrom partitions where scope_id = ?\n")) {
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
        try (PreparedStatement query = connection.prepareStatement("select ordinal, payload_json from timeline_entries\nwhere scope_id = ? and request_id = ? order by ordinal\n")) {
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
        return dev.openallay.util.Java8Collections.listCopyOf(timeline);
    }

    private List<GuideSource> readSources(
            Connection connection, String scopeId, UUID requestId) throws SQLException {
        List<GuideSource> sources = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement("select ordinal, payload_json from request_sources\nwhere scope_id = ? and request_id = ? order by ordinal\n")) {
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
                    sources.add(decoded.get(0));
                }
            }
        }
        return dev.openallay.util.Java8Collections.listCopyOf(sources);
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

    private static boolean rollback(Connection connection, Throwable original) {
        try {
            connection.rollback();
            return true;
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
            return false;
        }
    }

    @dev.openallay.value.ValueType(InterruptedRequest.ValueSchemaProvider.class)
private static final class InterruptedRequest {
    private final UUID requestId;
    private final String sessionId;
    private final String userMessage;
    private final boolean latestRequest;
    private InterruptedRequest(UUID requestId, String sessionId, String userMessage, boolean latestRequest) {
        this.requestId = requestId;
        this.sessionId = sessionId;
        this.userMessage = userMessage;
        this.latestRequest = latestRequest;
    }
    public UUID requestId() { return requestId; }
    public String sessionId() { return sessionId; }
    public String userMessage() { return userMessage; }
    public boolean latestRequest() { return latestRequest; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof InterruptedRequest)) return false;
        InterruptedRequest that = (InterruptedRequest) other;
        return java.util.Objects.equals(requestId, that.requestId) && java.util.Objects.equals(sessionId, that.sessionId) && java.util.Objects.equals(userMessage, that.userMessage) && latestRequest == that.latestRequest;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(requestId);
        hash = 31 * hash + java.util.Objects.hashCode(sessionId);
        hash = 31 * hash + java.util.Objects.hashCode(userMessage);
        hash = 31 * hash + Boolean.hashCode(latestRequest);
        return hash;
    }
    @Override public String toString() { return "InterruptedRequest[requestId=" + requestId + ", sessionId=" + sessionId + ", userMessage=" + userMessage + ", latestRequest=" + latestRequest + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<InterruptedRequest> schema() {
            return new dev.openallay.value.ValueSchema<>(InterruptedRequest.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<InterruptedRequest>>asList(new dev.openallay.value.ValueSchema.Component<>(InterruptedRequest.class, "requestId", InterruptedRequest::requestId), new dev.openallay.value.ValueSchema.Component<>(InterruptedRequest.class, "sessionId", InterruptedRequest::sessionId), new dev.openallay.value.ValueSchema.Component<>(InterruptedRequest.class, "userMessage", InterruptedRequest::userMessage), new dev.openallay.value.ValueSchema.Component<>(InterruptedRequest.class, "latestRequest", InterruptedRequest::latestRequest)), arguments -> new InterruptedRequest((UUID) arguments[0], (String) arguments[1], (String) arguments[2], (Boolean) arguments[3]));
        }
    }
}

    @dev.openallay.value.ValueType(PartitionHeader.ValueSchemaProvider.class)
private static final class PartitionHeader {
    private final String selectedSession;
    private final Instant updatedAt;
    private PartitionHeader(String selectedSession, Instant updatedAt) {
        this.selectedSession = selectedSession;
        this.updatedAt = updatedAt;
    }
    public String selectedSession() { return selectedSession; }
    public Instant updatedAt() { return updatedAt; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PartitionHeader)) return false;
        PartitionHeader that = (PartitionHeader) other;
        return java.util.Objects.equals(selectedSession, that.selectedSession) && java.util.Objects.equals(updatedAt, that.updatedAt);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(selectedSession);
        hash = 31 * hash + java.util.Objects.hashCode(updatedAt);
        return hash;
    }
    @Override public String toString() { return "PartitionHeader[selectedSession=" + selectedSession + ", updatedAt=" + updatedAt + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<PartitionHeader> schema() {
            return new dev.openallay.value.ValueSchema<>(PartitionHeader.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<PartitionHeader>>asList(new dev.openallay.value.ValueSchema.Component<>(PartitionHeader.class, "selectedSession", PartitionHeader::selectedSession), new dev.openallay.value.ValueSchema.Component<>(PartitionHeader.class, "updatedAt", PartitionHeader::updatedAt)), arguments -> new PartitionHeader((String) arguments[0], (Instant) arguments[1]));
        }
    }
}

    @dev.openallay.value.ValueType(SequencedRequest.ValueSchemaProvider.class)
private static final class SequencedRequest {
    private final GuideHistoryCursor cursor;
    private final GuideRequestSnapshot request;
    private SequencedRequest(GuideHistoryCursor cursor, GuideRequestSnapshot request) {
        this.cursor = cursor;
        this.request = request;
    }
    public GuideHistoryCursor cursor() { return cursor; }
    public GuideRequestSnapshot request() { return request; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SequencedRequest)) return false;
        SequencedRequest that = (SequencedRequest) other;
        return java.util.Objects.equals(cursor, that.cursor) && java.util.Objects.equals(request, that.request);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(cursor);
        hash = 31 * hash + java.util.Objects.hashCode(request);
        return hash;
    }
    @Override public String toString() { return "SequencedRequest[cursor=" + cursor + ", request=" + request + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<SequencedRequest> schema() {
            return new dev.openallay.value.ValueSchema<>(SequencedRequest.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<SequencedRequest>>asList(new dev.openallay.value.ValueSchema.Component<>(SequencedRequest.class, "cursor", SequencedRequest::cursor), new dev.openallay.value.ValueSchema.Component<>(SequencedRequest.class, "request", SequencedRequest::request)), arguments -> new SequencedRequest((GuideHistoryCursor) arguments[0], (GuideRequestSnapshot) arguments[1]));
        }
    }
}

    @dev.openallay.value.ValueType(ColumnSignature.ValueSchemaProvider.class)
private static final class ColumnSignature {
    private final String name;
    private final String type;
    private final boolean notNull;
    private final int primaryKeyPosition;
    private final int hidden;
    private ColumnSignature(String name, String type, boolean notNull, int primaryKeyPosition, int hidden) {
        this.name = name;
        this.type = type;
        this.notNull = notNull;
        this.primaryKeyPosition = primaryKeyPosition;
        this.hidden = hidden;
    }
    public String name() { return name; }
    public String type() { return type; }
    public boolean notNull() { return notNull; }
    public int primaryKeyPosition() { return primaryKeyPosition; }
    public int hidden() { return hidden; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ColumnSignature)) return false;
        ColumnSignature that = (ColumnSignature) other;
        return java.util.Objects.equals(name, that.name) && java.util.Objects.equals(type, that.type) && notNull == that.notNull && primaryKeyPosition == that.primaryKeyPosition && hidden == that.hidden;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(name);
        hash = 31 * hash + java.util.Objects.hashCode(type);
        hash = 31 * hash + Boolean.hashCode(notNull);
        hash = 31 * hash + Integer.hashCode(primaryKeyPosition);
        hash = 31 * hash + Integer.hashCode(hidden);
        return hash;
    }
    @Override public String toString() { return "ColumnSignature[name=" + name + ", type=" + type + ", notNull=" + notNull + ", primaryKeyPosition=" + primaryKeyPosition + ", hidden=" + hidden + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ColumnSignature> schema() {
            return new dev.openallay.value.ValueSchema<>(ColumnSignature.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ColumnSignature>>asList(new dev.openallay.value.ValueSchema.Component<>(ColumnSignature.class, "name", ColumnSignature::name), new dev.openallay.value.ValueSchema.Component<>(ColumnSignature.class, "type", ColumnSignature::type), new dev.openallay.value.ValueSchema.Component<>(ColumnSignature.class, "notNull", ColumnSignature::notNull), new dev.openallay.value.ValueSchema.Component<>(ColumnSignature.class, "primaryKeyPosition", ColumnSignature::primaryKeyPosition), new dev.openallay.value.ValueSchema.Component<>(ColumnSignature.class, "hidden", ColumnSignature::hidden)), arguments -> new ColumnSignature((String) arguments[0], (String) arguments[1], (Boolean) arguments[2], (Integer) arguments[3], (Integer) arguments[4]));
        }
    }
}

    @dev.openallay.value.ValueType(ForeignKeySignature.ValueSchemaProvider.class)
private static final class ForeignKeySignature {
    private final int sequence;
    private final String owner;
    private final String fromColumn;
    private final String toColumn;
    private final String onUpdate;
    private final String onDelete;
    private final String match;
    private ForeignKeySignature(int sequence, String owner, String fromColumn, String toColumn, String onUpdate, String onDelete, String match) {
        this.sequence = sequence;
        this.owner = owner;
        this.fromColumn = fromColumn;
        this.toColumn = toColumn;
        this.onUpdate = onUpdate;
        this.onDelete = onDelete;
        this.match = match;
    }
    public int sequence() { return sequence; }
    public String owner() { return owner; }
    public String fromColumn() { return fromColumn; }
    public String toColumn() { return toColumn; }
    public String onUpdate() { return onUpdate; }
    public String onDelete() { return onDelete; }
    public String match() { return match; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ForeignKeySignature)) return false;
        ForeignKeySignature that = (ForeignKeySignature) other;
        return sequence == that.sequence && java.util.Objects.equals(owner, that.owner) && java.util.Objects.equals(fromColumn, that.fromColumn) && java.util.Objects.equals(toColumn, that.toColumn) && java.util.Objects.equals(onUpdate, that.onUpdate) && java.util.Objects.equals(onDelete, that.onDelete) && java.util.Objects.equals(match, that.match);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Integer.hashCode(sequence);
        hash = 31 * hash + java.util.Objects.hashCode(owner);
        hash = 31 * hash + java.util.Objects.hashCode(fromColumn);
        hash = 31 * hash + java.util.Objects.hashCode(toColumn);
        hash = 31 * hash + java.util.Objects.hashCode(onUpdate);
        hash = 31 * hash + java.util.Objects.hashCode(onDelete);
        hash = 31 * hash + java.util.Objects.hashCode(match);
        return hash;
    }
    @Override public String toString() { return "ForeignKeySignature[sequence=" + sequence + ", owner=" + owner + ", fromColumn=" + fromColumn + ", toColumn=" + toColumn + ", onUpdate=" + onUpdate + ", onDelete=" + onDelete + ", match=" + match + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ForeignKeySignature> schema() {
            return new dev.openallay.value.ValueSchema<>(ForeignKeySignature.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ForeignKeySignature>>asList(new dev.openallay.value.ValueSchema.Component<>(ForeignKeySignature.class, "sequence", ForeignKeySignature::sequence), new dev.openallay.value.ValueSchema.Component<>(ForeignKeySignature.class, "owner", ForeignKeySignature::owner), new dev.openallay.value.ValueSchema.Component<>(ForeignKeySignature.class, "fromColumn", ForeignKeySignature::fromColumn), new dev.openallay.value.ValueSchema.Component<>(ForeignKeySignature.class, "toColumn", ForeignKeySignature::toColumn), new dev.openallay.value.ValueSchema.Component<>(ForeignKeySignature.class, "onUpdate", ForeignKeySignature::onUpdate), new dev.openallay.value.ValueSchema.Component<>(ForeignKeySignature.class, "onDelete", ForeignKeySignature::onDelete), new dev.openallay.value.ValueSchema.Component<>(ForeignKeySignature.class, "match", ForeignKeySignature::match)), arguments -> new ForeignKeySignature((Integer) arguments[0], (String) arguments[1], (String) arguments[2], (String) arguments[3], (String) arguments[4], (String) arguments[5], (String) arguments[6]));
        }
    }
}

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
