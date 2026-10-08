package dev.openallay.guide;

@dev.openallay.value.ValueType(GuideFailure.ValueSchemaProvider.class)
public final class GuideFailure {
    private final String code;
    private final String message;
    public GuideFailure(String code, String message) {

        if (code == null || dev.openallay.util.Java8Strings.isBlank(code) || message == null || dev.openallay.util.Java8Strings.isBlank(message)) {
            throw new IllegalArgumentException("failure code and message are required");
        }

        this.code = code;
        this.message = message;
    }
    public String code() { return code; }
    public String message() { return message; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GuideFailure)) return false;
        GuideFailure that = (GuideFailure) other;
        return java.util.Objects.equals(code, that.code) && java.util.Objects.equals(message, that.message);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(code);
        hash = 31 * hash + java.util.Objects.hashCode(message);
        return hash;
    }
    @Override public String toString() { return "GuideFailure[code=" + code + ", message=" + message + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<GuideFailure> schema() {
            return new dev.openallay.value.ValueSchema<>(GuideFailure.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<GuideFailure>>asList(new dev.openallay.value.ValueSchema.Component<>(GuideFailure.class, "code", GuideFailure::code), new dev.openallay.value.ValueSchema.Component<>(GuideFailure.class, "message", GuideFailure::message)), arguments -> new GuideFailure((String) arguments[0], (String) arguments[1]));
        }
    }
}
