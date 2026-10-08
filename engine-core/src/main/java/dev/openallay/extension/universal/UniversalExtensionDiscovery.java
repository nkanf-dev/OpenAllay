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
            results = dev.openallay.util.Java8Collections.listOf();
            return results;
        }
        if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            results = dev.openallay.util.Java8Collections.listOf(rejected("", "", "extension_directory_invalid"));
            return results;
        }
        List<Path> jars;
        try (java.util.stream.Stream<java.nio.file.Path> files = Files.list(directory)) {
            jars = dev.openallay.util.Java8Collections.toList(files.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString())));
        } catch (IOException failure) {
            results = dev.openallay.util.Java8Collections.listOf(rejected("", "", "extension_directory_unavailable"));
            return results;
        }
        if (jars.size() > limits.maximumCandidates()) {
            results = dev.openallay.util.Java8Collections.listOf(rejected("", "", "extension_package_limit_exceeded"));
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
            dev.openallay.api.extension.SupportTarget target = UniversalExtensionSupport.matchingTarget(descriptor.support(), host.environment()).orElseThrow(() -> new java.util.NoSuchElementException("No value present"));
            String legacyIncompatibility = registry.environment().incompatibility(
                    UniversalExtensionSupport.legacyDescriptor(descriptor, target));
            if (!legacyIncompatibility.isEmpty()) {
                discovered.add(new DiscoveryResult(filename, id, OpenAllayExtensionState.INCOMPATIBLE,
                        legacyIncompatibility));
                continue;
            }
            discovered.add(load(candidate));
        }
        results = dev.openallay.util.Java8Collections.listCopyOf(discovered);
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
            java.util.Enumeration<java.util.jar.JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                java.util.jar.JarEntry entry = entries.nextElement();
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
                    try (java.io.InputStream input = jar.getInputStream(entry)) {
                        byte[] bytes = dev.openallay.util.Java8Streams.readNBytes(input, limits.maximumManifestBytes() + 1);
                        if (bytes.length > limits.maximumManifestBytes()) {
                            throw new PackageFailure("extension_package_limit_exceeded");
                        }
                        manifest = utf8(bytes);
                    }
                }
            }
            java.util.jar.Manifest attributes = jar.getManifest();
            if (attributes != null) {
                String classPath = attributes.getMainAttributes().getValue(java.util.jar.Attributes.Name.CLASS_PATH);
                if (classPath != null && !dev.openallay.util.Java8Strings.isBlank(classPath)) {
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
            java.lang.reflect.Constructor<?> constructor = type.getConstructor(); // Public no-argument constructor only.
            OpenAllayExtension extension = (OpenAllayExtension) constructor.newInstance();
            ExtensionDescriptor declared = Objects.requireNonNull(extension.descriptor(), "descriptor");
            if (!candidate.manifest().descriptor().equals(declared)) {
                return rejected(filename, id, "extension_descriptor_mismatch");
            }
            dev.openallay.extension.OpenAllayExtensionRegistry.Registration registration = registry.register(new UniversalExtensionBridge(extension, declared, host));
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

    @dev.openallay.value.ValueType(Limits.ValueSchemaProvider.class)
public static final class Limits {
    private final long maximumJarBytes;
    private final int maximumEntries;
    private final long maximumExpandedBytes;
    private final int maximumManifestBytes;
    private final int maximumCandidates;
    public Limits(long maximumJarBytes, int maximumEntries, long maximumExpandedBytes, int maximumManifestBytes, int maximumCandidates) {

            if (maximumJarBytes < 1 || maximumEntries < 1 || maximumExpandedBytes < 1
                    || maximumManifestBytes < 1 || maximumManifestBytes == Integer.MAX_VALUE
                    || maximumCandidates < 1) throw new IllegalArgumentException("Positive package limits are required");

        this.maximumJarBytes = maximumJarBytes;
        this.maximumEntries = maximumEntries;
        this.maximumExpandedBytes = maximumExpandedBytes;
        this.maximumManifestBytes = maximumManifestBytes;
        this.maximumCandidates = maximumCandidates;
    }
    public long maximumJarBytes() { return maximumJarBytes; }
    public int maximumEntries() { return maximumEntries; }
    public long maximumExpandedBytes() { return maximumExpandedBytes; }
    public int maximumManifestBytes() { return maximumManifestBytes; }
    public int maximumCandidates() { return maximumCandidates; }
public static final Limits DEFAULT = new Limits(64L * 1024 * 1024, 10_000,
                128L * 1024 * 1024, 128 * 1024, 256);
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Limits)) return false;
        Limits that = (Limits) other;
        return maximumJarBytes == that.maximumJarBytes && maximumEntries == that.maximumEntries && maximumExpandedBytes == that.maximumExpandedBytes && maximumManifestBytes == that.maximumManifestBytes && maximumCandidates == that.maximumCandidates;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Long.hashCode(maximumJarBytes);
        hash = 31 * hash + Integer.hashCode(maximumEntries);
        hash = 31 * hash + Long.hashCode(maximumExpandedBytes);
        hash = 31 * hash + Integer.hashCode(maximumManifestBytes);
        hash = 31 * hash + Integer.hashCode(maximumCandidates);
        return hash;
    }
    @Override public String toString() { return "Limits[maximumJarBytes=" + maximumJarBytes + ", maximumEntries=" + maximumEntries + ", maximumExpandedBytes=" + maximumExpandedBytes + ", maximumManifestBytes=" + maximumManifestBytes + ", maximumCandidates=" + maximumCandidates + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Limits> schema() {
            return new dev.openallay.value.ValueSchema<>(Limits.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Limits>>asList(new dev.openallay.value.ValueSchema.Component<>(Limits.class, "maximumJarBytes", Limits::maximumJarBytes), new dev.openallay.value.ValueSchema.Component<>(Limits.class, "maximumEntries", Limits::maximumEntries), new dev.openallay.value.ValueSchema.Component<>(Limits.class, "maximumExpandedBytes", Limits::maximumExpandedBytes), new dev.openallay.value.ValueSchema.Component<>(Limits.class, "maximumManifestBytes", Limits::maximumManifestBytes), new dev.openallay.value.ValueSchema.Component<>(Limits.class, "maximumCandidates", Limits::maximumCandidates)), arguments -> new Limits((Long) arguments[0], (Integer) arguments[1], (Long) arguments[2], (Integer) arguments[3], (Integer) arguments[4]));
        }
    }
}
    @dev.openallay.value.ValueType(DiscoveryResult.ValueSchemaProvider.class)
