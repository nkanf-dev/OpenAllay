package dev.openallay.script.schema;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable index used by Rhino discovery and settings without resolving root suppliers. */
public final class HostSchemaCatalog {
    private final Map<String, HostRootDescriptor> roots;

    public HostSchemaCatalog(Collection<HostRootDescriptor> descriptors) {
        LinkedHashMap<String, HostRootDescriptor> collected = new LinkedHashMap<>();
        for (HostRootDescriptor descriptor : List.copyOf(descriptors)) {
            Objects.requireNonNull(descriptor, "descriptor");
            if (collected.putIfAbsent(descriptor.name(), descriptor) != null) {
                throw new IllegalArgumentException(
                        "Duplicate host root descriptor: " + descriptor.name());
            }
        }
        roots = java.util.Collections.unmodifiableMap(collected);
    }

    public List<RootSummary> list() {
        return roots.values().stream()
                .map(root -> new RootSummary(
                        root.name(),
                        root.availability(),
                        root.providerId(),
                        root.summary(),
                        root.evidenceOwner(),
                        root.schema().kind()))
                .toList();
    }

    public Optional<PathDescription> describe(String path) {
        if (path == null || path.isBlank()) {
            return Optional.empty();
        }
        String[] parts = path.strip().split("\\.");
        HostRootDescriptor root = roots.get(parts[0]);
        if (root == null) {
            return Optional.empty();
        }
        HostSchema schema = root.schema();
        ArrayList<String> resolved = new ArrayList<>();
        resolved.add(root.name());
        for (int index = 1; index < parts.length; index++) {
            String field = parts[index];
            if (!(schema instanceof HostSchema.RecordValue record)) {
                return Optional.empty();
            }
            schema = record.fields().get(field);
            if (schema == null) {
                return Optional.empty();
            }
            resolved.add(field);
        }
        return Optional.of(new PathDescription(
                String.join(".", resolved),
                root.availability(),
                root.providerId(),
                root.evidenceOwner(),
                schema));
    }

    public Optional<HostRootDescriptor> root(String name) {
        return Optional.ofNullable(roots.get(name));
    }

    public List<String> availableRootNames() {
        return roots.values().stream()
                .filter(HostRootDescriptor::available)
                .map(HostRootDescriptor::name)
                .toList();
    }

    @dev.openallay.value.ValueType(RootSummary.ValueSchemaProvider.class)
public static final class RootSummary {
    private final String name;
    private final HostRootDescriptor.Availability availability;
    private final String provider;
    private final String summary;
    private final String evidenceOwner;
    private final String schemaKind;
    public RootSummary(String name, HostRootDescriptor.Availability availability, String provider, String summary, String evidenceOwner, String schemaKind) {
        this.name = name;
        this.availability = availability;
        this.provider = provider;
        this.summary = summary;
        this.evidenceOwner = evidenceOwner;
        this.schemaKind = schemaKind;
    }
    public String name() { return name; }
    public HostRootDescriptor.Availability availability() { return availability; }
    public String provider() { return provider; }
    public String summary() { return summary; }
    public String evidenceOwner() { return evidenceOwner; }
    public String schemaKind() { return schemaKind; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RootSummary)) return false;
        RootSummary that = (RootSummary) other;
        return java.util.Objects.equals(name, that.name) && java.util.Objects.equals(availability, that.availability) && java.util.Objects.equals(provider, that.provider) && java.util.Objects.equals(summary, that.summary) && java.util.Objects.equals(evidenceOwner, that.evidenceOwner) && java.util.Objects.equals(schemaKind, that.schemaKind);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(name);
        hash = 31 * hash + java.util.Objects.hashCode(availability);
        hash = 31 * hash + java.util.Objects.hashCode(provider);
        hash = 31 * hash + java.util.Objects.hashCode(summary);
        hash = 31 * hash + java.util.Objects.hashCode(evidenceOwner);
        hash = 31 * hash + java.util.Objects.hashCode(schemaKind);
        return hash;
    }
    @Override public String toString() { return "RootSummary[name=" + name + ", availability=" + availability + ", provider=" + provider + ", summary=" + summary + ", evidenceOwner=" + evidenceOwner + ", schemaKind=" + schemaKind + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<RootSummary> schema() {
            return new dev.openallay.value.ValueSchema<>(RootSummary.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<RootSummary>>asList(new dev.openallay.value.ValueSchema.Component<>(RootSummary.class, "name", RootSummary::name), new dev.openallay.value.ValueSchema.Component<>(RootSummary.class, "availability", RootSummary::availability), new dev.openallay.value.ValueSchema.Component<>(RootSummary.class, "provider", RootSummary::provider), new dev.openallay.value.ValueSchema.Component<>(RootSummary.class, "summary", RootSummary::summary), new dev.openallay.value.ValueSchema.Component<>(RootSummary.class, "evidenceOwner", RootSummary::evidenceOwner), new dev.openallay.value.ValueSchema.Component<>(RootSummary.class, "schemaKind", RootSummary::schemaKind)), arguments -> new RootSummary((String) arguments[0], (HostRootDescriptor.Availability) arguments[1], (String) arguments[2], (String) arguments[3], (String) arguments[4], (String) arguments[5]));
        }
    }
}

    @dev.openallay.value.ValueType(PathDescription.ValueSchemaProvider.class)
