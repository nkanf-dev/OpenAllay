package dev.openallay.recipe;

@dev.openallay.value.ValueType(RecipeNavigationResult.ValueSchemaProvider.class)
public final class RecipeNavigationResult {
    private final boolean opened;
    private final String code;
    private final String message;
    public RecipeNavigationResult(boolean opened, String code, String message) {

        if (code == null || !code.matches("[a-z0-9_]+") || message == null || dev.openallay.util.Java8Strings.isBlank(message)) {
            throw new IllegalArgumentException("recipe navigation result is invalid");
        }

        this.opened = opened;
        this.code = code;
        this.message = message;
    }
    public boolean opened() { return opened; }
    public String code() { return code; }
    public String message() { return message; }
public static RecipeNavigationResult success() {
        return new RecipeNavigationResult(true, "opened", "Recipe viewer opened");
    }
public static RecipeNavigationResult failed(String code, String message) {
        return new RecipeNavigationResult(false, code, message);
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RecipeNavigationResult)) return false;
        RecipeNavigationResult that = (RecipeNavigationResult) other;
        return opened == that.opened && java.util.Objects.equals(code, that.code) && java.util.Objects.equals(message, that.message);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Boolean.hashCode(opened);
        hash = 31 * hash + java.util.Objects.hashCode(code);
        hash = 31 * hash + java.util.Objects.hashCode(message);
        return hash;
    }
    @Override public String toString() { return "RecipeNavigationResult[opened=" + opened + ", code=" + code + ", message=" + message + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<RecipeNavigationResult> schema() {
            return new dev.openallay.value.ValueSchema<>(RecipeNavigationResult.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<RecipeNavigationResult>>asList(new dev.openallay.value.ValueSchema.Component<>(RecipeNavigationResult.class, "opened", RecipeNavigationResult::opened), new dev.openallay.value.ValueSchema.Component<>(RecipeNavigationResult.class, "code", RecipeNavigationResult::code), new dev.openallay.value.ValueSchema.Component<>(RecipeNavigationResult.class, "message", RecipeNavigationResult::message)), arguments -> new RecipeNavigationResult((Boolean) arguments[0], (String) arguments[1], (String) arguments[2]));
        }
    }
}
