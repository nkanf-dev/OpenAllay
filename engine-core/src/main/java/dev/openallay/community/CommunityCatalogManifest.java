package dev.openallay.community;

import dev.openallay.extension.ExtensionCompatibility;
import java.net.URI;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** Canonical schema-2 manifest for one configuration-layer community catalog. */
@dev.openallay.value.ValueType(CommunityCatalogManifest.ValueSchemaProvider.class)
public final class CommunityCatalogManifest {
    private final int schemaVersion;
    private final String kind;
    private final Instant generatedAt;
    private final List<PackageEntry> packages;
    public CommunityCatalogManifest(int schemaVersion, String kind, Instant generatedAt, List<PackageEntry> packages) {

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

        this.schemaVersion = schemaVersion;
        this.kind = kind;
        this.generatedAt = generatedAt;
        this.packages = packages;
    }
    public int schemaVersion() { return schemaVersion; }
    public String kind() { return kind; }
    public Instant generatedAt() { return generatedAt; }
    public List<PackageEntry> packages() { return packages; }
public static final int SCHEMA_VERSION = 2;
@dev.openallay.value.ValueType(PackageEntry.ValueSchemaProvider.class)
public static final class PackageEntry {
    private final String id;
    private final String displayName;
    private final String description;
    private final String publisher;
    private final String version;
    private final URI archive;
    private final String sha256;
    private final Compatibility compatibility;
    private final URI source;
    public PackageEntry(String id, String displayName, String description, String publisher, String version, URI archive, String sha256, Compatibility compatibility, URI source) {

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

        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.publisher = publisher;
        this.version = version;
        this.archive = archive;
        this.sha256 = sha256;
        this.compatibility = compatibility;
        this.source = source;
    }
    public String id() { return id; }
    public String displayName() { return displayName; }
    public String description() { return description; }
    public String publisher() { return publisher; }
    public String version() { return version; }
    public URI archive() { return archive; }
    public String sha256() { return sha256; }
    public Compatibility compatibility() { return compatibility; }
    public URI source() { return source; }
private static final Pattern ID = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PackageEntry)) return false;
        PackageEntry that = (PackageEntry) other;
        return java.util.Objects.equals(id, that.id) && java.util.Objects.equals(displayName, that.displayName) && java.util.Objects.equals(description, that.description) && java.util.Objects.equals(publisher, that.publisher) && java.util.Objects.equals(version, that.version) && java.util.Objects.equals(archive, that.archive) && java.util.Objects.equals(sha256, that.sha256) && java.util.Objects.equals(compatibility, that.compatibility) && java.util.Objects.equals(source, that.source);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(displayName);
        hash = 31 * hash + java.util.Objects.hashCode(description);
        hash = 31 * hash + java.util.Objects.hashCode(publisher);
        hash = 31 * hash + java.util.Objects.hashCode(version);
        hash = 31 * hash + java.util.Objects.hashCode(archive);
        hash = 31 * hash + java.util.Objects.hashCode(sha256);
        hash = 31 * hash + java.util.Objects.hashCode(compatibility);
        hash = 31 * hash + java.util.Objects.hashCode(source);
        return hash;
    }
    @Override public String toString() { return "PackageEntry[id=" + id + ", displayName=" + displayName + ", description=" + description + ", publisher=" + publisher + ", version=" + version + ", archive=" + archive + ", sha256=" + sha256 + ", compatibility=" + compatibility + ", source=" + source + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<PackageEntry> schema() {
            return new dev.openallay.value.ValueSchema<>(PackageEntry.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<PackageEntry>>asList(new dev.openallay.value.ValueSchema.Component<>(PackageEntry.class, "id", PackageEntry::id), new dev.openallay.value.ValueSchema.Component<>(PackageEntry.class, "displayName", PackageEntry::displayName), new dev.openallay.value.ValueSchema.Component<>(PackageEntry.class, "description", PackageEntry::description), new dev.openallay.value.ValueSchema.Component<>(PackageEntry.class, "publisher", PackageEntry::publisher), new dev.openallay.value.ValueSchema.Component<>(PackageEntry.class, "version", PackageEntry::version), new dev.openallay.value.ValueSchema.Component<>(PackageEntry.class, "archive", PackageEntry::archive), new dev.openallay.value.ValueSchema.Component<>(PackageEntry.class, "sha256", PackageEntry::sha256), new dev.openallay.value.ValueSchema.Component<>(PackageEntry.class, "compatibility", PackageEntry::compatibility), new dev.openallay.value.ValueSchema.Component<>(PackageEntry.class, "source", PackageEntry::source)), arguments -> new PackageEntry((String) arguments[0], (String) arguments[1], (String) arguments[2], (String) arguments[3], (String) arguments[4], (URI) arguments[5], (String) arguments[6], (Compatibility) arguments[7], (URI) arguments[8]));
        }
    }
}
@dev.openallay.value.ValueType(Compatibility.ValueSchemaProvider.class)
public static final class Compatibility {
    private final String minecraft;
    private final String openallayApi;
    public Compatibility(String minecraft, String openallayApi) {

            if (minecraft == null || minecraft.isBlank()
                    || openallayApi == null || openallayApi.isBlank()) {
                throw new IllegalArgumentException("Community package compatibility is required");
            }
            minecraft = ExtensionCompatibility.requireRange(minecraft, "minecraft");

        this.minecraft = minecraft;
        this.openallayApi = openallayApi;
    }
    public String minecraft() { return minecraft; }
    public String openallayApi() { return openallayApi; }
public boolean supports(String minecraftVersion, String skillApiVersion) {
            return ExtensionCompatibility.includes(minecraft, minecraftVersion)
                    && openallayApi.equals(skillApiVersion);
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Compatibility)) return false;
        Compatibility that = (Compatibility) other;
        return java.util.Objects.equals(minecraft, that.minecraft) && java.util.Objects.equals(openallayApi, that.openallayApi);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(minecraft);
        hash = 31 * hash + java.util.Objects.hashCode(openallayApi);
        return hash;
    }
    @Override public String toString() { return "Compatibility[minecraft=" + minecraft + ", openallayApi=" + openallayApi + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Compatibility> schema() {
            return new dev.openallay.value.ValueSchema<>(Compatibility.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Compatibility>>asList(new dev.openallay.value.ValueSchema.Component<>(Compatibility.class, "minecraft", Compatibility::minecraft), new dev.openallay.value.ValueSchema.Component<>(Compatibility.class, "openallayApi", Compatibility::openallayApi)), arguments -> new Compatibility((String) arguments[0], (String) arguments[1]));
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
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CommunityCatalogManifest)) return false;
        CommunityCatalogManifest that = (CommunityCatalogManifest) other;
        return schemaVersion == that.schemaVersion && java.util.Objects.equals(kind, that.kind) && java.util.Objects.equals(generatedAt, that.generatedAt) && java.util.Objects.equals(packages, that.packages);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Integer.hashCode(schemaVersion);
        hash = 31 * hash + java.util.Objects.hashCode(kind);
        hash = 31 * hash + java.util.Objects.hashCode(generatedAt);
        hash = 31 * hash + java.util.Objects.hashCode(packages);
        return hash;
    }
    @Override public String toString() { return "CommunityCatalogManifest[schemaVersion=" + schemaVersion + ", kind=" + kind + ", generatedAt=" + generatedAt + ", packages=" + packages + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<CommunityCatalogManifest> schema() {
            return new dev.openallay.value.ValueSchema<>(CommunityCatalogManifest.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<CommunityCatalogManifest>>asList(new dev.openallay.value.ValueSchema.Component<>(CommunityCatalogManifest.class, "schemaVersion", CommunityCatalogManifest::schemaVersion), new dev.openallay.value.ValueSchema.Component<>(CommunityCatalogManifest.class, "kind", CommunityCatalogManifest::kind), new dev.openallay.value.ValueSchema.Component<>(CommunityCatalogManifest.class, "generatedAt", CommunityCatalogManifest::generatedAt), new dev.openallay.value.ValueSchema.Component<>(CommunityCatalogManifest.class, "packages", CommunityCatalogManifest::packages)), arguments -> new CommunityCatalogManifest((Integer) arguments[0], (String) arguments[1], (Instant) arguments[2], (List) arguments[3]));
        }
    }
}
