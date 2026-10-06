package dev.openallay.guide.history;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import dev.openallay.agent.context.ContextBudget;
import dev.openallay.agent.context.ContextCheckpoint;
import dev.openallay.agent.context.ContextSourceHash;
import dev.openallay.guide.GuideModelSelection;
import dev.openallay.guide.GuideRequestSnapshot;
import dev.openallay.guide.GuideRequestStatus;
import dev.openallay.guide.GuideTimelineEntry;
import dev.openallay.guide.GuideTopology;
import dev.openallay.guide.GuideSource;
import dev.openallay.testing.GroundedTestFixtures;
import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelRole;
import dev.openallay.model.ModelUsage;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SqliteGuideHistoryForkTest {
    private static final Instant NOW = Instant.parse("2026-10-02T08:00:00Z");
    private static final GuideHistoryScope SCOPE = GuideHistoryScope.derive(
            UUID.fromString("9cab9e6f-5af2-4558-b145-5c1d88deef68"),
            GuideHistoryScope.Kind.MULTIPLAYER, "fork.example");
    private static final GuideModelSelection SELECTION = GuideModelSelection.client("source-profile");
    @TempDir Path temporary;

    @Test
    void copiesEveryRecordThroughCutoffBeyondDisplayWindowAndLeavesIndependentBranches() {
        SqliteGuideHistoryStore store = store("complete.db");
        List<GuideRequestSnapshot> requests = seed(store, 181);
        GuideHistoryForkResult fork = fork(store, requests, 150, "branch");

        assertEquals(151, fork.session().requestCount());
        assertEquals(120, fork.page().requests().size());
        assertTrue(fork.page().hasEarlier());
        assertFalse(fork.page().hasLater());
        assertEquals(SELECTION, fork.session().modelSelection());
        assertEquals(List.of(ModelMessage.userText("projected boundary 150")), fork.messages());
        assertEquals(181, store.metadata(SCOPE).orElseThrow().sessions().stream()
                .filter(session -> session.sessionId().equals("main")).findFirst().orElseThrow().requestCount());
        List<GuideRequestSnapshot> full = new ArrayList<>(store.page(new GuideHistoryPageRequest(
                SCOPE, "branch", GuideHistoryPageRequest.Direction.BEFORE, fork.page().first(), 120)).requests());
        full.addAll(fork.page().requests());
        assertEquals(151, full.size());
        for (int index = 0; index < full.size(); index++) {
            GuideRequestSnapshot inherited = full.get(index);
            assertNotEquals(requests.get(index).requestId(), inherited.requestId());
            assertEquals("branch", inherited.sessionId());
            assertEquals(requests.get(index).timeline(), inherited.timeline());
            assertEquals(requests.get(index).sources(), inherited.sources());
            assertEquals(requests.get(index).usage(), inherited.usage());
            assertEquals(requests.get(index).usageProjection(), inherited.usageProjection());
            assertEquals(requests.get(index).progress(), inherited.progress());
            assertEquals(requests.get(index).requestId(), inherited.usageOriginRequestId());
            assertEquals(original(index), store.requestContext(SCOPE, inherited.requestId()));
        }
        store.commit(new GuideHistoryCommit(SCOPE, List.of(
                new GuideHistoryMutation.UpsertPartition("branch", NOW),
                new GuideHistoryMutation.DeleteSession("main"))));
        assertEquals(151, store.metadata(SCOPE).orElseThrow().sessions().getFirst().requestCount());
        assertEquals(original(0), store.requestContext(SCOPE, full.getFirst().requestId()));
        assertEquals(fork.messages(), store.context(context("branch")).messages());
    }

    @Test
    void inheritedUsageKeepsOriginalPriceAndOriginWithoutBillingTheNewBranch() {
        Path database = temporary.resolve("fork-usage.db");
        SqliteGuideHistoryStore store = store(database);
        List<GuideRequestSnapshot> requests = seed(store, 3);
        var quote = new dev.openallay.guide.GuideUsageSnapshot(100, 20, 40, 5, 2, 2, false, false,
                new java.math.BigDecimal("0.1234"), false);
        GuideRequestSnapshot source = requests.getFirst().withUsageProjection(quote);
        store.commit(new GuideHistoryCommit(SCOPE, List.of(
                new GuideHistoryMutation.UpsertRequest(0, source))));
        GuideHistoryForkResult fork = fork(store, requests, 0, "branch");
        GuideRequestSnapshot inherited = fork.page().requests().getFirst();
        assertEquals(source.requestId(), inherited.usageOriginRequestId());
        assertEquals(source.progress(), inherited.progress());
        assertEquals(source.usage(), inherited.usage());
        assertEquals(quote, inherited.usageProjection());
        assertEquals(dev.openallay.guide.GuideUsageSnapshot.empty(), fork.session().usage());
        assertEquals(quote, fork.session().inheritedUsage());
        var reopened = store(database);
        var branchMetadata = reopened.metadata(SCOPE).orElseThrow().sessions().stream()
                .filter(session -> session.sessionId().equals("branch")).findFirst().orElseThrow();
        assertEquals(quote, branchMetadata.inheritedUsage());
        assertEquals(dev.openallay.guide.GuideUsageSnapshot.empty(), branchMetadata.usage());
        var ownQuote = new dev.openallay.guide.GuideUsageSnapshot(50, 2, 10, 0, 1, 1, false, false,
                new java.math.BigDecimal("0.005"), false);
        GuideRequestSnapshot ownRequest = new GuideRequestSnapshot(UUID.randomUUID(), "branch",
                GuideTopology.CLIENT_LOCAL, "new branch request", List.of(), GuideRequestStatus.COMPLETED,
                List.of(), new ModelUsage(50, 2, 10), null, null, NOW, NOW, NOW, SELECTION)
                .withUsageProjection(ownQuote);
        reopened.commit(new GuideHistoryCommit(SCOPE, List.of(
                new GuideHistoryMutation.UpsertRequest(1, ownRequest),
                new GuideHistoryMutation.ReplaceRequestContext(ownRequest.requestId(),
                        List.of(ModelMessage.userText("new branch request"))))));
        var afterOwnRequest = reopened.metadata(SCOPE).orElseThrow().sessions().stream()
                .filter(session -> session.sessionId().equals("branch")).findFirst().orElseThrow();
        assertEquals(ownQuote, afterOwnRequest.usage());
        assertEquals(quote, afterOwnRequest.inheritedUsage());
        GuideHistoryForkResult second = reopened.fork(new GuideHistoryForkRequest(SCOPE,
                new GuideHistoryMutation.ForkSession("branch", branchMetadata.last(), "second", 2, SELECTION)));
        assertEquals(source.requestId(), second.page().requests().getFirst().usageOriginRequestId());
        assertEquals(quote, second.page().requests().getFirst().usageProjection());
        assertEquals(quote, second.session().inheritedUsage());
        assertEquals(dev.openallay.guide.GuideUsageSnapshot.empty(), second.session().usage());
        reopened.commit(new GuideHistoryCommit(SCOPE, List.of(
                new GuideHistoryMutation.UpsertPartition("second", NOW), new GuideHistoryMutation.DeleteSession("main"),
                new GuideHistoryMutation.DeleteSession("branch"))));
        var surviving = store(database).metadata(SCOPE).orElseThrow().sessions().getFirst();
        assertEquals("second", surviving.sessionId());
        assertEquals(quote, surviving.inheritedUsage());
        assertEquals(dev.openallay.guide.GuideUsageSnapshot.empty(), surviving.usage());
    }

    @Test
    void forkContinuationAndRestartAppendMessagesWithoutOverwritingInheritedRows() throws Exception {
        Path database = temporary.resolve("continuation.db");
        SqliteGuideHistoryStore store = store(database);
        List<GuideRequestSnapshot> requests = seed(store, 3);
        store.commit(new GuideHistoryCommit(SCOPE, List.of(
                new GuideHistoryMutation.UpsertMessage("main", 0, new dev.openallay.guide.GuideMessage(
                        requests.getFirst().requestId(), dev.openallay.guide.GuideMessage.Role.USER, "original user", NOW)),
                new GuideHistoryMutation.UpsertMessage("main", 1, new dev.openallay.guide.GuideMessage(
                        requests.getFirst().requestId(), dev.openallay.guide.GuideMessage.Role.ASSISTANT, "original answer", NOW)))));
        GuideHistoryForkResult fork = fork(store, requests, 1, "branch");
        assertEquals(2, fork.nextMessageOrdinal());
        assertEquals(2, store.metadata(SCOPE).orElseThrow().sessions().stream()
                .filter(session -> session.sessionId().equals("branch")).findFirst().orElseThrow().messageCount());
        UUID nextId = UUID.randomUUID();
        GuideRequestSnapshot next = new GuideRequestSnapshot(nextId, "branch", GuideTopology.CLIENT_LOCAL,
                "continue", List.of(), GuideRequestStatus.COMPLETED, List.of(), ModelUsage.empty(),
                null, null, NOW, NOW, NOW, SELECTION);
        store.commit(new GuideHistoryCommit(SCOPE, List.of(new GuideHistoryMutation.UpsertRequest(2, next),
                new GuideHistoryMutation.UpsertMessage("branch", fork.nextMessageOrdinal(),
                        new dev.openallay.guide.GuideMessage(nextId, dev.openallay.guide.GuideMessage.Role.USER,
                                "continue", NOW)))));
        try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + database);
                var rows = connection.createStatement().executeQuery(
                        "select message_text from messages where session_id = 'branch' order by ordinal")) {
            assertTrue(rows.next()); assertEquals("original user", rows.getString(1));
            assertTrue(rows.next()); assertEquals("original answer", rows.getString(1));
            assertTrue(rows.next()); assertEquals("continue", rows.getString(1));
            assertFalse(rows.next());
        }
        SqliteGuideHistoryStore reopened = store(database);
        var restarted = reopened.metadata(SCOPE).orElseThrow().sessions().stream()
                .filter(session -> session.sessionId().equals("branch")).findFirst().orElseThrow();
        assertEquals(3, restarted.messageCount());
        UUID restartId = UUID.randomUUID();
        GuideRequestSnapshot afterRestart = new GuideRequestSnapshot(restartId, "branch", GuideTopology.CLIENT_LOCAL,
                "continue after restart", List.of(), GuideRequestStatus.COMPLETED, List.of(), ModelUsage.empty(),
                null, null, NOW, NOW, NOW, SELECTION);
        reopened.commit(new GuideHistoryCommit(SCOPE, List.of(new GuideHistoryMutation.UpsertRequest(3, afterRestart),
                new GuideHistoryMutation.UpsertMessage("branch", restarted.messageCount(),
                        new dev.openallay.guide.GuideMessage(restartId, dev.openallay.guide.GuideMessage.Role.USER,
                                "continue after restart", NOW)))));
        try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + database);
                var rows = connection.createStatement().executeQuery(
                        "select ordinal, message_text from messages where session_id = 'branch' order by ordinal")) {
            for (int ordinal = 0; ordinal < 4; ordinal++) {
                assertTrue(rows.next());
                assertEquals(ordinal, rows.getInt(1));
                assertEquals(List.of("original user", "original answer", "continue", "continue after restart")
                        .get(ordinal), rows.getString(2));
            }
            assertFalse(rows.next());
        }
        assertEquals(4, store(database).metadata(SCOPE).orElseThrow().sessions().stream()
                .filter(session -> session.sessionId().equals("branch")).findFirst().orElseThrow().messageCount());
    }

    @Test
    void failedCancelledAndInterruptedRecordsForkOnlyTheirSafeCompletedContext() {
        SqliteGuideHistoryStore store = store("terminal-kinds.db");
        List<GuideRequestSnapshot> requests = seed(store, 3);
        List<GuideRequestStatus> statuses = List.of(GuideRequestStatus.FAILED,
                GuideRequestStatus.CANCELLED, GuideRequestStatus.INTERRUPTED);
        for (int index = 0; index < statuses.size(); index++) {
            GuideRequestSnapshot source = requests.get(index);
            String code = statuses.get(index) == GuideRequestStatus.CANCELLED
                    ? "agent_cancelled" : "synthetic_terminal_failure";
            store.commit(new GuideHistoryCommit(SCOPE, List.of(new GuideHistoryMutation.UpsertRequest(index,
                    new GuideRequestSnapshot(source.requestId(), "main", source.topology(), source.userMessage(),
                            source.timeline(), statuses.get(index), source.sources(), source.usage(), null,
                            new dev.openallay.guide.GuideFailure(code, "synthetic terminal diagnostic"),
                            source.createdAt(), source.updatedAt(), source.terminalAt(), source.modelSelection())))));
            GuideHistoryForkResult branch = fork(store, requests, index, "kind-" + index);
            assertEquals(statuses.get(index), branch.page().requests().getLast().status());
            assertEquals(List.of(ModelMessage.userText("projected boundary " + index)), branch.messages());
        }
        store.commit(new GuideHistoryCommit(SCOPE, List.of(new GuideHistoryMutation.DeleteSession("kind-0"))));
        assertEquals(original(0), store.requestContext(SCOPE, requests.getFirst().requestId()));
        assertEquals(3, store.metadata(SCOPE).orElseThrow().sessions().stream()
                .filter(session -> session.sessionId().equals("main")).findFirst().orElseThrow().requestCount());
    }

    @Test
    void toolIdsOriginalSourcesAndErrorResultsArePreservedWithoutReplay() {
        SqliteGuideHistoryStore store = store("pairs.db");
        List<GuideRequestSnapshot> requests = seed(store, 2);
        List<ModelMessage> actual = pair("request-qualified-tool-call");
        store.commit(new GuideHistoryCommit(SCOPE, List.of(
                new GuideHistoryMutation.ReplaceRequestContext(requests.getFirst().requestId(), actual),
                new GuideHistoryMutation.CaptureRequestBoundary(requests.getFirst().requestId(), actual, List.of()))));
        GuideHistoryForkResult fork = fork(store, requests, 0, "branch");
        UUID target = fork.page().requests().getFirst().requestId();
        assertEquals(actual, store.requestContext(SCOPE, target));
        assertEquals(actual, fork.messages());
        ModelContent.ToolUse use = (ModelContent.ToolUse) fork.messages().get(1).content().getFirst();
        ModelContent.ToolResult result = (ModelContent.ToolResult) fork.messages().get(2).content().getFirst();
        assertEquals("request-qualified-tool-call", use.id());
        assertEquals(use.id(), result.toolUseId());
        assertTrue(result.error());
        assertEquals("synthetic_original_source", result.value().getAsJsonObject().get("sourceId").getAsString());
    }

    @Test
    void usesSelectedBoundaryNotLaterCompactedOrActiveContextAndFiltersCheckpointReuse() {
        SqliteGuideHistoryStore store = store("cutoff.db");
        List<GuideRequestSnapshot> requests = seed(store, 3);
        List<ModelMessage> actual = pair("historical-call");
        ContextCheckpoint valid = checkpoint(actual, actual.size(), ContextSourceHash.compute(dev.openallay.json.EngineJson.create(), actual));
        ContextCheckpoint stale = checkpoint(actual, actual.size(), "b".repeat(64));
        ContextCheckpoint split = checkpoint(actual, 2, ContextSourceHash.compute(dev.openallay.json.EngineJson.create(), actual.subList(0, 2)));
        ContextCheckpoint failed = new ContextCheckpoint(UUID.randomUUID(), 0, actual.size(),
                ContextSourceHash.compute(dev.openallay.json.EngineJson.create(), actual), "fork-model", NOW, ContextCheckpoint.Status.FAILED,
                null, "synthetic_compaction_failure", "original checkpoint diagnostic", 10);
        store.commit(new GuideHistoryCommit(SCOPE, List.of(
                new GuideHistoryMutation.CaptureRequestBoundary(requests.get(1).requestId(), actual,
                        List.of(valid, stale, split, failed)),
                new GuideHistoryMutation.ReplaceContext("main", List.of(ModelMessage.userText("future compacted summary"))))));
        GuideHistoryForkResult fork = fork(store, requests, 1, "branch");
        assertEquals(actual, fork.messages());
        assertEquals(4, fork.checkpoints().size());
        assertEquals(1, GuideHistoryForkResult.reusableCheckpoints(fork.checkpoints(), fork.messages()).size());
        List<ContextCheckpoint> originals = List.of(valid, stale, split, failed);
        for (int index = 0; index < originals.size(); index++) {
            ContextCheckpoint original = originals.get(index);
            ContextCheckpoint copied = fork.checkpoints().get(index);
            assertNotEquals(original.checkpointId(), copied.checkpointId());
            assertEquals(original.sourceFromIndex(), copied.sourceFromIndex());
            assertEquals(original.sourceToIndexExclusive(), copied.sourceToIndexExclusive());
            assertEquals(original.sourceHash(), copied.sourceHash());
            assertEquals(original.modelIdentifier(), copied.modelIdentifier());
            assertEquals(original.createdAt(), copied.createdAt());
            assertEquals(original.status(), copied.status());
            assertEquals(original.summary(), copied.summary());
            assertEquals(original.failureCode(), copied.failureCode());
            assertEquals(original.failureMessage(), copied.failureMessage());
            assertEquals(original.estimatedProjectionTokens(), copied.estimatedProjectionTokens());
        }
        assertEquals(actual, store.context(context("branch")).messages());
        assertEquals(1, store.context(context("branch")).checkpoints().size());
    }

    @Test
    void newCompactionAppendsAfterEveryInheritedCheckpointDiagnosticWithoutOverwritingValidCopy() throws Exception {
        Path database = temporary.resolve("checkpoint-order.db");
        SqliteGuideHistoryStore store = store(database); List<GuideRequestSnapshot> requests = seed(store, 1);
        List<ModelMessage> messages = pair("checkpoint-order-call");
        ContextCheckpoint stale = checkpoint(messages, messages.size(), "c".repeat(64));
        ContextCheckpoint valid = checkpoint(messages, messages.size(), ContextSourceHash.compute(dev.openallay.json.EngineJson.create(), messages));
        store.commit(new GuideHistoryCommit(SCOPE, List.of(new GuideHistoryMutation.CaptureRequestBoundary(
                requests.getFirst().requestId(), messages, List.of(stale, valid)))));
        GuideHistoryForkResult branch = fork(store, requests, 0, "branch");
        assertEquals(2, branch.checkpoints().size());
        assertEquals(stale.sourceHash(), branch.checkpoints().getFirst().sourceHash());
        assertEquals(valid.sourceHash(), branch.checkpoints().getLast().sourceHash());
        ContextCheckpoint newCheckpoint = checkpoint(messages, messages.size(),
                ContextSourceHash.compute(dev.openallay.json.EngineJson.create(), messages));
        store.commit(new GuideHistoryCommit(SCOPE, List.of(new GuideHistoryMutation.UpsertCheckpoint(
                "branch", branch.checkpoints().size(), newCheckpoint))));
        try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + database);
                var rows = connection.createStatement().executeQuery(
                        "select checkpoint_id from compaction_checkpoints where session_id = 'branch' order by ordinal")) {
            assertTrue(rows.next()); assertEquals(branch.checkpoints().getFirst().checkpointId().toString(), rows.getString(1));
            assertTrue(rows.next()); assertEquals(branch.checkpoints().getLast().checkpointId().toString(), rows.getString(1));
            assertTrue(rows.next()); assertEquals(newCheckpoint.checkpointId().toString(), rows.getString(1));
            assertFalse(rows.next());
        }
    }

    @Test
    void reopenedCheckpointAppendPreservesAllInheritedDiagnosticsAndIsIdempotentByIdentity() throws Exception {
        Path database = temporary.resolve("checkpoint-restart.db");
        SqliteGuideHistoryStore store = store(database);
        List<GuideRequestSnapshot> requests = seed(store, 1);
        List<ModelMessage> messages = pair("checkpoint-restart-call");
        String hash = ContextSourceHash.compute(dev.openallay.json.EngineJson.create(), messages);
        ContextCheckpoint stale = checkpoint(messages, messages.size(), "d".repeat(64));
        ContextCheckpoint valid = checkpoint(messages, messages.size(), hash);
        ContextCheckpoint failed = new ContextCheckpoint(UUID.randomUUID(), 0, messages.size(), hash,
                "fork-model", NOW, ContextCheckpoint.Status.FAILED, null, "synthetic_old_failure",
                "retained inherited diagnostic", 10);
        store.commit(new GuideHistoryCommit(SCOPE, List.of(new GuideHistoryMutation.CaptureRequestBoundary(
                requests.getFirst().requestId(), messages, List.of(stale, valid, failed)))));
        GuideHistoryForkResult branch = fork(store, requests, 0, "branch");
        assertEquals(3, branch.checkpoints().size());
        SqliteGuideHistoryStore reopened = store(database);
        // Runtime loads only the applicable checkpoint, not all diagnostic ordinals.
        assertEquals(1, reopened.context(context("branch")).checkpoints().size());
        ContextCheckpoint appended = checkpoint(messages, messages.size(), hash);
        ContextCheckpoint newFailure = new ContextCheckpoint(UUID.randomUUID(), 0, messages.size(), hash,
                "fork-model", NOW, ContextCheckpoint.Status.FAILED, null, "synthetic_new_failure",
                "new independent diagnostic", 10);
        reopened.commit(new GuideHistoryCommit(SCOPE, List.of(
                new GuideHistoryMutation.AppendCheckpoint("branch", appended),
                new GuideHistoryMutation.AppendCheckpoint("branch", appended),
                new GuideHistoryMutation.AppendCheckpoint("branch", newFailure))));
        // A retry after another restart retains the assigned ordinal as well.
        store(database).commit(new GuideHistoryCommit(SCOPE, List.of(
                new GuideHistoryMutation.AppendCheckpoint("branch", appended))));
        assertEquals(appended, store(database).context(context("branch")).checkpoints().getFirst());
        ContextCheckpoint compensated = new ContextCheckpoint(appended.checkpointId(),
                appended.sourceFromIndex(), appended.sourceToIndexExclusive(), appended.sourceHash(),
                appended.modelIdentifier(), appended.createdAt(), ContextCheckpoint.Status.FAILED,
                null, "synthetic_append_compensation", "retained final failure after success", 10);
        store(database).commit(new GuideHistoryCommit(SCOPE, List.of(
                new GuideHistoryMutation.AppendCheckpoint("branch", compensated))));
        List<ContextCheckpoint> expected = new ArrayList<>(branch.checkpoints());
        expected.add(compensated);
        expected.add(newFailure);
        try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + database);
                var rows = connection.createStatement().executeQuery(
                        "select ordinal, checkpoint_id, payload_json from compaction_checkpoints "
                                + "where session_id = 'branch' order by ordinal")) {
            GuideHistoryCodec codec = new GuideHistoryCodec();
            for (int ordinal = 0; ordinal < expected.size(); ordinal++) {
                assertTrue(rows.next());
                assertEquals(ordinal, rows.getInt(1));
                assertEquals(expected.get(ordinal).checkpointId().toString(), rows.getString(2));
                assertEquals(expected.get(ordinal), codec.decodeCheckpoint(rows.getString(3)));
            }
            assertFalse(rows.next());
        }
        assertNull(compensated.summary());
        assertEquals("synthetic_append_compensation", compensated.failureCode());
        assertEquals(branch.checkpoints().get(1),
                store(database).context(context("branch")).checkpoints().getFirst());
        // The unique scoped checkpoint identity must not update another session's payload.
        assertCode("history_write_failed", () -> store(database).commit(new GuideHistoryCommit(SCOPE,
                List.of(new GuideHistoryMutation.AppendCheckpoint("main", compensated)))));
        assertEquals(branch.checkpoints().get(1),
                store(database).context(context("branch")).checkpoints().getFirst());
    }

    @Test
    void refusesNonterminalWrongPartitionMissingBoundaryAndInvalidPairWithoutGuessingUiText() {
        SqliteGuideHistoryStore store = store("reject.db");
        List<GuideRequestSnapshot> requests = seed(store, 3);
        GuideRequestSnapshot original = requests.get(2);
        GuideRequestSnapshot active = new GuideRequestSnapshot(original.requestId(), "main",
                original.topology(), original.userMessage(), original.timeline(), GuideRequestStatus.TOOL_WAIT,
                List.of(), ModelUsage.empty(), null, null, NOW, NOW, null, SELECTION);
        store.commit(new GuideHistoryCommit(SCOPE, List.of(new GuideHistoryMutation.UpsertRequest(2, active))));
        assertCode("fork_boundary_unavailable", () -> fork(store, requests, 2, "active-branch"));
        // A still-active successor does not invalidate the earlier immutable completed cutoff.
        assertEquals(2, fork(store, requests, 1, "safe-prefix").session().requestCount());
        GuideHistoryScope other = GuideHistoryScope.derive(SCOPE.actorId(), SCOPE.kind(), "other.example");
        assertCode("fork_unavailable", () -> store.fork(new GuideHistoryForkRequest(other,
                mutation(requests.getFirst(), 0, "wrong-scope"))));
        store.commit(new GuideHistoryCommit(SCOPE, List.of(new GuideHistoryMutation.UpsertRequest(2, original))));
        GuideRequestSnapshot noBoundary = request(3);
        store.commit(new GuideHistoryCommit(SCOPE, List.of(
                new GuideHistoryMutation.UpsertRequest(3, noBoundary),
                new GuideHistoryMutation.ReplaceRequestContext(noBoundary.requestId(), original(3)))));
        assertCode("fork_context_unavailable", () -> store.fork(new GuideHistoryForkRequest(SCOPE,
                mutation(noBoundary, 3, "no-boundary"))));
        List<ModelMessage> partial = List.of(new ModelMessage(ModelRole.ASSISTANT,
                List.of(new ModelContent.ToolUse("unmatched", "openallay:test", new JsonObject()))));
        assertThrows(IllegalArgumentException.class, () -> new GuideHistoryMutation.CaptureRequestBoundary(
                requests.getFirst().requestId(), partial, List.of()));
        assertEquals(2, store.metadata(SCOPE).orElseThrow().sessions().size());
    }

    @Test
    void failedAtomicCloneLeavesSourceAndNoTargetAndForkOfForkHasDistinctRequests() {
        Path database = temporary.resolve("rollback.db");
        SqliteGuideHistoryStore store = store(database);
        List<GuideRequestSnapshot> requests = seed(store, 4);
        SqliteGuideHistoryStore failing = new SqliteGuideHistoryStore(database,
                Clock.fixed(NOW, ZoneOffset.UTC), new GuideHistoryCodec(), mutation -> {
                    throw new IllegalStateException("synthetic commit failure");
                });
        assertCode("history_fork_failed", () -> fork(failing, requests, 2, "not-committed"));
        assertEquals(1, store.metadata(SCOPE).orElseThrow().sessions().size());
        assertEquals(original(2), store.requestContext(SCOPE, requests.get(2).requestId()));
        GuideHistoryForkResult first = fork(store, requests, 2, "first");
        GuideHistoryForkResult second = store.fork(new GuideHistoryForkRequest(SCOPE,
                new GuideHistoryMutation.ForkSession("first", first.session().last(), "second", 2, SELECTION)));
        assertEquals(first.messages(), second.messages());
        assertNotEquals(first.page().requests().getLast().requestId(), second.page().requests().getLast().requestId());
        assertEquals(original(2), store.requestContext(SCOPE, second.page().requests().getLast().requestId()));
        assertCode("fork_target_exists", () -> fork(store, requests, 2, "second"));
    }

    private List<GuideRequestSnapshot> seed(SqliteGuideHistoryStore store, int count) {
        List<GuideHistoryMutation> mutations = new ArrayList<>(List.of(
                new GuideHistoryMutation.UpsertPartition("main", NOW),
                new GuideHistoryMutation.UpsertSession("main", 0, SELECTION)));
        List<GuideRequestSnapshot> requests = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            GuideRequestSnapshot request = request(index);
            requests.add(request);
            mutations.add(new GuideHistoryMutation.UpsertRequest(index, request));
            mutations.add(new GuideHistoryMutation.UpsertTimelineEntry(request.requestId(), request.timeline().getFirst()));
            mutations.add(new GuideHistoryMutation.ReplaceRequestSources(request.requestId(), request.sources()));
            mutations.add(new GuideHistoryMutation.ReplaceRequestContext(request.requestId(), original(index)));
            mutations.add(new GuideHistoryMutation.CaptureRequestBoundary(request.requestId(),
                    List.of(ModelMessage.userText("projected boundary " + index)), List.of()));
        }
        mutations.add(new GuideHistoryMutation.ReplaceContext("main", List.of(ModelMessage.userText("current tail"))));
        store.commit(new GuideHistoryCommit(SCOPE, mutations));
        return requests;
    }

    private static GuideRequestSnapshot request(int index) {
        return new GuideRequestSnapshot(UUID.randomUUID(), "main", GuideTopology.CLIENT_LOCAL,
                "question " + index, List.of(new GuideTimelineEntry.Assistant(0, "answer " + index, false, List.of())),
                GuideRequestStatus.COMPLETED, List.of(new GuideSource("openallay:get_recipe",
                        GroundedTestFixtures.serverEvidence(), NOW.plusSeconds(index))),
                new ModelUsage(10, 20, 5), null, null, NOW, NOW, NOW, SELECTION);
    }
    private static List<ModelMessage> original(int index) {
        return List.of(ModelMessage.userText("question " + index),
                new ModelMessage(ModelRole.ASSISTANT, List.of(new ModelContent.Text("actual answer " + index))));
    }
    private static List<ModelMessage> pair(String callId) {
        JsonObject input = new JsonObject(); input.addProperty("action", "read only");
        JsonObject result = new JsonObject(); result.addProperty("sourceId", "synthetic_original_source");
        return List.of(ModelMessage.userText("historical original"),
                new ModelMessage(ModelRole.ASSISTANT, List.of(new ModelContent.ToolUse(callId, "openallay:test", input))),
                new ModelMessage(ModelRole.USER, List.of(new ModelContent.ToolResult(callId, result, true))));
    }
    private static ContextCheckpoint checkpoint(List<ModelMessage> ignored, int to, String hash) {
        return new ContextCheckpoint(UUID.randomUUID(), 0, to, hash, "fork-model", NOW,
                ContextCheckpoint.Status.SUCCEEDED, "synthetic summary", null, null, 10);
    }
    private static GuideHistoryContextRequest context(String session) {
        return new GuideHistoryContextRequest(SCOPE, session, new ContextBudget(8_192, 1_024), 256, "fork-model");
    }
    private GuideHistoryForkResult fork(SqliteGuideHistoryStore store, List<GuideRequestSnapshot> requests,
            int cutoff, String target) {
        return store.fork(new GuideHistoryForkRequest(SCOPE, mutation(requests.get(cutoff), cutoff, target)));
    }
    private static GuideHistoryMutation.ForkSession mutation(GuideRequestSnapshot request, int sequence, String target) {
        return new GuideHistoryMutation.ForkSession("main", new GuideHistoryCursor(sequence, request.requestId()),
                target, 1, SELECTION);
    }
    private SqliteGuideHistoryStore store(String name) { return store(temporary.resolve(name)); }
    private static SqliteGuideHistoryStore store(Path database) {
        return new SqliteGuideHistoryStore(database, Clock.fixed(NOW, ZoneOffset.UTC), new GuideHistoryCodec());
    }
    private static void assertCode(String code, Runnable operation) {
        assertEquals(code, assertThrows(GuideHistoryException.class, operation::run).code());
    }
}
