package dev.openallay.extension.install;

import java.nio.file.Path;
import java.util.Optional;

public record ExtensionInstallResult(
        String extensionId,
        ExtensionInstallState state,
        String diagnostic,
        Optional<Path> stagedArtifact,
        Optional<ExtensionPackageManifest> manifest,
        String sha256) {
    public ExtensionInstallResult {
        extensionId = extensionId == null ? "" : extensionId;
        java.util.Objects.requireNonNull(state, "state");
        diagnostic = diagnostic == null ? "" : diagnostic;
        stagedArtifact = java.util.Objects.requireNonNull(stagedArtifact, "stagedArtifact");
        manifest = java.util.Objects.requireNonNull(manifest, "manifest");
        sha256 = sha256 == null ? "" : sha256;
        if (state == ExtensionInstallState.RESTART_REQUIRED
                && (manifest.isEmpty() || sha256.isBlank())) {
            throw new IllegalArgumentException(
                    "A staged Extension must retain its validated package metadata");
        }
    }
}
