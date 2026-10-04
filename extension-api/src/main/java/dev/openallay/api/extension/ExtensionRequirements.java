package dev.openallay.api.extension;

import java.util.Set;
import java.util.Objects;
import java.util.Collections;

/** Advisory requirements only. These declarations never grant authority or availability. */
public final class ExtensionRequirements {
    private final Set<String> capabilities;
    private final Set<String> extensions;
    private final Set<String> skills;

    public ExtensionRequirements(Set<String> capabilities, Set<String> extensions, Set<String> skills) {
        this.capabilities = ApiValidation.ids(capabilities, "advisory capability ID", false);
        this.extensions = ApiValidation.ids(extensions, "Extension ID", true);
        this.skills = requireSkills(skills);
    }

    public Set<String> capabilities() { return capabilities; }
    public Set<String> extensions() { return extensions; }
    public Set<String> skills() { return skills; }

    public static final ExtensionRequirements EMPTY = new ExtensionRequirements(
            Collections.<String>emptySet(), Collections.<String>emptySet(), Collections.<String>emptySet());
    public boolean isEmpty() { return capabilities.isEmpty() && extensions.isEmpty() && skills.isEmpty(); }
    private static Set<String> requireSkills(Set<String> skills) {
        Set<String> copy = ApiValidation.ids(skills, "Skill ID", false);
        for (String id : copy)
            if (id.length() > 64 || !id.matches("[a-z0-9]+(?:-[a-z0-9]+)*"))
                throw new IllegalArgumentException("Invalid Skill ID: " + id);
        return copy;
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ExtensionRequirements)) return false;
        ExtensionRequirements that = (ExtensionRequirements) other;
        return Objects.equals(capabilities, that.capabilities) &&
                Objects.equals(extensions, that.extensions) &&
                Objects.equals(skills, that.skills);
    }
    @Override public int hashCode() { return Objects.hash(capabilities, extensions, skills); }
}
