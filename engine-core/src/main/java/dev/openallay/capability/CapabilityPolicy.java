package dev.openallay.capability;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** Deny-only local policy for stable Tool identities and bundled Skill names. */
@dev.openallay.value.ValueType(CapabilityPolicy.ValueSchemaProvider.class)
public final class CapabilityPolicy {
    private final Set<String> disabledTools;
    private final Set<String> disabledSkills;
    public CapabilityPolicy(Set<String> disabledTools, Set<String> disabledSkills) {

        disabledTools = canonical(disabledTools, CapabilityPolicy::requireToolId, "disabledTools");
        disabledSkills = canonical(
                disabledSkills, CapabilityPolicy::requireSkillName, "disabledSkills");

        this.disabledTools = disabledTools;
        this.disabledSkills = disabledSkills;
    }
    public Set<String> disabledTools() { return disabledTools; }
    public Set<String> disabledSkills() { return disabledSkills; }
private static final Pattern TOOL_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
private static final Pattern SKILL_NAME = Pattern.compile("[a-z0-9][a-z0-9-]*");
public static CapabilityPolicy defaults() {
        return new CapabilityPolicy(Set.of(), Set.of());
    }
public static String requireToolId(String value) {
        if (value == null || !TOOL_ID.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid Tool identity: " + value);
        }
        return value;
    }
public static String requireSkillName(String value) {
        if (value == null || !SKILL_NAME.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid Skill name: " + value);
        }
        return value;
    }
private static Set<String> canonical(
            Set<String> values,
            java.util.function.UnaryOperator<String> validator,
            String name) {
        Objects.requireNonNull(values, name);
        TreeSet<String> sorted = new TreeSet<>();
        for (String value : values) {
            sorted.add(validator.apply(value));
        }
        return Collections.unmodifiableSet(sorted);
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CapabilityPolicy)) return false;
        CapabilityPolicy that = (CapabilityPolicy) other;
        return java.util.Objects.equals(disabledTools, that.disabledTools) && java.util.Objects.equals(disabledSkills, that.disabledSkills);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(disabledTools);
        hash = 31 * hash + java.util.Objects.hashCode(disabledSkills);
        return hash;
    }
    @Override public String toString() { return "CapabilityPolicy[disabledTools=" + disabledTools + ", disabledSkills=" + disabledSkills + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<CapabilityPolicy> schema() {
            return new dev.openallay.value.ValueSchema<>(CapabilityPolicy.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<CapabilityPolicy>>asList(new dev.openallay.value.ValueSchema.Component<>(CapabilityPolicy.class, "disabledTools", CapabilityPolicy::disabledTools), new dev.openallay.value.ValueSchema.Component<>(CapabilityPolicy.class, "disabledSkills", CapabilityPolicy::disabledSkills)), arguments -> new CapabilityPolicy((Set) arguments[0], (Set) arguments[1]));
        }
    }
}
