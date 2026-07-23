package dev.openallay.script.extension;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.openallay.context.EvidenceMetadata;
import dev.openallay.context.ToolInvocationContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Isolated registry for trusted extension-provided detached JavaScript modules. */
public final class JavascriptDataModuleRegistry {
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private final Map<String, RegisteredModule> modules = new TreeMap<>();

    public synchronized void register(
            String providerId, Collection<? extends JavascriptDataModule> additions) {
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("Module provider ID must not be blank");
        }
        String normalizedProvider = providerId.strip();
        List<? extends JavascriptDataModule> candidates =
                List.copyOf(Objects.requireNonNull(additions, "additions"));
        Set<String> batchIds = new HashSet<>();
        for (JavascriptDataModule module : candidates) {
            Objects.requireNonNull(module, "module");
            String id = module.id();
            if (id == null || !ID.matcher(id).matches()) {
                throw new IllegalArgumentException("Invalid JavaScript module ID: " + id);
            }
            if (!batchIds.add(id)) {
                throw new IllegalStateException(
                        "Duplicate JavaScript module ID in provider "
                                + normalizedProvider + ": " + id);
            }
            RegisteredModule existing = modules.get(id);
            if (existing != null) {
                throw new IllegalStateException(
                        "Duplicate JavaScript module ID " + id
                                + " from providers " + existing.providerId()
                                + " and " + normalizedProvider);
            }
        }
        candidates.forEach(module ->
                modules.put(module.id(), new RegisteredModule(normalizedProvider, module)));
    }

    public Snapshot capture(ToolInvocationContext context) {
        List<RegisteredModule> captured;
        synchronized (this) {
            captured = List.copyOf(modules.values());
        }
        JsonObject values = new JsonObject();
        JsonArray diagnostics = new JsonArray();
        List<EvidenceMetadata> evidence = new ArrayList<>();
        for (RegisteredModule registered : captured) {
            JavascriptDataModule module = registered.module();
            try {
                JavascriptDataModule.Snapshot snapshot = module.capture(context);
                values.add(module.id(), snapshot.value());
                evidence.addAll(snapshot.evidence());
            } catch (RuntimeException failure) {
                JsonObject diagnostic = new JsonObject();
                diagnostic.addProperty("module", module.id());
                diagnostic.addProperty("provider", registered.providerId());
                diagnostic.addProperty("code", "module_capture_failed");
                diagnostics.add(diagnostic);
            }
        }
        return new Snapshot(values, diagnostics, evidence.stream().distinct().toList());
    }

    public record Snapshot(
            JsonObject values,
            JsonArray diagnostics,
            List<EvidenceMetadata> evidence) {
        public Snapshot {
            values = values.deepCopy();
            diagnostics = diagnostics.deepCopy();
            evidence = List.copyOf(evidence);
        }

        @Override
        public JsonObject values() {
            return values.deepCopy();
        }

        @Override
        public JsonArray diagnostics() {
            return diagnostics.deepCopy();
        }
    }

    private record RegisteredModule(String providerId, JavascriptDataModule module) {}
}
