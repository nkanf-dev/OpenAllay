package dev.openallay.skill;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Immutable Skill documents captured independently from future repository reloads. */
public final class SkillCatalogSnapshot implements SkillCatalog {
    public static final String UNRESTRICTED_JAVASCRIPT = "unrestricted-javascript";
    public static final String GAME_COMMANDS = "run-game-commands";
    private final Map<String, SkillDocument> skills;
    private final Map<String, SkillDocument> eligibleSkills;

    SkillCatalogSnapshot(Map<String, SkillDocument> skills) {
        this(skills, false);
    }

    private SkillCatalogSnapshot(Map<String, SkillDocument> skills, boolean unrestrictedJavascript) {
        this(skills, unrestrictedJavascript, skills.containsKey(GAME_COMMANDS));
    }

    private SkillCatalogSnapshot(
            Map<String, SkillDocument> skills, boolean unrestrictedJavascript, boolean commandsAvailable) {
        TreeMap<String, SkillDocument> canonical = new TreeMap<>(skills);
        canonical.forEach((name, document) -> {
            if (!name.equals(document.metadata().name())) {
                throw new IllegalArgumentException("Skill key does not match document name");
            }
        });
        this.eligibleSkills = Collections.unmodifiableMap(new TreeMap<>(canonical));
        if (!unrestrictedJavascript) {
            canonical.remove(UNRESTRICTED_JAVASCRIPT);
        }
        if (!commandsAvailable) {
            canonical.remove(GAME_COMMANDS);
        }
        this.skills = Collections.unmodifiableMap(canonical);
    }

    /** Selects guidance from captured documents, never from a mutable setting or repository. */
    public SkillCatalogSnapshot forRequest(boolean unrestrictedJavascript) {
        return forRequest(unrestrictedJavascript,
                unrestrictedJavascript || skills.containsKey(GAME_COMMANDS));
    }

    /** Explicit route availability controls guidance, even when full access has no bound route. */
    public SkillCatalogSnapshot forRequest(boolean unrestrictedJavascript, boolean commandsAvailable) {
        return new SkillCatalogSnapshot(eligibleSkills, unrestrictedJavascript, commandsAvailable);
    }

    @Override
    public Optional<SkillDocument> find(String name) {
        return Optional.ofNullable(skills.get(name));
    }

    @Override
    public List<SkillMetadata> metadata() {
        return skills.values().stream().map(SkillDocument::metadata).toList();
    }
}
