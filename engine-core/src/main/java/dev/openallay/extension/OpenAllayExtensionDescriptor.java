package dev.openallay.extension;

import dev.openallay.requirement.RequirementSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** Stable identity and compatibility metadata declared by an Extension JAR. */
@dev.openallay.value.ValueType(OpenAllayExtensionDescriptor.ValueSchemaProvider.class)
public final class OpenAllayExtensionDescriptor {
    private final String id;
    private final String name;
    private final String version;
    private final String provider;
    private final String summary;
    private final Set<String> loaders;
    private final String minecraftVersionRange;
    private final String openAllayApiVersionRange;
    private final String source;
    private final RequirementSet requirements;
    public OpenAllayExtensionDescriptor(String id, String name, String version, String provider, String summary, Set<String> loaders, String minecraftVersionRange, String openAllayApiVersionRange, String source, RequirementSet requirements) {

        id = require(id, "id");
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid Extension ID: " + id);
        }
        name = require(name, "name");
        version = require(version, "version");
        provider = require(provider, "provider");
        summary = require(summary, "summary");
        TreeSet<String> normalizedLoaders = new TreeSet<>();
        for (String loader : dev.openallay.util.Java8Collections.setCopyOf(loaders)) {
            normalizedLoaders.add(require(loader, "loader").toLowerCase(java.util.Locale.ROOT));
        }
        if (normalizedLoaders.isEmpty()) {
            throw new IllegalArgumentException("Extension must declare at least one loader");
        }
        loaders = dev.openallay.util.Java8Collections.setCopyOf(normalizedLoaders);
        minecraftVersionRange = ExtensionCompatibility.requireRange(
                minecraftVersionRange, "minecraftVersionRange");
        openAllayApiVersionRange = ExtensionCompatibility.requireRange(
                openAllayApiVersionRange, "openAllayApiVersionRange");
        source = require(source, "source");
        requirements = java.util.Objects.requireNonNull(requirements, "requirements");

        this.id = id;
        this.name = name;
        this.version = version;
        this.provider = provider;
        this.summary = summary;
        this.loaders = loaders;
        this.minecraftVersionRange = minecraftVersionRange;
        this.openAllayApiVersionRange = openAllayApiVersionRange;
        this.source = source;
        this.requirements = requirements;
    }
    public String id() { return id; }
    public String name() { return name; }
    public String version() { return version; }
    public String provider() { return provider; }
    public String summary() { return summary; }
    public Set<String> loaders() { return loaders; }
    public String minecraftVersionRange() { return minecraftVersionRange; }
    public String openAllayApiVersionRange() { return openAllayApiVersionRange; }
    public String source() { return source; }
    public RequirementSet requirements() { return requirements; }
private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
public OpenAllayExtensionDescriptor(
            String id, String name, String version, String provider, String summary,
            Set<String> loaders, String minecraftVersionRange, String openAllayApiVersionRange,
            String source) {
        this(id, name, version, provider, summary, loaders, minecraftVersionRange,
                openAllayApiVersionRange, source, RequirementSet.EMPTY);
    }
private static String require(String value, String name) {
        if (value == null || dev.openallay.util.Java8Strings.isBlank(value)) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return dev.openallay.util.Java8Strings.strip(value);
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof OpenAllayExtensionDescriptor)) return false;
        OpenAllayExtensionDescriptor that = (OpenAllayExtensionDescriptor) other;
        return java.util.Objects.equals(id, that.id) && java.util.Objects.equals(name, that.name) && java.util.Objects.equals(version, that.version) && java.util.Objects.equals(provider, that.provider) && java.util.Objects.equals(summary, that.summary) && java.util.Objects.equals(loaders, that.loaders) && java.util.Objects.equals(minecraftVersionRange, that.minecraftVersionRange) && java.util.Objects.equals(openAllayApiVersionRange, that.openAllayApiVersionRange) && java.util.Objects.equals(source, that.source) && java.util.Objects.equals(requirements, that.requirements);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(name);
        hash = 31 * hash + java.util.Objects.hashCode(version);
        hash = 31 * hash + java.util.Objects.hashCode(provider);
        hash = 31 * hash + java.util.Objects.hashCode(summary);
        hash = 31 * hash + java.util.Objects.hashCode(loaders);
        hash = 31 * hash + java.util.Objects.hashCode(minecraftVersionRange);
        hash = 31 * hash + java.util.Objects.hashCode(openAllayApiVersionRange);
        hash = 31 * hash + java.util.Objects.hashCode(source);
        hash = 31 * hash + java.util.Objects.hashCode(requirements);
        return hash;
    }
    @Override public String toString() { return "OpenAllayExtensionDescriptor[id=" + id + ", name=" + name + ", version=" + version + ", provider=" + provider + ", summary=" + summary + ", loaders=" + loaders + ", minecraftVersionRange=" + minecraftVersionRange + ", openAllayApiVersionRange=" + openAllayApiVersionRange + ", source=" + source + ", requirements=" + requirements + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<OpenAllayExtensionDescriptor> schema() {
            return new dev.openallay.value.ValueSchema<>(OpenAllayExtensionDescriptor.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<OpenAllayExtensionDescriptor>>asList(new dev.openallay.value.ValueSchema.Component<>(OpenAllayExtensionDescriptor.class, "id", OpenAllayExtensionDescriptor::id), new dev.openallay.value.ValueSchema.Component<>(OpenAllayExtensionDescriptor.class, "name", OpenAllayExtensionDescriptor::name), new dev.openallay.value.ValueSchema.Component<>(OpenAllayExtensionDescriptor.class, "version", OpenAllayExtensionDescriptor::version), new dev.openallay.value.ValueSchema.Component<>(OpenAllayExtensionDescriptor.class, "provider", OpenAllayExtensionDescriptor::provider), new dev.openallay.value.ValueSchema.Component<>(OpenAllayExtensionDescriptor.class, "summary", OpenAllayExtensionDescriptor::summary), new dev.openallay.value.ValueSchema.Component<>(OpenAllayExtensionDescriptor.class, "loaders", OpenAllayExtensionDescriptor::loaders), new dev.openallay.value.ValueSchema.Component<>(OpenAllayExtensionDescriptor.class, "minecraftVersionRange", OpenAllayExtensionDescriptor::minecraftVersionRange), new dev.openallay.value.ValueSchema.Component<>(OpenAllayExtensionDescriptor.class, "openAllayApiVersionRange", OpenAllayExtensionDescriptor::openAllayApiVersionRange), new dev.openallay.value.ValueSchema.Component<>(OpenAllayExtensionDescriptor.class, "source", OpenAllayExtensionDescriptor::source), new dev.openallay.value.ValueSchema.Component<>(OpenAllayExtensionDescriptor.class, "requirements", OpenAllayExtensionDescriptor::requirements)), arguments -> new OpenAllayExtensionDescriptor((String) arguments[0], (String) arguments[1], (String) arguments[2], (String) arguments[3], (String) arguments[4], (Set) arguments[5], (String) arguments[6], (String) arguments[7], (String) arguments[8], (RequirementSet) arguments[9]));
        }
    }
}
