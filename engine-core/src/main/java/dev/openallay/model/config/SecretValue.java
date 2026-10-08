package dev.openallay.model.config;

import dev.openallay.util.Java8Strings;
import java.util.Objects;

public final class SecretValue {
    private static final String REDACTED = "[REDACTED]";
    private final String value;

    private SecretValue(String value) {
        this.value = value;
    }

    public static SecretValue of(String value) {
        if (value == null || Java8Strings.isBlank(value)) {
            throw new IllegalArgumentException("API key must not be blank");
        }
        return new SecretValue(value);
    }

    public String reveal() {
        return value;
    }

    @Override
    public String toString() {
        return REDACTED;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SecretValue && value.equals(((SecretValue) other).value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(REDACTED);
    }
}
