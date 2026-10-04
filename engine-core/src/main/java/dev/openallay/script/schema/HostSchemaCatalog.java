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

    public record RootSummary(
            String name,
            HostRootDescriptor.Availability availability,
            String provider,
            String summary,
            String evidenceOwner,
            String schemaKind) {}

    public record PathDescription(
            String path,
            HostRootDescriptor.Availability availability,
            String provider,
            String evidenceOwner,
            HostSchema schema) {}
}
