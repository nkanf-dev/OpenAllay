package dev.openallay.extension.install;

import java.nio.file.Path;
import java.util.Optional;

@dev.openallay.value.ValueType(ExtensionInstallResult.ValueSchemaProvider.class)
public final class ExtensionInstallResult {
    private final String extensionId;
    private final ExtensionInstallState state;
    private final String diagnostic;
    private final Optional<Path> stagedArtifact;
    private final Optional<ExtensionPackageManifest> manifest;
    private final String sha256;
    public ExtensionInstallResult(String extensionId, ExtensionInstallState state, String diagnostic, Optional<Path> stagedArtifact, Optional<ExtensionPackageManifest> manifest, String sha256) {

        extensionId = extensionId == null ? "" : extensionId;
        java.util.Objects.requireNonNull(state, "state");
        diagnostic = diagnostic == null ? "" : diagnostic;
        stagedArtifact = java.util.Objects.requireNonNull(stagedArtifact, "stagedArtifact");
        manifest = java.util.Objects.requireNonNull(manifest, "manifest");
        sha256 = sha256 == null ? "" : sha256;
        if (state == ExtensionInstallState.RESTART_REQUIRED
                && (!manifest.isPresent() || dev.openallay.util.Java8Strings.isBlank(sha256))) {
            throw new IllegalArgumentException(
                    "A staged Extension must retain its validated package metadata");
        }

        this.extensionId = extensionId;
        this.state = state;
        this.diagnostic = diagnostic;
        this.stagedArtifact = stagedArtifact;
        this.manifest = manifest;
        this.sha256 = sha256;
    }
    public String extensionId() { return extensionId; }
    public ExtensionInstallState state() { return state; }
    public String diagnostic() { return diagnostic; }
    public Optional<Path> stagedArtifact() { return stagedArtifact; }
    public Optional<ExtensionPackageManifest> manifest() { return manifest; }
    public String sha256() { return sha256; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ExtensionInstallResult)) return false;
        ExtensionInstallResult that = (ExtensionInstallResult) other;
        return java.util.Objects.equals(extensionId, that.extensionId) && java.util.Objects.equals(state, that.state) && java.util.Objects.equals(diagnostic, that.diagnostic) && java.util.Objects.equals(stagedArtifact, that.stagedArtifact) && java.util.Objects.equals(manifest, that.manifest) && java.util.Objects.equals(sha256, that.sha256);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(extensionId);
        hash = 31 * hash + java.util.Objects.hashCode(state);
        hash = 31 * hash + java.util.Objects.hashCode(diagnostic);
        hash = 31 * hash + java.util.Objects.hashCode(stagedArtifact);
        hash = 31 * hash + java.util.Objects.hashCode(manifest);
        hash = 31 * hash + java.util.Objects.hashCode(sha256);
        return hash;
    }
    @Override public String toString() { return "ExtensionInstallResult[extensionId=" + extensionId + ", state=" + state + ", diagnostic=" + diagnostic + ", stagedArtifact=" + stagedArtifact + ", manifest=" + manifest + ", sha256=" + sha256 + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ExtensionInstallResult> schema() {
            return new dev.openallay.value.ValueSchema<>(ExtensionInstallResult.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ExtensionInstallResult>>asList(new dev.openallay.value.ValueSchema.Component<>(ExtensionInstallResult.class, "extensionId", ExtensionInstallResult::extensionId), new dev.openallay.value.ValueSchema.Component<>(ExtensionInstallResult.class, "state", ExtensionInstallResult::state), new dev.openallay.value.ValueSchema.Component<>(ExtensionInstallResult.class, "diagnostic", ExtensionInstallResult::diagnostic), new dev.openallay.value.ValueSchema.Component<>(ExtensionInstallResult.class, "stagedArtifact", ExtensionInstallResult::stagedArtifact), new dev.openallay.value.ValueSchema.Component<>(ExtensionInstallResult.class, "manifest", ExtensionInstallResult::manifest), new dev.openallay.value.ValueSchema.Component<>(ExtensionInstallResult.class, "sha256", ExtensionInstallResult::sha256)), arguments -> new ExtensionInstallResult((String) arguments[0], (ExtensionInstallState) arguments[1], (String) arguments[2], (Optional) arguments[3], (Optional) arguments[4], (String) arguments[5]));
        }
    }
}
