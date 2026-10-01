package dev.openallay.model;

import java.time.Duration;

/** A transient HTTP failure from the model service or an intermediate gateway. */
public final class ModelUpstreamException extends ModelClientException {
    private final Duration retryAfter;

    public ModelUpstreamException(int status, Duration retryAfter) {
        super(new ModelFailure(
                "model_upstream_error",
                "Model service or gateway returned HTTP " + status
                        + "; this model reply did not complete. Completed operations are preserved.",
                status));
        if (status != 502 && status != 503 && status != 504) {
            throw new IllegalArgumentException("Not a transient upstream HTTP status");
        }
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
