package dev.openallay.model.config;

import dev.openallay.util.Java8Strings;
import dev.openallay.value.ValueSchema;
import dev.openallay.value.ValueType;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Qualified credential identity safe to persist and expose in redacted settings state. */
@ValueType(CredentialReference.Schema.class)
public final class CredentialReference {
    private final Kind kind;
    private final String value;

    public CredentialReference(Kind kind, String value) {
        Objects.requireNonNull(kind, "kind");
        if (value == null || Java8Strings.isBlank(value)) {
            throw new IllegalArgumentException("credential reference value must not be blank");
        }
        switch (kind) {
            case LOCAL:
                value = UUID.fromString(value).toString();
                break;
            case ENVIRONMENT:
                if (!value.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                    throw new IllegalArgumentException("invalid credential environment reference");
                }
                break;
            default:
                throw new AssertionError(kind);
        }
        this.kind = kind;
        this.value = value;
    }

    public Kind kind() { return kind; }
    public String value() { return value; }

    public static CredentialReference local(UUID id) {
        return new CredentialReference(Kind.LOCAL, Objects.requireNonNull(id, "id").toString());
    }

    public static CredentialReference environment(String name) {
        return new CredentialReference(Kind.ENVIRONMENT, name);
    }

    public static CredentialReference parse(String encoded) {
        if (encoded == null) {
            throw new IllegalArgumentException("credential reference must not be null");
        }
        int separator = encoded.indexOf(':');
        if (separator <= 0 || separator == encoded.length() - 1) {
            throw new IllegalArgumentException("invalid credential reference");
        }
        Kind kind;
        switch (encoded.substring(0, separator).toLowerCase(Locale.ROOT)) {
            case "local": kind = Kind.LOCAL; break;
            case "env": kind = Kind.ENVIRONMENT; break;
            default: throw new IllegalArgumentException("unsupported credential reference kind");
        }
        return new CredentialReference(kind, encoded.substring(separator + 1));
    }

    public String encoded() {
        return (kind == Kind.LOCAL ? "local:" : "env:") + value;
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CredentialReference)) return false;
        CredentialReference that = (CredentialReference) other;
        return kind == that.kind && Objects.equals(value, that.value);
    }

    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Objects.hashCode(kind);
        hash = 31 * hash + Objects.hashCode(value);
        return hash;
    }

    @Override
    public String toString() {
        return encoded();
    }

    /** Public provider; calls only the declared accessors and constructor. */
    public static final class Schema implements ValueSchema.Provider {
        public Schema() {}
        @Override public ValueSchema<CredentialReference> schema() {
            return new ValueSchema<>(CredentialReference.class, Arrays.asList(
                    new ValueSchema.Component<>(CredentialReference.class, "kind", CredentialReference::kind),
                    new ValueSchema.Component<>(CredentialReference.class, "value", CredentialReference::value)),
                    arguments -> new CredentialReference((Kind) arguments[0], (String) arguments[1]));
        }
    }

    public enum Kind {
        LOCAL,
        ENVIRONMENT
    }
}
