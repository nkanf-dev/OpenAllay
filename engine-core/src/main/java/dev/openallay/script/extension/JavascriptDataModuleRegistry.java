package dev.openallay.script.extension;

import dev.openallay.context.EvidenceMetadata;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.script.host.HostAccessException;
import dev.openallay.script.schema.HostSchema;
import dev.openallay.script.schema.RhinoTypeSchema;
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
        validateRegistration(providerId, additions);
        String normalizedProvider = providerId.strip();
        List<? extends JavascriptDataModule> candidates = List.copyOf(additions);
        candidates.forEach(module -> {
            HostSchema schema = null;
            String schemaDiagnostic = null;
            try {
                schema = RhinoTypeSchema.require(module.valueType());
            } catch (HostAccessException failure) {
                schemaDiagnostic = failure.code();
            }
            modules.put(
                    module.id(),
                    new RegisteredModule(
                            normalizedProvider, module, schema, schemaDiagnostic));
        });
    }

    public synchronized void validateRegistration(
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
    }

    /** Returns immutable declarations without capturing any request value. */
    public synchronized List<Descriptor> descriptors() {
        return modules.values().stream()
                .map(registered -> new Descriptor(
                        registered.module().id(),
                        registered.providerId(),
                        registered.module().summary(),
                        registered.schema() != null,
                        registered.schema(),
                        registered.schemaDiagnostic()))
                .toList();
    }

    public Snapshot capture(ToolInvocationContext context) {
        List<RegisteredModule> captured;
        synchronized (this) {
            captured = List.copyOf(modules.values());
        }
        Map<String, Object> values = new TreeMap<>();
        List<Diagnostic> diagnostics = new ArrayList<>();
        List<EvidenceMetadata> evidence = new ArrayList<>();
        for (RegisteredModule registered : captured) {
            JavascriptDataModule module = registered.module();
            if (registered.schema() == null) {
                diagnostics.add(new Diagnostic(
                        module.id(),
                        registered.providerId(),
                        registered.schemaDiagnostic()));
                continue;
            }
            try {
                JavascriptDataModule.Snapshot snapshot = module.capture(context);
                RhinoTypeSchema.validateValue(module.valueType(), snapshot.value());
                values.put(module.id(), snapshot.value());
                evidence.addAll(snapshot.evidence());
            } catch (RuntimeException failure) {
                diagnostics.add(new Diagnostic(
                        module.id(),
                        registered.providerId(),
                        failure instanceof HostAccessException hostFailure
                                ? hostFailure.code()
                                : "module_capture_failed"));
            }
        }
        return new Snapshot(
                Map.copyOf(values),
                List.copyOf(diagnostics),
                evidence.stream().distinct().toList());
    }

    @dev.openallay.value.ValueType(Diagnostic.ValueSchemaProvider.class)
public static final class Diagnostic {
    private final String module;
    private final String provider;
    private final String code;
    public Diagnostic(String module, String provider, String code) {
        this.module = module;
        this.provider = provider;
        this.code = code;
    }
    public String module() { return module; }
    public String provider() { return provider; }
    public String code() { return code; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Diagnostic)) return false;
        Diagnostic that = (Diagnostic) other;
        return java.util.Objects.equals(module, that.module) && java.util.Objects.equals(provider, that.provider) && java.util.Objects.equals(code, that.code);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(module);
        hash = 31 * hash + java.util.Objects.hashCode(provider);
        hash = 31 * hash + java.util.Objects.hashCode(code);
        return hash;
    }
    @Override public String toString() { return "Diagnostic[module=" + module + ", provider=" + provider + ", code=" + code + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Diagnostic> schema() {
            return new dev.openallay.value.ValueSchema<>(Diagnostic.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Diagnostic>>asList(new dev.openallay.value.ValueSchema.Component<>(Diagnostic.class, "module", Diagnostic::module), new dev.openallay.value.ValueSchema.Component<>(Diagnostic.class, "provider", Diagnostic::provider), new dev.openallay.value.ValueSchema.Component<>(Diagnostic.class, "code", Diagnostic::code)), arguments -> new Diagnostic((String) arguments[0], (String) arguments[1], (String) arguments[2]));
        }
    }
}

    @dev.openallay.value.ValueType(Descriptor.ValueSchemaProvider.class)
