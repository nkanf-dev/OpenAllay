package dev.openallay.guide;

@dev.openallay.value.ValueType(GuideNotice.ValueSchemaProvider.class)
public final class GuideNotice {
    private final Level level;
    private final String message;
    public GuideNotice(Level level, String message) {

        java.util.Objects.requireNonNull(level, "level");
        if (message == null || dev.openallay.util.Java8Strings.isBlank(message)) {
            throw new IllegalArgumentException("notice message must not be blank");
        }

        this.level = level;
        this.message = message;
    }
    public Level level() { return level; }
    public String message() { return message; }
public enum Level { INFO, ERROR }
public static GuideNotice info(String message) {
        return new GuideNotice(Level.INFO, message);
    }
public static GuideNotice error(String message) {
        return new GuideNotice(Level.ERROR, message);
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GuideNotice)) return false;
        GuideNotice that = (GuideNotice) other;
        return java.util.Objects.equals(level, that.level) && java.util.Objects.equals(message, that.message);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(level);
        hash = 31 * hash + java.util.Objects.hashCode(message);
        return hash;
    }
    @Override public String toString() { return "GuideNotice[level=" + level + ", message=" + message + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<GuideNotice> schema() {
            return new dev.openallay.value.ValueSchema<>(GuideNotice.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<GuideNotice>>asList(new dev.openallay.value.ValueSchema.Component<>(GuideNotice.class, "level", GuideNotice::level), new dev.openallay.value.ValueSchema.Component<>(GuideNotice.class, "message", GuideNotice::message)), arguments -> new GuideNotice((Level) arguments[0], (String) arguments[1]));
        }
    }
}
