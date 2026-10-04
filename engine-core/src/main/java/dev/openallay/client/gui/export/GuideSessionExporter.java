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
    public record ExportedFile(String filename, int requestCount) {
        public ExportedFile {
            if (filename == null || !filename.matches("[a-zA-Z0-9_.-]+\\.txt")
                    || requestCount < 0) {
                throw new IllegalArgumentException("invalid exported file result");
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
        List<dev.openallay.model.ModelMessage> messages = snapshot.requests().stream()
                .flatMap(request -> request.originalContext().stream()).toList();
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
                appendUnrecordedTimeline(result, request, Set.of(), false);
            } else {
                Map<String, String> tools = new LinkedHashMap<>();
                Set<String> recordedCalls = new HashSet<>();
                boolean firstUserText = true;
                for (var message : request.originalContext()) {
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
                        switch (content) {
                            case ModelContent.Text text -> {
                                if (message.role() == dev.openallay.model.ModelRole.USER
                                        && firstUserText && text.text().equals(request.userMessage())) {
                                    firstUserText = false;
                                    continue;
                                }
                                result.append(message.role()).append('\n')
                                        .append(formatText(text.text())).append("\n\n");
                            }
                            case ModelContent.Image image -> {
                                ImageReference reference = image.reference();
                                if (image.originToolUseId() == null) {
                                    result.append(message.role()).append(" · IMAGE\n");
                                } else {
                                    result.append("Tool observation · ").append(formatText(image.originToolUseId()))
                                            .append(" · IMAGE\n");
                                }
                                appendImage(result, reference);
                            }
                            case ModelContent.ToolUse call -> {
                                tools.put(call.id(), call.name());
                                recordedCalls.add(call.id());
                                result.append("Tool · ").append(safeToolName(call.name()))
                                        .append(" · SUBMITTED\nInvocation ID: ")
                                        .append(formatText(call.id())).append("\nSubmitted arguments\n")
                                        .append(call.input())
                                        .append("\n\n");
                            }
                            case ModelContent.ToolResult outcome -> {
                                appendOutcome(result, tools.getOrDefault(outcome.toolUseId(), "unknown_tool"), outcome);
                                for (ImageReference reference : outcome.images()) {
                                    result.append("Tool observation · ").append(formatText(outcome.toolUseId()))
                                            .append(" · IMAGE\n");
                                    appendImage(result, reference);
                                }
                            }
                            case ModelContent.Reasoning ignored ->
                                    throw new IllegalArgumentException("export cannot contain reasoning");
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
        for (var entry : request.timeline()) {
            switch (entry) {
                case GuideSessionExportSnapshot.Entry.User user -> {
                    if (!hasOriginalContext) {
                        result.append("User (supplemental instruction)\n")
                                .append(formatText(user.text())).append("\n\n");
                    }
                }
                case GuideSessionExportSnapshot.Entry.Assistant assistant -> {
                    if (!hasOriginalContext) {
                        result.append(assistant.streaming() ? "Assistant (in progress)\n" : "Assistant\n")
                                .append(formatText(assistant.text())).append("\n\n");
                    } else if (assistant.streaming()) {
                        result.append("Visible unfinished assistant text (display snapshot, not an additional message)\n")
                                .append(formatText(assistant.text())).append("\n\n");
                    }
                }
                case GuideSessionExportSnapshot.Entry.Tool tool -> {
                    if (!recordedCalls.contains(tool.invocationId())) {
                        result.append("Tool · ").append(safeToolName(tool.toolId()))
                                .append(" · ").append(tool.status()).append("\nInvocation ID: ")
                                .append(formatText(tool.invocationId()))
                                .append("\n[No completed model-visible result was recorded.]\n\n");
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
        var value = outcome.value();
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
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
