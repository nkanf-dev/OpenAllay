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
public record ExtensionPackageManifest(
        int schemaVersion,
        OpenAllayExtensionDescriptor descriptor,
        Set<String> modIds) {
    public static final String JAR_PATH = "META-INF/openallay-extension.json";
    private static final Pattern MOD_ID = Pattern.compile("[a-z0-9_.-]+");

    public ExtensionPackageManifest {
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
    }
}
