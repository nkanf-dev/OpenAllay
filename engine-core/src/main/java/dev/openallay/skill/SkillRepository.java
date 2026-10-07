package dev.openallay.skill;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

public final class SkillRepository implements SkillCatalog {
    private final SkillParser parser;
    private final Set<String> availableTools;
    private volatile Map<String, SkillDocument> skills = dev.openallay.util.Java8Collections.mapOf();
    private volatile List<SkillDiagnostic> diagnostics = dev.openallay.util.Java8Collections.listOf();
    private volatile Set<String> runtimeDisabledSkills = dev.openallay.util.Java8Collections.setOf();
    // Originals are retained independently of local overrides and settings reload generations.
    private Map<String, SkillSource> externalSources = dev.openallay.util.Java8Collections.mapOf();
    private Set<String> baseSkillNames = dev.openallay.util.Java8Collections.setOf();

    public SkillRepository(SkillParser parser, Collection<String> availableTools) {
        this.parser = parser;
        this.availableTools = dev.openallay.util.Java8Collections.setCopyOf(availableTools);
    }

    public synchronized boolean reload(Collection<SkillSource> sources, Set<String> installedMods) {
        Map<String, SkillDocument> candidate = new TreeMap<>();
        Set<String> nextBaseNames = new java.util.HashSet<>();
        List<SkillDiagnostic> nextDiagnostics = new ArrayList<>();
        try {
            for (SkillSource source : dev.openallay.util.Java8Collections.listCopyOf(sources)) {
                SkillDocument document = validated(source, installedMods, nextDiagnostics);
                if (document == null) {
                    continue;
                }
                if (candidate.putIfAbsent(document.metadata().name(), document) != null) {
                    throw new IllegalArgumentException(
                            "Duplicate Skill name: " + document.metadata().name());
                }
                if (document.metadata().origin() != SkillSource.Origin.LOCAL) {
                    nextBaseNames.add(document.metadata().name());
                }
            }
            mergeExternal(candidate, installedMods, nextDiagnostics);
            skills = dev.openallay.util.Java8Collections.mapCopyOf(candidate);
            baseSkillNames = dev.openallay.util.Java8Collections.setCopyOf(nextBaseNames);
            diagnostics = dev.openallay.util.Java8Collections.listCopyOf(nextDiagnostics);
            return true;
        } catch (RuntimeException failure) {
            String provenance = sources.isEmpty() ? "skill-reload" : sources.iterator().next().provenance();
            diagnostics = dev.openallay.util.Java8Collections.listOf(new SkillDiagnostic(
                    "skill_validation_failed", failure.getMessage(), provenance));
            return false;
        }
    }

    /**
     * Validates an Extension-owned Skill batch against the current immutable catalog without
     * changing it. Loader registration uses this before publishing any contribution kind.
     */
    public synchronized void validateExternal(
            Collection<SkillSource> sources, Set<String> installedMods) {
        Set<String> names = new java.util.HashSet<>(externalSources.keySet());
        names.addAll(baseSkillNames);
        skills.forEach((name, document) -> {
            if (document.metadata().origin() != SkillSource.Origin.LOCAL) {
                names.add(name);
            }
        });
        List<SkillDiagnostic> ignoredDiagnostics = new ArrayList<>();
        for (SkillSource source : dev.openallay.util.Java8Collections.listCopyOf(sources)) {
            SkillDocument document = validated(source, installedMods, ignoredDiagnostics);
            if (document == null) {
                throw new IllegalArgumentException(
                        "Extension Skill requires unavailable mod: " + source.entryPath());
            }
            if (!names.add(document.metadata().name())) {
                throw new IllegalArgumentException(
                        "Duplicate Skill name: " + document.metadata().name());
            }
        }
    }

