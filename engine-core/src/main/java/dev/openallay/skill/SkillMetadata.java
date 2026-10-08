package dev.openallay.skill;

import dev.openallay.requirement.RequirementCodec;
import dev.openallay.requirement.RequirementSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@dev.openallay.value.ValueType(SkillMetadata.ValueSchemaProvider.class)
public final class SkillMetadata {
    private final String name;
    private final String description;
    private final Optional<String> license;
    private final Optional<String> compatibility;
    private final Map<String, String> attributes;
    private final Set<String> requiredMods;
    private final Set<String> allowedTools;
    private final List<String> references;
    private final String provenance;
    private final SkillSource.Origin origin;
    public SkillMetadata(String name, String description, Optional<String> license, Optional<String> compatibility, Map<String, String> attributes, Set<String> requiredMods, Set<String> allowedTools, List<String> references, String provenance, SkillSource.Origin origin) {

        if (name == null
                || name.length() > 64
                || !name.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
            throw new IllegalArgumentException("Invalid Skill name: " + name);
        }
        if (description == null || dev.openallay.util.Java8Strings.isBlank(description) || description.length() > 1024) {
            throw new IllegalArgumentException("Skill description must not be blank");
        }
        license = java.util.Objects.requireNonNull(license, "license");
        compatibility = java.util.Objects.requireNonNull(compatibility, "compatibility");
        license.ifPresent(value -> {
            if (dev.openallay.util.Java8Strings.isBlank(value)) {
                throw new IllegalArgumentException("Skill license must not be blank");
            }
        });
        compatibility.ifPresent(value -> {
            if (dev.openallay.util.Java8Strings.isBlank(value) || value.length() > 500) {
                throw new IllegalArgumentException("Invalid Skill compatibility");
            }
        });
        attributes = dev.openallay.util.Java8Collections.mapCopyOf(attributes);
        RequirementCodec.fromMetadata(attributes);
        requiredMods = dev.openallay.util.Java8Collections.setCopyOf(requiredMods);
        allowedTools = dev.openallay.util.Java8Collections.setCopyOf(allowedTools);
        references = dev.openallay.util.Java8Collections.listCopyOf(references);
        if (provenance == null || dev.openallay.util.Java8Strings.isBlank(provenance)) {
            throw new IllegalArgumentException("Skill provenance must not be blank");
        }
        origin = java.util.Objects.requireNonNull(origin, "origin");

        this.name = name;
        this.description = description;
        this.license = license;
        this.compatibility = compatibility;
        this.attributes = attributes;
        this.requiredMods = requiredMods;
        this.allowedTools = allowedTools;
        this.references = references;
        this.provenance = provenance;
        this.origin = origin;
    }
    public String name() { return name; }
    public String description() { return description; }
    public Optional<String> license() { return license; }
    public Optional<String> compatibility() { return compatibility; }
    public Map<String, String> attributes() { return attributes; }
    public Set<String> requiredMods() { return requiredMods; }
    public Set<String> allowedTools() { return allowedTools; }
    public List<String> references() { return references; }
    public String provenance() { return provenance; }
    public SkillSource.Origin origin() { return origin; }
public RequirementSet requirements() {
        return RequirementCodec.fromMetadata(attributes);
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SkillMetadata)) return false;
        SkillMetadata that = (SkillMetadata) other;
        return java.util.Objects.equals(name, that.name) && java.util.Objects.equals(description, that.description) && java.util.Objects.equals(license, that.license) && java.util.Objects.equals(compatibility, that.compatibility) && java.util.Objects.equals(attributes, that.attributes) && java.util.Objects.equals(requiredMods, that.requiredMods) && java.util.Objects.equals(allowedTools, that.allowedTools) && java.util.Objects.equals(references, that.references) && java.util.Objects.equals(provenance, that.provenance) && java.util.Objects.equals(origin, that.origin);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(name);
        hash = 31 * hash + java.util.Objects.hashCode(description);
        hash = 31 * hash + java.util.Objects.hashCode(license);
        hash = 31 * hash + java.util.Objects.hashCode(compatibility);
        hash = 31 * hash + java.util.Objects.hashCode(attributes);
        hash = 31 * hash + java.util.Objects.hashCode(requiredMods);
        hash = 31 * hash + java.util.Objects.hashCode(allowedTools);
        hash = 31 * hash + java.util.Objects.hashCode(references);
        hash = 31 * hash + java.util.Objects.hashCode(provenance);
        hash = 31 * hash + java.util.Objects.hashCode(origin);
        return hash;
    }
    @Override public String toString() { return "SkillMetadata[name=" + name + ", description=" + description + ", license=" + license + ", compatibility=" + compatibility + ", attributes=" + attributes + ", requiredMods=" + requiredMods + ", allowedTools=" + allowedTools + ", references=" + references + ", provenance=" + provenance + ", origin=" + origin + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<SkillMetadata> schema() {
            return new dev.openallay.value.ValueSchema<>(SkillMetadata.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<SkillMetadata>>asList(new dev.openallay.value.ValueSchema.Component<>(SkillMetadata.class, "name", SkillMetadata::name), new dev.openallay.value.ValueSchema.Component<>(SkillMetadata.class, "description", SkillMetadata::description), new dev.openallay.value.ValueSchema.Component<>(SkillMetadata.class, "license", SkillMetadata::license), new dev.openallay.value.ValueSchema.Component<>(SkillMetadata.class, "compatibility", SkillMetadata::compatibility), new dev.openallay.value.ValueSchema.Component<>(SkillMetadata.class, "attributes", SkillMetadata::attributes), new dev.openallay.value.ValueSchema.Component<>(SkillMetadata.class, "requiredMods", SkillMetadata::requiredMods), new dev.openallay.value.ValueSchema.Component<>(SkillMetadata.class, "allowedTools", SkillMetadata::allowedTools), new dev.openallay.value.ValueSchema.Component<>(SkillMetadata.class, "references", SkillMetadata::references), new dev.openallay.value.ValueSchema.Component<>(SkillMetadata.class, "provenance", SkillMetadata::provenance), new dev.openallay.value.ValueSchema.Component<>(SkillMetadata.class, "origin", SkillMetadata::origin)), arguments -> new SkillMetadata((String) arguments[0], (String) arguments[1], (Optional) arguments[2], (Optional) arguments[3], (Map) arguments[4], (Set) arguments[5], (Set) arguments[6], (List) arguments[7], (String) arguments[8], (SkillSource.Origin) arguments[9]));
        }
    }
}
