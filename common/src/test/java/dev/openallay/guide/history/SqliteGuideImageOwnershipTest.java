package dev.openallay.guide.history;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
