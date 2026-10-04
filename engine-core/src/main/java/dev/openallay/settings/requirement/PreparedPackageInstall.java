package dev.openallay.settings.requirement;

import dev.openallay.requirement.RequirementKind;
import dev.openallay.requirement.RequirementSet;
import dev.openallay.tool.ToolResult;

/** An unpublished, validated candidate. Commit or close consumes this handle exactly once. */
public interface PreparedPackageInstall extends AutoCloseable {
    RequirementKind kind();

    String id();

    default String name() {
        return id();
    }

    String version();

    /** SHA-256 of the captured candidate, not a mutable source path. */
    String sha256();

    RequirementSet requirements();

    default boolean catalogRequirementsDiffer() {
        return false;
    }

    ToolResult<Boolean> commit();

    /** Discards an unpublished candidate. This never publishes or changes settings. */
    @Override
    void close();
}
