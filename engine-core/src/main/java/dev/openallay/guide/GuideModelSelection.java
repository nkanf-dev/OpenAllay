package dev.openallay.guide;

/** Closed, credential-free model choice stored per guide session and request. */
@dev.openallay.value.ValueType(GuideModelSelection.ValueSchemaProvider.class)
public final class GuideModelSelection {
    private final Kind kind;
    private final String profileId;
    public GuideModelSelection(Kind kind, String profileId) {

        java.util.Objects.requireNonNull(kind, "kind");
        if (kind == Kind.CLIENT) {
            if (profileId == null || !profileId.matches("[a-zA-Z0-9_.-]+")) {
                throw new IllegalArgumentException("client selection requires a valid profileId");
            }
        } else if (profileId != null) {
            throw new IllegalArgumentException("server selection cannot contain a profileId");
        }

        this.kind = kind;
        this.profileId = profileId;
    }
    public Kind kind() { return kind; }
    public String profileId() { return profileId; }
public enum Kind { CLIENT, SERVER }
public static GuideModelSelection client(String profileId) {
        return new GuideModelSelection(Kind.CLIENT, profileId);
    }
public static GuideModelSelection server() {
        return new GuideModelSelection(Kind.SERVER, null);
    }
public GuideModelMode modelMode() {
        return kind == Kind.CLIENT ? GuideModelMode.CLIENT : GuideModelMode.SERVER;
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GuideModelSelection)) return false;
        GuideModelSelection that = (GuideModelSelection) other;
        return java.util.Objects.equals(kind, that.kind) && java.util.Objects.equals(profileId, that.profileId);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(kind);
        hash = 31 * hash + java.util.Objects.hashCode(profileId);
        return hash;
    }
    @Override public String toString() { return "GuideModelSelection[kind=" + kind + ", profileId=" + profileId + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<GuideModelSelection> schema() {
            return new dev.openallay.value.ValueSchema<>(GuideModelSelection.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<GuideModelSelection>>asList(new dev.openallay.value.ValueSchema.Component<>(GuideModelSelection.class, "kind", GuideModelSelection::kind), new dev.openallay.value.ValueSchema.Component<>(GuideModelSelection.class, "profileId", GuideModelSelection::profileId)), arguments -> new GuideModelSelection((Kind) arguments[0], (String) arguments[1]));
        }
    }
}
