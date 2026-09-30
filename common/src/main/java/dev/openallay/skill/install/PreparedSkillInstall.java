package dev.openallay.skill.install;

import dev.openallay.requirement.RequirementKind;
import dev.openallay.requirement.RequirementSet;
import dev.openallay.settings.requirement.PreparedPackageInstall;
import dev.openallay.skill.SkillMetadata;
import dev.openallay.tool.ToolResult;
import java.util.Objects;
import java.util.function.Supplier;

/** Private captured Skill bytes; construction and disposal never publish a Skill. */
public final class PreparedSkillInstall implements PreparedPackageInstall {
    private final SkillMetadata metadata;
    private final String sha256;
    private final String version;
    private final String provenance;
    private Supplier<ToolResult<Boolean>> publication;
    private final Runnable discard;

    PreparedSkillInstall(
            SkillMetadata metadata, String sha256, String version, String provenance,
            Supplier<ToolResult<Boolean>> publication, Runnable discard) {
        this.metadata = Objects.requireNonNull(metadata, "metadata");
        this.sha256 = Objects.requireNonNull(sha256, "sha256");
        this.version = Objects.requireNonNull(version, "version");
        this.provenance = Objects.requireNonNull(provenance, "provenance");
        this.publication = Objects.requireNonNull(publication, "publication");
        this.discard = Objects.requireNonNull(discard, "discard");
    }

    public SkillMetadata metadata() { return metadata; }
    public String provenance() { return provenance; }
    @Override public RequirementKind kind() { return RequirementKind.SKILL; }
    @Override public String id() { return metadata.name(); }
    @Override public String version() { return version; }
    @Override public String sha256() { return sha256; }
    @Override public RequirementSet requirements() { return metadata.requirements(); }

    @Override
    public synchronized ToolResult<Boolean> commit() {
        if (publication == null) {
            return new ToolResult.Failure<>(
                    "prepared_install_consumed", "The prepared Skill is no longer available");
        }
        Supplier<ToolResult<Boolean>> action = publication;
        publication = null;
        try {
            return action.get();
        } finally {
            discard.run();
        }
    }

    @Override
    public synchronized void close() {
        publication = null;
        discard.run();
    }
}
