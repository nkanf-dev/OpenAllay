package dev.openallay.world;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** The source before Guide entry. An absent image does not imply an unseen frame was captured. */
@dev.openallay.value.ValueType(ClientObservationAnchor.ValueSchemaProvider.class)
public final class ClientObservationAnchor {
    private final UUID associationId;
    private final Instant capturedAt;
    private final WorldFocusObservation focus;
    private final Optional<WorldViewCapture> image;
    public ClientObservationAnchor(UUID associationId, Instant capturedAt, WorldFocusObservation focus, Optional<WorldViewCapture> image) {

        Objects.requireNonNull(associationId, "associationId");
        Objects.requireNonNull(capturedAt, "capturedAt");
        Objects.requireNonNull(focus, "focus");
        image = Objects.requireNonNull(image, "image");
        if (!capturedAt.equals(focus.capturedAt())) throw new IllegalArgumentException("focus source times differ");

        this.associationId = associationId;
        this.capturedAt = capturedAt;
        this.focus = focus;
        this.image = image;
    }
    public UUID associationId() { return associationId; }
    public Instant capturedAt() { return capturedAt; }
    public WorldFocusObservation focus() { return focus; }
    public Optional<WorldViewCapture> image() { return image; }
public static ClientObservationAnchor focus(WorldFocusObservation focus) {
        return new ClientObservationAnchor(UUID.randomUUID(), focus.capturedAt(), focus, Optional.empty());
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ClientObservationAnchor)) return false;
        ClientObservationAnchor that = (ClientObservationAnchor) other;
        return java.util.Objects.equals(associationId, that.associationId) && java.util.Objects.equals(capturedAt, that.capturedAt) && java.util.Objects.equals(focus, that.focus) && java.util.Objects.equals(image, that.image);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(associationId);
        hash = 31 * hash + java.util.Objects.hashCode(capturedAt);
        hash = 31 * hash + java.util.Objects.hashCode(focus);
        hash = 31 * hash + java.util.Objects.hashCode(image);
        return hash;
    }
    @Override public String toString() { return "ClientObservationAnchor[associationId=" + associationId + ", capturedAt=" + capturedAt + ", focus=" + focus + ", image=" + image + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ClientObservationAnchor> schema() {
            return new dev.openallay.value.ValueSchema<>(ClientObservationAnchor.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ClientObservationAnchor>>asList(new dev.openallay.value.ValueSchema.Component<>(ClientObservationAnchor.class, "associationId", ClientObservationAnchor::associationId), new dev.openallay.value.ValueSchema.Component<>(ClientObservationAnchor.class, "capturedAt", ClientObservationAnchor::capturedAt), new dev.openallay.value.ValueSchema.Component<>(ClientObservationAnchor.class, "focus", ClientObservationAnchor::focus), new dev.openallay.value.ValueSchema.Component<>(ClientObservationAnchor.class, "image", ClientObservationAnchor::image)), arguments -> new ClientObservationAnchor((UUID) arguments[0], (Instant) arguments[1], (WorldFocusObservation) arguments[2], (Optional) arguments[3]));
        }
    }
}