public static final class PathDescription {
    private final String path;
    private final HostRootDescriptor.Availability availability;
    private final String provider;
    private final String evidenceOwner;
    private final HostSchema schema;
    public PathDescription(String path, HostRootDescriptor.Availability availability, String provider, String evidenceOwner, HostSchema schema) {
        this.path = path;
        this.availability = availability;
        this.provider = provider;
        this.evidenceOwner = evidenceOwner;
        this.schema = schema;
    }
    public String path() { return path; }
    public HostRootDescriptor.Availability availability() { return availability; }
    public String provider() { return provider; }
    public String evidenceOwner() { return evidenceOwner; }
    public HostSchema schema() { return schema; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PathDescription)) return false;
        PathDescription that = (PathDescription) other;
        return java.util.Objects.equals(path, that.path) && java.util.Objects.equals(availability, that.availability) && java.util.Objects.equals(provider, that.provider) && java.util.Objects.equals(evidenceOwner, that.evidenceOwner) && java.util.Objects.equals(schema, that.schema);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(path);
        hash = 31 * hash + java.util.Objects.hashCode(availability);
        hash = 31 * hash + java.util.Objects.hashCode(provider);
        hash = 31 * hash + java.util.Objects.hashCode(evidenceOwner);
        hash = 31 * hash + java.util.Objects.hashCode(schema);
        return hash;
    }
    @Override public String toString() { return "PathDescription[path=" + path + ", availability=" + availability + ", provider=" + provider + ", evidenceOwner=" + evidenceOwner + ", schema=" + schema + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<PathDescription> schema() {
            return new dev.openallay.value.ValueSchema<>(PathDescription.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<PathDescription>>asList(new dev.openallay.value.ValueSchema.Component<>(PathDescription.class, "path", PathDescription::path), new dev.openallay.value.ValueSchema.Component<>(PathDescription.class, "availability", PathDescription::availability), new dev.openallay.value.ValueSchema.Component<>(PathDescription.class, "provider", PathDescription::provider), new dev.openallay.value.ValueSchema.Component<>(PathDescription.class, "evidenceOwner", PathDescription::evidenceOwner), new dev.openallay.value.ValueSchema.Component<>(PathDescription.class, "schema", PathDescription::schema)), arguments -> new PathDescription((String) arguments[0], (HostRootDescriptor.Availability) arguments[1], (String) arguments[2], (String) arguments[3], (HostSchema) arguments[4]));
        }
    }
}
}
