package dev.openallay.extension;

import dev.openallay.internal.maven.artifact.versioning.ComparableVersion;
import dev.openallay.internal.maven.artifact.versioning.DefaultArtifactVersion;
import dev.openallay.internal.maven.artifact.versioning.InvalidVersionSpecificationException;
import dev.openallay.internal.maven.artifact.versioning.VersionRange;

/** Public game/core/API ranges use Maven's mature version and interval semantics. */
public final class ExtensionCompatibility {
    private ExtensionCompatibility() {}

    public static String requireRange(String value, String name) {
        if (value == null || dev.openallay.util.Java8Strings.isBlank(value)) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        String range = dev.openallay.util.Java8Strings.strip(value);
        if (range.equals("[]")) throw new IllegalArgumentException("Invalid version range: " + range);
        try {
            VersionRange parsed = VersionRange.createFromVersionSpec(range);
            if (range.startsWith("[") || range.startsWith("(")) {
                if (parsed.getRecommendedVersion() != null || parsed.getRestrictions().isEmpty()) {
                    throw new IllegalArgumentException("Invalid version range: " + range);
                }
            } else if (range.contains(",") || range.contains("[") || range.contains("]")
                    || range.contains("(") || range.contains(")")) {
                throw new IllegalArgumentException("Invalid version range: " + range);
            }
            return range;
        } catch (InvalidVersionSpecificationException malformed) {
            throw new IllegalArgumentException("Invalid version range: " + range, malformed);
        }
    }

    public static boolean includes(String range, String version) {
        range = requireRange(range, "range");
        if (version == null || dev.openallay.util.Java8Strings.isBlank(version)) {
            throw new IllegalArgumentException("version must not be blank");
        }
        if (!(range.startsWith("[") || range.startsWith("("))) {
            // A bare version is an exact public declaration, not Maven's recommended-any form.
            return new ComparableVersion(dev.openallay.util.Java8Strings.strip(version)).compareTo(new ComparableVersion(range)) == 0;
        }
        try {
            return VersionRange.createFromVersionSpec(range).containsVersion(
                    new DefaultArtifactVersion(dev.openallay.util.Java8Strings.strip(version)));
        } catch (InvalidVersionSpecificationException malformed) {
            throw new IllegalArgumentException("Invalid version range: " + range, malformed);
        }
    }
}
