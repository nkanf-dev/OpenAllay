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
    private static final Map<String, String> BUNDLED = Map.of(
            "openallay:crafting",
            "assets/openallay/openallay_js_modules/crafting.js");

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
        return Set.copyOf(BUNDLED.keySet());
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
        return Set.copyOf(sources.keySet());
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
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("JavaScript module source is required");
        }
    }

    private static String requireProvider(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("JavaScript module provider ID is required");
        }
        return providerId.strip();
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

    private record RegisteredSource(String provider, String source) {}
}
