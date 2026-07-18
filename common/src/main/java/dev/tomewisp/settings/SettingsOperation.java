package dev.tomewisp.settings;

import java.util.Objects;

/** One foreground settings action; only connection probes are cancellable. */
public record SettingsOperation(Kind kind, String targetId, boolean cancellable) {
    public enum Kind {
        IDLE,
        SAVING_MODELS,
        RELOADING_MODELS,
        REFRESHING_METADATA,
        TESTING_CONNECTION
    }

    public SettingsOperation {
        Objects.requireNonNull(kind, "kind");
        if (kind == Kind.IDLE && (targetId != null || cancellable)) {
            throw new IllegalArgumentException("idle settings operation has no target or cancellation");
        }
        if (cancellable != (kind == Kind.TESTING_CONNECTION)) {
            throw new IllegalArgumentException("only connection tests are cancellable");
        }
        if (targetId != null && targetId.isBlank()) {
            throw new IllegalArgumentException("targetId must be null or nonblank");
        }
    }

    public static SettingsOperation idle() {
        return new SettingsOperation(Kind.IDLE, null, false);
    }

    public static SettingsOperation models(Kind kind) {
        return new SettingsOperation(kind, null, false);
    }

    public static SettingsOperation probe(String profileId) {
        return new SettingsOperation(Kind.TESTING_CONNECTION, profileId, true);
    }
}
