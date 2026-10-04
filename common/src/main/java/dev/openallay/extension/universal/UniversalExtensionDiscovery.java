package dev.openallay.extension.universal;

import dev.openallay.api.extension.ExtensionDescriptor;
import dev.openallay.api.extension.ExtensionHost;
import dev.openallay.api.extension.OpenAllayExtension;
import dev.openallay.extension.OpenAllayExtensionRegistry;
import dev.openallay.extension.OpenAllayExtensionState;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.jar.JarFile;

/** Single startup pass over the configured universal directory; accepted loaders live until shutdown. */
public final class UniversalExtensionDiscovery implements AutoCloseable {
    private final Path directory;
    private final OpenAllayExtensionRegistry registry;
    private final ExtensionHost host;
    private final ClassLoader parent;
    private final Limits limits;
    private final List<URLClassLoader> loaders = new ArrayList<>();
    private List<DiscoveryResult> results;
    private boolean closed;

    public UniversalExtensionDiscovery(Path directory, OpenAllayExtensionRegistry registry,
            ExtensionHost host, ClassLoader parent) {
        this(directory, registry, host, parent, Limits.DEFAULT);
    }

    public UniversalExtensionDiscovery(Path directory, OpenAllayExtensionRegistry registry,
            ExtensionHost host, ClassLoader parent, Limits limits) {
        this.directory = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
        this.registry = Objects.requireNonNull(registry, "registry");
        this.host = Objects.requireNonNull(host, "host");
        this.parent = Objects.requireNonNull(parent, "parent");
        this.limits = Objects.requireNonNull(limits, "limits");
        try {
            if (Class.forName(OpenAllayExtension.class.getName(), false, parent) != OpenAllayExtension.class) {
                throw new IllegalArgumentException("Parent must share the public SDK identity");
            }
        } catch (ClassNotFoundException absent) {
            throw new IllegalArgumentException("Parent must provide the public SDK");
        }
    }

