package dev.openallay.settings.model;

import dev.openallay.model.config.ModelProtocol;
import java.time.Instant;
import java.util.Objects;

/** Credential-free result retained by the settings UI after a connection probe. */
public interface ModelConnectionResult {
    /** Closed Java8 result ingress; null retains the not-tested snapshot state. */
    static void requireKnown(ModelConnectionResult result) {
        if (result == null) return;
        Class<?> type = result.getClass();
        if (type != Success.class && type != Failure.class) {
            throw new IncompatibleClassChangeError("Unknown model connection result subtype");
        }
    }

    @dev.openallay.value.ValueType(Success.ValueSchemaProvider.class)
public static final class Success implements ModelConnectionResult {
    private final String profileId;
    private final ModelProtocol protocol;
    private final String authority;
    private final Instant completedAt;
    private final long latencyMillis;
    public Success(String profileId, ModelProtocol protocol, String authority, Instant completedAt, long latencyMillis) {

            ModelConnectionResultValidation.requireText(profileId, "profileId");
            Objects.requireNonNull(protocol, "protocol");
            ModelConnectionResultValidation.requireText(authority, "authority");
            Objects.requireNonNull(completedAt, "completedAt");
            if (latencyMillis < 0) {
                throw new IllegalArgumentException("latencyMillis must not be negative");
            }

        this.profileId = profileId;
        this.protocol = protocol;
        this.authority = authority;
        this.completedAt = completedAt;
        this.latencyMillis = latencyMillis;
    }
    public String profileId() { return profileId; }
    public ModelProtocol protocol() { return protocol; }
    public String authority() { return authority; }
    public Instant completedAt() { return completedAt; }
    public long latencyMillis() { return latencyMillis; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Success)) return false;
        Success that = (Success) other;
        return java.util.Objects.equals(profileId, that.profileId) && java.util.Objects.equals(protocol, that.protocol) && java.util.Objects.equals(authority, that.authority) && java.util.Objects.equals(completedAt, that.completedAt) && latencyMillis == that.latencyMillis;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(profileId);
        hash = 31 * hash + java.util.Objects.hashCode(protocol);
        hash = 31 * hash + java.util.Objects.hashCode(authority);
        hash = 31 * hash + java.util.Objects.hashCode(completedAt);
        hash = 31 * hash + Long.hashCode(latencyMillis);
        return hash;
    }
    @Override public String toString() { return "Success[profileId=" + profileId + ", protocol=" + protocol + ", authority=" + authority + ", completedAt=" + completedAt + ", latencyMillis=" + latencyMillis + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Success> schema() {
            return new dev.openallay.value.ValueSchema<>(Success.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Success>>asList(new dev.openallay.value.ValueSchema.Component<>(Success.class, "profileId", Success::profileId), new dev.openallay.value.ValueSchema.Component<>(Success.class, "protocol", Success::protocol), new dev.openallay.value.ValueSchema.Component<>(Success.class, "authority", Success::authority), new dev.openallay.value.ValueSchema.Component<>(Success.class, "completedAt", Success::completedAt), new dev.openallay.value.ValueSchema.Component<>(Success.class, "latencyMillis", Success::latencyMillis)), arguments -> new Success((String) arguments[0], (ModelProtocol) arguments[1], (String) arguments[2], (Instant) arguments[3], (Long) arguments[4]));
        }
    }
}

    @dev.openallay.value.ValueType(Failure.ValueSchemaProvider.class)
public static final class Failure implements ModelConnectionResult {
    private final String code;
    private final String message;
    public Failure(String code, String message) {

            ModelConnectionResultValidation.requireText(code, "code");
            ModelConnectionResultValidation.requireText(message, "message");

        this.code = code;
        this.message = message;
    }
    public String code() { return code; }
    public String message() { return message; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Failure)) return false;
        Failure that = (Failure) other;
        return java.util.Objects.equals(code, that.code) && java.util.Objects.equals(message, that.message);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(code);
        hash = 31 * hash + java.util.Objects.hashCode(message);
        return hash;
    }
    @Override public String toString() { return "Failure[code=" + code + ", message=" + message + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Failure> schema() {
            return new dev.openallay.value.ValueSchema<>(Failure.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Failure>>asList(new dev.openallay.value.ValueSchema.Component<>(Failure.class, "code", Failure::code), new dev.openallay.value.ValueSchema.Component<>(Failure.class, "message", Failure::message)), arguments -> new Failure((String) arguments[0], (String) arguments[1]));
        }
    }
}


}

/** Package-private constructor validation; no new public interface method. */
final class ModelConnectionResultValidation {
    private ModelConnectionResultValidation() {}
    static void requireText(String value, String name) {
        if (value == null || dev.openallay.util.Java8Strings.isBlank(value)) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
