package dev.openallay.extension.install;

import dev.openallay.requirement.RequirementKind;
import dev.openallay.requirement.RequirementSet;
import dev.openallay.settings.requirement.PreparedPackageInstall;
import dev.openallay.tool.ToolResult;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** Single-use validated Extension bytes, still invisible to the loader. */
public final class PreparedExtensionInstall implements PreparedPackageInstall {
    private final ExtensionPackageManifest manifest;
    private final String sha256;
    private final boolean catalogRequirementsDiffer;
    private Supplier<ExtensionInstallResult> publication;
    private final Runnable discard;

    PreparedExtensionInstall(
            ExtensionPackageManifest manifest, String sha256, boolean catalogRequirementsDiffer,
            Supplier<ExtensionInstallResult> publication, Runnable discard) {
        this.manifest = Objects.requireNonNull(manifest, "manifest");
        this.sha256 = Objects.requireNonNull(sha256, "sha256");
        this.catalogRequirementsDiffer = catalogRequirementsDiffer;
        this.publication = Objects.requireNonNull(publication, "publication");
        this.discard = Objects.requireNonNull(discard, "discard");
    }

    public ExtensionPackageManifest manifest() { return manifest; }
    @Override public RequirementKind kind() { return RequirementKind.EXTENSION; }
    @Override public String id() { return manifest.descriptor().id(); }
    @Override public String name() { return manifest.descriptor().name(); }
    @Override public String version() { return manifest.descriptor().version(); }
    @Override public String sha256() { return sha256; }
    @Override public RequirementSet requirements() { return manifest.descriptor().requirements(); }
    @Override public boolean catalogRequirementsDiffer() { return catalogRequirementsDiffer; }

    public synchronized ExtensionInstallResult commitInstall() {
        if (publication == null) {
            return new ExtensionInstallResult(id(), ExtensionInstallState.FAILED,
                    "prepared_install_consumed", Optional.empty(), Optional.empty(), "");
        }
        Supplier<ExtensionInstallResult> action = publication;
        publication = null;
        try {
            return action.get();
        } finally {
            discard.run();
        }
    }

    @Override
    public ToolResult<Boolean> commit() {
        ExtensionInstallResult result = commitInstall();
        return result.state() == ExtensionInstallState.RESTART_REQUIRED
                ? new ToolResult.Success<>(true)
                : new ToolResult.Failure<>(result.diagnostic(),
                        "The prepared Extension could not be published");
    }

    @Override
    public synchronized void close() {
        publication = null;
        discard.run();
    }
}
