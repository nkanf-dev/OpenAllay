package dev.openallay.api.extension;

import java.util.Objects;

/** Immutable identity and support metadata. Descriptor lookup must not touch host or native state. */
public final class ExtensionDescriptor {
    private final String id;
    private final String name;
    private final String version;
    private final String provider;
    private final String summary;
    private final String source;
    private final SupportDeclaration support;
    private final ExtensionRequirements requirements;

    public ExtensionDescriptor(
            String id,
            String name,
            String version,
            String provider,
            String summary,
            String source,
            SupportDeclaration support,
            ExtensionRequirements requirements) {
        this.id = ApiValidation.id(id, "Extension ID");
        this.name = ApiValidation.text(name, "name");
        this.version = ApiValidation.version(version, "Extension version");
        this.provider = ApiValidation.text(provider, "provider");
        this.summary = ApiValidation.text(summary, "summary");
        this.source = ApiValidation.text(source, "source");
        this.support = Objects.requireNonNull(support, "support");
        this.requirements = Objects.requireNonNull(requirements, "requirements");
    }

    public String id() { return id; }
    public String name() { return name; }
    public String version() { return version; }
    public String provider() { return provider; }
    public String summary() { return summary; }
    public String source() { return source; }
    public SupportDeclaration support() { return support; }
    public ExtensionRequirements requirements() { return requirements; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ExtensionDescriptor)) return false;
        ExtensionDescriptor that = (ExtensionDescriptor) other;
        return Objects.equals(id, that.id) &&
                Objects.equals(name, that.name) &&
                Objects.equals(version, that.version) &&
                Objects.equals(provider, that.provider) &&
                Objects.equals(summary, that.summary) &&
                Objects.equals(source, that.source) &&
                Objects.equals(support, that.support) &&
                Objects.equals(requirements, that.requirements);
    }
    @Override public int hashCode() { return Objects.hash(id, name, version, provider, summary, source, support, requirements); }
}
