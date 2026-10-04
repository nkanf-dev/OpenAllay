package dev.openallay.client.gui.export;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.openallay.guide.GuideFailure;
import dev.openallay.guide.GuideRequestStatus;
import dev.openallay.guide.GuideToolStatus;
import dev.openallay.guide.export.GuideSessionExportSnapshot;
import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelRole;
import dev.openallay.model.image.FileImageAttachmentStore;
import dev.openallay.model.image.ImageReference;
import dev.openallay.model.image.ImagePayloadResolver;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class GuideSessionExporterTest {
    private static final Instant NOW = Instant.parse("2026-07-19T12:34:56.789Z");

    @Test
    void associatedOnlyImageExportPublishesExactAnchorMetadataAndPermanentBytes(@TempDir Path game) throws Exception {
        byte[] bytes = new byte[] {1, 2, 3, 4};
        var reference = new ImageReference(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)),
                "image/png", 1, 1, bytes.length);
        var anchor = dev.openallay.world.InputObservationFixtures.anchor(reference);
        var input = ModelMessage.userText("which item").withInputObservation(anchor);
        var request = new GuideSessionExportSnapshot.Request(UUID.randomUUID(), NOW,
                GuideRequestStatus.COMPLETED, "which item", List.of(), List.of(input), null);
        var snapshot = new GuideSessionExportSnapshot("main", List.of(request), NOW)
                .withImagePayloadResolver(value -> { assertEquals(reference, value); return bytes; });
        var file = new GuideSessionExporter(game).export(snapshot);
        Path exports = game.resolve("openallay/exports");
        String text = Files.readString(exports.resolve(file.filename()));
        assertTrue(text.contains("Associated input source · IMAGE"));
        assertTrue(text.contains(anchor.associationId().toString()));
        assertTrue(text.contains(anchor.capturedAt().toString()));
        assertTrue(text.contains(anchor.focus().actorId().toString()));
        assertTrue(text.contains("9007199254740993.125"));
        assertArrayEquals(bytes, Files.readAllBytes(exports.resolve("images/" + reference.sha256() + ".png")));
    }

    @Test
    void writesChronologicalPlayerTextUnchangedUnderTheFixedManagedRoot(@TempDir Path game)
            throws Exception {
        GuideSessionExporter exporter = new GuideSessionExporter(game);
        GuideSessionExportSnapshot snapshot = snapshot(
                "API key: sk-" + "abcdefghijklmnopqrstuvwxyz\n\"apiKey\":\"opaque-provider-value\"",
                "authorization: Bearer " + "highly-sensitive-token");

        GuideSessionExporter.ExportedFile exported = exporter.export(snapshot);
        Path file = game.resolve("openallay/exports").resolve(exported.filename());
        String text = Files.readString(file);

        assertTrue(exported.filename().matches(
                "main-20260719-123456-789-[a-f0-9]{12}\\.txt"));
        assertEquals(1, exported.requestCount());
        assertTrue(text.indexOf("User") < text.indexOf("Assistant"));
        assertTrue(text.indexOf("Assistant") < text.indexOf("Tool · get_recipe"));
        assertTrue(text.contains("API key: sk-" + "abcdefghijklmnopqrstuvwxyz"));
        assertTrue(text.contains("\"apiKey\":\"opaque-provider-value\""));
        assertTrue(text.contains("authorization: Bearer " + "highly-sensitive-token"));
        assertFalse(text.contains("normalizedSecret"));
        assertEquals(1, Files.list(game.resolve("openallay/exports")).count());
    }

    @Test
    void rejectsAServiceDirectorySymlinkWithoutWritingOutside(@TempDir Path game)
            throws Exception {
        Path outside = Files.createDirectory(game.resolve("outside"));
        try {
            Files.createSymbolicLink(game.resolve("openallay"), outside);
        } catch (UnsupportedOperationException exception) {
            return;
        }

        assertThrows(GuideSessionExportException.class,
                () -> new GuideSessionExporter(game).export(snapshot("hello", "answer")));
        assertEquals(0, Files.list(outside).count());
    }

    @Test
    void failedManagedRootCreationPublishesNoFile(@TempDir Path game) throws Exception {
        Files.writeString(game.resolve("openallay"), "not a directory");

        assertThrows(GuideSessionExportException.class,
                () -> new GuideSessionExporter(game).export(snapshot("hello", "answer")));
        assertFalse(Files.exists(game.resolve("openallay/exports")));
    }

    @Test
    void marksCancelledPartialResponsesWithoutChangingCompletedExports() {
        GuideSessionExportSnapshot.Request completed = new GuideSessionExportSnapshot.Request(
                UUID.randomUUID(), NOW,
                GuideRequestStatus.COMPLETED,
                "complete",
                List.of(new GuideSessionExportSnapshot.Entry.Assistant("finished", false)),
                List.of(), null);
        GuideSessionExportSnapshot.Request cancelled = new GuideSessionExportSnapshot.Request(
                UUID.randomUUID(), NOW.plusSeconds(1),
                GuideRequestStatus.CANCELLED,
                "cancelled",
                List.of(new GuideSessionExportSnapshot.Entry.Assistant("partial ans", false)),
                List.of(), null);

        String text = GuideSessionExporter.format(new GuideSessionExportSnapshot(
                "main", List.of(completed, cancelled), NOW.plusSeconds(2)));

        assertEquals(1, text.split(
                "\\[This request ended before the response completed\\.]", -1).length - 1);
        assertTrue(text.indexOf("finished") < text.indexOf("=== Request 2"));
        assertTrue(text.indexOf("partial ans")
                < text.indexOf("[This request ended before the response completed.]"));
    }

    @Test
    void exportsOriginalCallsAndErrorsWithRequestFailureAfterCompaction() {
        UUID requestId = UUID.randomUUID();
        JsonObject input = new JsonObject();
        input.addProperty("source", "return mc.items.missing();");
        input.addProperty("apiKey", "opaque-secret-in-args");
        List<ModelMessage> original = List.of(
                new ModelMessage(ModelRole.ASSISTANT, List.of(
                        new ModelContent.ToolUse("failed-call", "openallay:run_javascript", input))),
                new ModelMessage(ModelRole.USER, List.of(new ModelContent.ToolResult(
                        "failed-call", new JsonPrimitive("status: failure\ncode: javascript_error\n"
                                + "message: TypeError at script line 1; token=opaque-secret-in-error"), true))));
        GuideSessionExportSnapshot.Request request = new GuideSessionExportSnapshot.Request(
                requestId, NOW, GuideRequestStatus.FAILED, "check the error",
                List.of(new GuideSessionExportSnapshot.Entry.Tool(
                        "failed-call", "openallay:run_javascript", GuideToolStatus.FAILED)),
                original, new GuideFailure("provider_unavailable", "provider did not respond"));

        String text = GuideSessionExporter.format(new GuideSessionExportSnapshot(
                "main", List.of(request), NOW));

        assertTrue(text.contains(requestId.toString()));
        assertTrue(text.contains("Invocation ID: failed-call"));
        assertTrue(text.contains("return mc.items.missing();"));
        assertTrue(text.contains("javascript_error"));
        assertTrue(text.contains("TypeError at script line 1"));
        assertTrue(text.contains("provider_unavailable"));
        assertTrue(text.contains("provider did not respond"));
        assertTrue(text.contains("\"apiKey\":\"opaque-secret-in-args\""));
        assertTrue(text.contains("token=opaque-secret-in-error"));
        assertFalse(text.contains("[REDACTED]"));
        assertTrue(text.contains("Workspace handles do not survive"));
    }

    @Test
    void originalExchangeOrderSurvivesAbsentOrDifferentDisplayCards() {
        JsonObject input = new JsonObject();
        input.addProperty("source", "return 42;");
        List<ModelMessage> original = List.of(
                ModelMessage.userText("question"),
                new ModelMessage(ModelRole.ASSISTANT, List.of(new ModelContent.Text("before tool"),
                        new ModelContent.ToolUse("call-42", "openallay:run_javascript", input))),
                new ModelMessage(ModelRole.USER, List.of(new ModelContent.ToolResult(
                        "call-42", new JsonPrimitive("actual answer 42"), false))),
                new ModelMessage(ModelRole.ASSISTANT, List.of(new ModelContent.Text("after tool"))));
        GuideSessionExportSnapshot.Request request = new GuideSessionExportSnapshot.Request(
                UUID.randomUUID(), NOW, GuideRequestStatus.COMPLETED, "question", List.of(), original, null);
        String text = GuideSessionExporter.format(new GuideSessionExportSnapshot("main", List.of(request), NOW));
        assertTrue(text.indexOf("before tool") < text.indexOf("SUBMITTED"));
        assertTrue(text.indexOf("SUBMITTED") < text.indexOf("actual answer 42"));
        assertTrue(text.indexOf("actual answer 42") < text.indexOf("after tool"));
        assertEquals(1, text.split("question", -1).length - 1);
    }

    @Test
    void mergedDisplayTextIsNotAppendedAfterTheOriginalToolExchange() {
        JsonObject input = new JsonObject();
        input.addProperty("source", "return 42;");
        List<ModelMessage> original = List.of(new ModelMessage(ModelRole.ASSISTANT, List.of(
                new ModelContent.Text("first-segment"), new ModelContent.Text("second-segment"),
                new ModelContent.ToolUse("call-42", "openallay:run_javascript", input))),
                new ModelMessage(ModelRole.USER, List.of(new ModelContent.ToolResult(
                        "call-42", new JsonPrimitive("actual answer 42"), false))));
        var request = new GuideSessionExportSnapshot.Request(UUID.randomUUID(), NOW,
                GuideRequestStatus.COMPLETED, "question",
                List.of(new GuideSessionExportSnapshot.Entry.Assistant(
                        "first-segmentsecond-segment", false)), original, null);
        String text = GuideSessionExporter.format(new GuideSessionExportSnapshot("main", List.of(request), NOW));
        assertEquals(1, text.split("first-segment", -1).length - 1);
        assertEquals(1, text.split("second-segment", -1).length - 1);
        assertFalse(text.contains("first-segmentsecond-segment"));
        assertTrue(text.indexOf("second-segment") < text.indexOf("actual answer 42"));
    }

    @Test
    void unfinishedCallsAreNotExportedAsSuccessfulResults() {
        GuideSessionExportSnapshot.Request request = new GuideSessionExportSnapshot.Request(
                UUID.randomUUID(), NOW, GuideRequestStatus.TOOL_WAIT, "still working",
                List.of(new GuideSessionExportSnapshot.Entry.Tool(
                        "pending-call", "openallay:run_javascript", GuideToolStatus.RUNNING)),
                List.of(), null);
        String text = GuideSessionExporter.format(new GuideSessionExportSnapshot(
                "main", List.of(request), NOW));
        assertTrue(text.contains("Invocation ID: pending-call"));
        assertTrue(text.contains("No completed model-visible result was recorded"));
        assertFalse(text.contains("Result (model-visible)"));
    }

    @Test
    void reasoningCannotEnterExportProjection() {
        assertThrows(IllegalArgumentException.class, () -> new GuideSessionExportSnapshot.Request(
                UUID.randomUUID(), NOW, GuideRequestStatus.COMPLETED, "question", List.of(),
                List.of(new ModelMessage(ModelRole.ASSISTANT,
                        List.of(new ModelContent.Reasoning("private reasoning", null)))), null));
    }

    @Test
    void exportsUniqueVerifiedImagesPermanentlyWithMetadataOnlyText(@TempDir Path game)
            throws Exception {
        UUID actor = UUID.randomUUID();
        FileImageAttachmentStore images = new FileImageAttachmentStore(game.resolve("managed-images"));
        byte[] bytes = png(0xff2367ab);
        ImageReference reference = images.importImage(actor, bytes);
        AtomicInteger reads = new AtomicInteger();
        var snapshot = imageSnapshot(List.of(reference, reference)).withImagePayloadResolver(ref -> {
            reads.incrementAndGet();
            return images.read(actor, ref);
        });

        var exported = new GuideSessionExporter(game).export(snapshot);
        Path root = game.resolve("openallay/exports");
        Path image = root.resolve("images").resolve(reference.sha256() + ".png");
        String text = Files.readString(root.resolve(exported.filename()));
        assertEquals(2, reads.get()); // One unique asset in each bounded-memory pass.
        assertArrayEquals(bytes, Files.readAllBytes(image));
        assertTrue(text.contains("IMAGE\nMIME: image/png\nDimensions: 4x3\nBytes: " + bytes.length));
        assertTrue(text.contains("SHA-256: " + reference.sha256()));
        assertTrue(text.contains("File: images/" + reference.sha256() + ".png"));
        assertFalse(text.contains(java.util.Base64.getEncoder().encodeToString(bytes)));
        assertEquals(1, images.collect(actor));
        assertArrayEquals(bytes, Files.readAllBytes(image)); // Exported copies are never managed GC targets.
    }

    @Test
    void imageExportWithoutScopedResolverFailsBeforeCreatingFiles(@TempDir Path game)
            throws Exception {
        UUID actor = UUID.randomUUID();
        var images = new FileImageAttachmentStore(game.resolve("managed-images"));
        var reference = images.importImage(actor, png(0xffabcdef));

        assertThrows(GuideSessionExportException.class,
                () -> new GuideSessionExporter(game).export(imageSnapshot(List.of(reference))));
        assertFalse(Files.exists(game.resolve("openallay")));
    }

    @Test
    void missingLaterImageFailsAllAssetPreflightWithoutPublishingEarlierImages(@TempDir Path game)
            throws Exception {
        UUID actor = UUID.randomUUID();
        var images = new FileImageAttachmentStore(game.resolve("managed-images"));
        var first = images.importImage(actor, png(0xff123456));
        var second = images.importImage(actor, png(0xffabcdef));
        Files.delete(game.resolve("managed-images").resolve(actor.toString()).resolve(second.sha256()));
        var snapshot = imageSnapshot(List.of(first, second))
                .withImagePayloadResolver(ref -> images.read(actor, ref));

        assertThrows(GuideSessionExportException.class,
                () -> new GuideSessionExporter(game).export(snapshot));
        assertFalse(Files.exists(game.resolve("openallay")));
    }

    @Test
    void wrongActorAndCorruptPayloadFailBeforePublication(@TempDir Path game) throws Exception {
        UUID actor = UUID.randomUUID();
        var images = new FileImageAttachmentStore(game.resolve("managed-images"));
        var bytes = png(0xff123456);
        var reference = images.importImage(actor, bytes);
        var wrongActor = imageSnapshot(List.of(reference))
                .withImagePayloadResolver(ref -> images.read(UUID.randomUUID(), ref));
        assertThrows(GuideSessionExportException.class,
                () -> new GuideSessionExporter(game).export(wrongActor));
        var corrupt = imageSnapshot(List.of(reference)).withImagePayloadResolver(ref -> new byte[] {1});
        assertThrows(GuideSessionExportException.class,
                () -> new GuideSessionExporter(game).export(corrupt));
        assertFalse(Files.exists(game.resolve("openallay")));
    }

    @Test
    void rejectsImageDirectorySymlinkWithoutPublishingText(@TempDir Path game) throws Exception {
        UUID actor = UUID.randomUUID();
        var images = new FileImageAttachmentStore(game.resolve("managed-images"));
        var reference = images.importImage(actor, png(0xff123456));
        Path root = Files.createDirectories(game.resolve("openallay/exports"));
        Path outside = Files.createDirectory(game.resolve("outside"));
        try {
            Files.createSymbolicLink(root.resolve("images"), outside);
        } catch (UnsupportedOperationException unsupported) {
            return;
        }
        var snapshot = imageSnapshot(List.of(reference))
                .withImagePayloadResolver(ref -> images.read(actor, ref));
        assertThrows(GuideSessionExportException.class,
                () -> new GuideSessionExporter(game).export(snapshot));
        try (var paths = Files.list(outside)) { assertEquals(0, paths.count()); }
        try (var paths = Files.list(root)) { assertEquals(1, paths.count()); }
    }

    @Test
    void closesSnapshotLeaseAfterSuccessfulAndFailedImageExports(@TempDir Path game)
            throws Exception {
        UUID actor = UUID.randomUUID();
        var images = new FileImageAttachmentStore(game.resolve("managed-images"));
        var reference = images.importImage(actor, png(0xff123456));
        AtomicInteger closes = new AtomicInteger();
        var resolver = new dev.openallay.model.image.ImagePayloadResolver() {
            @Override public byte[] read(ImageReference ref) throws IOException {
                return images.read(actor, ref);
            }
            @Override public void close() { closes.incrementAndGet(); }
        };
        new GuideSessionExporter(game).export(imageSnapshot(List.of(reference))
                .withImagePayloadResolver(resolver));
        assertEquals(1, closes.get());
        var failedResolver = new dev.openallay.model.image.ImagePayloadResolver() {
            @Override public byte[] read(ImageReference ref) throws IOException {
                throw new IOException("injected missing image");
            }
            @Override public void close() { closes.incrementAndGet(); }
        };
        assertThrows(GuideSessionExportException.class,
                () -> new GuideSessionExporter(game).export(imageSnapshot(List.of(reference))
                        .withImagePayloadResolver(failedResolver)));
        assertEquals(2, closes.get());
    }

    @Test
    void retainedSnapshotLeaseKeepsManagedBytesUntilPermanentCopiesExist(@TempDir Path game)
            throws Exception {
        UUID actor = UUID.randomUUID();
        var images = new FileImageAttachmentStore(game.resolve("managed-images"));
        byte[] bytes = png(0xff123456);
        String owner = "export:" + UUID.randomUUID();
        var reference = images.importImage(actor, owner, bytes);
        AtomicInteger reads = new AtomicInteger();
        AtomicInteger closes = new AtomicInteger();
        ImagePayloadResolver lease = new ImagePayloadResolver() {
            @Override public byte[] read(ImageReference ref) throws IOException {
                // Collection can run between the export's preflight and publication passes.
                assertEquals(0, images.collect(actor));
                reads.incrementAndGet();
                return images.read(actor, ref);
            }
            @Override public void close() {
                try { images.release(actor, owner); }
                catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
                closes.incrementAndGet();
            }
        };
        var snapshot = imageSnapshot(List.of(reference)).withImagePayloadResolver(lease);
        var exported = new GuideSessionExporter(game).export(snapshot);
        assertEquals(2, reads.get());
        assertEquals(1, closes.get());
        assertEquals(1, images.collect(actor));
        Path root = game.resolve("openallay/exports");
        assertArrayEquals(bytes, Files.readAllBytes(root.resolve("images/" + reference.sha256() + ".png")));
        assertTrue(Files.readString(root.resolve(exported.filename()))
                .contains("File: images/" + reference.sha256() + ".png"));
    }

    @Test
    void unusedSnapshotCanReleaseItsLeaseWithoutWritingFiles(@TempDir Path game) throws Exception {
        UUID actor = UUID.randomUUID();
        var images = new FileImageAttachmentStore(game.resolve("managed-images"));
        String owner = "export:" + UUID.randomUUID();
        var reference = images.importImage(actor, owner, png(0xff123456));
        ImagePayloadResolver lease = new ImagePayloadResolver() {
            @Override public byte[] read(ImageReference ref) throws IOException {
                return images.read(actor, ref);
            }
            @Override public void close() {
                try { images.release(actor, owner); }
                catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
            }
        };
        var snapshot = imageSnapshot(List.of(reference)).withImagePayloadResolver(lease);
        assertEquals(0, images.collect(actor));
        snapshot.close();
        assertEquals(1, images.collect(actor));
        assertFalse(Files.exists(game.resolve("openallay")));
    }

    @Test
    void leaseReleaseFailureDoesNotReportFalseFailureAfterPublication(@TempDir Path game)
            throws Exception {
        UUID actor = UUID.randomUUID();
        var images = new FileImageAttachmentStore(game.resolve("managed-images"));
        var reference = images.importImage(actor, png(0xff123456));
        var resolver = new ImagePayloadResolver() {
            @Override public byte[] read(ImageReference ref) throws IOException {
                return images.read(actor, ref);
            }
            @Override public void close() { throw new IllegalStateException("injected lease release failure"); }
        };
        var exported = new GuideSessionExporter(game).export(imageSnapshot(List.of(reference))
                .withImagePayloadResolver(resolver));
        assertTrue(Files.isRegularFile(game.resolve("openallay/exports").resolve(exported.filename())));
        assertArrayEquals(png(0xff123456), Files.readAllBytes(game.resolve("openallay/exports/images")
                .resolve(reference.sha256() + ".png")));
    }

    @Test
    void repeatedHashWithDifferentMetadataFailsBeforeFilesAndClosesLease(@TempDir Path game)
            throws Exception {
        UUID actor = UUID.randomUUID();
        var images = new FileImageAttachmentStore(game.resolve("managed-images"));
        var reference = images.importImage(actor, png(0xff123456));
        var different = new ImageReference(reference.sha256(), reference.mimeType(),
                reference.width() + 1, reference.height(), reference.byteSize());
        AtomicInteger reads = new AtomicInteger();
        AtomicInteger closes = new AtomicInteger();
        var resolver = new ImagePayloadResolver() {
            @Override public byte[] read(ImageReference ref) throws IOException {
                reads.incrementAndGet();
                return images.read(actor, ref);
            }
            @Override public void close() { closes.incrementAndGet(); }
        };
        assertThrows(GuideSessionExportException.class, () -> new GuideSessionExporter(game)
                .export(imageSnapshot(List.of(reference, different)).withImagePayloadResolver(resolver)));
        assertEquals(0, reads.get());
        assertEquals(1, closes.get());
        assertFalse(Files.exists(game.resolve("openallay")));
    }

    @Test
    void nestedToolImagesPublishVerifiedAssetsUnderTheirToolIdentity(@TempDir Path game) throws Exception {
        UUID actor = UUID.randomUUID();
        var images = new FileImageAttachmentStore(game.resolve("managed-images"));
        byte[] bytes = png(0xff123abc);
        var reference = images.importImage(actor, "export:nested", bytes);
        var original = List.of(ModelMessage.userText("look"),
                new ModelMessage(ModelRole.ASSISTANT, List.of(new ModelContent.ToolUse(
                        "native-view", "capture", new JsonObject()))),
                new ModelMessage(ModelRole.USER, List.of(new ModelContent.ToolResult("native-view",
                        new com.google.gson.JsonPrimitive("captured"), false, List.of(reference, reference)))));
        var snapshot = new GuideSessionExportSnapshot("main", List.of(new GuideSessionExportSnapshot.Request(
                UUID.randomUUID(), NOW, GuideRequestStatus.COMPLETED, "look", List.of(), original, null)), NOW)
                .withImagePayloadResolver(ref -> images.read(actor, ref));
        var output = new GuideSessionExporter(game).export(snapshot);
        Path root = game.resolve("openallay/exports");
        String text = Files.readString(root.resolve(output.filename()));
        assertTrue(text.contains("Tool observation · native-view · IMAGE"));
        assertTrue(text.contains("File: images/" + reference.sha256() + ".png"));
        assertFalse(text.contains(java.util.Base64.getEncoder().encodeToString(bytes)));
        assertArrayEquals(bytes, Files.readAllBytes(root.resolve("images/" + reference.sha256() + ".png")));
        try (var assets = Files.list(root.resolve("images"))) { assertEquals(1, assets.count()); }
    }


    @Test
    void carriedToolImageExportKeepsItsOriginalToolAssociation(@TempDir Path game) throws Exception {
        UUID actor = UUID.randomUUID();
        var images = new FileImageAttachmentStore(game.resolve("managed-images"));
        byte[] bytes = png(0xff234abc);
        var reference = images.importImage(actor, "export:carried", bytes);
        var summary = new ModelMessage(ModelRole.USER, List.of(new ModelContent.Text("derived memory"),
                new ModelContent.Image(reference, "original-view")));
        var snapshot = new GuideSessionExportSnapshot("main", List.of(new GuideSessionExportSnapshot.Request(
                UUID.randomUUID(), NOW, GuideRequestStatus.COMPLETED, "look", List.of(), List.of(summary), null)), NOW)
                .withImagePayloadResolver(ref -> images.read(actor, ref));
        var output = new GuideSessionExporter(game).export(snapshot);
        String text = Files.readString(game.resolve("openallay/exports").resolve(output.filename()));
        assertTrue(text.contains("Tool observation · original-view · IMAGE"));
        assertArrayEquals(bytes, Files.readAllBytes(game.resolve("openallay/exports/images")
                .resolve(reference.sha256() + ".png")));
    }


    private static GuideSessionExportSnapshot imageSnapshot(List<ImageReference> references) {
        List<ModelContent> content = new java.util.ArrayList<>();
        content.add(new ModelContent.Text("look"));
        references.forEach(reference -> content.add(new ModelContent.Image(reference)));
        return new GuideSessionExportSnapshot("main", List.of(new GuideSessionExportSnapshot.Request(
                UUID.randomUUID(), NOW, GuideRequestStatus.COMPLETED, "look", List.of(),
                List.of(new ModelMessage(ModelRole.USER, content)), null)), NOW);
    }

    private static byte[] png(int color) throws IOException {
        BufferedImage image = new BufferedImage(4, 3, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) image.setRGB(x, y, color);
        }
        var output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        image.flush();
        return output.toByteArray();
    }

    private static GuideSessionExportSnapshot snapshot(String user, String assistant) {
        GuideSessionExportSnapshot.Request request = new GuideSessionExportSnapshot.Request(
                UUID.randomUUID(), NOW,
                GuideRequestStatus.COMPLETED,
                user,
                List.of(
                        new GuideSessionExportSnapshot.Entry.Assistant(assistant, false),
                        new GuideSessionExportSnapshot.Entry.Tool(
                                "call-recipe", "openallay:get_recipe", GuideToolStatus.SUCCEEDED)),
                List.of(), null);
        return new GuideSessionExportSnapshot("main", List.of(request), NOW);
    }
}
