package dev.openallay.settings;

import java.util.Objects;

/** One foreground settings action; provider reads are explicitly cancellable. */
@dev.openallay.value.ValueType(SettingsOperation.ValueSchemaProvider.class)
public final class SettingsOperation {
    private final Kind kind;
    private final String targetId;
    private final boolean cancellable;
    public SettingsOperation(Kind kind, String targetId, boolean cancellable) {

        Objects.requireNonNull(kind, "kind");
        if (kind == Kind.IDLE && (targetId != null || cancellable)) {
            throw new IllegalArgumentException("idle settings operation has no target or cancellation");
        }
        if (cancellable != (kind == Kind.TESTING_CONNECTION
                || kind == Kind.FETCHING_MODEL_CATALOG)) {
            throw new IllegalArgumentException("only provider reads are cancellable");
        }
        if (targetId != null && dev.openallay.util.Java8Strings.isBlank(targetId)) {
            throw new IllegalArgumentException("targetId must be null or nonblank");
        }

        this.kind = kind;
        this.targetId = targetId;
        this.cancellable = cancellable;
    }
    public Kind kind() { return kind; }
    public String targetId() { return targetId; }
    public boolean cancellable() { return cancellable; }
public enum Kind {
        IDLE,
        SAVING_MODELS,
        RELOADING_MODELS,
        SAVING_CAPABILITIES,
        RELOADING_CAPABILITIES,
        SAVING_RECIPES,
        RELOADING_RECIPES,
        SAVING_SKILL,
        DELETING_SKILL_OVERRIDE,
        RELOADING_SKILLS,
        REFRESHING_SKILL_CATALOG,
        INSTALLING_COMMUNITY_SKILL,
        IMPORTING_SKILL_PACKAGE,
        REFRESHING_EXTENSION_CATALOG,
        INSTALLING_COMMUNITY_EXTENSION,
        IMPORTING_EXTENSION_PACKAGE,
        SAVING_DISPLAY,
        RELOADING_DISPLAY,
        SAVING_EXPERIMENTAL_COMMANDS,
        RELOADING_EXPERIMENTAL_COMMANDS,
        SAVING_UNRESTRICTED_JAVASCRIPT,
        RELOADING_UNRESTRICTED_JAVASCRIPT,
        DELETING_CURRENT_HISTORY,
        DELETING_ACTOR_HISTORY,
        RESETTING_HISTORY_DATABASE,
        REFRESHING_METADATA,
        FETCHING_MODEL_CATALOG,
        TESTING_CONNECTION
    }
public static SettingsOperation idle() {
        return new SettingsOperation(Kind.IDLE, null, false);
    }
public static SettingsOperation models(Kind kind) {
        return new SettingsOperation(kind, null, false);
    }
public static SettingsOperation probe(String profileId) {
        return new SettingsOperation(Kind.TESTING_CONNECTION, profileId, true);
    }
public static SettingsOperation catalog(String profileId) {
        return new SettingsOperation(Kind.FETCHING_MODEL_CATALOG, profileId, true);
    }
public static SettingsOperation domain(Kind kind) {
        if (kind == Kind.IDLE || kind == Kind.TESTING_CONNECTION
                || kind == Kind.FETCHING_MODEL_CATALOG) {
            throw new IllegalArgumentException("domain operation must be a non-cancellable mutation");
        }
        return new SettingsOperation(kind, null, false);
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SettingsOperation)) return false;
        SettingsOperation that = (SettingsOperation) other;
        return java.util.Objects.equals(kind, that.kind) && java.util.Objects.equals(targetId, that.targetId) && cancellable == that.cancellable;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(kind);
        hash = 31 * hash + java.util.Objects.hashCode(targetId);
        hash = 31 * hash + Boolean.hashCode(cancellable);
        return hash;
    }
    @Override public String toString() { return "SettingsOperation[kind=" + kind + ", targetId=" + targetId + ", cancellable=" + cancellable + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<SettingsOperation> schema() {
            return new dev.openallay.value.ValueSchema<>(SettingsOperation.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<SettingsOperation>>asList(new dev.openallay.value.ValueSchema.Component<>(SettingsOperation.class, "kind", SettingsOperation::kind), new dev.openallay.value.ValueSchema.Component<>(SettingsOperation.class, "targetId", SettingsOperation::targetId), new dev.openallay.value.ValueSchema.Component<>(SettingsOperation.class, "cancellable", SettingsOperation::cancellable)), arguments -> new SettingsOperation((Kind) arguments[0], (String) arguments[1], (Boolean) arguments[2]));
        }
    }
}
