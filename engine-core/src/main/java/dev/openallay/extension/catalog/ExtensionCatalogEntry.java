package dev.openallay.extension.catalog;

import dev.openallay.extension.OpenAllayExtensionDescriptor;
import dev.openallay.requirement.RequirementSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/** One immutable Extension version with one independently verified JAR per loader. */
@dev.openallay.value.ValueType(ExtensionCatalogEntry.ValueSchemaProvider.class)
public final class ExtensionCatalogEntry {
    private final String id;
    private final String name;
    private final String version;
    private final String provider;
    private final String summary;
    private final String minecraftVersionRange;
    private final String openAllayApiVersionRange;
    private final List<ExtensionCatalogArtifact> artifacts;
    private final String source;
    private final RequirementSet requirements;
    public ExtensionCatalogEntry(String id, String name, String version, String provider, String summary, String minecraftVersionRange, String openAllayApiVersionRange, List<ExtensionCatalogArtifact> artifacts, String source, RequirementSet requirements) {

        TreeMap<String, ExtensionCatalogArtifact> byLoader = new TreeMap<>();
        for (ExtensionCatalogArtifact artifact : dev.openallay.util.Java8Collections.listCopyOf(artifacts)) {
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
        artifacts = dev.openallay.util.Java8Collections.listCopyOf(byLoader.values());
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

        this.id = id;
        this.name = name;
        this.version = version;
        this.provider = provider;
        this.summary = summary;
        this.minecraftVersionRange = minecraftVersionRange;
        this.openAllayApiVersionRange = openAllayApiVersionRange;
        this.artifacts = artifacts;
        this.source = source;
        this.requirements = requirements;
    }
    public String id() { return id; }
    public String name() { return name; }
    public String version() { return version; }
    public String provider() { return provider; }
    public String summary() { return summary; }
    public String minecraftVersionRange() { return minecraftVersionRange; }
    public String openAllayApiVersionRange() { return openAllayApiVersionRange; }
    public List<ExtensionCatalogArtifact> artifacts() { return artifacts; }
    public String source() { return source; }
    public RequirementSet requirements() { return requirements; }
public ExtensionCatalogEntry(
            String id, String name, String version, String provider, String summary,
            String minecraftVersionRange, String openAllayApiVersionRange,
            List<ExtensionCatalogArtifact> artifacts, String source) {
        this(id, name, version, provider, summary, minecraftVersionRange,
                openAllayApiVersionRange, artifacts, source, RequirementSet.EMPTY);
    }
public Set<String> loaders() {
        return artifacts.stream()
                .map(ExtensionCatalogArtifact::loader)
                .collect(dev.openallay.util.Java8ApiSupport.toUnmodifiableSet());
    }
public Optional<ExtensionCatalogArtifact> artifactFor(String loader) {
        if (loader == null) {
            return Optional.empty();
        }
        String normalized = dev.openallay.util.Java8Strings.strip(loader).toLowerCase(java.util.Locale.ROOT);
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
                dev.openallay.util.Java8Collections.setOf(selected.loader()),
                minecraftVersionRange,
                openAllayApiVersionRange,
                source, requirements);
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ExtensionCatalogEntry)) return false;
        ExtensionCatalogEntry that = (ExtensionCatalogEntry) other;
        return java.util.Objects.equals(id, that.id) && java.util.Objects.equals(name, that.name) && java.util.Objects.equals(version, that.version) && java.util.Objects.equals(provider, that.provider) && java.util.Objects.equals(summary, that.summary) && java.util.Objects.equals(minecraftVersionRange, that.minecraftVersionRange) && java.util.Objects.equals(openAllayApiVersionRange, that.openAllayApiVersionRange) && java.util.Objects.equals(artifacts, that.artifacts) && java.util.Objects.equals(source, that.source) && java.util.Objects.equals(requirements, that.requirements);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(name);
        hash = 31 * hash + java.util.Objects.hashCode(version);
        hash = 31 * hash + java.util.Objects.hashCode(provider);
        hash = 31 * hash + java.util.Objects.hashCode(summary);
        hash = 31 * hash + java.util.Objects.hashCode(minecraftVersionRange);
        hash = 31 * hash + java.util.Objects.hashCode(openAllayApiVersionRange);
        hash = 31 * hash + java.util.Objects.hashCode(artifacts);
        hash = 31 * hash + java.util.Objects.hashCode(source);
        hash = 31 * hash + java.util.Objects.hashCode(requirements);
        return hash;
    }
    @Override public String toString() { return "ExtensionCatalogEntry[id=" + id + ", name=" + name + ", version=" + version + ", provider=" + provider + ", summary=" + summary + ", minecraftVersionRange=" + minecraftVersionRange + ", openAllayApiVersionRange=" + openAllayApiVersionRange + ", artifacts=" + artifacts + ", source=" + source + ", requirements=" + requirements + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ExtensionCatalogEntry> schema() {
            return new dev.openallay.value.ValueSchema<>(ExtensionCatalogEntry.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ExtensionCatalogEntry>>asList(new dev.openallay.value.ValueSchema.Component<>(ExtensionCatalogEntry.class, "id", ExtensionCatalogEntry::id), new dev.openallay.value.ValueSchema.Component<>(ExtensionCatalogEntry.class, "name", ExtensionCatalogEntry::name), new dev.openallay.value.ValueSchema.Component<>(ExtensionCatalogEntry.class, "version", ExtensionCatalogEntry::version), new dev.openallay.value.ValueSchema.Component<>(ExtensionCatalogEntry.class, "provider", ExtensionCatalogEntry::provider), new dev.openallay.value.ValueSchema.Component<>(ExtensionCatalogEntry.class, "summary", ExtensionCatalogEntry::summary), new dev.openallay.value.ValueSchema.Component<>(ExtensionCatalogEntry.class, "minecraftVersionRange", ExtensionCatalogEntry::minecraftVersionRange), new dev.openallay.value.ValueSchema.Component<>(ExtensionCatalogEntry.class, "openAllayApiVersionRange", ExtensionCatalogEntry::openAllayApiVersionRange), new dev.openallay.value.ValueSchema.Component<>(ExtensionCatalogEntry.class, "artifacts", ExtensionCatalogEntry::artifacts), new dev.openallay.value.ValueSchema.Component<>(ExtensionCatalogEntry.class, "source", ExtensionCatalogEntry::source), new dev.openallay.value.ValueSchema.Component<>(ExtensionCatalogEntry.class, "requirements", ExtensionCatalogEntry::requirements)), arguments -> new ExtensionCatalogEntry((String) arguments[0], (String) arguments[1], (String) arguments[2], (String) arguments[3], (String) arguments[4], (String) arguments[5], (String) arguments[6], (List) arguments[7], (String) arguments[8], (RequirementSet) arguments[9]));
        }
    }
}
