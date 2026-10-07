package dev.openallay.util;

import java.util.Objects;

/** Java8 exact eager fallback contract for Objects.requireNonNullElse. */
public final class Java8Objects {
    private Java8Objects() {}
    public static <T> T requireNonNullElse(T value, T defaultValue) {
        return value != null ? value : Objects.requireNonNull(defaultValue, "defaultObj");
    }
}
