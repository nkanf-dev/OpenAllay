package dev.openallay.extension.install;

import dev.openallay.extension.OpenAllayExtensionDescriptor;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Identity and compatibility metadata embedded in an installable Extension JAR.
 *
 * <p>The package manifest is the authority for local imports. A community catalog may describe
 * the same artifact, but it is never required merely to recognize a local package.
 */
@dev.openallay.value.ValueType(ExtensionPackageManifest.ValueSchemaProvider.class)
public final class ExtensionPackageManifest {
    private final int schemaVersion;
    private final OpenAllayExtensionDescriptor descriptor;
    private final Set<String> modIds;
    public ExtensionPackageManifest(int schemaVersion, OpenAllayExtensionDescriptor descriptor, Set<String> modIds) {

        if (schemaVersion != 1) {
            throw new IllegalArgumentException(
                    "Unsupported Extension package schema: " + schemaVersion);
        }
        java.util.Objects.requireNonNull(descriptor, "descriptor");
        TreeSet<String> normalized = new TreeSet<>();
        for (String modId : Set.copyOf(modIds)) {
            if (modId == null || !MOD_ID.matcher(modId).matches()) {
                throw new IllegalArgumentException("Invalid Extension mod ID: " + modId);
            }
            normalized.add(modId);
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    "Extension package must declare at least one mod ID");
        }
        modIds = Set.copyOf(normalized);

        this.schemaVersion = schemaVersion;
        this.descriptor = descriptor;
        this.modIds = modIds;
    }
    public int schemaVersion() { return schemaVersion; }
    public OpenAllayExtensionDescriptor descriptor() { return descriptor; }
    public Set<String> modIds() { return modIds; }
public static final String JAR_PATH = "META-INF/openallay-extension.json";
private static final Pattern MOD_ID = Pattern.compile("[a-z0-9_.-]+");
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ExtensionPackageManifest)) return false;
        ExtensionPackageManifest that = (ExtensionPackageManifest) other;
        return schemaVersion == that.schemaVersion && java.util.Objects.equals(descriptor, that.descriptor) && java.util.Objects.equals(modIds, that.modIds);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Integer.hashCode(schemaVersion);
        hash = 31 * hash + java.util.Objects.hashCode(descriptor);
        hash = 31 * hash + java.util.Objects.hashCode(modIds);
        return hash;
    }
    @Override public String toString() { return "ExtensionPackageManifest[schemaVersion=" + schemaVersion + ", descriptor=" + descriptor + ", modIds=" + modIds + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ExtensionPackageManifest> schema() {
            return new dev.openallay.value.ValueSchema<>(ExtensionPackageManifest.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ExtensionPackageManifest>>asList(new dev.openallay.value.ValueSchema.Component<>(ExtensionPackageManifest.class, "schemaVersion", ExtensionPackageManifest::schemaVersion), new dev.openallay.value.ValueSchema.Component<>(ExtensionPackageManifest.class, "descriptor", ExtensionPackageManifest::descriptor), new dev.openallay.value.ValueSchema.Component<>(ExtensionPackageManifest.class, "modIds", ExtensionPackageManifest::modIds)), arguments -> new ExtensionPackageManifest((Integer) arguments[0], (OpenAllayExtensionDescriptor) arguments[1], (Set) arguments[2]));
        }
    }
}