public static final class DiscoveryResult {
    private final String filename;
    private final String extensionId;
    private final OpenAllayExtensionState state;
    private final String diagnostic;
    public DiscoveryResult(String filename, String extensionId, OpenAllayExtensionState state, String diagnostic) {
        this.filename = filename;
        this.extensionId = extensionId;
        this.state = state;
        this.diagnostic = diagnostic;
    }
    public String filename() { return filename; }
    public String extensionId() { return extensionId; }
    public OpenAllayExtensionState state() { return state; }
    public String diagnostic() { return diagnostic; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DiscoveryResult)) return false;
        DiscoveryResult that = (DiscoveryResult) other;
        return java.util.Objects.equals(filename, that.filename) && java.util.Objects.equals(extensionId, that.extensionId) && java.util.Objects.equals(state, that.state) && java.util.Objects.equals(diagnostic, that.diagnostic);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(filename);
        hash = 31 * hash + java.util.Objects.hashCode(extensionId);
        hash = 31 * hash + java.util.Objects.hashCode(state);
        hash = 31 * hash + java.util.Objects.hashCode(diagnostic);
        return hash;
    }
    @Override public String toString() { return "DiscoveryResult[filename=" + filename + ", extensionId=" + extensionId + ", state=" + state + ", diagnostic=" + diagnostic + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<DiscoveryResult> schema() {
            return new dev.openallay.value.ValueSchema<>(DiscoveryResult.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<DiscoveryResult>>asList(new dev.openallay.value.ValueSchema.Component<>(DiscoveryResult.class, "filename", DiscoveryResult::filename), new dev.openallay.value.ValueSchema.Component<>(DiscoveryResult.class, "extensionId", DiscoveryResult::extensionId), new dev.openallay.value.ValueSchema.Component<>(DiscoveryResult.class, "state", DiscoveryResult::state), new dev.openallay.value.ValueSchema.Component<>(DiscoveryResult.class, "diagnostic", DiscoveryResult::diagnostic)), arguments -> new DiscoveryResult((String) arguments[0], (String) arguments[1], (OpenAllayExtensionState) arguments[2], (String) arguments[3]));
        }
    }
}
    @dev.openallay.value.ValueType(Candidate.ValueSchemaProvider.class)
private static final class Candidate {
    private final Path path;
    private final UniversalExtensionManifest manifest;
    private final String failure;
    private Candidate(Path path, UniversalExtensionManifest manifest, String failure) {
        this.path = path;
        this.manifest = manifest;
        this.failure = failure;
    }
    public Path path() { return path; }
    public UniversalExtensionManifest manifest() { return manifest; }
    public String failure() { return failure; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Candidate)) return false;
        Candidate that = (Candidate) other;
        return java.util.Objects.equals(path, that.path) && java.util.Objects.equals(manifest, that.manifest) && java.util.Objects.equals(failure, that.failure);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(path);
        hash = 31 * hash + java.util.Objects.hashCode(manifest);
        hash = 31 * hash + java.util.Objects.hashCode(failure);
        return hash;
    }
    @Override public String toString() { return "Candidate[path=" + path + ", manifest=" + manifest + ", failure=" + failure + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Candidate> schema() {
            return new dev.openallay.value.ValueSchema<>(Candidate.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Candidate>>asList(new dev.openallay.value.ValueSchema.Component<>(Candidate.class, "path", Candidate::path), new dev.openallay.value.ValueSchema.Component<>(Candidate.class, "manifest", Candidate::manifest), new dev.openallay.value.ValueSchema.Component<>(Candidate.class, "failure", Candidate::failure)), arguments -> new Candidate((Path) arguments[0], (UniversalExtensionManifest) arguments[1], (String) arguments[2]));
        }
    }
}
    private static final class PackageFailure extends RuntimeException {
        private final String code;
        private PackageFailure(String code) { this.code = code; }
    }
}
