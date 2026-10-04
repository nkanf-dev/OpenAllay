package dev.openallay.extension.catalog;

import java.net.URI;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** One loader-specific JAR belonging to an Extension catalog version. */
public record ExtensionCatalogArtifact(
        String loader,
        URI artifact,
        String sha256,
        Set<String> modIds) {
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern MOD_ID = Pattern.compile("[a-z0-9_.-]+");

    public ExtensionCatalogArtifact {
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
    }

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
}
