package dev.openallay.recipe;

import java.util.Objects;

/**
 * Development-probe view of whether required recipe viewers can be sampled safely.
 *
 * <p>{@code state}: readiness state</p>
 * <p>{@code code}: stable diagnostic code</p>
 * <p>{@code message}: human-readable diagnostic</p>
 */
@dev.openallay.value.ValueType(RecipeProviderReadiness.ValueSchemaProvider.class)
public final class RecipeProviderReadiness {
    private final State state;
    private final String code;
    private final String message;
    public RecipeProviderReadiness(State state, String code, String message) {

        Objects.requireNonNull(state, "state");
        code = require(code, "code");
        message = require(message, "message");

        this.state = state;
        this.code = code;
        this.message = message;
    }
    public State state() { return state; }
    public String code() { return code; }
    public String message() { return message; }
public static RecipeProviderReadiness ready() {
        return new RecipeProviderReadiness(State.READY, "ready", "Recipe providers are ready");
    }
public static RecipeProviderReadiness waiting(String code, String message) {
        return new RecipeProviderReadiness(State.WAITING, code, message);
    }
public static RecipeProviderReadiness failed(String code, String message) {
        return new RecipeProviderReadiness(State.FAILED, code, message);
    }
private static String require(String value, String name) {
        Objects.requireNonNull(value, name);
        if (dev.openallay.util.Java8Strings.isBlank(value)) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
public enum State {
        READY,
        WAITING,
        FAILED
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RecipeProviderReadiness)) return false;
        RecipeProviderReadiness that = (RecipeProviderReadiness) other;
        return java.util.Objects.equals(state, that.state) && java.util.Objects.equals(code, that.code) && java.util.Objects.equals(message, that.message);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(state);
        hash = 31 * hash + java.util.Objects.hashCode(code);
        hash = 31 * hash + java.util.Objects.hashCode(message);
        return hash;
    }
    @Override public String toString() { return "RecipeProviderReadiness[state=" + state + ", code=" + code + ", message=" + message + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<RecipeProviderReadiness> schema() {
            return new dev.openallay.value.ValueSchema<>(RecipeProviderReadiness.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<RecipeProviderReadiness>>asList(new dev.openallay.value.ValueSchema.Component<>(RecipeProviderReadiness.class, "state", RecipeProviderReadiness::state), new dev.openallay.value.ValueSchema.Component<>(RecipeProviderReadiness.class, "code", RecipeProviderReadiness::code), new dev.openallay.value.ValueSchema.Component<>(RecipeProviderReadiness.class, "message", RecipeProviderReadiness::message)), arguments -> new RecipeProviderReadiness((State) arguments[0], (String) arguments[1], (String) arguments[2]));
        }
    }
}
