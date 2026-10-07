package dev.openallay.requirement;

import java.util.Map;

/** Detached snapshot supplied by the settings UI; holds no mutation or runtime authority. */
@dev.openallay.value.ValueType(RequirementEnvironment.ValueSchemaProvider.class)
public final class RequirementEnvironment {
    private final Map<String, RequirementAvailability> capabilities;
    private final Map<String, RequirementAvailability> extensions;
    private final Map<String, RequirementAvailability> skills;
    public RequirementEnvironment(Map<String, RequirementAvailability> capabilities, Map<String, RequirementAvailability> extensions, Map<String, RequirementAvailability> skills) {

        capabilities = Map.copyOf(capabilities);
        extensions = Map.copyOf(extensions);
        skills = Map.copyOf(skills);

        this.capabilities = capabilities;
        this.extensions = extensions;
        this.skills = skills;
    }
    public Map<String, RequirementAvailability> capabilities() { return capabilities; }
    public Map<String, RequirementAvailability> extensions() { return extensions; }
    public Map<String, RequirementAvailability> skills() { return skills; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RequirementEnvironment)) return false;
        RequirementEnvironment that = (RequirementEnvironment) other;
        return java.util.Objects.equals(capabilities, that.capabilities) && java.util.Objects.equals(extensions, that.extensions) && java.util.Objects.equals(skills, that.skills);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(capabilities);
        hash = 31 * hash + java.util.Objects.hashCode(extensions);
        hash = 31 * hash + java.util.Objects.hashCode(skills);
        return hash;
    }
    @Override public String toString() { return "RequirementEnvironment[capabilities=" + capabilities + ", extensions=" + extensions + ", skills=" + skills + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<RequirementEnvironment> schema() {
            return new dev.openallay.value.ValueSchema<>(RequirementEnvironment.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<RequirementEnvironment>>asList(new dev.openallay.value.ValueSchema.Component<>(RequirementEnvironment.class, "capabilities", RequirementEnvironment::capabilities), new dev.openallay.value.ValueSchema.Component<>(RequirementEnvironment.class, "extensions", RequirementEnvironment::extensions), new dev.openallay.value.ValueSchema.Component<>(RequirementEnvironment.class, "skills", RequirementEnvironment::skills)), arguments -> new RequirementEnvironment((Map) arguments[0], (Map) arguments[1], (Map) arguments[2]));
        }
    }
}