    /**
     * Atomically overlays a previously validated Extension Skill batch. Existing Skills remain
     * available when validation fails.
     */
    public synchronized void registerExternal(
            Collection<SkillSource> sources, Set<String> installedMods) {
        List<SkillSource> batch = dev.openallay.util.Java8Collections.listCopyOf(sources);
        validateExternal(batch, installedMods);
        Map<String, SkillDocument> candidate = new TreeMap<>(skills);
        Map<String, SkillSource> nextExternal = new TreeMap<>(externalSources);
        List<SkillDiagnostic> nextDiagnostics = new ArrayList<>(diagnostics);
        for (SkillSource source : batch) {
            SkillDocument document = validated(source, installedMods, nextDiagnostics);
            if (document == null) {
                throw new IllegalStateException(
                        "Validated Extension Skill became unavailable: " + source.entryPath());
            }
            // A preloaded local override still wins when loader registration follows local loading.
            candidate.putIfAbsent(document.metadata().name(), document);
            nextExternal.put(document.metadata().name(), source);
        }
        skills = dev.openallay.util.Java8Collections.mapCopyOf(candidate);
        externalSources = dev.openallay.util.Java8Collections.mapCopyOf(nextExternal);
        diagnostics = dev.openallay.util.Java8Collections.listCopyOf(nextDiagnostics);
    }

