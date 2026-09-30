package dev.openallay.requirement;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** Advisory declarations only. These IDs neither grant authority nor gate runtime availability. */
public record RequirementSet(Set<String> capabilities, Set<String> extensions, Set<String> skills) {
    public static final RequirementSet EMPTY = new RequirementSet(Set.of(), Set.of(), Set.of());
    private static final Pattern CAPABILITY = Pattern.compile(
            "[a-z0-9][a-z0-9_.-]*(?::[a-z0-9_][a-z0-9_./-]*)?");
    private static final Pattern EXTENSION = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Pattern SKILL = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

    public RequirementSet {
        capabilities = canonical(capabilities, RequirementKind.CAPABILITY);
        extensions = canonical(extensions, RequirementKind.EXTENSION);
        skills = canonical(skills, RequirementKind.SKILL);
    }

    public boolean isEmpty() {
        return capabilities.isEmpty() && extensions.isEmpty() && skills.isEmpty();
    }

    static String requireId(String id, RequirementKind kind) {
        Pattern pattern = switch (kind) {
            case CAPABILITY -> CAPABILITY;
            case EXTENSION -> EXTENSION;
            case SKILL -> SKILL;
        };
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
}
