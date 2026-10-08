package dev.openallay.tool;

import dev.openallay.util.Java8Strings;
import dev.openallay.value.ValueSchema;
import dev.openallay.value.ValueType;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;

/** Result values are closed at the existing tool-result normalization boundary. */
public interface ToolResult<O> {
    @ValueType(Success.Schema.class)
    final class Success<O> implements ToolResult<O> {
        private final O value;

        public Success(O value) {
            this.value = Objects.requireNonNull(value, "value");
        }

        public O value() { return value; }

        @Override public final boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Success<?>)) return false;
            Success<?> that = (Success<?>) other;
            return Objects.equals(value, that.value);
        }
        @Override public final int hashCode() { return Objects.hashCode(value); }
        @Override public final String toString() { return "Success[value=" + value + "]"; }

        /** Public provider; calls only the declared accessor and constructor. */
        public static final class Schema<O> implements ValueSchema.Provider {
            public Schema() {}
            @Override public ValueSchema<Success<O>> schema() {
                Class<Success<O>> owner = owner();
                return new ValueSchema<>(owner, Collections.singletonList(
                        new ValueSchema.Component<>(owner, "value", Success::value)),
                        arguments -> new Success<>(value(arguments[0])));
            }
            @SuppressWarnings("unchecked") private Class<Success<O>> owner() {
                return (Class<Success<O>>) (Class<?>) Success.class;
            }
            @SuppressWarnings("unchecked") private O value(Object value) { return (O) value; }
        }
    }

    @ValueType(Failure.Schema.class)
    final class Failure<O> implements ToolResult<O> {
        private final String code;
        private final String message;

        public Failure(String code, String message) {
            if (code == null || Java8Strings.isBlank(code)
                    || message == null || Java8Strings.isBlank(message)) {
                throw new IllegalArgumentException("Failure code and message are required");
            }
            this.code = code;
            this.message = message;
        }

        public String code() { return code; }
        public String message() { return message; }

        @Override public final boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Failure<?>)) return false;
            Failure<?> that = (Failure<?>) other;
            return Objects.equals(code, that.code) && Objects.equals(message, that.message);
        }
        @Override public final int hashCode() {
            int hash = 0;
            hash = 31 * hash + Objects.hashCode(code);
            hash = 31 * hash + Objects.hashCode(message);
            return hash;
        }
        @Override public final String toString() {
            return "Failure[code=" + code + ", message=" + message + "]";
        }

        /** Public provider; calls only the declared accessors and constructor. */
        public static final class Schema<O> implements ValueSchema.Provider {
            public Schema() {}
            @Override public ValueSchema<Failure<O>> schema() {
                Class<Failure<O>> owner = owner();
                return new ValueSchema<>(owner, Arrays.asList(
                        new ValueSchema.Component<>(owner, "code", Failure::code),
                        new ValueSchema.Component<>(owner, "message", Failure::message)),
                        arguments -> new Failure<>((String) arguments[0], (String) arguments[1]));
            }
            @SuppressWarnings("unchecked") private Class<Failure<O>> owner() {
                return (Class<Failure<O>>) (Class<?>) Failure.class;
            }
        }
    }
}