    /** Immutable registered originals, including references, for settings display and overrides. */
    public synchronized List<SkillSource> externalSources() {
        return dev.openallay.util.Java8Collections.toList(externalSources.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue));
    }

    /**
     * Reloads trusted bundled Skills and overlays isolated local filesystem packages. A bad local
     * package never removes a bundled or previously validated local document with the same name.
     */
    public synchronized boolean reload(
            Collection<SkillSource> bundledSources,
            FilesystemSkillLoader.LoadResult localSkills,
            Set<String> installedMods) {
        java.util.Objects.requireNonNull(localSkills, "localSkills");
        Map<String, SkillDocument> candidate = new TreeMap<>();
        Set<String> nextBaseNames = new java.util.HashSet<>();
        List<SkillDiagnostic> nextDiagnostics = new ArrayList<>();
        try {
            for (SkillSource source : dev.openallay.util.Java8Collections.listCopyOf(bundledSources)) {
                SkillDocument document = validated(source, installedMods, nextDiagnostics);
                if (document == null) {
                    continue;
                }
                if (candidate.put(document.metadata().name(), document) != null) {
                    throw new IllegalArgumentException(
                            "Duplicate bundled Skill name: " + document.metadata().name());
                }
                nextBaseNames.add(document.metadata().name());
            }
            mergeExternal(candidate, installedMods, nextDiagnostics);
        } catch (RuntimeException failure) {
            String provenance = bundledSources.isEmpty()
                    ? "openallay:bundled"
                    : bundledSources.iterator().next().provenance();
            diagnostics = dev.openallay.util.Java8Collections.listOf(new SkillDiagnostic(
                    "skill_validation_failed", failure.getMessage(), provenance));
            return false;
        }

        for (SkillSource source : localSkills.sources()) {
            try {
                SkillDocument document = validated(source, installedMods, nextDiagnostics);
                if (document != null) {
                    candidate.put(document.metadata().name(), document);
                }
            } catch (RuntimeException failure) {
                retainLastValidLocal(source.directoryName(), candidate, installedMods);
                nextDiagnostics.add(new SkillDiagnostic(
                        "skill_validation_failed",
                        failure.getMessage(),
                        source.provenance() + ":" + source.entryPath()));
            }
        }
        for (FilesystemSkillLoader.RejectedSkill rejected : localSkills.rejected()) {
            retainLastValidLocal(rejected.skillName(), candidate, installedMods);
            nextDiagnostics.add(rejected.diagnostic());
        }
        skills = dev.openallay.util.Java8Collections.mapCopyOf(candidate);
        baseSkillNames = dev.openallay.util.Java8Collections.setCopyOf(nextBaseNames);
        diagnostics = dev.openallay.util.Java8Collections.listCopyOf(nextDiagnostics);
        return true;
    }

    private void mergeExternal(Map<String, SkillDocument> candidate, Set<String> installedMods,
            List<SkillDiagnostic> nextDiagnostics) {
        for (SkillSource source : externalSources()) {
            SkillDocument document = validated(source, installedMods, nextDiagnostics);
            if (document == null) {
                continue;
            }
            SkillDocument previous = candidate.putIfAbsent(document.metadata().name(), document);
            if (previous != null && previous.metadata().origin() != SkillSource.Origin.LOCAL) {
                throw new IllegalArgumentException("Duplicate Skill name: " + document.metadata().name());
            }
        }
    }

    private SkillDocument validated(
            SkillSource source, Set<String> installedMods, List<SkillDiagnostic> nextDiagnostics) {
        SkillDocument document = parser.parse(source);
        if (!installedMods.containsAll(document.metadata().requiredMods())) {
            Set<String> missing = new java.util.TreeSet<>(document.metadata().requiredMods());
            missing.removeAll(installedMods);
            nextDiagnostics.add(new SkillDiagnostic(
                    "required_mod_unavailable",
                    "Skill " + document.metadata().name() + " requires " + missing,
                    document.metadata().provenance()));
            return null;
        }
        if (!availableTools.containsAll(document.metadata().allowedTools())) {
            Set<String> missing = new java.util.TreeSet<>(document.metadata().allowedTools());
            missing.removeAll(availableTools);
            throw new IllegalArgumentException("Skill declares unavailable tools: " + missing);
        }
        return document;
    }

    private void retainLastValidLocal(
            String name, Map<String, SkillDocument> candidate, Set<String> installedMods) {
        SkillDocument previous = skills.get(name);
        if (previous == null
                || previous.metadata().origin() != SkillSource.Origin.LOCAL
                || !installedMods.containsAll(previous.metadata().requiredMods())
                || !availableTools.containsAll(previous.metadata().allowedTools())) {
            return;
        }
        candidate.put(name, previous);
    }

    public Optional<SkillDocument> find(String name) {
        return Optional.ofNullable(skills.get(name));
    }

    public List<SkillMetadata> metadata() {
        return dev.openallay.util.Java8Collections.toList(skills.values().stream().map(SkillDocument::metadata));
    }

    public List<SkillDiagnostic> diagnostics() {
        return diagnostics;
    }

    public SkillCatalogSnapshot snapshot(Set<String> disabledSkills) {
        return snapshot(disabledSkills, true);
    }

    public SkillCatalogSnapshot snapshotIncludingRuntimeDisabled(Set<String> disabledSkills) {
        return snapshot(disabledSkills, false);
    }

    public SkillCatalogSnapshot snapshotWithRuntimeEnabled(Set<String> disabledSkills, Set<String> enabledSkills) {
        java.util.HashSet<String> disabled = new java.util.HashSet<>(disabledSkills);
        runtimeDisabledSkills.stream().filter(name -> !enabledSkills.contains(name)).forEach(disabled::add);
        return snapshot(disabled, false);
    }

    private SkillCatalogSnapshot snapshot(
            Set<String> disabledSkills, boolean includeRuntimeDisabled) {
        java.util.HashSet<String> disabled = new java.util.HashSet<>(disabledSkills);
        if (includeRuntimeDisabled) {
            disabled.addAll(runtimeDisabledSkills);
        }
        Map<String, SkillDocument> captured = new TreeMap<>();
        for (Map.Entry<String, SkillDocument> entry : skills.entrySet()) {
            if (!disabled.contains(entry.getKey())) {
                captured.put(entry.getKey(), entry.getValue());
            }
        }
        return new SkillCatalogSnapshot(captured);
    }

    /**
     * Hides capabilities whose availability is controlled outside the general deny-only policy.
     * Existing request snapshots remain immutable.
     */
    public void setRuntimeDisabledSkills(Set<String> disabledSkills) {
        runtimeDisabledSkills = dev.openallay.util.Java8Collections.setCopyOf(disabledSkills);
    }
}
