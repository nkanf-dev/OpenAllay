package dev.openallay.settings.requirement;

import dev.openallay.requirement.RequirementKind;
import dev.openallay.requirement.RequirementSet;
import dev.openallay.tool.ToolResult;
import java.util.Objects;

/** Keeps a settings backend's view in sync only after successful publication. */
public final class RefreshingPreparedPackageInstall implements PreparedPackageInstall {
    private final PreparedPackageInstall candidate;
    private final Object ownerLock;
    private final Runnable refresh;

    public RefreshingPreparedPackageInstall(
            PreparedPackageInstall candidate, Object ownerLock, Runnable refresh) {
        this.candidate = Objects.requireNonNull(candidate, "candidate");
        this.ownerLock = Objects.requireNonNull(ownerLock, "ownerLock");
        this.refresh = Objects.requireNonNull(refresh, "refresh");
    }

    @Override public RequirementKind kind() { return candidate.kind(); }
    @Override public String id() { return candidate.id(); }
    @Override public String name() { return candidate.name(); }
    @Override public String version() { return candidate.version(); }
    @Override public String sha256() { return candidate.sha256(); }
    @Override public RequirementSet requirements() { return candidate.requirements(); }
    @Override public boolean catalogRequirementsDiffer() {
        return candidate.catalogRequirementsDiffer();
    }

    @Override
    public ToolResult<Boolean> commit() {
        synchronized (ownerLock) {
            ToolResult<Boolean> result = candidate.commit();
            if (result instanceof ToolResult.Success<Boolean>) {
                try {
                    refresh.run();
                } catch (RuntimeException failure) {
                    return new ToolResult.Failure<>("package_projection_failed",
                            "Package published, but settings view could not be refreshed");
                }
            }
            return result;
        }
    }

    @Override public void close() { candidate.close(); }
}