public static final class Descriptor {
    private final String module;
    private final String provider;
    private final String summary;
    private final boolean available;
    private final HostSchema schema;
    private final String diagnostic;
    public Descriptor(String module, String provider, String summary, boolean available, HostSchema schema, String diagnostic) {
        this.module = module;
        this.provider = provider;
        this.summary = summary;
        this.available = available;
        this.schema = schema;
        this.diagnostic = diagnostic;
    }
    public String module() { return module; }
    public String provider() { return provider; }
    public String summary() { return summary; }
    public boolean available() { return available; }
    public HostSchema schema() { return schema; }
    public String diagnostic() { return diagnostic; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Descriptor)) return false;
        Descriptor that = (Descriptor) other;
        return java.util.Objects.equals(module, that.module) && java.util.Objects.equals(provider, that.provider) && java.util.Objects.equals(summary, that.summary) && available == that.available && java.util.Objects.equals(schema, that.schema) && java.util.Objects.equals(diagnostic, that.diagnostic);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(module);
        hash = 31 * hash + java.util.Objects.hashCode(provider);
        hash = 31 * hash + java.util.Objects.hashCode(summary);
        hash = 31 * hash + Boolean.hashCode(available);
        hash = 31 * hash + java.util.Objects.hashCode(schema);
        hash = 31 * hash + java.util.Objects.hashCode(diagnostic);
        return hash;
    }
    @Override public String toString() { return "Descriptor[module=" + module + ", provider=" + provider + ", summary=" + summary + ", available=" + available + ", schema=" + schema + ", diagnostic=" + diagnostic + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Descriptor> schema() {
            return new dev.openallay.value.ValueSchema<>(Descriptor.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Descriptor>>asList(new dev.openallay.value.ValueSchema.Component<>(Descriptor.class, "module", Descriptor::module), new dev.openallay.value.ValueSchema.Component<>(Descriptor.class, "provider", Descriptor::provider), new dev.openallay.value.ValueSchema.Component<>(Descriptor.class, "summary", Descriptor::summary), new dev.openallay.value.ValueSchema.Component<>(Descriptor.class, "available", Descriptor::available), new dev.openallay.value.ValueSchema.Component<>(Descriptor.class, "schema", Descriptor::schema), new dev.openallay.value.ValueSchema.Component<>(Descriptor.class, "diagnostic", Descriptor::diagnostic)), arguments -> new Descriptor((String) arguments[0], (String) arguments[1], (String) arguments[2], (Boolean) arguments[3], (HostSchema) arguments[4], (String) arguments[5]));
        }
    }
}

    @dev.openallay.value.ValueType(Snapshot.ValueSchemaProvider.class)
public static final class Snapshot {
    private final Map<String, Object> values;
    private final List<Diagnostic> diagnostics;
    private final List<EvidenceMetadata> evidence;
    public Snapshot(Map<String, Object> values, List<Diagnostic> diagnostics, List<EvidenceMetadata> evidence) {

            values = Map.copyOf(values);
            diagnostics = List.copyOf(diagnostics);
            evidence = List.copyOf(evidence);

        this.values = values;
        this.diagnostics = diagnostics;
        this.evidence = evidence;
    }
    public Map<String, Object> values() { return values; }
    public List<Diagnostic> diagnostics() { return diagnostics; }
    public List<EvidenceMetadata> evidence() { return evidence; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Snapshot)) return false;
        Snapshot that = (Snapshot) other;
        return java.util.Objects.equals(values, that.values) && java.util.Objects.equals(diagnostics, that.diagnostics) && java.util.Objects.equals(evidence, that.evidence);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(values);
        hash = 31 * hash + java.util.Objects.hashCode(diagnostics);
        hash = 31 * hash + java.util.Objects.hashCode(evidence);
        return hash;
    }
    @Override public String toString() { return "Snapshot[values=" + values + ", diagnostics=" + diagnostics + ", evidence=" + evidence + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Snapshot> schema() {
            return new dev.openallay.value.ValueSchema<>(Snapshot.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Snapshot>>asList(new dev.openallay.value.ValueSchema.Component<>(Snapshot.class, "values", Snapshot::values), new dev.openallay.value.ValueSchema.Component<>(Snapshot.class, "diagnostics", Snapshot::diagnostics), new dev.openallay.value.ValueSchema.Component<>(Snapshot.class, "evidence", Snapshot::evidence)), arguments -> new Snapshot((Map) arguments[0], (List) arguments[1], (List) arguments[2]));
        }
    }
}

    @dev.openallay.value.ValueType(RegisteredModule.ValueSchemaProvider.class)
private static final class RegisteredModule {
    private final String providerId;
    private final JavascriptDataModule module;
    private final HostSchema schema;
    private final String schemaDiagnostic;
    private RegisteredModule(String providerId, JavascriptDataModule module, HostSchema schema, String schemaDiagnostic) {
        this.providerId = providerId;
        this.module = module;
        this.schema = schema;
        this.schemaDiagnostic = schemaDiagnostic;
    }
    public String providerId() { return providerId; }
    public JavascriptDataModule module() { return module; }
    public HostSchema schema() { return schema; }
    public String schemaDiagnostic() { return schemaDiagnostic; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RegisteredModule)) return false;
        RegisteredModule that = (RegisteredModule) other;
        return java.util.Objects.equals(providerId, that.providerId) && java.util.Objects.equals(module, that.module) && java.util.Objects.equals(schema, that.schema) && java.util.Objects.equals(schemaDiagnostic, that.schemaDiagnostic);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(providerId);
        hash = 31 * hash + java.util.Objects.hashCode(module);
        hash = 31 * hash + java.util.Objects.hashCode(schema);
        hash = 31 * hash + java.util.Objects.hashCode(schemaDiagnostic);
        return hash;
    }
    @Override public String toString() { return "RegisteredModule[providerId=" + providerId + ", module=" + module + ", schema=" + schema + ", schemaDiagnostic=" + schemaDiagnostic + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<RegisteredModule> schema() {
            return new dev.openallay.value.ValueSchema<>(RegisteredModule.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<RegisteredModule>>asList(new dev.openallay.value.ValueSchema.Component<>(RegisteredModule.class, "providerId", RegisteredModule::providerId), new dev.openallay.value.ValueSchema.Component<>(RegisteredModule.class, "module", RegisteredModule::module), new dev.openallay.value.ValueSchema.Component<>(RegisteredModule.class, "schema", RegisteredModule::schema), new dev.openallay.value.ValueSchema.Component<>(RegisteredModule.class, "schemaDiagnostic", RegisteredModule::schemaDiagnostic)), arguments -> new RegisteredModule((String) arguments[0], (JavascriptDataModule) arguments[1], (HostSchema) arguments[2], (String) arguments[3]));
        }
    }
}
}
