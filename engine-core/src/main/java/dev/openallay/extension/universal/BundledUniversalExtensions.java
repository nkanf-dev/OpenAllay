package dev.openallay.extension.universal;

import dev.openallay.api.extension.ExtensionHost;
import dev.openallay.extension.OpenAllayExtensionRegistry;
import dev.openallay.extension.OpenAllayExtensionState;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.jar.JarFile;

/** Startup-only custody for a core-bundled universal package; never rewrites community JARs. */
public final class BundledUniversalExtensions implements AutoCloseable {
    public static final String PROVENANCE = "META-INF/openallay/distribution.json";
    private static final String RESOURCE_ROOT = "META-INF/openallay/bundled-extensions/";
    private final UniversalExtensionDiscovery discovery;
    private final List<UniversalExtensionDiscovery.DiscoveryResult> results;
    private final String artifactSha256;
    private boolean closed;

    @FunctionalInterface
    public interface Resources { InputStream open(String path) throws IOException; }

    private BundledUniversalExtensions(UniversalExtensionDiscovery discovery,
            List<UniversalExtensionDiscovery.DiscoveryResult> results, String digest) {
        this.discovery = discovery;
        this.results = dev.openallay.util.Java8Collections.listCopyOf(results);
        this.artifactSha256 = digest;
    }

    public static BundledUniversalExtensions open(Path cacheRoot, OpenAllayExtensionRegistry registry,
            ExtensionHost host, ClassLoader parent, Resources resources) throws IOException {
        Objects.requireNonNull(cacheRoot, "cacheRoot");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(parent, "parent");
        Objects.requireNonNull(resources, "resources");
        JsonObject provenance;
        try (InputStream input = resources.open(PROVENANCE)) {
            if (input == null) return new BundledUniversalExtensions(null, dev.openallay.util.Java8Collections.listOf(), "");
            provenance = object(UniversalExtensionJson.parse(new String(input.readAllBytes(), StandardCharsets.UTF_8)));
        }
        exact(provenance, dev.openallay.util.Java8Collections.setOf("source", "project", "version", "extensionId", "openAllayApiVersion", "artifact"));
        JsonObject source = object(provenance.get("source"));
        exact(source, dev.openallay.util.Java8Collections.setOf("repository", "revision", "dirty", "pinned"));
        text(source, "repository");
        if (!text(source, "revision").matches("[0-9a-f]{40}")) throw invalid();
        bool(source, "dirty"); bool(source, "pinned");
        text(provenance, "project"); text(provenance, "openAllayApiVersion");
        String id = text(provenance, "extensionId"), version = text(provenance, "version");
        JsonObject artifact = object(provenance.get("artifact"));
        exact(artifact, dev.openallay.util.Java8Collections.setOf("path", "sha256"));
        String path = text(artifact, "path"), digest = text(artifact, "sha256");
        if (!digest.matches("[0-9a-f]{64}") || !path.startsWith(RESOURCE_ROOT)
                || path.substring(RESOURCE_ROOT.length()).contains("/")
                || !path.substring(RESOURCE_ROOT.length()).matches("[A-Za-z0-9_.-]+\\.jar")) throw invalid();
        // A compatible community package has already been admitted by normal discovery.
        // It takes precedence; no cache or community file is written in this branch.
        if (registry.snapshot().extensions().stream().anyMatch(value -> value.state() == OpenAllayExtensionState.ACTIVE
                && value.descriptor().id().equals(id))) {
            return new BundledUniversalExtensions(null, dev.openallay.util.Java8Collections.listOf(new UniversalExtensionDiscovery.DiscoveryResult(
                    path, id, OpenAllayExtensionState.ACTIVE, "bundled_extension_overridden")), digest);
        }
        byte[] bytes;
        try (InputStream input = resources.open(path)) {
            if (input == null) throw new IOException("Bundled Extension resource is missing");
            bytes = input.readAllBytes();
        }
        if (!sha256(bytes).equals(digest)) throw new IOException("Bundled Extension checksum mismatch");
        Path root = cacheRoot.toAbsolutePath().normalize();
        ensureOwnedDirectory(root);
        Path directory = root.resolve(digest);
        ensureOwnedDirectory(directory);
        Path target = directory.resolve(path.substring(RESOURCE_ROOT.length()));
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(target) || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
                    || !sha256(Files.readAllBytes(target)).equals(digest)) {
                throw new IOException("Existing bundled Extension cache does not match its checksum");
            }
        } else {
            Path staged = Files.createTempFile(directory, ".bundled-", ".tmp");
            try {
                Files.write(staged, bytes);
                // A same-directory move publishes complete bytes without replacing an existing file.
                Files.move(staged, target);
            } finally { Files.deleteIfExists(staged); }
        }
        try (java.util.stream.Stream<java.nio.file.Path> files = Files.list(directory)) {
            if (files.anyMatch(file -> file.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".jar")
                    && !file.equals(target))) {
                throw new IOException("Bundled Extension cache contains an unexpected package");
            }
        }
        try (JarFile jar = new JarFile(target.toFile(), false)) {
            java.util.jar.JarEntry entry = jar.getJarEntry(UniversalExtensionManifest.JAR_PATH);
            if (entry == null) throw new IOException("Bundled Extension manifest is missing");
            UniversalExtensionManifest manifest;
            try (InputStream input = jar.getInputStream(entry)) {
                manifest = UniversalExtensionManifest.decode(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            }
            if (!manifest.descriptor().id().equals(id) || !manifest.descriptor().version().equals(version)) {
                throw new IOException("Bundled Extension provenance and declaration differ");
            }
        }
        UniversalExtensionDiscovery discovery = new UniversalExtensionDiscovery(directory, registry, host, parent);
        try {
            return new BundledUniversalExtensions(discovery, discovery.discover(), digest);
        } catch (RuntimeException | Error failure) { discovery.close(); throw failure; }
    }

    public List<UniversalExtensionDiscovery.DiscoveryResult> results() { return results; }
    public String artifactSha256() { return artifactSha256; }
    public synchronized boolean closed() { return closed; }
    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        if (discovery != null) discovery.close();
    }

    private static void ensureOwnedDirectory(Path directory) throws IOException {
        // Reject a redirected owned directory. System aliases such as macOS /var remain valid.
        if (Files.isSymbolicLink(directory)) throw new IOException("Bundled Extension cache path is a symlink");
        Files.createDirectories(directory);
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Bundled cache is not a directory");
    }
    private static String sha256(byte[] bytes) {
        try { return dev.openallay.util.Java8Hex.formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static JsonObject object(com.google.gson.JsonElement value) {
        if (value == null || !value.isJsonObject()) throw invalid();
        return value.getAsJsonObject();
    }
    private static void exact(JsonObject value, Set<String> keys) { if (!dev.openallay.json.JsonTrees.keys(value).equals(keys)) throw invalid(); }
    private static String text(JsonObject value, String key) {
        com.google.gson.JsonElement item = value.get(key);
        if (item == null || !item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()
                || dev.openallay.util.Java8Strings.isBlank(item.getAsString())) throw invalid();
        return item.getAsString();
    }
    private static boolean bool(JsonObject value, String key) {
        com.google.gson.JsonElement item = value.get(key);
        if (item == null || !item.isJsonPrimitive() || !item.getAsJsonPrimitive().isBoolean()) throw invalid();
        return item.getAsBoolean();
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Bundled Extension provenance shape is invalid"); }
}
