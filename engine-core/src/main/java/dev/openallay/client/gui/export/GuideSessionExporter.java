package dev.openallay.client.gui.export;

import dev.openallay.guide.export.GuideSessionExportSnapshot;
import dev.openallay.model.ModelContent;
import dev.openallay.model.image.ImageReference;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Writes player-requested conversation text and permanent images under one fixed child. */
public final class GuideSessionExporter {
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter
            .ofPattern("uuuuMMdd-HHmmss-SSS", Locale.ROOT)
            .withZone(ZoneOffset.UTC);
    @dev.openallay.value.ValueType(ExportedFile.ValueSchemaProvider.class)
public static final class ExportedFile {
    private final String filename;
    private final int requestCount;
    public ExportedFile(String filename, int requestCount) {

            if (filename == null || !filename.matches("[a-zA-Z0-9_.-]+\\.txt")
                    || requestCount < 0) {
                throw new IllegalArgumentException("invalid exported file result");
            }

        this.filename = filename;
        this.requestCount = requestCount;
    }
    public String filename() { return filename; }
    public int requestCount() { return requestCount; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ExportedFile)) return false;
        ExportedFile that = (ExportedFile) other;
        return java.util.Objects.equals(filename, that.filename) && requestCount == that.requestCount;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(filename);
        hash = 31 * hash + Integer.hashCode(requestCount);
        return hash;
    }
    @Override public String toString() { return "ExportedFile[filename=" + filename + ", requestCount=" + requestCount + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ExportedFile> schema() {
            return new dev.openallay.value.ValueSchema<>(ExportedFile.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ExportedFile>>asList(new dev.openallay.value.ValueSchema.Component<>(ExportedFile.class, "filename", ExportedFile::filename), new dev.openallay.value.ValueSchema.Component<>(ExportedFile.class, "requestCount", ExportedFile::requestCount)), arguments -> new ExportedFile((String) arguments[0], (Integer) arguments[1]));
        }
    }
}

    private final Path gameDirectory;

    public GuideSessionExporter(Path gameDirectory) {
        this.gameDirectory = Objects.requireNonNull(gameDirectory, "gameDirectory")
                .toAbsolutePath().normalize();
    }

    public ExportedFile export(GuideSessionExportSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        try {
            // Resolve and verify every unique asset before creating any export files.
            // A missing scoped resolver is an explicit export failure, never a text-only fallback.
            Map<String, ImageReference> assets = preflightAssets(snapshot);
            byte[] encoded = format(snapshot).getBytes(StandardCharsets.UTF_8);
            String filename = snapshot.sessionId() + "-"
                    + FILE_TIME.format(snapshot.capturedAt()) + "-"
                    + digest(encoded).substring(0, 12) + ".txt";
            Path root = prepareManagedRoot();
            if (!assets.isEmpty()) {
                Path images = root.resolve("images");
                rejectSymlink(images);
                Files.createDirectories(images);
                rejectSymlink(images);
                if (!images.toRealPath().getParent().equals(root)) {
                    throw new IOException("export image directory escaped the managed directory");
                }
                for (ImageReference reference : assets.values()) {
                    // Keep memory bounded to one managed image. Re-read under the snapshot
                    // lease after the all-assets preflight and verify again before publication.
                    publishAtomic(images, imageFilename(reference), readVerified(snapshot, reference));
                }
            }
            // The text is the publication marker. All linked images already exist permanently.
            // An IO failure may leave verified images, but never a text file with missing assets.
            publishAtomic(root, filename, encoded);
            return new ExportedFile(filename, snapshot.requests().size());
        } catch (IOException | RuntimeException failure) {
            throw new GuideSessionExportException(
                    "history_export_failed", "Unable to write the guide session export", failure);
        } finally {
            try {
                snapshot.close();
            } catch (Exception conservativeRetention) {
                // Lease release failure can leave a managed pin, but must not turn an already
                // published permanent export into a failed operation. The owner handles retry.
            }
        }
    }

    private static Map<String, ImageReference> preflightAssets(GuideSessionExportSnapshot snapshot)
            throws IOException {
        Map<String, ImageReference> references = new LinkedHashMap<>();
        List<dev.openallay.model.ModelMessage> messages = dev.openallay.util.Java8Collections.toList(snapshot.requests().stream()
                .flatMap(request -> request.originalContext().stream()));
        dev.openallay.model.image.ModelImages.uniqueReferences(messages)
                .forEach(reference -> references.put(reference.sha256(), reference));
        for (ImageReference reference : references.values()) readVerified(snapshot, reference);
        return references;
    }

    private static byte[] readVerified(
            GuideSessionExportSnapshot snapshot, ImageReference reference) throws IOException {
        byte[] bytes = Objects.requireNonNull(
                snapshot.imagePayloadResolver().read(reference), "resolved image bytes").clone();
        if (bytes.length != reference.byteSize() || !digest(bytes).equals(reference.sha256())) {
            throw new IOException("export image payload does not match its managed reference");
        }
        // The scoped resolver verifies the MIME type, dimensions and complete image decode.
        return bytes;
    }

    private static void appendImage(StringBuilder result, ImageReference reference) {
        result.append("MIME: ").append(reference.mimeType())
                .append("\nDimensions: ").append(reference.width()).append('x').append(reference.height())
                .append("\nBytes: ").append(reference.byteSize())
                .append("\nSHA-256: ").append(reference.sha256())
                .append("\nFile: images/").append(imageFilename(reference)).append("\n\n");
    }

    private static String imageFilename(ImageReference reference) {
        return reference.sha256() + (reference.mimeType().equals("image/png") ? ".png" : ".jpg");
    }

    private static void publishAtomic(Path root, String filename, byte[] encoded) throws IOException {
        Path destination = root.resolve(filename).normalize();
        if (!destination.getParent().equals(root)) {
            throw new IOException("export destination escaped the managed directory");
        }
        rejectSymlink(root);
        rejectSymlink(destination);
        Path temporary = Files.createTempFile(root, ".openallay-export-", ".tmp");
        try {
            restrictPermissions(temporary);
            try (FileChannel channel = FileChannel.open(temporary,
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING,
                    LinkOption.NOFOLLOW_LINKS)) {
                ByteBuffer buffer = ByteBuffer.wrap(encoded);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            rejectSymlink(root);
            rejectSymlink(destination);
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                throw new IOException("atomic export publication is unavailable", unsupported);
            }
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // Best-effort cleanup must not report a false failure after atomic publication.
            }
        }
    }

    static String format(GuideSessionExportSnapshot snapshot) {
        StringBuilder result = new StringBuilder();
        result.append("OpenAllay conversation export\n")
                .append("Session: ").append(snapshot.sessionId()).append('\n')
                .append("Captured: ").append(snapshot.capturedAt()).append("\n")
                .append("Tool results below are the recorded model-visible values or declared previews.\n")
                .append("Workspace handles do not survive the request that created them.\n\n");
        int index = 0;
        for (GuideSessionExportSnapshot.Request request : snapshot.requests()) {
            result.append("=== Request ").append(++index)
                    .append(" · ").append(request.createdAt())
                    .append(" · ").append(request.status()).append(" ===\n")
                    .append("Request ID: ").append(request.requestId()).append('\n')
                    .append("User\n")
                    .append(formatText(request.userMessage())).append("\n\n");
            if (request.originalContext().isEmpty()) {
                appendUnrecordedTimeline(result, request, dev.openallay.util.Java8Collections.setOf(), false);
            } else {
                Map<String, String> tools = new LinkedHashMap<>();
                Set<String> recordedCalls = new HashSet<>();
                boolean firstUserText = true;
                for (dev.openallay.model.ModelMessage message : request.originalContext()) {
                    message.inputObservation().ifPresent(anchor -> {
                        result.append("Player-input reference context\n")
                                .append(formatText(dev.openallay.model.image.ModelImages.inputObservationLabel(anchor)))
                                .append("\nTyped source metadata\n")
                                .append(dev.openallay.world.ClientObservationAnchorJson.encode(java.util.Optional.of(anchor)))
                                .append("\n\n");
                        anchor.image().ifPresent(capture -> {
                            result.append("Associated input source · IMAGE\n");
                            appendImage(result, capture.image());
                        });
                    });
                    for (ModelContent content : message.content()) {
                        Objects.requireNonNull(content);
                        final class $oaPattern0_Holder { dev.openallay.model.ModelContent value; ModelContent.Text bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
if ((($oaPattern0_holder.value = content) instanceof dev.openallay.model.ModelContent.Text && (($oaPattern0_holder.bound = (ModelContent.Text) $oaPattern0_holder.value) != null))) {
                            if (message.role() == dev.openallay.model.ModelRole.USER
                                    && firstUserText && $oaPattern0_holder.bound.text().equals(request.userMessage())) {
                                firstUserText = false;
                                continue;
                            }
                            result.append(message.role()).append('\n')
                                    .append(formatText($oaPattern0_holder.bound.text())).append("\n\n");
                        } else {
final class $oaPattern1_Holder { dev.openallay.model.ModelContent value; ModelContent.Image bound; }
final $oaPattern1_Holder $oaPattern1_holder = new $oaPattern1_Holder();
if ((($oaPattern1_holder.value = content) instanceof dev.openallay.model.ModelContent.Image && (($oaPattern1_holder.bound = (ModelContent.Image) $oaPattern1_holder.value) != null))) {
                            ImageReference reference = $oaPattern1_holder.bound.reference();
                            if ($oaPattern1_holder.bound.originToolUseId() == null) {
                                result.append(message.role()).append(" · IMAGE\n");
                            } else {
                                result.append("Tool observation · ").append(formatText($oaPattern1_holder.bound.originToolUseId()))
                                        .append(" · IMAGE\n");
                            }
                            appendImage(result, reference);
                        } else {
final class $oaPattern2_Holder { dev.openallay.model.ModelContent value; ModelContent.ToolUse bound; }
final $oaPattern2_Holder $oaPattern2_holder = new $oaPattern2_Holder();
if ((($oaPattern2_holder.value = content) instanceof dev.openallay.model.ModelContent.ToolUse && (($oaPattern2_holder.bound = (ModelContent.ToolUse) $oaPattern2_holder.value) != null))) {
                            tools.put($oaPattern2_holder.bound.id(), $oaPattern2_holder.bound.name());
                            recordedCalls.add($oaPattern2_holder.bound.id());
                            result.append("Tool · ").append(safeToolName($oaPattern2_holder.bound.name()))
                                    .append(" · SUBMITTED\nInvocation ID: ")
                                    .append(formatText($oaPattern2_holder.bound.id())).append("\nSubmitted arguments\n")
                                    .append($oaPattern2_holder.bound.input())
                                    .append("\n\n");
                        } else {
final class $oaPattern3_Holder { dev.openallay.model.ModelContent value; ModelContent.ToolResult bound; }
final $oaPattern3_Holder $oaPattern3_holder = new $oaPattern3_Holder();
if ((($oaPattern3_holder.value = content) instanceof dev.openallay.model.ModelContent.ToolResult && (($oaPattern3_holder.bound = (ModelContent.ToolResult) $oaPattern3_holder.value) != null))) {
                            appendOutcome(result, tools.getOrDefault($oaPattern3_holder.bound.toolUseId(), "unknown_tool"), $oaPattern3_holder.bound);
                            for (ImageReference reference : $oaPattern3_holder.bound.images()) {
                                result.append("Tool observation · ").append(formatText($oaPattern3_holder.bound.toolUseId()))
                                        .append(" · IMAGE\n");
                                appendImage(result, reference);
                            }
                        } else {
final class $oaPattern4_Holder { dev.openallay.model.ModelContent value; ModelContent.Reasoning bound; }
final $oaPattern4_Holder $oaPattern4_holder = new $oaPattern4_Holder();
if ((($oaPattern4_holder.value = content) instanceof dev.openallay.model.ModelContent.Reasoning && (($oaPattern4_holder.bound = (ModelContent.Reasoning) $oaPattern4_holder.value) != null))) {
                            throw new IllegalArgumentException("export cannot contain reasoning");
                        } else {
                            throw new IncompatibleClassChangeError();
                        }
}
}
}
}
                    }
                }
                appendUnrecordedTimeline(result, request, recordedCalls, true);
            }
            if (request.failure() != null) {
                result.append("Request failure\nCode: ")
                        .append(formatText(request.failure().code()))
                        .append("\nMessage: ").append(formatText(request.failure().message()))
                        .append("\n\n");
            }
            if (request.status() == dev.openallay.guide.GuideRequestStatus.CANCELLED
                    || request.status() == dev.openallay.guide.GuideRequestStatus.INTERRUPTED) {
                result.append("[This request ended before the response completed.]\n\n");
            }
        }
        return result.toString();
    }

    private static void appendUnrecordedTimeline(
            StringBuilder result, GuideSessionExportSnapshot.Request request,
            Set<String> recordedCalls, boolean hasOriginalContext) {
        for (dev.openallay.guide.export.GuideSessionExportSnapshot.Entry entry : request.timeline()) {
            Objects.requireNonNull(entry);
            final class $oaPattern5_Holder { dev.openallay.guide.export.GuideSessionExportSnapshot.Entry value; GuideSessionExportSnapshot.Entry.User bound; }
final $oaPattern5_Holder $oaPattern5_holder = new $oaPattern5_Holder();
if ((($oaPattern5_holder.value = entry) instanceof dev.openallay.guide.export.GuideSessionExportSnapshot.Entry.User && (($oaPattern5_holder.bound = (GuideSessionExportSnapshot.Entry.User) $oaPattern5_holder.value) != null))) {
                if (!hasOriginalContext) {
                    result.append("User (supplemental instruction)\n")
                            .append(formatText($oaPattern5_holder.bound.text())).append("\n\n");
                }
            } else {
final class $oaPattern6_Holder { dev.openallay.guide.export.GuideSessionExportSnapshot.Entry value; GuideSessionExportSnapshot.Entry.Assistant bound; }
final $oaPattern6_Holder $oaPattern6_holder = new $oaPattern6_Holder();
if ((($oaPattern6_holder.value = entry) instanceof dev.openallay.guide.export.GuideSessionExportSnapshot.Entry.Assistant && (($oaPattern6_holder.bound = (GuideSessionExportSnapshot.Entry.Assistant) $oaPattern6_holder.value) != null))) {
                if (!hasOriginalContext) {
                    result.append($oaPattern6_holder.bound.streaming() ? "Assistant (in progress)\n" : "Assistant\n")
                            .append(formatText($oaPattern6_holder.bound.text())).append("\n\n");
                } else if ($oaPattern6_holder.bound.streaming()) {
                    result.append("Visible unfinished assistant text (display snapshot, not an additional message)\n")
                            .append(formatText($oaPattern6_holder.bound.text())).append("\n\n");
                }
            } else {
final class $oaPattern7_Holder { dev.openallay.guide.export.GuideSessionExportSnapshot.Entry value; GuideSessionExportSnapshot.Entry.Tool bound; }
final $oaPattern7_Holder $oaPattern7_holder = new $oaPattern7_Holder();
if ((($oaPattern7_holder.value = entry) instanceof dev.openallay.guide.export.GuideSessionExportSnapshot.Entry.Tool && (($oaPattern7_holder.bound = (GuideSessionExportSnapshot.Entry.Tool) $oaPattern7_holder.value) != null))) {
                if (!recordedCalls.contains($oaPattern7_holder.bound.invocationId())) {
                    result.append("Tool · ").append(safeToolName($oaPattern7_holder.bound.toolId()))
                            .append(" · ").append($oaPattern7_holder.bound.status()).append("\nInvocation ID: ")
                            .append(formatText($oaPattern7_holder.bound.invocationId()))
                            .append("\n[No completed model-visible result was recorded.]\n\n");
                }
            } else {
                throw new IncompatibleClassChangeError();
            }
}
}
        }
    }

    private static void appendOutcome(
            StringBuilder result, String toolId, ModelContent.ToolResult outcome) {
        result.append("Tool · ").append(safeToolName(toolId))
                .append(outcome.error() ? " · FAILED\n" : " · SUCCEEDED\n")
                .append("Invocation ID: ").append(formatText(outcome.toolUseId())).append('\n')
                .append(outcome.error() ? "Tool error (model-visible)\n" : "Result (model-visible)\n");
        com.google.gson.JsonElement value = outcome.value();
        String text = value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString() : value.toString();
        result.append(formatText(text)).append("\n\n");
    }

    static String formatText(String value) {
        return value == null ? "" : value
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\p{Cc}&&[^\\n\\t]]", "�");
    }

    private Path prepareManagedRoot() throws IOException {
        Path realGame = gameDirectory.toRealPath();
        Path openallay = gameDirectory.resolve("openallay");
        Path exports = openallay.resolve("exports");
        rejectSymlink(openallay);
        rejectSymlink(exports);
        Files.createDirectories(exports);
        rejectSymlink(openallay);
        rejectSymlink(exports);
        Path realExports = exports.toRealPath();
        if (!realExports.startsWith(realGame)) {
            throw new IOException("managed export directory escaped the game directory");
        }
        return realExports;
    }

    private static void rejectSymlink(Path path) throws IOException {
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(path)) {
            throw new IOException("managed export directory contains a symbolic link");
        }
    }

    private static void restrictPermissions(Path file) {
        try {
            Files.setPosixFilePermissions(file, java.nio.file.attribute.PosixFilePermissions
                    .fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows and non-POSIX filesystems retain their platform ACL defaults.
        }
    }

    private static String safeToolName(String toolId) {
        int separator = toolId.indexOf(':');
        String value = separator < 0 ? toolId : toolId.substring(separator + 1);
        return value.replaceAll("[^a-zA-Z0-9_.-]", "_");
    }

    private static String digest(byte[] value) {
        try {
            return dev.openallay.util.Java8Hex.formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
