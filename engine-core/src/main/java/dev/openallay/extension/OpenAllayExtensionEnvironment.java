package dev.openallay.extension;

import java.util.Set;

/** Actual implemented public API coordinates; the legacy primary accessor is retained. */
@dev.openallay.value.ValueType(OpenAllayExtensionEnvironment.ValueSchemaProvider.class)
public final class OpenAllayExtensionEnvironment {
    private final String loader;
    private final String minecraftVersion;
    private final String openAllayApiVersion;
    private final Set<String> implementedApiVersions;
    public OpenAllayExtensionEnvironment(String loader, String minecraftVersion, String openAllayApiVersion, Set<String> implementedApiVersions) {

        loader = require(loader, "loader").toLowerCase(java.util.Locale.ROOT);
        minecraftVersion = require(minecraftVersion, "minecraftVersion");
        openAllayApiVersion = require(openAllayApiVersion, "openAllayApiVersion");
        implementedApiVersions = dev.openallay.util.Java8Collections.setCopyOf(implementedApiVersions);
        implementedApiVersions.forEach(version -> require(version, "implemented API version"));
        if (!implementedApiVersions.contains(openAllayApiVersion)) {
            throw new IllegalArgumentException("Implemented APIs must include the legacy primary API");
        }

        this.loader = loader;
        this.minecraftVersion = minecraftVersion;
        this.openAllayApiVersion = openAllayApiVersion;
        this.implementedApiVersions = implementedApiVersions;
    }
    public String loader() { return loader; }
    public String minecraftVersion() { return minecraftVersion; }
    public String openAllayApiVersion() { return openAllayApiVersion; }
    public Set<String> implementedApiVersions() { return implementedApiVersions; }
public OpenAllayExtensionEnvironment(String loader, String minecraftVersion, String openAllayApiVersion) {
        this(loader, minecraftVersion, openAllayApiVersion, dev.openallay.util.Java8Collections.setOf(openAllayApiVersion));
    }
public String incompatibility(OpenAllayExtensionDescriptor descriptor) {
        if (!descriptor.loaders().contains(loader)) return "incompatible_loader";
        if (!ExtensionCompatibility.includes(descriptor.minecraftVersionRange(), minecraftVersion)) {
            return "incompatible_game_version";
        }
        if (implementedApiVersions.stream().noneMatch(version -> ExtensionCompatibility.includes(
                descriptor.openAllayApiVersionRange(), version))) {
            return "incompatible_openallay_api";
        }
        return "";
    }
private static String require(String value, String name) {
        if (value == null || dev.openallay.util.Java8Strings.isBlank(value)) throw new IllegalArgumentException(name + " must not be blank");
        return dev.openallay.util.Java8Strings.strip(value);
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof OpenAllayExtensionEnvironment)) return false;
        OpenAllayExtensionEnvironment that = (OpenAllayExtensionEnvironment) other;
        return java.util.Objects.equals(loader, that.loader) && java.util.Objects.equals(minecraftVersion, that.minecraftVersion) && java.util.Objects.equals(openAllayApiVersion, that.openAllayApiVersion) && java.util.Objects.equals(implementedApiVersions, that.implementedApiVersions);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(loader);
        hash = 31 * hash + java.util.Objects.hashCode(minecraftVersion);
        hash = 31 * hash + java.util.Objects.hashCode(openAllayApiVersion);
        hash = 31 * hash + java.util.Objects.hashCode(implementedApiVersions);
        return hash;
    }
    @Override public String toString() { return "OpenAllayExtensionEnvironment[loader=" + loader + ", minecraftVersion=" + minecraftVersion + ", openAllayApiVersion=" + openAllayApiVersion + ", implementedApiVersions=" + implementedApiVersions + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<OpenAllayExtensionEnvironment> schema() {
            return new dev.openallay.value.ValueSchema<>(OpenAllayExtensionEnvironment.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<OpenAllayExtensionEnvironment>>asList(new dev.openallay.value.ValueSchema.Component<>(OpenAllayExtensionEnvironment.class, "loader", OpenAllayExtensionEnvironment::loader), new dev.openallay.value.ValueSchema.Component<>(OpenAllayExtensionEnvironment.class, "minecraftVersion", OpenAllayExtensionEnvironment::minecraftVersion), new dev.openallay.value.ValueSchema.Component<>(OpenAllayExtensionEnvironment.class, "openAllayApiVersion", OpenAllayExtensionEnvironment::openAllayApiVersion), new dev.openallay.value.ValueSchema.Component<>(OpenAllayExtensionEnvironment.class, "implementedApiVersions", OpenAllayExtensionEnvironment::implementedApiVersions)), arguments -> new OpenAllayExtensionEnvironment((String) arguments[0], (String) arguments[1], (String) arguments[2], (Set) arguments[3]));
        }
    }
}
