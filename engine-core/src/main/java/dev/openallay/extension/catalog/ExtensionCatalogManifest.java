package dev.openallay.extension.catalog;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Strict schema-2 community catalog for loader-specific Extension JARs. */
public record ExtensionCatalogManifest(
        int schemaVersion,
        String kind,
        Instant generatedAt,
        List<ExtensionCatalogEntry> extensions) {
    public static final int SCHEMA_VERSION = 2;

    public ExtensionCatalogManifest {
        if (schemaVersion != SCHEMA_VERSION || !"extension".equals(kind)) {
            throw new IllegalArgumentException("Unsupported Extension catalog schema or kind");
        }
        Objects.requireNonNull(generatedAt, "generatedAt");
        extensions = List.copyOf(extensions).stream()
                .sorted(Comparator.comparing(ExtensionCatalogEntry::id)
                        .thenComparing(ExtensionCatalogEntry::version))
                .toList();
        HashSet<String> identities = new HashSet<>();
        for (ExtensionCatalogEntry extension : extensions) {
            if (!identities.add(extension.id() + "\0" + extension.version())) {
                throw new IllegalArgumentException("Duplicate Extension package identity");
            }
        }
    }
}
