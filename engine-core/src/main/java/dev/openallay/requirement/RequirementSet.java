package dev.openallay.requirement;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** Advisory declarations only. These IDs neither grant authority nor gate runtime availability. */
@dev.openallay.value.ValueType(RequirementSet.ValueSchemaProvider.class)
public final class RequirementSet {
    private final Set<String> capabilities;
    private final Set<String> extensions;
    private final Set<String> skills;
    public RequirementSet(Set<String> capabilities, Set<String> extensions, Set<String> skills) {

        capabilities = canonical(capabilities, RequirementKind.CAPABILITY);
        extensions = canonical(extensions, RequirementKind.EXTENSION);
        skills = canonical(skills, RequirementKind.SKILL);

        this.capabilities = capabilities;
        this.extensions = extensions;
        this.skills = skills;
    }
    public Set<String> capabilities() { return capabilities; }
    public Set<String> extensions() { return extensions; }
    public Set<String> skills() { return skills; }
public static final RequirementSet EMPTY = new RequirementSet(dev.openallay.util.Java8Collections.setOf(), dev.openallay.util.Java8Collections.setOf(), dev.openallay.util.Java8Collections.setOf());
private static final Pattern CAPABILITY = Pattern.compile(
            "[a-z0-9][a-z0-9_.-]*(?::[a-z0-9_][a-z0-9_./-]*)?");
private static final Pattern EXTENSION = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
private static final Pattern SKILL = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
public boolean isEmpty() {
        return capabilities.isEmpty() && extensions.isEmpty() && skills.isEmpty();
    }
static String requireId(String id, RequirementKind kind) {
        Pattern pattern;
        switch (kind) {
            case CAPABILITY: pattern = CAPABILITY; break;
            case EXTENSION: pattern = EXTENSION; break;
            case SKILL: pattern = SKILL; break;
            default: throw new IllegalStateException("Unknown advisory requirement kind: " + kind);
        }
        if (id == null || !pattern.matcher(id).matches()
                || (kind == RequirementKind.SKILL && id.length() > 64)) {
            throw new IllegalArgumentException("Invalid advisory " + kind + " ID: " + id);
        }
        return id;
    }
private static Set<String> canonical(Set<String> ids, RequirementKind kind) {
        TreeSet<String> result = new TreeSet<>();
        for (String id : Objects.requireNonNull(ids, "ids")) {
            result.add(requireId(id, kind));
        }
        return Collections.unmodifiableSet(result);
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RequirementSet)) return false;
        RequirementSet that = (RequirementSet) other;
        return java.util.Objects.equals(capabilities, that.capabilities) && java.util.Objects.equals(extensions, that.extensions) && java.util.Objects.equals(skills, that.skills);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(capabilities);
        hash = 31 * hash + java.util.Objects.hashCode(extensions);
        hash = 31 * hash + java.util.Objects.hashCode(skills);
        return hash;
    }
    @Override public String toString() { return "RequirementSet[capabilities=" + capabilities + ", extensions=" + extensions + ", skills=" + skills + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<RequirementSet> schema() {
            return new dev.openallay.value.ValueSchema<>(RequirementSet.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<RequirementSet>>asList(new dev.openallay.value.ValueSchema.Component<>(RequirementSet.class, "capabilities", RequirementSet::capabilities), new dev.openallay.value.ValueSchema.Component<>(RequirementSet.class, "extensions", RequirementSet::extensions), new dev.openallay.value.ValueSchema.Component<>(RequirementSet.class, "skills", RequirementSet::skills)), arguments -> new RequirementSet((Set) arguments[0], (Set) arguments[1], (Set) arguments[2]));
        }
    }
}
