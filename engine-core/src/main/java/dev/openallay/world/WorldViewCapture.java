package dev.openallay.world;

import dev.openallay.context.EvidenceMetadata;
import dev.openallay.model.image.ImageReference;
import java.time.Instant;
import java.util.UUID;

/** Detached source-frame facts and an actor-owned managed image, never a path or pixel payload. */
public record WorldViewCapture(
        String captureId,
        Instant capturedAt,
        UUID actorId,
        String dimension,
        WorldViewRequest.Target target,
        boolean includedHud,
        boolean includedGameUi,
        int sourceWidth,
        int sourceHeight,
        int guiScale,
        WorldFocusObservation.Camera camera,
        WorldFocusObservation.Screen screen,
        ImageReference image,
        EvidenceMetadata evidence) {
    public WorldViewCapture {
        if (captureId == null || captureId.isBlank()) throw new IllegalArgumentException("captureId is required");
        java.util.Objects.requireNonNull(capturedAt, "capturedAt");
        java.util.Objects.requireNonNull(actorId, "actorId");
        if (dimension == null || dimension.isBlank()) throw new IllegalArgumentException("dimension is required");
        java.util.Objects.requireNonNull(target, "target");
        if (sourceWidth <= 0 || sourceHeight <= 0 || guiScale <= 0) {
            throw new IllegalArgumentException("source frame dimensions and scale must be positive");
        }
        java.util.Objects.requireNonNull(camera, "camera");
        java.util.Objects.requireNonNull(screen, "screen");
        java.util.Objects.requireNonNull(image, "image");
        java.util.Objects.requireNonNull(evidence, "evidence");
        if (!capturedAt.equals(evidence.capturedAt())) throw new IllegalArgumentException("source times differ");
    }
}
