package dev.openallay.world;

import dev.openallay.context.EvidenceMetadata;
import dev.openallay.model.image.ImageReference;
import java.time.Instant;
import java.util.UUID;

/** Detached source-frame facts and an actor-owned managed image, never a path or pixel payload. */
@dev.openallay.value.ValueType(WorldViewCapture.ValueSchemaProvider.class)
public final class WorldViewCapture {
    private final String captureId;
    private final Instant capturedAt;
    private final UUID actorId;
    private final String dimension;
    private final WorldViewRequest.Target target;
    private final boolean includedHud;
    private final boolean includedGameUi;
    private final int sourceWidth;
    private final int sourceHeight;
    private final int guiScale;
    private final WorldFocusObservation.Camera camera;
    private final WorldFocusObservation.Screen screen;
    private final ImageReference image;
    private final EvidenceMetadata evidence;
    public WorldViewCapture(String captureId, Instant capturedAt, UUID actorId, String dimension, WorldViewRequest.Target target, boolean includedHud, boolean includedGameUi, int sourceWidth, int sourceHeight, int guiScale, WorldFocusObservation.Camera camera, WorldFocusObservation.Screen screen, ImageReference image, EvidenceMetadata evidence) {

        if (captureId == null || dev.openallay.util.Java8Strings.isBlank(captureId)) throw new IllegalArgumentException("captureId is required");
        java.util.Objects.requireNonNull(capturedAt, "capturedAt");
        java.util.Objects.requireNonNull(actorId, "actorId");
        if (dimension == null || dev.openallay.util.Java8Strings.isBlank(dimension)) throw new IllegalArgumentException("dimension is required");
        java.util.Objects.requireNonNull(target, "target");
        if (sourceWidth <= 0 || sourceHeight <= 0 || guiScale <= 0) {
            throw new IllegalArgumentException("source frame dimensions and scale must be positive");
        }
        java.util.Objects.requireNonNull(camera, "camera");
        java.util.Objects.requireNonNull(screen, "screen");
        java.util.Objects.requireNonNull(image, "image");
        java.util.Objects.requireNonNull(evidence, "evidence");
        if (!capturedAt.equals(evidence.capturedAt())) throw new IllegalArgumentException("source times differ");

        this.captureId = captureId;
        this.capturedAt = capturedAt;
        this.actorId = actorId;
        this.dimension = dimension;
        this.target = target;
        this.includedHud = includedHud;
        this.includedGameUi = includedGameUi;
        this.sourceWidth = sourceWidth;
        this.sourceHeight = sourceHeight;
        this.guiScale = guiScale;
        this.camera = camera;
        this.screen = screen;
        this.image = image;
        this.evidence = evidence;
    }
    public String captureId() { return captureId; }
    public Instant capturedAt() { return capturedAt; }
    public UUID actorId() { return actorId; }
    public String dimension() { return dimension; }
    public WorldViewRequest.Target target() { return target; }
    public boolean includedHud() { return includedHud; }
    public boolean includedGameUi() { return includedGameUi; }
    public int sourceWidth() { return sourceWidth; }
    public int sourceHeight() { return sourceHeight; }
    public int guiScale() { return guiScale; }
    public WorldFocusObservation.Camera camera() { return camera; }
    public WorldFocusObservation.Screen screen() { return screen; }
    public ImageReference image() { return image; }
    public EvidenceMetadata evidence() { return evidence; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof WorldViewCapture)) return false;
        WorldViewCapture that = (WorldViewCapture) other;
        return java.util.Objects.equals(captureId, that.captureId) && java.util.Objects.equals(capturedAt, that.capturedAt) && java.util.Objects.equals(actorId, that.actorId) && java.util.Objects.equals(dimension, that.dimension) && java.util.Objects.equals(target, that.target) && includedHud == that.includedHud && includedGameUi == that.includedGameUi && sourceWidth == that.sourceWidth && sourceHeight == that.sourceHeight && guiScale == that.guiScale && java.util.Objects.equals(camera, that.camera) && java.util.Objects.equals(screen, that.screen) && java.util.Objects.equals(image, that.image) && java.util.Objects.equals(evidence, that.evidence);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(captureId);
        hash = 31 * hash + java.util.Objects.hashCode(capturedAt);
        hash = 31 * hash + java.util.Objects.hashCode(actorId);
        hash = 31 * hash + java.util.Objects.hashCode(dimension);
        hash = 31 * hash + java.util.Objects.hashCode(target);
        hash = 31 * hash + Boolean.hashCode(includedHud);
        hash = 31 * hash + Boolean.hashCode(includedGameUi);
        hash = 31 * hash + Integer.hashCode(sourceWidth);
        hash = 31 * hash + Integer.hashCode(sourceHeight);
        hash = 31 * hash + Integer.hashCode(guiScale);
        hash = 31 * hash + java.util.Objects.hashCode(camera);
        hash = 31 * hash + java.util.Objects.hashCode(screen);
        hash = 31 * hash + java.util.Objects.hashCode(image);
        hash = 31 * hash + java.util.Objects.hashCode(evidence);
        return hash;
    }
    @Override public String toString() { return "WorldViewCapture[captureId=" + captureId + ", capturedAt=" + capturedAt + ", actorId=" + actorId + ", dimension=" + dimension + ", target=" + target + ", includedHud=" + includedHud + ", includedGameUi=" + includedGameUi + ", sourceWidth=" + sourceWidth + ", sourceHeight=" + sourceHeight + ", guiScale=" + guiScale + ", camera=" + camera + ", screen=" + screen + ", image=" + image + ", evidence=" + evidence + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<WorldViewCapture> schema() {
            return new dev.openallay.value.ValueSchema<>(WorldViewCapture.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<WorldViewCapture>>asList(new dev.openallay.value.ValueSchema.Component<>(WorldViewCapture.class, "captureId", WorldViewCapture::captureId), new dev.openallay.value.ValueSchema.Component<>(WorldViewCapture.class, "capturedAt", WorldViewCapture::capturedAt), new dev.openallay.value.ValueSchema.Component<>(WorldViewCapture.class, "actorId", WorldViewCapture::actorId), new dev.openallay.value.ValueSchema.Component<>(WorldViewCapture.class, "dimension", WorldViewCapture::dimension), new dev.openallay.value.ValueSchema.Component<>(WorldViewCapture.class, "target", WorldViewCapture::target), new dev.openallay.value.ValueSchema.Component<>(WorldViewCapture.class, "includedHud", WorldViewCapture::includedHud), new dev.openallay.value.ValueSchema.Component<>(WorldViewCapture.class, "includedGameUi", WorldViewCapture::includedGameUi), new dev.openallay.value.ValueSchema.Component<>(WorldViewCapture.class, "sourceWidth", WorldViewCapture::sourceWidth), new dev.openallay.value.ValueSchema.Component<>(WorldViewCapture.class, "sourceHeight", WorldViewCapture::sourceHeight), new dev.openallay.value.ValueSchema.Component<>(WorldViewCapture.class, "guiScale", WorldViewCapture::guiScale), new dev.openallay.value.ValueSchema.Component<>(WorldViewCapture.class, "camera", WorldViewCapture::camera), new dev.openallay.value.ValueSchema.Component<>(WorldViewCapture.class, "screen", WorldViewCapture::screen), new dev.openallay.value.ValueSchema.Component<>(WorldViewCapture.class, "image", WorldViewCapture::image), new dev.openallay.value.ValueSchema.Component<>(WorldViewCapture.class, "evidence", WorldViewCapture::evidence)), arguments -> new WorldViewCapture((String) arguments[0], (Instant) arguments[1], (UUID) arguments[2], (String) arguments[3], (WorldViewRequest.Target) arguments[4], (Boolean) arguments[5], (Boolean) arguments[6], (Integer) arguments[7], (Integer) arguments[8], (Integer) arguments[9], (WorldFocusObservation.Camera) arguments[10], (WorldFocusObservation.Screen) arguments[11], (ImageReference) arguments[12], (EvidenceMetadata) arguments[13]));
        }
    }
}