    public synchronized List<DiscoveryResult> discover() {
        if (closed) throw new IllegalStateException("Extension discovery is closed");
        if (results != null) return results;
        List<DiscoveryResult> discovered = new ArrayList<>();
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            results = List.of();
            return results;
        }
        if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            results = List.of(rejected("", "", "extension_directory_invalid"));
            return results;
        }
        List<Path> jars;
        try (var files = Files.list(directory)) {
            jars = files.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString())).toList();
        } catch (IOException failure) {
            results = List.of(rejected("", "", "extension_directory_unavailable"));
            return results;
        }
        if (jars.size() > limits.maximumCandidates()) {
            results = List.of(rejected("", "", "extension_package_limit_exceeded"));
            return results;
        }
        List<Candidate> candidates = new ArrayList<>();
        Map<String, Integer> identities = new HashMap<>();
        for (Path jar : jars) {
            try {
                UniversalExtensionManifest manifest = inspect(jar);
                candidates.add(new Candidate(jar, manifest, ""));
                identities.merge(manifest.descriptor().id(), 1, Integer::sum);
            } catch (PackageFailure failure) {
                candidates.add(new Candidate(jar, null, failure.code));
            } catch (Throwable failure) {
                candidates.add(new Candidate(jar, null, "extension_package_invalid"));
            }
        }
        Set<String> existing = new HashSet<>();
        registry.snapshot().extensions().forEach(extension -> existing.add(extension.descriptor().id()));
        for (Candidate candidate : candidates) {
            String filename = candidate.path().getFileName().toString();
            if (candidate.manifest() == null) {
                discovered.add(rejected(filename, "", candidate.failure()));
                continue;
            }
            ExtensionDescriptor descriptor = candidate.manifest().descriptor();
            String id = descriptor.id();
            if (identities.get(id) > 1 || existing.contains(id)) {
                discovered.add(rejected(filename, id, "duplicate_extension_id"));
                continue;
            }
            String incompatibility = UniversalExtensionSupport.incompatibility(descriptor.support(), host.environment());
            if (!incompatibility.isEmpty()) {
                discovered.add(new DiscoveryResult(filename, id, OpenAllayExtensionState.INCOMPATIBLE, incompatibility));
                continue;
            }
            // Old registry must also accept the real API range before any class initialization.
            var target = UniversalExtensionSupport.matchingTarget(descriptor.support(), host.environment()).orElseThrow();
            String legacyIncompatibility = registry.environment().incompatibility(
                    UniversalExtensionSupport.legacyDescriptor(descriptor, target));
            if (!legacyIncompatibility.isEmpty()) {
                discovered.add(new DiscoveryResult(filename, id, OpenAllayExtensionState.INCOMPATIBLE,
                        legacyIncompatibility));
                continue;
            }
            discovered.add(load(candidate));
        }
        results = List.copyOf(discovered);
        return results;
    }

    private UniversalExtensionManifest inspect(Path path) throws IOException {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                || !path.toRealPath().getParent().equals(directory.toRealPath())) {
            throw new PackageFailure("extension_package_path_invalid");
        }
        if (Files.size(path) > limits.maximumJarBytes()) throw new PackageFailure("extension_package_limit_exceeded");
        String manifest = null;
        Set<String> names = new HashSet<>();
        long expanded = 0;
        int count = 0;
        try (JarFile jar = new JarFile(path.toFile(), false)) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                String name = entry.getName();
                if (++count > limits.maximumEntries() || entry.getSize() < 0
                        || entry.getSize() > limits.maximumExpandedBytes() - expanded) {
                    throw new PackageFailure("extension_package_limit_exceeded");
                }
                expanded += entry.getSize();
                if (!names.add(name) || !validEntry(name)) throw new PackageFailure("extension_package_invalid");
                if (forbidden(name)) throw new PackageFailure("extension_package_shadow_classes");
                if (name.equalsIgnoreCase("META-INF/MANIFEST.MF")
                        && entry.getSize() > limits.maximumManifestBytes()) {
                    throw new PackageFailure("extension_package_limit_exceeded");
                }
                if (name.equals(UniversalExtensionManifest.JAR_PATH)) {
                    if (entry.getSize() > limits.maximumManifestBytes()) {
                        throw new PackageFailure("extension_package_limit_exceeded");
                    }
                    try (var input = jar.getInputStream(entry)) {
                        byte[] bytes = input.readNBytes(limits.maximumManifestBytes() + 1);
                        if (bytes.length > limits.maximumManifestBytes()) {
                            throw new PackageFailure("extension_package_limit_exceeded");
                        }
                        manifest = utf8(bytes);
                    }
                }
            }
            var attributes = jar.getManifest();
            if (attributes != null) {
                String classPath = attributes.getMainAttributes().getValue(java.util.jar.Attributes.Name.CLASS_PATH);
                if (classPath != null && !classPath.isBlank()) {
                    throw new PackageFailure("extension_package_external_classpath");
                }
            }
        }
        if (manifest == null) throw new PackageFailure("extension_package_manifest_missing");
        UniversalExtensionManifest decoded = UniversalExtensionManifest.decode(manifest);
        if (!names.contains(decoded.entrypoint().replace('.', '/') + ".class")) {
            throw new PackageFailure("extension_entrypoint_unavailable");
        }
        return decoded;
    }

    private DiscoveryResult load(Candidate candidate) {
        String filename = candidate.path().getFileName().toString();
        String id = candidate.manifest().descriptor().id();
        URLClassLoader loader = null;
        boolean accepted = false;
        try {
            loader = new URLClassLoader(new URL[] {candidate.path().toUri().toURL()}, parent);
            Class<?> type = Class.forName(candidate.manifest().entrypoint(), false, loader);
            if (type.getClassLoader() != loader || !Modifier.isPublic(type.getModifiers())
                    || Modifier.isAbstract(type.getModifiers()) || type.isInterface()
                    || !OpenAllayExtension.class.isAssignableFrom(type)) {
                return rejected(filename, id, "extension_entrypoint_invalid");
            }
            var constructor = type.getConstructor(); // Public no-argument constructor only.
            OpenAllayExtension extension = (OpenAllayExtension) constructor.newInstance();
            ExtensionDescriptor declared = Objects.requireNonNull(extension.descriptor(), "descriptor");
            if (!candidate.manifest().descriptor().equals(declared)) {
                return rejected(filename, id, "extension_descriptor_mismatch");
            }
            var registration = registry.register(new UniversalExtensionBridge(extension, declared, host));
            if (registration.state() == OpenAllayExtensionState.ACTIVE) {
                loaders.add(loader);
                accepted = true;
            }
            return new DiscoveryResult(filename, id, registration.state(), registration.diagnostic());
        } catch (Throwable failure) {
            return rejected(filename, id, "extension_entrypoint_failed");
        } finally {
            if (loader != null && !accepted) closeLoader(loader);
        }
    }

    private static boolean validEntry(String name) {
        if (name.isEmpty() || name.startsWith("/") || name.contains("\\") || name.contains(":")) return false;
        String[] parts = name.split("/", -1);
        for (int index = 0; index < parts.length; index++) {
            if (parts[index].equals(".") || parts[index].equals("..")
                    || (parts[index].isEmpty() && index != parts.length - 1)) return false;
        }
        return true;
    }

    private boolean forbidden(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.startsWith("meta-inf/versions/") || lower.endsWith(".jar") || lower.endsWith(".dll")
                || lower.endsWith(".so") || lower.endsWith(".dylib") || lower.endsWith(".jnilib")
                || lower.endsWith(".exe")) return true;
        if (!name.endsWith(".class")) return false;
        return name.startsWith("dev/openallay/api/extension/")
                || (name.startsWith("dev/openallay/") && parent.getResource(name) != null)
                || name.startsWith("net/minecraft/")
                || name.startsWith("net/minecraftforge/") || name.startsWith("net/neoforged/")
                || name.startsWith("net/fabricmc/") || name.startsWith("cpw/mods/")
                || name.startsWith("com/mojang/") || name.startsWith("org/lwjgl/");
    }

    private static String utf8(byte[] bytes) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    }
    private static DiscoveryResult rejected(String filename, String id, String code) {
        return new DiscoveryResult(filename, id, OpenAllayExtensionState.UNAVAILABLE, code);
    }
    private static void closeLoader(URLClassLoader loader) {
        try { loader.close(); } catch (IOException ignored) { /* Continue closing every owned loader. */ }
    }

    /** Framework shutdown only, after outstanding invocation scopes have unwound. */
    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        for (int index = loaders.size() - 1; index >= 0; index--) closeLoader(loaders.get(index));
        loaders.clear();
    }

    public record Limits(long maximumJarBytes, int maximumEntries, long maximumExpandedBytes,
            int maximumManifestBytes, int maximumCandidates) {
        public static final Limits DEFAULT = new Limits(64L * 1024 * 1024, 10_000,
                128L * 1024 * 1024, 128 * 1024, 256);
        public Limits {
            if (maximumJarBytes < 1 || maximumEntries < 1 || maximumExpandedBytes < 1
                    || maximumManifestBytes < 1 || maximumManifestBytes == Integer.MAX_VALUE
                    || maximumCandidates < 1) throw new IllegalArgumentException("Positive package limits are required");
        }
    }
    public record DiscoveryResult(String filename, String extensionId, OpenAllayExtensionState state, String diagnostic) {}
    private record Candidate(Path path, UniversalExtensionManifest manifest, String failure) {}
    private static final class PackageFailure extends RuntimeException {
        private final String code;
        private PackageFailure(String code) { this.code = code; }
    }
}
