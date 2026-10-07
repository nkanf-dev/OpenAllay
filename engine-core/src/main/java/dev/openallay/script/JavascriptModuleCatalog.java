package dev.openallay.script;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Reviewed JavaScript modules available inside one Rhino scope. */
public final class JavascriptModuleCatalog {
    private static final Map<String, String> BUNDLED = dev.openallay.util.Java8Collections.mapOf("openallay:crafting", "assets/openallay/openallay_js_modules/crafting.js");

    private final Map<String, RegisteredSource> sources = new TreeMap<>();

    public JavascriptModuleCatalog(Map<String, String> sources) {
        Objects.requireNonNull(sources, "sources").forEach((id, source) -> {
            validate(id, source);
            this.sources.put(id, new RegisteredSource("openallay:bundled", source));
        });
    }

    public static JavascriptModuleCatalog bundled() {
        ClassLoader loader = JavascriptModuleCatalog.class.getClassLoader();
        LinkedHashMap<String, String> sources = new LinkedHashMap<>();
        BUNDLED.forEach((id, path) -> sources.put(id, read(loader, path)));
        return new JavascriptModuleCatalog(sources);
    }

    /** Descriptor-only view used by Settings without loading module source text. */
    public static Set<String> bundledIds() {
        return dev.openallay.util.Java8Collections.setCopyOf(BUNDLED.keySet());
    }

    public synchronized String source(String id) {
        RegisteredSource source = sources.get(id);
        if (source == null) {
            throw new JavascriptExecutionException(
                    "javascript_module_unavailable",
                    "JavaScript module is unavailable: " + id);
        }
        return source.source();
    }

    public synchronized Set<String> ids() {
        return dev.openallay.util.Java8Collections.setCopyOf(sources.keySet());
    }

    public synchronized void validateRegistration(
            String providerId, Map<String, String> additions) {
        String provider = requireProvider(providerId);
        java.util.HashSet<String> batch = new java.util.HashSet<>();
        Objects.requireNonNull(additions, "additions").forEach((id, source) -> {
            validate(id, source);
            if (!batch.add(id)) {
                throw new IllegalStateException(
                        "Duplicate JavaScript module ID in provider " + provider + ": " + id);
            }
            RegisteredSource existing = sources.get(id);
            if (existing != null) {
                throw new IllegalStateException(
                        "Duplicate JavaScript module ID " + id + " from providers "
                                + existing.provider() + " and " + provider);
            }
        });
    }

    public synchronized void register(String providerId, Map<String, String> additions) {
        String provider = requireProvider(providerId);
        validateRegistration(provider, additions);
        additions.forEach((id, source) ->
                sources.put(id, new RegisteredSource(provider, source)));
    }

    private static void validate(String id, String source) {
        if (id == null || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("Invalid JavaScript module id");
        }
        if (source == null || dev.openallay.util.Java8Strings.isBlank(source)) {
            throw new IllegalArgumentException("JavaScript module source is required");
        }
    }

    private static String requireProvider(String providerId) {
        if (providerId == null || dev.openallay.util.Java8Strings.isBlank(providerId)) {
            throw new IllegalArgumentException("JavaScript module provider ID is required");
        }
        return dev.openallay.util.Java8Strings.strip(providerId);
    }

    private static String read(ClassLoader loader, String path) {
        try (InputStream stream = loader.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing bundled JavaScript module " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to load bundled JavaScript module " + path, failure);
        }
    }

    @dev.openallay.value.ValueType(RegisteredSource.ValueSchemaProvider.class)
private static final class RegisteredSource {
    private final String provider;
    private final String source;
    private RegisteredSource(String provider, String source) {
        this.provider = provider;
        this.source = source;
    }
    public String provider() { return provider; }
    public String source() { return source; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RegisteredSource)) return false;
        RegisteredSource that = (RegisteredSource) other;
        return java.util.Objects.equals(provider, that.provider) && java.util.Objects.equals(source, that.source);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(provider);
        hash = 31 * hash + java.util.Objects.hashCode(source);
        return hash;
    }
    @Override public String toString() { return "RegisteredSource[provider=" + provider + ", source=" + source + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<RegisteredSource> schema() {
            return new dev.openallay.value.ValueSchema<>(RegisteredSource.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<RegisteredSource>>asList(new dev.openallay.value.ValueSchema.Component<>(RegisteredSource.class, "provider", RegisteredSource::provider), new dev.openallay.value.ValueSchema.Component<>(RegisteredSource.class, "source", RegisteredSource::source)), arguments -> new RegisteredSource((String) arguments[0], (String) arguments[1]));
        }
    }
}
}
