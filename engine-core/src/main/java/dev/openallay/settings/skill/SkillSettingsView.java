package dev.openallay.settings.skill;

import dev.openallay.skill.SkillDiagnostic;
import dev.openallay.skill.SkillMetadata;
import dev.openallay.skill.SkillSource;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Immutable player-settings projection of the validated Agent Skills catalog. */
@dev.openallay.value.ValueType(SkillSettingsView.ValueSchemaProvider.class)
public final class SkillSettingsView {
    private final List<Skill> skills;
    private final List<SkillDiagnostic> diagnostics;
    public SkillSettingsView(List<Skill> skills, List<SkillDiagnostic> diagnostics) {

        skills = dev.openallay.util.Java8Collections.toList(dev.openallay.util.Java8Collections.listCopyOf(skills).stream()
                .sorted(Comparator.comparing(skill -> skill.metadata().name())));
        diagnostics = dev.openallay.util.Java8Collections.listCopyOf(diagnostics);

        this.skills = skills;
        this.diagnostics = diagnostics;
    }
    public List<Skill> skills() { return skills; }
    public List<SkillDiagnostic> diagnostics() { return diagnostics; }
public static SkillSettingsView empty() {
        return new SkillSettingsView(dev.openallay.util.Java8Collections.listOf(), dev.openallay.util.Java8Collections.listOf());
    }
public Optional<Skill> find(String name) {
        return skills.stream().filter(skill -> skill.metadata().name().equals(name)).findFirst();
    }
@dev.openallay.value.ValueType(Skill.ValueSchemaProvider.class)
public static final class Skill {
    private final SkillMetadata metadata;
    private final String body;
    private final String markdown;
    private final boolean overridePresent;
    public Skill(SkillMetadata metadata, String body, String markdown, boolean overridePresent) {

            metadata = java.util.Objects.requireNonNull(metadata, "metadata");
            if (body == null || dev.openallay.util.Java8Strings.isBlank(body)) {
                throw new IllegalArgumentException("Skill body must not be blank");
            }
            if (markdown == null || dev.openallay.util.Java8Strings.isBlank(markdown)) {
                throw new IllegalArgumentException("Skill Markdown must not be blank");
            }

        this.metadata = metadata;
        this.body = body;
        this.markdown = markdown;
        this.overridePresent = overridePresent;
    }
    public SkillMetadata metadata() { return metadata; }
    public String body() { return body; }
    public String markdown() { return markdown; }
    public boolean overridePresent() { return overridePresent; }
public SkillSource.Origin origin() {
            return metadata.origin();
        }
public boolean createsOverrideOnSave() {
            return (metadata.origin() == SkillSource.Origin.BUNDLED
                    || metadata.origin() == SkillSource.Origin.EXTERNAL) && !overridePresent;
        }
public boolean canDeleteOverride() {
            return overridePresent;
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Skill)) return false;
        Skill that = (Skill) other;
        return java.util.Objects.equals(metadata, that.metadata) && java.util.Objects.equals(body, that.body) && java.util.Objects.equals(markdown, that.markdown) && overridePresent == that.overridePresent;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(metadata);
        hash = 31 * hash + java.util.Objects.hashCode(body);
        hash = 31 * hash + java.util.Objects.hashCode(markdown);
        hash = 31 * hash + Boolean.hashCode(overridePresent);
        return hash;
    }
    @Override public String toString() { return "Skill[metadata=" + metadata + ", body=" + body + ", markdown=" + markdown + ", overridePresent=" + overridePresent + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Skill> schema() {
            return new dev.openallay.value.ValueSchema<>(Skill.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Skill>>asList(new dev.openallay.value.ValueSchema.Component<>(Skill.class, "metadata", Skill::metadata), new dev.openallay.value.ValueSchema.Component<>(Skill.class, "body", Skill::body), new dev.openallay.value.ValueSchema.Component<>(Skill.class, "markdown", Skill::markdown), new dev.openallay.value.ValueSchema.Component<>(Skill.class, "overridePresent", Skill::overridePresent)), arguments -> new Skill((SkillMetadata) arguments[0], (String) arguments[1], (String) arguments[2], (Boolean) arguments[3]));
        }
    }
}
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SkillSettingsView)) return false;
        SkillSettingsView that = (SkillSettingsView) other;
        return java.util.Objects.equals(skills, that.skills) && java.util.Objects.equals(diagnostics, that.diagnostics);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(skills);
        hash = 31 * hash + java.util.Objects.hashCode(diagnostics);
        return hash;
    }
    @Override public String toString() { return "SkillSettingsView[skills=" + skills + ", diagnostics=" + diagnostics + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<SkillSettingsView> schema() {
            return new dev.openallay.value.ValueSchema<>(SkillSettingsView.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<SkillSettingsView>>asList(new dev.openallay.value.ValueSchema.Component<>(SkillSettingsView.class, "skills", SkillSettingsView::skills), new dev.openallay.value.ValueSchema.Component<>(SkillSettingsView.class, "diagnostics", SkillSettingsView::diagnostics)), arguments -> new SkillSettingsView((List) arguments[0], (List) arguments[1]));
        }
    }
}
