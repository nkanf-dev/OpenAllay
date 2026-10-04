package dev.openallay.guide.history;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openallay.guide.GuideModelSelection;
import dev.openallay.guide.GuideRequestSnapshot;
import dev.openallay.guide.GuideRequestStatus;
import dev.openallay.guide.GuideTopology;
import dev.openallay.guide.GuideUsageSnapshot;
import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelRole;
import dev.openallay.model.ModelUsage;
import dev.openallay.model.image.FileImageAttachmentStore;
import dev.openallay.model.image.ImageAttachmentStore;
import dev.openallay.model.image.ImageInputLimits;
import dev.openallay.model.image.ImageReference;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SqliteGuideImageOwnershipTest {
    private static final UUID ACTOR = UUID.fromString("e3a55180-4a8f-41d0-979d-914982a9757c");
    private static final Instant NOW = Instant.parse("2026-10-02T10:00:00Z");
    @TempDir Path temporary;

    @Test
    void requestAndOtherSessionOwnersRemainIndependentOfCompactedSource() throws Exception {
        var images = images();
        var reference = images.importImage(ACTOR, png(0xff234567));
        var store = store(images);
        var scope = scope("original.example");
        var request = request("main", "original");
        seed(store, scope, request, reference);
        store.commit(new GuideHistoryCommit(scope, List.of(
                new GuideHistoryMutation.UpsertSession("retained", 1, GuideModelSelection.client("default")),
                new GuideHistoryMutation.ReplaceContext("retained", messages(reference)),
                new GuideHistoryMutation.ReplaceContext("main", List.of(ModelMessage.userText("compacted"))))));
        assertEquals(0, images.collect(ACTOR));
        store.commit(new GuideHistoryCommit(scope, List.of(new GuideHistoryMutation.DeleteSession("main"))));
        assertArrayEquals(png(0xff234567), images.read(ACTOR, reference));
        assertEquals(0, images.collect(ACTOR));
        store.commit(new GuideHistoryCommit(scope, List.of(new GuideHistoryMutation.ClearSession("retained"))));
        assertThrows(IOException.class, () -> images.read(ACTOR, reference));
        assertEquals(0, images.collect(ACTOR));
    }

    @Test
    void originalRequestImagesSurviveSessionCompactionBeyondOneUiPage() throws Exception {
        var images = images();
        var reference = images.importImage(ACTOR, png(0xff345678));
        var store = store(images);
        var scope = scope("paged.example");
        var first = request("main", "old");
        seed(store, scope, first, reference);
        for (int index = 1; index <= 8; index++) {
            var newer = request("main", "new-" + index);
            store.commit(new GuideHistoryCommit(scope, List.of(
                    new GuideHistoryMutation.UpsertRequest(index, newer),
                    new GuideHistoryMutation.ReplaceRequestContext(newer.requestId(),
                            List.of(ModelMessage.userText(newer.userMessage()))))));
        }
        store.commit(new GuideHistoryCommit(scope, List.of(
                new GuideHistoryMutation.ReplaceContext("main", List.of(ModelMessage.userText("summary"))))));
        assertEquals(1, store.page(new GuideHistoryPageRequest(scope, "main",
                GuideHistoryPageRequest.Direction.NEWEST, null, 1)).requests().size());
        assertEquals(0, images.collect(ACTOR));
        assertEquals(messages(reference), store.requestContext(scope, first.requestId()));
        store.delete(GuideHistoryDeleteScope.partition(scope));
        assertThrows(IOException.class, () -> images.read(ACTOR, reference));
    }

    @Test
    void deletingOnePartitionCannotReleaseSameActorOtherPartition() throws Exception {
        var images = images();
        var first = images.importImage(ACTOR, png(0xff345678));
        var second = images.importImage(ACTOR, png(0xff456789));
        var store = store(images);
        var firstScope = scope("first.example");
        var secondScope = scope("second.example");
        seed(store, firstScope, request("main", "first"), first);
        seed(store, secondScope, request("main", "second"), second);
        store.delete(GuideHistoryDeleteScope.partition(firstScope));
        assertThrows(IOException.class, () -> images.read(ACTOR, first));
        assertArrayEquals(png(0xff456789), images.read(ACTOR, second));
        store.delete(GuideHistoryDeleteScope.actor(ACTOR));
        assertThrows(IOException.class, () -> images.read(ACTOR, second));
    }

    @Test
    void missingNewAssetFailsBeforeAnySqlMutation() throws Exception {
        var images = images();
        var store = store(images);
        var scope = scope("missing.example");
        var missing = new ImageReference("a".repeat(64), "image/png", 4, 3, 100);
        var request = request("main", "missing");
        GuideHistoryException failure = assertThrows(GuideHistoryException.class,
                () -> seed(store, scope, request, missing));
        assertEquals("history_write_failed", failure.code());
        assertTrue(store.metadata(scope).isEmpty());
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database());
                var query = connection.createStatement().executeQuery("select count(*) from requests")) {
            assertTrue(query.next());
            assertEquals(0, query.getInt(1));
        }
    }

    @Test
    void sqlRollbackKeepsOldImagesAndDoesNotCollectNewBytesBeforeCommit() throws Exception {
        var images = images();
        var first = images.importImage(ACTOR, png(0xff234567));
        var second = images.importImage(ACTOR, png(0xffabcdef));
        var scope = scope("rollback.example");
        var request = request("main", "old");
        seed(store(images), scope, request, first);
        var failing = new SqliteGuideHistoryStore(database(), clock(), new GuideHistoryCodec(), images,
                mutation -> {
                    if (mutation == SqliteGuideHistoryStore.Mutation.COMMIT) {
                        try {
                            assertEquals(0, images.collect(ACTOR));
                            images.read(ACTOR, second);
                        } catch (IOException problem) { throw new SQLException(problem); }
                        throw new SQLException("injected before durable commit");
                    }
                });
        assertThrows(GuideHistoryException.class, () -> failing.commit(new GuideHistoryCommit(scope,
                List.of(new GuideHistoryMutation.ReplaceContext("main", messages(second))))));
        assertEquals(messages(first), store(images).requestContext(scope, request.requestId()));
        assertEquals(1, images.collect(ACTOR));
        images.read(ACTOR, first);
        assertThrows(IOException.class, () -> images.read(ACTOR, second));
    }

    @Test
    void postCommitManifestFailureIsDurableSuccessAndRecoveredAfterReopen() throws Exception {
        var images = images();
        var reference = images.importImage(ACTOR, png(0xffabcdef));
        var scope = scope("postcommit.example");
        var failures = new AtomicBoolean();
        ImageAttachmentStore failingImages = new DelegatingStore(images) {
            @Override
            public void reconcile(UUID actor, String namespace, Map<String, List<ImageReference>> owners)
                    throws IOException {
                if (namespace.equals("scope:" + scope.scopeId()) && failures.compareAndSet(true, false)) {
                    throw new IOException("injected after SQL commit");
                }
                super.reconcile(actor, namespace, owners);
            }
        };
        var store = new SqliteGuideHistoryStore(database(), clock(), new GuideHistoryCodec(), failingImages,
                mutation -> { if (mutation == SqliteGuideHistoryStore.Mutation.COMMIT) failures.set(true); });
        var request = request("main", "durable");
        seed(store, scope, request, reference); // Must not throw a false rollback/retryable failure.
        assertEquals(0, images.collect(ACTOR)); // Only the durable pending owner currently pins it.
        var reopened = store(images);
        assertTrue(reopened.metadata(scope).isPresent());
        assertEquals(messages(reference), reopened.requestContext(scope, request.requestId()));
        assertEquals(0, images.collect(ACTOR));
        reopened.delete(GuideHistoryDeleteScope.partition(scope));
        assertThrows(IOException.class, () -> images.read(ACTOR, reference));
    }

    @Test
    void abandonedWritePinsAreClearedOnlyAfterReconciliationUnderTheDatabaseLock() throws Exception {
        var images = images();
        var durable = images.importImage(ACTOR, png(0xff123456));
        var abandoned = images.importImage(ACTOR, png(0xffabcdef));
        var scope = scope("crash.example");
        seed(store(images), scope, request("main", "durable"), durable);
        images.reconcile(ACTOR, "scope-write:" + scope.scopeId(),
                Map.of("pending", List.of(durable, abandoned)));
        assertEquals(0, images.collect(ACTOR));
        assertTrue(store(images).metadata(scope).isPresent());
        assertEquals(1, images.collect(ACTOR));
        images.read(ACTOR, durable);
        assertThrows(IOException.class, () -> images.read(ACTOR, abandoned));
    }

    @Test
    void explicitResetCollectsOwnedAssetsButNeverForeignPermanentExports() throws Exception {
        var images = images();
        var reference = images.importImage(ACTOR, png(0xff123456));
        var store = store(images);
        var scope = scope("reset.example");
        seed(store, scope, request("main", "saved"), reference);
        Path exported = temporary.resolve("exports/images/" + reference.sha256() + ".png");
        Files.createDirectories(exported.getParent());
        Files.write(exported, images.read(ACTOR, reference));
        store.resetDatabase();
        assertTrue(store.metadata(scope).isEmpty());
        assertThrows(IOException.class, () -> images.read(ACTOR, reference));
        assertArrayEquals(png(0xff123456), Files.readAllBytes(exported));
    }

    @Test
    void malformedExistingContextRejectsMutationWithoutChangingSqlOrImageOwners() throws Exception {
        var images = images();
        var reference = images.importImage(ACTOR, png(0xff123456));
        var scope = scope("malformed.example");
        var store = store(images);
        seed(store, scope, request("main", "saved"), reference);
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database());
                var update = connection.createStatement()) {
            update.executeUpdate("update model_context set payload_json = '[{}]'");
        }
        assertThrows(RuntimeException.class, () -> store.commit(new GuideHistoryCommit(scope,
                List.of(new GuideHistoryMutation.UpsertPartition("must-not-write", NOW),
                        new GuideHistoryMutation.ReplaceContext("main", List.of(ModelMessage.userText("new")))))));
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database());
                var query = connection.createStatement().executeQuery("select selected_session from partitions")) {
            assertTrue(query.next());
            assertEquals("main", query.getString(1));
        }
        assertEquals(0, images.collect(ACTOR));
    }

    @Test
    void reopenedStoresSerializeScopeReconciliationAndWrites() throws Exception {
        var images = images();
        var first = images.importImage(ACTOR, png(0xff123456));
        var second = images.importImage(ACTOR, png(0xffabcdef));
        // Each request owner keeps both assets usable while session snapshots are replaced.
        var scope = scope("concurrent.example");
        var original = request("main", "first");
        var store = store(images);
        seed(store, scope, original, first);
        var other = request("main", "second");
        store.commit(new GuideHistoryCommit(scope, List.of(
                new GuideHistoryMutation.UpsertRequest(1, other),
                new GuideHistoryMutation.ReplaceRequestContext(other.requestId(), messages(second)))));
        try (var workers = Executors.newFixedThreadPool(2)) {
            var one = workers.submit(() -> {
                for (int index = 0; index < 8; index++) store(images).commit(new GuideHistoryCommit(scope,
                        List.of(new GuideHistoryMutation.ReplaceContext("main", messages(first)))));
            });
            var two = workers.submit(() -> {
                for (int index = 0; index < 8; index++) store(images).commit(new GuideHistoryCommit(scope,
                        List.of(new GuideHistoryMutation.ReplaceContext("main", messages(second)))));
            });
            one.get();
            two.get();
        }
        assertEquals(0, images.collect(ACTOR));
        images.read(ACTOR, first);
        images.read(ACTOR, second);
    }

    @Test
    void corruptExistingAssetFailsBeforeChangingAnySqlRow() throws Exception {
        var images = images();
        var reference = images.importImage(ACTOR, png(0xff123456));
        var scope = scope("corrupt-image.example");
        var store = store(images);
        seed(store, scope, request("main", "saved"), reference);
        Path asset = temporary.resolve("images").resolve(ACTOR.toString()).resolve(reference.sha256());
        byte[] changed = Files.readAllBytes(asset);
        changed[changed.length - 1] ^= 1;
        Files.write(asset, changed);
        assertThrows(GuideHistoryException.class, () -> store.commit(new GuideHistoryCommit(scope,
                List.of(new GuideHistoryMutation.UpsertPartition("must-not-write", NOW),
                        new GuideHistoryMutation.ReplaceContext("main", List.of(ModelMessage.userText("new")))))));
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database());
                var query = connection.createStatement().executeQuery("select selected_session from partitions")) {
            assertTrue(query.next());
            assertEquals("main", query.getString(1));
        }
        assertEquals(0, images.collect(ACTOR));
    }

    @Test
    void imageWritesRequireAnExplicitStoreAndActorAuthorization() throws Exception {
        var images = images();
        var reference = images.importImage(ACTOR, png(0xff123456));
        var scope = scope("actor-image.example");
        var textOnly = new SqliteGuideHistoryStore(database(), clock(), new GuideHistoryCodec());
        assertThrows(GuideHistoryException.class,
                () -> seed(textOnly, scope, request("main", "image"), reference));
        assertTrue(textOnly.metadata(scope).isEmpty());
        var anotherActor = new GuideHistoryScope(UUID.randomUUID(), scope.kind(), scope.scopeId());
        assertThrows(GuideHistoryException.class,
                () -> seed(store(images), anotherActor, request("main", "unauthorized"), reference));
        assertTrue(textOnly.metadata(scope).isEmpty());
    }

    @Test
    void steadyStatePagesAndTimelineUpdatesDoNotRescanHistoricalImagePayloads() throws Exception {
        var images = images();
        var reference = images.importImage(ACTOR, png(0xff123456));
        var scope = scope("no-rescan.example");
        var reconciles = new java.util.concurrent.atomic.AtomicInteger();
        var counts = new DelegatingStore(images) {
            @Override
            public void reconcile(UUID actor, String namespace, Map<String, List<ImageReference>> owners)
                    throws IOException {
                reconciles.incrementAndGet();
                super.reconcile(actor, namespace, owners);
            }
        };
        var store = store(counts);
        var request = request("main", "saved");
        seed(store, scope, request, reference);
        reconciles.set(0);
        for (int index = 0; index < 5; index++) {
            store.page(new GuideHistoryPageRequest(scope, "main",
                    GuideHistoryPageRequest.Direction.NEWEST, null, 1));
            store.requestContext(scope, request.requestId());
            store.metadata(scope);
            store.commit(new GuideHistoryCommit(scope, List.of(
                    new GuideHistoryMutation.UpsertRequest(0, request))));
        }
        assertEquals(0, reconciles.get());
        var reopened = store(counts);
        reopened.metadata(scope);
        assertEquals(2, reconciles.get()); // One scope reconcile, then abandoned pending cleanup.
        reconciles.set(0);
        reopened.metadata(scope);
        assertEquals(0, reconciles.get());
    }

    @Test
    void replacingBothContextOwnersReleasesOnlyTheOldImage() throws Exception {
        var images = images();
        var first = images.importImage(ACTOR, png(0xff123456));
        var second = images.importImage(ACTOR, png(0xffabcdef));
        var scope = scope("replace.example");
        var store = store(images);
        var request = request("main", "saved");
        seed(store, scope, request, first);
        store.commit(new GuideHistoryCommit(scope, List.of(
                new GuideHistoryMutation.ReplaceRequestContext(request.requestId(), messages(second)),
                new GuideHistoryMutation.ReplaceContext("main", messages(second)))));
        assertEquals(1, images.collect(ACTOR));
        assertThrows(IOException.class, () -> images.read(ACTOR, first));
        assertEquals(messages(second), store.requestContext(scope, request.requestId()));
        images.read(ACTOR, second);
    }

    @Test
    void imageContextWritesPreserveAllUsagePresenceAndBillingFieldsAfterReopen() throws Exception {
        var images = images();
        var reference = images.importImage(ACTOR, png(0xff123456));
        var scope = scope("usage-image.example");
        var original = request("main", "saved");
        // Unknown output and cache read must not become reported zeroes. Cache creation is
        // distinct from a cache hit, and the numeric bill is retained independently of images.
        var usage = new ModelUsage(19, 0, 0, 5, 14,
                false, false, false, true, true);
        var bill = new GuideUsageSnapshot(19, 0, 0, 5,
                1, 1, true, true, new BigDecimal("0.001234"), true);
        var request = new GuideRequestSnapshot(original.requestId(), original.sessionId(),
                original.topology(), original.userMessage(), original.timeline(), original.status(),
                original.sources(), usage, original.retryAfterMillis(), original.failure(),
                original.createdAt(), original.updatedAt(), original.terminalAt(),
                original.modelSelection(), original.progress(), bill, null);
        seed(store(images), scope, request, reference);
        var reopened = store(images);
        var loaded = reopened.page(new GuideHistoryPageRequest(scope, "main",
                GuideHistoryPageRequest.Direction.NEWEST, null, 1)).requests().getFirst();
        assertEquals(usage, loaded.usage());
        assertEquals(bill, loaded.usageProjection());
        assertEquals(bill, reopened.metadata(scope).orElseThrow().sessions().getFirst().usage());
        assertEquals(messages(reference), reopened.requestContext(scope, request.requestId()));
    }

    @Test
    void interruptedImageRequestRecoveryKeepsBothOriginalAndSessionReferences() throws Exception {
        var images = images();
        var reference = images.importImage(ACTOR, png(0xff123456));
        var scope = scope("recover-image.example");
        var original = request("main", "accepted");
        var active = new GuideRequestSnapshot(original.requestId(), "main", original.topology(),
                original.userMessage(), List.of(), GuideRequestStatus.MODEL_WAIT, List.of(),
                ModelUsage.empty(), null, null, NOW, NOW, null);
        seed(store(images), scope, active, reference);
        var reopened = store(images);
        assertTrue(reopened.metadata(scope).isPresent());
        var recovered = reopened.page(new GuideHistoryPageRequest(scope, "main",
                GuideHistoryPageRequest.Direction.NEWEST, null, 1)).requests().getFirst();
        assertEquals(GuideRequestStatus.INTERRUPTED, recovered.status());
        var context = reopened.requestContext(scope, active.requestId());
        assertEquals(messages(reference).getFirst(), context.getFirst());
        assertEquals(2, context.size());
        assertEquals(0, images.collect(ACTOR));
        images.read(ACTOR, reference);
        reopened.commit(new GuideHistoryCommit(scope,
                List.of(new GuideHistoryMutation.ClearSession("main"))));
        assertThrows(IOException.class, () -> images.read(ACTOR, reference));
    }

    @Test
    void deleteManifestFailureIsDurableSuccessAndRetainsUntilReconciliation() throws Exception {
        var images = images();
        var reference = images.importImage(ACTOR, png(0xff123456));
        var scope = scope("delete-image.example");
        seed(store(images), scope, request("main", "saved"), reference);
        ImageAttachmentStore failingImages = new DelegatingStore(images) {
            @Override
            public void reconcile(UUID actor, String namespace, Map<String, List<ImageReference>> owners)
                    throws IOException {
                if (namespace.equals("scope:" + scope.scopeId()) && owners.isEmpty()) {
                    throw new IOException("injected after durable delete");
                }
                super.reconcile(actor, namespace, owners);
            }
        };
        store(failingImages).delete(GuideHistoryDeleteScope.partition(scope));
        assertEquals(0, images.collect(ACTOR));
        assertTrue(store(images).metadata(scope).isEmpty());
        assertEquals(1, images.collect(ACTOR));
        assertThrows(IOException.class, () -> images.read(ACTOR, reference));
    }

    @Test
    void forkPinsOriginalImagesOutsideItsNewestDisplayWindowAndKeepsThemAfterAncestorDeletion()
            throws Exception {
        var images = images(); var reference = images.importImage(ACTOR, png(0xff456789));
        var scope = scope("older-than-window.example"); var store = store(images);
        List<GuideHistoryMutation> mutations = new java.util.ArrayList<>(List.of(
                new GuideHistoryMutation.UpsertPartition("main", NOW),
                new GuideHistoryMutation.UpsertSession("main", 0, GuideModelSelection.client("default"))));
        GuideRequestSnapshot first = null; GuideRequestSnapshot cutoff = null;
        for (int index = 0; index < 151; index++) {
            GuideRequestSnapshot request = request("main", "full prefix " + index);
            if (index == 0) first = request;
            cutoff = request;
            mutations.add(new GuideHistoryMutation.UpsertRequest(index, request));
            mutations.add(new GuideHistoryMutation.ReplaceRequestContext(request.requestId(), index == 0
                    ? messages(reference) : List.of(ModelMessage.userText("original " + index))));
        }
        mutations.add(new GuideHistoryMutation.ReplaceContext("main", List.of(ModelMessage.userText("compacted tail"))));
        mutations.add(new GuideHistoryMutation.CaptureRequestBoundary(cutoff.requestId(),
                List.of(ModelMessage.userText("safe cutoff")), List.of()));
        store.commit(new GuideHistoryCommit(scope, mutations));
        var branch = store.fork(new GuideHistoryForkRequest(scope, new GuideHistoryMutation.ForkSession("main",
                new GuideHistoryCursor(150, cutoff.requestId()), "branch", 1, GuideModelSelection.client("default"))));
        assertEquals(151, branch.session().requestCount());
        assertEquals(120, branch.page().requests().size());
        assertTrue(branch.page().hasEarlier());
        var prefix = store.page(new GuideHistoryPageRequest(scope, "branch",
                GuideHistoryPageRequest.Direction.BEFORE, branch.page().first(), 120));
        assertEquals(31, prefix.requests().size());
        var clonedFirst = prefix.requests().getFirst();
        assertNotEquals(first.requestId(), clonedFirst.requestId());
        store.commit(new GuideHistoryCommit(scope, List.of(new GuideHistoryMutation.UpsertPartition("branch", NOW),
                new GuideHistoryMutation.DeleteSession("main"))));
        assertEquals(0, images.collect(ACTOR)); images.read(ACTOR, reference);
        assertEquals(messages(reference), store(images).requestContext(scope, clonedFirst.requestId()));
        store.commit(new GuideHistoryCommit(scope, List.of(new GuideHistoryMutation.DeleteSession("branch"))));
        assertThrows(IOException.class, () -> images.read(ACTOR, reference));
    }

    @Test
    void boundaryOnlyImageSurvivesCompactionForkAndDeletionOfItsAncestor() throws Exception {
        var images = images();
        var reference = images.importImage(ACTOR, png(0xff123456));
        var scope = scope("boundary-only.example");
        var store = store(images);
        var request = request("main", "cutoff");
        seed(store, scope, request, reference);
        store.commit(new GuideHistoryCommit(scope, List.of(
                new GuideHistoryMutation.CaptureRequestBoundary(request.requestId(), messages(reference), List.of()),
                new GuideHistoryMutation.ReplaceContext("main", List.of(ModelMessage.userText("compacted"))),
                new GuideHistoryMutation.ReplaceRequestContext(request.requestId(),
                        List.of(ModelMessage.userText("original no image"))))));
        assertEquals(0, images.collect(ACTOR)); // The boundary is the only remaining image owner.
        var branch = store.fork(new GuideHistoryForkRequest(scope,
                new GuideHistoryMutation.ForkSession("main", new GuideHistoryCursor(0, request.requestId()),
                        "branch", 1, GuideModelSelection.client("default"))));
        assertEquals(messages(reference), branch.messages());
        store.commit(new GuideHistoryCommit(scope, List.of(
                new GuideHistoryMutation.UpsertPartition("branch", NOW),
                new GuideHistoryMutation.DeleteSession("main"))));
        assertEquals(0, images.collect(ACTOR));
        images.read(ACTOR, reference);
        // Prove copied request boundary ownership is independent of the fork's session context.
        store.commit(new GuideHistoryCommit(scope, List.of(new GuideHistoryMutation.ReplaceContext(
                "branch", List.of(ModelMessage.userText("branch compacted"))))));
        assertEquals(0, images.collect(ACTOR));
        store.commit(new GuideHistoryCommit(scope, List.of(new GuideHistoryMutation.DeleteSession("branch"))));
        assertThrows(IOException.class, () -> images.read(ACTOR, reference));
    }

    @Test
    void missingBoundaryOnlyAssetFailsBeforeWritingOtherSqlMutations() throws Exception {
        var images = images();
        var scope = scope("missing-boundary.example");
        var store = store(images);
        var request = request("main", "cutoff");
        store.commit(new GuideHistoryCommit(scope, List.of(
                new GuideHistoryMutation.UpsertPartition("main", NOW),
                new GuideHistoryMutation.UpsertSession("main", 0, GuideModelSelection.client("default")),
                new GuideHistoryMutation.UpsertRequest(0, request))));
        var missing = new ImageReference("b".repeat(64), "image/png", 4, 3, 100);
        assertThrows(GuideHistoryException.class, () -> store.commit(new GuideHistoryCommit(scope, List.of(
                new GuideHistoryMutation.UpsertPartition("must-not-write", NOW),
                new GuideHistoryMutation.CaptureRequestBoundary(request.requestId(), messages(missing), List.of())))));
        assertEquals("main", store.metadata(scope).orElseThrow().selectedSession());
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database());
                var query = connection.createStatement().executeQuery("select count(*) from request_context_boundaries")) {
            assertTrue(query.next());
            assertEquals(0, query.getInt(1));
        }
    }

    @Test
    void separateForkEntryPinsBeforeCommitAndRecoversPostCommitManifestFailureWithoutRetry()
            throws Exception {
        var images = images();
        var reference = images.importImage(ACTOR, png(0xff123456));
        var scope = scope("fork-postcommit.example");
        var request = request("main", "cutoff");
        var initial = store(images);
        seed(initial, scope, request, reference);
        initial.commit(new GuideHistoryCommit(scope, List.of(new GuideHistoryMutation.CaptureRequestBoundary(
                request.requestId(), messages(reference), List.of()))));
        var failPostCommit = new AtomicBoolean();
        var forkPins = new AtomicInteger();
        var manifestFailures = new AtomicInteger();
        var failures = new DelegatingStore(images) {
            @Override
            public void reconcile(UUID actor, String namespace, Map<String, List<ImageReference>> owners)
                    throws IOException {
                if (namespace.equals("scope-write:" + scope.scopeId()) && !owners.isEmpty()) {
                    forkPins.incrementAndGet();
                    assertEquals(List.of(reference), owners.get("pending").stream().distinct().toList());
                }
                if (namespace.equals("scope:" + scope.scopeId()) && failPostCommit.compareAndSet(true, false)) {
                    manifestFailures.incrementAndGet();
                    throw new IOException("injected fork durable manifest failure");
                }
                super.reconcile(actor, namespace, owners);
            }
        };
        var forkStore = new SqliteGuideHistoryStore(database(), clock(), new GuideHistoryCodec(), failures,
                mutation -> {
                    assertEquals(2, forkPins.get());
                    try { assertEquals(0, images.collect(ACTOR)); }
                    catch (IOException problem) { throw new SQLException(problem); }
                    failPostCommit.set(true);
                });
        var branch = forkStore.fork(new GuideHistoryForkRequest(scope,
                new GuideHistoryMutation.ForkSession("main", new GuideHistoryCursor(0, request.requestId()),
                        "branch", 1, GuideModelSelection.client("default"))));
        assertEquals("branch", branch.session().sessionId());
        assertEquals(2, forkPins.get());
        assertEquals(1, manifestFailures.get());
        assertEquals(0, images.collect(ACTOR));
        var reopened = store(images);
        assertEquals(2, reopened.metadata(scope).orElseThrow().sessions().size());
        reopened.commit(new GuideHistoryCommit(scope, List.of(
                new GuideHistoryMutation.UpsertPartition("branch", NOW),
                new GuideHistoryMutation.DeleteSession("main"))));
        assertEquals(0, images.collect(ACTOR));
        assertEquals(messages(reference), reopened.requestContext(scope,
                branch.page().requests().getFirst().requestId()));
        reopened.commit(new GuideHistoryCommit(scope, List.of(new GuideHistoryMutation.DeleteSession("branch"))));
        assertThrows(IOException.class, () -> images.read(ACTOR, reference));
    }

    @Test
    void freshCurrentLayoutLoadsPagesForksAndExportsFullOriginalsAfterReopen() throws Exception {
        var images = images();
        byte[] imageBytes = png(0xff2468ac);
        var reference = images.importImage(ACTOR, imageBytes);
        var scope = scope("fresh-current-contract.example");
        var writer = store(images);
        var usage = new ModelUsage(19, 0, 0, 5, 14,
                false, false, false, true, true);
        var bill = new GuideUsageSnapshot(19, 0, 0, 5,
                1, 1, true, true, new BigDecimal("0.001234"), true);
        var unknown = new GuideUsageSnapshot(0, 0, 0, 0,
                1, 0, true, true, null, true);
        var control = new GuideUsageSnapshot(10, 3, 4, 0,
                1, 1, false, false, new BigDecimal("0.000900"), false);
        var arguments = new com.google.gson.JsonObject();
        arguments.addProperty("source", "return 'quoted original \"value\"';");
        var outcome = new com.google.gson.JsonObject();
        outcome.addProperty("text", "original result with \"quotes\" and newlines\nkept");
        outcome.addProperty("value", 42);
        List<ModelMessage> original = List.of(
                new ModelMessage(ModelRole.USER, List.of(new ModelContent.Text("original question"),
                        new ModelContent.Image(reference))),
                new ModelMessage(ModelRole.ASSISTANT, List.of(new ModelContent.ToolUse(
                        "original-tool-id", "openallay:run_javascript", arguments))),
                new ModelMessage(ModelRole.USER, List.of(new ModelContent.ToolResult(
                        "original-tool-id", outcome, true))),
                new ModelMessage(ModelRole.ASSISTANT, List.of(new ModelContent.Text(
                        "complete original answer " + "tail ".repeat(100)))));
        var first = new GuideRequestSnapshot(UUID.randomUUID(), "main", GuideTopology.CLIENT_LOCAL,
                "original question", List.of(new dev.openallay.guide.GuideTimelineEntry.Assistant(
                        0, "display is not the original transcript", false, List.of())),
                GuideRequestStatus.COMPLETED, List.of(), usage, null, null, NOW, NOW, NOW,
                GuideModelSelection.client("default"),
                GuideRequestSnapshot.legacyProgress(GuideRequestStatus.COMPLETED, null, NOW, NOW), bill, null);
        var checkpoint = new dev.openallay.agent.context.ContextCheckpoint(UUID.randomUUID(),
                0, 3, dev.openallay.agent.context.ContextSourceHash.compute(
                        new com.google.gson.Gson(), original.subList(0, 3)), "test:model", NOW,
                dev.openallay.agent.context.ContextCheckpoint.Status.SUCCEEDED,
                "derived summary does not replace originals", null, null, 12);
        List<GuideHistoryMutation> mutations = new java.util.ArrayList<>(List.of(
                new GuideHistoryMutation.UpsertPartition("main", NOW),
                new GuideHistoryMutation.UpsertSession("main", 0, GuideModelSelection.client("default")),
                new GuideHistoryMutation.UpsertSessionUsage("main", control),
                new GuideHistoryMutation.UpsertRequest(0, first),
                new GuideHistoryMutation.UpsertTimelineEntry(first.requestId(), first.timeline().getFirst()),
                new GuideHistoryMutation.ReplaceRequestContext(first.requestId(), original),
                new GuideHistoryMutation.ReplaceContext("main", original),
                new GuideHistoryMutation.AppendCheckpoint("main", checkpoint),
                new GuideHistoryMutation.CaptureRequestBoundary(first.requestId(), original, List.of(checkpoint))));
        GuideRequestSnapshot last = first;
        for (int index = 1; index < 130; index++) {
            var value = request("main", "later question " + index);
            var projection = index == 1 ? unknown : GuideUsageSnapshot.empty();
            last = new GuideRequestSnapshot(value.requestId(), value.sessionId(), value.topology(),
                    value.userMessage(), value.timeline(), value.status(), value.sources(), value.usage(),
                    value.retryAfterMillis(), value.failure(), value.createdAt(), value.updatedAt(), value.terminalAt(),
                    value.modelSelection(), value.progress(), projection, null);
            mutations.add(new GuideHistoryMutation.UpsertRequest(index, last));
            mutations.add(new GuideHistoryMutation.ReplaceRequestContext(last.requestId(),
                    List.of(ModelMessage.userText(last.userMessage()))));
        }
        writer.commit(new GuideHistoryCommit(scope, mutations));
        writer.commit(new GuideHistoryCommit(scope, List.of(
                new GuideHistoryMutation.AppendCheckpoint("main", checkpoint))));
        var reopened = store(images);
        var metadata = reopened.metadata(scope).orElseThrow();
        assertEquals(130, metadata.sessions().getFirst().requestCount());
        assertEquals(bill.plus(unknown).plus(control), metadata.sessions().getFirst().usage());
        assertEquals(control, metadata.sessions().getFirst().controlUsage());
        var newest = reopened.page(new GuideHistoryPageRequest(scope, "main",
                GuideHistoryPageRequest.Direction.NEWEST, null, 5));
        assertEquals(125, newest.first().sequence());
        assertEquals(last.requestId(), newest.last().requestId());
        var earlier = reopened.page(new GuideHistoryPageRequest(scope, "main",
                GuideHistoryPageRequest.Direction.BEFORE, newest.first(), 128));
        assertEquals(125, earlier.requests().size());
        assertEquals(usage, earlier.requests().getFirst().usage());
        assertEquals(bill, earlier.requests().getFirst().usageProjection());
        assertEquals(unknown, earlier.requests().get(1).usageProjection());
        var context = reopened.context(new GuideHistoryContextRequest(scope, "main",
                new dev.openallay.agent.context.ContextBudget(240, 100), 1, "test:model"));
        assertEquals(original, context.messages(), "a small context budget must not truncate stored originals");
        assertEquals(List.of(checkpoint), context.checkpoints());
        assertEquals(original, reopened.requestContext(scope, first.requestId()));
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database());
                var query = connection.createStatement().executeQuery(
                        "select count(*) from compaction_checkpoints")) {
            assertTrue(query.next());
            assertEquals(1, query.getInt(1), "AppendCheckpoint must update a repeated identity, not append it again");
        }
        var branch = reopened.fork(new GuideHistoryForkRequest(scope,
                new GuideHistoryMutation.ForkSession("main", new GuideHistoryCursor(0, first.requestId()),
                        "branch", 1, GuideModelSelection.client("default"))));
        var inherited = branch.page().requests().getFirst();
        assertNotEquals(first.requestId(), inherited.requestId());
        assertEquals(first.requestId(), inherited.usageOriginRequestId());
        assertEquals(usage, inherited.usage());
        assertEquals(bill, branch.session().inheritedUsage());
        assertEquals(original, reopened.requestContext(scope, inherited.requestId()));
        assertEquals(original, branch.messages());
        var repository = new GuideHistoryRepository(reopened);
        try {
            var export = new dev.openallay.guide.export.GuideSessionExportCollector(scope, repository)
                    .collect("main", List.of(), Map.of(), 129, NOW).get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(130, export.requests().size(), "export must page beyond its 128-request batch");
            assertEquals(original, export.requests().getFirst().originalContext());
            String owner = "export:current-contract";
            images.retain(ACTOR, owner, List.of(reference));
            var closes = new AtomicInteger();
            export = export.withImagePayloadResolver(new dev.openallay.model.image.ImagePayloadResolver() {
                @Override public byte[] read(ImageReference value) throws IOException {
                    assertEquals(0, images.collect(ACTOR));
                    return images.read(ACTOR, value);
                }
                @Override public void close() {
                    try { images.release(ACTOR, owner); }
                    catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
                    closes.incrementAndGet();
                }
            });
            repository.commit(new GuideHistoryCommit(scope, List.of(
                    new GuideHistoryMutation.UpsertPartition("branch", NOW),
                    new GuideHistoryMutation.DeleteSession("main")))).get(5, java.util.concurrent.TimeUnit.SECONDS);
            var published = new dev.openallay.client.gui.export.GuideSessionExporter(temporary).export(export);
            Path exports = temporary.resolve("openallay/exports");
            String text = Files.readString(exports.resolve(published.filename()));
            assertEquals(130, published.requestCount());
            assertTrue(text.contains(arguments.toString()));
            assertTrue(text.contains(outcome.toString()));
            assertTrue(text.contains("Invocation ID: original-tool-id"));
            assertTrue(text.contains("=== Request 130"));
            assertTrue(text.contains("later question 129"));
            assertFalse(text.contains("display is not the original transcript"));
            assertTrue(text.contains("complete original answer " + "tail ".repeat(100)));
            assertEquals(1, closes.get());
            assertArrayEquals(imageBytes, Files.readAllBytes(exports.resolve("images/" + reference.sha256() + ".png")));
            assertEquals(0, images.collect(ACTOR), "the fork owns its inherited original image independently");
            repository.commit(new GuideHistoryCommit(scope, List.of(
                    new GuideHistoryMutation.UpsertPartition("branch", NOW),
                    new GuideHistoryMutation.ClearSession("branch")))).get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertThrows(IOException.class, () -> images.read(ACTOR, reference));
            assertArrayEquals(imageBytes, Files.readAllBytes(exports.resolve("images/" + reference.sha256() + ".png")));
        } finally {
            repository.closeAsync().get(5, java.util.concurrent.TimeUnit.SECONDS);
        }
    }

    @Test
    void nestedToolImageSurvivesReopenCompactionForkAndIndependentDeletion() throws Exception {
        var images = images();
        byte[] bytes = png(0xff102938);
        var reference = images.importImage(ACTOR, bytes);
        var scope = scope("nested.example");
        var request = request("main", "view question");
        var original = List.of(ModelMessage.userText("view question"),
                new ModelMessage(ModelRole.ASSISTANT, List.of(new ModelContent.ToolUse(
                        "view", "capture", new com.google.gson.JsonObject()))),
                new ModelMessage(ModelRole.USER, List.of(new ModelContent.ToolResult("view",
                        new com.google.gson.JsonPrimitive("view metadata"), false, List.of(reference)))),
                new ModelMessage(ModelRole.ASSISTANT, List.of(new ModelContent.Text("seen"))));
        store(images).commit(new GuideHistoryCommit(scope, List.of(
                new GuideHistoryMutation.UpsertPartition("main", NOW),
                new GuideHistoryMutation.UpsertSession("main", 0, GuideModelSelection.client("default")),
                new GuideHistoryMutation.UpsertRequest(0, request),
                new GuideHistoryMutation.ReplaceContext("main", original),
                new GuideHistoryMutation.ReplaceRequestContext(request.requestId(), original),
                new GuideHistoryMutation.CaptureRequestBoundary(request.requestId(), original, List.of()))));
        var reopened = store(images);
        assertEquals(original, reopened.requestContext(scope, request.requestId()));
        reopened.commit(new GuideHistoryCommit(scope, List.of(new GuideHistoryMutation.ReplaceContext(
                "main", List.of(ModelMessage.userText("compacted"))))));
        assertEquals(0, images.collect(ACTOR));
        var fork = reopened.fork(new GuideHistoryForkRequest(scope, new GuideHistoryMutation.ForkSession(
                "main", new GuideHistoryCursor(0, request.requestId()), "branch", 1, GuideModelSelection.client("default"))));
        reopened.commit(new GuideHistoryCommit(scope, List.of(new GuideHistoryMutation.UpsertPartition("branch", NOW),
                new GuideHistoryMutation.DeleteSession("main"))));
        assertArrayEquals(bytes, images.read(ACTOR, reference));
        assertEquals(0, images.collect(ACTOR));
        assertEquals(original, store(images).requestContext(scope, fork.page().requests().getFirst().requestId()));
        reopened.commit(new GuideHistoryCommit(scope, List.of(new GuideHistoryMutation.DeleteSession("branch"))));
        assertThrows(IOException.class, () -> images.read(ACTOR, reference));
    }


    private void seed(GuideHistoryStore store, GuideHistoryScope scope,
            GuideRequestSnapshot request, ImageReference reference) {
        store.commit(new GuideHistoryCommit(scope, List.of(
                new GuideHistoryMutation.UpsertPartition("main", NOW),
                new GuideHistoryMutation.UpsertSession("main", 0, GuideModelSelection.client("default")),
                new GuideHistoryMutation.UpsertRequest(0, request),
                new GuideHistoryMutation.ReplaceContext("main", messages(reference)),
                new GuideHistoryMutation.ReplaceRequestContext(request.requestId(), messages(reference)))));
    }

    private static GuideRequestSnapshot request(String session, String text) {
        return new GuideRequestSnapshot(UUID.nameUUIDFromBytes(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                session, GuideTopology.CLIENT_LOCAL, text, List.of(), GuideRequestStatus.COMPLETED,
                List.of(), ModelUsage.empty(), null, null, NOW, NOW, NOW);
    }

    private static List<ModelMessage> messages(ImageReference reference) {
        return List.of(new ModelMessage(ModelRole.USER,
                List.of(new ModelContent.Text("look"), new ModelContent.Image(reference))));
    }

    private static GuideHistoryScope scope(String server) {
        return GuideHistoryScope.derive(ACTOR, GuideHistoryScope.Kind.MULTIPLAYER, server);
    }

    private static byte[] png(int color) throws IOException {
        var image = new BufferedImage(4, 3, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) image.setRGB(x, y, color);
        }
        var output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        image.flush();
        return output.toByteArray();
    }

    private Path database() { return temporary.resolve("history.sqlite3"); }
    private Clock clock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
    private FileImageAttachmentStore images() { return new FileImageAttachmentStore(temporary.resolve("images")); }
    private SqliteGuideHistoryStore store(ImageAttachmentStore images) {
        return new SqliteGuideHistoryStore(database(), clock(), new GuideHistoryCodec(), images);
    }

    private static class DelegatingStore implements ImageAttachmentStore {
        private final ImageAttachmentStore delegate;
        DelegatingStore(ImageAttachmentStore delegate) { this.delegate = delegate; }
        public ImageInputLimits limits() { return delegate.limits(); }
        public ImageReference importImage(UUID actor, byte[] bytes) throws IOException {
            return delegate.importImage(actor, bytes);
        }
        public ImageReference importImage(UUID actor, String owner, byte[] bytes) throws IOException {
            return delegate.importImage(actor, owner, bytes);
        }
        public byte[] read(UUID actor, ImageReference ref) throws IOException { return delegate.read(actor, ref); }
        public void retain(UUID actor, String owner, List<ImageReference> refs) throws IOException {
            delegate.retain(actor, owner, refs);
        }
        public void release(UUID actor, String owner) throws IOException { delegate.release(actor, owner); }
        public int collect(UUID actor) throws IOException { return delegate.collect(actor); }
        public void reconcile(UUID actor, String namespace, Map<String, List<ImageReference>> owners)
                throws IOException { delegate.reconcile(actor, namespace, owners); }
    }
}
