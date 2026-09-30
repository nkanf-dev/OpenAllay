package dev.openallay.extension.catalog;

import dev.openallay.extension.OpenAllayExtensionDescriptor;
import dev.openallay.requirement.RequirementSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/** One immutable Extension version with one independently verified JAR per loader. */
public record ExtensionCatalogEntry(
        String id,
        String name,
        String version,
        String provider,
        String summary,
        String minecraftVersionRange,
        String openAllayApiVersionRange,
        List<ExtensionCatalogArtifact> artifacts,
        String source,
        RequirementSet requirements) {

    public ExtensionCatalogEntry(
            String id, String name, String version, String provider, String summary,
            String minecraftVersionRange, String openAllayApiVersionRange,
            List<ExtensionCatalogArtifact> artifacts, String source) {
        this(id, name, version, provider, summary, minecraftVersionRange,
                openAllayApiVersionRange, artifacts, source, RequirementSet.EMPTY);
    }

    public ExtensionCatalogEntry {
        TreeMap<String, ExtensionCatalogArtifact> byLoader = new TreeMap<>();
        for (ExtensionCatalogArtifact artifact : List.copyOf(artifacts)) {
            java.util.Objects.requireNonNull(artifact, "artifact");
            if (byLoader.putIfAbsent(artifact.loader(), artifact) != null) {
                throw new IllegalArgumentException(
                        "Duplicate Extension artifact loader: " + artifact.loader());
            }
        }
        if (byLoader.isEmpty()) {
            throw new IllegalArgumentException(
                    "Extension catalog entry must declare at least one artifact");
        }
        artifacts = List.copyOf(byLoader.values());
        OpenAllayExtensionDescriptor descriptor = new OpenAllayExtensionDescriptor(
                id,
                name,
                version,
                provider,
                summary,
                byLoader.keySet(),
                minecraftVersionRange,
                openAllayApiVersionRange,
                source, requirements);
        id = descriptor.id();
        name = descriptor.name();
        version = descriptor.version();
        provider = descriptor.provider();
        summary = descriptor.summary();
        minecraftVersionRange = descriptor.minecraftVersionRange();
        openAllayApiVersionRange = descriptor.openAllayApiVersionRange();
        source = descriptor.source();
        requirements = descriptor.requirements();
    }

    public Set<String> loaders() {
        return artifacts.stream()
                .map(ExtensionCatalogArtifact::loader)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public Optional<ExtensionCatalogArtifact> artifactFor(String loader) {
        if (loader == null) {
            return Optional.empty();
        }
        String normalized = loader.strip().toLowerCase(java.util.Locale.ROOT);
        return artifacts.stream()
                .filter(artifact -> artifact.loader().equals(normalized))
                .findFirst();
    }

    public OpenAllayExtensionDescriptor descriptor() {
        return new OpenAllayExtensionDescriptor(
                id,
                name,
                version,
                provider,
                summary,
                loaders(),
                minecraftVersionRange,
                openAllayApiVersionRange,
                source, requirements);
    }

    public OpenAllayExtensionDescriptor descriptorFor(String loader) {
        ExtensionCatalogArtifact selected = artifactFor(loader)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Extension has no artifact for loader: " + loader));
        return new OpenAllayExtensionDescriptor(
                id,
                name,
                version,
                provider,
                summary,
                Set.of(selected.loader()),
                minecraftVersionRange,
                openAllayApiVersionRange,
                source, requirements);
    }
}
