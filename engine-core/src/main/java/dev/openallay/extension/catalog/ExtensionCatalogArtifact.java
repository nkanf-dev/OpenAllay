package dev.openallay.extension.catalog;

import java.net.URI;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** One loader-specific JAR belonging to an Extension catalog version. */
@dev.openallay.value.ValueType(ExtensionCatalogArtifact.ValueSchemaProvider.class)
public final class ExtensionCatalogArtifact {
    private final String loader;
    private final URI artifact;
    private final String sha256;
    private final Set<String> modIds;
    public ExtensionCatalogArtifact(String loader, URI artifact, String sha256, Set<String> modIds) {

        if (loader == null || loader.isBlank()) {
            throw new IllegalArgumentException("Extension artifact loader is required");
        }
        loader = loader.strip().toLowerCase(java.util.Locale.ROOT);
        if (!Set.of("fabric", "neoforge").contains(loader)) {
            throw new IllegalArgumentException(
                    "Unsupported Extension artifact loader: " + loader);
        }
        artifact = secureUri(artifact);
        if (sha256 == null || !SHA256.matcher(sha256).matches()) {
            throw new IllegalArgumentException(
                    "Extension SHA-256 must be 64 lowercase hex digits");
        }
        TreeSet<String> normalizedModIds = new TreeSet<>();
        for (String modId : Set.copyOf(modIds)) {
            if (modId == null || !MOD_ID.matcher(modId).matches()) {
                throw new IllegalArgumentException("Invalid Extension mod ID: " + modId);
            }
            normalizedModIds.add(modId);
        }
        if (normalizedModIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "Extension artifact must declare at least one mod ID");
        }
        modIds = Set.copyOf(normalizedModIds);

        this.loader = loader;
        this.artifact = artifact;
        this.sha256 = sha256;
        this.modIds = modIds;
    }
    public String loader() { return loader; }
    public URI artifact() { return artifact; }
    public String sha256() { return sha256; }
    public Set<String> modIds() { return modIds; }
private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
private static final Pattern MOD_ID = Pattern.compile("[a-z0-9_.-]+");
public ExtensionCatalogArtifact(
            String loader, String artifact, String sha256, Set<String> modIds) {
        this(loader, URI.create(artifact), sha256, modIds);
    }
private static URI secureUri(URI uri) {
        java.util.Objects.requireNonNull(uri, "artifact");
        String host = uri.getHost();
        boolean loopback = host != null
                && (host.equalsIgnoreCase("localhost")
                        || host.equals("127.0.0.1")
                        || host.equals("::1"));
        if (!"https".equalsIgnoreCase(uri.getScheme())
                && !("http".equalsIgnoreCase(uri.getScheme()) && loopback)) {
            throw new IllegalArgumentException(
                    "Extension artifact URI must use HTTPS or loopback HTTP");
        }
        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException(
                    "Extension artifact URI must not contain credentials");
        }
        return uri;
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ExtensionCatalogArtifact)) return false;
        ExtensionCatalogArtifact that = (ExtensionCatalogArtifact) other;
        return java.util.Objects.equals(loader, that.loader) && java.util.Objects.equals(artifact, that.artifact) && java.util.Objects.equals(sha256, that.sha256) && java.util.Objects.equals(modIds, that.modIds);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(loader);
        hash = 31 * hash + java.util.Objects.hashCode(artifact);
        hash = 31 * hash + java.util.Objects.hashCode(sha256);
        hash = 31 * hash + java.util.Objects.hashCode(modIds);
        return hash;
    }
    @Override public String toString() { return "ExtensionCatalogArtifact[loader=" + loader + ", artifact=" + artifact + ", sha256=" + sha256 + ", modIds=" + modIds + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ExtensionCatalogArtifact> schema() {
            return new dev.openallay.value.ValueSchema<>(ExtensionCatalogArtifact.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ExtensionCatalogArtifact>>asList(new dev.openallay.value.ValueSchema.Component<>(ExtensionCatalogArtifact.class, "loader", ExtensionCatalogArtifact::loader), new dev.openallay.value.ValueSchema.Component<>(ExtensionCatalogArtifact.class, "artifact", ExtensionCatalogArtifact::artifact), new dev.openallay.value.ValueSchema.Component<>(ExtensionCatalogArtifact.class, "sha256", ExtensionCatalogArtifact::sha256), new dev.openallay.value.ValueSchema.Component<>(ExtensionCatalogArtifact.class, "modIds", ExtensionCatalogArtifact::modIds)), arguments -> new ExtensionCatalogArtifact((String) arguments[0], (URI) arguments[1], (String) arguments[2], (Set) arguments[3]));
        }
    }
}
