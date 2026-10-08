package dev.openallay.extension.catalog;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Strict schema-2 community catalog for loader-specific Extension JARs. */
@dev.openallay.value.ValueType(ExtensionCatalogManifest.ValueSchemaProvider.class)
public final class ExtensionCatalogManifest {
    private final int schemaVersion;
    private final String kind;
    private final Instant generatedAt;
    private final List<ExtensionCatalogEntry> extensions;
    public ExtensionCatalogManifest(int schemaVersion, String kind, Instant generatedAt, List<ExtensionCatalogEntry> extensions) {

        if (schemaVersion != SCHEMA_VERSION || !"extension".equals(kind)) {
            throw new IllegalArgumentException("Unsupported Extension catalog schema or kind");
        }
        Objects.requireNonNull(generatedAt, "generatedAt");
        extensions = dev.openallay.util.Java8Collections.toList(dev.openallay.util.Java8Collections.listCopyOf(extensions).stream()
                .sorted(Comparator.comparing(ExtensionCatalogEntry::id)
                        .thenComparing(ExtensionCatalogEntry::version)));
        HashSet<String> identities = new HashSet<>();
        for (ExtensionCatalogEntry extension : extensions) {
            if (!identities.add(extension.id() + "\0" + extension.version())) {
                throw new IllegalArgumentException("Duplicate Extension package identity");
            }
        }

        this.schemaVersion = schemaVersion;
        this.kind = kind;
        this.generatedAt = generatedAt;
        this.extensions = extensions;
    }
    public int schemaVersion() { return schemaVersion; }
    public String kind() { return kind; }
    public Instant generatedAt() { return generatedAt; }
    public List<ExtensionCatalogEntry> extensions() { return extensions; }
public static final int SCHEMA_VERSION = 2;
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ExtensionCatalogManifest)) return false;
        ExtensionCatalogManifest that = (ExtensionCatalogManifest) other;
        return schemaVersion == that.schemaVersion && java.util.Objects.equals(kind, that.kind) && java.util.Objects.equals(generatedAt, that.generatedAt) && java.util.Objects.equals(extensions, that.extensions);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Integer.hashCode(schemaVersion);
        hash = 31 * hash + java.util.Objects.hashCode(kind);
        hash = 31 * hash + java.util.Objects.hashCode(generatedAt);
        hash = 31 * hash + java.util.Objects.hashCode(extensions);
        return hash;
    }
    @Override public String toString() { return "ExtensionCatalogManifest[schemaVersion=" + schemaVersion + ", kind=" + kind + ", generatedAt=" + generatedAt + ", extensions=" + extensions + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ExtensionCatalogManifest> schema() {
            return new dev.openallay.value.ValueSchema<>(ExtensionCatalogManifest.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ExtensionCatalogManifest>>asList(new dev.openallay.value.ValueSchema.Component<>(ExtensionCatalogManifest.class, "schemaVersion", ExtensionCatalogManifest::schemaVersion), new dev.openallay.value.ValueSchema.Component<>(ExtensionCatalogManifest.class, "kind", ExtensionCatalogManifest::kind), new dev.openallay.value.ValueSchema.Component<>(ExtensionCatalogManifest.class, "generatedAt", ExtensionCatalogManifest::generatedAt), new dev.openallay.value.ValueSchema.Component<>(ExtensionCatalogManifest.class, "extensions", ExtensionCatalogManifest::extensions)), arguments -> new ExtensionCatalogManifest((Integer) arguments[0], (String) arguments[1], (Instant) arguments[2], (List) arguments[3]));
        }
    }
}
