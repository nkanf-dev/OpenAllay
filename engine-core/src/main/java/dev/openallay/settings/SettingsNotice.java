package dev.openallay.settings;

/** Redacted terminal status suitable for native settings presentation. */
@dev.openallay.value.ValueType(SettingsNotice.ValueSchemaProvider.class)
public final class SettingsNotice {
    private final Level level;
    private final String code;
    private final String message;
    public SettingsNotice(Level level, String code, String message) {

        if (level == null || code == null || code.isBlank() || message == null || message.isBlank()) {
            throw new IllegalArgumentException("settings notice fields are required");
        }

        this.level = level;
        this.code = code;
        this.message = message;
    }
    public Level level() { return level; }
    public String code() { return code; }
    public String message() { return message; }
public enum Level {
        SUCCESS,
        FAILURE
    }
public static SettingsNotice success(String code, String message) {
        return new SettingsNotice(Level.SUCCESS, code, message);
    }
public static SettingsNotice failure(String code, String message) {
        return new SettingsNotice(Level.FAILURE, code, message);
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SettingsNotice)) return false;
        SettingsNotice that = (SettingsNotice) other;
        return java.util.Objects.equals(level, that.level) && java.util.Objects.equals(code, that.code) && java.util.Objects.equals(message, that.message);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(level);
        hash = 31 * hash + java.util.Objects.hashCode(code);
        hash = 31 * hash + java.util.Objects.hashCode(message);
        return hash;
    }
    @Override public String toString() { return "SettingsNotice[level=" + level + ", code=" + code + ", message=" + message + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<SettingsNotice> schema() {
            return new dev.openallay.value.ValueSchema<>(SettingsNotice.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<SettingsNotice>>asList(new dev.openallay.value.ValueSchema.Component<>(SettingsNotice.class, "level", SettingsNotice::level), new dev.openallay.value.ValueSchema.Component<>(SettingsNotice.class, "code", SettingsNotice::code), new dev.openallay.value.ValueSchema.Component<>(SettingsNotice.class, "message", SettingsNotice::message)), arguments -> new SettingsNotice((Level) arguments[0], (String) arguments[1], (String) arguments[2]));
        }
    }
}
