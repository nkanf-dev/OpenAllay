package dev.openallay.guide.history;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@dev.openallay.value.ValueType(GuideHistoryScope.ValueSchemaProvider.class)
public final class GuideHistoryScope {
    private final UUID actorId;
    private final Kind kind;
    private final String scopeId;
    public GuideHistoryScope(UUID actorId, Kind kind, String scopeId) {

        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(kind, "kind");
        if (scopeId == null || !scopeId.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("scopeId must be a lowercase SHA-256 digest");
        }

        this.actorId = actorId;
        this.kind = kind;
        this.scopeId = scopeId;
    }
    public UUID actorId() { return actorId; }
    public Kind kind() { return kind; }
    public String scopeId() { return scopeId; }
public enum Kind {
        SINGLEPLAYER,
        MULTIPLAYER
    }
public static GuideHistoryScope derive(UUID actorId, Kind kind, String discriminator) {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(kind, "kind");
        if (discriminator == null || discriminator.isBlank()) {
            throw new IllegalArgumentException("history discriminator must not be blank");
        }
        String normalized = switch (kind) {
            case SINGLEPLAYER -> Path.of(discriminator.trim())
                    .toAbsolutePath()
                    .normalize()
                    .toString();
            case MULTIPLAYER -> discriminator.trim().toLowerCase(Locale.ROOT);
        };
        String material = actorId + "\0" + kind.name() + "\0" + normalized;
        return new GuideHistoryScope(actorId, kind, digest(material));
    }
private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("JDK does not provide SHA-256", impossible);
        }
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GuideHistoryScope)) return false;
        GuideHistoryScope that = (GuideHistoryScope) other;
        return java.util.Objects.equals(actorId, that.actorId) && java.util.Objects.equals(kind, that.kind) && java.util.Objects.equals(scopeId, that.scopeId);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(actorId);
        hash = 31 * hash + java.util.Objects.hashCode(kind);
        hash = 31 * hash + java.util.Objects.hashCode(scopeId);
        return hash;
    }
    @Override public String toString() { return "GuideHistoryScope[actorId=" + actorId + ", kind=" + kind + ", scopeId=" + scopeId + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<GuideHistoryScope> schema() {
            return new dev.openallay.value.ValueSchema<>(GuideHistoryScope.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<GuideHistoryScope>>asList(new dev.openallay.value.ValueSchema.Component<>(GuideHistoryScope.class, "actorId", GuideHistoryScope::actorId), new dev.openallay.value.ValueSchema.Component<>(GuideHistoryScope.class, "kind", GuideHistoryScope::kind), new dev.openallay.value.ValueSchema.Component<>(GuideHistoryScope.class, "scopeId", GuideHistoryScope::scopeId)), arguments -> new GuideHistoryScope((UUID) arguments[0], (Kind) arguments[1], (String) arguments[2]));
        }
    }
}
