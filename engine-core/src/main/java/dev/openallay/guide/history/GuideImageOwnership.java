package dev.openallay.guide.history;

import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.image.ImageAttachmentStore;
import dev.openallay.model.image.ImageReference;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Bridges SQLite durability and the separate atomic image owner manifest.
 * The database sibling lock serializes every image-enabled history operation, including
 * recovery and collection, across JVMs. Namespaced write pins survive process interruption.
 * Only the holder of this lock may replace or clear those pins. A failed post-commit
 * manifest update deliberately over-retains; the next operation reconciles SQLite truth
 * before clearing abandoned pins. Neither pinning nor reconciliation collects bytes.
 */
final class GuideImageOwnership {
    private static final ReentrantLock[] LOCKS = new ReentrantLock[64];
    static {
        for (int index = 0; index < LOCKS.length; index++) LOCKS[index] = new ReentrantLock();
    }

    private final Path database;
    private final ImageAttachmentStore images;

    GuideImageOwnership(Path database, ImageAttachmentStore images) {
        this.database = database;
        this.images = images;
    }

    boolean enabled() { return images != null; }

    Guard lock() {
        if (!enabled()) return new Guard(null, null, null);
        ReentrantLock local = null;
        FileChannel channel = null;
        try {
            Files.createDirectories(database.getParent());
            Path file = database.resolveSibling(database.getFileName() + ".images.lock");
            if (Files.isSymbolicLink(file)) throw new IOException("history image lock is a symbolic link");
            Path identity = file.getParent().toRealPath().resolve(file.getFileName());
            local = LOCKS[Math.floorMod(identity.hashCode(), LOCKS.length)];
            local.lock();
            channel = FileChannel.open(file, StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
            FileLock lock = channel.lock();
            return new Guard(local, channel, lock);
        } catch (IOException | RuntimeException failure) {
            if (channel != null) {
                try { channel.close(); } catch (IOException close) { failure.addSuppressed(close); }
            }
            if (local != null && local.isHeldByCurrentThread()) local.unlock();
            throw new GuideHistoryException("history_image_failed",
                    "Unable to lock durable guide image ownership", failure);
        }
    }

    static List<ImageReference> references(List<ModelMessage> messages) {
        return dev.openallay.model.image.ModelImages.uniqueReferences(messages);
    }

    static List<ImageReference> references(Map<String, List<ImageReference>> owners) {
        LinkedHashSet<ImageReference> refs = new LinkedHashSet<>();
        owners.values().forEach(refs::addAll);
        return dev.openallay.util.Java8Collections.listCopyOf(refs);
    }

    void pin(GuideHistoryScope scope, List<ImageReference> refs) throws IOException {
        if (!enabled()) {
            if (!refs.isEmpty()) throw new IOException("history image attachment store is unavailable");
            return;
        }
        images.reconcile(scope.actorId(), writeNamespace(scope.scopeId()),
                refs.isEmpty() ? dev.openallay.util.Java8Collections.mapOf() : dev.openallay.util.Java8Collections.mapOf("pending", dev.openallay.util.Java8Collections.listCopyOf(refs)));
    }

    void reconcile(GuideHistoryScope scope, Map<String, List<ImageReference>> owners)
            throws IOException {
        if (!enabled()) return;
        // The replacement verifies every asset before removing any existing history owners.
        images.reconcile(scope.actorId(), "scope:" + scope.scopeId(), owners);
        images.reconcile(scope.actorId(), writeNamespace(scope.scopeId()), dev.openallay.util.Java8Collections.mapOf());
    }

    void collect(UUID actor) throws IOException {
        if (enabled()) images.collect(actor);
    }

    private static String writeNamespace(String scopeId) { return "scope-write:" + scopeId; }

    static final class Guard implements AutoCloseable {
        private final ReentrantLock local;
        private final FileChannel channel;
        private final FileLock lock;

        Guard(ReentrantLock local, FileChannel channel, FileLock lock) {
            this.local = local;
            this.channel = channel;
            this.lock = lock;
        }

        @Override
        public void close() {
            // A lock-close error cannot turn a successful SQL commit into a retryable failure.
            try {
                if (lock != null) lock.close();
            } catch (IOException ignored) {
                // Closing the channel also releases its file lock.
            } finally {
                try {
                    if (channel != null) channel.close();
                } catch (IOException ignored) {
                    // The OS releases the remaining handle when this process exits.
                } finally {
                    if (local != null) local.unlock();
                }
            }
        }
    }
}
