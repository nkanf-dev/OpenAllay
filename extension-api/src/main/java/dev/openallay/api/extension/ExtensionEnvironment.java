package dev.openallay.api.extension;

import java.util.Set;
import java.util.Objects;
import java.util.Collections;
import java.util.LinkedHashSet;

/** Actual runtime facts. Host features and API versions are not permission grants. */
public final class ExtensionEnvironment {
    private final String loader;
    private final String minecraftVersion;
    private final String openAllayVersion;
    private final Set<String> openAllayApiVersions;
    private final int javaVersion;
    private final Set<String> hostFeatures;

    public ExtensionEnvironment(
            String loader,
            String minecraftVersion,
            String openAllayVersion,
            Set<String> openAllayApiVersions,
            int javaVersion,
            Set<String> hostFeatures) {
        this.loader = ApiValidation.loader(loader);
        this.minecraftVersion = ApiValidation.version(minecraftVersion, "Minecraft version");
        this.openAllayVersion = ApiValidation.version(openAllayVersion, "core version");
        this.openAllayApiVersions = requireApiVersions(openAllayApiVersions);
        this.javaVersion = ApiValidation.javaVersion(javaVersion);
        this.hostFeatures = ApiValidation.ids(hostFeatures, "host feature ID", false);
    }

    public String loader() { return loader; }
    public String minecraftVersion() { return minecraftVersion; }
    public String openAllayVersion() { return openAllayVersion; }
    public Set<String> openAllayApiVersions() { return openAllayApiVersions; }
    public int javaVersion() { return javaVersion; }
    public Set<String> hostFeatures() { return hostFeatures; }

    private static Set<String> requireApiVersions(Set<String> versions) {
        LinkedHashSet<String> copy = new LinkedHashSet<String>();
        for (String version : Objects.requireNonNull(versions, "openAllayApiVersions"))
            copy.add(ApiValidation.version(version, "public API version"));
        if (copy.isEmpty()) throw new IllegalArgumentException("At least one implemented API version is required");
        return Collections.unmodifiableSet(copy);
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ExtensionEnvironment)) return false;
        ExtensionEnvironment that = (ExtensionEnvironment) other;
        return Objects.equals(loader, that.loader) &&
                Objects.equals(minecraftVersion, that.minecraftVersion) &&
                Objects.equals(openAllayVersion, that.openAllayVersion) &&
                Objects.equals(openAllayApiVersions, that.openAllayApiVersions) &&
                javaVersion == that.javaVersion &&
                Objects.equals(hostFeatures, that.hostFeatures);
    }
    @Override public int hashCode() { return Objects.hash(loader, minecraftVersion, openAllayVersion, openAllayApiVersions, javaVersion, hostFeatures); }
}
