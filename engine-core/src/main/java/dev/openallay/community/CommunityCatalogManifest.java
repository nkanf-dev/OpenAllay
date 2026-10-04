package dev.openallay.community;

import java.net.URI;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** Canonical schema-2 manifest for one configuration-layer community catalog. */
public record CommunityCatalogManifest(
        int schemaVersion,
        String kind,
        Instant generatedAt,
        List<PackageEntry> packages) {
    public static final int SCHEMA_VERSION = 2;

    public CommunityCatalogManifest {
        if (schemaVersion != SCHEMA_VERSION || !"skill".equals(kind)) {
            throw new IllegalArgumentException("Unsupported community catalog schema or kind");
        }
        Objects.requireNonNull(generatedAt, "generatedAt");
        packages = List.copyOf(packages).stream()
                .sorted(Comparator.comparing(PackageEntry::id)
                        .thenComparing(PackageEntry::version))
                .toList();
        HashSet<String> identities = new HashSet<>();
        for (PackageEntry entry : packages) {
            if (!identities.add(entry.id() + "\0" + entry.version())) {
                throw new IllegalArgumentException("Duplicate community package identity");
            }
        }
    }

    public record PackageEntry(
            String id,
            String displayName,
            String description,
            String publisher,
            String version,
            URI archive,
            String sha256,
            Compatibility compatibility,
            URI source) {
        private static final Pattern ID = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
        private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

        public PackageEntry {
            if (id == null || !ID.matcher(id).matches()
                    || displayName == null || displayName.isBlank()
                    || description == null || description.isBlank()
                    || publisher == null || publisher.isBlank()
                    || version == null || version.isBlank()
                    || sha256 == null || !SHA256.matcher(sha256).matches()) {
                throw new IllegalArgumentException("Invalid community package identity");
            }
            archive = validateRemoteUri(archive, "archive");
            source = validateRemoteUri(source, "source");
            Objects.requireNonNull(compatibility, "compatibility");
        }
    }

    public record Compatibility(String minecraft, String openallayApi) {
        public Compatibility {
            if (minecraft == null || minecraft.isBlank()
                    || openallayApi == null || openallayApi.isBlank()) {
                throw new IllegalArgumentException("Community package compatibility is required");
            }
        }
    }

    public static URI validateRemoteUri(URI uri, String label) {
        Objects.requireNonNull(uri, label);
        String scheme = uri.getScheme();
        String host = uri.getHost();
        boolean loopback = host != null
                && (host.equalsIgnoreCase("localhost")
                        || host.equals("127.0.0.1")
                        || host.equals("::1"));
        if (!"https".equalsIgnoreCase(scheme)
                && !("http".equalsIgnoreCase(scheme) && loopback)) {
            throw new IllegalArgumentException(label + " URI must use HTTPS or loopback HTTP");
        }
        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException(label + " URI must not contain credentials");
        }
        return uri;
    }
}
