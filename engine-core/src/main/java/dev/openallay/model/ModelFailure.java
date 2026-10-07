package dev.openallay.model;

@dev.openallay.value.ValueType(ModelFailure.ValueSchemaProvider.class)
public final class ModelFailure implements ModelEvent {
    private final String code;
    private final String message;
    private final Integer httpStatus;
    public ModelFailure(String code, String message, Integer httpStatus) {

        if (code == null || dev.openallay.util.Java8Strings.isBlank(code) || message == null || dev.openallay.util.Java8Strings.isBlank(message)) {
            throw new IllegalArgumentException("Model failure code and message are required");
        }

        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
    public String code() { return code; }
    public String message() { return message; }
    public Integer httpStatus() { return httpStatus; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ModelFailure)) return false;
        ModelFailure that = (ModelFailure) other;
        return java.util.Objects.equals(code, that.code) && java.util.Objects.equals(message, that.message) && java.util.Objects.equals(httpStatus, that.httpStatus);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(code);
        hash = 31 * hash + java.util.Objects.hashCode(message);
        hash = 31 * hash + java.util.Objects.hashCode(httpStatus);
        return hash;
    }
    @Override public String toString() { return "ModelFailure[code=" + code + ", message=" + message + ", httpStatus=" + httpStatus + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ModelFailure> schema() {
            return new dev.openallay.value.ValueSchema<>(ModelFailure.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ModelFailure>>asList(new dev.openallay.value.ValueSchema.Component<>(ModelFailure.class, "code", ModelFailure::code), new dev.openallay.value.ValueSchema.Component<>(ModelFailure.class, "message", ModelFailure::message), new dev.openallay.value.ValueSchema.Component<>(ModelFailure.class, "httpStatus", ModelFailure::httpStatus)), arguments -> new ModelFailure((String) arguments[0], (String) arguments[1], (Integer) arguments[2]));
        }
    }
}
