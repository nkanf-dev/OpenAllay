package dev.openallay.model.image;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class FileImageAttachmentStoreTest {
    @TempDir Path temporary;
    private final UUID actor = UUID.fromString("70f785d0-5cd0-4b6d-9c19-7053b33e978f");

    @Test
    void importsActualPngAndJpegMetadataAndDeduplicatesByHash() throws Exception {
        FileImageAttachmentStore store = store();
        for (String format : List.of("png", "jpeg")) {
            byte[] bytes = image(format, 13, 7, 0xff2488ab);
            ImageReference reference = store.importImage(actor, bytes);
            assertEquals("image/" + format, reference.mimeType());
            assertEquals(13, reference.width());
            assertEquals(7, reference.height());
            assertEquals(bytes.length, reference.byteSize());
            assertEquals(hash(bytes), reference.sha256());
            assertArrayEquals(bytes, store.read(actor, reference));
            assertEquals(reference, store.importImage(actor, bytes));
            assertTrue(Files.isRegularFile(actorDirectory().resolve(reference.sha256())));
        }
        try (var paths = Files.list(actorDirectory())) {
            assertEquals(2, paths.filter(path -> path.getFileName().toString().matches("[a-f0-9]{64}")).count());
        }
    }

    @Test
    void rejectsUnknownFormatsEmptyDataAndTruncatedImagesBeforePublication() throws Exception {
        FileImageAttachmentStore store = store();
        byte[] png = image("png", 12, 7, 0xffab8c15);
        byte[] jpeg = image("jpeg", 12, 7, 0xffab8c15);
        for (byte[] bytes : List.of(new byte[0], new byte[] {1, 2, 3},
                image("gif", 12, 7, 0xffab8c15),
                Arrays.copyOf(png, 8), Arrays.copyOf(png, png.length / 2),
                Arrays.copyOf(jpeg, 4), Arrays.copyOf(jpeg, jpeg.length - 20))) {
            assertThrows(IOException.class, () -> store.importImage(actor, bytes));
        }
        assertFalse(Files.exists(actorDirectory()));
    }

    @Test
    void limitsBytesDimensionsAndPixelAllocationButAllowsLongThinImages() throws Exception {
        byte[] png = image("png", 10, 8, 0xff123456);
        FileImageAttachmentStore byteBound = new FileImageAttachmentStore(temporary.resolve("byte-bound"),
                new ImageInputLimits(png.length - 1, 8000, 1000));
        assertThrows(IOException.class, () -> byteBound.importImage(actor, png));
        FileImageAttachmentStore axisBound = new FileImageAttachmentStore(temporary.resolve("axis-bound"),
                new ImageInputLimits(1000000, 9, 1000));
        assertThrows(IOException.class, () -> axisBound.importImage(actor, png));
        FileImageAttachmentStore pixelBound = new FileImageAttachmentStore(temporary.resolve("pixel-bound"),
                new ImageInputLimits(1000000, 8000, 79));
        assertThrows(IOException.class, () -> pixelBound.importImage(actor, png));
        assertEquals(7999, store().importImage(actor, image("png", 7999, 1, 0xff123456)).width());
    }

    @Test
    void checksActorHashSizeMimeAndDimensionsOnEveryRead() throws Exception {
        FileImageAttachmentStore store = store();
        byte[] png = image("png", 10, 8, 0xff123456);
        ImageReference reference = store.importImage(actor, png);
        assertThrows(IOException.class, () -> store.read(UUID.randomUUID(), reference));
        assertThrows(IOException.class, () -> store.read(actor, new ImageReference(
                reference.sha256(), "image/jpeg", 10, 8, png.length)));
        assertThrows(IOException.class, () -> store.read(actor, new ImageReference(
                reference.sha256(), "image/png", 9, 8, png.length)));
        assertThrows(IOException.class, () -> store.read(actor, new ImageReference(
                reference.sha256(), "image/png", 10, 8, png.length + 1)));
        byte[] changed = png.clone();
        changed[changed.length - 1] ^= 1;
        Files.write(actorDirectory().resolve(reference.sha256()), changed);
        assertThrows(IOException.class, () -> store.read(actor, reference));
    }

    @Test
    void atomicImportAddsDurableOwnerBeforeCollectionAndPreservesEarlierImports() throws Exception {
        FileImageAttachmentStore store = store();
        ImageReference first = store.importImage(actor, "import:connection", image("png", 10, 8, 0xff123456));
        assertEquals(0, store().collect(actor));
        ImageReference second = store.importImage(actor, "import:connection", image("png", 10, 8, 0xffabcdef));
        assertEquals(0, store().collect(actor));
        assertEquals(first, store.importImage(actor, "import:connection", image("png", 10, 8, 0xff123456)));
        // Transfer to a durable draft/history before releasing the import receipt owner.
        store.retain(actor, "draft:one", List.of(first, second));
        store.release(actor, "import:connection");
        assertEquals(0, store().collect(actor));
        store.release(actor, "draft:one");
        assertEquals(2, store().collect(actor));
    }

    @Test
    void retainForkAndExportSnapshotSurviveReopenAndIndependentRelease() throws Exception {
        FileImageAttachmentStore store = store();
        ImageReference reference = store.importImage(actor, image("png", 10, 8, 0xff123456));
        store.retain(actor, "history:original", List.of(reference));
        store.retain(actor, "history:fork", List.of(reference));
        store.retain(actor, "export:snapshot", List.of(reference));
        store.release(actor, "history:original");
        store.release(actor, "history:original");
        FileImageAttachmentStore reopened = store();
        assertEquals(0, reopened.collect(actor));
        reopened.release(actor, "history:fork");
        assertEquals(0, reopened.collect(actor));
        reopened.read(actor, reference);
        reopened.release(actor, "export:snapshot");
        // Release never destroys bytes immediately, including those a snapshot may still use.
        reopened.read(actor, reference);
        assertEquals(1, reopened.collect(actor));
        assertEquals(0, reopened.collect(actor));
        assertThrows(IOException.class, () -> reopened.read(actor, reference));
    }

    @Test
    void retainReplacementIsAtomicAndChecksEveryReference() throws Exception {
        FileImageAttachmentStore store = store();
        ImageReference first = store.importImage(actor, image("png", 10, 8, 0xff123456));
        ImageReference second = store.importImage(actor, image("png", 10, 8, 0xffabcdef));
        store.retain(actor, "history", List.of(first, first));
        ImageReference falseMetadata = new ImageReference(second.sha256(), "image/jpeg",
                second.width(), second.height(), second.byteSize());
        assertThrows(IOException.class, () -> store.retain(actor, "history", List.of(second, falseMetadata)));
        assertEquals(1, store.collect(actor));
        store.read(actor, first);
        assertThrows(IOException.class, () -> store.read(actor, second));
        store.retain(actor, "history", List.of());
        assertEquals(1, store.collect(actor));
    }

    @Test
    void reconciliationPreservesOtherNamespacesAndDirectOwners() throws Exception {
        FileImageAttachmentStore store = store();
        ImageReference original = store.importImage(actor, image("png", 10, 8, 0xff123456));
        ImageReference fork = store.importImage(actor, image("png", 10, 8, 0xffabcdef));
        ImageReference exported = store.importImage(actor, image("jpeg", 10, 8, 0xffab7315));
        store.reconcile(actor, "scope:original", Map.of("request:one", List.of(original)));
        store.reconcile(actor, "scope:fork", Map.of("request:one", List.of(original, fork)));
        store.retain(actor, "export:snapshot", List.of(exported));
        store.reconcile(actor, "scope:original", Map.of());
        assertEquals(0, store.collect(actor));
        FileImageAttachmentStore reopened = store();
        reopened.reconcile(actor, "scope:fork", Map.of("request:two", List.of(fork)));
        assertEquals(1, reopened.collect(actor));
        reopened.read(actor, fork);
        reopened.read(actor, exported);
        ImageReference falseMetadata = new ImageReference(fork.sha256(), "image/png", 1, 1, fork.byteSize());
        assertThrows(IOException.class, () -> reopened.reconcile(
                actor, "scope:fork", Map.of("request:bad", List.of(falseMetadata))));
        assertEquals(0, reopened.collect(actor));
        reopened.reconcile(actor, "scope:fork", Map.of());
        assertEquals(1, reopened.collect(actor));
        reopened.read(actor, exported);
    }

    @Test
    void ownerIdentifiersAreNotPathsAndForeignFilesAreNeverCollected() throws Exception {
        FileImageAttachmentStore store = store();
        ImageReference reference = store.importImage(actor, image("png", 10, 8, 0xff123456));
        store.retain(actor, "../../arbitrary/path", List.of(reference));
        Path exported = actorDirectory().resolve("player-export.png");
        Files.writeString(exported, "not an artifact");
        Path nested = actorDirectory().resolve("exports");
        Files.createDirectory(nested);
        Files.writeString(nested.resolve(reference.sha256()), "not an artifact");
        assertEquals(0, store.collect(actor));
        store.release(actor, "../../arbitrary/path");
        assertEquals(1, store.collect(actor));
        assertEquals("not an artifact", Files.readString(exported));
        assertEquals("not an artifact", Files.readString(nested.resolve(reference.sha256())));
        assertFalse(Files.exists(temporary.resolve("arbitrary")));
    }

    @Test
    void rejectsRootActorImageOwnerAndLockSymlinksWithoutFollowingThem() throws Exception {
        Path outside = temporary.resolve("outside");
        Files.createDirectory(outside);
        Path rootLink = temporary.resolve("root-link");
        Files.createSymbolicLink(rootLink, outside);
        assertThrows(IOException.class, () -> new FileImageAttachmentStore(rootLink)
                .importImage(actor, image("png", 10, 8, 0xff123456)));

        FileImageAttachmentStore store = store();
        ImageReference reference = store.importImage(actor, image("png", 10, 8, 0xff123456));
        Path file = actorDirectory().resolve(reference.sha256());
        byte[] original = Files.readAllBytes(file);
        Path externalImage = outside.resolve("external.png");
        Files.write(externalImage, original);
        Files.delete(file);
        Files.createSymbolicLink(file, externalImage);
        assertThrows(IOException.class, () -> store.read(actor, reference));
        assertThrows(IOException.class, () -> store.collect(actor));
        assertArrayEquals(original, Files.readAllBytes(externalImage));
        Files.delete(file);
        Files.write(file, original);

        Path externalOwners = outside.resolve("owners");
        Files.writeString(externalOwners, "must not change");
        Path owners = actorDirectory().resolve(".owners");
        Files.createSymbolicLink(owners, externalOwners);
        assertThrows(IOException.class, () -> store.retain(actor, "history", List.of(reference)));
        assertThrows(IOException.class, () -> store.release(actor, "history"));
        assertThrows(IOException.class, () -> store.collect(actor));
        assertEquals("must not change", Files.readString(externalOwners));
        Files.delete(owners);

        Path lock = actorDirectory().resolve(".lock");
        Files.delete(lock);
        Files.createSymbolicLink(lock, externalOwners);
        assertThrows(IOException.class, () -> store.read(actor, reference));
        Files.delete(lock);
        Files.delete(file);
        Files.delete(actorDirectory());
        Files.createSymbolicLink(actorDirectory(), outside);
        assertThrows(IOException.class, () -> store.importImage(actor, original));
        assertEquals("must not change", Files.readString(externalOwners));
    }

    @Test
    void corruptedOwnerManifestFailsCollectionClosed() throws Exception {
        FileImageAttachmentStore store = store();
        ImageReference reference = store.importImage(actor, image("png", 10, 8, 0xff123456));
        store.retain(actor, "history", List.of(reference));
        Files.writeString(actorDirectory().resolve(".owners"), "incomplete or corrupt manifest");
        assertThrows(IOException.class, () -> store.collect(actor));
        store.read(actor, reference);
    }

    @Test
    void concurrentStoreInstancesDoNotLoseDurableOwners() throws Exception {
        FileImageAttachmentStore store = store();
        ImageReference reference = store.importImage(actor, image("png", 10, 8, 0xff123456));
        List<Callable<Void>> operations = java.util.stream.IntStream.range(0, 24)
                .mapToObj(index -> (Callable<Void>) () -> {
                    store().retain(actor, "history:" + index, List.of(reference));
                    return null;
                }).toList();
        try (var executor = Executors.newFixedThreadPool(8)) {
            for (var result : executor.invokeAll(operations)) result.get();
        }
        for (int index = 0; index < 23; index++) store.release(actor, "history:" + index);
        assertEquals(0, store.collect(actor));
        store.release(actor, "history:23");
        assertEquals(1, store.collect(actor));
    }

    private FileImageAttachmentStore store() {
        return new FileImageAttachmentStore(temporary.resolve("managed"));
    }

    private Path actorDirectory() {
        return temporary.resolve("managed").resolve(actor.toString());
    }

    private static byte[] image(String format, int width, int height, int color) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) image.setRGB(x, y, color);
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, format, output)) throw new IOException("test image writer is unavailable");
            return output.toByteArray();
        } finally {
            image.flush();
        }
    }

    private static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
