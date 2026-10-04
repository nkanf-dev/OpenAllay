package dev.openallay.settings.skill;

import dev.openallay.skill.SkillDiagnostic;
import dev.openallay.skill.SkillMetadata;
import dev.openallay.skill.SkillSource;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Immutable player-settings projection of the validated Agent Skills catalog. */
public record SkillSettingsView(List<Skill> skills, List<SkillDiagnostic> diagnostics) {
    public SkillSettingsView {
        skills = List.copyOf(skills).stream()
                .sorted(Comparator.comparing(skill -> skill.metadata().name()))
                .toList();
        diagnostics = List.copyOf(diagnostics);
    }

    public static SkillSettingsView empty() {
        return new SkillSettingsView(List.of(), List.of());
    }

    public Optional<Skill> find(String name) {
        return skills.stream().filter(skill -> skill.metadata().name().equals(name)).findFirst();
    }

    public record Skill(
            SkillMetadata metadata,
            String body,
            String markdown,
            boolean overridePresent) {
        public Skill {
            metadata = java.util.Objects.requireNonNull(metadata, "metadata");
            if (body == null || body.isBlank()) {
                throw new IllegalArgumentException("Skill body must not be blank");
            }
            if (markdown == null || markdown.isBlank()) {
                throw new IllegalArgumentException("Skill Markdown must not be blank");
            }
        }

        public SkillSource.Origin origin() {
            return metadata.origin();
        }

        /** Immutable bundled and Extension documents are edited through local overrides. */
        public boolean createsOverrideOnSave() {
            return (metadata.origin() == SkillSource.Origin.BUNDLED
                    || metadata.origin() == SkillSource.Origin.EXTERNAL) && !overridePresent;
        }

        public boolean canDeleteOverride() {
            return overridePresent;
        }
    }
}
