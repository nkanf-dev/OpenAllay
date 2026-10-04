package dev.openallay.world;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** The source before Guide entry. An absent image does not imply an unseen frame was captured. */
public record ClientObservationAnchor(
        UUID associationId,
        Instant capturedAt,
        WorldFocusObservation focus,
        Optional<WorldViewCapture> image) {
    public ClientObservationAnchor {
        Objects.requireNonNull(associationId, "associationId");
        Objects.requireNonNull(capturedAt, "capturedAt");
        Objects.requireNonNull(focus, "focus");
        image = Objects.requireNonNull(image, "image");
        if (!capturedAt.equals(focus.capturedAt())) throw new IllegalArgumentException("focus source times differ");
    }

    public static ClientObservationAnchor focus(WorldFocusObservation focus) {
        return new ClientObservationAnchor(UUID.randomUUID(), focus.capturedAt(), focus, Optional.empty());
    }
}
