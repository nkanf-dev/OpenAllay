package dev.openallay.api.extension;

import java.util.List;
import java.util.Set;
import java.util.Objects;
import java.util.HashSet;

/** Compatibility declarations and separately recorded validation facts; no authority grants. */
public final class SupportDeclaration {
    private final List<SupportTarget> targets;
    private final int minimumJavaVersion;
    private final Set<String> requiredHostFeatures;
    private final Set<String> validatedTargetIds;

    public SupportDeclaration(
            List<SupportTarget> targets,
            int minimumJavaVersion,
            Set<String> requiredHostFeatures,
            Set<String> validatedTargetIds) {
        this.targets = requireTargets(targets);
        this.minimumJavaVersion = ApiValidation.javaVersion(minimumJavaVersion);
        this.requiredHostFeatures = ApiValidation.ids(requiredHostFeatures, "host feature ID", false);
        this.validatedTargetIds = ApiValidation.ids(validatedTargetIds, "validated target ID", false);
    }

    public List<SupportTarget> targets() { return targets; }
    public int minimumJavaVersion() { return minimumJavaVersion; }
    public Set<String> requiredHostFeatures() { return requiredHostFeatures; }
    public Set<String> validatedTargetIds() { return validatedTargetIds; }

    private static List<SupportTarget> requireTargets(List<SupportTarget> targets) {
        List<SupportTarget> copy = ApiValidation.list(targets, "targets");
        if (copy.isEmpty()) throw new IllegalArgumentException("At least one support target is required");
        if (new HashSet<SupportTarget>(copy).size() != copy.size())
            throw new IllegalArgumentException("Duplicate support target");
        return copy;
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SupportDeclaration)) return false;
        SupportDeclaration that = (SupportDeclaration) other;
        return Objects.equals(targets, that.targets) &&
                minimumJavaVersion == that.minimumJavaVersion &&
                Objects.equals(requiredHostFeatures, that.requiredHostFeatures) &&
                Objects.equals(validatedTargetIds, that.validatedTargetIds);
    }
    @Override public int hashCode() { return Objects.hash(targets, minimumJavaVersion, requiredHostFeatures, validatedTargetIds); }
}
