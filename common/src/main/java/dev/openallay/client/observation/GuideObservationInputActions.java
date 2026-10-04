package dev.openallay.client.observation;

import dev.openallay.world.ClientObservationAnchor;
import java.util.concurrent.CompletableFuture;

/** Explicit player input capture. Implementations sample the current native source, never Guide pixels. */
public interface GuideObservationInputActions {
    ClientObservationAnchor captureFocus();
    CompletableFuture<ClientObservationAnchor> captureCurrentFrame();

    /** Release a temporary producer only after draft custody is acknowledged, or if never applied. */
    default CompletableFuture<Void> releaseCapture(ClientObservationAnchor anchor) {
        return CompletableFuture.completedFuture(null);
    }

    /** Native menu entry waits for its actual displayed frame; gameplay keeps lightweight focus only. */
    default CompletableFuture<ClientObservationAnchor> captureBeforeGuide() {
        ClientObservationAnchor focus = captureFocus();
        return "game_ui".equals(focus.focus().screen().role())
                ? captureCurrentFrame() : CompletableFuture.completedFuture(focus);
    }
}
