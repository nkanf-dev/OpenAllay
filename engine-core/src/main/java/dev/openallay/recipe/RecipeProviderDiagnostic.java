package dev.openallay.recipe;

import dev.openallay.context.RecipeReference;
import java.util.Objects;

@dev.openallay.value.ValueType(RecipeProviderDiagnostic.ValueSchemaProvider.class)
public final class RecipeProviderDiagnostic {
    private final String sourceId;
    private final String code;
    private final String message;
    public RecipeProviderDiagnostic(String sourceId, String code, String message) {

        sourceId = RecipeReference.requireSourceId(sourceId);
        code = require(code, "code");
        message = require(message, "message");
            this.sourceId = sourceId;
        this.code = code;
        this.message = message;
    }
    public String sourceId() { return sourceId; }
    public String code() { return code; }
    public String message() { return message; }



    private static String require(String value, String name) {
        Objects.requireNonNull(value, name);
        if (dev.openallay.util.Java8Strings.isBlank(value)) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RecipeProviderDiagnostic)) return false;
        RecipeProviderDiagnostic that = (RecipeProviderDiagnostic) other;
        return java.util.Objects.equals(sourceId, that.sourceId) && java.util.Objects.equals(code, that.code) && java.util.Objects.equals(message, that.message);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(sourceId);
        hash = 31 * hash + java.util.Objects.hashCode(code);
        hash = 31 * hash + java.util.Objects.hashCode(message);
        return hash;
    }
    @Override public String toString() { return "RecipeProviderDiagnostic[sourceId=" + sourceId + ", code=" + code + ", message=" + message + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<RecipeProviderDiagnostic> schema() {
            return new dev.openallay.value.ValueSchema<>(RecipeProviderDiagnostic.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<RecipeProviderDiagnostic>>asList(
                    new dev.openallay.value.ValueSchema.Component<>(RecipeProviderDiagnostic.class, "sourceId", RecipeProviderDiagnostic::sourceId),
                    new dev.openallay.value.ValueSchema.Component<>(RecipeProviderDiagnostic.class, "code", RecipeProviderDiagnostic::code),
                    new dev.openallay.value.ValueSchema.Component<>(RecipeProviderDiagnostic.class, "message", RecipeProviderDiagnostic::message)), arguments -> new RecipeProviderDiagnostic((String) arguments[0], (String) arguments[1], (String) arguments[2]));
        }
    }
}
