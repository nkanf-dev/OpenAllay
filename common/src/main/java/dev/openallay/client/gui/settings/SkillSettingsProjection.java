package dev.openallay.client.gui.settings;

import dev.openallay.settings.skill.SkillSettingsView;
import dev.openallay.settings.skill.SkillCommunityView;
import dev.openallay.skill.SkillSource;
import dev.openallay.requirement.RequirementEnvironment;
import java.util.Map;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Readable Skill catalog projection with editing limited to explicit local overrides. */
public record SkillSettingsProjection(
        List<Skill> skills,
        Community community,
        int diagnosticCount,
        boolean debugMode) {
    public SkillSettingsProjection {
        skills = List.copyOf(skills);
        Objects.requireNonNull(community, "community");
        if (diagnosticCount < 0) {
            throw new IllegalArgumentException("diagnosticCount must not be negative");
        }
    }

    public static SkillSettingsProjection from(SkillSettingsView view, boolean debugMode) {
        return from(view, SkillCommunityView.unavailable(), debugMode);
    }

    public static SkillSettingsProjection from(
            SkillSettingsView view,
            SkillCommunityView community,
            boolean debugMode) {
        return from(view, community, new RequirementEnvironment(Map.of(), Map.of(), Map.of()), debugMode);
    }

    public static SkillSettingsProjection from(
            SkillSettingsView view,
            SkillCommunityView community,
            RequirementEnvironment environment,
            boolean debugMode) {
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(community, "community");
        return new SkillSettingsProjection(
                view.skills().stream().map(skill -> Skill.from(skill, environment)).toList(),
                Community.from(community),
                view.diagnostics().size(),
                debugMode);
    }

    public Optional<Skill> find(String name) {
        return skills.stream().filter(skill -> skill.name().equals(name)).findFirst();
    }

    public record Skill(
            String name,
            String description,
            String body,
            String markdown,
            SkillSource.Origin origin,
            boolean createsOverrideOnSave,
            boolean canDeleteOverride,
            String provenance,
            RequirementSettingsProjection requirements) {
        static Skill from(SkillSettingsView.Skill skill, RequirementEnvironment environment) {
            return new Skill(
                    skill.metadata().name(),
                    skill.metadata().description(),
                    skill.body(),
                    skill.markdown(),
                    skill.origin(),
                    skill.createsOverrideOnSave(),
                    skill.canDeleteOverride(),
                    skill.metadata().provenance(),
                    RequirementSettingsProjection.evaluate(skill.metadata().requirements(), environment));
        }

        public boolean localOverride() {
            return origin == SkillSource.Origin.LOCAL;
        }
    }

    public record Community(
            boolean available,
            Optional<Instant> generatedAt,
            List<Package> packages,
            Optional<Notice> notice) {
        public Community {
            generatedAt = Objects.requireNonNull(generatedAt, "generatedAt");
            packages = List.copyOf(packages);
            notice = Objects.requireNonNull(notice, "notice");
        }

        static Community from(SkillCommunityView view) {
            return new Community(
                    view.available(),
                    view.generatedAt(),
                    view.packages().stream().map(Package::from).toList(),
                    view.notice().map(value -> new Notice(value.code(), value.message())));
        }

        public Optional<Package> find(String id) {
            return packages.stream().filter(value -> value.id().equals(id)).findFirst();
        }
    }

    public record Package(
            String id,
            String displayName,
            String description,
            String publisher,
            String version,
            PackageState state,
            String source,
            String archive,
            String sha256) {
        static Package from(SkillCommunityView.Package value) {
            PackageState state;
            if (!value.compatible()) {
                state = PackageState.INCOMPATIBLE;
            } else if (value.installed() && value.updateAvailable()) {
                state = PackageState.UPDATE_AVAILABLE;
            } else if (value.installed()) {
                state = PackageState.INSTALLED;
            } else {
                state = PackageState.AVAILABLE;
            }
            return new Package(
                    value.id(),
                    value.displayName(),
                    value.description(),
                    value.publisher(),
                    value.availableVersion(),
                    state,
                    value.source(),
                    value.archive(),
                    value.sha256());
        }

        public boolean installable() {
            return state == PackageState.AVAILABLE || state == PackageState.UPDATE_AVAILABLE;
        }
    }

    public enum PackageState {
        AVAILABLE,
        INSTALLED,
        UPDATE_AVAILABLE,
        INCOMPATIBLE
    }

    public record Notice(String code, String message) {}
}
